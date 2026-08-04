# Pixel TZZ Pro 2A–3B 示例与验收夹具

此数据包只用于模组开发验收，同时保留：

- 2A/2B 的定义、主题、布局和组件预览；
- 2C 的极短服务端权威初始化流程；
- 2D 的逐玩家准备、最小任务时间线、独占选择、事件、统计和回调夹具。
- 3A 的普通玩家终端、身份专属任务页、个人数据裁剪、玩家历史与注册函数夹具；
- 3B 的文字效果、完整演出、动态字段、服务端播放与控制验收夹具。

它不是正式的全员逃走中玩法数据包。所有“北门”“中央站”“南侧场地”“终端”等名称均是无地图坐标、无玩法含义的显式占位，不应复制为正式任务。

## 2B 预览页面

以下六个页面只用于开发预览器，不被可执行流程引用：

- `pixel_tzz:tutorial/welcome`：组件展示；
- `pixel_tzz:tutorial/buttons`：四种按钮语义与禁用状态展示；
- `pixel_tzz:tutorial/rules`：响应式布局；
- `pixel_tzz:tutorial/acknowledge`：FieldInput；
- `pixel_tzz:hunter/briefing`：Compact 单列、Standard/Wide 双列的 64 人响应式玩家档案 Repeat/Scroll；
- `pixel_tzz:hunter/acknowledge`：可选图片缺失后的原版资产回退。

这些页面需要配合相邻的 `pixel-tzz-base-resourcepack` 进行完整客户端验收。

## 2C 初始化流程

### 通用初始化

`pixel_tzz:general_tutorial`（版本 `2`）保持“说明 → 最终确认 → 完成”三步：

- 页面：`pixel_tzz:fixture/general/briefing` → `pixel_tzz:fixture/general/confirm`；
- 来源操作：`pixel_tzz:start_general_initialization`；
- `completion_policy: if_incomplete`，当前版本已完成者不重复打开。

### 猎人初始化与独占出生点

`pixel_tzz:hunter_initialization` 已提升到版本 `3`：

```text
身份说明 → 独占出生点选择 → 最终确认 → 完成并锁定
```

- 来源操作：`pixel_tzz:assign_hunter` 或 `pixel_tzz:reinitialize_hunter`；
- 字段：`pixel_tzz:hunter_spawn`；
- 三个中性示例选项：`north_gate`、`central_station`、`south_yard`；
- 同一游戏实例内一个选项只能由一名猎人占用，因此夹具最多同时初始化三名猎人；
- 点击卡片时立即创建权威临时预约；再次点击自己的临时值会释放，点击另一项会原子改选；
- 临时预约、释放、冲突和最终锁定会主动广播给同流程页面；最终确认页可以返回重选并保留当前预约；
- 完整流程完成时才锁定字段并提交猎人身份；
- 取消未完成流程会释放新预约；身份不再是猎人时会释放锁定；
- 主持人批准开局前，所有非主持人猎人必须拥有当前有效且已锁定的出生点。

旁观者仍无需先完成通用初始化。恢复参与时继续按既有记录分流：

- `pixel_tzz:restore_runner`：已完成当前通用初始化版本，立即恢复为逃走者；
- `pixel_tzz:initialize_runner`：尚未完成当前版本，先运行通用初始化，完成后再原子提交逃走者身份。

## 2D 玩家准备

游戏进入 `pixel_tzz:ready` 时，框架自动打开 `pixel_tzz:player_readiness`，不会把内部操作 `pixel_tzz:open_player_readiness` 暴露成普通主持人按钮。

- 受众使用身份标签 `pixel_tzz:ready_participant`；当前夹具的逃走者与猎人均带此标签；
- 主持人和旁观者不计入准备分母；
- 每名逃走者必须在不可关闭页面中逐人确认，确认后不能主动取消；
- 主持人控制台显示 `玩家准备: 已完成/总数`、未完成人员和同一进度的 BossBar；
- 掉线会清除该玩家准备，重连自动重开；身份、队伍或 `invalidates_ready: true` 字段变化会重算准备资格；
- `/reload`、存档加载和正常服务器重启恢复冻结会话，不会替玩家准备；
- 全员准备后只开放主持人“批准开局”，不会自动进入时间线。

