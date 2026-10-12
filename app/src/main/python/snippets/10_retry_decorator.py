"""异常重试装饰器（指数退避 + 可选抖动）

适合：网络请求、文件锁竞争、临时性失败的操作。
要点：只对「值得重试」的异常重试；参数错误这类硬失败应该立刻抛出。
"""

import functools
import random
import time


class RetryFailed(Exception):
    """所有重试都失败后抛出，并用 __cause__ 保留最后一次的原始异常。"""


def retry(
    times=3,
    delay=0.5,
    backoff=2.0,
    jitter=0.15,
    exceptions=(Exception,),
    on_retry=None,
):
    """重试装饰器。

    :param times: 总尝试次数（含第一次）
    :param delay: 第一次重试前等待的秒数
    :param backoff: 每次重试等待时间乘的系数
    :param jitter: 随机抖动比例，避免大量任务同时重试
    :param exceptions: 只有这些异常才触发重试
    :param on_retry: 回调 (attempt, exception, wait_seconds)
    """
    if times < 1:
        raise ValueError("times 至少为 1")

    def decorator(func):
        @functools.wraps(func)
        def wrapper(*args, **kwargs):
            wait = delay
            last_exc = None
            for attempt in range(1, times + 1):
                try:
                    return func(*args, **kwargs)
                except exceptions as e:
                    last_exc = e
                    if attempt >= times:
                        break
                    actual = wait * (1 + random.uniform(-jitter, jitter))
                    if on_retry:
                        on_retry(attempt, e, actual)
                    time.sleep(actual)
                    wait *= backoff
            raise RetryFailed(
                f"{func.__name__} 重试 {times} 次后仍失败"
            ) from last_exc

        return wrapper

    return decorator


# ---------------------------------------------------------------- 演示 --

flaky_state = {"n": 0}


@retry(times=4, delay=0.05, backoff=1.8,
       on_retry=lambda a, e, w: print(f"    第 {a} 次失败：{e}，{w:.3f}s 后重试"))
def flaky_call(succeed_on=3):
    """前 succeed_on-1 次必失败，第 succeed_on 次成功。"""
    flaky_state["n"] += 1
    if flaky_state["n"] < succeed_on:
        raise ConnectionError(f"模拟网络抖动（第 {flaky_state['n']} 次）")
    return f"成功！总共调用了 {flaky_state['n']} 次"


@retry(times=3, delay=0.01, exceptions=(ConnectionError, TimeoutError))
def always_fails():
    raise ConnectionError("永远连不上")


@retry(times=5, delay=0.01, exceptions=(ConnectionError,))
def wrong_exception_type():
    """抛的是 ValueError，不在重试白名单里 → 立刻向上抛。"""
    raise ValueError("参数不合法，重试没意义")


if __name__ == "__main__":
    print("=== 1. 最终成功的重试 ===")
    print("  ", flaky_call())

    print("\n=== 2. 全部失败 ===")
    try:
        always_fails()
    except RetryFailed as e:
        print("  捕获 RetryFailed:", e)
        print("  原始异常:", type(e.__cause__).__name__, "-", e.__cause__)

    print("\n=== 3. 不在白名单的异常不重试 ===")
    try:
        wrong_exception_type()
    except ValueError as e:
        print("  直接抛出 ValueError:", e)
    except RetryFailed as e:
        print("  不该走到这里:", e)

    print("\n=== 4. 手写循环版（不用装饰器）===")


def fetch_with_retry(url, tries=3):
    for i in range(1, tries + 1):
        try:
            if i < tries:
                raise TimeoutError("模拟超时")
            return f"第 {i} 次拿到 {url} 的数据"
        except TimeoutError as e:
            print(f"  · 第 {i} 次：{e}")
            time.sleep(0.05 * i)
    raise RetryFailed("重试耗尽")


print("  ", fetch_with_retry("https://example.com/api"))

print("\n=== 5. 给函数加上超时提醒 ===")


def with_deadline(seconds):
    """上下文管理器：跑超时了只提示，不强杀（Python 无法安全打断同步代码）。"""

    class Deadline:
        def __enter__(self):
            self.start = time.perf_counter()
            return self

        def __exit__(self, exc_type, exc, tb):
            used = time.perf_counter() - self.start
            flag = "⚠ 超时" if used > seconds else "✓ 在预算内"
            print(f"  {flag}：用时 {used:.3f}s / 预算 {seconds}s")
            return False

    return Deadline()


with with_deadline(0.5):
    time.sleep(0.1)

with with_deadline(0.05):
    time.sleep(0.1)
