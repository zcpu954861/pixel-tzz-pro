# V3C：倒计时系统与开局倒计时实施计划

状态：**已完成；完整自动门与用户逐项五客户端实机验收均已通过**

适用基线：

- Minecraft：`26.2`
- 当前模组版本：`0.1.0`
- 目标游戏 API：v4
- 目标网络协议：v13
- 目标世界状态：Schema v5
- 最后整理：2026-08-06

## 0. 文档职责

本文固定 Pixel TZZ Pro V3C 的产品边界、视觉方向、权威模型、实施顺序和完成门槛。精确资源
路径、JSON 字段、默认值、范围与示例见
[`DATA-PACK-COUNTDOWN-API-3C.md`](DATA-PACK-COUNTDOWN-API-3C.md)，逐项五客户端验收入口见
[`MILESTONE-3C-TESTING.md`](MILESTONE-3C-TESTING.md)。

原 V3C “常驻 HUD + 正式开局倒计时”原型已完整保存在 `codex/milestone-3c` 分支，留档提交为
`e66b92d`。该原型没有合并主线，不是当前交付目标。未来确有常驻 HUD 需求时可以重新评估，
当前分支只迁移其中经验证有价值的倒计时权威内核，不携带信息坞、组件树、路由和布局编辑器。

本文不是实机通过清单。自动检查、成功构建和客户端能启动都不能代替真实画面、声音、多人同步、
掉线、Reload 和正常重启结果。

## 1. 阶段目标

V3C 建立：

1. 一套与具体用途解耦的服务端权威倒计时核心；
2. 当前唯一公开用途：主持人批准开局后的正式开局倒计时；
3. 一个在 ActionBar 上方短时出现、紧凑、不抢视野的专用客户端表现层；
4. 数据包广泛但有界的文案、颜色、时间格式、逐位动画、声音和生命周期配置；
5. 掉线、取消、Reload、重启、回调失败和重同步下仍唯一且可恢复的时序。

最终体验应做到：

- 主持人批准后，所有冻结参与者看到同一个权威倒数；
- 倒计时不占用原版 ActionBar、Title 或 Subtitle；
- 数字固定宽度，变化位可以像机械里程表一样独立上下滚动；
- 数据包可选择整数、十分位或百分位，最高显示到 `0.01s`；
- 客户端高帧率动画平滑，但服务端开局仍严格落在 Tick；
- 玩家可降级动画、启用高对比或静音，不能从模组设置彻底隐藏关键倒计时；
- 倒计时完成后才进入热身，倒计时与热身都不计入全局游戏时长；
- 任何异常路径都不会产生两个实例、重复回调、重复开局或残留限制。

## 2. 明确不做

当前 V3C 不包含：

- 右下角常驻信息坞；
- 当前任务、阶段、身份或本局数据的常驻 HUD；
- HUD Component、Layout、Profile、路由、Binding 和任意布局 API；
- 玩家拖动、重排、自由缩放、排除矩形或组件管理；
- 任意坐标、客户端脚本、Shader、输入框、按钮或 HUD 游戏操作；
- 多倒计时并发；
- 新实例覆盖当前实例；
- 倒计时队列、自动接替或合并；
- 主持人或玩家跳过、快进、立即完成和修改剩余时间；
- 数据包自行调用内部用途处理器；
- 通用镜头、捕获、死亡旁观和复活；这些仍属于后续阶段。

普通临时文字继续由 V3B 的 Chat、Title、Subtitle、ActionBar 和声音承担。当前任务与历史详情
继续由 V3A 玩家终端承担，不为了填满屏幕复制第二套常驻信息。

## 3. 通用核心与 opening 特化

倒计时核心只理解：

- instance 与 purpose；
- total、remaining、paused 和权威状态；
- 固定参与者、显示受众和掉线策略；
- 限制；
- Checkpoint；
- 生命周期回调与账本；
- generation 冻结、持久化和网络投影。

核心不理解任务 DAG、热身或猎人语义。用途处理器负责启动前冻结和完成后的业务提交。当前只
注册 `opening`：

- 入口是 Game 的 `opening_countdown`；
- 主持人批准时冻结 `LaunchPlan`；
- complete callbacks 成功后提交该 LaunchPlan；
- 未来其他用途必须新增受控接入点，不能让数据包伪造 purpose。

