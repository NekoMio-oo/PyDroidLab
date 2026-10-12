# 第 6 课：常用标准库

## 本课目标

- 记住几个「不用装包就能用」的模块
- 会用 `datetime` / `random` / `collections` / `math` / `re` / `itertools`
- 会用「REPL」和 `help()` 自己探索标准库

## 6.1 用 REPL 探索

打开「REPL」页，然后：

```
>>> import math
>>> help(math.sqrt)
>>> math.sqrt(2)
1.4142135623730951
```

`help(对象)` 会打印文档；「编辑器」里长按函数名也会弹文档。

## 6.2 math：数学函数

```python
import math

print(math.pi, math.e)
print(math.sqrt(16), math.pow(2, 10))
print(math.floor(3.7), math.ceil(3.2), round(3.5))
print(math.sin(math.pi / 2), math.log(math.e), math.log10(1000))
print(math.gcd(24, 36), math.factorial(5))
print(math.isclose(0.1 + 0.2, 0.3))   # 浮点比较的推荐方式
```

浮点数存在精度问题：`0.1 + 0.2 == 0.30000000000000004`。要精确小数可以用 `decimal`。

## 6.3 datetime：日期时间

```python
from datetime import datetime, date, timedelta

now = datetime.now()
print(now.strftime("%Y-%m-%d %H:%M:%S"))
print(now.year, now.month, now.day, now.weekday())

# 字符串 → 时间
t = datetime.strptime("2026-10-12 17:51", "%Y-%m-%d %H:%M")
print(t)

# 时间运算
print(t + timedelta(days=1, hours=-2))
print((date(2026, 12, 31) - date.today()).days, "天后跨年")

# 计时
import time
start = time.perf_counter()
time.sleep(0.05)
print(f"耗时 {(time.perf_counter() - start) * 1000:.1f} ms")
```

常用格式符：`%Y` 年、`%m` 月、`%d` 日、`%H` 时、`%M` 分、`%S` 秒、`%A` 星期几。

## 6.4 random：随机

```python
import random

random.seed(42)                         # 固定种子 → 结果可复现
print(random.random())                  # [0, 1) 浮点
print(random.randint(1, 6))             # [1, 6] 整数（含两端）
print(random.randrange(0, 100, 5))      # 步长 5
print(random.choice(["石头", "剪刀", "布"]))
print(random.sample(range(1, 50), 6))   # 不重复抽样
deck = list(range(10)); random.shuffle(deck); print(deck)
```

## 6.5 collections：几个好用的容器

```python
from collections import Counter, defaultdict, deque, namedtuple

# 计数
words = "a b a c b a".split()
print(Counter(words))                   # Counter({'a': 3, 'b': 2, 'c': 1})
print(Counter(words).most_common(2))

# 带默认值的字典，省掉 get 判断
groups = defaultdict(list)
for name, dept in [("小明", "开发"), ("小红", "测试"), ("小刚", "开发")]:
    groups[dept].append(name)
print(dict(groups))

# 双端队列：两端插入删除都是 O(1)
q = deque([1, 2, 3])
q.appendleft(0); q.pop()
print(q)

# 轻量值对象
Point = namedtuple("Point", "x y")
p = Point(3, 5)
print(p.x, p.y, p)
```

## 6.6 re：正则

```python
import re

text = "订单 A1234 金额 88.50 元，订单 B5678 金额 199 元"

print(re.findall(r"[AB]\d{4}", text))          # ['A1234', 'B5678']
print(re.search(r"\d+\.\d+", text).group())    # 88.50

# 替换
print(re.sub(r"\d+", "#", "a1b22c333"))

# 分组提取
m = re.search(r"([AB])(\d{4})", text)
if m:
    print(m.group(0), m.group(1), m.group(2))

# 拆分
print(re.split(r"[，,]\s*", "a, b，c"))
```

写正则建议用**原始字符串** `r"..."`，避免 `\d` 被当成转义字符。

## 6.7 itertools：省掉手写循环

```python
import itertools

print(list(itertools.chain([1, 2], [3], [4, 5])))
print(list(itertools.islice(itertools.count(10, 3), 5)))     # 10 13 16 19 22
print(list(itertools.combinations("abc", 2)))
print(list(itertools.product([0, 1], repeat=2)))
print(list(itertools.groupby(sorted("aabbcc"), key=lambda c: c)))
```

## 6.8 其它值得一提的

| 模块 | 用途 |
| --- | --- |
| `os` / `pathlib` | 路径与环境变量 |
| `sys` | `sys.argv`、`sys.path`、`sys.exit()` |
| `json` / `csv` | 结构化数据读写 |
| `argparse` | 命令行参数解析 |
| `urllib` / `requests` | 网络请求（requests 需要安装） |
| `hashlib` | MD5 / SHA 摘要 |
| `statistics` | 均值、中位数、方差 |
| `pprint` | 好看地打印嵌套结构 |
| `traceback` | 手动打印异常堆栈 |

## 6.9 动手练习

1. 用 `random` 写一个「石头剪刀布」，让用户和电脑对战 5 局并统计胜率。
2. 用 `Counter` 统计一段中文文本里出现最多的 5 个字符。
3. 用 `re` 从一段文本里提取所有手机号（`1[3-9]\d{9}`）。

## 下一课

`07_errors_debug` 讲异常处理，并配合「调试」页打断点单步执行。

---

### 本课示例

`06_stdlib_example.py` 把上面这些模块都跑了一遍，可以直接运行看输出。
