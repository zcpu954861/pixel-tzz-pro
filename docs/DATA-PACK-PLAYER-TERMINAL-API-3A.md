# Pixel TZZ Pro 数据包玩家终端 API（3A）

状态：**接口、示例与 Minecraft 多客户端实机验收均已完成**

- 适用当前模组版本：`0.1.0`
- 目标 Minecraft：`26.2`
- 游戏 API：`api_version: 3`
- 网络协议：v11
- 世界状态：Schema v4

本文是 V3A 普通玩家终端的当前数据包契约。页面基础格式继续遵循
[`DATA-PACK-UI-2B.md`](DATA-PACK-UI-2B.md)，任务与事件事实继续遵循
[`DATA-PACK-TASK-API-2D.md`](DATA-PACK-TASK-API-2D.md)。

## 1. 设计边界

模组提供：

- ESC 入口、普通终端外壳、页面栈、返回、关闭和统一右上角同步徽标；
- 服务端权威路由；
- 按查看者裁剪的任务、个人数据和历史绑定；
- 注册操作的重新绑定、权限、次数、冷却、确认、防重放和持久账本；
- 数据包函数的有限服务端上下文与安全执行器；
- `/reload`、掉线、重连、页面失效和路由变化恢复。

数据包独立注册：

- 默认玩家页与上下文路由；
- 每一张玩家页的内容、布局、动画和按钮；
- 每一项允许下发的个人或任务数据；
- 每一种可回顾事件的内容、受众和公开时机；
- 每一个可调用操作及其函数、次数、冷却、确认和反馈。

模组不固定生成任务卡、个人资料卡或历史详情页。没有注册的内容就不显示。客户端只提交有类型意图，不能提交任意页面 ID、函数 ID、任务结果或“操作已经成功”。

## 2. 资源路径与版本

新增三类定义：

```text
data/<namespace>/pixel_tzz_pro/player_routes/<path>.json
data/<namespace>/pixel_tzz_pro/player_data/<path>.json
data/<namespace>/pixel_tzz_pro/player_actions/<path>.json
```

扩展：

```text
data/<namespace>/pixel_tzz_pro/games/<path>.json
data/<namespace>/pixel_tzz_pro/tasks/<path>.json
data/<namespace>/pixel_tzz_pro/pages/<path>.json
```

所有文件保持顶层 `format_version: 1`。使用玩家终端的游戏必须声明
`api_version: 3`。路径决定稳定定义 ID；高优先级数据包完整覆盖同路径定义，不进行深合并。

同一游戏的硬上限：

| 类型 | 上限 |
|---|---:|
| `player_routes` | 128 |
| `player_data` | 256 |
| `player_actions` | 256 |
| 单次投影历史条目 | 最近 64 条 |
| 页面栈深度 | 32 |
| 普通终端空闲会话 | 10 分钟后过期 |

## 3. Game 定义

```json
{
  "format_version": 1,
  "api_version": 3,
  "content_version": 3,
  "name": {"text": "全员逃走中"},
  "initial_phase": "pixel_tzz:setup",
  "default_role": "pixel_tzz:runner",
  "default_life_state": "pixel_tzz:alive",
  "player_terminal": {
    "default_page": "pixel_tzz:player/home",
    "history_enabled": true,
    "history_source": "tasks"
  }
}
```

| 键 | 必填 | 默认 | 说明 |
|---|---|---|---|
| `default_page` | 是 | 无 | 没有更具体路由时打开的同游戏页面 |
| `history_enabled` | 否 | `false` | 玩家历史总开关 |
| `history_source` | 否 | `events` | `events` 逐事件展示；`tasks` 按已经结束的任务汇总 |

`history_enabled: false` 时，服务端不会投影玩家历史条目。页面可以根据
`history.enabled` 显示数据包自己的空状态，但不能从其他绑定还原后台事件。
`history_source` 只改变当前查看者合法内容的组织方式，不扩大受众、公开时机或字段权限。

## 4. 普通终端入口

服务端入口优先级固定为：

1. 玩家存在活动强制流程：返回不可关闭的强制页；
2. 玩家是当前主持人：打开主持人控制台；
3. 其他玩家：解析数据包玩家路由并打开普通终端；
4. 定义缺失或无合法页面：进入内置安全失败闭环。