全局同时最多一个非终态倒计时。任何第二次启动都必须明确失败；既不覆盖现有实例，也不排队
等待现有实例结束。

## 4. 视觉方向

### 4.1 固定位置

倒计时使用模组自己的专用 Overlay，固定绘制在原版 ActionBar 基准线上方：

```text
                         正式游戏即将开始  00:05
                         ━━━━━━━━━━━━━━━━━
                              原版 ActionBar
```

它不向 ActionBar 发送文字，不清除或挤占 V3B ActionBar。无论原版 ActionBar 当前是否有内容，
倒计时都使用同一稳定基线，不能在 ActionBar 出现或消失时上下跳动。

### 4.2 视觉尺度

- 普通文字大小参考原版 ActionBar；
- 数字可以有限放大，但不形成中央大数字；
- 默认只占一条主行和一条细进度线；
- 背景使用轻量半透明底、细描边和有限内边距；
- 不覆盖准星、不遮挡主要视野、不铺满屏幕；
- 2K 基准下清晰，小窗整体等比缩小，大屏设置最大尺寸；
- 长中文、Emoji、前后缀必须有界收缩、换行或省略，数字永不裁切。

### 4.3 与 Screen 的关系

- 正常游戏、聊天框与 ESC 中保持可见；
- 原版 F1 继续隐藏整个 HUD，V3C 尊重该行为；
- “不可隐藏”指模组设置没有关闭开关、透明度 0 或移出屏幕入口；
- 第三方 Screen 完全覆盖 HUD 时不承诺强行绘制在其上方；
- Screen 关闭后直接恢复当前权威值，不重播进入动画或旧秒数。

## 5. 时间与精度

服务端仍以 20 TPS 推进权威 remaining。客户端收到 Tick 锚点和速率后，用单调时钟在相邻 Tick
之间按渲染帧插值：

- `seconds`：整数秒；
- `tenths`：最高 `0.1s`；
- `hundredths`：最高 `0.01s`。

`0.01s` 只是视觉显示精度，不是服务器 100 TPS：

- 不为百分位发送 100Hz 网络包；
- 不让客户端上报完成；
- 不改变服务端限制、回调或 LaunchPlan 提交 Tick；
- 低 FPS 时跳到当前值，不排队补播已过百分位；
- 正值按显示粒度向上取整，避免尚未完成时提前出现负数；
- 客户端可能短暂显示 `00.00`，但只有权威 complete 才能退场和开局。

时间使用固定宽度，精度变化和借位不能让整条显示左右抖动。

## 6. 逐位滚动

默认动画方向固定为“新数字从上进入，旧数字向下退出”：

```text
旧：  00:06
          ↓
新：  00:05   # 只有个位变化
```

规则：

- 每位数字拥有独立裁切窗口；
- 只有变化位滚动；
- 冒号和小数点稳定不动；
- `10 → 09`、`01:00 → 00:59` 正确处理多个变化位；
- 数据包可配置滚动/淡入/瞬切、方向、时长、距离、缓动、错峰、入场色和离场色；
- 小数位是否滚动单独配置，默认直接更新，避免百分位持续制造视觉噪声；
- 暂停必须落到完整字符，不能停在半格；
- 网络校正、重连和低 FPS 不补演过期数字；
- simplified 缩短或移除位移，static 直接切换。

## 7. 数据包配置边界

数据包可以独立注册：

- 显示名、标题、前缀、后缀；
- 等待、暂停和完成文案；
- 总时长；
- 时间格式、精度、补零和分隔符；
- 文字、背景、描边、强调、完成和新旧数字颜色；
- ActionBar 上方安全间距、内边距和最大宽度；
- 数字比例、阴影和逐位动画；
- 是否显示细进度线及其颜色、粗细和平滑；
- 进入、退出和完成表现；
- Checkpoint 的文字、颜色、有限强调、声音和可选 V3B Cue；
- required player 与 host 掉线策略；
- 参与者限制；
- start/pause/resume/cancel/complete 服务端回调。

模组保留：

