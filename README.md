# Pixel-Tzz-Pro

`全员逃走中-扩展` 是面向 Minecraft 26.2 的 Fabric 客户端与服务端模组。

第一个基础里程碑已于 2026-07-26 通过用户实机验收：协议握手、世界持久化、数据包重载提示，以及可从 ESC 打开的玩家/主持人控制台外壳均已形成基础闭环。地图、任务、猎人分配、准备流程、捕获与运镜尚未开始实现。

完整产品要求见 [`docs/REQUIREMENTS.md`](docs/REQUIREMENTS.md)。
第一里程碑的验收证据与收口边界见 [`docs/MILESTONE-1-CLOSEOUT.md`](docs/MILESTONE-1-CLOSEOUT.md)。

第二里程碑 2A 已实现数据包注册层：游戏、身份、队伍、生存状态、阶段、版本化字段、流程节点、主持人 BossBar 配置及动态面板操作都可以通过严格校验的 JSON 注册。候选定义以不可变 generation 原子发布，失败时保留上一代快照并提供有界诊断。

2A 只注册与诊断，不执行流程或面板操作，不修改任何游戏状态，也不渲染数据包页面或 BossBar。完整格式见 [`docs/DATA-PACK-API-2A.md`](docs/DATA-PACK-API-2A.md)，验收步骤见 [`docs/MILESTONE-2A-TESTING.md`](docs/MILESTONE-2A-TESTING.md)。

可运行示例位于 [`examples/pixel-tzz-base-datapack`](examples/pixel-tzz-base-datapack)，当前包含 `31` 个文件和 `23` 项 Pixel TZZ 定义。

2A 的自动检查、独立服务端有效/无效/恢复重载回归及用户客户端实机验收均已通过。验收记录见 [`docs/MILESTONE-2A-TESTING.md`](docs/MILESTONE-2A-TESTING.md)；当前改动继续保持未提交、未推送。

第二里程碑 2B 的核心实现已经完成并进入收口：页面与主题严格编译、网络协议 v5、客户端缓存、生产布局与渲染器、管理员预览器、主题、动画及资源预检均已落地。预览器当前只截获 Local、Flow 和 Registered 请求并展示请求信封，不向服务端发送，也不修改权威游戏状态。页面 API、当前实现限制及停止线见 [`docs/DATA-PACK-UI-2B.md`](docs/DATA-PACK-UI-2B.md)，验收计划见 [`docs/MILESTONE-2B-PLAN.md`](docs/MILESTONE-2B-PLAN.md)，最终自动构建与独服回归证据见 [`docs/MILESTONE-2B-TESTING.md`](docs/MILESTONE-2B-TESTING.md)。

2B 尚待用户在真实开发客户端中验收视觉、声音、键盘操作、资源异常手感及 32/64 人性能，因此目前不能标记为最终通过。

## 开发环境

- Java 25
- Gradle 9.5.0
- Fabric Loom 1.17.17
- Fabric Loader 0.19.3
- Fabric API 0.155.2+26.2

## 常用检查

```powershell
.\gradlew.bat foundationSelfCheck
.\gradlew.bat pageFrameworkSelfCheck
.\gradlew.bat check
.\gradlew.bat build
.\gradlew.bat runClient
.\gradlew.bat runServer
```

`foundationSelfCheck` 同时校验内存错误用例和仓库中的 2A 示例数据包；`pageFrameworkSelfCheck` 校验 2B 页面框架与示例定义。自动检查不能代替真实客户端中的视觉、声音、键盘、资源回退和性能验收。

构建产物名为 `Pixel-Tzz-Pro-<version>.jar`。