非主持人 OP 仍进入普通玩家终端，只由模组外壳追加一个低优先级主持人操作：

- 当前世界没有主持人时显示“认领主持人”；
- 已有其他主持人时显示“接管主持人”。

数据包不能伪造、替换或隐藏该系统按钮。两种操作都重新检查 OP 权限并经过详细二次确认。服务端不会向非主持人 OP 下发完整 `ConsoleSnapshot`，也不会向其提供主持人专用页面预览目录或页面文档；OP 身份不能用于取得主持人任务线、完整玩家名单、回调审计、高风险操作或未授权页面源码。普通终端可关闭且不会因为任务、身份、历史或绑定变化自动弹出。

## 5. 玩家路由

路径：`player_routes/<path>.json`

```json
{
  "format_version": 1,
  "game": "pixel_tzz:main",
  "page": "pixel_tzz:player/task_runner",
  "priority": 900,
  "audience": {
    "roles": ["pixel_tzz:runner"],
    "exclude_host": true,
    "online_only": true
  },
  "phases": ["pixel_tzz:running"],
  "tasks": ["pixel_tzz:task/example"],
  "task_states": ["running", "paused"],
  "predicate": "pixel_tzz:terminal/example"
}
```

| 键 | 必填 | 默认 | 说明 |
|---|---|---|---|
| `game` | 是 | 无 | 所属游戏 |
| `page` | 是 | 无 | 同游戏目标页面 |
| `priority` | 是 | 无 | `-10000..10000`，数值越大越优先 |
| `audience` | 是 | 无 | 共享受众条件 |
| `phases` | 否 | `[]` | 允许阶段；空表示不限 |
| `tasks` | 否 | `[]` | 当前任务；空表示不限 |
| `task_states` | 否 | `[]` | 当前任务状态；空表示不限 |
| `predicate` | 否 | 无 | 服务端 predicate |

`task_states` 可用值：

```text
starting, running, paused, settling, intermission,
settled, completed, interrupted, blocked
```

同一游戏不允许两个路由使用相同 `priority`。服务端按优先级解析，客户端不参与猜测。路由失效或 `/reload` 改变有效路由时，框架生成新的页面实例并正常切页；普通绑定变化只发送增量，不重建页面。

示例夹具分别注册：

- `pixel_tzz:runner_task` → `pixel_tzz:player/task_runner`；
- `pixel_tzz:hunter_task` → `pixel_tzz:player/task_hunter`；
- `pixel_tzz:ended_history` → `pixel_tzz:player/history`。

## 6. 玩家可见数据授权

路径：`player_data/<path>.json`

```json
{
  "format_version": 1,
  "game": "pixel_tzz:main",
  "source": {
    "type": "exclusive_choice",
    "id": "pixel_tzz:hunter_spawn"
  },
  "surfaces": ["terminal"],
  "audience": {
    "roles": ["pixel_tzz:hunter"],
    "exclude_host": true,
    "online_only": true
  },
  "phases": ["pixel_tzz:running"],
  "tasks": [],
  "task_states": [],
  "predicate": "pixel_tzz:may_view_spawn",
  "name": {"text": "猎人出生点"},
  "masked_fallback": {"text": "尚未选择"}
}
```

每个文件只授权一个稳定值。授权通过后，页面使用：

```json
{"bind": "personal/<完整 player_data 定义 ID>"}
```

例如：

```json
{"bind": "personal/pixel_tzz:hunter_spawn"}
```

当同一份 `player_data` 显式声明了 `name` 时，页面还可以读取该数据项的展示名称：

```json
{"bind": "personal_meta/<完整 player_data 定义 ID>/name"}
```

例如：

```json
{"bind": "personal_meta/pixel_tzz:hunter_spawn/name"}
```

若授权源是 `exclusive_choice`，而且本人持久值与 `LOCKED` 独占预留一致，还可以读取该选项由
字段 `options[].name` 注册的玩家显示名：

```json
{
  "bind": "personal_meta/pixel_tzz:hunter_spawn/value_name",
  "fallback": {
    "text": "出生点已锁定"
  }
}
```

例如机器值 `north_gate` 可以显示为 `北门`；页面不需要也不应硬编码每个数据包选项。若显示名因
预算或定义变化暂时缺席，fallback 必须使用固定安全文案，不能回落到原始值并把机器键显示给玩家。

