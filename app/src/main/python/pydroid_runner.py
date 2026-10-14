"""
pydroid_runner.py
Core execution engine for PyDroidLab.

Responsibilities:
1. Configures user private `site-packages` directory in `sys.path` via `site.addsitedir` so
   runtime-installed pip packages (e.g., `requests`) are immediately importable.
2. Redirects `sys.stdout`, `sys.stderr`, `sys.stdin`, and `builtins.input` to the Kotlin
   `PythonBridgeHost` attached to the interactive terminal emulator.
3. Supports synchronous line-blocking `input(prompt)` and asynchronous execution interruption
   via `ctypes.pythonapi.PyThreadState_SetAsyncExc(tid, ctypes.py_object(KeyboardInterrupt))`.
"""

import builtins
import ctypes
import io
import os
import site
import sys
import threading
import time
import traceback

_state_lock = threading.Lock()
_running_thread_id = None
_active_host = None
_interrupted_flag = False


class _InteractiveOutStream(io.TextIOBase):
    """Forwards stdout or stderr writes immediately to the Kotlin terminal bridge."""

    def __init__(self, host, is_stderr=False):
        super().__init__()
        self._host = host
        self._is_stderr = is_stderr

    def writable(self):
        return True

    def write(self, text):
        if not text:
            return 0
        s = str(text)
        if self._is_stderr:
            self._host.onStderr(s)
        else:
            self._host.onStdout(s)
        return len(s)

    def flush(self):
        pass


class _InteractiveInStream(io.TextIOBase):
    """Provides line-oriented stdin backed by the Kotlin terminal's blocking input queue."""

    def __init__(self, host):
        super().__init__()
        self._host = host
        self._buffer = ""

    def readable(self):
        return True

    def readline(self, size=-1):
        global _interrupted_flag
        if _interrupted_flag:
            raise KeyboardInterrupt("Execution interrupted by user")
        line = self._host.requestInput("")
        if line is None:
            if _interrupted_flag:
                raise KeyboardInterrupt("Execution interrupted during input")
            return ""
        result = str(line) + "\n"
        if size is not None and size >= 0:
            return result[:size]
        return result

    def read(self, size=-1):
        return self.readline(size)


def ensure_paths(user_site_packages: str, workspace_dir: str):
    """
    Ensures the user's runtime pip directory and workspace directory are at the front of sys.path.
    """
    os.makedirs(user_site_packages, exist_ok=True)
    os.makedirs(workspace_dir, exist_ok=True)

    # Register .pth files if any exist in user_site_packages
    site.addsitedir(user_site_packages)

    # Ensure user_site_packages has top priority so upgraded packages take precedence
    if user_site_packages in sys.path:
        sys.path.remove(user_site_packages)
    sys.path.insert(0, user_site_packages)

    if workspace_dir in sys.path:
        sys.path.remove(workspace_dir)
    sys.path.insert(0, workspace_dir)


def interrupt_running_code() -> bool:
    """
    Injects a KeyboardInterrupt asynchronously into the running Python script thread
    using CPython's C-API `PyThreadState_SetAsyncExc`.
    Works even if user code is stuck in a tight `while True: pass` CPU loop.
    """
    global _interrupted_flag, _running_thread_id
    with _state_lock:
        _interrupted_flag = True
        tid = _running_thread_id

    if tid is None:
        return False

    try:
        res = ctypes.pythonapi.PyThreadState_SetAsyncExc(
            ctypes.c_long(tid),
            ctypes.py_object(KeyboardInterrupt)
        )
        if res == 0:
            return False
        elif res > 1:
            # Roll back if more than one thread state was modified
            ctypes.pythonapi.PyThreadState_SetAsyncExc(ctypes.c_long(tid), None)
            return False
        return True
    except Exception:
        return False


def execute_script(
    code: str,
    script_path: str,
    workspace_dir: str,
    user_site_packages: str,
    host
) -> dict:
    """
    Executes a Python script string with full stdout/stderr redirection and interactive input().

    :param code: The Python source code to execute.
    :param script_path: Full path of the script file (used for `__file__` and stack traces).
    :param workspace_dir: Working directory (`os.chdir`) for relative file operations.
    :param user_site_packages: App private directory where runtime pip packages are installed.
    :param host: Kotlin `PythonBridgeHost` instance with `onStdout`, `onStderr`, `requestInput`.
    :return: Dictionary with `exit_code` (int) and `elapsed_ms` (int).
    """
    global _running_thread_id, _active_host, _interrupted_flag

    start_time = time.monotonic()
    ensure_paths(user_site_packages, workspace_dir)

    script_dir = os.path.dirname(script_path) if script_path else workspace_dir
    if script_dir and os.path.isdir(script_dir):
        os.chdir(script_dir)
        if script_dir not in sys.path:
            sys.path.insert(0, script_dir)
    else:
        os.chdir(workspace_dir)

    orig_stdout = sys.stdout
    orig_stderr = sys.stderr
    orig_stdin = sys.stdin
    orig_input = builtins.input

    stdout_stream = _InteractiveOutStream(host, is_stderr=False)
    stderr_stream = _InteractiveOutStream(host, is_stderr=True)
    stdin_stream = _InteractiveInStream(host)

    def _custom_input(prompt=""):
        global _interrupted_flag
        if _interrupted_flag:
            raise KeyboardInterrupt("Execution interrupted by user")
        prompt_str = str(prompt) if prompt is not None else ""
        line = host.requestInput(prompt_str)
        if line is None:
            if _interrupted_flag:
                raise KeyboardInterrupt("Execution interrupted by user")
            raise EOFError("EOF when reading a line from interactive console")
        return str(line)

    with _state_lock:
        _running_thread_id = threading.get_ident()
        _active_host = host
        _interrupted_flag = False

    exit_code = 0
    try:
        sys.stdout = stdout_stream
        sys.stderr = stderr_stream
        sys.stdin = stdin_stream
        builtins.input = _custom_input

        exec_globals = {
            "__name__": "__main__",
            "__file__": script_path or "<untitled.py>",
            "__doc__": None,
            "__package__": None,
            "__builtins__": builtins,
        }

        compiled = compile(code, script_path or "<untitled.py>", "exec")
        exec(compiled, exec_globals, exec_globals)

    except SystemExit as e:
        if e.code is None:
            exit_code = 0
        elif isinstance(e.code, int):
            exit_code = e.code
        else:
            stderr_stream.write(f"{e.code}\n")
            exit_code = 1
    except KeyboardInterrupt:
        stderr_stream.write("\nKeyboardInterrupt: Execution stopped by user.\n")
        exit_code = 130
    except BaseException:
        exit_code = 1
        tb_lines = traceback.format_exception(*sys.exc_info())
        # Filter out internal runner frame for clean user-facing tracebacks
        filtered = [line for line in tb_lines if "pydroid_runner.py" not in line]
        stderr_stream.write("".join(filtered if filtered else tb_lines))
    finally:
        try:
            stdout_stream.flush()
            stderr_stream.flush()
        except Exception:
            pass
        sys.stdout = orig_stdout
        sys.stderr = orig_stderr
        sys.stdin = orig_stdin
        builtins.input = orig_input
        with _state_lock:
            _running_thread_id = None
            _active_host = None

    elapsed_ms = int((time.monotonic() - start_time) * 1000)
    return {
        "exit_code": exit_code,
        "elapsed_ms": elapsed_ms,
    }
