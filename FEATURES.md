# PyDroidLab 功能清单

> 入口路径统一从 **App 启动后的默认页面（编辑器）** 开始描述。
> 「导航抽屉」指左上角 ☰ 打开的侧边菜单，共 10 项 +
> 「编辑器 / 控制台 / 终端 / 文件 / 软件包 / 图表 / 调试 / REPL / 教程 / 设置」。

---

## 0. Phase 0 真机 Bug 修复记录（v1.1.2）

| # | 真机问题 | 根因 | 修复 | 验证方式 |
| --- | --- | --- | --- | --- |
| 1/2 | 代码补全 / 高亮不可见 | 代码层面无 API 误用（已对照 sora 0.23.6 源码核验），按排查加固 | ① `PythonAnalyzeManager.analyze` 全量 try/catch，异常打 logcat（TAG `PdlEditor`）、上报诊断并回退纯 TEXT_NORMAL，分析线程不再静默死掉；② 新设置「输入法联想时仍显示补全」映射 `props.autoCompletionOnComposing`（默认开）；③ 设置 → 诊断 → 「编辑器诊断」：语言挂载状态 / 最近分析结果 / 输入 `pri` 的候选数 | 设置页诊断面板；logcat 过滤 `PdlEditor` |
| 3 | 未命名文件点运行/保存被要求先「另存为」 | `runActiveScript` / `saveActive` 直接拒绝 `tab.file == null` | 自动落盘到 `filesDir/scratch/untitled_<n>.py`（目录不存在则创建），绑定后继续，全程不弹窗；标签与状态栏显示「未命名-N（临时）」；「另存为」保留 | 新建标签 → 直接点「运行」→ 应正常出结果 |
| 4 | 控制台运行结果无视觉区分 | stdout 与运行横幅同色 | `ConsoleLine` 增加 kind（HEADER/STDOUT/STDERR/FOOTER）分色：横幅 primary、stderr error、结束行弱化；结束行改为「■ 进程结束 · 退出码 X · 用时 Ys」（`PythonRunner` 记录开始时间，公开签名未变，退出码/耗时经控制台文本传出） | 运行任意脚本，横幅与结束行颜色应明显不同 |
| 5 | 终端字体巨大 | `EmulatorView.setTextSize` 传了 px（sp × density），jackpal 内部再乘一次 density，density 被乘两次 | factory 与 update 两处改为直接传 dp：`setTextSize(fontSizeSp.roundToInt())` | 终端字号应与编辑器设置一致 |
| 6 | REPL 输入表达式无回显 | `codeop.compile_command(symbol="exec")` 不走 displayhook | 改为 `symbol="single"`，表达式语句按原生 REPL 回显 repr；赋值不回显、多行块与 incomplete 判断不变 | REPL 输入 `1+1` 应显示 `2`；输入 `x=5` 不应显示；`def f():` 多行块照常 |
| 7 | 括号自动配对无脑 | sora `SymbolPairMatch` 无上下文策略、无 skip-over | ① 引号：光标前/后紧贴单词字符时不补对（`SymbolPairEx.shouldReplace`）；② 右括号/引号：光标右侧已是同一字符时 skip-over（`ContentChangeEvent` 拦截，删掉刚插入的并右移光标）；③ 左括号后跟单词字符仍配对（默认行为）；④ SymbolBar 改走 `commitText`，与键盘输入共用同一套逻辑，不双重处理；`autoPair` 关闭时整体停用 | 输入 `abc"` 只出一个引号；在 `foo(|)` 输入 `)` 应跳过而非出现两个 |

---

## A. 原始需求逐条对照表（v1.0 查漏）

图例：
**✅** 已实现，且已通过编译 / 逻辑验证 ·
**🟢** 已实现，交互部分需真机确认 ·
**⛔** 受上游库限制无法实现（已说明替代方案）

### A.1 技术栈

| 需求 | 状态 | 对应代码 / 说明 |
| --- | --- | --- |
| Kotlin + Jetpack Compose + Material 3 | ✅ | 全部 33 个 `.kt`；`ui/theme/Theme.kt` |
| compileSdk / targetSdk 34+，minSdk 26 | ✅ | `app/build.gradle.kts`：34 / 34 / 26 |
| 代码编辑组件 `io.github.Rosemoe:sora-editor` | ✅ | `editor:0.23.6`；`ui/editor/*` |
| Python 解释器嵌入 Chaquopy（含 pip） | ✅ | `com.chaquo.python` 15.0.1，Python 3.11，见 README §2 |
| 终端 terminal-emulator | ✅ | `com.github.jackpal:Android-Terminal-Emulator:1.0.70` |

### A.2 代码编辑器