`personal_meta` 不是第二套数据源，也不能枚举其他授权。它只允许 `name` 与 `value_name` 两个
有界纯文本属性，每项最多两个属性、每个最长 256 个字符；完整定义 ID 可以包含 `/`。其中
`value_name` 只有在同一份 `exclusive_choice` 原始值已经获准投影时才存在，不会绕过字段版本、
锁定预留、受众或终端表现层检查。页面编译时仍会确认目标定义存在、属于同一 Game、包含
`terminal` 表现层。

`surfaces` 当前枚举为：

```text
terminal, hud, dynamic_message
```

V3A 只实现终端投影；声明其他表现层不等于 V3B/V3C 已经实现。必须至少声明一个表现层，页面只能绑定包含 `terminal` 的授权。

### 6.1 数据源

| `source.type` | `id` | `key` | 投影类型 |
|---|---|---|---|
| `role` | 禁止 | 禁止 | Identifier |
| `team` | 禁止 | 禁止 | Identifier，可缺失 |
| `life_state` | 禁止 | 禁止 | Identifier |
| `initialized` | 禁止 | 禁止 | Boolean |
| `ready` | 禁止 | 禁止 | Boolean |
| `field` | 玩家字段，必填 | 禁止 | 取决于字段类型 |
| `exclusive_choice` | 玩家独占字段，必填 | 禁止 | String；只投影本人已锁定值 |
| `task_id` | 可选任务 | 禁止 | Identifier |
| `task_name` | 可选任务 | 禁止 | String |
| `task_description` | 可选任务 | 禁止 | String |
| `task_status` | 可选任务 | 禁止 | String |
| `task_elapsed_ticks` | 可选任务 | 禁止 | Integer |
| `task_remaining_ticks` | 可选任务 | 禁止 | Integer，可缺失 |
| `game_elapsed_ticks` | 禁止 | 禁止 | Integer |
| `task_progress` | 任务，必填 | 统计 key，必填 | Integer |
| `task_result` | 任务，必填 | 可选结果 key | String，可缺失 |
| `task_statistic` | 任务，必填 | 统计 key，必填 | 取决于统计类型 |

`task_progress` 只能读取同任务的 `integer` 或 `duration_ticks` 统计。

可见任务源同时投影到 `task.*` 的标准槽位，页面可以读取：

```text
task.exists
task.id
task.name
task.description
task.status
task.paused
task.elapsed_ticks
task.remaining_ticks
task.duration_ticks
task.progress
task.result
task.statistics
```

未授权的槽位不下发。不要因为编译器允许某个绑定名，就假定服务端一定提供值。

`task.exists` 也受任务数据授权保护，不是无条件的当前任务探针。服务端只有在至少一条对当前查看者有效的任务来源授权指向当前任务时，才把它设为 `true` 并开放相应 `task.*` 槽位；没有授权与确实没有活动任务对玩家页面表现相同，不能据此探测隐藏任务。

### 6.2 `format` 与缺值

- `format` 只允许 String 数据；
- 必须恰好包含一次 `{value}`；
- 不允许其他 `{` 或 `}`；
- `masked_fallback` 也只允许 String 数据；
- fallback 只处理“定义已授权但当前合法缺值”，不能替代受众、阶段、任务或 predicate 授权；
- 非字符串缺值保持缺失，页面使用 `exists` 或文本 fallback 处理。

未经授权的数据根本不进入客户端，不能靠灰色文字或隐藏节点代替服务端裁剪。

任务正文、任务名称、任务计时、全局计时、剩余时间和统计可以拆成多份 `player_data` 定义，并为
逃走者与猎人分别声明 `audience.roles`、阶段、任务状态和 predicate。框架只合并当前查看者实际
通过的授权：允许猎人看到任务但隐藏全局计时、允许逃走者看到剩余时间但隐藏猎人统计等组合均不
需要修改模组。若两类玩家策略不同，应使用不同稳定定义 ID，而不是把真实值统一下发后在页面隐藏。

### 6.3 数据名称与投影预算

