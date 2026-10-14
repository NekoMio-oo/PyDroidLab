"""
pip_manager.py
Android-Native Wheel & Pip Package Manager for PyDroidLab.

Why a dual-engine architecture is required on Android (Chaquopy):
1. By default, Chaquopy only bundles packages explicitly listed in `build.gradle.kts` (`install("pip")`).
2. Even when `pip` is bundled, standard upstream `pip` runs on a non-standard Android platform where:
   - `sys.executable` cannot spawn subprocesses for `setup.py` / build isolation.
   - Standard `pip` detects platform as `linux_aarch64` rather than Chaquopy's `android_21_arm64_v8a` wheel tag,
     causing `pip install --upgrade numpy` to fail or attempt source compilation.
3. Therefore, `pip_manager.py` implements an Android-Native Wheel Resolver & Installer (backed by Python's
   stdlib `urllib.request`, `json`, `zipfile`, `email.parser`) that queries both PyPI (`pypi.org`) and
   Chaquopy's Android Wheel Repository (`https://chaquo.com/pypi-13.1`), matches pure-Python (`none-any`)
   and Android binary wheels (`android_*_arm64_v8a` / `x86_64`), resolves `Requires-Dist` dependencies,
   and installs them cleanly into `user_site_packages` (`filesDir/python/site-packages`).
"""

import csv
import email.parser
import html.parser
import importlib
import importlib.metadata
import io
import json
import os
import platform
import re
import shutil
import site
import ssl
import sys
import traceback
import urllib.parse
import urllib.request
import zipfile

CHAQUOPY_WHEEL_INDEX = "https://chaquo.com/pypi-13.1"
DEFAULT_PYPI_JSON_BASE = "https://pypi.org/pypi"
DEFAULT_PYPI_SIMPLE_BASE = "https://pypi.org/simple"


class _PipLogStream(io.TextIOBase):
    """Streams real-time installation logs back to the Kotlin Package Manager UI."""

    def __init__(self, logger):
        super().__init__()
        self._logger = logger

    def writable(self):
        return True

    def write(self, text):
        if text and self._logger is not None:
            self._logger.onLog(str(text))
        return len(text) if text else 0

    def flush(self):
        pass


def _log(logger, msg: str):
    if logger is not None:
        logger.onLog(msg if msg.endswith("\n") else msg + "\n")


def _refresh_import_caches(user_site_packages: str):
    """Refreshes Python's import caches and `sys.path` after package changes."""
    os.makedirs(user_site_packages, exist_ok=True)
    site.addsitedir(user_site_packages)
    if user_site_packages in sys.path:
        sys.path.remove(user_site_packages)
    sys.path.insert(0, user_site_packages)
    importlib.invalidate_caches()


def _normalize_pkg_name(name: str) -> str:
    return re.sub(r"[-_.]+", "-", (name or "").strip().lower())


def _get_installed_map(user_site_packages: str) -> dict:
    """Returns a dict mapping normalized package name -> installed metadata dict."""
    _refresh_import_caches(user_site_packages)
    norm_user_dir = os.path.realpath(user_site_packages)
    seen = {}
    search_paths = [user_site_packages] + [p for p in sys.path if p != user_site_packages]

    for dist in importlib.metadata.distributions(path=search_paths):
        try:
            meta = dist.metadata
            name = (meta.get("Name") or dist.name or "").strip()
            if not name:
                continue
            key = _normalize_pkg_name(name)
            if key in seen:
                continue
            version = (meta.get("Version") or dist.version or "0").strip()
            summary = (meta.get("Summary") or "").strip()
            loc_path = os.path.realpath(str(dist.locate_file("")))
            is_user = loc_path.startswith(norm_user_dir)
            seen[key] = {
                "name": name,
                "version": version,
                "summary": summary,
                "is_user_installed": is_user,
                "location": loc_path,
            }
        except Exception:
            continue
    return seen


def list_installed_packages(user_site_packages: str) -> str:
    """Returns a JSON array of installed packages sorted by user-installed first, then name."""
    seen = _get_installed_map(user_site_packages)
    items = sorted(seen.values(), key=lambda x: (not x["is_user_installed"], x["name"].lower()))
    return json.dumps(items, ensure_ascii=False)


# --- Version & Wheel Tag Helpers ---