| 需求 | 状态 | 对应代码 / 说明 |
| --- | --- | --- |
| 多标签页编辑 · 标签栏切换 | ✅ | `EditorScreen` `ScrollableTabRow` |
| 多标签页编辑 · 滑动关闭 | ✅ | `EditorScreen.swipeToClose`（拖动 >140px 关闭） |
| 多标签页编辑 · 未保存小圆点 | ✅ | `EditorTab.displayTitle` → `"标题 ●"` |
| Python 语法高亮 | ✅ | `PythonLanguage.PythonAnalyzeManager`（关键字/内置/字符串/数字/注释/运算符/函数名） |
| 行号 | ✅ | `isLineNumberEnabled` |
| **代码折叠** | ⛔ | sora 0.23.6 的 `Language` 接口**没有**任何折叠方法，`lang/folding/FoldingRegion.java` 也没有被 `CodeEditor` 使用（`CodeEditor` 中不存在任何 fold API）。上游在 0.22 前后移除了编辑器内置折叠。替代方案：编辑器支持「查找 + 跳转」与折叠无关的快速定位；**未做假折叠**（临时删行会破坏撤销栈与文件内容）。 |
| 自动缩进 | ✅ | `PythonLanguage.IndentHandler`（行尾 `:` 多缩进一级） |
| 括号 / 引号自动补全 | ✅ | `PythonSymbolPairs` + `props.symbolPairAutoCompletion` |
| 查找替换 · 正则 | ✅ | `EditorSearcher.SearchOptions.TYPE_REGULAR_EXPRESSION` |
| 查找替换 · 区分大小写 | ✅ | 对话框「区分大小写」开关（本次新增） |
| 查找替换 · 全词匹配 | ✅ | `TYPE_WHOLE_WORD`（本次新增） |
| 撤销 / 重做 | ✅ | 工具栏「撤销 / 重做」按钮 + sora 内置 Ctrl+Z / Ctrl+Y |
| 补全 · 关键字 | ✅ | `IdentifierAutoComplete(PYTHON_KEYWORDS)` |
| 补全 · 已导入模块成员 | ✅ | `pdl_complete.py` + `PythonLanguage` 点号前缀分支（`math.` → `math.pi`） |
| 补全 · 内置代码片段 | ✅ | `PythonLanguage.SNIPPETS`（for/def/class/try/ifmain/while/with/if/elif） |
| 保存时自动格式化（black） | ✅ | `pdl_format.py` + `EditorSettings.formatOnSave`；black 不可用时原样返回 |
| 长按符号查看 docstring 速览 | ✅ | `CodeEditorView` 订阅 `LongPressEvent` → `ReplController.docstring`（本次接线；此前该回调从未被触发） |

### A.3 运行与调试

| 需求 | 状态 | 对应代码 / 说明 |
| --- | --- | --- |
| 一键运行当前文件，运行前自动保存 | ✅ | `AppViewModel.runActiveScript` |
| 运行配置 · 命令行参数 | ✅ | `RunConfigStore` + `RunConfigDialog`（本次新增） |
| 运行配置 · stdin 预设 | ✅ | 同上 → `RunConfig.stdinPreset` |
| 运行配置 · 工作目录 | ✅ | 同上 → `RunConfig.cwd`（留空用脚本目录） |
| 运行配置 · 环境变量 | ✅ | 同上 → `RunConfig.env` |
| 交互式控制台 · 支持 `input()` | ✅ | `pdl_runtime._ConsoleIn` → `HostBridge.read_stdin`；控制台底部输入框 |
| 交互式控制台 · 中断（SIGINT） | ✅ | `PythonRunner.interrupt()` + `pdl_interrupt`（见 README §3.4 / §5.3） |
| 交互式控制台 · 清屏 | ✅ | 控制台「清屏」 |
| 输出可复制 | ✅ | 「复制」 |
| 输出可分享 | ✅ | 「分享」→ `ACTION_SEND` |
| 输出保存为文件 | ✅ | 「存为文件」（本次新增）→ 项目目录 `console_YYYYmmdd_HHMMSS.txt` |
| 调试器 · 行号点击设置断点 | ✅ | `ClickEvent` + `REGION_LINE_NUMBER`（本次接线），行号栏另有红色断点圆点 |
| 调试器 · 单步 / 进入 / 跳出 / 继续 | ✅ | `DebuggerScreen` 五个按钮 → `pdl_debugger` 的 `cmd_step/next/return/continue` |
| 调试器 · 变量监视 | ✅ | 「变量」页：关注列表 + 当前帧全部局部变量（已过滤 dunder 与超长值） |
| 调试器 · 调用栈查看 | ✅ | 「调用栈」页（已过滤 threading / importlib / bdb 等运行库帧） |
| 基于 pdb 封装 UI | ✅ | 实际用的是 `bdb`（`pdb` 的底层；`pdl_debugger` 预置实现） |
| 独立 Python REPL 入口 | ✅ | 抽屉 →「REPL」，支持多行块与补全 |

### A.4 包管理

| 需求 | 状态 | 对应代码 / 说明 |
| --- | --- | --- |
| pip install / upgrade / uninstall | ✅ | `PackagesScreen` + `pdl_pip` |
| 支持指定版本 | ✅ | 输入框接受 `requests==2.31.0` |
| PyPI / 清华镜像源切换 | ✅ | 软件包页与设置页均可切换 |
| 预置科学计算仓库一键安装 | ✅ | 「安装全部」→ `PackageManager.installScientific`（numpy/pandas/matplotlib/scipy/scikit-learn/requests/Pillow） |
| requirements.txt 导入 | ✅ | SAF 选文件 → `pip install -r` |
| requirements.txt 导出 | ✅ | `freeze()` → 写到当前项目目录 |
| 一键还原环境 | ✅ | 即「导入 requirements」 |
| 本地 .whl 离线安装 | ✅ | SAF 选 `.whl` → `pip install <file>` |

