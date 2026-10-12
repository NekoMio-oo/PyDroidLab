"""跨 Chaquopy 边界的类型转换工具。

**为什么需要这个模块**

Kotlin 侧传过来的集合会被 Chaquopy 包装成 *Java 对象*，而不是 Python 原生
容器。由此产生一个非常隐蔽的坑：

    env = {"A": "1"}        # Kotlin  Map<String, String>
    env.items()             # AttributeError: 'LinkedHashMap' object has no
                            # attribute 'items'

更麻烦的是，Java 包装对象**没有 `__len__` / `__bool__`**，所以下面这种
"兜底"写法完全不起作用 —— 空 Map 一样是 truthy，照样走进 `.items()`：

    for k, v in (env or {}).items():   # 空 env 也会崩

修复方式有两条路：Kotlin 侧不再传集合，或者 Python 侧统一转换。这里选后者，
因为它不需要改动任何已经稳定的 Kotlin 接口签名。

**约定**：所有跨边界接收 Map / 数组 / List 的 Python 函数，入口第一件事就是
过一遍 `as_dict()` / `as_list()`。
"""


def as_dict(obj):
    """把 Kotlin 传过来的 Map（或 None / 原生 dict）统一转成 Python dict。

    兼容四种输入：None、Python dict、Java Map 包装对象、可迭代的键值对。
    """
    if obj is None:
        return {}
    if isinstance(obj, dict):
        return {str(k): v for k, v in obj.items()}

    # Java Map 包装（LinkedHashMap / HashMap / TreeMap …）：首选 entrySet()，
    # 它是唯一不依赖 key 对象的遍历方式。
    entry_set = getattr(obj, "entrySet", None)
    if callable(entry_set):
        try:
            return {str(e.getKey()): e.getValue() for e in entry_set()}
        except Exception:
            pass

    # 退一步用 keySet() + get()。
    key_set = getattr(obj, "keySet", None)
    key_get = getattr(obj, "get", None)
    if callable(key_set) and callable(key_get):
        try:
            return {str(k): key_get(k) for k in key_set()}
        except Exception:
            pass

    # 最后兜底：任何可迭代的 (k, v)。
    try:
        return {str(k): v for k, v in dict(obj).items()}
    except Exception:
        return {}


def as_list(obj):
    """把 Kotlin 传过来的数组 / List（或 None / 原生序列）转成 Python list。

    Java 数组与 Collection 包装都支持迭代，但**不支持** `list + jarray`、
    切片等 Python 专属操作，所以先物化成真正的 list 更保险。
    """
    if obj is None:
        return []
    if isinstance(obj, (list, tuple)):
        return list(obj)
    if isinstance(obj, str):
        return [obj]
    try:
        return [item for item in obj]
    except TypeError:
        return []


def as_str(obj, default=""):
    """把 Java String / None 转成 Python str（None 走 default）。"""
    if obj is None:
        return default
    try:
        return str(obj)
    except Exception:
        return default


def as_str_list(obj):
    """`as_list` + 逐项 `str()`，处理 `String[]` 这类入参最常用的一步到位写法。"""
    return [as_str(item) for item in as_list(obj)]
