# 第 8 课：numpy 与 matplotlib 绘图

## 本课目标

- 用「软件包」页安装 numpy / matplotlib
- 用 numpy 做数组运算
- 用 matplotlib 画图，并让图直接出现在「图表」页

## 8.1 先装包

打开「软件包」页：

1. 镜像源推荐选「清华镜像 (TUNA)」（国内更快）
2. 在输入框里填 `numpy`，点「安装」
3. 同样安装 `matplotlib`
4. 也可以直接点「科学计算栈（一键安装）」一次装完 numpy / pandas / matplotlib / scipy / scikit-learn / requests / Pillow

安装过程与结果都会打印到「控制台」，装完点「刷新列表」可以看到版本号。

> 这些是**带原生扩展**的包，体积较大（合计数百 MB），建议在 WiFi 下安装。
> 它们用的是 Chaquopy 预先编译好的 Android wheel，不需要你在手机上编译。

验证：

```python
import numpy as np
import matplotlib
print(np.__version__, matplotlib.__version__)
```

## 8.2 numpy 基础

```python
import numpy as np

a = np.array([1, 2, 3, 4])
print(a, a.dtype, a.shape)

# 向量化运算：不用写循环
print(a * 2)          # [2 4 6 8]
print(a ** 2)         # [1 4 9 16]
print(a.sum(), a.mean(), a.max())

# 生成序列
x = np.linspace(0, 2 * np.pi, 5)     # 5 个等分点（含两端）
print(x)
print(np.arange(0, 10, 2))           # 0 2 4 6 8（不含终点）

# 全 0 / 全 1 / 单位阵 / 随机
print(np.zeros((2, 3)))
print(np.ones(3))
print(np.eye(3))
print(np.random.default_rng(0).normal(size=3))

# 广播：不同形状也能逐元素运算
m = np.arange(6).reshape(2, 3)
print(m)
print(m + np.array([10, 20, 30]))    # 每列加不同的值
print(m.sum(axis=0), m.sum(axis=1))
```

numpy 的核心价值是：**同样的运算，代码更短、速度快几十倍**。

## 8.3 matplotlib：把图画出来

PyDroidLab 已经帮你把 matplotlib 切到 **Agg**（无界面）后端，
并把 `plt.show()` 重定向成「渲染成 PNG → 送到图表页」。所以直接写就行：

```python
import numpy as np
import matplotlib.pyplot as plt

x = np.linspace(0, 2 * np.pi, 200)

plt.figure(figsize=(6, 4))
plt.plot(x, np.sin(x), label="sin(x)")
plt.plot(x, np.cos(x), label="cos(x)", linestyle="--")
plt.title("正弦与余弦")
plt.xlabel("x"); plt.ylabel("y")
plt.legend(); plt.grid(True)

plt.show()          # ← 关键：这会生成一张图出现在「图表」页
```

在「图表」页可以：

- 左右切换多张图（一个脚本调多次 `plt.show()` 就会有多张）
- 点「保存 PNG」把图存进当前项目目录
- 点「清空」清掉本页

> 也可以 `plt.savefig("out.png")` 自己保存，但**保存后仍然要调 `plt.show()`**
> 才能在图表页看到。

## 8.4 常用图类型

```python
# 折线
plt.plot(x, y)

# 散点
plt.scatter(x, y, s=10, c=y, cmap="viridis")

# 柱状
plt.bar(["A", "B", "C"], [3, 7, 5])

# 直方图
plt.hist(np.random.default_rng(1).normal(size=1000), bins=30)

# 饼图
plt.pie([30, 45, 25], labels=["A", "B", "C"], autopct="%1.1f%%")

# 多子图
fig, axes = plt.subplots(1, 2, figsize=(10, 4))
axes[0].plot(x, np.sin(x)); axes[0].set_title("sin")
axes[1].plot(x, np.cos(x)); axes[1].set_title("cos")
```

## 8.5 中文乱码

matplotlib 默认字体没有中文字形，标题会显示成方框。两种办法：

```python
# 办法一：干脆用英文
plt.title("Sine Wave")

# 办法二：指定一个系统里存在的中文字体（不同设备可用字体不同）
import matplotlib
from matplotlib import font_manager
available = {f.name for f in font_manager.fontManager.ttflist}
for name in ["Noto Sans CJK SC", "Droid Sans Fallback", "Source Han Sans CN"]:
    if name in available:
        matplotlib.rcParams["font.sans-serif"] = [name]
        break
matplotlib.rcParams["axes.unicode_minus"] = False   # 负号也能正常显示
```

## 8.6 pandas 快速一瞥（选修）

```python
import pandas as pd

df = pd.DataFrame({
    "name": ["小明", "小红", "小刚"],
    "score": [92, 88, 57],
})
print(df)
print(df["score"].mean())
print(df[df["score"] >= 60])
```

## 8.7 动手练习

1. 画 `y = x^2` 在 `[-5, 5]` 上的曲线，加上标题和网格。
2. 生成 1000 个标准正态随机数，画直方图。
3. 同一个脚本里画 3 张图（正弦、余弦、两者之差），切到「图表」页逐张查看。

## 下一课

`09_requests` 讲用 requests 访问网络接口。

---

### 本课示例

`08_numpy_matplotlib_example.py` 会画 4 张图并送到「图表」页；
如果还没装 numpy/matplotlib，它会打印安装提示而不是报错。
