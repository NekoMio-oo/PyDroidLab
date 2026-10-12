"""
PyDroidLab runtime bootstrap.

This module is imported by the Kotlin host (via Chaquopy) before any user code
runs.  It installs:

  * stdin/stdout/stderr redirection so input()/print() flow through the
    Android console UI instead of a real POSIX terminal;
  * the matplotlib Agg bridge that renders every figure to PNG and forwards it
    to the "Charts" panel;
  * small helpers used by the debugger / REPL.

Everything here runs on the *Python* side of the Chaquopy bridge.  The Kotlin
side supplies concrete implementations of the `host` object (see HostBridge.kt)
so we stay decoupled from Android APIs.
"""

import io
import os
import sys
import threading
import traceback

# The Kotlin host injects a `pdl_host` module attribute before importing us.
# It exposes: write_stdout(str), write_stderr(str), read_stdin() -> str,
#             on_chart(path, title), host_dir() -> str
import pdl_host  # type: ignore  # provided at runtime by the Kotlin bridge


# --------------------------------------------------------------------------
# stdio redirection
# --------------------------------------------------------------------------
class _ConsoleOut(io.TextIOBase):
    def __init__(self, is_err=False):
        self._is_err = is_err

    def writable(self):
        return True

    def write(self, s):
        if not s:
            return 0
        if self._is_err:
            pdl_host.write_stderr(str(s))
        else:
            pdl_host.write_stdout(str(s))
        return len(s)

    def flush(self):
        pass


class _ConsoleIn(io.TextIOBase):
    """stdin backed by a blocking call into the Android console."""

    def readable(self):
        return True

    def readline(self, size=-1):
        # Blocks until the user submits a line in the console UI.
        line = pdl_host.read_stdin()
        if line is None:
            raise KeyboardInterrupt()
        if not line.endswith("\n"):
            line += "\n"
        return line


_installed = False


def install_stdio():
    global _installed
    if _installed:
        return
    sys.stdout = _ConsoleOut(False)
    sys.stderr = _ConsoleOut(True)
    sys.stdin = _ConsoleIn()
    _installed = True


def uninstall_stdio():
    global _installed
    sys.stdout = sys.__stdout__
    sys.stderr = sys.__stderr__
    sys.stdin = sys.__stdin__
    _installed = False


# --------------------------------------------------------------------------
# matplotlib bridge
# --------------------------------------------------------------------------
def install_matplotlib_bridge():
    """Force the Agg backend and reroute plt.show() to the Charts panel."""
    try:
        import matplotlib
        matplotlib.use("Agg")
        import matplotlib.pyplot as plt
    except Exception:
        return  # matplotlib not installed yet; nothing to bridge

    chart_dir = os.path.join(pdl_host.host_dir(), "charts")
    os.makedirs(chart_dir, exist_ok=True)
    _counter = {"n": 0}
    _lock = threading.Lock()

    def _show(*args, **kwargs):
        try:
            with _lock:
                _counter["n"] += 1
                n = _counter["n"]
            path = os.path.join(chart_dir, "chart_%d.png" % n)
            fig = plt.gcf()
            title = "Figure %d" % n
            try:
                # Use the first axes title if present for a friendlier label.
                ax = fig.axes[0] if fig.axes else None
                if ax is not None and ax.get_title():
                    title = ax.get_title()
            except Exception:
                pass
            fig.savefig(path, dpi=150, bbox_inches="tight")
            pdl_host.on_chart(path, title)
        except Exception:
            traceback.print_exc()
        finally:
            plt.close("all")

    plt.show = _show
    # Also bridge savefig-less draw callers.
    try:
        plt.ion = lambda: None
        plt.ioff = lambda: None
    except Exception:
        pass


def host_dir():
    return pdl_host.host_dir()


def format_exc():
    return traceback.format_exc()
