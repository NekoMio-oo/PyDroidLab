"""给函数计时（装饰器 + 上下文管理器两种写法）

适合：找性能瓶颈、对比两种算法。
要点：time.perf_counter() 是高精度单调时钟，比 time.time() 适合计时。
"""

import functools
import time
from contextlib import contextmanager


def timed(func):
    """装饰器：调用后打印耗时。"""

    @functools.wraps(func)          # 保留原函数的名字与文档
    def wrapper(*args, **kwargs):
        start = time.perf_counter()
        try:
            return func(*args, **kwargs)
        finally:
            elapsed = (time.perf_counter() - start) * 1000
            print(f"  ⏱ {func.__name__} 用时 {elapsed:.2f} ms")

    return wrapper


@contextmanager
def timer(label="代码块"):
    """上下文管理器：with timer('xxx'): ..."""
    start = time.perf_counter()
    try:
        yield
    finally:
        elapsed = (time.perf_counter() - start) * 1000
        print(f"  ⏱ {label} 用时 {elapsed:.2f} ms")


@timed
def sum_with_loop(n):
    total = 0
    for i in range(n):
        total += i
    return total


@timed
def sum_with_builtin(n):
    return sum(range(n))


def slow_way(values):
    """O(n^2)：每次都线性查找。"""
    result = []
    for v in values:
        if v not in result:
            result.append(v)
    return result


def fast_way(values):
    """O(n)：用 set 去重（但会丢失顺序）。"""
    return set(values)


def ordered_unique(values):
    """O(n) 且保序：dict 从 3.7 起保序。"""
    return list(dict.fromkeys(values))


if __name__ == "__main__":
    print("=== 装饰器计时 ===")
    sum_with_loop(1_000_000)
    sum_with_builtin(1_000_000)

    print("\n=== 上下文管理器计时 ===")
    with timer("列表推导式"):
        _ = [x * x for x in range(200_000)]

    with timer("生成器求和"):
        _ = sum(x * x for x in range(200_000))

    print("\n=== 真实对比：去重三种写法 ===")
    data = [(i * 7919) % 5000 for i in range(200_000)]

    with timer("慢：list + not in"):
        r1 = slow_way(data)

    with timer("快：set"):
        r2 = fast_way(data)

    with timer("快且保序：dict.fromkeys"):
        r3 = ordered_unique(data)

    print(f"  结果数量：{len(r1)} / {len(r2)} / {len(r3)}")

    print("\n=== 秒表小工具 ===")
    with timer("sleep 0.12s"):
        time.sleep(0.12)