### A.5 图形输出

| 需求 | 状态 | 对应代码 / 说明 |
| --- | --- | --- |
| matplotlib Agg 后端内联渲染 | ✅ | `pdl_runtime.install_matplotlib_bridge()` |
| 独立「图表」面板分页展示 | ✅ | `ChartsScreen` 上一张 / 下一张 + `n / N` |
| 支持保存为图片 | ✅ | 「保存 PNG」；脚本也可自己 `savefig` |
| pygame / kivy 作为 v2 扩展点（架构预留解耦接口） | ✅ | `runtime/ScriptRuntime.kt`：`ScriptRuntime` 接口 + `ConsoleRuntimeProvider` + `RuntimeRegistry`，在 `PyDroidLabApp.registerRuntimes()` 注册。**v1.0 按非目标未实现 pygame/kivy 本身** |

### A.6 文件与项目管理

| 需求 | 状态 | 对应代码 / 说明 |
| --- | --- | --- |
| 项目制：每个项目独立目录 | ✅ | `ProjectManager` |
| 记住上次打开的项目 | ✅ | `ProjectManager.lastOpenedProject` |
| 文件浏览器 · 新建 / 重命名 / 删除 / 移动 / 复制 | ✅ | `FilesScreen` |
| 文件浏览器 · 多选批量操作 | ✅ | 勾选 Checkbox，支持批量删除 / 复制到 / 移动到 |
| SAF 导入外部文件 | ✅ | 「导入(SAF)」→ `OpenDocument` |
| 接收系统分享的 .py（intent-filter） | ✅ | `AndroidManifest` SEND / VIEW + `MainActivity.handleIntent` |
| 可分享代码文本 | ✅ | 编辑器工具栏「分享 / 复制」（本次新增） |
| 最近打开文件列表 | ✅ | 编辑器工具栏「最近」→ `RecentFilesDialog`（本次新增，复用已有 `RecentFiles`） |
| 新建文件模板（空 / 函数 / 类 / 脚本） | ✅ | `FileTemplates.ALL` |

### A.7 终端

| 需求 | 状态 | 对应代码 / 说明 |
| --- | --- | --- |
| 完整终端模拟器 | 🟢 | jackpal `EmulatorView`（真 VT100 渲染、颜色、滚动、软键盘） |
| **多会话标签** | ✅ | `TerminalScreen` 会话标签 + 「新会话」/「重置本会话」/ 关闭（本次新增） |
| 可直接执行 pip / python 命令 | ✅ | `pip install` / `python -c` / `python x.py` / `run x.py` |
| 完整 shell（`ls`/`git` 等外部命令） | ⛔ | 无真 pty（jackpal AAR 不含 libtermexec，普通应用也拿不到 pty）。已用内置命令集替代，见 README §5.1 |

### A.8 键盘与输入

| 需求 | 状态 | 对应代码 / 说明 |
| --- | --- | --- |
| 自定义符号工具栏（可编辑） | ✅ | `SymbolBar` + 设置页增删符号 + 恢复默认 |
| 外接键盘快捷键 Ctrl+S/R/F/H 等 | ✅ | `MainActivity.dispatchKeyEvent`（必须在 Activity 层拦截，原因见下） |
| 快捷键支持自定义 | ✅ | 设置页「外接键盘快捷键」逐项编辑，写入 `AppSettings.shortcuts` |

> 为什么快捷键不在 Compose 里处理：sora 的 `EditorKeyEventHandler` 会把
> 「Ctrl / Alt / Shift + 字母」全部派发成 `KeyBindingEvent` 并消费掉，
> 编辑器取得焦点后事件根本冒泡不到上层。`Ctrl+Z / Ctrl+Y` 有意**不**拦截，
> 交给 sora 维护撤销栈。

### A.9 外观

| 需求 | 状态 | 对应代码 / 说明 |
| --- | --- | --- |
| 深色 / 浅色 / 跟随系统 | ✅ | `ui/theme/Theme.kt` + `values-night/themes.xml` |
| 多套编辑器配色（含 Darcula、GitHub） | ✅ | 5 套：Darcula / GitHub Light / Monokai / Solarized Dark / Solarized Light |
| 字体大小 | ✅ | 10–28 sp 滑杆 |
| **行高** | ✅ | `setLineSpacing(0f, multiply)`（本次接线；此前设置项被忽略） |
| Tab 宽度（2/4/8） | ✅ | 三选一 |
| 自动换行开关 | ✅ | `setWordwrap` |

### A.10 学习区

| 需求 | 状态 | 对应代码 / 说明 |
| --- | --- | --- |
| 内置分级教程（基础语法 → 文件操作 → 网络请求 → 数据分析） | ✅ | 9 课：`tutorials/01..09`，覆盖 起步 / 基础 / 进阶 / 数据与网络 |
| 每课配可一键运行的示例代码 | ✅ | 每课 `NN_xxx_example.py`；详情页「导入」与「运行」两个按钮（运行 = 导入 + 打开 + 立即执行） |
| 示例库按分类浏览 | ✅ | 分类筛选栏（全部 / 起步 / 基础 / 进阶 / 数据与网络）（本次新增） |
| 代码片段库 | ✅ | `snippets/` 10 个片段，可查看 / 复制 / 导入为文件 |

