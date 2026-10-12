"""读取打包进 APK 的教程 / 代码片段文本。

`snippets/` 与 `tutorials/` 同时在 app/build.gradle.kts 里声明为 Chaquopy 的
`extractPackages`，因此它们在设备上是真实存在的文件，可以直接 listdir / open，
而不必依赖 AssetManager。
"""

import os


def _dir_of(package):
    mod = __import__(package)
    path = getattr(mod, "__file__", None)
    if not path:
        raise RuntimeError("package %s has no __file__" % package)
    return os.path.dirname(os.path.abspath(path))


def root_dir(package):
    return _dir_of(package)


def list_files(package, suffix=None):
    """返回包目录下的文件名（已排序，跳过 __ 开头的内部文件）。"""
    directory = _dir_of(package)
    names = sorted(n for n in os.listdir(directory) if not n.startswith("__"))
    names = [n for n in names if os.path.isfile(os.path.join(directory, n))]
    if suffix:
        names = [n for n in names if n.endswith(suffix)]
    return names


def read_text(package, name):
    directory = _dir_of(package)
    target = os.path.join(directory, name)
    with open(target, "r", encoding="utf-8") as f:
        return f.read()


def exists(package, name):
    try:
        return os.path.isfile(os.path.join(_dir_of(package), name))
    except Exception:
        return False
