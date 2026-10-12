"""第 5 课示例：文件读写与路径。

运行后会在脚本所在目录（项目目录）创建若干文件，
可以在「文件」页看到：hello.txt / lines.txt / todo.txt / config.json / scores.csv
"""

import csv
import json
import os
from pathlib import Path

print("=== 0. 位置信息 ===")
print("当前工作目录 :", os.getcwd())
print("脚本文件     :", os.path.abspath(__file__))
here = Path(__file__).parent
print("脚本所在目录 :", here)

print("\n=== 1. 写文本文件 ===")
hello = here / "hello.txt"
with open(hello, "w", encoding="utf-8") as f:
    f.write("第一行：你好，文件！\n")
    f.write("第二行：PyDroidLab\n")
print("已写入", hello.name, f"({hello.stat().st_size} 字节)")

lines_file = here / "lines.txt"
with open(lines_file, "w", encoding="utf-8") as f:
    f.writelines(["alpha\n", "beta\n", "gamma\n"])

print("\n=== 2. 读文本文件 ===")
print("--- 一次读完 ---")
with open(hello, "r", encoding="utf-8") as f:
    print(f.read(), end="")

print("--- 按行读 ---")
with open(lines_file, "r", encoding="utf-8") as f:
    for i, line in enumerate(f, 1):
        print(f"{i}: {line.rstrip()}")

print("\n=== 3. 追加模式 ===")
with open(hello, "a", encoding="utf-8") as f:
    f.write("第三行：追加进来的\n")
print("现在共", len(hello.read_text(encoding="utf-8").splitlines()), "行")

print("\n=== 4. pathlib ===")
todo = here / "todo.txt"
todo.write_text("买牛奶\n写代码\n锻炼\n", encoding="utf-8")
print(f"name={todo.name}  suffix={todo.suffix}  exists={todo.exists()}")
print("内容:")
for i, item in enumerate(todo.read_text(encoding="utf-8").splitlines(), 1):
    print(f"  {i}. {item}")

print("\n=== 5. JSON 读写 ===")
config = {
    "app": "PyDroidLab",
    "version": "1.0.0",
    "features": ["editor", "console", "charts"],
    "score": 92.5,
}
config_path = here / "config.json"
with open(config_path, "w", encoding="utf-8") as f:
    json.dump(config, f, ensure_ascii=False, indent=2)
print(config_path.read_text(encoding="utf-8"))

with open(config_path, "r", encoding="utf-8") as f:
    loaded = json.load(f)
print("读回 features[0] =", loaded["features"][0], " score =", loaded["score"])

print("\n=== 6. CSV 读写 ===")
scores_path = here / "scores.csv"
rows = [["name", "score"], ["小明", 92], ["小红", 88], ["小刚", 57]]
with open(scores_path, "w", encoding="utf-8", newline="") as f:
    csv.writer(f).writerows(rows)
print("已写入 scores.csv")

total = 0
count = 0
with open(scores_path, "r", encoding="utf-8") as f:
    reader = csv.DictReader(f)
    for row in reader:
        score = int(row["score"])
        total += score
        count += 1
        print(f"  {row['name']:<4} {score:>3}")
print(f"总分 {total}，平均 {total / count:.1f}")

print("\n=== 7. 遍历目录 ===")
print(f"{here} 下的内容：")
for entry in sorted(here.iterdir()):
    kind = "DIR " if entry.is_dir() else "FILE"
    size = "-" if entry.is_dir() else str(entry.stat().st_size)
    print(f"  [{kind}] {entry.name:<20} {size}")

print("\n=== 8. 异常处理 ===")
try:
    open(here / "不存在的文件.txt", "r", encoding="utf-8")
except FileNotFoundError as e:
    print("捕获到 FileNotFoundError:", e)

print("\n=== 示例结束（文件已留在项目目录里）===")
