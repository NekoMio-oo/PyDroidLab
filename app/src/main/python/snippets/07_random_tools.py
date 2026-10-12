"""随机抽样与小工具（洗牌 / 抽签 / 加权随机 / 分组）

适合：做小游戏、抽奖、随机测试数据、分训练测试集。
要点：想复现结果就先 random.seed(...)；要密码学安全请用 secrets 模块。
"""

import random
from collections import defaultdict

SEED = 20261012


def lottery(pool, k, seed=None):
    """不放回抽 k 个（等价于抽奖）。"""
    rng = random.Random(seed)
    k = min(k, len(pool))
    return rng.sample(pool, k)


def shuffle_copy(seq, seed=None):
    """洗牌但不动原序列。"""
    items = list(seq)
    random.Random(seed).shuffle(items)
    return items


def weighted_choice(options, weights, seed=None):
    """带权重的随机选择。

    options / weights 等长，权重可以不是整数（会自动归一化）。
    """
    if len(options) != len(weights):
        raise ValueError("options 与 weights 长度必须一致")
    if not options:
        raise ValueError("options 不能为空")
    rng = random.Random(seed)
    return rng.choices(options, weights=weights, k=1)[0]


def split_train_test(data, ratio=0.8, seed=None):
    """按比例切成训练集 / 测试集。"""
    if not 0 < ratio < 1:
        raise ValueError("ratio 必须在 (0, 1) 之间")
    shuffled = shuffle_copy(data, seed)
    cut = int(len(shuffled) * ratio)
    return shuffled[:cut], shuffled[cut:]


def round_robin_groups(members, group_count, seed=None):
    """尽量均匀地分成若干组。"""
    if group_count <= 0:
        raise ValueError("group_count 必须为正")
    shuffled = shuffle_copy(members, seed)
    groups = defaultdict(list)
    for i, m in enumerate(shuffled):
        groups[i % group_count].append(m)
    return [groups[i] for i in range(group_count)]


if __name__ == "__main__":
    random.seed(SEED)
    print("=== 1. 基础随机 ===")
    print("random()        :", f"{random.random():.6f}")
    print("randint(1,6)    :", random.randint(1, 6))
    print("uniform(-1,1)   :", f"{random.uniform(-1, 1):.4f}")
    print("gauss(0,1)      :", f"{random.gauss(0, 1):.4f}")

    print("\n=== 2. 抽奖（不放回）===")
    people = [f"用户{i:02d}" for i in range(1, 21)]
    print("一等奖 1 名 :", lottery(people, 1, seed=1))
    print("二等奖 3 名 :", lottery(people, 3, seed=2))
    print("三等奖 5 名 :", lottery(people, 5, seed=3))

    print("\n=== 3. 可复现的洗牌 ===")
    cards = list(range(1, 11))
    print("原序 :", cards)
    print("洗牌 :", shuffle_copy(cards, seed=42))
    print("再洗 :", shuffle_copy(cards, seed=42), "← 同种子结果一致")

    print("\n=== 4. 加权随机（抽卡）===")
    prizes = ["SSR", "SR", "R", "N"]
    weights = [1, 9, 30, 60]
    counts = {p: 0 for p in prizes}
    for _ in range(10000):
        counts[weighted_choice(prizes, weights)] += 1
    for p in prizes:
        print(f"  {p:<4} {counts[p]:>5} 次  ({counts[p] / 100:.2f}%)")

    print("\n=== 5. 划分训练 / 测试集 ===")
    dataset = [f"样本{i:02d}" for i in range(1, 21)]
    train, test = split_train_test(dataset, ratio=0.75, seed=7)
    print(f"训练集 {len(train)} 条 : {train}")
    print(f"测试集 {len(test)} 条 : {test}")
    print("有无重叠:", bool(set(train) & set(test)))

    print("\n=== 6. 随机分组 ===")
    members = [f"队员{i:02d}" for i in range(1, 13)]
    for i, g in enumerate(round_robin_groups(members, 3, seed=5), 1):
        print(f"  第 {i} 组: {g}")

    print("\n=== 7. secrets：生成密码（真正安全）===")
    import secrets
    import string

    alphabet = string.ascii_letters + string.digits
    pwd = "".join(secrets.choice(alphabet) for _ in range(16))
    print("密码:", pwd)
    print("随机字节 hex:", secrets.token_hex(8))
