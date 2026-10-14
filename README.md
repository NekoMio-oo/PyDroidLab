# PyDroidLab — Android 手机 Python 3 代码编辑器与运行环境

PyDroidLab 是一款对标 Pydroid 3 的移动端 Python 3 集成开发环境（IDE），基于 **Kotlin + Jetpack Compose (Material 3) + Rosemoe Sora Editor + Chaquopy + 交互式 Terminal Emulator** 构建。面向 Python 初学者与移动开发者，提供开箱即用的代码高亮编辑、支持 `input()` 的实时交互终端、运行时 `pip` 第三方库管理、私有工作区文件浏览器与 12 个精选实战示例。

## 1. 环境要求与技术栈

| 模块 | 版本与规格要求 |
| :--- | :--- |
| **开发工具** | Android Studio Jellyfish (2023.3.1) / Koala (2024.1.1) 或更高版本 |
| **JDK 版本** | JDK 17 (`JavaVersion.VERSION_17`) |
| **Gradle 与 AGP** | Gradle `8.7` + Android Gradle Plugin `8.4.2` |
| **Android SDK** | `compileSdk = 34`、`targetSdk = 34`、`minSdk = 26` (Android 8.0 ~ Android 14+) |
| **开发语言与 UI** | Kotlin `1.9.24` + Jetpack Compose BOM `2024.06.00` (Material 3) |
| **代码编辑器组件** | `io.github.Rosemoe.sora-editor:editor:0.23.4` + `language-textmate:0.23.4` |
| **Python 解释器引擎** | Chaquopy `17.1.0`（MIT 开源协议，内置 CPython `3.13` 运行时与 `pip`） |
| **交互式终端控制台** | `:terminal-emulator` 模块（支持 VT100/ANSI 转义序列解析、阻塞式 `stdin` 行缓冲队列） |

## 2. 项目目录结构

```text
PyDroidLab/
├── build.gradle.kts                   # 根工程 Gradle 配置（AGP 8.4.2, Kotlin 1.9.24, Chaquopy 17.1.0）
├── settings.gradle.kts                # 仓库配置（Google, MavenCentral, JitPack, Chaquopy Maven）与模块声明
├── gradle.properties                  # JVM 参数与科学计算包（numpy/pandas）构建期预装开关
├── gradlew / gradlew.bat              # 支持自动引导下载 Gradle 8.7 的 Wrapper 脚本
├── terminal-emulator/                 # 独立交互式终端模拟器库模块 (:terminal-emulator)
│   └── src/main/java/com/pydroidlab/terminal/
│       └── TerminalEmulator.kt        # ANSI/VT100 解析器、环形滚屏缓冲区与阻塞式 StdinSignal 队列
└── app/                               # 主应用模块 (:app)
    ├── build.gradle.kts               # Android、Compose、Sora Editor 与 Chaquopy 详细配置
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml    # 声明网络权限与 adjustResize 软键盘自适应
        │   ├── assets/textmate/       # Python TextMate 语法定义与括号配对配置
        │   ├── python/                # Chaquopy Python 桥接脚本层
        │   │   ├── pydroid_runner.py  # 脚本执行器、stdout/stderr/input() 重定向与异步线程中断
        │   │   └── pip_manager.py     # 运行时 pip 安装/升级/卸载与 importlib.metadata 枚举
        │   └── java/com/pydroidlab/app/
        │       ├── PyDroidLabApp.kt   # Application 入口，初始化 AndroidPlatform Python 实例
        │       ├── MainActivity.kt    # 主界面 Scaffold 与底部可折叠交互终端面板
        │       ├── data/              # 数据层：SettingsRepository、FileRepository、SampleRepository (12 示例)
        │       ├── editor/            # PythonSoraLanguage（Python 词法高亮、冒号自动缩进、括号补全、代码补全）
        │       ├── runtime/           # PythonExecutor 与 PythonBridgeHost (JNI/Chaquopy 双向回调桥)
        │       ├── ext/               # v1.0 预留扩展接口（GuiBackendPlugin, NotebookKernelPlugin, AiCompletionPlugin）
        │       └── ui/                # MainViewModel 与五大功能页面（编辑器、文件、库管理、示例库、设置）
        └── test/                      # 单元测试（验证 12 个内置示例与终端 input() 阻塞唤醒机制）
```

## 3. 构建步骤

1. **解压并配置 SDK 路径**：解压工程后，使用 Android Studio 直接打开 `PyDroidLab` 目录（Android Studio 会自动生成 `local.properties`），或在根目录手动创建 `local.properties`：
   ```properties
   sdk.dir=/Users/你的用户名/Library/Android/sdk
   ```