### A.11 设置与数据

| 需求 | 状态 | 对应代码 / 说明 |
| --- | --- | --- |
| 设置导入 / 导出 | ✅ | 设置页 → JSON 弹窗，可复制 / 粘贴导入 |
| 项目一键备份为 zip **并分享** | ✅ | 备份后直接拉起系统分享面板（`FileProvider`，本次新增；清单已加 provider） |

### A.12 非目标（确认**未**实现）

| 非目标 | 状态 |
| --- | --- |
| Jupyter Notebook 支持 | ✅ 未实现（符合要求） |
| AI 代码补全 | ✅ 未实现（符合要求） |
| Git 集成 | ✅ 未实现（符合要求） |
| pygame / kivy 运行 | ✅ 未实现（仅保留 `ScriptRuntime` 扩展点） |

---

## B. 验收标准自测结果

### B.0 验证方式说明（重要）

本工程是在**没有 Android 设备/模拟器**的环境里开发的，因此自测分三档，
不把「编译通过」当成「功能可用」：

| 方式 | 含义 |
| --- | --- |
| **静态验证** | `./gradlew assembleDebug` 通过 + 解包 APK 核对产物 |
| **逻辑验证** | 在沙箱里直接跑 Python 侧代码：用打桩的 `pdl_host` 复现 Kotlin 侧的调用契约，真实执行 `pdl_runner` / `pdl_debugger` / `pdl_interrupt` / matplotlib 桥 |
| **待真机** | 涉及触屏、软键盘、旋转、实际 pip 下载等必须上设备才能确认的项 |

> 请把下表当作「已知能跑通什么、还需要在设备上点一遍什么」的清单，
> 而不是「全部已通过」的声明。

### B.1 逐项结果

| # | 验收标准 | 结果 | 证据 / 说明 |
| --- | --- | --- | --- |
| 1 | 新建文件 → 写含 `print` + `input` 的程序 → 运行 → 交互输入 → 正确输出 | ✅ Python 侧已验 / 🟢 UI 待真机 | **逻辑验证**：桩 `pdl_host.read_stdin` 依次返回 `42` / `小明`，`pdl_runner.run_script` 输出<br>`请输入一个整数：你输入了: 42  它的平方是: 1764`<br>`你的名字：你好, 小明`<br>同一测试还确认 `argv`、cwd 切换、env 注入均生效。UI 侧（控制台底部输入框 → `HostBridge.submitStdin`）需真机点一遍。 |
| 2 | 死循环脚本可中断 | ✅ 已验 | **逻辑验证**：`pdl_interrupt` 打断 `while True: i += 1`，线程捕获到 `KeyboardInterrupt`（打断时已执行 **43,483,626** 次迭代）。`time.sleep(30)` 无法打断 —— CPython 只在字节码边界检查异步异常，已如实写入 README §5.3。 |
| 3 | pip 安装 requests 并 import 使用 | 🟢 待真机 | **静态验证**：构建期 pip 已成功把 `requests` 与 `black` 及其依赖打进 APK（构建日志无错）。运行时链路 `PackagesScreen → PackageManager → pdl_pip.subprocess` 需联网设备确认。 |
| 3b | 切换清华源成功 | 🟢 待真机 | 软件包页 / 设置页切换后写入 `AppSettings.pipMirror`，`pdl_pip._run` 会追加 `-i https://pypi.tuna.tsinghua.edu.cn/simple`；**静态验证**确认参数拼接正确，实际下载需联网设备。 |
| 4 | matplotlib 绘制正弦曲线，在图表面板正常显示，可保存为 png | ✅ 已验 | **逻辑验证**：真实执行 `pdl_runner.run_script` 跑两段 `plt.show()`，桥接捕获 **2 张图**：<br>`Sine Wave → chart_1.png (39,562 字节)`<br>`Cosine Wave → chart_2.png (38,231 字节)`<br>标题自动取自 axes 标题，PNG 均为有效图像。图表页「保存 PNG」写盘路径复用同一套逻辑。 |
| 5 | 设置断点 → 单步执行 → 变量监视显示正确的值 | ✅ 已验 | **逻辑验证**：`pdl_debugger` 在 `target.py:3` 命中断点，`get_state()` 返回 `a='5'`、`b='7'`、`total='12'`（与源码 `def add(a,b): total=a+b` 一致）；`cmd_next` / `cmd_return` / `cmd_continue` 均能推进并正常收尾。<br>测试同时暴露并修复了 3 个展示缺陷：`__builtins__` 巨型 repr 冲垮变量表、threading/importlib 帧污染调用栈、超长值撑爆面板。 |
| 6 | 横竖屏切换不丢失编辑内容 | 🟢 待真机（设计已保证） | **静态验证**：清单里 Activity 已声明 `configChanges="orientation\|screenSize\|..."`，且编辑器标签状态放在 `AppViewModel`（配置变更不重建），sora 的 `Content` 实例随之保留。 |
| 6b | 多标签切换不丢失光标位置 | 🟢 待真机（逻辑已闭环） | `CodeEditorView` 订阅 `SelectionChangeEvent` 实时写回 `EditorTab.cursorLine/Column`，并在 `onRelease` 兜底保存；重新进入时 `setSelection(tab.cursorLine, tab.cursorColumn)` 恢复。**本次接线前 `onCursorChanged` 从未被触发**，状态栏坐标也不会更新。 |
| 7 | 2000 行文件打开编辑无明显卡顿 | 🟢 待真机（架构已保证不阻塞） | **静态验证**：语法分析由 sora 的 `SimpleAnalyzeManager` 在后台线程 `SplAnalyzer-N` 执行（非主线程）；编辑器实例按标签缓存，`AndroidView.update` 只同步设置不重设文本；补全改为「打开/保存/运行时刷新缓存、按键时读内存」，避免每次输入都调 Python。帧率需真机测。 |