准备页、受众、主题和 BossBar 均由本数据包注册；模组只提供可复用的权威准备事务。当前 `host_can_force` 为 `false`，不提供主持人代确认。

## 2D 最小任务时间线

游戏定义使用 `api_version: 2`。主持人可用 `pixel_tzz:enter_initializing` 从设置进入初始化，再用既有 `pixel_tzz:enter_ready` 进入准备；所有准备参与者逐人确认后才开放批准，批准阶段为 `pixel_tzz:ready`，批准后进入 `pixel_tzz:warmup`。

时间线严格无环且同时只有一个任务：

```text
acceptance/warmup
  └─ ready → acceptance/main_branch
                 ├─ success → 60 秒结果间隔 → acceptance/timeout_only
                 │                               └─ 10 秒到时 → 60 秒间隔 → ended
                 ├─ failure → 60 秒结果间隔 → ended
                 └─ timeout → 60 秒结果间隔 → ended
```

上述 success、failure、timeout 与 `timeout_only/finished` 四个示例间隔都注册为 `1200 Tick（60 秒）`，便于人工完成审阅与二次确认。

### 任务覆盖

- `pixel_tzz:acceptance/warmup`
  - `kind: warmup`；
  - `completion_policy: event_only`；
  - `counts_toward_game_time: false`，只推进环节时间，不推进正式游戏时间；
- `pixel_tzz:acceptance/main_branch`
  - `completion_policy: early_or_timeout`，时限 5 分钟，避免 UI 验收期间意外超时；
  - 成功、提前失败、自然超时三条结果路线；
  - 每个结果拥有独立注册的结果特定 interval；当前示例统一为 60 秒，仍分别冻结和执行；
  - `terminal_activated` 为最多 8 条的重复事件；
  - `activated_terminals` 为 `increment` 整数统计；
  - `on_start_players` 对每名猎人执行一次，并把锁定的 `hunter_spawn` 绑定为宏字段 `spawn_point`；
- `pixel_tzz:acceptance/timeout_only`
  - `completion_policy: timeout_only`，时限 10 秒；
  - 没有提前完成入口；
  - 到时回调记录 `deadline_reached`，随后提交唯一结果。

普通参与者在游戏进行中仍看不到完整路线；主持人可以查看候选分支，终局参与者可在回顾中查看实际事件、统计和路线。

## 3A 玩家终端

游戏定义使用 `api_version: 3`，默认玩家页为 `pixel_tzz:player/home`，并启用玩家历史总开关。

当前正式示例页：

- `pixel_tzz:player/home`：默认玩家首页；
- `pixel_tzz:player/task_runner`：逃走者当前任务；
- `pixel_tzz:player/task_hunter`：猎人当前任务和本人锁定出生点；
- `pixel_tzz:player/history`：左侧过去任务轨迹与右侧原位回顾档案；示例 Game 使用 `history_source: "tasks"`；
- `pixel_tzz:player/history_detail`：保留的独立详情兼容示例，当前历史夹具不跳转至此；
- `pixel_tzz:player/profile`：仅本人获准读取的个人数据；
- `pixel_tzz:player/help`：四份文档的动画轮播入口；
- `pixel_tzz:player/help_catalog`：同一批文档的直接目录；
- `pixel_tzz:player/help_terminal`、`help_visibility`、`help_history`、`help_actions`：终端基础、可见内容、事件回顾与安全操作正文。

路由会让活动任务中的逃走者与猎人进入不同页面，结束阶段进入历史页；其他上下文回退玩家首页。数据授权逐项拆分在 `player_data/`，未授权字段不会进入客户端。任务根页使用 `terminal_footer_navigation` 把“过去事件 / 个人信息 / 终端帮助”交给固定外壳底栏排版，按钮仍执行服务端注册操作。个人信息页的出生点卡片同时读取 `personal/pixel_tzz:hunter_spawn`、`personal_meta/pixel_tzz:hunter_spawn/name` 与 `personal_meta/pixel_tzz:hunter_spawn/value_name`：展示名称和“北门”等选项显示值只会随同已成功投影的本人原始值出现。

