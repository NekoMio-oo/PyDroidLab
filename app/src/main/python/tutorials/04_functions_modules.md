# 第 4 课：函数与模块

## 本课目标

- 定义函数、传参、返回值
- 理解位置参数、关键字参数、默认值、`*args` / `**kwargs`
- 把代码拆成多个 `.py` 文件并通过 `import` 组合起来

## 4.1 定义与调用

```python
def greet(name):
    """给名字加上问候语并返回。"""
    return f"你好，{name}！"

message = greet("小明")
print(message)
```

- `def` 定义函数，函数名后面是参数表，冒号后缩进是函数体
- 没有 `return` 的函数返回 `None`
- 函数体第一句字符串是**文档字符串**（docstring），用 `help(greet)` 可以查看

在 PyDroidLab 编辑器里，**长按一个内置函数名**可以弹出它的文档。

## 4.2 参数的各种玩法

```python
def f(a, b=2, *args, **kwargs):
    print("a =", a)
    print("b =", b)          # 有默认值，可不传
    print("args =", args)    # 多余的按位置传进来的参数，是个 tuple
    print("kwargs =", kwargs)  # 多余的按名字传进来的参数，是个 dict

f(1)
f(1, 3)
f(1, 3, 5, 7, name="x")
```

调用时按名字传参可读性更好：

```python
def make_user(name, age, city="未知"):
    return {"name": name, "age": age, "city": city}

make_user("小红", 20)                      # 位置参数
make_user(age=20, name="小红")             # 关键字参数，顺序随意
make_user("小刚", 30, city="上海")
```

> 默认值只在函数**定义时**求值一次，所以不要用可变对象（如 `[]`）当默认值：
>
> ```python
> def bad(x, acc=[]):     # ✗ 多次调用会共享同一个列表
>     acc.append(x); return acc
>
> def good(x, acc=None):  # ✓
>     if acc is None: acc = []
>     acc.append(x); return acc
> ```

## 4.3 多返回值

`return a, b` 实际上是返回一个元组，调用方用解包接收：

```python
def min_max(values):
    return min(values), max(values)

low, high = min_max([3, 9, 1])
print(low, high)     # 1 9
```

## 4.4 作用域

```python
total = 0

def add(n):
    global total      # 声明要修改的是模块级变量
    total += n

add(5)
print(total)          # 5
```

函数内部赋值默认创建**局部变量**。能不用 `global` 就不用，把值通过参数传进去、
通过返回值传出来是更好的做法。

## 4.5 模块与 import

假设项目里有两个文件：

```
my_project/
├── main.py
└── utils.py
```

`utils.py`：

```python
def double(x):
    return x * 2
```

`main.py`：

```python
import utils                     # 导入整个模块
print(utils.double(21))          # 42

from utils import double         # 只导入一个名字
print(double(21))

import utils as u                # 起别名
print(u.double(21))
```

关键点：`pdl_runner` 会把**脚本所在目录**自动加入 `sys.path`，
所以在项目目录里新建的 `.py` 文件可以直接互相 `import`。
如果放在子目录里，那么子目录需要有一个 `__init__.py` 才会被当成包：

```
my_project/
├── main.py
└── lib/
    ├── __init__.py
    └── helpers.py
```

```python
from lib.helpers import something
```

## 4.6 `if __name__ == "__main__":`

被 `import` 时，模块里的顶层代码会立刻执行。把「只有直接运行才该做的事」
放进这个判断里，就可以安全地被导入：

```python
def main():
    print("程序入口")

if __name__ == "__main__":
    main()
```

编辑器「新建 → 脚本模板」生成的就是这个骨架。

## 4.7 动手练习

1. 写 `is_prime(n)` 判断质数，并打印 100 以内的所有质数。
2. 写 `stats(values)` 返回 `(最小值, 最大值, 平均值)`，用解包接收。
3. 建一个 `mytools.py`，在里面写两个函数；再建 `main.py` 导入并使用它们。

## 下一课

`05_files` 讲文件读写与路径处理。

---

### 本课示例

`04_functions_modules_example.py` 综合演示了以上所有参数形式与作用域规则。
