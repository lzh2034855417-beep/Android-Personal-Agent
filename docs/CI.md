# 持续集成

[开发与构建](DEVELOPMENT.md) · [测试记录](TESTING.md)

当前 `.github/workflows/android-ci.yml` 在 main 推送、Pull Request 和手动触发时运行，使用只读仓库权限。

一个 Ubuntu 任务安装 Java 21、Android SDK 与固定 SHA 的 Actions，运行：

- Python 标准库离线评测工具测试；不调用模型服务或读取 API Key。
- JVM 单元测试、Debug/Release Lint。
- Debug APK 与 AndroidTest APK 编译；不执行正式签名打包。

CI 显式指定不存在的本地签名配置，不需要发布密钥。预览应用与正式版包名不同。

当前工作流不运行模拟器或真机仪器测试，也不调用真实在线模型。AndroidTest APK 编译成功不能当成设备验收；本地或历史检查成功也不能冒充本次远端任务成功。实际结果查看仓库 Actions。
