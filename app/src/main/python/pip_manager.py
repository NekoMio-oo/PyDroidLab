"""
pip_manager.py
Runtime pip package manager for PyDroidLab.

Responsibilities:
1. Installs, upgrades, and uninstalls Python packages into the app's writable private directory
   (`context.filesDir/python/site-packages`), because Android's APK asset/native directories are read-only.
2. Uses Chaquopy's extra index URL (`https://chaquo.com/pypi-13.1`) alongside PyPI (`https://pypi.org/simple`)
   so both pure-Python wheels and Chaquopy Android binary wheels can be resolved when available.
3. Performs clean uninstalls directly from `user_site_packages` by inspecting `.dist-info` metadata
   and `RECORD` manifests (since `pip uninstall` does not accept `--target`).
4. Enumerates all installed packages and versions via `importlib.metadata`, distinguishing between
   user-installed packages (upgradable/uninstallable) and build-time bundled packages.
"""

import csv
import importlib
import importlib.metadata
import io
import json
import os
import shutil
import site
import sys
import traceback


CHAQUOPY_WHEEL_INDEX = "https://chaquo.com/pypi-13.1"


class _PipLogStream(io.TextIOBase):
    """Streams real-time pip output back to the Kotlin Package Manager UI."""

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


def _refresh_import_caches(user_site_packages: str):
    """Refreshes Python's import caches and `sys.path` after package changes."""
    os.makedirs(user_site_packages, exist_ok=True)
    site.addsitedir(user_site_packages)
    if user_site_packages in sys.path:
        sys.path.remove(user_site_packages)
    sys.path.insert(0, user_site_packages)
    importlib.invalidate_caches()


def list_installed_packages(user_site_packages: str) -> str:
    """
    Returns a JSON array of installed packages sorted by name.
    Each item has:
      - name: str
      - version: str
      - summary: str
      - is_user_installed: bool (True if located inside `user_site_packages`)
      - location: str
    """
    _refresh_import_caches(user_site_packages)
    norm_user_dir = os.path.realpath(user_site_packages)

    seen = {}
    # Inspect user_site_packages first so upgraded versions shadow bundled ones
    search_paths = [user_site_packages] + [p for p in sys.path if p != user_site_packages]

    for dist in importlib.metadata.distributions(path=search_paths):
        try:
            meta = dist.metadata
            name = (meta.get("Name") or dist.name or "").strip()
            if not name:
                continue
            key = name.lower().replace("_", "-")
            if key in seen:
                continue
            version = (meta.get("Version") or dist.version or "unknown").strip()
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

    items = sorted(seen.values(), key=lambda x: (not x["is_user_installed"], x["name"].lower()))
    return json.dumps(items, ensure_ascii=False)


def _run_pip_cli(args: list, logger) -> int:
    """
    Runs pip's internal CLI entry point in-process while capturing stdout/stderr.
    """
    orig_stdout = sys.stdout
    orig_stderr = sys.stderr
    log_stream = _PipLogStream(logger)
    try:
        sys.stdout = log_stream
        sys.stderr = log_stream
        try:
            from pip._internal.cli.main import main as pip_main
        except ImportError:
            from pip import main as pip_main
        code = pip_main(args)
        return int(code) if code is not None else 0
    except SystemExit as e:
        return int(e.code) if isinstance(e.code, int) else 0
    except Exception:
        log_stream.write(traceback.format_exc())
        return 1
    finally:
        sys.stdout = orig_stdout
        sys.stderr = orig_stderr


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
    """
    pkg = (package_spec or "").strip()
    if not pkg:
        return {"success": False, "message": "Package name cannot be empty."}

    os.makedirs(user_site_packages, exist_ok=True)
    os.makedirs(cache_dir, exist_ok=True)

    args = [
        "install",
        "--target", user_site_packages,
        "--cache-dir", cache_dir,
        "--extra-index-url", CHAQUOPY_WHEEL_INDEX,
        "--no-warn-script-location",
        "--disable-pip-version-check",
    ]
    if upgrade:
        args.append("--upgrade")
    if custom_index_url and custom_index_url.strip():
        args.extend(["--index-url", custom_index_url.strip()])

    # Allow space-separated package specifications safely
    args.extend(pkg.split())

    if logger is not None:
        action_label = "Upgrading" if upgrade else "Installing"
        logger.onLog(f"$ pip {' '.join(args)}\n{action_label} {pkg} into {user_site_packages}...\n")

    code = _run_pip_cli(args, logger)
    _refresh_import_caches(user_site_packages)

    if code == 0:
        msg = f"Successfully {'upgraded' if upgrade else 'installed'} {pkg}."
        if logger is not None:
            logger.onLog(f"\n✓ {msg}\n")
        return {"success": True, "message": msg}
    else:
        msg = f"pip exited with code {code} while processing '{pkg}'."
        if logger is not None:
            logger.onLog(f"\n✗ {msg}\n")
        return {"success": False, "message": msg}


def uninstall_package(
    package_name: str,
    user_site_packages: str,
    logger=None
) -> dict:
    """
    Uninstalls a user-installed package from `user_site_packages` by reading its `.dist-info/RECORD`
    file and removing all owned files and metadata directories.
    (Note: Standard `pip uninstall` does not support `--target`, so inspecting `.dist-info/RECORD`
    inside `user_site_packages` is the cleanest and safest way on Android.)
    """
    pkg = (package_name or "").strip()
    if not pkg:
        return {"success": False, "message": "Package name cannot be empty."}

    norm_target = pkg.lower().replace("_", "-")
    norm_user_dir = os.path.realpath(user_site_packages)

    if not os.path.isdir(norm_user_dir):
        return {"success": False, "message": f"Package '{pkg}' is not installed in user directory."}

    matched_dist = None
    for dist in importlib.metadata.distributions(path=[norm_user_dir]):
        dist_name = (dist.metadata.get("Name") or dist.name or "").strip().lower().replace("_", "-")
        if dist_name == norm_target:
            matched_dist = dist
            break

    if matched_dist is None:
        msg = f"Package '{pkg}' was not found in user site-packages (built-in APK packages cannot be removed)."
        if logger is not None:
            logger.onLog(f"✗ {msg}\n")
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

        # Remove dist-info directory if still present
        if os.path.isdir(dist_info_dir) and dist_info_dir.startswith(norm_user_dir):
            shutil.rmtree(dist_info_dir, ignore_errors=True)
            removed_count += 1

        # Clean up empty package directories
        for d in top_level_dirs:
            real_d = os.path.realpath(d)
            if real_d.startswith(norm_user_dir) and os.path.isdir(real_d):
                shutil.rmtree(real_d, ignore_errors=True)

        _refresh_import_caches(user_site_packages)
        msg = f"Uninstalled '{pkg}' ({removed_count} files/directories removed)."
        if logger is not None:
            logger.onLog(f"✓ {msg}\n")
        return {"success": True, "message": msg}
    except Exception as e:
        msg = f"Failed to uninstall '{pkg}': {e}"
        if logger is not None:
            logger.onLog(f"✗ {msg}\n")
        return {"success": False, "message": msg}
