"""bdb-based debugger engine driven by the Kotlin UI.

The Kotlin side owns a debugger session object and calls:
    dbg = PdlDebugger(host)           # host == pdl_host module
    dbg.set_breakpoint(file, line)
    dbg.start(path, argv, cwd)        # blocking; returns when program ends
While paused, the UI pulls state via get_state() and issues stepping commands
with cmd_step()/cmd_next()/cmd_return()/cmd_continue().
"""

import bdb
import os
import sys
import threading

import pdl_host
import pdl_runtime
import pdl_utils


class PdlDebugger(bdb.Bdb):
    def __init__(self):
        super().__init__()
        self._cmd = threading.Event()
        self._pending = "continue"
        self._state_lock = threading.Lock()
        self._paused_frame = None
        self._stack_cache = []

    # -- command channel ----------------------------------------------------
    def _wait_command(self, frame):
        self._paused_frame = frame
        self._publish_state(frame)
        self._cmd.clear()
        # Block the debuggee thread until the UI issues a command.
        self._cmd.wait()
        self._paused_frame = None
        return self._pending

    def _issue(self, name):
        self._pending = name
        self._cmd.set()

    def cmd_step(self):
        self._issue("step")

    def cmd_next(self):
        self._issue("next")

    def cmd_return(self):
        self._issue("return")

    def cmd_continue(self):
        self._issue("continue")

    # -- bdb hooks ----------------------------------------------------------
    def user_line(self, frame):
        action = self._wait_command(frame)
        if action == "step":
            self.set_step()
        elif action == "next":
            self.set_next(frame)
        elif action == "return":
            self.set_return(frame)
        else:
            self.set_continue()

    def user_call(self, frame, arg_list):
        # Pause on function entry only when stepping.
        if self.stop_here(frame):
            self.user_line(frame)

    def user_return(self, frame, return_value):
        pass

    def user_exception(self, frame, exc_info):
        # Pause on uncaught exceptions so the user can inspect state.
        self._publish_state(frame, exception=repr(exc_info[1]))
        self._wait_command(frame)
        self.set_continue()

    # -- state export --------------------------------------------------------
    def _frame_info(self, frame):
        return {
            "file": frame.f_code.co_filename,
            "line": frame.f_lineno,
            "func": frame.f_code.co_name,
        }

    def _publish_state(self, frame, exception=None):
        stack = []
        f = frame
        while f is not None:
            stack.append(self._frame_info(f))
            f = f.f_back
        stack.reverse()
        locals_ = {}
        try:
            for k, v in frame.f_locals.items():
                try:
                    locals_[k] = repr(v)
                except Exception:
                    locals_[k] = "<unrepr>"
        except Exception:
            pass
        with self._state_lock:
            self._stack_cache = {
                "stack": stack,
                "locals": locals_,
                "exception": exception,
            }
        pdl_host.debug_paused(
            frame.f_code.co_filename, frame.f_lineno)

    def get_state(self):
        with self._state_lock:
            return self._stack_cache

    # -- breakpoints ---------------------------------------------------------
    def add_breakpoint(self, filename, lineno):
        return self.set_break(filename, lineno)

    def remove_breakpoint(self, filename, lineno):
        try:
            self.clear_break(filename, lineno)
        except Exception:
            pass

    def clear_all_breakpoints(self):
        self.clear_all_file_breaks(self._current_file or "")

    _current_file = None

    # -- entry point ---------------------------------------------------------
    def start(self, path, argv, cwd):
        self._current_file = os.path.abspath(path)
        pdl_runtime.install_stdio()
        pdl_runtime.install_matplotlib_bridge()
        try:
            import pdl_interrupt
            pdl_interrupt.arm()
        except Exception:
            pass
        if cwd:
            try:
                os.chdir(pdl_utils.as_str(cwd))
            except OSError:
                pass
        # argv 来自 Kotlin（String[] → Java 数组包装对象），先转成 Python list。
        sys.argv = [path] + pdl_utils.as_str_list(argv)
        script_dir = os.path.dirname(os.path.abspath(path))
        if script_dir not in sys.path:
            sys.path.insert(0, script_dir)
        with open(path, "rb") as f:
            src = f.read()
        code = compile(src, path, "exec")
        globs = {"__name__": "__main__", "__file__": path,
                 "__builtins__": __builtins__}
        try:
            self.run(code, globs, globs)
        except bdb.BdbQuit:
            pass
        except SystemExit:
            pass
        except KeyboardInterrupt:
            sys.stderr.write("\n[Interrupted]\n")
        finally:
            pdl_host.debug_finished()
        return 0


def make_debugger():
    return PdlDebugger()
