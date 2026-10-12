"""matplotlib 画正弦曲线并推送到「图表」页

适合：作为绘图模板改一改就能用。
要点：PyDroidLab 已把 matplotlib 切到 Agg 后端，plt.show() 会自动
      渲染成 PNG 并送到「图表」面板；也可以自己 savefig 存文件。
"""

try:
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt
except ImportError:
    print("还没安装 matplotlib。请到「软件包」页搜索 matplotlib 安装后再运行。")
    raise SystemExit(0)

try:
    import numpy as np
except ImportError:
    print("还没安装 numpy。请到「软件包」页搜索 numpy 安装后再运行。")
    raise SystemExit(0)

from pathlib import Path

HERE = Path(__file__).parent


def setup_chinese_font():
    """尽量找一个可用的中文字体，找不到就保持默认（标题可能是方框）。"""
    from matplotlib import font_manager

    available = {f.name for f in font_manager.fontManager.ttflist}
    for name in ("Noto Sans CJK SC", "Source Han Sans CN",
                 "Droid Sans Fallback", "WenQuanYi Zen Hei"):
        if name in available:
            matplotlib.rcParams["font.sans-serif"] = [name]
            matplotlib.rcParams["axes.unicode_minus"] = False
            return name
    return None


def sine_plot(amplitude=1.0, frequency=1.0, thickness=2.0,
              samples=400, title=None, show=True):
    """画一条正弦曲线；show=True 时会推送到「图表」页。"""
    x = np.linspace(0, 2 * np.pi / frequency, samples)
    y = amplitude * np.sin(frequency * x)

    fig, ax = plt.subplots(figsize=(7, 4.2))
    ax.plot(x, y, linewidth=thickness, color="#2563EB", label=f"y = {amplitude}·sin({frequency}x)")
    ax.axhline(0, color="#999999", linewidth=0.8)
    ax.set_xlabel("x")
    ax.set_ylabel("y")
    ax.set_title(title or "Sine Wave")
    ax.grid(True, alpha=0.3)
    ax.legend(loc="upper right")
    fig.tight_layout()

    if show:
        plt.show()          # ← 交给 PyDroidLab 渲染到图表页
    return fig


def save_and_show(name="snippet_sine.png"):
    """先存文件、再推到图表页，两不误。"""
    fig = sine_plot(amplitude=1.5, frequency=2.0, title="Saved + Shown")
    out = HERE / name
    fig.savefig(out, dpi=150, bbox_inches="tight")
    plt.show()
    return out


if __name__ == "__main__":
    font = setup_chinese_font()
    print(f"matplotlib {matplotlib.__version__} / numpy {np.__version__}")
    print("中文字体:", font or "未找到，图形标题请用英文")

    print("\n--- 图 1：基础正弦 ---")
    sine_plot()

    print("--- 图 2：叠加对比 ---")
    x = np.linspace(0, 2 * np.pi, 300)
    fig, ax = plt.subplots(figsize=(7, 4.2))
    for amp, color in ((1.0, "#2563EB"), (0.5, "#16A34A"), (1.5, "#DC2626")):
        ax.plot(x, amp * np.sin(x), label=f"幅度 {amp}", color=color)
    ax.set_title("Different Amplitudes")
    ax.grid(True, alpha=0.3)
    ax.legend()
    fig.tight_layout()
    plt.show()

    print("--- 图 3：正弦 + 余弦 + 差 ---")
    fig, axes = plt.subplots(1, 3, figsize=(12, 3.6))
    axes[0].plot(x, np.sin(x), color="#2563EB")
    axes[0].set_title("sin")
    axes[1].plot(x, np.cos(x), color="#16A34A")
    axes[1].set_title("cos")
    axes[2].plot(x, np.sin(x) - np.cos(x), color="#DC2626")
    axes[2].set_title("sin - cos")
    fig.tight_layout()
    plt.show()

    print("\n--- 保存 PNG ---")
    path = save_and_show()
    print("已保存:", path)
    print("（也可以直接在「图表」页点「保存 PNG」）")

    print("\n提示：数据点也可以换成你自己算出来的列表，")
    print("      plt.plot(xs, ys) 只要求两个序列等长。")