- 固定显示区域和渲染顺序；
- 全局单实例；
- 数值、透明度、位移、音量、闪烁和动画时长硬上限；
- 最低可读性和不可由模组设置隐藏；
- full/simplified/static、高对比和静音的辅助功能覆盖权；
- 权威时间、受众、取消、完成和恢复；
- 对未知键、越界值和错误引用的严格失败。

## 8. 玩家设置与辅助功能

设置入口：

```text
选项 → 全员逃走中设置 → 开局倒计时
```

只需要一个紧凑设置页，包含：

- 动态强度：full / simplified / static；
- 高对比；
- 倒计时音量；
- 本地预览；
- 重置和诊断。

不提供完全隐藏、自由位置、透明度 0、修改时间精度或跳过功能。数据包不能强制玩家恢复 Full、
关闭高对比或提高本地音量。Static 只删除动画，不删除正文、时间或关键状态。

## 9. 开局权威时序

未配置 `opening_countdown` 时保持 2D 立即开局。存在定义时：

1. 玩家完成初始化、开局前字段和逐人准备；
2. 主持人审阅并批准；
3. 服务端原子冻结参与者、可达任务计划、倒计时定义、限制、回调和 LaunchPlan；
4. 进入 `COUNTDOWN/running`；
5. 权威剩余时间到零，进入 `completing`；
6. required complete callbacks 全部成功；
7. 有 warmup 时启动 warmup；
8. warmup 全部结束；
9. 全局游戏时钟和第一个计时 main task 同一权威提交启动。

无 warmup 时，第 7 步直接原子启动全局游戏时钟与第一个 main task。

倒计时和所有 `kind: warmup`、`counts_toward_game_time: false` 的赛前环节都不计入全局游戏
时长。Warmup 可以拥有自己的 Task clock。倒计时完成时不得重新选择参与者、重查 readiness、
扩大 audience 或用 live generation 重编首任务。

required 定义无效时阻止批准。optional 定义无效时，主持人必须明确审阅“无倒计时立即开局”
后才可降级，不能静默绕过。

## 10. 状态与控制

权威状态：

- `running`
- `waiting_for_players`
- `recovery_wait`
- `completing`
- `canceling`
- `completed`
- `canceled`

主持人不能跳过或快进。正常控制台只提供高风险“取消倒计时”：

- 二次确认绑定 game instance、countdown instance、purpose、状态版本和到期 Tick；
- 摘要写明剩余时间、保留内容、丢弃内容和外部回调不可回滚部分；
- 状态变化后旧确认失效；
- “重新审阅”直接打开最新权威确认，不连续弹两个错误；
- 取消后回到数据包注册的等待批准阶段；
- 保留身份、初始化、准备和开局前字段；
- 丢弃本次冻结候选，不自动开始下一轮。

手动暂停不属于 3C 对外操作。暂停由掉线策略、重启恢复协调或内部安全条件触发。

## 11. 掉线与接管

required player：

- `pause`：默认；冻结 remaining，等待同一 UUID 返回并完成协调；
- `cancel`：正式取消并回到等待批准；
- `continue`：其他人继续，但不移出冻结玩家、不换人、不扩大受众。

host 策略独立配置，默认 `continue`。主持人掉线不会把 OP 自动提升为主持人。合法接管后，新
主持人看到并控制同一个 Countdown instance，不重建、不重播、不改变参与者。

## 12. Reload 与正常重启

`/reload`：

- 使用批准时冻结的 definition generation；
- 不中途替换 duration、presentation、声音、限制和 callback；
- 不因 Reload 补扣或补偿 Tick；
- 新 generation 只影响下一轮；
- 编译失败继续保留上一代有效目录和活动实例。

正常关闭与重启：

- 持久化 instance、purpose、state/version、remaining、冻结定义、参与者、LaunchPlan、限制
  恢复标记和 callback ledger；
- 启动后先进入 `recovery_wait`；
- 离线墙钟时间不计入倒计时；
- 所需连接尚未协调完成时不倒数；
- 恢复同一实例，不重播 start、旧 Checkpoint 或完整进入动画；
- 冻结快照损坏时 fail closed，由主持人诊断和安全取消，绝不立即开局。

## 13. 参与者限制

默认：

