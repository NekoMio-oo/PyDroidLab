"""第 7 课示例：异常处理与调试。

文件里有一处**故意留下的 bug**（calculate_average 的下标越界），
建议在编辑器里给 `total += values[i]` 那一行打断点，
然后到「调试」页单步执行，观察 i 与 len(values) 的变化。
"""

import json
import os

print("=== 1. 最简单的捕获 ===")
try:
    print(1 / 0)
except ZeroDivisionError as e:
    print("捕获:", type(e).__name__, "-", e)

print("\n=== 2. 多分支 except ===")


def parse_int(text):
    try:
        return int(text)
    except ValueError:
        print(f"  ✗「{text}」不是整数")
    except TypeError:
        print(f"  ✗ 类型错误：{type(text).__name__}")
    return None


for t in ["42", "abc", None, "3.5"]:
    print(f"parse_int({t!r}) -> {parse_int(t)}")

print("\n=== 3. try / except / else / finally ===")


def read_number(text):
    try:
        value = int(text)
    except ValueError:
        print("  转换失败")
        return None
    else:
        print("  转换成功")
        return value
    finally:
        print("  finally 总会出现")


print("结果:", read_number("7"))
print("结果:", read_number("x"))

print("\n=== 4. 主动抛出 + 自定义异常 ===")


class BalanceError(Exception):
    """余额不足。"""


def withdraw(balance, amount):
    if amount <= 0:
        raise ValueError("取款金额必须为正")
    if amount > balance:
        raise BalanceError(f"还差 {amount - balance} 元")
    return balance - amount


for amount in [50, -10, 9999]:
    try:
        print(f"取 {amount}: 余额变为 {withdraw(100, amount)}")
    except (ValueError, BalanceError) as e:
        print(f"取 {amount}: 失败 -> {type(e).__name__}: {e}")

print("\n=== 5. 常见异常速查 ===")
cases = [
    ("NameError", lambda: undefined_name),
    ("TypeError", lambda: "1" + 1),
    ("ValueError", lambda: int("abc")),
    ("IndexError", lambda: [1, 2, 3][10]),
    ("KeyError", lambda: {"a": 1}["b"]),
    ("AttributeError", lambda: (1).not_exist),
    ("ZeroDivisionError", lambda: 1 / 0),
]
for name, fn in cases:
    try:
        fn()
        print(f"  {name:<18} 没有触发")
    except Exception as e:
        print(f"  {name:<18} 实际是 {type(e).__name__}: {e}")

print("\n=== 6. 优雅降级：读取 JSON 配置 ===")


def load_config(path):
    """任何问题都返回一份默认配置，绝不抛出。"""
    default = {"theme": "dark", "font_size": 14}
    if not os.path.exists(path):
        print(f"  · 配置不存在（{path}），使用默认值")
        return default
    try:
        with open(path, "r", encoding="utf-8") as f:
            data = json.load(f)
        if not isinstance(data, dict):
            raise ValueError("配置根节点必须是对象")
        merged = dict(default)
        merged.update(data)
        return merged
    except (json.JSONDecodeError, ValueError) as e:
        print(f"  · 配置损坏（{e}），使用默认值")
        return default
    except OSError as e:
        print(f"  · 读取失败（{e}），使用默认值")
        return default


print("load_config('不存在.json') ->", load_config("不存在.json"))


def save_config(path, data):
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)


here = os.path.dirname(os.path.abspath(__file__))
good = os.path.join(here, "app_config.json")
broken = os.path.join(here, "broken_config.json")

save_config(good, {"theme": "light"})
with open(broken, "w", encoding="utf-8") as f:
    f.write("{ 这不是合法 JSON")

print("正常配置 ->", load_config(good))
print("损坏配置 ->", load_config(broken))

print("\n=== 7. 【故意留的 bug】请用调试器定位 ===")


def calculate_average(values):
    """本函数里的循环多走了一步，会抛 IndexError。

    调试步骤：
      1. 在下面 `total += values[i]` 处打断点
      2. 调试页点「开始调试」
      3. 反复点「下一行」，观察变量监视里的 i 与 values
    """
    total = 0
    i = 0
    while i <= len(values):          # ← 条件应为 i < len(values)
        total += values[i]           # ← 在这里打断点
        i += 1
    return total / len(values)


print("准备调用 calculate_average([10, 20, 30]) …")
try:
    print("平均值 =", calculate_average([10, 20, 30]))
except IndexError as e:
    print("如期捕获到 IndexError:", e)
    print("修好之后应该是 20.0 —— 请把 while 条件改成 i < len(values) 再运行一次")

print("\n=== 示例结束 ===")