`main_branch/terminal_activated` 立即进入允许查看者的历史，并在同一历史页右侧原位展开详情；列表只接收 Boolean `item.detail_available`，客户端点击时只提交不透明记录键，目标页由服务端从冻结事件定义重新解析。事件配置中的 `detail_page` 不会投影给客户端。两个 `deadline_reached` 事件在所属任务结束后才公开。历史显示文案与主持人审计分离。

帮助轮播进入“安全操作”正文后，“运行一次终端自检”调用注册操作 `pixel_tzz:acceptance_ping`，再执行：

```mcfunction
/data get storage pixel_tzz:acceptance_3a last_player_action
```

可以查看服务端生成的有限函数宏。该函数没有玩法副作用，不会推进任务或改写模组权威状态。

## 3B 动态文本与可执行验收

基础包注册：

- `pixel_tzz:acceptance`：打字机、块状光标、新字临时色、渐变回色、淡入/停留/淡出与主字符音；
- `pixel_tzz:decode_notice`：原版乱码字符集逐字解码与淡入淡出；
- `pixel_tzz:acceptance/field_capture`：Chat、Title、Subtitle、ActionBar、独立声音、三种字段捕获、
  简化动态、HUD 布局与完成回调；其受众严格只来自显式 `call_targets`，字段中的
  `target` 参数也只允许显式调用参数或唯一调用目标，不回退到 invoker；
- `pixel_tzz:acceptance/policy_matrix`：参数授权、条件、语言、效果覆盖、依赖调度、有限重复、
  `refresh`、生命周期、静态最终文本、历史、资源和软上限；
- `pixel_tzz:acceptance/restart_finalize`：快照受众在 `restart=finalize` 下逐玩家、跨多次
  停服消费冻结最终文本的专用验收；不替代 `policy_matrix` 的 `live_strict` 安全语义；
- `pixel_tzz:acceptance/verified_player_speaker`：只向显式目标投影、且不会被身份变体覆盖的
  服务端验证玩家发言者验收；
- `pixel_tzz:acceptance/history_replay`：只向获准目标公开的静态回顾与安全重播验收；
- `pixel_tzz:acceptance/registered_entity`：合法实体的服务端验证显示名与无效实体匿名降级验收；
- `pixel_tzz:lifecycle/notice`、`player_notice`、`progress_notice`：由权威游戏事务触发的中文
  生命周期 Subtitle 模板；
- `pixel_tzz:acceptance_3b/is_hunter`：供节点条件引用的原版 Predicate。

### 生命周期 Hook

正式中文文案保存在拥有事件的 game、phase、task、task event、flow、readiness 或 role 定义中，
模组只在权威事务提交成功后补入事实参数并调用 cue。基础包用三条模板复用表现层：

| Cue | 数据包参数 | 服务端自动参数 | 用途 |
|---|---|---|---|
| `pixel_tzz:lifecycle/notice` | `message` | 不读取 | 游戏、阶段、任务与任务事件 |
| `pixel_tzz:lifecycle/player_notice` | `message` | `player_name` | 身份变化、身份初始化与单名流程成员完成 |
| `pixel_tzz:lifecycle/progress_notice` | `message` | `completed`、`total` | 流程发起、流程全员完成与玩家准备完成 |

`notice` 与 `progress_notice` 只投影给 `current_host`；`player_notice` 只投影给在线
`call_targets`，让完成流程的玩家本人得到返回游戏后的成功反馈，而不把逐玩家节点默认抄送给主持人。三者都按
`presentation_time` 播放，并在普通页面关闭、玩家回到游戏画面后才开始 Subtitle；同一时刻产生
的多条生命周期消息按通道队列依次展示。模板不写历史、不执行回调，也不会把 ID、UUID、
状态修订号或其他内部自动参数显示给玩家。

