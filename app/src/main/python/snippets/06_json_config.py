"""JSON 配置的读写与合并（带默认值与校验）

适合：给脚本加一个可编辑的设置文件。
要点：读配置时永远给默认值；写的时候 ensure_ascii=False + indent=2 便于人工编辑。
"""

import json
from pathlib import Path

HERE = Path(__file__).parent

DEFAULTS = {
    "theme": "dark",
    "font_size": 14,
    "auto_save": True,
    "recent": [],
    "pip_mirror": "pypi",
}

# 每个键期望的类型与取值范围，用于校验
VALIDATORS = {
    "theme": lambda v: v in ("dark", "light", "system"),
    "font_size": lambda v: isinstance(v, int) and 8 <= v <= 40,
    "auto_save": lambda v: isinstance(v, bool),
    "recent": lambda v: isinstance(v, list),
    "pip_mirror": lambda v: v in ("pypi", "tsinghua"),
}


def load_config(path, defaults=None, validators=None):
    """读取配置；文件缺失/损坏/字段非法时都回退到默认值。"""
    defaults = defaults if defaults is not None else DEFAULTS
    validators = validators if validators is not None else VALIDATORS
    config = json.loads(json.dumps(defaults))   # 深拷贝，避免污染 DEFAULTS

    p = Path(path)
    if not p.exists():
        print(f"  · {p.name} 不存在，使用默认配置")
        return config

    try:
        raw = json.loads(p.read_text(encoding="utf-8"))
    except json.JSONDecodeError as e:
        print(f"  · {p.name} 不是合法 JSON（{e}），使用默认配置")
        return config
    except OSError as e:
        print(f"  · {p.name} 读取失败（{e}），使用默认配置")
        return config

    if not isinstance(raw, dict):
        print(f"  · {p.name} 顶层不是对象，使用默认配置")
        return config

    for key, value in raw.items():
        if key not in defaults:
            print(f"  · 忽略未知配置项 {key!r}")
            continue
        check = validators.get(key)
        if check and not check(value):
            print(f"  · {key}={value!r} 不合法，保留默认值 {defaults[key]!r}")
            continue
        config[key] = value
    return config


def save_config(path, config):
    p = Path(path)
    p.write_text(
        json.dumps(config, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    return p


def update_config(path, **changes):
    """局部更新：先读、合并、再写回。"""
    config = load_config(path)
    config.update(changes)
    save_config(path, config)
    return config


if __name__ == "__main__":
    cfg_path = HERE / "snippet_config.json"

    print("=== 1. 首次读取（文件不存在）===")
    print("结果:", load_config(cfg_path))

    print("\n=== 2. 写入默认配置 ===")
    save_config(cfg_path, DEFAULTS)
    print(cfg_path.read_text(encoding="utf-8").rstrip())

    print("\n=== 3. 局部更新 ===")
    updated = update_config(cfg_path, font_size=18, recent=["main.py", "test.py"])
    print("font_size =", updated["font_size"], " recent =", updated["recent"])

    print("\n=== 4. 手工写坏文件，看降级行为 ===")
    cfg_path.write_text("{ 这不是 JSON", encoding="utf-8")
    print("结果:", load_config(cfg_path))

    print("\n=== 5. 非法取值会被拒 ===")
    save_config(cfg_path, {"font_size": 999, "theme": "rainbow"})
    print("结果:", load_config(cfg_path))

    cfg_path.unlink(missing_ok=True)
    print("\n（示例文件已清理）")
