# 第 3 课：类型、条件与循环

## 本课目标

- 掌握 list / dict / set / tuple 四种容器
- 熟练使用 if / elif / else 与 for / while
- 认识「可变对象」和「不可变对象」的区别

## 3.1 四种容器

```python
names = ["小明", "小红", "小刚"]           # list：有序、可改
scores = {"小明": 92, "小红": 88}          # dict：键值对
tags = {"python", "android"}              # set：无序、去重
point = (3, 5)                            # tuple：有序、不可改
```

| 类型 | 写法 | 特点 | 典型用途 |
| --- | --- | --- | --- |
| `list` | `[1, 2, 3]` | 有序，可增删改 | 一组同质数据 |
| `tuple` | `(1, 2, 3)` | 有序，不可改 | 固定结构，如坐标 |
| `dict` | `{"a": 1}` | 键→值，快查 | 带名称的数据 |
| `set` | `{1, 2, 3}` | 无序，自动去重 | 去重、集合运算 |

常用操作：

```python
nums = [3, 1, 2]
nums.append(4)          # 末尾追加 -> [3, 1, 2, 4]
nums.sort()             # 就地排序 -> [1, 2, 3, 4]
print(len(nums))        # 4
print(nums[0], nums[-1])   # 1 4（-1 表示最后一个）
print(nums[1:3])        # [2, 3]（切片，含头不含尾）

d = {"a": 1}
d["b"] = 2              # 新增 / 覆盖
print(d.get("c", 0))    # 0（键不存在时返回默认值，比 d["c"] 安全）
for k, v in d.items():
    print(k, v)
```

> 切片 `a[起:止:步长]` 是 Python 的特色：`nums[::-1]` 可以把列表整个反转。

## 3.2 条件

```python
score = 85

if score >= 90:
    grade = "优秀"
elif score >= 60:
    grade = "及格"
else:
    grade = "不及格"

print(grade)
```

Python 里「假值」包括 `False`、`None`、`0`、`0.0`、`""`、`[]`、`{}`、`set()`，
其余都算真值。所以判空可以直接写：

```python
items = []
if not items:
    print("列表为空")
```

三元表达式：

```python
label = "及格" if score >= 60 else "不及格"
```

## 3.3 循环

```python
for i in range(5):          # 0 1 2 3 4
    print(i)

for i in range(1, 10, 2):   # 1 3 5 7 9（起点、终点、步长）
    print(i)

for name in names:
    print(name)

for idx, name in enumerate(names, start=1):   # 带序号
    print(idx, name)

for a, b in zip([1, 2, 3], ["x", "y", "z"]):  # 并行遍历
    print(a, b)
```

`while` 用于「条件成立就一直做」：

```python
total = 0
i = 1
while i <= 100:
    total += i
    i += 1
print(total)
```

两个循环控制关键字：

- `break`：立刻跳出整个循环
- `continue`：跳过本次剩余部分，进入下一轮

还有个好用的小语法 —— `for ... else`：循环没有被 `break` 打断时执行 `else`：

```python
for n in [3, 5, 7]:
    if n % 2 == 0:
        print("找到偶数", n)
        break
else:
    print("列表里没有偶数")
```

## 3.4 推导式

一行完成「生成新列表」：

```python
squares = [x * x for x in range(10)]           # [0, 1, 4, ..., 81]
evens = [x for x in range(20) if x % 2 == 0]   # 带过滤
mapping = {x: x * x for x in range(5)}         # 字典推导
```

推导式比手写 for 循环更短也更快，是 Python 代码里出现频率极高的写法。

## 3.5 可变 vs 不可变（重要坑）

```python
a = [1, 2, 3]
b = a            # b 和 a 指向同一个列表！
b.append(4)
print(a)         # [1, 2, 3, 4] —— a 也被改了

c = a.copy()     # 要复制就显式 copy
c.append(5)
print(a)         # [1, 2, 3, 4] —— 这次 a 没变
```

`int` / `float` / `str` / `tuple` 是不可变的，函数传参时不用担心被改；
`list` / `dict` / `set` 是可变的，传进函数后可能被修改，需要留意。

## 3.6 动手练习

1. 给定一串数字，输出最大值、最小值、平均值、中位数。
2. 统计一句话里每个单词出现的次数（提示：`split()` + `dict`）。
3. 用列表推导式生成 1~100 中所有能被 3 整除的数的平方。

## 下一课

`04_functions_modules` 讲怎么把代码拆成函数和模块。

---

### 本课示例

`03_types_control_example.py` 用一个小型成绩统计程序把这些知识串起来。
