"""
pydroid_runner.py
Core execution, REPL, AST diagnostics, and plot-interception engine for PyDroidLab v1.2.
"""

import ast
import builtins
import code
import ctypes
import importlib
import io
import json
import os
import platform
import site
import sys
import threading
import time
import traceback

import android_app

_state_lock = threading.Lock()
_running_thread_id = None
_active_host = None
_interrupted_flag = False
_repl_console = None
_repl_locals = {"__name__": "__console__", "__doc__": None}


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
    """Ensures the user's runtime pip directory and workspace directory are at the front of sys.path."""
    os.makedirs(user_site_packages, exist_ok=True)
    os.makedirs(workspace_dir, exist_ok=True)
    site.addsitedir(user_site_packages)

    if user_site_packages in sys.path:
        sys.path.remove(user_site_packages)
    sys.path.insert(0, user_site_packages)

    if workspace_dir in sys.path:
        sys.path.remove(workspace_dir)
    sys.path.insert(0, workspace_dir)


def _install_visual_hooks(workspace_dir: str, hardware_bridge):
    """
    Hooks matplotlib.pyplot.show() and PIL.Image.Image.show() if those libraries are present,
    automatically saving the figure/image to a temporary PNG in workspace_dir and triggering
    PyDroidLab's built-in zoomable image preview dialog!
    """
    if hardware_bridge is None:
        return

    # Hook matplotlib.pyplot.show if matplotlib is available
    try:
        import matplotlib
        matplotlib.use("Agg")
        import matplotlib.pyplot as plt

        def _custom_plt_show(*args, **kwargs):
            out_dir = os.path.join(workspace_dir, ".plots")
            os.makedirs(out_dir, exist_ok=True)
            out_file = os.path.join(out_dir, f"figure_{int(time.time() * 1000)}.png")
            plt.savefig(out_file, dpi=160, bbox_inches="tight")
            print(f"[PyDroidLab] 已生成图表预览: {out_file}")
            hardware_bridge.showImagePreview(out_file)

        plt.show = _custom_plt_show
    except Exception:
        pass

    # Hook PIL.Image.Image.show if Pillow is available
    try:
        from PIL import Image

        def _custom_pil_show(self, title=None):
            out_dir = os.path.join(workspace_dir, ".plots")
            os.makedirs(out_dir, exist_ok=True)
            out_file = os.path.join(out_dir, f"image_{int(time.time() * 1000)}.png")
            self.save(out_file, format="PNG")
            print(f"[PyDroidLab] 已打开图片预览: {out_file}")
            hardware_bridge.showImagePreview(out_file)

        Image.Image.show = _custom_pil_show
    except Exception:
        pass


def interrupt_running_code() -> bool:
    """
    Injects a KeyboardInterrupt asynchronously into the running Python script thread
    using CPython's C-API `PyThreadState_SetAsyncExc`.
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
            ctypes.pythonapi.PyThreadState_SetAsyncExc(ctypes.c_long(tid), None)
            return False
        return True
    except Exception:
        return False


def execute_script(
    code_str: str,
    script_path: str,
    workspace_dir: str,
    user_site_packages: str,
    host,
    hardware_bridge=None
) -> dict:
    """
    Executes a Python script string with full stdout/stderr redirection, interactive input(),
    Android hardware bridge (`import android_app`), and plot/image preview hooks.
    """
    global _running_thread_id, _active_host, _interrupted_flag

    start_time = time.monotonic()
    ensure_paths(user_site_packages, workspace_dir)
    android_app._set_bridge(hardware_bridge)
    _install_visual_hooks(workspace_dir, hardware_bridge)

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

        compiled = compile(code_str, script_path or "<untitled.py>", "exec")
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


def reset_repl_session():
    """Resets the persistent interactive REPL namespace."""
    global _repl_console, _repl_locals
    _repl_locals = {"__name__": "__console__", "__doc__": None}
    _repl_console = code.InteractiveConsole(locals=_repl_locals, filename="<repl>")


def eval_repl_line(
    line: str,
    workspace_dir: str,
    user_site_packages: str,
    host,
    hardware_bridge=None
) -> bool:
    """
    Pushes a single line into the persistent `code.InteractiveConsole` REPL session.
    Returns True if more multi-line input is expected (i.e. prompt should be `... `),
    or False if the statement completed (prompt should be `>>> `).
    """
    global _repl_console
    ensure_paths(user_site_packages, workspace_dir)
    android_app._set_bridge(hardware_bridge)
    _install_visual_hooks(workspace_dir, hardware_bridge)

    if _repl_console is None:
        reset_repl_session()

    orig_stdout = sys.stdout
    orig_stderr = sys.stderr
    stdout_stream = _InteractiveOutStream(host, is_stderr=False)
    stderr_stream = _InteractiveOutStream(host, is_stderr=True)

    try:
        sys.stdout = stdout_stream
        sys.stderr = stderr_stream
        more_needed = _repl_console.push(line)
        return bool(more_needed)
    finally:
        sys.stdout = orig_stdout
        sys.stderr = orig_stderr


def analyze_code_ast(source_code: str, filename: str = "<editor.py>") -> str:
    """
    Performs fast static analysis using Python's `ast.parse`:
    1. Detects any SyntaxError / IndentationError with 1-based line, column, and error message.
    2. Extracts top-level and nested `class` and `def` / `async def` symbols for the Code Outline drawer.
    Returns a JSON string.
    """
    diagnostics = []
    outline = []

    try:
        tree = ast.parse(source_code, filename=filename)
        for node in ast.walk(tree):
            if isinstance(node, ast.ClassDef):
                outline.append({
                    "name": f"class {node.name}",
                    "kind": "class",
                    "line": getattr(node, "lineno", 1),
                    "col": getattr(node, "col_offset", 0) + 1
                })
            elif isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)):
                args_list = [a.arg for a in node.args.args[:3]]
                if len(node.args.args) > 3:
                    args_list.append("...")
                sig = f"{node.name}({', '.join(args_list)})"
                outline.append({
                    "name": f"def {sig}",
                    "kind": "function",
                    "line": getattr(node, "lineno", 1),
                    "col": getattr(node, "col_offset", 0) + 1
                })
        outline.sort(key=lambda x: x["line"])
    except SyntaxError as e:
        lineno = e.lineno or 1
        col = e.offset or 1
        end_col = getattr(e, "end_offset", None) or (col + 4)
        diagnostics.append({
            "line": lineno,
            "col": max(1, col),
            "end_col": max(col + 1, end_col),
            "message": f"{e.__class__.__name__}: {e.msg}"
        })
    except Exception:
        pass

    return json.dumps({
        "diagnostics": diagnostics,
        "outline": outline
    }, ensure_ascii=False)


def get_runtime_diagnostics(workspace_dir: str, user_site_packages: str, invalidate_cache: bool = False) -> str:
    """
    Returns TinaIDE-style runtime context diagnostics (Python version, platform/ABI, cwd, sys.path).
    Optionally invalidates importlib caches.
    """
    ensure_paths(user_site_packages, workspace_dir)
    if invalidate_cache:
        importlib.invalidate_caches()

    info = {
        "python_version": sys.version.split()[0],
        "full_version": sys.version,
        "platform": f"{platform.system()} {platform.release()} ({platform.machine()})",
        "cwd": os.getcwd(),
        "workspace_dir": workspace_dir,
        "user_site_packages": user_site_packages,
        "sys_path": list(sys.path),
        "loaded_modules_count": len(sys.modules)
    }
    return json.dumps(info, ensure_ascii=False)
