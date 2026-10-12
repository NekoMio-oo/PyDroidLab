"""第 1 课示例：你好，Python。

直接点「运行」，输出会出现在「控制台」。
"""

# 单行注释：这一行不会被执行

print("Hello, PyDroidLab!")

# print 可以一次输出多个值，默认用空格分隔
print("Python 版本之外，先认识几个基础函数：", type(print), type(1), type("a"))

# 用 sep / end 控制分隔符和结尾
print("A", "B", "C", sep=" | ")
print("这一行不换行……", end="")
print("接着上一行继续。")

# 三引号字符串：多行文本
message = """
===========
 PyDroidLab
===========
"""
print(message)

# 基本运算
print("1 + 2 =", 1 + 2)
print("10 / 3 =", 10 / 3)
print("10 // 3 =", 10 // 3, "（整除）")
print("10 % 3 =", 10 % 3, "（取余）")
print("2 ** 10 =", 2 ** 10, "（幂）")

# f-string：把变量嵌进字符串（最常用的格式化方式）
name = "同学"
count = 3
print(f"你好，{name}！这是你今天的第 {count} 个练习。")

# 缩进表示代码块
for i in range(1, 4):
    print("第", i, "次循环")

print("\n=== 本课示例结束 ===")
