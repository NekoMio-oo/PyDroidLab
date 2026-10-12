"""把「用户点了中断」真正传递给正在执行的 Python 字节码。

## 背景

Kotlin 侧 `PythonRunner.interrupt()` 原本只做两件事：

1. `job.cancel()` 取消 Kotlin 协程 —— 对**已经进入 Python 的调用**无效；
2. `HostBridge.cancelStdin()` 解除 `input()` 的阻塞 —— 只对卡在输入时有效。

所以 `while True: pass` 这种纯 CPU 死循环实际上是中断不了的。

CPython 没有公开的「从外部打断某个线程」的 Python 级 API，但 C-API 里有
`PyThreadState_SetAsyncExc`：它会在**目标线程的下一个字节码边界**注入一个
异常。这正是需要「异步中断」时最常用的做法，而且几乎没有运行时开销
（不像 `sys.settrace` 那样逐行回调、拖慢好几倍）。

## 用法

```python
pdl_interrupt.arm()       # 在执行用户代码的那个线程里调用，记录线程 id
pdl_interrupt.request()   # 任意线程（Kotlin 侧）调用，注入 KeyboardInterrupt
```

## 兜底

`arm` / `request` 任何失败都只是返回 False，调用方继续走原有的
「取消协程 + 解除 stdin 阻塞」路径，行为不会比之前更差。
"""

import threading

_target_tid = {"value": 0}

try:
    import ctypes
    _HAS_CTYPES = True
except Exception:  # pragma: no cover - Chaquopy 上 ctypes 总是可用
    ctypes = None
    _HAS_CTYPES = False


def available():
    """当前环境是否支持异步注入异常。"""
    if not _HAS_CTYPES:
        return False
    try:
        return hasattr(ctypes.pythonapi, "PyThreadState_SetAsyncExc")
    except Exception:
        return False


def arm():
    """在执行用户代码的线程里调用，记录目标线程 id。返回该 id。"""
    _target_tid["value"] = threading.get_ident()
    return _target_tid["value"]


def target():
    return _target_tid["value"]


def disarm():
    _target_tid["value"] = 0


def request(exc_type=None):
    """向已 arm 的线程注入异常；成功返回 True。

    中断是「尽力而为」：任何失败都吞掉并返回 False，
    绝不能因为注入机制不可用而让调用方出错。
    """
    tid = _target_tid["value"]
    if not tid or not _HAS_CTYPES:
        return False
    if exc_type is None:
        exc_type = KeyboardInterrupt

    try:
        res = ctypes.pythonapi.PyThreadState_SetAsyncExc(
            ctypes.c_ulong(tid), ctypes.py_object(exc_type)
        )
    except Exception:
        return False

    if res == 0:
        # 目标线程已经结束，没什么可打断的
        return False
    if res > 1:
        # 文档要求：返回值 > 1 表示影响到了多个线程，必须立刻回滚
        try:
            ctypes.pythonapi.PyThreadState_SetAsyncExc(ctypes.c_ulong(tid), None)
        except Exception:
            pass
        return False
    return True