服务端先投影所有实际 `personal/<ID>` 值，再用剩余预算投影对应的
`personal_meta/<ID>/name` 与可用的 `personal_meta/<ID>/value_name`。只有同一个
`personal/<ID>` 已成功进入本次文档时，这些展示文本才有资格进入；受众不匹配、predicate
不通过、当前合法缺值、独占选择没有有效显示名或值本身被预算淘汰时，均不会留下孤立元数据。

因此页面必须把 `personal_meta` 当作值的附属展示信息，不能把“名称存在”当成权限、身份或数据存在
探针。原有 `personal/<ID>` 标量类型保持不变，声明 `name` 或选项显示名不会把它改造成对象。

## 7. 页面绑定

普通终端页面仍是 2B 数据驱动页面。V3A 常用绑定：

```text
viewer.uuid
viewer.name
viewer.online
viewer.admin
viewer.host
viewer.initialized
viewer.ready

session.game
session.game_name
session.phase
session.phase_name
session.revision
session.generation

task.*
history.*
personal/<player_data ID>
personal_meta/<完整 player_data ID>/name
personal_meta/<完整 player_data ID>/value_name
```

同步状态由普通终端外壳统一绘制在右上角。页面正文不要重复写“已同步服务端”“同步中”等文本。

页面按钮调用注册操作：

```json
{
  "type": "button",
  "id": "open_help",
  "label": {"text": "终端帮助"},
  "action": {
    "type": "registered",
    "action": "pixel_tzz:open_help"
  }
}
```

注册按钮必须拥有稳定、页面内唯一的 `id`。服务端执行时会从当前权威页面按
`page ID + page instance ID + node ID + action ID` 重新绑定；客户端仅伪造 action ID 无效。

本地 `back`、`close` 等仍使用 2B 的 `type: "local"`。打开数据包页面应使用注册
`open_page` 操作，以便服务端重新校验受众和页面栈。每个打开的普通子页都会保存
“来源页面 + 来源 page instance + node ID + action ID”的服务端授权锚点；此后每次玩家操作和后台
刷新都会按当前身份、阶段、任务、任务状态与 predicate 重新校验。锚点失效时自动丢弃该页及其后代，
返回最近仍合法的父页。已打开页面的保留校验不重复消费次数、冷却或确认令牌。

### 7.1 根页底栏导航

普通终端根页可以把 1–5 个常用目的地交给终端外壳统一排入底栏。页面根节点的直属子节点声明：

```json
{
  "type": "grid",
  "id": "terminal_footer_navigation",
  "layout": {
    "width": {"mode": "fill"},
    "height": {"mode": "fixed", "value": 0}
  },
  "children": [
    {
      "type": "button",
      "id": "open_history",
      "label": {"text": "过去事件"},
      "action": {
        "type": "registered",
        "action": "pixel_tzz:open_history"
      }
    }
  ]
}
```

合同如下：

- 一张页面最多声明一个 `terminal_footer_navigation`，且必须是页面根节点的直属容器；
- 只允许用于普通终端根页；子页继续使用外壳唯一的“返回”；
- 容器必须直接包含 1–5 个带非空稳定 `id` 的 `registered` 按钮，不能放文本、嵌套容器或本地按钮；
- 容器本身只负责声明顺序，实际尺寸、间距、命中区域与小屏缩放由终端外壳统一计算；推荐高度为 `0`，避免正文重复预留空间；
- 每个按钮仍按原 node ID、action ID、可见条件、禁用状态、Tooltip、事件动画和声音工作；
- 点击后仍经过 `page instance + node ID + action ID` 的服务端权威重新绑定，移入底栏不会降低安全级别；
- 超过五项是定义错误，不会静默截断；低频入口应进入二级页。

### 7.2 安全乱码表现

文本节点样式可以声明 Boolean `obfuscated`：

```json
{
  "type": "text",
  "style": "terminal_masked",
  "text": {"text": "00:00"}
}
```

```json
{
  "styles": {
    "terminal_masked": {
      "normal": {
        "text_color": "#657286",
        "obfuscated": true
      }
    }
  }
}
```

该属性只改变已经投影到页面的文本节点绘制方式，不会让客户端取得真实值，也不会把未授权字段变成
“存在”。需要遮蔽时应使用固定安全占位文本；不得先发送真实任务名、计时或个人值再依赖乱码隐藏。
`obfuscated` 必须是 JSON Boolean，字符串 `"true"` 会编译失败。

## 8. 玩家历史