当前基础包已注册以下映射：

- Game：`start`、`pause`、`resume`、`end`；
- Phase：`enter`、`exit`；
- Task：`start`、`complete`、`interrupt`；Task Event：`trigger`；
- Flow：`start`、`player_complete`、`all_complete`；
- Role：`initialization`、`role_changed`；
- Readiness：Game 内嵌准备定义的 `complete`。

这些是基础包中可复用的 Hook 映射示例，不代表每个同类定义都必须注册。猎人初始化 Flow 与
猎人 Role 刻意不注册 V3B Subtitle Hook，继续沿用已经验收的 3A 反馈，避免向主持人或猎人
重复追加“流程已发起 / 玩家完成 / 全部完成 / 身份变化 / 身份初始化”播报。

`player_readiness` 是独立准备槽，但仍复用其冻结 Flow 的生命周期 Hook：创建成功后发出一次
`start`，每名成员完成时各发一次 `player_complete`，最后一名完成后先发 `all_complete`，再发
Game 内嵌 Readiness 的一次 `complete`。Reset、恢复、Reload、时钟 checkpoint 与页面重新投影
不会补播这些业务消息。

所有 `acceptance_3b` 函数仅用于验收。它们不注册正式任务、不改变身份、不推进时间线。
先由要观察演出的主持人玩家执行：

```mcfunction
/function pixel_tzz:acceptance_3b/setup
```

这会建立 `pixel_tzz_demo` 计分项，并初始化单目标与全体目标各自使用的参数路径：

```mcfunction
/data get storage pixel_tzz:acceptance_3b call
/data get storage pixel_tzz:acceptance_3b all_call
/data get storage pixel_tzz:acceptance_3b layout_call
/data get storage pixel_tzz:acceptance_3b layout_all_call
```

`field_capture.target` 是必填的单玩家参数。单目标命令会由唯一 `call_target` 自动补入；`to @a`
没有唯一目标，因此验收专用的 `all_call` / `layout_all_call` 显式写入 `Player972`，保证多目标命令
测试的是同一实例的同步受众，而不是在参数解析阶段被拒绝。全体播放必须分别使用 `all_call` 或
`layout_all_call`，不能把只供单目标使用的 `call` / `layout_call` 直接套到多目标命令上。

### 四个正向播放包装

| 验收函数 | 实际命令形态 | 用途 |
|---|---|---|
| `pixel_tzz:acceptance_3b/play/default` | `message play <id> to @s` | 默认参数的 self 包装 |
| `pixel_tzz:acceptance_3b/play/to_self` | `message play <id> to @s` | 保留原验收入口名的兼容别名 |
| `pixel_tzz:acceptance_3b/play/with_storage` | `message play <id> to @s with storage <storage> <path>` | Storage 参数的 self 包装 |
| `pixel_tzz:acceptance_3b/play/to_self_with_storage` | `message play <id> to @s with storage <storage> <path>` | 保留原验收入口名的兼容别名 |

逐项执行时应等待上一条结束：

```mcfunction
/function pixel_tzz:acceptance_3b/play/default
/function pixel_tzz:acceptance_3b/play/to_self
/function pixel_tzz:acceptance_3b/play/with_storage
/function pixel_tzz:acceptance_3b/play/to_self_with_storage
```

四个正向包装都显式把函数执行者写入 `call_targets`，不再借用 invoker 扩大受众。
如果要验收无目标的反向合同，可直接执行：

```mcfunction
/pixel_tzz_pro message play pixel_tzz:acceptance/field_capture
/pixel_tzz_pro message play pixel_tzz:acceptance/field_capture with storage pixel_tzz:acceptance_3b call
```

两条都必须明确拒绝缺失的必填 `target` 参数，不创建实例，也不向命令执行者或其他
玩家投影演出。

