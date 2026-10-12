# 第 7 课：异常处理与调试

## 本课目标

- 用 `try / except / else / finally` 写出健壮的代码
- 认识常见异常类型
- 会用 PyDroidLab 的「调试」页打断点、单步、看变量

## 7.1 为什么需要异常处理

程序遇到错误默认会**中断并打印回溯**：

```python
print(1 / 0)
```

```
Traceback (most recent call last):
  File "main.py", line 1, in <module>
    print(1 / 0)
ZeroDivisionError: division by zero
```

读回溯的方法：**从最后一行往上读**。最后一行是错误类型与描述，
往上是出错的文件、行号与源码行。

## 7.2 try / except

```python
try:
    n = int(input("请输入一个整数："))
    print(10 / n)
except ValueError:
    print("这不是整数")
except ZeroDivisionError:
    print("不能除以零")
except Exception as e:
    print(f"其它错误：{type(e).__name__}: {e}")
```

- `except` 可以写多个，按顺序匹配，第一个匹配的生效
- `except Exception as e` 是兜底，能捕到绝大多数错误
- **不要用裸 `except:`**，它会连 `KeyboardInterrupt` 一起吞掉，让「中断」按钮失效

## 7.3 else 与 finally

```python
def read_number(text):
    try:
        value = int(text)
    except ValueError:
        print("转换失败")
        return None
    else:
        print("转换成功")      # 没出异常时执行
        return value
    finally:
        print("无论如何都会执行")   # 常用于释放资源
```

## 7.4 主动抛异常

```python
def set_age(age):
    if not isinstance(age, int):
        raise TypeError("age 必须是整数")
    if age < 0 or age > 150:
        raise ValueError(f"age 超出范围：{age}")
    return age

try:
    set_age(-5)
except ValueError as e:
    print("被拒:", e)
```

自定义异常：

```python
class BalanceError(Exception):
    """余额不足。"""

try:
    raise BalanceError("还差 20 元")
except BalanceError as e:
    print("业务异常:", e)
```

## 7.5 常见异常速查

| 异常 | 典型原因 |
| --- | --- |
| `SyntaxError` | 语法写错（少冒号、括号不配对） |
| `IndentationError` | 缩进不一致 |
| `NameError` | 变量没定义 / 拼错 |
| `TypeError` | 类型不对，如 `"1" + 1` |
| `ValueError` | 类型对但值不对，如 `int("abc")` |
| `IndexError` | 列表下标越界 |
| `KeyError` | 字典键不存在 |
| `AttributeError` | 对象没有这个属性 |
| `FileNotFoundError` | 文件不存在 |
| `ZeroDivisionError` | 除以零 |
| `ImportError` | 模块没装或名字写错 |
| `RecursionError` | 递归太深（没写终止条件） |

## 7.6 用「调试」页定位问题

比 `print` 更有效的方式是打断点。

**准备**

1. 在「编辑器」里写好脚本并**保存**（未保存的新标签没有文件路径，无法打断点）
2. 把光标放到想停下的那一行，点工具栏的「断点」
   （也可以到「调试」页 → 断点标签页 → 手动填文件路径 + 行号）

**调试**

1. 「调试」页 → 点「开始调试」
2. 程序运行到断点时会暂停，顶部状态栏显示 `已暂停于 文件名:行号`
3. 用这些按钮控制：
   - **继续**：跑到下一个断点或结束
   - **单步**：进入函数内部（step into）
   - **下一行**：把当前行当作一步执行完，不进入函数（step over）
   - **跳出**：执行完当前函数并返回（step out）
   - **停止**：结束会话

**观察**

- 「变量」页：当前帧的全部局部变量；也可以在上面输入变量名把它「关注」起来
- 「调用栈」页：从入口到当前位置的调用链，每层都带文件名与行号
- 如果抛了未捕获异常，会在「变量」页顶部显示异常信息

**练习**：写一个递归函数，比如 `factorial(5)`，在递归那一行打断点，
然后反复点「下一行」，观察 `n` 是怎么变化的。

## 7.7 动手练习

1. 写一个 `safe_div(a, b)`，除数为 0 时返回 `None` 并打印提示。
2. 写一个读取 JSON 配置的函数，文件不存在 / 内容非法时都能优雅降级。
3. 写一个带 bug 的循环（例如下标多走一位），用断点找出来。

## 下一课

`08_numpy_matplotlib` 讲科学计算与绘图（图表会直接显示在「图表」页）。

---

### 本课示例

`07_errors_debug_example.py` 里有一处**故意留下的索引越界 bug**，
建议直接按第 7.6 节的步骤打断点定位它。