玩家历史必须同时满足：

1. Game `player_terminal.history_enabled: true`；
2. 事件定义显式存在 `player_history`；
3. 已达到该记录的 `release` 时机；
4. 当前查看者符合独立的 `audience`；
5. 记录确实已经发生。

Game 的 `player_terminal.history_source` 决定合法记录如何组织：

| 值 | 行为 |
|---|---|
| `events` | 默认兼容模式；逐条列出已经公开的事件，包含当前任务中已达到 `immediate` 的记录 |
| `tasks` | 只列出 `timeline.taskHistory` 中已经结束的任务；按任务实例汇总当前查看者实际可见且声明 `show_task: true` 的事件 |

`tasks` 模式不会从后台任务、当前任务、未来候选或其他玩家的历史生成摘要。一个任务没有任何对
当前查看者合法的 `show_task` 事件时，该任务完全不出现；`event_count` 也只统计该查看者实际
可见的记录，因此不同身份可以看到不同数量。摘要标题使用已授权的任务中文名，正文、统计、
个人结果与详情仍只来自该任务中已经通过同一事件 `player_history` 策略公开的字段。

扩展任务 `events[]`：

```json
{
  "id": "terminal_activated",
  "name": {"text": "验收终端已触发"},
  "policy": "repeatable",
  "max_records": 8,
  "allowed_states": ["running"],
  "player_history": {
    "release": "immediate",
    "audience": {
      "role_tags": ["pixel_tzz:active_participant"],
      "exclude_host": true,
      "online_only": false
    },
    "title": {"text": "验收终端已触发"},
    "summary": {"text": "场内终端完成了一次有效触发。"},
    "icon": "minecraft:lever",
    "color": "#64D8E8",
    "category": "task_event",
    "show_game_time": true,
    "show_task": true,
    "show_actor": true,
    "show_targets": false,
    "parameters": [],
    "statistics": ["activated_terminals"],
    "show_personal_result": false,
    "details": {"text": "稳定的玩家回顾正文。"},
    "detail_page": "pixel_tzz:player/history_detail"
  }
}
```

`release`：

| 值 | 公开时机 |
|---|---|
| `immediate` | 记录发生后 |
| `task_end` | 所属任务 settled 或 interrupted 后 |
| `game_end` | 时间线 completed 或 interrupted 后 |

省略 `player_history` 表示默认不公开。`show_targets: true` 不会让框架猜测当前记录没有保存的目标；无权威目标时只投影空数组。

`parameters` 是为后续接口版本预留的键。V3A 没有持久化任意事件参数，也不会把它们投影给玩家，因此该键只能省略或写成空数组 `[]`；任何非空值都会以 `INVALID_CONSTRAINT` 拒绝整个候选 generation。V3A 已支持的事件附加数据仅包括显式列入 `statistics` 的任务统计及受策略控制的结果字段。

页面读取：

```text
history.enabled
history.empty
history.count
history.source
history.items
```

`history.source` 为 `events` 或 `tasks`，与当前冻结 Game 配置一致。

`history.items` 中每个对象只包含策略允许的字段：

```text
item.id
item.source
item.source_name
item.title
item.summary
item.event_count
item.category
item.color
item.icon
item.game_time
item.task_time
item.game_time_ticks
item.task_time_ticks
item.game_time_text
item.task_time_text
item.task
item.task_name
item.actor.uuid
item.actor.name
item.targets
item.statistics
item.result
item.details
item.detail_available
```

`item.source` 为 `event` 或 `task`，`source_name` 是对应的玩家可读中文名称。`event_count` 只在
任务汇总项存在。`game_time` 与 `task_time` 继续保留原始 Tick，兼容旧页面；新增的
`*_ticks` 是语义明确的同值字段，玩家页面应优先显示 `game_time_text` / `task_time_text`：
前者固定为 `开局后 MM:SS`，后者固定为 `任务开始后 MM:SS`；一小时及以上的时间部分改用
`H:MM:SS`。详情的 `metadata` 对应以“发生时间”和“任务内时间”标识，不要求玩家理解内部 Tick
或含义不明确的“游戏时刻”。

