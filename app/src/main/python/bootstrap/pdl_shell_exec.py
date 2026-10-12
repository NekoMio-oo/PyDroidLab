"""终端里执行 Python 代码的小工具。

与 `pdl_runner` 的区别：这里把 stdout / stderr 捕获成一个字符串一次性返回，
方便内置终端把它渲染到 VT100 模拟器里。也正因为如此，`input()` 在这种模式下
不可用 —— 需要交互输入请到「控制台」运行脚本（那边走 pdl_host 的 stdin 桥）。

同时仍然安装 matplotlib 桥，所以在终端里 `python -c "import matplotlib..."` 画的图
一样会出现在「图表」面板。
"""

import contextlib
import io
import os
import runpy
import sys
import traceback

import pdl_runtime
import pdl_utils

_last_status = {"code": 0}


def _capture(fn):
    """运行 fn()，返回合并后的 stdout + stderr 文本。"""
    out = io.StringIO()
    err = io.StringIO()
    # 终端里执行的代码同样允许被「中断」打断
    try:
        import pdl_interrupt
        pdl_interrupt.arm()
    except Exception:
        pass
    # pdl_runtime 可能已经把 sys.stdout 换成了 pdl_host 桥；这里先记下再替换回来。
    old_out, old_err = sys.stdout, sys.stderr
    code = 0
    try:
        sys.stdout, sys.stderr = out, err
        with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
            fn()
    except SystemExit as e:
        code = e.code if isinstance(e.code, int) else 0
    except KeyboardInterrupt:
        err.write("\n[Interrupted]\n")
        code = 130
    except BaseException:
        err.write(traceback.format_exc())
        code = 1
    finally:
        sys.stdout, sys.stderr = old_out, old_err
    _last_status["code"] = code
    return out.getvalue() + err.getvalue()


def run_code(code, filename="<terminal>"):
    try:
        pdl_runtime.install_matplotlib_bridge()
    except Exception:
        pass

    def _f():
        globs = {"__name__": "__main__", "__builtins__": __builtins__}
        exec(compile(code, filename, "exec"), globs, globs)

    return _capture(_f)


def run_file(path, argv=None):
    try:
        pdl_runtime.install_matplotlib_bridge()
    except Exception:
        pass

    def _f():
        old_argv = sys.argv
        sys.argv = [path] + pdl_utils.as_str_list(argv)
        script_dir = os.path.dirname(os.path.abspath(path))
        if script_dir and script_dir not in sys.path:
            sys.path.insert(0, script_dir)
        try:
            runpy.run_path(path, run_name="__main__")
        finally:
            sys.argv = old_argv

    return _capture(_f)


def run_command(args):
    """给 `python ...` 用：支持 -c / 脚本路径 / 版本号查询。"""
    argv = pdl_utils.as_str_list(args)
    if not argv:
        _last_status["code"] = 2
        return "usage: python [-c code | file.py [args...]]\n"

    if argv[0] in ("-V", "--version"):
        _last_status["code"] = 0
        return "Python %s\n" % sys.version.split()[0]

    if argv[0] == "-c":
        if len(argv) < 2:
            _last_status["code"] = 2
            return "python: -c 需要一个代码参数\n"
        return run_code(argv[1], "<string>")

    if argv[0] == "-m":
        if len(argv) < 2:
            _last_status["code"] = 2
            return "python: -m 需要一个模块名\n"
        mod = argv[1]
        return run_code("import runpy; runpy.run_module(%r, run_name='__main__')" % mod,
                        "<run_module:%s>" % mod)

    path = argv[0]
    if not os.path.isabs(path):
        path = os.path.abspath(path)
    if not os.path.exists(path):
        _last_status["code"] = 2
        return "python: 找不到文件 %s\n" % path
    return run_file(path, argv[1:])


def run_source(source):
    """执行一段源码字符串（终端里极少用，留给扩展）。"""
    return run_code(source, "<terminal>")


def set_cwd(path):
    """让 Python 侧的当前工作目录与内置终端保持一致。"""
    try:
        if path and os.path.isdir(path):
            os.chdir(path)
            return True
    except Exception:
        pass
    return False


def run_pip(args, mirror="pypi"):
    """捕获 pip 的输出一次性返回（终端里用）。"""
    import subprocess

    argv = pdl_utils.as_str_list(args)
    cmd = [sys.executable, "-m", "pip"] + argv
    if mirror == "tsinghua":
        cmd += ["-i", "https://pypi.tuna.tsinghua.edu.cn/simple"]
    elif mirror == "pypi":
        cmd += ["-i", "https://pypi.org/simple"]
    try:
        proc = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        _last_status["code"] = proc.returncode
        return proc.stdout.decode("utf-8", "replace")
    except Exception:
        _last_status["code"] = 1
        return traceback.format_exc()


def last_status():
    return int(_last_status["code"])


def python_version():
    return sys.version.split()[0]
