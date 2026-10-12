# 第 5 课：文件读写与路径

## 本课目标

- 用 `open()` 读写文本与 JSON
- 用 `pathlib` 处理路径，避开手工拼字符串
- 知道脚本的「当前工作目录」是怎么决定的

## 5.1 当前工作目录

PyDroidLab 运行脚本时会把 `cwd` 设为**脚本所在目录**，所以：

```python
open("data.txt", "w")   # 会在脚本旁边创建 data.txt
```

用「文件」页可以看到它，长按/点击即可打开或删除。

> 「终端」页顶部的「工作目录」和终端里 `cd` 的结果也会影响
> 终端里执行的 `python` 命令，但不影响编辑器里点「运行」。

想知道确切位置，可以：

```python
import os
print(os.getcwd())
print(os.path.abspath(__file__))
```

## 5.2 写文件

```python
# 方式一：with 语句（推荐，自动关闭）
with open("hello.txt", "w", encoding="utf-8") as f:
    f.write("第一行\n")
    f.write("第二行\n")

# 方式二：writelines
lines = ["a\n", "b\n", "c\n"]
with open("lines.txt", "w", encoding="utf-8") as f:
    f.writelines(lines)
```

`open` 的常用模式：

| 模式 | 含义 | 文件不存在时 |
| --- | --- | --- |
| `"r"` | 只读（默认） | 报 `FileNotFoundError` |
| `"w"` | 只写，**清空原内容** | 新建 |
| `"a"` | 追加 | 新建 |
| `"x"` | 只写，文件已存在则报错 | 新建 |
| `"rb"` / `"wb"` | 二进制（图片、zip） | 同上 |

在 Android 上写文本文件**一定加上 `encoding="utf-8"`**，否则中文可能乱码。

## 5.3 读文件

```python
# 一次读完（小文件）
with open("hello.txt", "r", encoding="utf-8") as f:
    text = f.read()
print(text)

# 按行读（大文件推荐，内存友好）
with open("hello.txt", "r", encoding="utf-8") as f:
    for line in f:
        print(line.rstrip("\n"))
```

## 5.4 pathlib：现代路径写法

```python
from pathlib import Path

base = Path(".")                       # 当前目录
p = base / "sub" / "note.txt"          # 用 / 拼接，跨平台
p.parent.mkdir(parents=True, exist_ok=True)
p.write_text("内容", encoding="utf-8")

print(p.exists(), p.name, p.suffix, p.parent)
print(p.read_text(encoding="utf-8"))

for f in Path(".").iterdir():          # 遍历目录
    print(f.name, f.is_dir(), f.stat().st_size)
```

## 5.5 JSON

```python
import json

data = {"name": "小明", "tags": ["python", "android"], "score": 92}

# 写
with open("config.json", "w", encoding="utf-8") as f:
    json.dump(data, f, ensure_ascii=False, indent=2)

# 读
with open("config.json", "r", encoding="utf-8") as f:
    loaded = json.load(f)

print(loaded["tags"][0])
```

`ensure_ascii=False` 让中文以原样写出而不是 `\uXXXX`。

## 5.6 CSV

```python
import csv

rows = [["name", "score"], ["小明", 92], ["小红", 88]]

with open("scores.csv", "w", encoding="utf-8", newline="") as f:
    w = csv.writer(f)
    w.writerows(rows)

with open("scores.csv", "r", encoding="utf-8") as f:
    for row in csv.reader(f):
        print(row)
```

## 5.7 常见错误

| 报错 | 原因 |
| --- | --- |
| `FileNotFoundError` | 路径写错，或文件在别的目录 |
| `PermissionError` | 试图写入 App 没有权限的目录（如 `/sdcard` 根、`/system`） |
| `UnicodeDecodeError` | 读文件时编码不对；先试 `encoding="utf-8"`，再试 `"gbk"` |
| `IsADirectoryError` | 把目录当文件打开了 |

可写目录只有 App 自己的私有目录与项目目录；这也是把工作文件放在项目里的原因。

## 5.8 动手练习

1. 把一个列表写进 `todo.txt`，每行一条；然后再读出来并编号打印。
2. 写一个 `save_config()` / `load_config()`，用 JSON 保存「上次打开的文件名」。
3. 把 `scores.csv` 读进来，打印总分与平均分。

## 下一课

`06_stdlib` 介绍最常用的标准库。

---

### 本课示例

`05_files_example.py` 会在项目目录里真实创建若干文件，并在结束时打印它们的路径。