- 冻结移动与速度推进；
- 阻止攻击；
- 阻止方块、实体和物品交互；
- 阻止使用、丢弃、交换物品和打开库存；
- 临时伤害免疫；
- 允许镜头、第三人称、聊天和 ESC。

限制通过服务端事件门与独立权威状态实现，不写永久药水效果、不改游戏模式、不覆盖无关属性。
完成、取消、重启恢复失败、紧急终止和参与者移出都走同一个幂等解除边界。

## 14. Checkpoint、声音与回调

Checkpoint：

- 以唯一 ID 和唯一 remaining 注册；
- 可提供有限 Presentation override、声音和 V3B Cue；
- 低 FPS 跨过多个节点时不爆发补播旧声音；
- 每客户端每节点至多播放一次；
- 本地静音不改变权威状态。

生命周期回调：start、pause、resume、cancel、complete。

- 只在服务端执行注册 `mcfunction`；
- 以 instance、purpose、slot、callback ID、scope 和 participant UUID 记账；
- 成功项至多一次；
- `PENDING` 冷恢复复用原 occurrence，不创建替代 occurrence；
- `FAILED` 按原冻结上下文普通重试；`OUTCOME_UNKNOWN` 只能在明确接受重复外部副作用风险后走
  独立高风险重试；
- required complete 未成功前不提交 LaunchPlan；
- canceling/completing 不回到 running 重播最后几秒；
- completed 后交接失败保持同一冻结实例和诊断，停止自动反复提交；主持人可重试同一候选，或
  高风险放弃并返回等待批准；
- Preview、渲染、设置和重同步永不执行回调。

## 15. 网络与客户端恢复

专用网络投影提供 Replace、Patch、Clear 和 Resync：

- 帧携带 game epoch、instance、purpose、state version 和 sequence；
- 时钟携带服务端 Tick 锚点、remaining、rate 和 paused；
- 结构变化 Replace，状态与时间 Patch，结束或撤权 Clear；
- Clear 墓碑阻止迟到旧帧跨局复活；
- 乱序、重复和旧 epoch 丢弃；
- 序列缺口只触发节流 Resync；
- 重同步不重播进入动画、声音或旧关键秒；
- 服务端不每 Tick 给所有玩家重发完整定义。

客户端网络校正优先保证“当前值正确”，不按顺序补演过期视觉。

## 16. 预览与诊断

普通本地预览使用内置样例或当前已授权定义，可以检查：

- 进入与退出；
- 普通秒与最后三秒；
- `10 → 09` 和 `01:00 → 00:59`；
- tenths/hundredths；
- pause、waiting、resume 和 complete；
- full/simplified/static、高对比和静音。

预览不写世界状态、不发其他客户端、不执行回调、不创建实例、不写历史。主持人诊断继续提供
简洁汉化和原始详情，但普通玩家不接收资源路径、Selector、NBT 或异常栈。

## 17. 性能与硬边界

实现必须冻结并由 SelfCheck 证明：

- 每 generation Countdown 数量；
- 单定义文本、Checkpoint、Callback、声音和投影字节上限；
- duration、动画时长、位移、透明度、字号、进度线和音量范围；
- 每秒 Patch、待处理序列、Resync 和诊断上限；
- 单调时钟校正与低 FPS 追帧不会积压动画；
- 32/64 人时不每 Tick 重编定义或生成无界网络流量；
- 全局活动实例硬上限始终为 1。

超限定义隔离并保留上一代，不截断关键数字后继续假装成功。

## 18. 实施架构

从 archived 原型保留：

- Countdown authority 和状态机；
- opening approval 与冻结 LaunchPlan；
- 持久 Countdown、Schema v5 迁移和 recovery wait；
- callback ledger 与重试确认；
- restriction events 与 Mixin；
- Replace/Patch/Clear/Resync 的顺序思想；
- Tick、Reload、掉线、重连和服务器重启语义。

必须重做或拆分：

- Countdown Definition 从 HUD Definitions 独立；
- 删除 Countdown 对 HUD Layout/Component 的引用；
- 服务端改为专用 Countdown Runtime；
- 客户端改为专用 ActionBarAboveCountdownOverlay；
- 重新定义紧凑 Presentation 与逐位数字状态；
- 设置页只保留开局倒计时；
- 示例数据包、语言键、自检和文档移除常驻 HUD。

