# Pixel-Tzz-Pro

`全员逃走中-扩展` 是面向 Minecraft 26.2 的 Fabric 客户端与服务端模组。

第一个基础里程碑已于 2026-07-26 通过用户实机验收：协议握手、世界持久化、数据包重载提示，以及可从 ESC 打开的玩家/主持人控制台外壳均已形成基础闭环。当前工作树已经继续落入 2A～2D 的通用框架；正式地图、任务内容、剧情、捕获规则和运镜仍属于后续数据包与内容层，不写死在模组中。

完整产品要求见 [`docs/REQUIREMENTS.md`](docs/REQUIREMENTS.md)。
第一里程碑的验收证据与收口边界见 [`docs/MILESTONE-1-CLOSEOUT.md`](docs/MILESTONE-1-CLOSEOUT.md)。

第二里程碑 2A 已实现数据包注册层：游戏、身份、队伍、生存状态、阶段、版本化字段、流程节点、主持人 BossBar 配置及动态面板操作都可以通过严格校验的 JSON 注册。候选定义以不可变 generation 原子发布，失败时保留上一代快照并提供有界诊断。

2A 只注册与诊断，不执行流程或面板操作，不修改任何游戏状态，也不渲染数据包页面或 BossBar。完整格式见 [`docs/DATA-PACK-API-2A.md`](docs/DATA-PACK-API-2A.md)，验收步骤见 [`docs/MILESTONE-2A-TESTING.md`](docs/MILESTONE-2A-TESTING.md)。

可运行示例位于 [`examples/pixel-tzz-base-datapack`](examples/pixel-tzz-base-datapack)。它只用于框架验收，不是正式主线；文件、定义与引用的精确数量由 `foundationSelfCheck` 从仓库现状核验，不在 README 中维护一份容易过期的手工数字。

2A 的自动检查、独立服务端有效/无效/恢复重载回归及用户客户端实机验收均已通过。验收记录见 [`docs/MILESTONE-2A-TESTING.md`](docs/MILESTONE-2A-TESTING.md)。

第二里程碑 2B 已经用户确认通过，并以提交 `79e5f99`（`Complete milestone 2B framework`）成为当前 UI 与交互质量基线：页面与主题严格编译、客户端缓存、生产布局与渲染器、管理员预览器、主题、动画及资源预检均已落地。页面 API 与停止线见 [`docs/DATA-PACK-UI-2B.md`](docs/DATA-PACK-UI-2B.md)，实施计划见 [`docs/MILESTONE-2B-PLAN.md`](docs/MILESTONE-2B-PLAN.md)，自动构建与独服回归记录见 [`docs/MILESTONE-2B-TESTING.md`](docs/MILESTONE-2B-TESTING.md)。

第二里程碑 2C 已把数据包注册内容接入服务端权威执行、当时的 Schema v2 持久化、强制页面、主持人 BossBar、一次性二次确认和上下文管理员控制台。它的定位是供未来数据包复用的执行框架，不是把正式教程、准备、开局、任务或地图内容写死进模组。当前新增的两条极短流程仅用于验收框架。

2C 已于 2026-07-28 完成自动检查与用户分项实机验收。完整边界见 [`docs/MILESTONE-2C-PLAN.md`](docs/MILESTONE-2C-PLAN.md)，验收步骤与结果见 [`docs/MILESTONE-2C-TESTING.md`](docs/MILESTONE-2C-TESTING.md)，集中问题与修复记录见 [`docs/MILESTONE-2C-ACCEPTANCE-FINDINGS.md`](docs/MILESTONE-2C-ACCEPTANCE-FINDINGS.md)。

第二里程碑 2D 的框架代码已经进入当前工作树：数据包 API v2、世界状态 Schema v3、协议 v10、逐玩家权威准备、单活动任务时间线、双 Tick 时钟、冻结 DAG、结果与间隔、回调账本、任务事件与统计、赛后回顾、独占选择、开局审批及两级重置均已有对应实现。v10 在既有 2D 视图协议上新增了 `exclusive_choice` 卡片点击后的即时权威预约 mutation，使 HOLD、RELEASE、原子改选和多人页面广播不再等待页面提交。准备页、受众和 BossBar 由数据包注册；全员完成后仍只开放主持人最终审批，不自动开局。主持人时间线与参与者终局回顾采用服务端裁剪后的只读视图；普通玩家在游戏进行中不能浏览任务路线。

数据包注册的可见名称应是没有外围括号的干净中文名称；统一渲染层在需要强调时使用 `『名称』` 表示正式注册名称，使用 `「对象」` 表示当前对象或当前选项。对应 helper 必须幂等，已经带有同一对标点的文本不会被重复包裹。

