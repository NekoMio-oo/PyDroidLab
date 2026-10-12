"""Interactive REPL with persistent globals, driven by Kotlin one line at a time.

Usage from Kotlin:
    repl = pdl_repl.Repl()
    more_needed = repl.push(source_line)   # returns True if block incomplete
Output flows through the bridged stdout/stderr.
"""

import codeop
import sys
import traceback

import pdl_runtime


class Repl:
    def __init__(self):
        self.globals = {"__name__": "__console__", "__builtins__": __builtins__}
        self._buffer = []
        pdl_runtime.install_stdio()
        pdl_runtime.install_matplotlib_bridge()

    def push(self, line):
        """Execute a line (or accumulate a block). Returns True if the block
        is incomplete and more lines are needed."""
        try:
            import pdl_interrupt
            pdl_interrupt.arm()
        except Exception:
            pass
        self._buffer.append(line)
        source = "\n".join(self._buffer)
        try:
            # symbol="single"：让顶层表达式语句在 exec 时走 sys.displayhook，
            # 像原生 REPL 一样回显 repr（Bug 6）。def/class 等语句不受影响，
            # 多行块的不完整判断（返回 None）行为也不变。
            compiled = codeop.compile_command(source, symbol="single")
        except (SyntaxError, OverflowError, ValueError):
            self._buffer = []
            traceback.print_exc()
            return False
        if compiled is None:
            # Incomplete (e.g. unterminated block) -> need more input.
            return True
        self._buffer = []
        try:
            exec(compiled, self.globals)
        except SystemExit:
            pass
        except KeyboardInterrupt:
            sys.stderr.write("\n[Interrupted]\n")
        except BaseException:
            traceback.print_exc()
        return False

    def complete(self, text):
        """Return a list of completion candidates for `text` (rlcompleter)."""
        import rlcompleter
        c = rlcompleter.Completer(self.globals)
        out = []
        i = 0
        while True:
            r = c.complete(text, i)
            if r is None:
                break
            out.append(r)
            i += 1
            if i > 200:
                break
        return out

    def docstring(self, expr):
        """Evaluate an attribute path and return its docstring (long-press help)."""
        try:
            obj = eval(expr, self.globals)
            return getattr(obj, "__doc__", None) or "No documentation."
        except Exception as e:
            return "Error: %s" % e


def make_repl():
    return Repl()
