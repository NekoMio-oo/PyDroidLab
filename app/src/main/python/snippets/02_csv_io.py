"""写出 CSV 并读回（含中文与表头）

适合：把计算结果导出成表格，方便用 Excel / WPS 打开。
要点：写文件时加 newline=""，否则 Windows 风格换行会多出空行。
"""

import csv
from pathlib import Path

HERE = Path(__file__).parent


def write_dicts(path, rows, fieldnames=None):
    """把「字典列表」写成带表头的 CSV。"""
    if not rows:
        raise ValueError("rows 不能为空")
    fieldnames = fieldnames or list(rows[0].keys())
    with open(path, "w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)
    return path


def read_dicts(path):
    """读回 CSV，返回字典列表（所有值都是字符串）。"""
    with open(path, "r", encoding="utf-8", newline="") as f:
        return list(csv.DictReader(f))


def read_with_types(path, converters):
    """读 CSV 并按 converters 做类型转换。

    converters 形如 {"score": int, "height": float}
    """
    out = []
    for row in read_dicts(path):
        converted = dict(row)
        for key, fn in converters.items():
            if key in converted and converted[key] != "":
                converted[key] = fn(converted[key])
        out.append(converted)
    return out


if __name__ == "__main__":
    target = HERE / "snippet_scores.csv"

    data = [
        {"name": "小明", "score": 92, "city": "北京"},
        {"name": "小红", "score": 88, "city": "上海"},
        {"name": "小刚", "score": 57, "city": "广州"},
    ]
    write_dicts(target, data)
    print(f"已写入 {target.name}")

    print("\n--- 读回原始字符串 ---")
    for row in read_dicts(target):
        print(row)

    print("\n--- 读回并转换类型 ---")
    typed = read_with_types(target, {"score": int})
    total = sum(r["score"] for r in typed)
    print(f"总分 {total}，平均 {total / len(typed):.1f}")
    for r in sorted(typed, key=lambda x: -x["score"]):
        print(f"  {r['name']:<4} {r['score']:>3}  {r['city']}")

    target.unlink(missing_ok=True)
    print("\n（示例文件已清理）")
