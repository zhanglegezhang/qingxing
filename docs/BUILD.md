## 公开源码与本地签名配置

安卓仓库为 `zhanglegezhang/qingxing_android`。`qingxing_ios` 仅预留，不含 iOS 应用。公开源码保留 1.1.1 的应用代码、资源和测试；本次仅调整构建凭据读取及下载文档，历史 APK 没有重新生成。

构建需要以下本地文件，均位于被忽略的 `.tooling/`，不要提交到 GitHub：

- `baidu-ak.txt`：自己的百度 Android AK，绑定相应包名及签名。
- `personal.keystore`：原有签名文件，别名为 `personal`。原作者正式包的覆盖升级必须继续使用原签名；其他开发者应使用自己的签名，不能覆盖安装原作者版本。
- `signing-store-password.txt`：UTF-8 无 BOM 文本，第一行为签名库密码。
- `signing-key-password.txt`：UTF-8 无 BOM 文本，第一行为私钥密码；与库密码相同时，也使用单独文件保存同一个值。apksigner 对同一个密码文件的多次引用会依次读取下一行，因此不要省略此文件。

构建和测试通过同一个 `scripts/signing.ps1` 读取密码文件路径，并使用 apksigner 的 `file:` 参数。密码不再写在脚本或命令行中；缺失配置会中止，不生成或替换原密钥。请限制这些文件的本机访问权限。

工具链、历史 APK 测试种子和本地生成记录不随源码上传。首次构建前须按下文配置 JDK、Android SDK、Python、百度依赖及本地签名；脚本中的 Python 路径是原开发机路径，需要改为自己的解释器。先递增 `release/version.json`，不要重建覆盖现有版本。历史升级测试还需要自行准备对应 `tests/fixtures/*.apk`；公开 Releases 目前只有 1.0 和 1.1.1，不应把缺失种子的测试记为通过。

## 1.1.1 隐藏定位入口

默认仅三个导航，版本行中间独立空白连续点击十次解锁。解锁仅本次界面会话有效，同进程重建可恢复，跨进程旧状态不可恢复；通知打开也执行入口检查，停止动作保留。HiddenLocationTests 覆盖真实触摸、超时、背景切换、重建和重开，BaiduTests 先按实际手势解锁，再继续 SDK/模拟测试。公共页面入口变化按自动计划全量回归。无持久设置迁移。

## 1.1 定位工具

重新接入按需百度地图/定位，锁定依赖及 SHA256 在 scripts/baidu-sdk-lock.json。构建时校验官方 AAR 并从中恢复 JAR/native/assets；AK 仍只从忽略目录读取并生成私有资源，不写入源码或交付文档。定位工具独立于 Wi-Fi 打卡流程，模拟只使用 Android 官方 mock provider，默认关闭、无自启。baidu-map 检查真实鉴权与地图回调；baidu-location 检查按需定位和取消；baidu-address 现覆盖 BD09/WGS84 转换与模拟服务、系统坐标、通知停止和授权撤销。原打卡及历史升级全量回归保留。

# 1.0 当前行为与版本格式

通知模式固定为安静监控与吵闹提醒，应用内不再提供模式选择区；旧版本保存的模式选择被忽略，手机通知开关、音量、静音和勿扰仍有效。打卡提醒响铃及振动持续 5 秒，提前确认或暂停可中止，不改变每分钟检查规则。

首页两个企微按钮在打开请求成功后直接确认对应打卡、停止提醒，失败时不确认；没有返回后的二次询问，已确认按钮仍可连续双击取消。删除独立企微按钮与必要授权标题的说明图标。日志改为口语化“最近动态”，内部滚动区域固定 456dp，保留 100 条原始本机记录，历史事实不改写。

`release/version.json` 仍是唯一版本来源。版本名支持两段（例如 `1.0`）和三段（例如 `1.0.1`），versionCode 必须递增；不接受路径字符、后缀或其他段数。

以下 0.7 通知模式及后续旧版本段落供历史追溯，以本节及当前验证记录为准。

# 0.7 历史通知模式、授权与日志

四个独立通知类别：安静监控/吵闹监控、安静提醒/吵闹提醒；默认安静监控加吵闹提醒。吵闹监控每 10 秒检查时提交一次声震通知请求，遵循系统声震设置、勿扰及通知冷却限制。选择标记与设置不匹配警告独立。必要授权包含通知、Wi-Fi 名称/后台读取、精确闹钟和自启动手动确认；缺失暂停监控，不把声震设置不匹配误当成授权缺失。

新安装默认开启，默认/重置时段为 08:30–10:00、17:30–19:00；升级保留已有总开关与时段。历史动态和诊断合并为 events，按时间倒序保留 100 条，不去重，不保存两套历史副本。首次使用引导授权，自启动明确为用户确认，非系统自动核验。

# 0.6 起：仅 Wi-Fi 监控

当前构建不读取或打包百度 AK，不编译/打包百度 SDK，不需要 prepare_baidu.py。使用独立的 build/wifi-res 生成版本资源，原私有文件和旧版本归档保留。