2D 当前工作树已于 2026-07-29 通过本轮完整 `clean check build`（37 个 actionable tasks 全部执行），仍在用户逐项实机验收与修复复测阶段，不能写成“已经实机通过”。契约与实现边界见 [`docs/DATA-PACK-TASK-API-2D.md`](docs/DATA-PACK-TASK-API-2D.md)，当前实施状态见 [`docs/MILESTONE-2D-PLAN.md`](docs/MILESTONE-2D-PLAN.md)，逐项验收脚本见 [`docs/MILESTONE-2D-TESTING.md`](docs/MILESTONE-2D-TESTING.md)。

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
.\gradlew.bat protocolV10SelfCheck
.\gradlew.bat flowCallbackRunnerSelfCheck
.\gradlew.bat flowCallbackAuthoritySelfCheck
.\gradlew.bat taskCallbackRunnerSelfCheck
.\gradlew.bat taskCallbackLedgerAuthoritySelfCheck
.\gradlew.bat taskFactAuthoritySelfCheck
.\gradlew.bat timelineAuthoritySelfCheck
.\gradlew.bat timelineServerRuntimeSelfCheck
.\gradlew.bat timelineViewSelfCheck
.\gradlew.bat exclusiveChoiceAuthoritySelfCheck
.\gradlew.bat timelineControlOperationSelfCheck
.\gradlew.bat timelineApprovalAuthoritySelfCheck
.\gradlew.bat readinessAuthoritySelfCheck
.\gradlew.bat timelineCommandsSelfCheck
.\gradlew.bat worldResetAuthoritySelfCheck
.\gradlew.bat flowRosterAuthoritySelfCheck
.\gradlew.bat hostFlowBossBarSelfCheck
.\gradlew.bat controlRequestGateSelfCheck
.\gradlew.bat worldActivityInitializationSelfCheck
.\gradlew.bat tabListDisplaySelfCheck
.\gradlew.bat check
.\gradlew.bat build
.\gradlew.bat runClient
.\gradlew.bat runServer
```

`runClient` 的主测试客户端默认固定使用 `Player972` 和 `run/client`。额外测试客户端通过
`-PpixelTzzUsername=PlayerB` 或 `-PpixelTzzUsername=PlayerC` 显式覆盖用户名。五个固定验收账号
继续使用原有独立目录：`Player972 → run/client`、`PlayerB → run/client-b`、
`PlayerC → run/client-c`、`PlayerD → run/client-PlayerD`、`PlayerE → run/client-PlayerE`；
其他临时用户名才使用 `run/client-<用户名>`，不会与既有客户端档案冲突。

Windows 下可在仓库根目录用一条命令批量启动五个验收客户端：

```powershell
.\start-test-clients.cmd
```

脚本先执行一次客户端预编译，再按 `Player972 → PlayerB → PlayerC → PlayerD → PlayerE`
每隔 2 秒发出启动命令；五个客户端默认均使用 `2560×1440` 窗口，并继续复用各自独立的
`run/client*` 目录。入口明确调用 PATH 中的 PowerShell 7 `pwsh.exe`，不会回退到旧版
Windows PowerShell；Gradle 辅助窗口会隐藏，只保留 Minecraft 客户端窗口。

需要临时覆盖尺寸或间隔时，可直接把参数传给同一入口：

```powershell
.\start-test-clients.cmd -Width 1920 -Height 1080 -IntervalSeconds 3
```

`foundationSelfCheck` 同时校验内存错误用例、仓库示例数据包、关键引用与冻结快照；`pageFrameworkSelfCheck` 校验 2B 页面框架、示例定义以及页面内容投影更新门。2C 自检继续覆盖确认令牌、主持人权威、强制流程、名单、BossBar、请求门控及回调。新增 2D 自检覆盖 Schema v3 与迁移、协议 v10（含独占选择即时预约 mutation）、逐玩家准备、时间线状态机与编排、回调账本、事件统计、独占选择、开局审批、受控命令、权限裁剪视图、两级重置、动态阶段和数据驱动 TAB。`check` 串起当前已注册的全部自检；完整记录见 2D 测试文档。

自动检查不能代替真实客户端中的视觉、声音、强制关闭策略、BossBar、身份延后提交、离线重连、正常重启、冻结 `/reload` 和 32/64 人性能验收。本代理不会代替用户启动 Minecraft 或填写实机结果。

构建产物名为 `Pixel-Tzz-Pro-<version>.jar`。
