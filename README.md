# Pixel-Tzz-Pro

`全员逃走中-扩展` 是面向 Minecraft 26.2 的 Fabric 客户端与服务端模组。

第一个基础里程碑已于 2026-07-26 通过用户实机验收：协议握手、世界持久化、数据包重载提示，以及可从 ESC 打开的玩家/主持人控制台外壳均已形成基础闭环。第二里程碑 2A～2D 也已完成并通过用户逐项 Minecraft 多客户端实机验收。正式地图、任务内容和剧情继续留在数据包内容层；V3A 玩家终端已完成，V3B 动态文本正在收口，自定义 HUD、通用运镜和捕获权威状态机属于后续 V3 能力，具体规则、页面和演出仍由数据包注册。

完整产品要求见 [`docs/REQUIREMENTS.md`](docs/REQUIREMENTS.md)。
第一里程碑的验收证据与收口边界见 [`docs/MILESTONE-1-CLOSEOUT.md`](docs/MILESTONE-1-CLOSEOUT.md)。

第二里程碑 2A 已实现数据包注册层：游戏、身份、队伍、生存状态、阶段、版本化字段、流程节点、主持人 BossBar 配置及动态面板操作都可以通过严格校验的 JSON 注册。候选定义以不可变 generation 原子发布，失败时保留上一代快照并提供有界诊断。

2A 只注册与诊断，不执行流程或面板操作，不修改任何游戏状态，也不渲染数据包页面或 BossBar。完整格式见 [`docs/DATA-PACK-API-2A.md`](docs/DATA-PACK-API-2A.md)，验收步骤见 [`docs/MILESTONE-2A-TESTING.md`](docs/MILESTONE-2A-TESTING.md)。

可运行示例位于 [`examples/pixel-tzz-base-datapack`](examples/pixel-tzz-base-datapack)。它只用于框架验收，不是正式主线；文件、定义与引用的精确数量由 `foundationSelfCheck` 从仓库现状核验，不在 README 中维护一份容易过期的手工数字。

2A 的自动检查、独立服务端有效/无效/恢复重载回归及用户客户端实机验收均已通过。验收记录见 [`docs/MILESTONE-2A-TESTING.md`](docs/MILESTONE-2A-TESTING.md)。

第二里程碑 2B 已经用户确认通过，并以提交 `79e5f99`（`Complete milestone 2B framework`）成为当前 UI 与交互质量基线：页面与主题严格编译、客户端缓存、生产布局与渲染器、管理员预览器、主题、动画及资源预检均已落地。页面 API 与停止线见 [`docs/DATA-PACK-UI-2B.md`](docs/DATA-PACK-UI-2B.md)，实施计划见 [`docs/MILESTONE-2B-PLAN.md`](docs/MILESTONE-2B-PLAN.md)，自动构建与独服回归记录见 [`docs/MILESTONE-2B-TESTING.md`](docs/MILESTONE-2B-TESTING.md)。

第二里程碑 2C 已把数据包注册内容接入服务端权威执行、当时的 Schema v2 持久化、强制页面、主持人 BossBar、一次性二次确认和上下文管理员控制台。它的定位是供未来数据包复用的执行框架，不是把正式教程、准备、开局、任务或地图内容写死进模组。当前新增的两条极短流程仅用于验收框架。

2C 已于 2026-07-28 完成自动检查与用户分项实机验收。完整边界见 [`docs/MILESTONE-2C-PLAN.md`](docs/MILESTONE-2C-PLAN.md)，验收步骤与结果见 [`docs/MILESTONE-2C-TESTING.md`](docs/MILESTONE-2C-TESTING.md)，集中问题与修复记录见 [`docs/MILESTONE-2C-ACCEPTANCE-FINDINGS.md`](docs/MILESTONE-2C-ACCEPTANCE-FINDINGS.md)。

第二里程碑 2D 已完成：数据包 API v2、世界状态 Schema v3、协议 v10、逐玩家权威准备、单活动任务时间线、双 Tick 时钟、冻结 DAG、结果与间隔、回调账本、任务事件与统计、赛后回顾、独占选择、开局审批及两级重置均已形成闭环。v10 在既有 2D 视图协议上新增了 `exclusive_choice` 卡片点击后的即时权威预约 mutation，使 HOLD、RELEASE、原子改选和多人页面广播不再等待页面提交。准备页、受众和 BossBar 由数据包注册；全员完成后仍只开放主持人最终审批，不自动开局。主持人时间线与参与者终局回顾采用服务端裁剪后的只读视图；普通玩家在游戏进行中不能浏览任务路线。

数据包注册的可见名称应是没有外围括号的干净中文名称；统一渲染层在需要强调时使用 `『名称』` 表示正式注册名称，使用 `「对象」` 表示当前对象或当前选项。对应 helper 必须幂等，已经带有同一对标点的文本不会被重复包裹。

2D 已于 2026-07-29 完成最终自动检查、全部实机问题复测、提交、推送并合并主线。功能提交为 `27b54cd`，主线合并提交为 `fc0de8b`。契约与实现边界见 [`docs/DATA-PACK-TASK-API-2D.md`](docs/DATA-PACK-TASK-API-2D.md)，实施计划见 [`docs/MILESTONE-2D-PLAN.md`](docs/MILESTONE-2D-PLAN.md)，逐项验收脚本见 [`docs/MILESTONE-2D-TESTING.md`](docs/MILESTONE-2D-TESTING.md)。

