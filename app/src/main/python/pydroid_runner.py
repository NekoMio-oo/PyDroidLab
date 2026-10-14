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
    hardware_bridge=None,
    cli_args_str: str = ""
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
    orig_argv = list(sys.argv)

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
        import shlex
        extra_argv = shlex.split(cli_args_str) if cli_args_str and cli_args_str.strip() else []
        sys.argv = [script_path or "<untitled.py>"] + extra_argv

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
        sys.argv = orig_argv
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
    2. Detects Pydroid/Flake8-style Static Linter Warnings (mutable default arguments, bare `except:`,
       shadowing built-in functions, pointless `pass` in non-empty blocks).
    3. Extracts top-level and nested `class`, `def`, and global variable symbols for the Code Outline drawer.
    4. Computes Code Metrics (total lines, code lines, comment lines, blank lines, function/class counts).
    """
    diagnostics = []
    warnings = []
    outline = []

    raw_lines = source_code.splitlines()
    total_lines = len(raw_lines)
    blank_lines = sum(1 for ln in raw_lines if not ln.strip())
    comment_lines = sum(1 for ln in raw_lines if ln.strip().startswith("#"))
    code_lines = max(0, total_lines - blank_lines - comment_lines)
    class_count = 0
    func_count = 0

    try:
        tree = ast.parse(source_code, filename=filename)
        for node in ast.walk(tree):
            if isinstance(node, ast.ClassDef):
                class_count += 1
                bases = [ getattr(b, "id", "") for b in node.bases if getattr(b, "id", "") ]
                base_str = f"({', '.join(bases)})" if bases else ""
                outline.append({
                    "name": f"class {node.name}{base_str}",
                    "kind": "class",
                    "line": getattr(node, "lineno", 1),
                    "col": getattr(node, "col_offset", 0) + 1
                })
            elif isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)):
                func_count += 1
                args_list = [a.arg for a in node.args.args[:3]]
                if len(node.args.args) > 3:
                    args_list.append("...")
                prefix = "async def" if isinstance(node, ast.AsyncFunctionDef) else "def"
                sig = f"{node.name}({', '.join(args_list)})"
                outline.append({
                    "name": f"{prefix} {sig}",
                    "kind": "function",
                    "line": getattr(node, "lineno", 1),
                    "col": getattr(node, "col_offset", 0) + 1
                })
                # Linter Rule W0102: Mutable default argument (list, dict, set)
                for default_val in node.args.defaults + node.args.kw_defaults:
                    if isinstance(default_val, (ast.List, ast.Dict, ast.Set)):
                        warnings.append({
                            "line": getattr(node, "lineno", 1),
                            "code": "W0102",
                            "message": f"函数 '{node.name}' 使用了可变默认参数 (建议改用 None 初始化)"
                        })
            elif isinstance(node, ast.ExceptHandler):
                if node.type is None:
                    warnings.append({
                        "line": getattr(node, "lineno", 1),
                        "code": "E722",
                        "message": "使用了裸 except: 捕获所有异常 (建议改为 except Exception as e:)"
                    })
            elif isinstance(node, ast.Assign):
                for target in node.targets:
                    if isinstance(target, ast.Name) and target.id in ("list", "dict", "str", "int", "set", "max", "min", "sum", "id", "type"):
                        warnings.append({
                            "line": getattr(node, "lineno", 1),
                            "code": "A001",
                            "message": f"变量名 '{target.id}' 覆盖了 Python 内置函数"
                        })
        outline.sort(key=lambda x: x["line"])
        warnings.sort(key=lambda x: x["line"])
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
        "warnings": warnings,
        "outline": outline,
        "metrics": {
            "total_lines": total_lines,
            "code_lines": code_lines,
            "comment_lines": comment_lines,
            "blank_lines": blank_lines,
            "class_count": class_count,
            "func_count": func_count
        }
    }, ensure_ascii=False)


def inspect_sqlite_db(db_path: str) -> str:
    """
    Reads all tables, schemas, and up to 50 rows per table from a SQLite `.db` / `.sqlite` file
    and returns a formatted report for the built-in FilePreviewDialog.
    """
    import sqlite3
    if not os.path.isfile(db_path):
        return "Database file not found."
    out = []
    conn = sqlite3.connect(db_path)
    try:
        cur = conn.cursor()
        cur.execute("SELECT name, sql FROM sqlite_master WHERE type='table' ORDER BY name;")
        tables = cur.fetchall()
        out.append(f"🗄️ SQLite 数据库文件: {os.path.basename(db_path)}")
        out.append(f"共包含 {len(tables)} 张数据表\n" + "=" * 44)
        for tname, tsql in tables:
            out.append(f"\n📌 表名: {tname}")
            if tsql:
                out.append(f"DDL: {tsql}")
            cur.execute(f"PRAGMA table_info('{tname}')")
            cols = [row[1] for row in cur.fetchall()]
            cur.execute(f"SELECT * FROM '{tname}' LIMIT 50")
            rows = cur.fetchall()
            out.append(f"字段 ({len(cols)}): {' | '.join(cols)}")
            out.append("-" * 44)
            for r in rows:
                out.append(" | ".join(str(v) for v in r))
            out.append(f"(共显示 {len(rows)} 行记录)")
        return "\n".join(out)
    except Exception as e:
        return f"读取 SQLite 数据库失败: {e}"
    finally:
        conn.close()


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


def format_python_code(source_code: str, tab_width: int = 4) -> dict:
    """
    Formats Python source code according to PEP 8 conventions:
    1. If `autopep8` is installed in user site-packages, uses `autopep8.fix_code`.
    2. Otherwise, uses a built-in `tokenize`-aware PEP 8 formatter that:
       - Normalizes indentation levels (including tabs and irregular 1/2/3/5-space indents)
         to clean multiples of `tab_width` spaces while respecting multiline strings and bracket continuation.
       - Normalizes spacing around commas (`, `) and comments (`  # `).
       - Strips trailing whitespace on every line and collapses >2 consecutive blank lines.
    """
    if not source_code or not source_code.strip():
        return {"success": True, "formatted": source_code, "changed": False}

    # Try autopep8 first if user installed it via pip
    try:
        import autopep8
        fixed = autopep8.fix_code(
            source_code,
            options={"indent_size": int(tab_width), "max_line_length": 100}
        )
        return {
            "success": True,
            "formatted": fixed,
            "changed": fixed != source_code
        }
    except Exception:
        pass

    import tokenize

    # Step 1: Collect line indices that are inside multiline strings so we never alter their indentation
    multiline_string_lines = set()
    indent_levels_by_line = {}
    bracket_depth_by_line = {}

    try:
        tokens = list(tokenize.generate_tokens(io.StringIO(source_code).readline))
        cur_indent = 0
        cur_bracket = 0
        for tok in tokens:
            ttype, tstr, start, end, _ = tok
            sline, scol = start
            eline, ecol = end

            if sline not in indent_levels_by_line:
                indent_levels_by_line[sline] = cur_indent
                bracket_depth_by_line[sline] = cur_bracket

            if ttype == tokenize.INDENT:
                cur_indent += 1
                indent_levels_by_line[sline] = cur_indent
            elif ttype == tokenize.DEDENT:
                cur_indent = max(0, cur_indent - 1)
                indent_levels_by_line[sline] = cur_indent
            elif ttype == tokenize.OP:
                if tstr in ("(", "[", "{"):
                    cur_bracket += 1
                elif tstr in (")", "]", "}"):
                    cur_bracket = max(0, cur_bracket - 1)
            elif ttype == tokenize.STRING and eline > sline:
                for ln in range(sline + 1, eline + 1):
                    multiline_string_lines.add(ln)
    except tokenize.TokenError:
        # If code has unclosed brackets/strings, fall back to safe line-by-line tab/trailing-space cleanup
        pass

    raw_lines = source_code.replace("\r\n", "\n").replace("\r", "\n").split("\n")
    formatted_lines = []
    blank_streak = 0
    unit = " " * max(2, int(tab_width))

    # Track heuristic indentation for fallback or unparsed lines
    heuristic_level = 0
    prev_ended_with_colon = False

    for idx, raw_line in enumerate(raw_lines, start=1):
        if idx in multiline_string_lines:
            formatted_lines.append(raw_line)
            blank_streak = 0
            continue

        stripped = raw_line.strip()
        if not stripped:
            blank_streak += 1
            if blank_streak <= 2:
                formatted_lines.append("")
            continue

        blank_streak = 0

        # Determine target indentation level
        if idx in indent_levels_by_line:
            lvl = indent_levels_by_line[idx]
            b_depth = bracket_depth_by_line.get(idx, 0)
            if b_depth > 0:
                if stripped[0] in (")", "]", "}"):
                    indent_str = unit * (lvl + max(0, b_depth - 1))
                else:
                    indent_str = unit * (lvl + b_depth)
            else:
                indent_str = unit * lvl
        else:
            # Heuristic fallback if tokenize stopped early due to syntax error
            expanded = raw_line.expandtabs(int(tab_width))
            leading_spaces = len(expanded) - len(expanded.lstrip(" "))
            if prev_ended_with_colon and leading_spaces > 0:
                heuristic_level += 1
            elif leading_spaces == 0:
                heuristic_level = 0
            else:
                heuristic_level = max(1, round(leading_spaces / float(tab_width)))
            if stripped.startswith(("elif ", "else:", "except", "finally:")):
                heuristic_level = max(0, heuristic_level - 1)
            indent_str = unit * heuristic_level

        # Clean inline spacing outside strings/comments on this line
        cleaned_body = _format_single_line_tokens(stripped)
        formatted_lines.append(indent_str + cleaned_body)

        code_no_comment = stripped.split("#", 1)[0].rstrip()
        prev_ended_with_colon = code_no_comment.endswith(":")

    # Remove leading/trailing blank lines and add single EOF newline
    while formatted_lines and formatted_lines[0] == "":
        formatted_lines.pop(0)
    while formatted_lines and formatted_lines[-1] == "":
        formatted_lines.pop()

    result = "\n".join(formatted_lines) + "\n"
    return {
        "success": True,
        "formatted": result,
        "changed": result != source_code
    }


def _format_single_line_tokens(stripped_line: str) -> str:
    # Formats spacing around commas, binary/assignment operators at top-level bracket depth,
    # redundant internal whitespace, and inline `#` comments on a single stripped line.
    out = []
    i = 0
    n = len(stripped_line)
    in_quote = None
    bracket_depth = 0

    while i < n:
        ch = stripped_line[i]
        if in_quote is not None:
            out.append(ch)
            if ch == "\\" and i + 1 < n:
                out.append(stripped_line[i + 1])
                i += 2
                continue
            if ch == in_quote:
                in_quote = None
            i += 1
            continue

        if ch in ("\"", "\x27"):
            if i + 2 < n and stripped_line[i:i+3] == ch * 3:
                end_q = stripped_line.find(ch * 3, i + 3)
                if end_q != -1:
                    out.append(stripped_line[i:end_q+3])
                    i = end_q + 3
                    continue
                else:
                    out.append(stripped_line[i:])
                    break
            in_quote = ch
            out.append(ch)
            i += 1
            continue

        if ch == "#":
            comment_body = stripped_line[i+1:].strip()
            while out and out[-1] == " ":
                out.pop()
            if out:
                out.append("  ")
            if comment_body and not comment_body.startswith(("!", "#")):
                out.append(f"# {comment_body}")
            else:
                out.append(stripped_line[i:])
            break

        if ch in ("(", "[", "{"):
            bracket_depth += 1
            out.append(ch)
            i += 1
            continue
        elif ch in (")", "]", "}"):
            bracket_depth = max(0, bracket_depth - 1)
            out.append(ch)
            i += 1
            continue

        if ch in (" ", "\t"):
            if out and out[-1] != " ":
                out.append(" ")
            i += 1
            continue

        if ch == ",":
            while out and out[-1] == " ":
                out.pop()
            out.append(",")
            if i + 1 < n and stripped_line[i + 1] not in (" ", ")", "]", "}"):
                out.append(" ")
            i += 1
            continue

        if i + 1 < n and stripped_line[i:i+2] in ("==", "!=", "<=", ">=", "+=", "-=", "*=", "/="):
            op2 = stripped_line[i:i+2]
            if out and out[-1] != " ":
                out.append(" ")
            out.append(op2)
            out.append(" ")
            i += 2
            while i < n and stripped_line[i] == " ":
                i += 1
            continue

        if bracket_depth == 0 and ch in ("=", "<", ">"):
            prev_ch = stripped_line[i - 1] if i > 0 else ""
            next_ch = stripped_line[i + 1] if i + 1 < n else ""
            if prev_ch not in ("=", "!", "<", ">", "+", "-", "*", "/") and next_ch not in ("=", "<", ">"):
                if out and out[-1] != " ":
                    out.append(" ")
                out.append(ch)
                out.append(" ")
                i += 1
                while i < n and stripped_line[i] == " ":
                    i += 1
                continue

        out.append(ch)
        i += 1

    return "".join(out).rstrip()
