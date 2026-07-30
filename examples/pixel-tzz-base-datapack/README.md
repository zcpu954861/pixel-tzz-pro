# Pixel TZZ Pro 2A–3A 示例与验收夹具

此数据包只用于模组开发验收，同时保留：

- 2A/2B 的定义、主题、布局和组件预览；
- 2C 的极短服务端权威初始化流程；
- 2D 的逐玩家准备、最小任务时间线、独占选择、事件、统计和回调夹具。
- 3A 的普通玩家终端、身份专属任务页、个人数据裁剪、玩家历史与注册函数夹具。

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

基础数据包当前共包含 `115` 个文件和 `86` 项 Pixel TZZ 定义，其中有：

- `24` 个页面；
- `2` 个字段；
- `2` 条强制流程；
- `3` 个任务；
- `6` 个阶段；
- `3` 条玩家终端路由；
- `17` 项玩家数据授权；
- `12` 项玩家注册操作。

回调和包装函数共 `27` 个：既有 2C 无副作用函数 `6` 个、2D 宏回调与验收入口 `20` 个，以及 3A 注册函数验收入口 `1` 个。