def _version_sort_key(ver_str: str) -> tuple:
    """Converts a PEP 440 version string into a comparable tuple."""
    clean = (ver_str or "0").split("+")[0].strip()
    parts = re.findall(r"\d+|[a-zA-Z]+", clean)
    key = []
    for p in parts:
        if p.isdigit():
            key.append((1, int(p)))
        else:
            # Pre-release tags (a, b, rc, dev) sort before final release
            key.append((0, p.lower()))
    key.append((2, 0))
    return tuple(key)


def _is_prerelease(ver_str: str) -> bool:
    return bool(re.search(r"(a|b|rc|dev|alpha|beta)\d*", ver_str or "", re.IGNORECASE))


def _detect_android_abi_tags() -> list:
    """Returns compatible Chaquopy Android ABI suffixes for the current device."""
    machine = platform.machine().lower()
    if machine in ("aarch64", "arm64", "armv8l"):
        return ["arm64_v8a"]
    elif machine in ("x86_64", "amd64"):
        return ["x86_64"]
    elif "arm" in machine:
        return ["armeabi_v7a"]
    elif "86" in machine:
        return ["x86"]
    return ["arm64_v8a", "x86_64"]


def _is_wheel_compatible(filename: str) -> tuple:
    """
    Checks if a wheel filename is compatible with the current Python version and Android ABI.
    Returns (is_compatible: bool, priority: int, version_str: str).
    Priority: 2 for Android native binary wheel, 1 for pure-Python py3-none-any wheel.
    """
    if not filename.endswith(".whl"):
        return (False, 0, "")
    stem = filename[:-4]
    parts = stem.split("-")
    if len(parts) < 5:
        return (False, 0, "")

    # Wheel format: {distribution}-{version}(-{build tag})?-{python tag}-{abi tag}-{platform tag}.whl
    plat_tag = parts[-1].lower()
    abi_tag = parts[-2].lower()
    py_tag = parts[-3].lower()
    version_str = parts[1]

    py_major = sys.version_info.major
    py_minor = sys.version_info.minor
    cp_exact = f"cp{py_major}{py_minor}"
    py_exact = f"py{py_major}{py_minor}"

    # Check Python compatibility
    py_tags = py_tag.split(".")
    py_ok = any(
        t in (cp_exact, py_exact, f"py{py_major}", "py2.py3", "py3") or
        (t.startswith("cp3") and abi_tag == "abi3")
        for t in py_tags
    )
    if not py_ok:
        return (False, 0, "")

    # Case 1: Pure Python wheel
    if plat_tag == "any" and abi_tag == "none":
        return (True, 1, version_str)

    # Case 2: Chaquopy / Android binary wheel
    device_abis = _detect_android_abi_tags()
    if "android" in plat_tag and any(abi in plat_tag for abi in device_abis):
        return (True, 2, version_str)

    return (False, 0, "")


class _SimpleIndexLinkParser(html.parser.HTMLParser):
    """Parses PEP 503 Simple Repository HTML pages (`<a>` links)."""

    def __init__(self):
        super().__init__()
        self.links = []

    def handle_starttag(self, tag, attrs):
        if tag.lower() == "a":
            for k, v in attrs:
                if k.lower() == "href" and v:
                    self.links.append(v)


def _create_ssl_context():
    try:
        import certifi
        return ssl.create_default_context(cafile=certifi.where())
    except Exception:
        ctx = ssl.create_default_context()
        return ctx


def _http_get_bytes(url: str, timeout: int = 20) -> bytes:
    req = urllib.request.Request(
        url,
        headers={"User-Agent": f"PyDroidLab-Pip/2.0 (Python {sys.version.split()[0]}; Android)"}
    )
    ctx = _create_ssl_context()
    with urllib.request.urlopen(req, timeout=timeout, context=ctx) as resp:
        return resp.read()