`item.detail_available` 是 Boolean，只表示当前记录是否拥有一个经服务端验证后可以打开的详情页。
事件定义中的 `player_history.detail_page` 只属于服务端配置，绝不投影给客户端。其他可选字段缺失时
必须用 `exists` 处理。历史按权威游戏 Tick 排序并只保留最近 64 条投影；记录键是稳定的不透明句柄，
页面不能解析或依赖其中的任务实例、事件 ID 或顺序信息。共享字节预算不足时优先保留较新的记录，
最终 `history.items` 仍维持时间正序；当前页的 selected/defaulted/recovered 条目会与对应
`detail` 原子预留，不会出现左侧默认项仍在、右侧却因列表先耗尽预算而变成不可用。活动时间线使用
开局时冻结的任务与公开策略；
后续 `/reload` 不会改写已经开始这一局的可见历史语义。

### 8.1 类型化历史详情导航

只有直接或间接位于 `items: {"bind": "history.items"}` 的 Repeat 模板内，按钮才可以声明：

```json
{
  "type": "button",
  "id": "history_open_detail",
  "label": {"text": "查看事件详情"},
  "visible_when": {
    "eq": [
      {"bind": "item.detail_available"},
      {"literal": true}
    ]
  },
  "action": {"type": "history_detail"}
}
```

`history_detail` 不接受 `page`、`action`、函数或任意参数。客户端点击时只提交该权威投影条目的有界
`item.id` 不透明记录键，不提交目标页面 ID 或 item JSON。服务端会按 session、请求序列、页面实例及
node ID 重新绑定按钮，确认它仍属于本次精确投影进 `history.items` 的 Repeat 条目，再以当前查看者、
当前公开条件和活动局冻结定义重新解析记录。目标页只采用事件声明的同 Game `detail_page`；
`detail_page` 始终是事件配置，列表与详情投影都不会把它下发给客户端。

Page Frame 保存的是服务端解析后的记录引用。返回使用
`{"type":"local","name":"back"}`，恢复原列表；记录被清理、受众或公开条件变化、冻结定义不可恢复时一律关闭旧正文。

详情页只读取服务端裁剪的 `detail.*`：

```text
detail.exists
detail.status
detail.id
detail.source
detail.source_name
detail.title
detail.summary
detail.event_count
detail.category
detail.color
detail.icon
detail.game_time
detail.task_time
detail.game_time_ticks
detail.task_time_ticks
detail.game_time_text
detail.task_time_text
detail.task
detail.task_name
detail.actor.uuid
detail.actor.name
detail.targets
detail.statistics
detail.result
detail.details
detail.metadata
detail.detail_available
```

`detail.status` 的当前值为：

| 值 | 说明 |
|---|---|
| `selected` | 正在显示玩家明确选择的合法记录 |
| `defaulted` | 首次打开主从历史页，自动选择本次实际投影中最新的同页合法记录 |
| `recovered` | 同页旧选择失效，但页面导航授权仍有效，已回退到另一条最新合法记录 |
| `empty` | 当前页没有可显示的合法详情；应使用中性空状态 |
| `disabled` | Game 关闭了玩家历史 |
| `unavailable` | 显式独立详情引用失效且不允许同页回退；可以显示明确的不可用提示 |

初次进入不得把 `empty` 当成红色错误。默认与回退都只在当前查看者已经通过受众、公开时机、最近
64 条窗口和共享字节预算的精确 `history.items` 集合内选择；不会从后台候选补一条。

`detail.metadata` 是适合 `Repeat` 的安全数组，每项只含：

```text
item.id
item.label
item.value
```

框架只从同一详情中已经获准投影的时间、任务、个人结果和白名单统计生成这些行，不重新读取隐藏
字段。页面应给 `item.value` 足够宽度并开启自动换行，不要把它重新排成固定四格卡片。

`detail.detail_available` 同样是 Boolean，不包含页面 ID。其余详情字段与同一条 `item.*` 使用相同
公开策略。纯独立详情 Frame 的记录被清理、受众失效或退出最近窗口时继续 fail-closed 并返回父页；
带有效页面导航授权的同页主从历史 Frame 保留页面，投影改为 `recovered`，从而不会因为一条旧记录
被撤回而把玩家踢出整张历史页。

## 9. 玩家注册操作

路径：`player_actions/<path>.json`

### 9.1 打开页面