模块边界：

- content：Countdown record、严格 parser、覆盖与引用校验；
- server：通用 authority、opening purpose handler、受众、限制和回调；
- state：Schema v5 instance、冻结定义、LaunchPlan 和账本；
- network：v13 专用 payload、sequence、tombstone 和 resync；
- client：时钟插值、固定位置、逐位滚动、声音和降级；
- settings：动态强度、高对比、音量、预览和重置；
- integration：2D approval/timeline、V3B Cue、ESC/F1/ActionBar。

## 19. 实施顺序

### 19.1 3C-1：剥离资源模型

- 迁移 Countdown record、parser 和 Game opening 引用；
- 删除 HUD Component/Layout/Profile 依赖；
- 固定 Presentation、精度、动画和安全范围；
- 更新示例与错误夹具。

### 19.2 3C-2：权威与持久化

- 迁移 authority、single-instance gate 和 opening purpose；
- 迁移 LaunchPlan、限制、掉线、取消和 callback ledger；
- 验证 Reload、重启恢复与旧世界迁移。

### 19.3 3C-3：协议与专用客户端

- 建立专用 Replace/Patch/Clear/Resync；
- 实现 ActionBar 上方布局和稳定安全基线；
- 实现客户端时钟、0.01s 格式、固定宽度和逐位滚动；
- 实现进入、退出、完成、Checkpoint 声音和低 FPS 追帧。

### 19.4 3C-4：设置与预览

- 设置分类改为“开局倒计时”；
- 实现 full/simplified/static、高对比、音量和不可隐藏；
- 实现无副作用本地预览与主持人诊断。

### 19.5 3C-5：收口

- 完成自动 SelfCheck 与完整 `clean check build`；
- 按验收手册逐项完成五客户端测试；
- 集中修复并只回归受影响项；
- 用户明确全部 PASS 后才提交、推送和合并。

## 20. 自动与实机边界

自动检查应证明：

- 严格定义、覆盖、上一代恢复和硬上限；
- single-instance gate；
- 权威状态、掉线、限制、取消、回调和 LaunchPlan；
- Schema v5 持久化与恢复；
- v13 sequence、tombstone、resync 和容量；
- 时间格式、向上取整、逐位差异和低 FPS 追帧纯逻辑；
- 本地设置不能隐藏、修改权威时间或扩大受众。

自动检查不能证明：

- ActionBar 上方是否自然；
- 字号、边距、背景和进度线是否达到 3A 水准；
- 数字滚动是否顺滑、不晃动；
- 0.01s 在 60/144 FPS 下是否可读；
- 五端声音、关键秒和完成是否一致；
- ESC、F1、聊天、窗口缩放和第三方 UI 是否符合预期。

这些项目必须由用户在真实 Minecraft 多客户端中逐项确认。

## 21. V3C 完成定义

V3C 只有同时满足以下条件才算完成：

1. Countdown 成为独立资源，不依赖 HUD Component/Layout/Profile；
2. 通用核心不硬编码 opening 业务，当前只公开 opening 接入点；
3. 全局最多一个活动实例，第二次启动不覆盖、不排队；
4. 玩家和主持人均不能跳过、快进或修改剩余时间；
5. ActionBar 上方显示紧凑、稳定，不占用原版文字通道；
6. 时间最高显示到 `0.01s`，且明确为客户端插值；
7. 固定宽度逐位滚动、借位、暂停、追帧和静态降级正确；
8. 数据包表现配置充分且全部受安全硬上限约束；
9. 模组设置不能隐藏倒计时，F1 仍遵循原版；
10. 无倒计时 Game 保持 2D 立即开局；
11. 有倒计时时严格执行批准、倒计时、热身、正式计时顺序；
12. 倒计时与热身均不计入全局游戏时长；
13. 掉线、取消、Reload、重启和回调失败不重复成功项或开局；
14. 限制完整且幂等恢复；
15. 示例、API 文档、实现、自动检查和验收命令一致；
16. 完整自动门和用户逐项五客户端实机验收全部通过；
17. 用户明确确认 V3C 收口。

在以上条件满足前，V3C 只能标记为实现或验收中，不能宣称完成，也不能把 archived 常驻 HUD
原型重新混入当前阶段。