需要单独验收玩家发言者显示名与 Chat Heads 头像时，直接指定唯一目标；运行时会把该玩家冻结为
必填 `target`，无需额外 Storage 参数，也没有会把发言者替换成 narrator 的身份变体。随后单独
播放注册实体 cue，验证村民与无效实体不会伪装成玩家发言者：

```mcfunction
/pixel_tzz_pro message play pixel_tzz:acceptance/verified_player_speaker to PlayerC
/pixel_tzz_pro message play pixel_tzz:acceptance/registered_entity to PlayerC
```

第一条应让 PlayerC 获得服务端验证的 `player_parameter(target)` 发言者与正确玩家头像；第二条的
合法村民只显示服务端验证的实体名且没有玩家头像，无效实体匿名降级为普通系统消息。两条 cue 都
只向显式 `call_targets` 投影，执行命令的玩家不会因 `invoker` 身份自动收到演出。

当且仅当数据包函数确实需要跨越 cue 注册的游戏、阶段或任务限制时，可以把稳定后缀
`bypass context` 放在上述任一命令形态末尾。例如：

```mcfunction
pixel_tzz_pro message play pixel_tzz:acceptance/policy_matrix with storage pixel_tzz:acceptance_3b call bypass context
pixel_tzz_pro message play pixel_tzz:acceptance/field_capture to @s with storage pixel_tzz:acceptance_3b call bypass context
```

该后缀只接受保留服务端来源的控制台或数据包函数。普通玩家、非主持人 OP 和直接输入命令的
主持人都会被拒绝；`execute as` 只能提供 invoker，不能把玩家来源提升为绕过权限。绕过不会
跳过参数、受众、资源、并发或回调检查。排队期间会冻结这项选择；可持久化实例开始后也会把它
写入稳定快照。无需跨阶段调用时不要添加该后缀，以便错误的任务时序尽早失败。

预期：

1. 直接执行 `message play` 时回显 cue、受影响人数与实例 UUID；通过 `/function` 包装调用时，
   内部命令反馈不会透传，应以实际演出、活动实例和回调证据为准；
2. Chat 始终只有一条真实原版记录原位长成，不能逐帧追加聊天；
3. Title、Subtitle、ActionBar 在原版语义位置显示，独立提示音只播放一次；
4. 带 Storage 的两项显示“来自 Storage 的参数”；参数路径不是唯一对象时应明确拒绝；
5. `to` 选择器形成显式 `call_targets`；命令来源不会因 `invoker` 身份自动加入受众。若来源不在
   目标选择器中，最多只能看到直接命令的有限回显，不能看到 Chat、Title、Subtitle 或 ActionBar 演出；
6. `target_name` 与使用 `@s` 的动态字段都以显式目标为上下文，不能采用 invoker 的
   姓名、分数或其他玩家字段。

### Score、Data 与三种捕获模式

```mcfunction
/function pixel_tzz:acceptance_3b/capture/start
```

该函数会在服务端自动错开写值：初始分数 `10`，第 5 Tick 改为 `25`；初始 Storage 正文在
第 15 Tick 改为“最新 Storage 数据”。预期：

- `target_name` 使用 `on_display`，内容开始时立即锁定；
- Chat 中 `score_now` 使用 `on_first_field`，字段光标出现前锁定为 `25`；
- Subtitle 中 `storage_note` 使用 `per_field`，显示最新 Storage 正文；
- 三个字段在光标出现前都不能先闪 fallback，字段开始显示后也不能继续跳值；
- 主 Chat 的字符音启用，Subtitle 的并行字符轨静音，不能叠出双重打字音。

可查看自动写值状态与最后一次正常完成回调：

```mcfunction
/data get storage pixel_tzz:acceptance_3b state
/data get storage pixel_tzz:acceptance_3b last_callback
```

`last_callback` 应包含 `cue`、`instance`、`cycle`、`node`、`occurrence`、冻结参数和服务端
`game_time`；客户端动画结束本身不能伪造或提前执行这份回调。

### 暂停、继续、补全与取消