```json
{
  "format_version": 1,
  "game": "pixel_tzz:main",
  "operation": {
    "type": "open_page",
    "page": "pixel_tzz:player/help"
  },
  "audience": {
    "exclude_host": true,
    "online_only": true
  },
  "audit": "none"
}
```

目标必须是同游戏页面。页面栈由框架管理，深度上限 32。返回、刷新和路由替换都会生成新的 page instance，旧实例请求不能复用。

### 9.2 运行函数

```json
{
  "format_version": 1,
  "game": "pixel_tzz:main",
  "operation": {
    "type": "run_function",
    "function": "pixel_tzz:terminal/example"
  },
  "audience": {
    "roles": ["pixel_tzz:runner"],
    "exclude_host": true,
    "online_only": true
  },
  "phases": ["pixel_tzz:running"],
  "tasks": ["pixel_tzz:task/example"],
  "task_states": ["running"],
  "predicate": "pixel_tzz:terminal/can_use",
  "usage": {
    "scope": "player",
    "max": 1
  },
  "cooldown": {
    "ticks": 100,
    "scope": "player"
  },
  "confirmation": {
    "title": {"text": "确认启动装置"},
    "consequences": [
      {"text": "将以你的身份在当前位置执行已注册函数。"}
    ]
  },
  "execution_context": "player",
  "feedback": {
    "success": {"text": "装置已启动"},
    "denied": {"text": "当前不能启动装置"},
    "failed": {"text": "装置执行失败"}
  },
  "audit": "all"
}
```

| 键 | 值 |
|---|---|
| `usage.scope` | `unlimited`、`player`、`game` |
| `usage.max` | `unlimited` 必须为 `0`；其他为 `1..1000000` |
| `cooldown.scope` | `player`、`game` |
| `cooldown.ticks` | `0..12096000` |
| `execution_context` | `player`、`server` |
| `audit` | `none`、`failures`、`all` |

省略 `usage` 表示无限次；省略 `cooldown` 表示玩家范围 `0 Tick`；省略
`execution_context` 表示 `player`；省略 `audit` 表示 `all`。

`open_page` 只能使用 `player` 执行上下文。

### 9.3 函数宏上下文

函数通过 Minecraft 函数宏收到服务端生成的有界字段：

```text
game_id
game_instance_id
action_id
request_id
request_sequence
player_uuid
player_name
phase_id
page_id
node_id
terminal_session_id
task_id                    # 有当前任务时
task_instance_id           # 与 task_id 成对出现
game_elapsed_ticks
task_elapsed_ticks
definition_generation
state_revision
world_tick
execution_context
```

`execution_context: player` 以点击玩家为 `@s` 并在玩家位置执行；
`execution_context: server` 以服务器身份执行，但宏中仍保留已授权点击玩家的身份。

`request_id` 不来自客户端。客户端只发送当前终端会话内的递增
`request_sequence`；服务端接受该序列后，使用带固定域分隔的
`terminal_session_id + request_sequence` 确定性派生持久 UUID。因此，同一已接受请求在
重试、确认与崩溃恢复路径中始终得到相同 `request_id`，不同序列得到不同 ID，客户端也
无法自选或复用另一次操作的持久请求号。

`world_tick` 使用主世界持久化的 `gameTime`，不是进程启动后从零计算的 server tick。次数和冷却因此跨正常停服、存档加载及服务器重启继续生效。

示例：

```mcfunction
$data modify storage pixel_tzz:terminal last set value {player_uuid:"$(player_uuid)",request_id:"$(request_id)",page_id:"$(page_id)",node_id:"$(node_id)"}
return 1
```

宏中没有任意客户端参数、完整绑定文档、其他个人字段或模组状态句柄。函数可以修改普通 Minecraft 世界，但不能直接写模组持久化文件，也不能绕过模组 API 宣称任务、身份、捕获、复活或准备已经成功。

### 9.4 两阶段账本

函数操作先持久化 `PREPARED`，再调用函数，最后写入 `SUCCEEDED` 或 `FAILED`。

- 同一服务端派生 request UUID 重放不会再次执行；
- 次数与冷却在预提交时原子消耗；
- 正常失败会留下稳定结果；
- 服务器在 `PREPARED` 后、最终结果前崩溃时，重启后标记 outcome unknown，绝不猜测函数未执行并自动重跑；
- 确认操作使用服务端挑战，确认时重新校验页面、路由、定义和状态；
- 挑战只绑定发起它的已接受序列；确认期间若服务端又接受同会话的后续终端意图，旧挑战立即取消，旧 token 不能在新序列之后补交；
- 页面、阶段、任务或受众已变化时，旧按钮请求失败关闭。

