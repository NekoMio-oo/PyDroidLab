"""第 3 课示例：类型、条件与循环。

一个不需要任何输入的成绩统计小程序。
"""

SCORES = {
    "小明": 92,
    "小红": 88,
    "小刚": 57,
    "小美": 76,
    "小强": 65,
}

print("=== 1. 遍历字典 ===")
for name, score in SCORES.items():
    print(f"{name:<4} {score:>3} 分")

print("\n=== 2. 条件分支：评级 ===")
for name, score in SCORES.items():
    if score >= 90:
        grade = "优秀"
    elif score >= 75:
        grade = "良好"
    elif score >= 60:
        grade = "及格"
    else:
        grade = "不及格"
    print(f"{name:<4} {grade}")

print("\n=== 3. 列表推导式 ===")
values = list(SCORES.values())
passed = [s for s in values if s >= 60]
print(f"全部成绩 : {values}")
print(f"及格成绩 : {passed}")
print(f"成绩平方 : {[s * s for s in values]}")

print("\n=== 4. 聚合计算 ===")
print(f"人数   : {len(values)}")
print(f"总分   : {sum(values)}")
print(f"平均分 : {sum(values) / len(values):.1f}")
print(f"最高分 : {max(values)}  ({max(SCORES, key=SCORES.get)})")
print(f"最低分 : {min(values)}  ({min(SCORES, key=SCORES.get)})")

ordered = sorted(values)
mid = len(ordered) // 2
median = ordered[mid] if len(ordered) % 2 else (ordered[mid - 1] + ordered[mid]) / 2
print(f"中位数 : {median:.1f}")

print("\n=== 5. set 去重与集合运算 ===")
a = {"python", "java", "kotlin"}
b = {"kotlin", "swift", "python"}
print(f"并集   : {a | b}")
print(f"交集   : {a & b}")
print(f"差集   : {a - b}")

print("\n=== 6. for-else：找出第一个不及格的人 ===")
for name, score in SCORES.items():
    if score < 60:
        print(f"第一个不及格的是：{name}（{score} 分）")
        break
else:
    print("所有人都及格了")

print("\n=== 7. 可变对象陷阱 ===")
raw = [1, 2, 3]
alias = raw
alias.append(4)
print(f"别名修改后 raw      = {raw}   ← 被一起改了")

copy = raw.copy()
copy.append(5)
print(f"copy 修改后 raw     = {raw}   ← 这次没变")

print("\n=== 8. 统计一段话的单词频率 ===")
text = "the quick brown fox jumps over the lazy dog the fox"
counter = {}
for word in text.split():
    counter[word] = counter.get(word, 0) + 1

for word, n in sorted(counter.items(), key=lambda kv: (-kv[1], kv[0])):
    print(f"{word:<8} {'*' * n} ({n})")

print("\n=== 示例结束 ===")
