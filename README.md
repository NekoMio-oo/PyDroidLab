# PyDroidLab

在 Android 手机上写、跑、调试 Python 的**一体化工作台**。

内嵌 CPython 3.11（Chaquopy），界面全部用 Jetpack Compose Material 3 重写，
代码编辑器基于 [sora-editor](https://github.com/Rosemoe/sora-editor)，
终端基于 [jackpal/Android-Terminal-Emulator](https://github.com/jackpal/Android-Terminal-Emulator)。

功能清单与入口路径见 [FEATURES.md](FEATURES.md)。

| 项目 | 当前值 |
| --- | --- |
| 版本 | **1.1.2**（`versionCode = 4`） |
| 应用 ID | `com.pydroidlab.app` |
| Python | 3.11（Chaquopy 15.0.1） |
| minSdk / targetSdk | 26 / 34 |
| 变更历史 | 见 [§8 更新日志](#8-更新日志) |

---

## 1. 快速开始

### 1.1 环境要求

| 组件 | 版本 | 说明 |
| --- | --- | --- |
| JDK | 17 或以上（实测 20 可用） | AGP 8.5 要求 ≥ 17 |
| Android SDK | Platform 34 + Build-Tools 34.0.0 | `compileSdk = 34` |
| Android NDK | **不需要单独安装** | Chaquopy 的原生库已预编译并 strip 过 |
| 构建机 Python | 3.8 – 3.12（用于 pip 打包） | Chaquopy 会自动查找 `python3.11` → `python3` |
| Gradle | 由 wrapper 提供（8.9） | 无需本机安装 |
| 网络 | 构建期需要 | 下载依赖 + Chaquopy 运行时 + 预装 pip 包 |

### 1.2 配置 Android SDK 路径

在工程根目录建 `local.properties`（该文件不应提交到版本库）：

```properties
sdk.dir=/path/to/Android/Sdk
```

或者设置环境变量 `ANDROID_HOME` / `ANDROID_SDK_ROOT`（Chaquopy 允许没有
`local.properties`，只要能找到 SDK）。

### 1.3 构建

```bash
# Debug APK
./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk

# 安装到已连接的设备
./gradlew installDebug

# Release（开启 R8 + 资源压缩）
./gradlew assembleRelease
```

首次构建会做这些比较慢的事（合计 3–8 分钟）：

1. 下载 Gradle 8.9 发行版与全部 Maven 依赖
2. Chaquopy 下载 CPython 3.11 的 Android 运行时与三个 ABI 的原生库
3. 在**构建机**上跑 pip，把 `black` 与 `requests` 及其依赖打进 APK

后续构建走缓存，通常 20–60 秒。

### 1.4 三个 ABI

`app/build.gradle.kts` 里声明了：

```kotlin
ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86_64") }
```

每个 ABI 都会显著增加 APK 体积（Python 运行时本身约 10 MB/ABI）。
只给现代真机打包时，可以只保留 `arm64-v8a`：

```kotlin
ndk { abiFilters += listOf("arm64-v8a", "x86_64") }   // 真机 + 模拟器
```

---

## 2. Chaquopy 配置说明（重要）

### 2.1 版本变更：12.0.0 → 15.0.1

接手时的工程声明了 `id("com.chaquo.python") version "12.0.0"`，但这与既定的
Python 3.11 / AGP 8.5 技术栈**直接冲突**：

| Chaquopy | 支持的 Python | 支持的 AGP |
| --- | --- | --- |
| 12.0.0 | **仅 3.8** | 4.1 – **7.2** |
| 13.0.0 | 3.8 – 3.11 | 4.1 – 7.3 |
| 14.0.2 | 3.8 – 3.11 | 4.1 – 8.0 |
| **15.0.1（本次采用）** | **3.8 – 3.12** | **7.0 – 8.5** |

同时 Chaquopy 13 起把插件发布到了 **Maven Central**（`chaquo.com/maven` 只镜像
到 12.0.0），所以 `settings.gradle.kts` 里的 `mavenCentral()` 必须存在。
选用 **15.0.1** 是因为它是最后一个官方标注支持 AGP 8.5 的版本。

> 这属于构建配置变更，没有改动任何 Kotlin 后端类的签名。

### 2.2 配置块位置变了

Chaquopy 15 的 Kotlin DSL 使用**顶层 `chaquopy { }` 扩展**，
原来的 `android.defaultConfig.python { }` 只在 ≤ 12 的 Groovy 构建里有效：

```kotlin
chaquopy {
    defaultConfig {
        version = "3.11"
        pip {
            install("black")
            install("requests")
            options("--no-cache-dir")
        }
        extractPackages("tutorials", "snippets")
    }
    sourceSets {
        getByName("main") {
            srcDir("src/main/python/bootstrap")
        }
    }
}
```

### 2.3 为什么必须加 `srcDir("src/main/python/bootstrap")`

Chaquopy 默认只把 `src/main/python` 本身加进 `sys.path`。
但 Kotlin 侧是**按顶层模块名**调用 Python 的：

```kotlin
Python.getInstance().getModule("pdl_runner")   // 不是 bootstrap.pdl_runner
Python.getInstance().getModule("pdl_pip")
```

`bootstrap/` 目录下的 5 个桥接脚本（外加本次新增的 3 个）如果不额外声明为源码根，
`getModule("pdl_runner")` 会在运行时抛 `ModuleNotFoundError`。
加上这一行之后，`bootstrap/` 与 `python/` 同时成为源码根，两边都能按顶层名字导入。

### 2.4 为什么需要 `extractPackages`

教程（Markdown 讲义）与代码片段是**数据文件**而不是模块。
Chaquopy 默认把 `.py` 直接打进 APK，不会在设备上落地成真实文件。
声明 `extractPackages("tutorials", "snippets")` 后，它们会被解压成真实文件，
`pdl_resources.py` 就能用 `os.listdir` / `open` 读取，供「教程」页展示。

---

## 3. 为跑通构建所做的其它必要修正

### 3.1 `gradle.properties`

```properties
org.gradle.jvmargs=-Xmx2048m -XX:MaxMetaspaceSize=768m -Dfile.encoding=UTF-8
kotlin.daemon.jvmargs=-Xmx1536m
org.gradle.workers.max=2
org.gradle.caching=true
org.gradle.configuration-cache=false   # ← 必须
org.gradle.parallel=false              # ← 必须
org.gradle.daemon=true
```

- **关闭配置缓存**：Chaquopy 15 的 `BuildPackagesTask` 在配置缓存序列化阶段会
  强制初始化 `sitePackages`，直接报
  `app/build/python/env/debug/lib does not exist`。
- **关闭并行**：Kotlin 2.0.x 与 Chaquopy 共存时，并行配置会在
  `:app:compileDebugKotlin` 的 `friendPathsSet` 上抛
  `Current thread does not hold the state lock for root project`。
- **下调堆内存**：Gradle 守护进程 + Kotlin 守护进程 + R8/D8 worker 会同时驻留，
  在 8 GB 内存的容器里容易被 OOM Killer 干掉（表现为
  `Gradle build daemon disappeared unexpectedly`）。内存充裕的开发机可以调回
  `-Xmx4096m`。

### 3.2 预置代码里 4 处无法编译的真实签名问题

这些文件属于「已完成」部分，但不能编译，因此按最小改动修正，**未改任何类/方法签名**：

| 文件 | 问题 | 修正 |
| --- | --- | --- |
| `ui/editor/CodeEditorView.kt` | `textSize = s.fontSizeSp` —— sora 只有 `setTextSize(float)` / `getTextSizePx()`，没有 `getTextSize()`，Kotlin 合成不出属性 | 改为 `setTextSize(s.fontSizeSp)` |
| `ui/editor/PythonLanguage.kt` | `TextStyle.makeStyle(slot, 0, bold, italic)` —— 没有 4 参数重载 | 补第 5 个 flag：`makeStyle(slot, 0, bold, italic, false)` |
| `ui/editor/PythonLanguage.kt` | `IdentifierAutoComplete.requireAutoComplete(content, position, publisher, extras)` —— sora 0.23 的签名多了前缀参数 | 先算 `prefix`，再调用 5 参数版 `(content, position, prefix, publisher, null)` |
| `python/HostBridge.kt` | `mod["key"] = value` —— `PyObject` 继承 `AbstractMap<String, PyObject>`，Kotlin 的 `set` 会绑定到 `put(String, PyObject)` | 改用显式 `mod.put("key", value)`（`put(String, Object)` 重载） |
| `python/DebuggerController.kt` | `PyObject.asDict()` 在新版 Chaquopy 已移除 | 改为 `asMap()` |

### 3.3 新增的 4 个 Python 桥接脚本

`bootstrap/` 下原有 5 个脚本保持不变，新增：

| 文件 | 用途 |
| --- | --- |
| `pdl_shell_exec.py` | 内置终端里执行 `python -c` / `python file.py` / `pip`，并**捕获**输出一次性返回 |
| `pdl_format.py` | 用 black 格式化源码；black 不可用或语法错误时**原样返回**，绝不丢代码 |
| `pdl_resources.py` | 读取 `tutorials/` 与 `snippets/` 里的讲义与片段文本 |
| `pdl_complete.py` | 扫描 `import` 语句、取模块成员，供编辑器补全（打开/保存/运行时刷新一次，按键时读缓存） |
| `pdl_interrupt.py` | 真正可用的中断：`PyThreadState_SetAsyncExc` 注入 `KeyboardInterrupt` |

### 3.4 `PythonRunner.interrupt()` 的一处内部增强（签名未变）

原来的 `interrupt()` 只取消协程 + 解除 stdin 阻塞，**无法**中断已经在执行的
Python 字节码，即 `while True: pass` 停不下来 —— 而这是明确的验收标准。

因此在**不改方法签名**的前提下，在 `interrupt()` 内补了一次
`pdl_interrupt.request()`（放到独立线程里执行，避免 GIL 争用卡住 UI）。
注入机制不可用时静默降级为原有行为，不会比之前更差。
`pdl_runner` / `pdl_debugger` / `pdl_repl` / `pdl_shell_exec` 在执行用户代码前
会调用 `pdl_interrupt.arm()` 记录线程 id。

---

## 4. 工程结构

```
PyDroidLab/
├── build.gradle.kts / settings.gradle.kts / gradle.properties
├── gradlew / gradlew.bat / gradle/wrapper/          ← 本次补齐
└── app/
    ├── build.gradle.kts                             ← Chaquopy 15 配置
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml                      （已有，含 SEND/VIEW .py）
        ├── res/                                     ← 本次补齐
        │   ├── values/{strings,colors,themes}.xml
        │   ├── values-night/themes.xml
        │   ├── drawable/ic_launcher_foreground.xml
        │   ├── mipmap-anydpi-v26/ic_launcher{,_round}.xml
        │   └── mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher{,_round}.png
        ├── java/com/pydroidlab/app/
        │   ├── PyDroidLabApp.kt
        │   ├── MainActivity.kt                      ← 本次新增
        │   ├── core/{ServiceLocator,Events}.kt      （已有）
        │   ├── project/{ProjectManager,RecentFiles}.kt （已有）
        │   ├── python/                              （已有 + 2 处签名修正）
        │   ├── shell/PyShell.kt                     ← 本次新增
        │   ├── terminal/{PdlTerminalSession,TerminalController}.kt  ← 本次新增
        │   └── ui/
        │       ├── AppViewModel.kt                  ← 本次新增
        │       ├── AppNavigation.kt                 ← 本次新增（导航 + 抽屉）
        │       ├── UiCommon.kt                      ← 本次新增（公共组件）
        │       ├── editor/{CodeEditorView,EditorState,PythonLanguage,EditorThemes}.kt （已有）
        │       ├── editor/EditorScreen.kt           ← 本次新增
        │       ├── console/ConsoleScreen.kt         ← 本次新增
        │       ├── terminal/TerminalScreen.kt       ← 本次新增
        │       ├── files/FilesScreen.kt             ← 本次新增
        │       ├── packages/PackagesScreen.kt       ← 本次新增
        │       ├── charts/ChartsScreen.kt           ← 本次新增
        │       ├── debugger/DebuggerScreen.kt       ← 本次新增
        │       ├── repl/ReplScreen.kt               ← 本次新增
        │       ├── learn/LearnScreen.kt             ← 本次新增
        │       ├── settings/{SettingsRepository,SettingsScreen}.kt （已有 + 新增）
        │       └── theme/Theme.kt                   ← 本次新增
        └── python/
            ├── bootstrap/                           （已有 5 个 + 新增 3 个）
            ├── snippets/                            ← 本次新增 10 个片段
            └── tutorials/                           ← 本次新增 9 课（讲义 + 示例）
```

---

## 5. 已知限制

请如实了解这些边界，它们都是**平台层面的限制**，不是未完成的 TODO。

### 5.1 终端没有真 shell

jackpal 的 JitPack 制品 `com.github.jackpal:Android-Terminal-Emulator:1.0.70`
的 `classes.jar` 里**只有 `emulatorview` 模块**，不包含 `libtermexec` 的 JNI
（那是 fork/exec 真 pty 所必需的）。Android 上要拿到真 pty 需要 root 或
`Runtime.exec("/system/bin/sh")` + pty 分配，两者都超出普通应用权限。

因此「终端」页是这样实现的：

- 显示层是**真正的 VT100 模拟器**（`jackpal.androidterm.emulatorview.EmulatorView`）
- 输入输出通过 `PdlTerminalSession` 的两条 `PipedInputStream/OutputStream` 接到
  宿主自己实现的命令解释器（`shell/PyShell.kt`）
- 支持命令：`help` `clear` `pwd` `ls` `cd` `cat` `mkdir` `touch` `rm` `echo`
  `open` `run` `python [-c code | file.py]` `pip install|list`
- **不支持**任意可执行文件（`git`、`ls` 等系统命令），因为那需要真 pty

需要完整功能请用「控制台」+「编辑器」。

### 5.2 终端里的 `python` 不支持 `input()`

`pdl_shell_exec.py` 把 stdout/stderr 捕获成字符串一次性返回，
所以没有 stdin 通道。要在终端里跑带 `input()` 的脚本，请改用「编辑器 → 运行」。

### 5.3 中断：CPU 死循环可以，C 层阻塞不行

`PythonRunner.interrupt()` 现在做三件事：

1. `HostBridge.cancelStdin()` —— 解除卡在 `input()` 的阻塞；
2. `pdl_interrupt.request()` —— 通过 CPython 的 `PyThreadState_SetAsyncExc`
   往执行线程的下一个字节码边界注入 `KeyboardInterrupt`；
3. 取消 Kotlin 协程。

第 2 条是本次补的（方法签名未变）。**实测** `while True: i += 1` 会在约
4300 万次迭代后被成功打断并恢复正常流程。

**仍然不行的情况**：脚本卡在 `time.sleep(3600)`、`socket.recv()` 这类
C 层阻塞调用里 —— CPython 只在字节码边界检查异步异常，而 Android 又无法给
非主线程投递信号（没有 `pthread_kill` 到 Python 线程的通道），所以这时最可靠
的仍然是强杀 App。控制台顶部的「中断」按钮已经把该做的前两步都做了。

### 5.4 matplotlib 中文字体

Android 上不一定存在 matplotlib 能识别名字的中文字体，标题可能显示成方框。
`pdl_runtime.install_matplotlib_bridge()` 已经设置 `axes.unicode_minus`，
但 sans-serif 字体需要脚本自己指定（教程第 8 课给了自动探测的代码）。

### 5.5 科学计算包体积与安装时间

`numpy` / `pandas` / `matplotlib` / `scipy` / `scikit-learn` 都是**带原生扩展**的
Android wheel，合计数百 MB，只能通过「软件包」页在运行时按需安装，
**没有**打进 APK（否则 APK 会超过 1 GB）。建议在 WiFi 下安装。

### 5.6 `black` 格式化是「可选增强」

`black` 由构建期 pip 预装。如果执行环境里 black 导入失败，
`pdl_format.format_code()` 会**原样返回源码**并给出「无需格式化（或 black 不可用）」
的提示，不会丢代码。勾选「保存时用 black 格式化」也一样有兜底。

### 5.7 其它

- `assets/` 目录为空，Chaquopy 的 Python 资源不经过它
- Release 构建开启 R8；`proguard-rules.pro` 已 keep 住 Chaquopy / sora-editor /
  terminal 三个包。此工程只验证了 `assembleDebug`
- 竖屏专属布局未做；所有界面在横竖屏下都能用，靠 Compose 自适应

---

## 6. 自测路径（对应验收标准）

| # | 验收项 | 操作 |
| --- | --- | --- |
| 1 | 新建文件 → 含 input 的脚本 → 运行 → 交互输入 → 输出正确 | 编辑器 →「新建」→ 粘贴 `02_input_output_example.py` 的内容 →「保存」→「运行」→ 控制台底部输入 → 输出正确 |
| 2 | 死循环可中断 | 编辑器写 `while True: pass` →「运行」→ 控制台点「中断」 |
| 3 | pip 装 requests 并使用 | 软件包 → 切清华源 → 输入 `requests` → 安装 → 编辑器跑 `import requests; print(requests.__version__)` |
| 4 | matplotlib 画正弦并展示、保存 | 软件包 →「科学计算栈」或分别装 `numpy`/`matplotlib` → 运行 `08_numpy_matplotlib_example.py` → 图表页出现 5 张图 →「保存 PNG」→ 文件页可见 |
| 5 | 断点 → 单步 → 变量监视正确 | 运行 `07_errors_debug_example.py`，在 `total += values[i]` 打断点 → 调试页「开始调试」→ 反复「下一行」→ 变量页看到 `i`、`values` |
| 6 | 横竖屏切换不丢编辑内容 | 在编辑器里输入内容 → 旋转屏幕 → 内容与光标位置保留 |

更细的入口路径见 [FEATURES.md](FEATURES.md)。

---

## 7. 许可与致谢

- [Chaquopy](https://chaquo.com/chaquopy/) —— 内嵌 CPython
- [sora-editor](https://github.com/Rosemoe/sora-editor) —— 代码编辑器
- [Android-Terminal-Emulator](https://github.com/jackpal/Android-Terminal-Emulator) —— VT100 模拟器（Apache-2.0）
- Jetpack Compose / Material 3 —— UI

---

## 8. 更新日志

### v1.1.2 —— 2026-10-13（`versionCode = 4`）

Phase 0：修复 7 个真机 bug（详见 FEATURES.md 的 Phase 0 记录表）。

| Bug | 修复 |
| --- | --- |
| 补全/高亮不可见 | 分析线程 try/catch 加固（logcat TAG `PdlEditor`）+「输入法联想时仍显示补全」开关 + 设置页「编辑器诊断」面板 |
| 未命名文件不能直接运行/保存 | 自动落盘 `filesDir/scratch/`，标签显示「未命名-N（临时）」，全程不弹窗 |
| 控制台无视觉区分 | `ConsoleLine` 按 kind 分色；结束行带退出码与耗时（`PythonRunner` 公开签名未变） |
| 终端字体巨大 | jackpal `setTextSize` 参数是 dp，删掉多余的 density 乘法（factory/update 两处） |
| REPL 无回显 | `compile_command` 改 `symbol="single"`，表达式按原生 REPL 回显 |
| 括号配对无脑 | 引号避让单词字符（`SymbolPairEx`）+ 右括号 skip-over（内容事件拦截）+ 符号栏统一走 `commitText` |

### v1.1.1 —— 2026-10-12（`versionCode = 3`）

修掉一个**必然触发**的运行崩溃 + 一处排版缺陷。

| 类别 | 变更 |
| --- | --- |
| **运行崩溃** | 运行任何脚本都会报 `AttributeError: 'LinkedHashMap' object has no attribute 'items'`。原因是 Kotlin 的 `Map<String,String>`（env）经 Chaquopy 传过来是 **Java 对象**，没有 `.items()`；而 Java 包装对象**没有 `__len__`**，所以 `(env or {})` 这类兜底对**空 Map 也失效**。新增 `bootstrap/pdl_utils.py`（`as_dict` / `as_list` / `as_str` / `as_str_list`），在 `pdl_runner`（env、argv、cwd）、`pdl_debugger`（argv、cwd）、`pdl_shell_exec`（python 与 pip 的参数）、`pdl_complete`（模块名列表）全部边界入口统一转换 |
| **界面排版** | 编辑器 / 图表页的空状态没有宽度约束，长文案固有宽度超出屏宽后居中对齐失效、整块贴左溢出。改为 `fillMaxWidth` + 水平内边距 + `textAlign = Center`；`EmptyHint` 同样处理。图表页的示例代码改用等宽字体 + 横向滚动，不再折行 |
| 文档 | 本节新增 |

### v1.1.0 —— 2026-10-12（`versionCode = 2`）

对照原始需求自查后补齐的功能（逐条状态见 FEATURES.md 的 A/B 两节）：

| 类别 | 变更 |
| --- | --- |
| 编辑器 | 行号点击设断点 + gutter 红点渲染；长按取词查 docstring；光标位置实时上报；行高设置真正生效；模块成员**点号补全**（`pdl_complete.py`）；全词匹配 / 区分大小写查找；撤销重做；分享代码；标签滑动关闭；最近打开列表 |
| 运行 | 新增「运行配置」（argv / cwd / env / stdin 预设，`RunConfigStore`）；控制台输出**保存为文件** |
| 中断 | `pdl_interrupt.py`：CPU 死循环可真正打断（`PythonRunner.interrupt()` 方法签名未变） |
| 终端 | 多会话标签 |
| 学习区 | 示例库按难度分类、示例一键导入编辑器 |
| 系统 | 外接键盘快捷键（Ctrl+S/R/F/H 等，设置页可改）；项目备份 zip 后直接拉起分享（新增 FileProvider） |
| 架构 | `runtime/ScriptRuntime.kt` —— pygame / kivy 等外部运行时的 v2 解耦扩展点（仅接口预留） |
| 其它 | 设置页「关于」改为读取 `BuildConfig.VERSION_NAME`，不再硬编码 |

### v1.0.0 —— 首版

Kotlin + Compose Material 3 + Chaquopy 15.0.1 + sora-editor + jackpal 终端；
10 个导航页、11 个 Python 桥接脚本、9 课教程 + 10 个代码片段。
