"""第 2 课示例：input() 交互与格式化输出。

运行后请在「控制台」底部的输入框里回答提问，再按「发送」。
如果输入了非数字，程序会演示 ValueError 的处理方式。
"""

print("=== 简易 BMI 计算器 ===")
print("（运行中请用控制台底部的输入框回答）\n")


def ask_float(prompt):
    """反复追问，直到用户给出一个合法的小数。"""
    while True:
        raw = input(prompt)
        try:
            value = float(raw)
        except ValueError:
            print(f"  ✗「{raw}」不是数字，请重新输入。")
            continue
        if value <= 0:
            print("  ✗ 必须大于 0，请重新输入。")
            continue
        return value


height = ask_float("请输入身高（米），例如 1.72：")
weight = ask_float("请输入体重（公斤），例如 65：")

bmi = weight / (height ** 2)

print()
print(f"身高：{height:.2f} m")
print(f"体重：{weight:.1f} kg")
print(f"BMI ：{bmi:.1f}")

if bmi < 18.5:
    level = "偏瘦"
elif bmi < 24:
    level = "正常"
elif bmi < 28:
    level = "偏胖"
else:
    level = "肥胖"
print(f"结论：{level}")

# ---- 格式化演示 ----
print("\n=== 格式化演示 ===")
pi = 3.14159265
print(f"保留两位小数   : {pi:.2f}")
print(f"宽度 12 补空格 : [{pi:12.3f}]")
print(f"左对齐         : [{pi:<12.3f}]")
print(f"百分比         : {bmi / 30:.1%}")
print(f"千分位         : {1234567:,}")
print(f"十六进制       : {255:#x}")

# ---- 一个不需要输入的追问示范（EOF 时 input 会收到 KeyboardInterrupt）----
print("\n=== 再问一次，直接点「发送」留空也可以 ===")
text = input("随便说点什么：")
print(f"你说了 {len(text)} 个字符：" + ("（空）" if text == "" else text))

print("\n=== 示例结束 ===")
