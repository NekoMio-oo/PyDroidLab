"""pip helper invoked from the Kotlin Package Manager.

Runs pip as a subprocess-like module call with an optional index mirror and
streams output through the bridged stdout.  Binary wheels (numpy etc.) resolve
against Chaquopy's repository automatically because its index is baked into
the embedded pip configuration.
"""

import subprocess
import sys

import pdl_runtime

PYPI = "https://pypi.org/simple"
TSINGHUA = "https://pypi.tuna.tsinghua.edu.cn/simple"


def _run(args, mirror):
    pdl_runtime.install_stdio()
    cmd = [sys.executable, "-m", "pip"] + args
    if mirror == "tsinghua":
        cmd += ["-i", TSINGHUA]
    elif mirror == "pypi":
        cmd += ["-i", PYPI]
    # Binary packages (numpy/pandas/matplotlib/scipy/scikit-learn/Pillow) are
    # served from Chaquopy's own wheel repo; pip is preconfigured for it.
    sys.stdout.write("$ " + " ".join(cmd) + "\n")
    proc = subprocess.run(cmd, stdout=sys.stdout, stderr=sys.stderr)
    return proc.returncode


def install(spec, mirror="pypi"):
    return _run(["install", spec], mirror)


def upgrade(spec, mirror="pypi"):
    return _run(["install", "--upgrade", spec], mirror)


def uninstall(name):
    return _run(["uninstall", "-y", name], "pypi")


def install_requirements(path, mirror="pypi"):
    return _run(["install", "-r", path], mirror)


def install_wheel(path):
    return _run(["install", path], "pypi")


def list_installed():
    import importlib.metadata as md
    pkgs = sorted((d.metadata["Name"], d.version)
                  for d in md.distributions())
    return ["%s==%s" % (n, v) for n, v in pkgs]


def freeze():
    return list_installed()


def pip_version():
    import pip
    return pip.__version__
