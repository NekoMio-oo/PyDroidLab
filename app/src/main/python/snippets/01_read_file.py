"""读取文本文件（按行 / 一次性）

适合：读配置、读数据、读日志。
要点：一定写 encoding="utf-8"，并且用 with 让文件自动关闭。
"""

import os
from pathlib import Path

# 当前脚本所在目录 —— 相对路径的稳妥基准
HERE = Path(__file__).parent


def read_all(path):
    """一次性读完整个文件。适合小文件（几 MB 以内）。"""
    with open(path, "r", encoding="utf-8") as f:
        return f.read()


def read_lines(path):
    """按行读，返回去掉行尾换行的列表。大文件推荐这种写法。"""
    lines = []
    with open(path, "r", encoding="utf-8") as f:
        for line in f:
            lines.append(line.rstrip("\n"))
    return lines


def read_chunks(path, chunk_size=8192):
    """分块读取，适合超大文件或边读边处理。"""
    with open(path, "r", encoding="utf-8") as f:
        while True:
            chunk = f.read(chunk_size)
            if not chunk:
                break
            yield chunk


def write_lines(path, lines):
    """把一串字符串写成文件，每行一条。"""
    with open(path, "w", encoding="utf-8") as f:
        for line in lines:
            f.write(line + "\n")


if __name__ == "__main__":
    demo = HERE / "snippet_read_file_demo.txt"
    write_lines(demo, ["第一行", "第二行", "third line"])

    print(f"文件：{demo}（{os.path.getsize(demo)} 字节）")
    print("--- read_all ---")
    print(read_all(demo), end="")
    print("--- read_lines ---")
    for i, line in enumerate(read_lines(demo), 1):
        print(f"{i}: {line}")
    print("--- read_chunks ---")
    for chunk in read_chunks(demo, chunk_size=3):
        print(repr(chunk))

    # 清理
    demo.unlink(missing_ok=True)
    print("（示例文件已清理）")
