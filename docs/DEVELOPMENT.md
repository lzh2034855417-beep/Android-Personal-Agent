# 开发与构建 / Development

[文档索引](README.md) · [贡献指南](../CONTRIBUTING.md) · [测试记录](TESTING.md)

## 先选择正确源码

`main` 以 0.3.1 正式版源码为基线，保留后续文档与仓库维护改动。精确复现发布 APK 时使用相同版本标签；工作分支可能包含未发布功能，先核对当前 checkout 的版本和提交。

复现 0.3.1 正式版：

```sh
git clone https://github.com/lzh2034855417-beep/Android-Personal-Agent.git
cd Android-Personal-Agent
git switch --detach v0.3.1
```

要提交修复，在所选基线上新建分支。默认分支的工程记录不能替代发布标签的测试证据。[0.3.1 源码](https://github.com/lzh2034855417-beep/Android-Personal-Agent/tree/v0.3.1) · [正式发布](https://github.com/lzh2034855417-beep/Android-Personal-Agent/releases/tag/v0.3.1)

## 环境与命令

Android 8.0 / API 26+；0.3.1 使用 compile SDK 36.1、target SDK 36。使用仓库 Gradle Wrapper、兼容的 Android Studio JBR/JDK 和相应 SDK。首次构建需下载依赖，仅已有缓存时使用 `--offline`。

Windows PowerShell：

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME="$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat testDebugUnitTest lintDebug lintRelease assembleDebug assembleDebugAndroidTest
```

macOS / Linux：

```sh
./gradlew testDebugUnitTest lintDebug lintRelease assembleDebug assembleDebugAndroidTest
```

通过测试与编译不等于通过真机、在线模型或所有品牌报告验收。记录实际执行范围，未运行项目明确标注。

## 构建产物与签名

- Debug 包名 `com.aegis.apa.preview`，与正式版 `com.aegis.apa` 可并存，数据不互通。
- 0.3.1 标签的 Debug APK：`app/build/outputs/apk/debug/APA-v0.3.1-debug.apk`；其他分支以构建配置和产物为准。
- 单元测试、Lint 和 Debug 构建无需发布密钥。正式 `assembleRelease` 要求完整本地签名配置。
- 本地 `keystore.properties` 字段：`storeFile`、`storePassword`、`keyAlias`、`keyPassword`；相对 `storeFile` 按 app 模块目录解析。可用 `-Papa.signingProperties=本地文件` 指定配置，PowerShell 中将整项参数加引号。
- 不提交密钥、签名文件或私人原始报告。正式发布另核对版本、签名、校验值和升级保留数据，不能把 Debug 包当正式包分发。

## 工程入口

[架构](ARCHITECTURE.md) · [数据语义](DATA_MODEL.md) · [技术债](TECH_DEBT.md) · [路线图](../ROADMAP.md)

旧设计与实施草稿已从当前目录移除，需要追溯时查看 Git 历史。架构与测试记录按各自注明的源码和日期阅读，不代表其中所有计划均已发布。

English: build a published release from its matching Git tag. Use the Gradle Wrapper with a compatible JDK and Android SDK. Debug builds need no release signing key. Report JVM, lint, build, instrumentation, device and live-model checks separately.