> 本节 mcfunction 已接入 `MessageCommands` 的 control 分支，但暂停、继续、补全、取消的画面、
> 声音与回调顺序仍须在 Minecraft 内逐项验收。

cue 级包装适合数据包直接调用，不需要抄实例 UUID：

```mcfunction
/function pixel_tzz:acceptance_3b/control/start_paused
/function pixel_tzz:acceptance_3b/control/resume
/function pixel_tzz:acceptance_3b/control/complete
```

预期暂停时打字、效果和字符音冻结在同一进度；继续后原位恢复；`complete` 立即补齐并保留最终
正文，随后执行 `all_complete` 回调。取消需单独重开一条：

```mcfunction
/data remove storage pixel_tzz:acceptance_3b last_callback
/function pixel_tzz:acceptance_3b/control/start_paused
/function pixel_tzz:acceptance_3b/control/cancel
/data get storage pixel_tzz:acceptance_3b last_callback
```

最后一条应找不到路径：`cancel` 只走 interrupt，不能伪装为正常完成回调。cue 级命令会控制该
cue 当前全部活动实例；只控制命令回显中的某一个 UUID 时使用：

```mcfunction
/pixel_tzz_pro message control instance <uuid> pause
/pixel_tzz_pro message control instance <uuid> resume
/pixel_tzz_pro message control instance <uuid> complete
/pixel_tzz_pro message control instance <uuid> cancel
```

对应的稳定 cue 级语法为：

```mcfunction
/pixel_tzz_pro message control cue pixel_tzz:acceptance/field_capture pause
/pixel_tzz_pro message control cue pixel_tzz:acceptance/field_capture resume
/pixel_tzz_pro message control cue pixel_tzz:acceptance/field_capture complete
/pixel_tzz_pro message control cue pixel_tzz:acceptance/field_capture cancel
```

注册了 `policies.control_groups` 或并发 `groups` 时，也可按分组控制；需要找出包含某名当前在线
玩家的活动实例时，可按单玩家控制：

```mcfunction
/pixel_tzz_pro message control group acceptance_capture pause
/pixel_tzz_pro message control group acceptance_capture resume
/pixel_tzz_pro message control target @s complete
```

目标入口控制匹配到的完整实例，不是仅在一个客户端改变动画。离线玩家应改用实例、cue 或 group
入口。

### 并发、减少动态与布局压力

`ignore_while_active`：

```mcfunction
/function pixel_tzz:acceptance_3b/conflict/ignore_while_active
```

同一 Tick 的第一次调用应创建实例，第二次应命中“相同动态消息正在播放”的忽略路径；数据包
函数不会把内部命令回显透传到执行者聊天，因此以最终只有一条 Chat、一次主字符音、一次完成回调
以及实例列表为准。需要观察两次彩色命令回显时，应直接逐条执行对应 `message play` 命令。

`refresh` 只在 `pixel_tzz:running` 且当前任务为 `pixel_tzz:acceptance/main_branch` 时验收：

```mcfunction
/function pixel_tzz:acceptance_3b/conflict/refresh
```

第二次调用应走“已原位刷新”，沿用稳定实例身份并增加 cycle，不能追加第二条 Chat。包装函数不
透传内部命令回显，应以原位变化和实例列表核对；需要观察彩色回显时改用两条直接命令。上下文不符
时明确拒绝属于预期，不能绕过策略强播。

在客户端动态文字设置中打开“减少动态”后：

- 运行 `pixel_tzz:acceptance_3b/accessibility/simplified`，`field_capture` 应采用 `simplified`，
  但最终正文仍完整；
- 在上述合法任务上下文运行 `pixel_tzz:acceptance_3b/accessibility/static_final`，应采用
  `static_final`，不再播放完整位移/脉冲动画。

最后执行布局压力：

```mcfunction
/function pixel_tzz:acceptance_3b/layout/pressure
```

在 2K 基准、窄窗口、高 GUI 缩放各观察一次。Title 的 `scale`、Subtitle 和 ActionBar 的
`ellipsis` 必须约束在注册的 `max_width` / `max_lines` 内，不能越过屏幕或让保留正文消失；
Chat 仍只消费富文本帧，不套用 HUD 位移和缩放。