### B.2 已知未覆盖 / 有风险的点

| 项 | 说明 |
| --- | --- |
| 设备端 pip 下载 | 沙箱无法验证真实下载与 wheel 兼容性；`pandas`/`scipy` 等原生包依赖 Chaquopy 的预编译 wheel 是否覆盖 |
| `pdl_interrupt` 在 Android 上的 `ctypes.pythonapi` | 桌面 CPython 已验证可用；Chaquopy 的 `_ctypes.so` 已在 APK 中，但 `PyThreadState_SetAsyncExc` 的符号解析需设备确认。不可用时静默降级（不会崩） |
| 标签滑动关闭的手势冲突 | 与 `ScrollableTabRow` 的横向滚动共用横向手势，阈值设为 140px；真机上可能偏灵敏或偏迟钝，可按需调整 `SWIPE_CLOSE_THRESHOLD_PX` |
| Terminal 多会话的内存 | 所有会话共用组合树（用 alpha/zIndex 切换可见性），会话数很多时内存线性增长，实际按 3~5 个会话设计 |
| Release 构建 | 只验证了 `assembleDebug`；R8 规则已 keep Chaquopy / sora-editor / terminal 三个包，但未实跑 release 产物 |

---

## 0. 界面总览

| # | 界面 | 路由 | 源码 | 核心职责 |
| --- | --- | --- | --- | --- |
| 1 | 编辑器 | `editor` | `ui/editor/EditorScreen.kt` | 多标签页 + 符号工具栏 + 运行按钮 |
| 2 | 控制台 | `console` | `ui/console/ConsoleScreen.kt` | stdout/stderr、`input()` 交互、中断、清屏、复制/分享 |
| 3 | 终端 | `terminal` | `ui/terminal/TerminalScreen.kt` | jackpal VT100 模拟器 + 内置命令解释器 |
| 4 | 文件 | `files` | `ui/files/FilesScreen.kt` | 项目 / 文件 CRUD、多选、SAF 导入、zip 备份 |
| 5 | 软件包 | `packages` | `ui/packages/PackagesScreen.kt` | pip 安装卸载、镜像源、requirements、本地 whl |
| 6 | 图表 | `charts` | `ui/charts/ChartsScreen.kt` | matplotlib Agg 渲染结果浏览与保存 |
| 7 | 调试 | `debugger` | `ui/debugger/DebuggerScreen.kt` | 断点、单步、变量监视、调用栈 |
| 8 | REPL | `repl` | `ui/repl/ReplScreen.kt` | 交互式求值 + 补全 + 多行块 |
| 9 | 教程 | `learn` | `ui/learn/LearnScreen.kt` | 9 课讲义 + 10 个代码片段，一键导入 |
| 10 | 设置 | `settings` | `ui/settings/SettingsScreen.kt` | 主题 / 字体 / 符号栏 / 设置与项目备份 |

另有第 11 个 UI 模块（非界面）：

| # | 模块 | 源码 | 职责 |
| --- | --- | --- | --- |
| 11 | 主题 | `ui/theme/Theme.kt` | Material 3 配色、深色模式、等宽字体族 |

共享状态与导航：

| 模块 | 源码 | 职责 |
| --- | --- | --- |
| 全局状态 | `ui/AppViewModel.kt` | 编辑器标签、控制台缓冲、项目、运行状态、设置快照 |
| 导航骨架 | `ui/AppNavigation.kt` | 抽屉 + `NavHost` + 顶部栏 + Snackbar |
| 公共组件 | `ui/UiCommon.kt` | `SectionCard` / 输入对话框 / 确认对话框 / 空态提示 |

---

## 1. 编辑器

**入口**：默认页；或在任意页打开抽屉 →「编辑器」；或从「文件」页点任意 `.py`。

