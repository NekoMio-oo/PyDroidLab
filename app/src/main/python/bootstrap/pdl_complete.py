"""编辑器代码补全的辅助模块。

Kotlin 侧在「打开 / 保存 / 运行」等时机调用一次，把结果缓存起来，
补全回调只读缓存（补全回调是同步的，不能在里面等 Python）。
"""

import ast
import importlib
import re

import pdl_utils

_IMPORT_RE = re.compile(r"^\s*import\s+([\w\.,\s]+)", re.M)
_FROM_RE = re.compile(r"^\s*from\s+([\w\.]+)\s+import\s+([^\n#]+)", re.M)


def _regex_modules(source):
    names = []
    for m in _IMPORT_RE.finditer(source or ""):
        for part in m.group(1).split(","):
            name = part.strip().split(" as ")[0].strip()
            if name:
                names.append(name)
    for m in _FROM_RE.finditer(source or ""):
        names.append(m.group(1).strip())
    return names


def imported_modules(source):
    """返回源码里 import 到的模块名（同时给出完整路径与顶层包名）。"""
    try:
        tree = ast.parse(source or "")
        names = []
        for node in ast.walk(tree):
            if isinstance(node, ast.Import):
                for alias in node.names:
                    names.append(alias.name)
            elif isinstance(node, ast.ImportFrom):
                # level > 0 表示相对导入，跳过
                if node.module and getattr(node, "level", 0) == 0:
                    names.append(node.module)
    except (SyntaxError, ValueError):
        names = _regex_modules(source)

    out, seen = [], set()
    for name in names:
        for candidate in (name, name.split(".")[0]):
            if candidate and candidate not in seen:
                seen.add(candidate)
                out.append(candidate)
    return out


def from_import_names(source):
    """返回 `from x import a, b as c` 里可以直接使用的名字。"""
    names = []
    try:
        tree = ast.parse(source or "")
        for node in ast.walk(tree):
            if isinstance(node, ast.ImportFrom):
                for alias in node.names:
                    names.append(alias.asname or alias.name)
    except (SyntaxError, ValueError):
        for m in _FROM_RE.finditer(source or ""):
            for part in m.group(2).split(","):
                token = part.strip().split(" as ")[-1].strip()
                if token:
                    names.append(token)

    out, seen = [], set()
    for name in names:
        if name and name != "*" and name not in seen:
            seen.add(name)
            out.append(name)
    return out


def module_members(name):
    """返回模块的公开顶层属性名（不含下划线开头）。"""
    if not name:
        return []
    try:
        module = importlib.import_module(name)
    except BaseException:
        return []
    try:
        return sorted(n for n in dir(module) if not n.startswith("_"))
    except BaseException:
        return []


def module_members_map(names):
    """批量取成员，返回 {模块名: [成员...]}，失败的模块值为空列表。

    names 来自 Kotlin 的 `Array<String>`（Java 数组包装），先转成 list。
    """
    return {name: module_members(name) for name in pdl_utils.as_str_list(names)}


def builtin_names():
    """内置作用域里可直接使用的名字（供补全用）。"""
    import builtins

    return sorted(n for n in dir(builtins) if not n.startswith("_"))
