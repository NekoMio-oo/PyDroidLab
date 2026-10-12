"""第 6 课示例：常用标准库综合演示。

无需安装任何第三方包，直接运行。
"""

import itertools
import math
import random
import re
import statistics
import time
from collections import Counter, defaultdict, deque, namedtuple
from datetime import date, datetime, timedelta

print("=== 1. math ===")
print("pi =", math.pi, " e =", math.e)
print("sqrt(16) =", math.sqrt(16), " pow(2,10) =", math.pow(2, 10))
print("floor(3.7) =", math.floor(3.7), " ceil(3.2) =", math.ceil(3.2), " round(3.5) =", round(3.5))
print("sin(pi/2) =", math.sin(math.pi / 2), " log10(1000) =", math.log10(1000))
print("gcd(24,36) =", math.gcd(24, 36), " factorial(5) =", math.factorial(5))
print("0.1+0.2 =", 0.1 + 0.2, " isclose(.., 0.3) =", math.isclose(0.1 + 0.2, 0.3))

print("\n=== 2. datetime / time ===")
now = datetime.now()
print("现在      :", now.strftime("%Y-%m-%d %H:%M:%S"))
print("星期      :", now.strftime("%A"), "weekday =", now.weekday())
parsed = datetime.strptime("2026-10-12 17:51", "%Y-%m-%d %H:%M")
print("解析结果  :", parsed)
print("加一天减两小时 :", parsed + timedelta(days=1, hours=-2))
print("距年底    :", (date(2026, 12, 31) - date.today()).days, "天")

start = time.perf_counter()
time.sleep(0.05)
print(f"计时演示  : {(time.perf_counter() - start) * 1000:.1f} ms")

print("\n=== 3. random（固定种子便于复现）===")
random.seed(42)
print("random()        :", f"{random.random():.6f}")
print("randint(1,6)    :", random.randint(1, 6))
print("randrange(0,100,5):", random.randrange(0, 100, 5))
print("choice          :", random.choice(["石头", "剪刀", "布"]))
print("sample 6 of 49  :", sorted(random.sample(range(1, 50), 6)))
deck = list(range(10))
random.shuffle(deck)
print("shuffle         :", deck)

print("\n=== 4. collections ===")
words = "a b a c b a".split()
counter = Counter(words)
print("Counter         :", counter)
print("most_common(2)  :", counter.most_common(2))

groups = defaultdict(list)
for name, dept in [("小明", "开发"), ("小红", "测试"), ("小刚", "开发")]:
    groups[dept].append(name)
print("defaultdict     :", dict(groups))

q = deque([1, 2, 3])
q.appendleft(0)
q.pop()
print("deque           :", list(q))

Point = namedtuple("Point", "x y")
p = Point(3, 5)
print("namedtuple      :", p, "-> x =", p.x, " y =", p.y)

print("\n=== 5. re ===")
text = "订单 A1234 金额 88.50 元，订单 B5678 金额 199 元"
print("findall         :", re.findall(r"[AB]\d{4}", text))
print("search 小数     :", re.search(r"\d+\.\d+", text).group())
print("sub 数字→#      :", re.sub(r"\d+", "#", text))
m = re.search(r"([AB])(\d{4})", text)
print("分组            :", m.group(0), m.group(1), m.group(2))

phones = "联系 13812345678 或 19900001111"
print("手机号          :", re.findall(r"1[3-9]\d{9}", phones))

print("\n=== 6. itertools ===")
print("chain           :", list(itertools.chain([1, 2], [3], [4, 5])))
print("islice count    :", list(itertools.islice(itertools.count(10, 3), 5)))
print("combinations    :", list(itertools.combinations("abc", 2)))
print("product         :", list(itertools.product([0, 1], repeat=2)))
print("groupby         :", [(k, "".join(g)) for k, g in itertools.groupby("aabbcc")])

print("\n=== 7. statistics ===")
data = [7, 2, 9, 4, 9, 5]
print("mean            :", statistics.mean(data))
print("median          :", statistics.median(data))
print("mode            :", statistics.mode(data))
print("stdev           :", f"{statistics.stdev(data):.3f}")

print("\n=== 8. 小应用：石头剪刀布模拟 1000 局 ===")
random.seed(7)
options = ["石头", "剪刀", "布"]
win = lose = draw = 0
for _ in range(1000):
    me = random.choice(options)
    cpu = random.choice(options)
    if me == cpu:
        draw += 1
    elif (me == "石头" and cpu == "剪刀") or (me == "剪刀" and cpu == "布") or (me == "布" and cpu == "石头"):
        win += 1
    else:
        lose += 1
print(f"胜 {win}  负 {lose}  平 {draw}  胜率 {win / 1000:.1%}")

print("\n=== 示例结束 ===")