def _find_best_wheel_candidates(pkg_name: str, exact_version: str, custom_index_url: str, logger) -> list:
    """
    Searches both Chaquopy's Android Wheel Index and PyPI (or custom mirror) for compatible wheels.
    Returns a list of candidate dicts sorted from highest version/priority to lowest.
    """
    norm_name = _normalize_pkg_name(pkg_name)
    candidates = []

    # 1. Check Chaquopy Android binary wheel index first (for C-extension packages like numpy, pandas, pillow, etc.)
    chaquo_page_url = f"{CHAQUOPY_WHEEL_INDEX}/{norm_name}/"
    try:
        raw_html = _http_get_bytes(chaquo_page_url, timeout=12).decode("utf-8", errors="replace")
        parser = _SimpleIndexLinkParser()
        parser.feed(raw_html)
        for href in parser.links:
            clean_href = href.split("#")[0]
            fname = urllib.parse.unquote(os.path.basename(clean_href))
            ok, prio, ver = _is_wheel_compatible(fname)
            if ok and (not exact_version or ver == exact_version):
                full_url = urllib.parse.urljoin(chaquo_page_url, href.split("#")[0])
                candidates.append({
                    "filename": fname,
                    "url": full_url,
                    "version": ver,
                    "priority": prio,
                    "source": "Chaquopy Android Repo"
                })
    except Exception:
        pass

    # 2. Check custom PyPI Simple mirror if configured, otherwise PyPI JSON API
    if custom_index_url and custom_index_url.strip():
        simple_base = custom_index_url.strip().rstrip("/")
        page_url = f"{simple_base}/{norm_name}/"
        try:
            raw_html = _http_get_bytes(page_url, timeout=15).decode("utf-8", errors="replace")
            parser = _SimpleIndexLinkParser()
            parser.feed(raw_html)
            for href in parser.links:
                clean_href = href.split("#")[0]
                fname = urllib.parse.unquote(os.path.basename(clean_href))
                ok, prio, ver = _is_wheel_compatible(fname)
                if ok and not _is_prerelease(ver) and (not exact_version or ver == exact_version):
                    full_url = urllib.parse.urljoin(page_url, clean_href)
                    candidates.append({
                        "filename": fname,
                        "url": full_url,
                        "version": ver,
                        "priority": prio,
                        "source": simple_base
                    })
        except Exception as e:
            _log(logger, f"Mirror lookup warning ({page_url}): {e}")
    else:
        pypi_json_url = f"{DEFAULT_PYPI_JSON_BASE}/{norm_name}/json"
        try:
            data = json.loads(_http_get_bytes(pypi_json_url, timeout=15).decode("utf-8"))
            releases = data.get("releases") or {}
            for ver, files in releases.items():
                if _is_prerelease(ver) and not exact_version:
                    continue
                if exact_version and ver != exact_version:
                    continue
                for fobj in files:
                    if fobj.get("yanked"):
                        continue
                    fname = fobj.get("filename") or ""
                    ok, prio, _ = _is_wheel_compatible(fname)
                    if ok and fobj.get("url"):
                        candidates.append({
                            "filename": fname,
                            "url": fobj["url"],
                            "version": ver,
                            "priority": prio,
                            "source": "PyPI"
                        })
        except Exception:
            pass

    candidates.sort(
        key=lambda c: (_version_sort_key(c["version"]), c["priority"]),
        reverse=True
    )
    return candidates


def _parse_package_requirement(spec: str) -> tuple:
    """
    Parses a requirement string like `requests==2.31.0` or `beautifulsoup4>=4.12`.
    Returns `(pkg_name, exact_version_or_empty)`.
    """
    s = (spec or "").strip()
    # Strip environment markers if any
    s = s.split(";")[0].strip()
    # Strip extras like pkg[extra]
    s = re.sub(r"\[.*?\]", "", s)
    m = re.match(r"^([A-Za-z0-9_.-]+)\s*(?:(==|>=|<=|~=|!=|>|<)\s*([A-Za-z0-9_.*+-]+))?", s)
    if not m:
        return (s, "")
    name = m.group(1)
    op = m.group(2) or ""
    ver = m.group(3) or ""
    return (name, ver if op == "==" else "")


def _extract_wheel_and_get_dependencies(whl_path: str, target_dir: str) -> list:
    """
    Extracts a `.whl` archive into `target_dir` and parses `Requires-Dist` from its `.dist-info/METADATA`.
    """
    deps = []
    with zipfile.ZipFile(whl_path, "r") as zf:
        zf.extractall(target_dir)
        for member in zf.namelist():
            if member.endswith(".dist-info/METADATA"):
                raw_meta = zf.read(member).decode("utf-8", errors="replace")
                msg = email.parser.Parser().parsestr(raw_meta)
                for req in msg.get_all("Requires-Dist") or []:
                    # Skip optional `extra ==` dependencies
                    if ";" in req:
                        _, marker = req.split(";", 1)
                        if "extra" in marker:
                            continue
                        if "sys_platform" in marker and "win32" in marker:
                            continue
                        if "platform_system" in marker and "Windows" in marker:
                            continue
                    dep_name, _ = _parse_package_requirement(req)
                    if dep_name:
                        deps.append(dep_name)
    return deps