## 10. 实时更新与生命周期

- 打开页面后，服务端只在投影哈希或权威 revision 变化时发送 binding delta；
- 无变化的周期检查不触发按钮闪烁、控件重建、进入动画或重复声音；
- 真正路由变化才重新下发完整页面；
- 普通子页的来源按钮持续重验；身份、阶段、任务、任务状态、predicate 或历史可见性失效时，自动返回最近仍合法的父页；
- 页面关闭、掉线、世界重置和停服会清理会话、摘要与未使用确认挑战；
- `/reload` 重新编译定义；未开始的普通终端只在健康 live generation 恢复后采用新路由，活动时间线的受保护任务/历史授权始终使用冻结快照，即使本次 live 编译失败也继续可用；
- 普通终端过期、页面失效或投影失败时使用显式 invalidation，不让客户端停在可操作的旧页面；
- 强制流程始终高于普通终端，普通终端不能覆盖或关闭强制页。

## 11. 示例夹具

仓库：

```text
examples/pixel-tzz-base-datapack
```

玩家页：

| 页面 | 用途 |
|---|---|
| `pixel_tzz:player/home` | 默认玩家首页 |
| `pixel_tzz:player/task_runner` | 逃走者当前任务 |
| `pixel_tzz:player/task_hunter` | 猎人当前任务与本人出生点 |
| `pixel_tzz:player/history` | 已裁剪历史列表与同页原位详情 |
| `pixel_tzz:player/history_detail` | 保留的独立详情兼容示例 |
| `pixel_tzz:player/profile` | 本人授权数据 |
| `pixel_tzz:player/help` | 文档轮播入口 |
| `pixel_tzz:player/help_catalog` | 可直接选择的帮助目录 |
| `pixel_tzz:player/help_terminal` | 终端基础正文 |
| `pixel_tzz:player/help_visibility` | 可见内容与授权正文 |
| `pixel_tzz:player/help_history` | 事件回顾正文 |
| `pixel_tzz:player/help_actions` | 安全操作与函数验收入口 |

指南轮播中的书封不是模组写死的资源。数据包在 `player/help` 页每个封面卡片的首个
`text` 节点中直接声明书名，例如：

```json
{
  "type": "text",
  "style": "ui_kicker_info",
  "text": {"text": "TERMINAL HANDBOOK"},
  "wrap": true,
  "max_lines": 3,
  "overflow": "ellipsis"
}
```

框架按封面可用宽度自动换行，最多显示三行；超过三行才使用省略号，避免书名越过封面边界。
不同指南可以独立使用不同名称、颜色样式和正文页面。

验收函数动作：

```text
pixel_tzz:acceptance_ping
pixel_tzz:acceptance_3a/player/ping
```

点击后读取：

```mcfunction
/data get storage pixel_tzz:acceptance_3a last_player_action
```

该 storage 只用于观察函数宏，不是模组权威状态，也不会推进游戏。

## 12. 内容作者检查表

- Game 已提升到 `api_version: 3`；
- 有且只有一个合法 `default_page`；
- 每个路由优先级在游戏内唯一；
- 不向玩家页发送完整玩家名单、未来任务、隐藏分支或后台审计；
- 每个 `personal/` 绑定都有独立 `player_data` 授权；
- `personal_meta/<ID>/name` 与 `/value_name` 只用于同一份已投影 personal 值的安全展示文本，
  缺席时不得回退显示原始机器键；
- 历史总开关和每条事件公开均明确；
- 历史按钮只判断 Boolean `detail_available`，不读取或猜测 `detail_page`；
- 每个注册按钮有稳定 node ID；
- 玩家函数只接收服务端宏，不接收任意客户端值；
- 高风险函数配置确认、次数、冷却和审计；
- 页面正文不重复右上角同步徽标；
- 中文为主，英文只作弱化衬托；
- `『』` 只用于正式高层名称，`「」` 只用于当前对象或结果；
- 真实客户端完成缩放、小窗口、多人、重连、`/reload` 和重放验收。