使用原签名覆盖升级。SDK 移除后安装包不含供应商原生库；位置相关权限仅用于 Android 的 Wi-Fi 名称访问控制，不获取坐标。schema=4 只导入 Wi-Fi、时段及日历；迁移清理旧配置、旧提醒和旧循环响铃。

# 按影响范围验证与发布

默认以 `release/latest.json` 的正式 APK 和归档 `inputs.json` 为基线，比较文件哈希，不依赖 Git。`release-plan.ps1` 只读输出变更、选测原因和未执行项；未知文件、公共基础或构建/发布机制变化自动全量。选测为保守文件级映射，不是函数级依赖分析。

- `build.ps1`：冻结测试计划并构建，保留全部快速纯 Java 测试。
- `release.ps1`：只运行计划必测项，验收后发布；失败重试复用同 APK、输入、计划和报告哈希下的通过项。
- `build.ps1 -Full` / `release.ps1 -Full`：追加全量覆盖；计划变化使原计划绑定的结果失效。
- `release.ps1 -AddSuite holiday,baidu-map`：只能增加覆盖，不能删掉自动必测项。
- `test-android.ps1 -Group location`：保留分组名，现验证无定位的 Wi-Fi 轮询、真实一分钟等待及锁屏生命周期；android / holiday 为通用流程和调休。
- `test-baidu.ps1 -Scope location`：保留历史分组入口，现验证百度 SDK、地图组件、定位服务和 AK 已从 APK 移除；三个 scope 均不调用百度网络服务。
- `test-workflow.ps1`：在共享锁内执行不连接设备的选测、复用、发布保护测试。

每版必测上一正式版升级和最小冒烟（启动、版本、三页渲染、提醒确认）；完整界面、历史升级及其他功能组按影响选择。测试分组为 latest-upgrade / upgrade / layout-upgrade / android / location / holiday / baidu-location / baidu-map / baidu-address / smoke-min / smoke。每个分组报告独立，不把旧聚合报告当作本版通过。

正式包、签名和 latest 入口规则不变；过去的正式包及元数据不修改。旧记录没有 testPlan 时仍可由 status 核验，新的候选必须有可重算且匹配的计划。基线缺失转全量，基线文件哈希不符则中止。未测项会在交付记录显示未测原因。纯文档修改无需构建或发 APK。

本轮调整发布验收机制，需要以全量计划验证一次真实候选，之后普通业务修改才可受益于选测。以下为历史构建说明。

# 构建与签名

## 当前统一流程（0.5.3 起）

1. 只在 `release/version.json` 设置开发版本名和递增的 versionCode。
2. `build.ps1` 生成未发布候选包并执行基础测试，路径记录于 `build/current-candidate.json`。旧正式包保持原样。
3. 确保独立 `QingxingSmoke16` / `emulator-5556` 已启动，运行 `release.ps1`。它串行执行候选测试计划中的验证，影响不明自动全量，通过后才写入 `dist/releases/<version>/` 与 `release/latest.json`。
4. `release-status.ps1` 验证当前正式包，唯一下载入口为 `release/INSTALL.md`。最终发给用户的 APK 必须取自 latest.json，不能从 dist 根目录随意选包。

所有公开构建/测试/发布命令共用系统文件锁；冲突时退出，不等待或覆盖。源码/脚本变化后旧候选拒绝发布，需要重建。正式版本不允许再次构建覆盖，必须递增版本。历史根目录 APK 保留，以下旧版记录仅供追溯。