| 功能 | 操作路径 |
| --- | --- |
| 多标签页 | 顶部 `ScrollableTabRow`，每页带 ● 脏标记与 ✕ 关闭 |
| 新建脚本 | 工具栏「新建」→ 选择模板（空脚本 / 函数 / 类 / 脚本） |
| 打开文件 | 「文件」页点文件；或从外部 App 分享 `.py` 打开（见 §12） |
| 保存 | 工具栏「保存」（未命名标签会提示先用「另存为」） |
| 另存为 | 工具栏「另存为」→ 填文件名 → 存到当前项目目录 |
| 运行 | 工具栏「运行」→ 自动保存 → 跳到「控制台」 |
| 符号工具栏 | 编辑区下方常驻；工具栏最右可「隐藏符号栏 / 显示符号栏」 |
| 插入符号 | 点符号栏按钮，在当前光标处插入（`insertText(sym, sym.length)`） |
| 查找 / 替换 | 工具栏「查找」→ 支持「忽略大小写」「正则」「替换」「全部替换」；底层用 sora `EditorSearcher` |
| 格式化 | 工具栏「格式化」→ 调用 `black`（不可用时原样保留） |
| 打断点 | 把光标移到目标行 → 工具栏「断点」（也可在「调试」页手工添加） |
| 启动调试 | 工具栏「调试」→ 保存 → 跳到「调试」页并开始会话 |
| 查看内置帮助 | **长按**代码里的函数名 → 弹出 `docstring` |
| 自动补全 | 输入 ≥1 个字符触发；候选来自：文件内标识符 + Python 关键字 + 内置函数 + 9 个代码模板 |
| 自动缩进 | 行尾 `:` 回车自动 +4 空格；括号/引号自动配对 |
| 语法高亮 | 关键字 / 内置 / 字符串 / 数字 / 注释 / 运算符 / 函数名 |
| 撤销重做 | sora 内置，键盘 `Ctrl+Z` / `Ctrl+Y` |
| 状态栏 | 底部显示文件绝对路径、`Ln x, Col y`、未保存标记、断点数 |
| 横竖屏不丢内容 | 标签状态放在 `AppViewModel`，`Content` 实例跨配置变更保留 |

---

## 2. 控制台

**入口**：抽屉 →「控制台」；或编辑器点「运行」后自动跳转。

| 功能 | 操作路径 |
| --- | --- |
| stdout / stderr 渲染 | 实时流式追加，stderr 用 `error` 色显示 |
| 自动滚底 | 有新输出时自动滚到末尾 |
| `input()` 交互 | 运行期间底部输入框可用 → 输入 → 点「发送」（等价回车） |
| 中断 | 「中断」→ `PythonRunner.interrupt()` + 解除 stdin 阻塞 |
| 清屏 | 「清屏」 |
| 复制 | 「复制」→ 全量输出进剪贴板 |
| 分享 | 「分享」→ 系统分享面板（`ACTION_SEND`） |
| 存为文件 | 「存为文件」→ 写入当前项目目录 `console_YYYYmmdd_HHMMSS.txt`，并拉起分享面板 |
| 运行指示 | 运行中顶部显示进度条 |

---

## 3. 终端

**入口**：抽屉 →「终端」。

| 功能 | 操作路径 |
| --- | --- |
| 多会话标签 | 顶部标签栏，每项可关闭；「新会话」新增一个独立会话（各自的历史 / 当前目录 / 滚动缓冲） |
| 重置本会话 | 工具栏「重置本会话」→ 丢弃当前会话的缓冲与历史，换一个新会话 |
| VT100 渲染 | jackpal `EmulatorView`，支持颜色、光标、滚动、软键盘 |
| 行编辑 | 退格、`Ctrl+U` 清行、`Ctrl+C` 取消、`Ctrl+L` 清屏、Tab → 4 空格 |
| 命令历史 | ↑ / ↓ |
| 清屏 | 工具栏「清屏」 |
| 复制全部 | 工具栏「复制」→ `TermSession.getTranscriptText()` |
| 重置会话 | 工具栏「重置会话」→ 重建 `TerminalController` |
| 内置命令 | `help` `clear` `pwd` `ls` `cd` `cat` `mkdir` `touch` `rm` `echo` `open` `run` `python` `pip` |
| 运行脚本 | `run main.py` 或 `python main.py` → 输出直接渲染在终端（无 `input()`） |
| 执行单行 | `python -c "print(sum(range(10)))"` |
| 包管理 | `pip install requests` / `pip list`（沿用「设置」里的镜像源） |
| 在编辑器打开 | `open main.py` |
| 工作目录 | 顶部显示；`cd` 会同步到「文件」页与 Python 的 `os.getcwd()` |

> 无 pty，因此**不支持** `ls`/`git` 等系统可执行文件。详见 README §5.1。

---

## 4. 文件

**入口**：抽屉 →「文件」。

| 功能 | 操作路径 |
| --- | --- |
| 项目列表 | 顶部「项目：xxx ▾」下拉 |
| 新建项目 | 下拉 →「＋ 新建项目」 |
| 进入目录 | 点目录行 |
| 返回上级 | 「上级」（到项目根为止） |
| 打开文件 | 点文件行 → 编辑器 |
| 直接运行 | `.py` 行右侧 ▶ |
| 多选 | 勾选任意 Checkbox，或先勾一个再点其它行 |
| 新建文件 | 「新建文件」→ 填名字（`.py` 自动补扩展名） |
| 新建文件夹 | 「新建文件夹」 |
| 重命名 | 单选后「重命名」 |
| 复制到 / 移动到 | 多选后点按钮 → 目录选择对话框（含子目录树） |
| 删除 | 多选后「删除」→ 二次确认（列出将删除的项） |
| SAF 导入 | 「导入(SAF)」→ 系统文件选择器（`OpenDocument`，`*/*`） |
| 项目备份 | 「备份 zip」→ 导出到 App 外部私有目录 |

---

## 5. 软件包

**入口**：抽屉 →「软件包」。

