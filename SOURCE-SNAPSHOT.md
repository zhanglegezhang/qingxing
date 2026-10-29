# 轻醒 1.1.1 原始源码快照（公开脱敏版）

此快照对应已发布的 1.1.1（versionCode 23）。现有 APK 未重新打包、替换或重新签名；原始 APK 的 SHA256 见 `release/source-snapshot-1.1.1.json`。

应用源码及资源的 23 个文件全部逐字节匹配该正式 APK 的原始构建输入记录。原有公开构建输入中，67 个文件恢复为原始字节；唯有下面两个文件保留必要的凭据脱敏适配：

- `scripts/build-core.ps1`
- `scripts/test-android-core.ps1`

这两个文件把原来写在脚本中的签名密码改为通过新增的 `scripts/signing.ps1` 读取本地文件，应用行为和签名身份不变。`tests/test_signing.py` 是该适配的附加验证。所有差异和文件哈希均列在快照记录中；不得将公开快照宣称为包含私人凭据的完整原始构建环境或字节级可复现构建。

构建凭据需自行放在被忽略的 `.tooling/`：`baidu-ak.txt`、`personal.keystore`（别名 personal）、`signing-store-password.txt` 和 `signing-key-password.txt`。两个密码文件分别保存一行 UTF-8 无 BOM 密码，即使密码相同也使用两个文件。原作者的私有凭据不在源码包中；使用自己的签名不能覆盖安装原作者正式包。

历史 `docs/BUILD.md`、README 和构建流程文件按原始记录保留。因此其中“脚本中的密码”等历史描述应结合本说明理解；下载现有 APK 请使用 GitHub Release 的 APK 附件。历史测试 APK、SDK 工具及本地缓存不在源码包中，缺少它们时不能声称完整升级测试已通过。

修复仅关联 `v1.1.1` 与此源码提交，不回退 main 的当前开发及交付规则；GitHub Release 中现有 APK 附件保持原 ID、文件内容和哈希。