V3 转向玩家持续可见的体验：玩家终端、动态文本、自定义 HUD、通用运镜、捕获与复活。V3 不建设多 Game Profile 选择或切换；一套实际运行的正式数据包只注册一个 Game Profile。V3A 玩家终端的 Schema、协议 v11、世界状态 Schema v4、服务端权威执行、客户端页面桥接、示例数据包和实机验收脚本已经落地，并于 2026-07-30 通过完整自动检查与用户逐项 Minecraft 多客户端实机验收。非主持人 OP 始终使用普通玩家终端：世界没有主持人时只追加“认领主持人”，已有主持人时只追加“接管主持人”，服务端也不会向其下发完整主持人快照或主持人页面预览文档。历史详情使用专门的 `history_detail` 动作：客户端只提交当前投影的不透明记录键，只接收 Boolean `detail_available`，服务端从活动局冻结定义重新解析授权和目标页；事件配置的 `detail_page` 不会下发，详情只读取裁剪后的 `detail.*`。`personal_meta/<完整 player_data ID>/name` 与 `/value_name` 允许页面复用数据包声明的中文字段名和选择项显示名，但只会随同对应的已授权、已成功投影 personal 值出现，显示名缺席时也不会回退暴露机器键。普通玩家终端使用固定 `620×340` 参考构图；其余模组自有页面使用固定 `640×360` 参考画布。两者都精确复用 `2560×1440 + GUI Scale 4` 下已经验收的逻辑尺寸与控件比例：大屏不无限放大，小屏把页面、文字、动画、裁剪、滚动条与点击命中一起等比缩放，背景遮罩仍覆盖实际全屏；只有数据包明确声明的正文、数据和历史区域独立滚动。玩家历史的任意事件参数保留给后续版本，V3A 仅允许省略 `player_history.parameters` 或声明空数组，避免无效配置静默通过。总体分段与质量基线见 [`docs/MILESTONE-3-PLAN.md`](docs/MILESTONE-3-PLAN.md)，数据包格式见 [`docs/DATA-PACK-PLAYER-TERMINAL-API-3A.md`](docs/DATA-PACK-PLAYER-TERMINAL-API-3A.md)，逐项验收入口见 [`docs/MILESTONE-3A-TESTING.md`](docs/MILESTONE-3A-TESTING.md)。

V3B 动态文本与统一文字演出的产品要求、默认行为、权限边界、恢复语义和实施顺序已于
2026-07-31 逐项确认。资源、协议、多通道播放、动态字段、服务端权威实例、生命周期、恢复、
命令、偏好和示例已经接线，当前正在完成恢复契约检查、文档一致性与逐项多客户端实机验收。
一个稳定演出 ID 可以打包 Chat、Title、Subtitle、ActionBar、声音和服务端回调；数据包仍负责
注册正式内容，客户端只接收服务端授权后的最小演出包。完整计划见
[`docs/MILESTONE-3B-PLAN.md`](docs/MILESTONE-3B-PLAN.md)，数据包契约见
[`docs/DATA-PACK-MESSAGE-API-3B.md`](docs/DATA-PACK-MESSAGE-API-3B.md)，逐项五客户端验收脚本见
[`docs/MILESTONE-3B-TESTING.md`](docs/MILESTONE-3B-TESTING.md)。

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
.\gradlew.bat protocolV11SelfCheck
.\gradlew.bat flowCallbackRunnerSelfCheck
.\gradlew.bat flowCallbackAuthoritySelfCheck
.\gradlew.bat playerActionAuthoritySelfCheck
.\gradlew.bat playerTerminalRuntimeBridgeSelfCheck
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
.\gradlew.bat playerTerminalProjectorSelfCheck
.\gradlew.bat playerTerminalRouterSelfCheck
.\gradlew.bat playerTerminalSessionsSelfCheck
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

`foundationSelfCheck` 同时校验内存错误用例、仓库示例数据包、关键引用与冻结快照；`pageFrameworkSelfCheck` 校验 2B 页面框架、示例定义以及页面内容投影更新门。2C 自检继续覆盖确认令牌、主持人权威、强制流程、名单、BossBar、请求门控及回调。2D 自检覆盖逐玩家准备、时间线状态机与编排、回调账本、事件统计、独占选择、开局审批、受控命令、权限裁剪视图、两级重置、动态阶段和数据驱动 TAB。V3A 新增协议 v11、Schema v4、玩家终端路由、最小授权投影、页面会话、注册操作、两阶段函数账本以及服务端到客户端桥接自检。V3B 自检覆盖资源与覆盖、策略 Schema、协议 v12、文字/帧时间线、视觉效果、服务端实例、投递生命周期、资产协议、历史、持久化恢复、命令权限和动态 Chat 发言者。`check` 串起当前已注册的全部自检；V3A 的自动门与实机步骤见 [`docs/MILESTONE-3A-TESTING.md`](docs/MILESTONE-3A-TESTING.md)，V3B 的逐项五客户端脚本见 [`docs/MILESTONE-3B-TESTING.md`](docs/MILESTONE-3B-TESTING.md)。

自动检查不能代替真实客户端中的视觉、声音、强制关闭策略、BossBar、身份延后提交、离线重连、正常重启、冻结 `/reload` 和 32/64 人性能验收。本代理不会代替用户启动 Minecraft 或填写实机结果。

构建产物名为 `Pixel-Tzz-Pro-<version>.jar`。