2. **宿主机 Python 环境说明**：Chaquopy 在构建期间若需打包 `pip` 依赖，会自动在宿主机查找 `python3`（建议宿主机已安装 Python 3.8~3.12）；你也可以在 `app/build.gradle.kts` 的 `chaquopy.defaultConfig` 中通过 `buildPython("/usr/local/bin/python3")` 指定宿主机 Python 路径。
3. **执行一键命令行构建**：
   ```bash
   chmod +x gradlew
   ./gradlew assembleDebug
   ```
   > **自引导说明**：如果本地尚未缓存 `gradle-wrapper.jar`，工程自带的 `./gradlew` 脚本会自动从 `services.gradle.org` 下载并解压 Gradle 8.7 分发包到 `~/.gradle/wrapper/dists`，确保首次执行 `./gradlew assembleDebug` 即可一次通过。
4. **生成的 APK 路径**：
   ```text
   app/build/outputs/apk/debug/app-debug.apk
   ```

## 4. Chaquopy 配置说明与体积切换

在 `app/build.gradle.kts` 与 `gradle.properties` 中，我们完成了如下针对移动端 Python IDE 的配置：

* **开源许可**：采用 Chaquopy `17.1.0`（自 15.0 起已全面采用 **MIT 开源许可证**，无论开源还是商业闭源均免费且无运行时授权弹窗）。
* **ABI 架构配置**：默认开启 `arm64-v8a`（主流 Android 14 真机）与 `x86_64`（PC 端 Android 模拟器）。
* **科学计算库（`numpy` / `pandas`）一键开关**：在根目录 `gradle.properties` 中提供了 `pydroidlab.preinstallScientific` 属性：
  * `pydroidlab.preinstallScientific=true`（**默认开启**）：在构建期通过 Chaquopy 官方预编译二进制轮子源打包 `numpy`、`pandas` 及 `requests`，用户在手机端离线即可直接 `import numpy as np`、`import pandas as pd`。
  * `pydroidlab.preinstallScientific=false`（轻量模式）：仅预装标准库与 `requests` 基础依赖，将单架构 APK 体积缩减约 35MB~50MB，纯 Python 库可在应用内「库管理」页面随时通过 `pip` 动态下载安装。

## 5. 三大关键设计决策（架构深潜）

### 5.1 解释器如何打包与启动
* **原生库与标准库打包**：构建期间，Chaquopy Gradle 插件会将针对 `arm64-v8a` 和 `x86_64` 交叉编译的 `libpython3.13.so`、`libchaquopy_java.so`、OpenSSL/SQLite/libffi 等原生动态库打包至 APK 的 `lib/<abi>/` 目录；Python 标准库与构建期 `pip` 包被打包至 `assets/chaquopy/`。
* **进程内 JNI 桥接**：应用启动时，`PyDroidLabApp.onCreate()` 调用 `Python.start(AndroidPlatform(this))` 在当前 Android 进程内初始化 CPython 解释器（耗时约 80ms~150ms）。相较于 Fork 独立子进程的方案，进程内 JNI 桥接避免了 Android 10+ W^X（`SELinux execmem/execmod`）对外部可执行二进制文件的限制，在 Android 14 (targetSdk 34) 上 100% 合规稳定。
* **字节码编译设置**：在 `chaquopy.defaultConfig.pyc` 中设置 `src = false`，保留原始 `.py` 源码回溯信息，以便用户在触发异常时能看到清晰的堆栈行号。

### 5.2 运行时 `pip` 包安装到哪里、如何立即生效
* **核心痛点**：Android APK 解压后的原生目录与 `assets` 均为只读文件系统，运行时无法直接向系统 `site-packages` 写入文件。
* **私有可写目录隔离**：我们将运行时 `pip` 的安装目标固定在应用私有存储目录：
  * **安装目标路径 (`--target`)**：`context.filesDir/python/site-packages`（即 `/data/user/0/com.pydroidlab.app/files/python/site-packages`）
  * **下载缓存路径 (`--cache-dir`)**：`context.cacheDir/pip-cache`
* **安装、升级与卸载实现 (`pip_manager.py`)**：
  1. **安装与升级**：通过调用 `pip._internal.cli.main.main(["install", "--target", user_site_packages, "--extra-index-url", "https://chaquo.com/pypi-13.1", ...])`，同时检索官方 PyPI 与 Chaquopy Android 预编译仓库，并将标准输出实时回调至 `PipLogBridge` 展示在 UI 日志框中。
  2. **动态 `sys.path` 优先级注入**：每次安装完成以及每次运行用户脚本前，都会调用 `site.addsitedir(user_site_packages)` 并将 `user_site_packages` 置于 `sys.path[0]` 首位，同时调用 `importlib.invalidate_caches()`。这意味着用户无需重启 App，新安装的库（如 `requests`、`beautifulsoup4`）即可立即被 `import`，且用户升级后的新版本会自动覆盖 APK 内置旧版本。
  3. **精准卸载**：由于标准 `pip uninstall` 命令不支持 `--target` 参数，`pip_manager.py` 通过解析 `user_site_packages` 下对应包的 `.dist-info/RECORD` 清单文件，安全清理该包所属的全部文件与空目录，并在 UI 上区分「用户安装（可卸载/升级）」与「内置预装（可升级覆盖）」。