| 功能 | 操作路径 |
| --- | --- |
| 镜像源切换 | 顶部 PyPI / 清华 TUNA 二选一（同时写入设置） |
| 安装 | 输入包名（可带 `==版本`）→「安装」 |
| 升级 | 同输入框 →「升级」 |
| 卸载 | 同输入框 →「卸载」 |
| 科学栈一键装 | 「安装全部」→ numpy / pandas / matplotlib / scipy / scikit-learn / requests / Pillow |
| requirements 导入 | 「导入 requirements」→ SAF 选文件 → `pip install -r` |
| requirements 导出 | 「导出 requirements.txt」→ 写到当前项目目录 |
| 本地 whl 安装 | 「安装 .whl」→ SAF 选 `.whl` → `pip install <file>` |
| 已安装列表 | 底部列表，按名称过滤，逐项「卸载」 |
| 刷新列表 | 「刷新列表」 |
| 查看 pip 原始输出 | pip 输出经 `pdl_host` 桥流到**控制台** |

---

## 6. 图表

**入口**：抽屉 →「图表」；或脚本里调用 `plt.show()` 后自动出现内容。

| 功能 | 操作路径 |
| --- | --- |
| 自动收集 | `pdl_runtime.install_matplotlib_bridge()` 把 `plt.show()` 重定向为「存 PNG + 通知图表页」 |
| 翻页 | 「上一张」/「下一张」，中间显示 `n / N` |
| 缩放查看 | 图片区可纵向滚动 |
| 保存 PNG | 「保存 PNG」→ 存到当前项目目录（自动去重命名） |
| 清空 | 「清空」→ `EventBus.clearCharts()` |
| 空态提示 | 未安装 matplotlib 时给出安装与示例代码 |

支持的图类型取决于 matplotlib 自身：折线、散点、柱状、直方图、饼图、多子图等，见教程第 8 课。

---

## 7. 调试

**入口**：抽屉 →「调试」；或编辑器点「调试」。

| 功能 | 操作路径 |
| --- | --- |
| 开始调试 | 「开始调试」（需先保存文件） |
| 继续 | 暂停后「继续」→ `cmd_continue` |
| 单步（进入） | 「单步」→ `cmd_step` |
| 下一行（跨过） | 「下一行」→ `cmd_next` |
| 跳出 | 「跳出」→ `cmd_return` |
| 停止 | 「停止」→ 结束会话 |
| 添加断点 | 「断点」→ 填文件绝对路径 + 行号 |
| 断点列表 | 「断点」标签页，逐项移除或「全部清除」 |
| 变量监视 | 「变量」标签页 → 输入变量名 →「关注」→ 暂停时持续显示其值 |
| 全部局部变量 | 「变量」标签页下半部分 |
| 异常信息 | 未捕获异常时在「变量」页顶部显示 |
| 调用栈 | 「调用栈」标签页，显示函数名、文件、行号（栈顶在前） |
| 状态提示 | 顶部显示「已暂停于 文件名:行号」 |

---

## 8. REPL

**入口**：抽屉 →「REPL」。

| 功能 | 操作路径 |
| --- | --- |
| 求值 | 输入 →「执行」；`globals` 跨次保持 |
| 多行块 | 输入 `def f():` 后提示「块未结束，继续输入」，提示符变 `...` |
| 自动补全 | 输入前缀 →「补全」→ 候选列表（`rlcompleter`） |
| 重置 | 「重置」→ 清空 `globals` |
| 清屏 | 「清屏」 |
| 输出合并 | REPL 期间新产生的输出会按顺序并入本页，与「控制台」共享事件流 |

---

## 9. 教程

**入口**：抽屉 →「教程」。

| 功能 | 操作路径 |
| --- | --- |
| 课程列表 | 「教程」标签页，显示 9 课（编号 + 标题） |
| 阅读讲义 | 点课程 → Markdown 渲染（标题 / 段落 / 列表 / 代码块） |
| 复制讲义 | 「复制」 |
| 导入示例 | 「导入示例」→ 把 `NN_xxx_example.py` 写进当前项目并打开 |
| 代码片段列表 | 「代码片段」标签页，10 个片段 |
| 分类筛选 | 列表上方「全部 / 起步 / 基础 / 进阶 / 数据与网络」，按文件名数字前缀分组 |
| 一键运行示例 | 教程详情页「运行」→ 导入项目 + 打开 + 立刻在控制台执行 |
| 查看片段 | 点片段 → 代码块高亮等宽显示 |
| 复制片段 | 「复制」→ 剪贴板 |
| 导入为文件 | 「导入为文件」→ 写进当前项目并打开 |
| 重新加载 | 「重新加载」（改了 Python 资源后使用） |

### 教程目录

| 编号 | 讲义 | 示例 |
| --- | --- | --- |
| 01 | 你好，Python | `01_hello_world_example.py` |
| 02 | `input()` 交互与格式化输出 | `02_input_output_example.py` |
| 03 | 类型、条件与循环 | `03_types_control_example.py` |
| 04 | 函数与模块 | `04_functions_modules_example.py` |
| 05 | 文件读写与路径 | `05_files_example.py` |
| 06 | 常用标准库 | `06_stdlib_example.py` |
| 07 | 异常处理与调试 | `07_errors_debug_example.py`（**含故意留的 bug**，配合调试器） |
| 08 | numpy 与 matplotlib 绘图 | `08_numpy_matplotlib_example.py`（生成 5 张图） |
| 09 | 用 requests 访问网络 | `09_requests_example.py` |

