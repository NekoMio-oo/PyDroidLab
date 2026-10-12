"""第 4 课示例：函数与模块。

演示：默认参数、*args/**kwargs、多返回值、作用域、闭包、递归。
"""

print("=== 1. 基本函数与 docstring ===")


def greet(name, greeting="你好"):
    """返回带问候语的一行文字。

    :param name: 称呼
    :param greeting: 问候语前缀
    """
    return f"{greeting}，{name}！"


print(greet("小明"))
print(greet("小红", greeting="早上好"))
print("docstring:", greet.__doc__.splitlines()[0])
help(greet)

print("\n=== 2. 各种参数形式 ===")


def showcase(a, b=2, *args, **kwargs):
    print(f"  a       = {a!r}")
    print(f"  b       = {b!r}")
    print(f"  args    = {args!r}   (tuple)")
    print(f"  kwargs  = {kwargs!r} (dict)")


print("showcase(1)")
showcase(1)
print("showcase(1, 3, 5, 7, name='x', debug=True)")
showcase(1, 3, 5, 7, name="x", debug=True)

print("\n=== 3. 多返回值（其实是元组解包）===")


def min_max_avg(values):
    return min(values), max(values), sum(values) / len(values)


low, high, avg = min_max_avg([7, 2, 9, 4])
print(f"min={low}  max={high}  avg={avg:.2f}")

result = min_max_avg([7, 2, 9, 4])
print("不做解包时是一个 tuple：", result)

print("\n=== 4. 默认值的经典陷阱 ===")


def bad_append(x, acc=[]):
    acc.append(x)
    return acc


def good_append(x, acc=None):
    if acc is None:
        acc = []
    acc.append(x)
    return acc


print("bad_append(1) ->", bad_append(1))
print("bad_append(2) ->", bad_append(2), "  ← 列表被共享了！")
print("good_append(1) ->", good_append(1))
print("good_append(2) ->", good_append(2), " ← 正确")

print("\n=== 5. 作用域 ===")

counter = {"n": 0}  # 用字典避免 global


def bump():
    counter["n"] += 1


bump()
bump()
print("counter =", counter["n"])


def make_multiplier(k):
    """闭包：返回一个记住了 k 的函数。"""

    def multiply(x):
        return x * k

    return multiply


double = make_multiplier(2)
triple = make_multiplier(3)
print(f"double(10) = {double(10)}  triple(10) = {triple(10)}")

print("\n=== 6. 递归 ===")


def factorial(n):
    if n <= 1:
        return 1
    return n * factorial(n - 1)


for i in range(1, 8):
    print(f"{i}! = {factorial(i)}")

print("\n=== 7. 高阶函数 ===")
nums = [1, 2, 3, 4, 5, 6]
print("filter 偶数 :", list(filter(lambda x: x % 2 == 0, nums)))
print("map 平方    :", list(map(lambda x: x * x, nums)))
print("sorted 逆序 :", sorted(nums, key=lambda x: -x))
print("sum 求和    :", sum(nums))
print("any 有偶数  :", any(x % 2 == 0 for x in nums))
print("all 都正数  :", all(x > 0 for x in nums))

print("\n=== 8. 类型标注（不影响运行，但编辑器/工具会读）===")


def area(width: float, height: float) -> float:
    return width * height


print("area(3, 4) =", area(3, 4))
print("area.__annotations__ =", area.__annotations__)

print("\n=== 示例结束 ===")