内部脚本位于 scripts/*-core.ps1，不直接调用。旧测试种子版本为固定历史事实，当前期望版本通过候选元数据传入，不逐文件硬编码。

工具位于项目 `.tooling/`，不会修改系统 Java、Android SDK 或 PATH 的持久设置。build.ps1 只修改本进程环境变量。Java 直接编译，Android D8 生成 dex，aapt2 处理资源，zipalign 和 apksigner 打包签名。

工具来源：

| 工具 | 下载来源/版本 | 验证 |
|---|---|---|
| Microsoft OpenJDK | https://aka.ms/download-jdk/microsoft-jdk-21.0.8-windows-x64.zip | HTTPS 下载；本机 SHA256 记录见 tooling-hashes.txt |
| Android 平台 35 r02 | https://dl.google.com/android/repository/platform-35_r02.zip | SHA1 `0bb560a90a7a2cbd0dd8348224d518b638fe7949`，来自 Google SDK 仓库元数据 |
| Android Build Tools 35.0.0 | https://dl.google.com/android/repository/build-tools_r35_windows.zip | SHA1 `af059bb67cf7786f45ee0db85e2d24985df1b4b6`，来自 Google SDK 仓库元数据 |
| Platform Tools | Google Android SDK repository，37.0.1 | 仅用于设备/模拟器验证 |

以上压缩包分别解压到 `.tooling/jdk/`、`.tooling/platform/`、`.tooling/build-tools/`；脚本从中定位实际子目录。Android SDK 使用受其官方许可约束。

```powershell
powershell -ExecutionPolicy Bypass -File .\build.ps1
```

以下为历史流程说明：当时输出 `dist/Qingxing-0.5.1.apk` 及 SHA256，规则测试输出 `PASS 42 decision-rule checks`。当前候选包统一生成在 `build/candidates/`；签名凭据配置以本页开头为准。**保留原签名文件，后续覆盖安装必须沿用同一密钥**。如丢失密钥，通常只能卸载后重装，请提前导出设置。

`test-android.ps1` 编译独立测试 APK 并仅对 默认 emulator-5556 且 AVD 名称为 QingxingSmoke16 的模拟器执行。测试会清空模拟器里的轻醒数据，不会连接用户手机。测试输出保存于 `docs/android-test-output.txt`。它覆盖通知分流、打卡重复、旧动作隔离、稍后提醒、有效/无效备份、紧凑表单和定位服务启停。

模拟器测试包独立于主 APK，不会把测试入口放入交付 App。主 App 的接收器、服务、地图页均不对其他应用导出。

本项目尚未发布应用商店，未申请后台账户或购买服务。本版已接入百度 SDK；楼内精度、后台持续性和耗电仍需目标手机现场检查。


## 0.2 的额外检查

构建后还运行 376 项公告解析与日期检查。`test-android.ps1` 包含原有安卓交互回归与调休同步/缓存检查；`test-upgrade.ps1` 在可丢弃模拟器内卸载本测试应用、安装归档 0.1 APK、写入旧版状态，再覆盖安装 0.2 检查保留结果；`test-smoke.ps1` 执行最终独立冒烟。所有测试安装操作限定单独 QingxingSmoke16 AVD，防止重置用户正在查看的模拟器。

正式 APK 仅包含 app 源码与资源，不打入 tests/fixtures。holiday-cn 为数据依赖，不复制其程序代码；MIT 许可证随文档提供。网络同步没有放宽 TLS 校验，也不接受任意来源链接。

## 百度 SDK 构建依赖

运行 scripts/prepare_baidu.py 下载锁定的 Maven Central 官方 AAR（地图/工具 8.2.0、基础定位 9.6.9 及其依赖），缓存到忽略目录 .tooling/baidu。版本及 SHA256 见 BAIDU-SDK-dependencies.json。需要 Python 3.9+，当前脚本使用本机 Codex Python 路径。

将 Android AK 保存在 .tooling/baidu-ak.txt，构建只在忽略目录生成私有资源。不要把该文件、生成资源或签名文件复制到公开仓库。SDK 的 Java、assets 和 arm64-v8a/armeabi-v7a/x86_64 原生库一并打入 APK（约 30 MB），支持库安装时提取。D8 对供应商已混淆的 JAR 有栈映射/外围方法诊断，保留于 build/d8-diagnostics.txt；非零退出会中断构建。

test-baidu.ps1 实际联网测试 Android 鉴权、地图加载和定位。-Preview 会短暂停留供界面检查，并仅为独立测试 AVD 保留交互预览的 SDK 同意状态。

## 0.4 的运行环境和数据清理检查

本版只提供打卡提醒。旧事项清理在 Store 首次构造时执行，须早于调度恢复。新备份 schema=3，公司字段白名单导入；旧事项不恢复。test-upgrade.ps1 使用归档 0.3.0 APK 写入真实系统闹钟/通知，再升级到 0.4.0，检查清理与打卡数据保留。

新系统镜像的官方元数据为 .tooling/google-sysimages36.xml，zip 校验后由 scripts/prepare_android16.py 安装；该准备脚本只创建全新 AVD，不覆盖已有目录。预览 AVD 名为 QingxingAndroid16，独立测试 AVD 名为 QingxingSmoke16。启动后等 sys.boot_completed=1 再运行测试。镜像和 AVD 不含用户手机数据。

当前界面版 0.5.1：test-layout-upgrade.ps1 验证真实 0.5.0 覆盖升级、缓存地址、自定义半径与打卡数据保留，受独立测试 AVD 名称保护。


## 0.5.2 定位诊断与恢复

当前输出为 `dist/Qingxing-0.5.2.apk`，versionCode=8。新增纯 Java `LocationHealthTest` 和 Android `LocationTests`；后者已接入 `test-android.ps1`，输出 `docs/location-test-output.txt`。覆盖升级断言同步到 versionCode=8，旧版种子 APK 不变。

同时运行模拟器时，本机一次 D8 构建遇到 Windows 提交内存不足；本轮通过仅为当前 PowerShell 进程设置 `$env:JAVA_TOOL_OPTIONS='-Xmx768m'` 后构建通过。未更改系统环境变量或页面文件设置。

定位恢复次数使用新偏好项 `locationRecoveryWindow` / `locationRecoveryCount`，按本机日期和 am/pm 窗口隔离；读取旧安装时默认 0，不修改原公司配置、打卡记录、时段或备份 schema。恢复时清空移动趋势样本，避免拼接重启前后的轨迹；有定位回调但结果不合格时只报告原因。
