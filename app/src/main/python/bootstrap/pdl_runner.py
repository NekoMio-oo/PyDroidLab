"""Runs a user script with argv / cwd / env / stdin preset, under the bridged
stdio.  Invoked from Kotlin on a dedicated thread so SIGINT can interrupt it."""

import os
import runpy
import sys

import pdl_host  # injected by Kotlin
import pdl_runtime
import pdl_utils


def run_script(path, argv, cwd, env, stdin_preset):
    pdl_runtime.install_stdio()
    pdl_runtime.install_matplotlib_bridge()

    # 记录当前执行线程 id，让 Kotlin 侧的「中断」能真正打断 CPU 死循环
    try:
        import pdl_interrupt
        pdl_interrupt.arm()
    except Exception:
        pass

    # Apply environment + cwd + argv.
    #
    # `env` / `argv` 来自 Kotlin，Chaquopy 把它们包装成了 Java 对象
    # （Map<String,String> → LinkedHashMap，List<String> → String[]）。
    # 必须先用 pdl_utils 转成 Python 原生容器：Java Map 包装对象没有
    # `.items()`，而且因为没有 `__len__`，`(env or {})` 这种兜底写法对
    # **空 Map 也照样失效**，会直接把 AttributeError 抛到用户面前。
    for k, v in pdl_utils.as_dict(env).items():
        os.environ[str(k)] = str(v)
    if cwd:
        try:
            os.chdir(pdl_utils.as_str(cwd))
        except OSError as e:
            sys.stderr.write("chdir failed: %s\n" % e)
    sys.argv = [path] + pdl_utils.as_str_list(argv)

    # Preload stdin if a preset was provided (feeds the first input() calls).
    if stdin_preset:
        import io as _io
        preset = _io.StringIO(stdin_preset)
        real_readline = sys.stdin.readline

        def _preset_line(size=-1):
            line = preset.readline()
            if line:
                return line
            return real_readline(size)

        sys.stdin.readline = _preset_line

    # Make the script's directory importable, like CPython does.
    script_dir = os.path.dirname(os.path.abspath(path))
    if script_dir not in sys.path:
        sys.path.insert(0, script_dir)

    try:
        runpy.run_path(path, run_name="__main__")
    except SystemExit:
        pass
    except KeyboardInterrupt:
        sys.stderr.write("\n[Interrupted by user]\n")
    except BaseException:
        import traceback
        traceback.print_exc()
    finally:
        sys.stdout.flush()
        sys.stderr.flush()
    return 0