验收结束后清理临时实例、标签、分数与参数 Storage（保留共享 objective 和重复初始化标记）：

```mcfunction
/function pixel_tzz:acceptance_3b/reset
```

同目录的 `pixel-tzz-3b-pressure-datapack` 只用于自动压力验收。它完整覆盖同 ID Effect/Cue，
并以 64 个动态字段验证合法上限；策略矩阵覆盖还证明高优先级资源不会继承低优先级的参数、
历史、静态降级或资源声明。不能把覆盖包文案当成正式玩法内容。

## 易用验收函数

任务 `on_start` 宏回调会把当前权威上下文写入：

```text
storage pixel_tzz:acceptance_2d session.current
```

因此手工验收不需要抄写随机 `task_instance_id`。

### 推荐成功路线

主持人批准开局并看到赛前环节后依次执行：

```mcfunction
/data get storage pixel_tzz:acceptance_2d session.current
/function pixel_tzz:acceptance_2d/warmup/complete
```

进入分支任务后：

```mcfunction
/data get storage pixel_tzz:acceptance_2d session.current.deployments
/function pixel_tzz:acceptance_2d/main/record_terminal
/function pixel_tzz:acceptance_2d/main/succeed
```

预期：

1. `session.current.deployments` 为每名猎人各有一条记录，包含 `player_uuid`、`player_name`，以及 `player_field.spawn_point`；
2. 事件与统计进入当前任务回顾；
3. 成功结果冻结后显示 60 秒间隔；
4. 随后进入 10 秒 `timeout_only` 任务，不能提前提交；
5. 到时回调自动提交结果，60 秒最终间隔后进入 `pixel_tzz:ended`。

任意时刻可查看最近冻结结果或间隔回调上下文：

```mcfunction
/data get storage pixel_tzz:acceptance_2d session.result_history[-1]
```

### 另外两条分支

需要新一局或重置本局进程后重新走到 `main_branch`：

```mcfunction
/function pixel_tzz:acceptance_2d/main/fail
```

会选择提前失败路线并显示 60 秒间隔。什么都不执行并等待 5 分钟，则由 `on_timeout` 记录到时事件、提交 `timeout` 结果并显示 60 秒间隔。

所有入口都仍通过模组的 `pixel_tzz task submit_result`、`record_event` 和 `set_statistic` 权威命令；包装函数只从回调缓存读取当前任务 UUID，不绕过实例、状态、结果、事件或统计校验。

### 结果回调失败与重试

进入 `main_branch` 后、提交任一结果前执行：

```mcfunction
/function pixel_tzz:acceptance_2d/fault/arm
/function pixel_tzz:acceptance_2d/main/succeed
```

结果会先被权威冻结，随后验收专用 `on_apply` 门返回失败；时间线必须保持技术阻塞，不能启动间隔或更换结果。然后执行：

```mcfunction
/function pixel_tzz:acceptance_2d/fault/clear
```

再由主持人在时间线页面对失败回调执行二次确认重试。预期只重试失败步骤，成功后继续原先冻结的 `success` 路线。该开关只存在于本验收数据包，每次进入新的 `main_branch` 都自动恢复为不注入故障。

## 内容规模

基础数据包当前共包含 `181` 个文件和 `97` 项 Pixel TZZ 定义，其中有：

- `24` 个页面；
- `2` 个字段；
- `3` 条强制流程；
- `3` 个任务；
- `6` 个阶段；
- `3` 条玩家终端路由；
- `17` 项玩家数据授权；
- `12` 项玩家注册操作；
- `2` 个文字效果；
- `9` 条完整文字演出。

回调和包装函数共 `81` 个：既有 2C 无副作用函数 `6` 个、2D 宏回调与验收入口 `20` 个、
3A 注册函数验收入口 `1` 个，以及 3B 播放、控制、动态值、无障碍、布局、外部 HUD、重启和回调验收函数 `54` 个。
