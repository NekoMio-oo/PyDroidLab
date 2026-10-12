"""第 8 课示例：numpy 与 matplotlib。

运行前请到「软件包」页安装 numpy 与 matplotlib。
运行后会生成 4 张图，请切到「图表」页查看，并可逐张「保存 PNG」。
"""

try:
    import numpy as np
except ImportError:
    print("还没有安装 numpy。")
    print("请打开左侧「软件包」页 → 搜索框输入 numpy → 点「安装」，装完再运行本示例。")
    raise SystemExit(0)

try:
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt
except ImportError:
    print("还没有安装 matplotlib。")
    print("请打开左侧「软件包」页 → 搜索框输入 matplotlib → 点「安装」，装完再运行本示例。")
    raise SystemExit(0)

print(f"numpy {np.__version__} / matplotlib {matplotlib.__version__}")

print("\n=== 1. ndarray 基础 ===")
a = np.array([1, 2, 3, 4])
print("a        =", a, " dtype =", a.dtype, " shape =", a.shape)
print("a * 2    =", a * 2)
print("a ** 2   =", a ** 2)
print("sum/mean/max =", a.sum(), a.mean(), a.max())

print("\n=== 2. 生成与变形 ===")
x = np.linspace(0, 2 * np.pi, 5)
print("linspace :", x)
print("arange   :", np.arange(0, 10, 2))
print("zeros    :\n", np.zeros((2, 3)))
print("eye      :\n", np.eye(3))
rng = np.random.default_rng(0)
print("normal   :", rng.normal(size=3))

m = np.arange(6).reshape(2, 3)
print("reshape  :\n", m)
print("广播相加 :\n", m + np.array([10, 20, 30]))
print("axis=0 和:", m.sum(axis=0), " axis=1 和:", m.sum(axis=1))

print("\n=== 3. 图 1：正弦 / 余弦 ===")
xs = np.linspace(0, 2 * np.pi, 200)
plt.figure(figsize=(6, 4))
plt.plot(xs, np.sin(xs), label="sin(x)")
plt.plot(xs, np.cos(xs), label="cos(x)", linestyle="--")
plt.title("Sine and Cosine")
plt.xlabel("x")
plt.ylabel("y")
plt.legend()
plt.grid(True)
plt.show()

print("=== 4. 图 2：散点 ===")
rng = np.random.default_rng(42)
sx = rng.normal(size=300)
sy = sx * 0.8 + rng.normal(scale=0.5, size=300)
plt.figure(figsize=(6, 4))
plt.scatter(sx, sy, s=18, c=sy, cmap="viridis", alpha=0.8)
plt.title("Scatter with Color Mapping")
plt.colorbar()
plt.show()

print("=== 5. 图 3：直方图 ===")
samples = rng.normal(loc=0, scale=1, size=5000)
plt.figure(figsize=(6, 4))
plt.hist(samples, bins=40, color="#4C9BE8", edgecolor="white")
plt.axvline(samples.mean(), color="crimson", linestyle="--", label="mean")
plt.title("Histogram of Normal Samples")
plt.legend()
plt.show()

print("=== 6. 图 4：多子图 ===")
fig, axes = plt.subplots(1, 2, figsize=(10, 4))
axes[0].plot(xs, np.sin(xs), color="#E8574C")
axes[0].set_title("sin")
axes[1].plot(xs, np.cos(xs), color="#2E9E5B")
axes[1].set_title("cos")
fig.suptitle("Two Subplots")
plt.show()

print("\n=== 7. 顺带保存一个 PNG 到项目目录 ===")
import os

here = os.path.dirname(os.path.abspath(__file__))
out = os.path.join(here, "sine_saved_by_script.png")
plt.figure(figsize=(6, 4))
plt.plot(xs, np.sin(xs) ** 2, color="#7C4DFF")
plt.title("sin^2(x)")
plt.savefig(out, dpi=150, bbox_inches="tight")
plt.show()
print("已保存:", out)
print("（也可以在「图表」页点「保存 PNG」，效果一样）")

print("\n=== 示例结束：请切到「图表」页查看 5 张图 ===")