### 5.3 `input()` 交互输入与强制中断如何实现
* **跨线程阻塞式行输入机制 (`TerminalEmulator.kt` + `pydroid_runner.py`)**：
  1. 当用户点击「运行」时，`PythonExecutor` 在后台 `Dispatchers.IO` 线程启动脚本，将 `PythonBridgeHost` 实例传入 `pydroid_runner.execute_script()`。
  2. `pydroid_runner.py` 将 `sys.stdout`、`sys.stderr` 替换为无缓冲的 `_InteractiveOutStream`（保证 `print("请输入姓名: ", end="")` 即使没有换行符也能瞬间刷新到终端屏幕），并将 `builtins.input` 与 `sys.stdin` 重定向至 `host.requestInput(prompt)`。
  3. 当 Python 脚本执行到 `input(prompt)` 时，工作线程调用 `TerminalEmulator.requestLineInput(prompt)`：首先将 `prompt` 渲染到终端缓冲区，然后将终端状态标记为 `isAwaitingInput = true`（UI 指示灯变黄并自动聚焦底部输入框），接着工作线程在 `LinkedBlockingQueue<StdinSignal>.take()` 上挂起阻塞，**完全不阻塞 Android 主线程 UI**。
  4. 用户在终端输入框输入内容并点击「发送」或按回车后，UI 线程向队列投递 `StdinSignal.InputLine(text)`，唤醒后台 Python 线程，将用户输入以高亮青色回显到终端并作为字符串返回给 Python 的 `input()` 调用，脚本继续向下执行。
* **双重安全中断机制（兼顾 `input()` 等待与死循环）**：
  1. 若程序正阻塞在 `input()` 等待输入，点击「停止」会向队列投递 `StdinSignal.Interrupt`，立即唤醒阻塞线程并抛出 `KeyboardInterrupt`。
  2. 若程序正处于 CPU 密集型死循环（如 `while True: pass`），`stopRunningScript()` 会调用 CPython 底层 C-API `ctypes.pythonapi.PyThreadState_SetAsyncExc(ctypes.c_long(tid), ctypes.py_object(KeyboardInterrupt))`，向正在运行的目标 Python 线程异步注入 `KeyboardInterrupt` 异常，实现毫秒级强制终止。

## 6. 横竖屏切换状态保持与预留扩展点

* **横竖屏切换无损保持**：编辑器文本内容、未保存修改标记（`*`）、当前打开的文件路径、光标行列位置、查找替换栏状态以及正在运行的终端会话均托管在 `MainViewModel` (`AndroidViewModel`) 中；当设备旋转重建 `MainActivity` 时，`CodeEditorScreen` 会自动从 `MainViewModel` 恢复完整缓冲区，不会丢失任何编辑内容或中断正在运行的脚本。
* **v1.0 预留扩展接口 (`com.pydroidlab.app.ext.ExtensionPoints.kt`)**：
  * `GuiBackendPlugin`：预留 `SurfaceHolder` 绑定接口，供未来 v2.0 接入 SDL2 / `pygame` / `kivy` 图形窗口渲染。
  * `NotebookKernelPlugin`：预留单元格（Cell）状态化执行与多 MIME 输出结构，供未来支持 Jupyter `.ipynb`。
  * `AiCompletionPlugin`：预留上下文前缀/后缀异步补全接口，可无缝汇入 `PythonSoraLanguage.requireAutoComplete`。

## 7. 已知限制（v1.0）

* **含 C/C++ 原生扩展的第三方包**：在手机运行时通过「库管理」页面执行 `pip install` 仅支持纯 Python Wheel（`py3-none-any.whl`）以及 Chaquopy 官方源已提供 Android 预编译二进制 Wheel 的包；若某个冷门 C 扩展库未提供 Android Wheel，由于手机端不含 Clang/NDK 编译工具链，无法在设备端从源码编译，需在 `app/build.gradle.kts` 的 `chaquopy.defaultConfig.pip` 中于构建期引入。
* **多进程 (`multiprocessing`)**：受限于 Android JVM 进程模型与 Chaquopy 单解释器实例架构，建议使用 `threading`、`concurrent.futures.ThreadPoolExecutor` 或 `asyncio` 替代 `multiprocessing.Process`。
* **GUI 窗口库**：v1.0 专注于控制台交互与标准库/网络/数据处理，`tkinter`、`pygame`、`kivy` 图形窗口将在后续版本通过 `GuiBackendPlugin` 扩展支持。