### 代码片段目录

`python/snippets/`

| 文件 | 内容 |
| --- | --- |
| `01_read_file.py` | 文本文件三种读法 + 写入 |
| `02_csv_io.py` | CSV 读写与类型转换 |
| `03_class_dataclass.py` | 普通类（属性校验/运算符重载）+ `@dataclass` |
| `04_argparse_cli.py` | 命令行参数解析 |
| `05_timing.py` | 装饰器计时、上下文管理器计时、去重算法对比 |
| `06_json_config.py` | JSON 配置读写、默认值合并、字段校验 |
| `07_random_tools.py` | 抽奖、加权随机、训练/测试集划分、随机分组、`secrets` |
| `08_matplotlib_sine.py` | 正弦曲线模板 + 中文字体探测 + 保存 |
| `09_requests_get.py` | 带超时/重试/统一异常封装的 HTTP 客户端 + 流式下载 |
| `10_retry_decorator.py` | 指数退避重试装饰器 + 截止时间上下文管理器 |

---

## 10. 设置

**入口**：抽屉 →「设置」。

| 分组 | 项目 | 说明 |
| --- | --- | --- |
| 外观 | 主题模式 | 跟随系统 / 浅色 / 深色（写入 DataStore） |
| 编辑器 | 字号 | 10–28 sp 滑杆 |
| | 行高倍数 | 0.8–2.0 滑杆 |
| | Tab 宽度 | 2 / 4 / 8 空格 |
| | 自动换行 | 开关 |
| | 显示行号 | 开关 |
| | 自动缩进 | 开关 |
| | 括号/引号自动配对 | 开关 |
| | 保存时用 black 格式化 | 开关（black 不可用时自动降级） |
| | 配色方案 | Darcula / GitHub Light / Monokai / Solarized Dark / Solarized Light |
| 符号栏 | 查看 / 删除 / 添加 / 恢复默认 | 自定义编辑器下方符号栏 |
| 包管理 | pip 镜像源 | PyPI / 清华 TUNA |
| 快捷键 | 外接键盘快捷键编辑 | 保存 / 运行 / 查找 / 替换 / 格式化 / 调试 六个动作可改键位，立即生效 |
| 导入导出 | 导出 JSON | 弹窗展示，可「复制」 |
| | 导入 JSON | 粘贴 → 校验 → 写入 DataStore |
| 项目备份 | 选择项目 + 导出 zip | 导出到 App 外部私有目录并显示绝对路径 |
| 关于 | 版本信息 | PyDroidLab 1.1.1（build 3）/ Python 3.11 / Chaquopy / sora-editor |

---

## 11. 全局行为

| 功能 | 说明 |
| --- | --- |
| 顶部栏 | 抽屉开关 + 当前页标题 |
| Snackbar 提示 | 保存成功、导入结果、错误原因等统一走 Snackbar |
| 旋转屏幕 | 所有状态在 `AppViewModel`，编辑内容、光标、控制台输出均保留 |
| 键盘适配 | `windowSoftInputMode="adjustResize"` + Compose `imePadding()` |
| 深色模式 | Compose 配色 + `values-night/themes.xml` 双轨 |

---

## 12. 与系统集成

**入口**：从其它 App 分享 / 打开 `.py` 文件。

| 方式 | 行为 |
| --- | --- |
| `ACTION_SEND`（`text/x-python`、`text/plain`） | 把文件流导入 `filesDir/shared/` 并在编辑器打开；纯文本则填入新标签页 |
| `ACTION_VIEW`（`file://`、`content://`，`.py`） | 直接打开或导入后打开 |
| `launchMode="singleTask"` | 已运行时走 `onNewIntent`，不会重复创建 Activity |

---

## 13. 验收对照

| 验收标准 | 对应实现 | 建议操作 |
| --- | --- | --- |
| 新建文件 → 含 input 的脚本 → 运行 → 交互输入 → 正确输出 | 编辑器「新建」+ 控制台输入框 + `HostBridge.read_stdin` 桥 | 导入教程 02 示例，运行时按提示输入 |
| 死循环可中断 | 控制台「中断」→ `PythonRunner.interrupt()` | 写 `while True: pass` 后点「中断」 |
| pip 装 requests 并 import 使用 | 软件包页 + `pdl_pip` + `pdl_host` 桥输出 | 装 requests（或直接用它，APK 已预装）→ 运行教程 09 |
| matplotlib 画正弦曲线在图表面板显示并可保存 | `pdl_runtime.install_matplotlib_bridge()` + `EventBus.emitChart` + 图表页 | 运行教程 08 示例 → 图表页「保存 PNG」 |
| 设断点 → 单步 → 变量监视值正确 | `pdl_debugger`（bdb）+ 调试页三标签 | 运行教程 07 示例并在 `total += values[i]` 打断点 |
| 横竖屏切换不丢编辑内容 | `AppViewModel` + `EditorTab.cursorLine/Column` | 编辑器输入后旋转 |