def _install_via_android_wheel_resolver(
    package_specs: list,
    user_site_packages: str,
    cache_dir: str,
    upgrade: bool,
    custom_index_url: str,
    logger
) -> tuple:
    """
    Installs or upgrades one or more packages (and their required dependencies) using
    the built-in Android-Native Wheel Resolver.
    Returns `(success: bool, summary_message: str)`.
    """
    os.makedirs(user_site_packages, exist_ok=True)
    os.makedirs(cache_dir, exist_ok=True)

    installed_map = _get_installed_map(user_site_packages)
    queue = [(spec, True) for spec in package_specs]
    visited = set()
    installed_summary = []

    while queue:
        raw_spec, is_root_request = queue.pop(0)
        pkg_name, exact_ver = _parse_package_requirement(raw_spec)
        norm_name = _normalize_pkg_name(pkg_name)
        if not norm_name or norm_name in visited:
            continue
        visited.add(norm_name)

        existing = installed_map.get(norm_name)
        if existing and not (upgrade and is_root_request) and not exact_ver:
            _log(logger, f"Requirement already satisfied: {existing['name']}=={existing['version']} ({existing['location']})")
            continue

        _log(logger, f"Collecting {raw_spec}...")
        candidates = _find_best_wheel_candidates(pkg_name, exact_ver, custom_index_url, logger)

        if not candidates:
            if existing:
                _log(
                    logger,
                    f"ℹ️ {existing['name']} (v{existing['version']}) 已是当前 Android ({platform.machine()}) 可用的预编译版本。"
                )
                installed_summary.append(f"{existing['name']}=={existing['version']} (已是最新)")
                continue
            return (
                False,
                f"未找到兼容 Android ({platform.machine()}, Python {sys.version_info.major}.{sys.version_info.minor}) 的 '{pkg_name}' 预编译 Wheel 包 (可能是需要 C/C++ 编译的冷门包)。"
            )

        best = candidates[0]
        if existing and _version_sort_key(best["version"]) <= _version_sort_key(existing["version"]) and not exact_ver:
            _log(
                logger,
                f"Requirement already up-to-date: {existing['name']}=={existing['version']} (latest Android compatible wheel is {best['version']})"
            )
            installed_summary.append(f"{existing['name']}=={existing['version']}")
            continue

        whl_filename = best["filename"]
        whl_url = best["url"]
        local_whl = os.path.join(cache_dir, whl_filename)

        _log(logger, f"  Downloading {whl_filename} from {best['source']}...")
        try:
            whl_bytes = _http_get_bytes(whl_url, timeout=30)
            with open(local_whl, "wb") as f:
                f.write(whl_bytes)
            size_kb = max(1, len(whl_bytes) // 1024)
            _log(logger, f"  Downloaded {whl_filename} ({size_kb} KB)")
        except Exception as e:
            return (False, f"下载 {whl_filename} 失败: {e}")

        # If upgrading a user-installed package, clean old version first
        if existing and existing.get("is_user_installed"):
            uninstall_package(pkg_name, user_site_packages, logger=None)

        _log(logger, f"  Installing {whl_filename} into {user_site_packages}...")
        try:
            deps = _extract_wheel_and_get_dependencies(local_whl, user_site_packages)
            installed_summary.append(f"{pkg_name}-{best['version']}")
            for dep in deps:
                dep_norm = _normalize_pkg_name(dep)
                if dep_norm not in visited and dep_norm not in installed_map:
                    queue.append((dep, False))
        except Exception as e:
            return (False, f"解压安装 {whl_filename} 失败: {e}")

    _refresh_import_caches(user_site_packages)
    if installed_summary:
        return (True, "Successfully processed: " + ", ".join(installed_summary))
    return (True, "All requested packages are already up-to-date.")


def install_or_upgrade_package(
    package_spec: str,
    user_site_packages: str,
    cache_dir: str,
    upgrade: bool = False,
    custom_index_url: str = "",
    logger=None
) -> dict:
    """
    Installs or upgrades a package into `user_site_packages` (`context.filesDir/python/site-packages`).
    Uses the Android-Native Wheel Resolver (supporting both PyPI pure-Python wheels and Chaquopy
    Android C-extension binary wheels without requiring external subprocesses).
    """
    pkg = (package_spec or "").strip()
    if not pkg:
        return {"success": False, "message": "包名不能为空。"}

    os.makedirs(user_site_packages, exist_ok=True)
    os.makedirs(cache_dir, exist_ok=True)

    action_label = "Upgrading" if upgrade else "Installing"
    _log(logger, f"$ pip install {'--upgrade ' if upgrade else ''}--target {user_site_packages} {pkg}")
    _log(logger, f"{action_label} {pkg} (Android {platform.machine()}, Python {sys.version.split()[0]})...")

    specs = [s for s in pkg.split() if not s.startswith("-")]
    ok, msg = _install_via_android_wheel_resolver(
        package_specs=specs,
        user_site_packages=user_site_packages,
        cache_dir=cache_dir,
        upgrade=upgrade,
        custom_index_url=custom_index_url,
        logger=logger
    )

    if ok:
        _log(logger, f"\n✓ {msg}")
        return {"success": True, "message": msg}
    else:
        _log(logger, f"\n✗ {msg}")
        return {"success": False, "message": msg}


def uninstall_package(
    package_name: str,
    user_site_packages: str,
    logger=None
) -> dict:
    """
    Uninstalls a user-installed package from `user_site_packages` by reading its `.dist-info/RECORD`
    file and removing all owned files and metadata directories.
    """
    pkg = (package_name or "").strip()
    if not pkg:
        return {"success": False, "message": "包名不能为空。"}

    norm_target = _normalize_pkg_name(pkg)
    norm_user_dir = os.path.realpath(user_site_packages)

    if not os.path.isdir(norm_user_dir):
        return {"success": False, "message": f"Package '{pkg}' is not installed in user directory."}

    matched_dist = None
    for dist in importlib.metadata.distributions(path=[norm_user_dir]):
        dist_name = _normalize_pkg_name(dist.metadata.get("Name") or dist.name or "")
        if dist_name == norm_target:
            matched_dist = dist
            break

    if matched_dist is None:
        msg = f"'{pkg}' 为 APK 内置预装包（无法卸载，但支持安装新版本覆盖）。"
        _log(logger, f"✗ {msg}")
        return {"success": False, "message": msg}

    removed_count = 0
    try:
        dist_info_dir = os.path.realpath(str(matched_dist._path))
        record_file = os.path.join(dist_info_dir, "RECORD")
        top_level_dirs = set()

        if os.path.isfile(record_file):
            with open(record_file, "r", encoding="utf-8", errors="replace") as f:
                reader = csv.reader(f)
                for row in reader:
                    if not row:
                        continue
                    rel_path = row[0]
                    abs_path = os.path.realpath(os.path.join(norm_user_dir, rel_path))
                    if abs_path.startswith(norm_user_dir) and os.path.exists(abs_path):
                        if os.path.isfile(abs_path) or os.path.islink(abs_path):
                            os.remove(abs_path)
                            removed_count += 1
                        parts = rel_path.replace("\\", "/").split("/")
                        if len(parts) > 1:
                            top_level_dirs.add(os.path.join(norm_user_dir, parts[0]))

        if os.path.isdir(dist_info_dir) and dist_info_dir.startswith(norm_user_dir):
            shutil.rmtree(dist_info_dir, ignore_errors=True)
            removed_count += 1

        for d in top_level_dirs:
            real_d = os.path.realpath(d)
            if real_d.startswith(norm_user_dir) and os.path.isdir(real_d):
                shutil.rmtree(real_d, ignore_errors=True)

        _refresh_import_caches(user_site_packages)
        msg = f"已卸载 '{pkg}' (清理了 {removed_count} 个文件/目录)。"
        _log(logger, f"✓ {msg}")
        return {"success": True, "message": msg}
    except Exception as e:
        msg = f"卸载 '{pkg}' 失败: {e}"
        _log(logger, f"✗ {msg}")
        return {"success": False, "message": msg}
