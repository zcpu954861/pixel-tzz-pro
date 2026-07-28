# Pixel-Tzz-Pro

`全员逃走中-扩展` 是面向 Minecraft 26.2 的 Fabric 客户端与服务端模组。

第一个基础里程碑已于 2026-07-26 通过用户实机验收：协议握手、世界持久化、数据包重载提示，以及可从 ESC 打开的玩家/主持人控制台外壳均已形成基础闭环。地图、任务、猎人分配、准备流程、捕获与运镜尚未开始实现。

完整产品要求见 [`docs/REQUIREMENTS.md`](docs/REQUIREMENTS.md)。
第一里程碑的验收证据与收口边界见 [`docs/MILESTONE-1-CLOSEOUT.md`](docs/MILESTONE-1-CLOSEOUT.md)。

第二里程碑 2A 已实现数据包注册层：游戏、身份、队伍、生存状态、阶段、版本化字段、流程节点、主持人 BossBar 配置及动态面板操作都可以通过严格校验的 JSON 注册。候选定义以不可变 generation 原子发布，失败时保留上一代快照并提供有界诊断。

2A 只注册与诊断，不执行流程或面板操作，不修改任何游戏状态，也不渲染数据包页面或 BossBar。完整格式见 [`docs/DATA-PACK-API-2A.md`](docs/DATA-PACK-API-2A.md)，验收步骤见 [`docs/MILESTONE-2A-TESTING.md`](docs/MILESTONE-2A-TESTING.md)。

可运行示例位于 [`examples/pixel-tzz-base-datapack`](examples/pixel-tzz-base-datapack)，当前包含 `38` 个文件和 `30` 项 Pixel TZZ 定义。原有六个 2B 页面继续只用于预览；新增四个 2C 页面组成通用初始化与猎人初始化两条“说明 → 确认 → 完成”极短验收流程，且分别显式采用 `if_incomplete` 与 `always` 完成策略。

2A 的自动检查、独立服务端有效/无效/恢复重载回归及用户客户端实机验收均已通过。验收记录见 [`docs/MILESTONE-2A-TESTING.md`](docs/MILESTONE-2A-TESTING.md)。

第二里程碑 2B 已经用户确认通过，并以提交 `79e5f99`（`Complete milestone 2B framework`）成为当前 UI 与交互质量基线：页面与主题严格编译、客户端缓存、生产布局与渲染器、管理员预览器、主题、动画及资源预检均已落地。页面 API 与停止线见 [`docs/DATA-PACK-UI-2B.md`](docs/DATA-PACK-UI-2B.md)，实施计划见 [`docs/MILESTONE-2B-PLAN.md`](docs/MILESTONE-2B-PLAN.md)，自动构建与独服回归记录见 [`docs/MILESTONE-2B-TESTING.md`](docs/MILESTONE-2B-TESTING.md)。

第二里程碑 2C 已把数据包注册内容接入服务端权威执行、Schema v2 持久化、强制页面、主持人 BossBar、一次性二次确认和上下文管理员控制台。它的定位是供未来数据包复用的执行框架，不是把正式教程、准备、开局、任务或地图内容写死进模组。当前新增的两条极短流程仅用于验收框架。

2C 已于 2026-07-28 完成自动检查与用户分项实机验收。完整边界见 [`docs/MILESTONE-2C-PLAN.md`](docs/MILESTONE-2C-PLAN.md)，验收步骤与结果见 [`docs/MILESTONE-2C-TESTING.md`](docs/MILESTONE-2C-TESTING.md)，集中问题与修复记录见 [`docs/MILESTONE-2C-ACCEPTANCE-FINDINGS.md`](docs/MILESTONE-2C-ACCEPTANCE-FINDINGS.md)。

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
.\gradlew.bat confirmationTokensSelfCheck
.\gradlew.bat hostAuthoritySelfCheck
.\gradlew.bat forcedFlowAuthoritySelfCheck
.\gradlew.bat statePersistenceSelfCheck
.\gradlew.bat worldStateColdStartSelfCheck
.\gradlew.bat protocolV7SelfCheck
.\gradlew.bat flowCallbackRunnerSelfCheck
.\gradlew.bat flowCallbackAuthoritySelfCheck
.\gradlew.bat flowRosterAuthoritySelfCheck
.\gradlew.bat hostFlowBossBarSelfCheck
.\gradlew.bat controlRequestGateSelfCheck
.\gradlew.bat worldActivityInitializationSelfCheck
.\gradlew.bat check
.\gradlew.bat build
.\gradlew.bat runClient
.\gradlew.bat runServer
```

`runClient` 的主测试客户端默认固定使用 `Player972`。额外测试客户端通过
`-PpixelTzzUsername=PlayerB` 或 `-PpixelTzzUsername=PlayerC` 显式覆盖用户名，并使用独立 `--gameDir`，不会与主客户端档案冲突。

`foundationSelfCheck` 同时校验内存错误用例、仓库示例数据包及 2C 极短流程的关键引用与执行快照；`pageFrameworkSelfCheck` 校验 2B 页面框架与示例定义。其余 2C 自检分别覆盖确认令牌、主持人权威、流程节点、Schema v2 持久化、迁移优先冷启动、协议 v7、回调、名单调整、BossBar、请求防重放/限频及 reload 时空活动状态恢复。`check` 会串起当前全部自检；2026-07-28 已完成最终干净的 `clean check build`，24 个任务全部通过。

自动检查不能代替真实客户端中的视觉、声音、强制关闭策略、BossBar、身份延后提交、离线重连、正常重启、冻结 `/reload` 和 32/64 人性能验收。本代理不会代替用户启动 Minecraft 或填写实机结果。

构建产物名为 `Pixel-Tzz-Pro-<version>.jar`。
