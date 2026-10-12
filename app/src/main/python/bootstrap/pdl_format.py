"""用 black 格式化源码。

- black 未安装 / 源码有语法错误 / 任何内部异常 → 原样返回，
  保证「格式化」按钮永远不会把用户的代码弄丢。
- black 是构建期通过 Chaquopy pip 预装的纯 Python 包。
"""


def available():
    try:
        import black  # noqa: F401
        return True
    except Exception:
        return False


def version():
    try:
        import black
        return getattr(black, "__version__", "unknown")
    except Exception:
        return ""


def format_code(src):
    """返回格式化后的源码；失败时返回原串。"""
    if not src or not src.strip():
        return src
    try:
        import black
    except Exception:
        return src
    try:
        mode = black.Mode()
    except Exception:
        try:
            mode = black.FileMode()
        except Exception:
            return src
    try:
        formatted = black.format_str(src, mode=mode)
        return formatted if formatted else src
    except Exception:
        return src
