# Pixel TZZ Pro 数据包注册 API（2A）

状态：`format_version = 1` / `api_version = 1`  
适用模组版本：`0.1.0`  
目标 Minecraft：`26.2`

## 1. 2A 的边界

2A 只完成内容定义的发现、严格解析、交叉引用校验、不可变快照发布和诊断：

- 模组提供安全、可复用的注册框架；
- 数据包注册具体游戏、身份、队伍、生存状态、阶段、字段、流程、节点和面板操作；
- 2A 不执行流程或面板操作；
- 2A 不修改玩家身份、队伍、生存状态、阶段、字段、准备状态或既有世界数据；
- 2A 不渲染数据包页面、图标或 BossBar；
- `page` 节点中的页面 ID 和面板 `icon` 在 2A 只校验资源标识符语法；
- 页面文档、布局组件及实际交互在后续里程碑接入；
- 第一里程碑持久化的 `GamePhase` 枚举暂时只作为旧存档桥，不是新游戏定义的阶段来源。

下面描述的是已经由 2A 注册层接受并校验的格式，同时也是后续执行器必须遵守的语义契约。

## 2. 路径与定义 ID

定义文件使用以下路径：

```text
data/<namespace>/pixel_tzz_pro/games/<path>.json
data/<namespace>/pixel_tzz_pro/roles/<path>.json
data/<namespace>/pixel_tzz_pro/teams/<path>.json
data/<namespace>/pixel_tzz_pro/life_states/<path>.json
data/<namespace>/pixel_tzz_pro/phases/<path>.json
data/<namespace>/pixel_tzz_pro/fields/<path>.json
data/<namespace>/pixel_tzz_pro/flows/<path>.json
data/<namespace>/pixel_tzz_pro/panel_actions/<path>.json
```

文件路径直接决定定义 ID，JSON 内不重复填写 `id`。例如：

```text
data/pixel_tzz/pixel_tzz_pro/life_states/alive.json
→ pixel_tzz:alive
```

所有资源引用都必须显式携带命名空间。`runner` 会被拒绝，必须写成 `pixel_tzz:runner`。

相同资源路径遵循 Minecraft 原生数据包优先级：最高优先级文件完整覆盖低优先级文件，不进行 JSON 深合并。

## 3. 公共规则

每个定义文件都必须包含：

```json
{
  "format_version": 1
}
```

除游戏定义外，其他定义还必须包含所属游戏：

```json
{
  "game": "pixel_tzz:main"
}
```

文本字段接受非空的原版文本组件 JSON，例如：

```json
{"text": "逃走者", "color": "#65D68A"}
```

也可以使用原版文本组件数组。颜色、翻译键及其他文本样式遵循原版文本组件格式。

注册层使用严格 JSON：

- 不接受注释、尾随逗号或根对象后的额外内容；
- 不接受重复 JSON 键；
- 每层对象都拒绝未知键；
- 拒绝错误类型、缺少必填键、无效枚举和越界数值；
- 拒绝无命名空间或无效的资源 ID；
- 拒绝缺失引用和跨游戏引用；
- 任一 Pixel TZZ 定义失败都会使整份候选注册表失败，不会只跳过坏文件。

标签 ID 是开放词汇，不要求另建标签定义；身份、队伍、生存状态、阶段、字段、流程等实体引用则必须存在且属于同一游戏。

## 4. 游戏定义

路径：`games/<path>.json`

```json
{
  "format_version": 1,
  "api_version": 1,
  "content_version": 1,
  "name": {"text": "全员逃走中"},
  "initial_phase": "pixel_tzz:setup",
  "default_role": "pixel_tzz:runner",
  "default_life_state": "pixel_tzz:alive"
}
```

- `api_version` 当前必须为 `1`；
- `content_version` 必须为正整数；
- `initial_phase`、`default_role` 和 `default_life_state` 必须存在且属于该游戏；
- 同一世界的活动游戏选择在后续里程碑接入，2A 可以同时注册多个游戏。

## 5. 身份、队伍与生存状态

### 5.1 身份

路径：`roles/<path>.json`

```json
{
  "format_version": 1,
  "game": "pixel_tzz:main",
  "name": {"text": "逃走者"},
  "description": {"text": "本轮游戏的普通参与者。"},
  "tags": [
    "pixel_tzz:general_initialization",
    "pixel_tzz:ready_participant"
  ],
  "tab": {
    "prefix": {"text": "[逃走者]"},
    "color": "#65D68A"
  }
}
```

- `description`、`tags` 和 `tab` 可省略；
- `tab.prefix` 是原版文本组件；
- `tab.color` 必须是 `#RRGGBB`；
- 模组不根据“猎人”或“逃走者”等显示文字推断规则。

### 5.2 队伍

路径：`teams/<path>.json`

```json
{
  "format_version": 1,
  "game": "pixel_tzz:main",
  "name": {"text": "逃走者 A 队"},
  "description": {"text": "逃走者内部的可选分队。"},
  "allowed_roles": ["pixel_tzz:runner"],
  "tags": [],
  "tab": {
    "prefix": {"text": "[A队]"},
    "color": "#64D8E8"
  }
}
```

- `allowed_roles` 必填且不能为空；
- 所有允许身份必须存在且属于同一游戏；
- 玩家最多一个主要身份和一个队伍，实际状态执行不属于 2A。

### 5.3 生存状态

路径：`life_states/<path>.json`

```json
{
  "format_version": 1,
  "game": "pixel_tzz:main",
  "name": {"text": "已捕获", "color": "#A92D3E"},
  "description": {"text": "已被捕获，但仍保留原身份与队伍。"},
  "tags": ["pixel_tzz:inactive_participant"]
}
```

- `description` 和 `tags` 可省略；
- `life_state` 是独立于 `role` 和 `team` 的维度；
- 捕获只应把生存状态从 `alive` 改为 `captured`，不能因此丢失逃走者身份；
- 复活可以把同一玩家改回数据包注册的存活状态；
- 2A 只校验并保存这些定义，不执行捕获或复活。

## 6. 阶段

路径：`phases/<path>.json`

```json
{
  "format_version": 1,
  "game": "pixel_tzz:main",
  "name": {"text": "开局设置"},
  "transitions": ["pixel_tzz:initializing"],
  "on_enter": "pixel_tzz:phase/setup_enter",
  "on_exit": "pixel_tzz:phase/setup_exit"
}
```

- `transitions` 可省略；省略或空数组表示没有已注册的相邻目标；
- 所有目标阶段必须存在且属于同一游戏；
- `on_enter`、`on_exit` 可省略；
- 回调必须对应实际存在的 `data/<namespace>/function/<path>.mcfunction`；
- 转换条件、事务和主持人确认在实际阶段运行器中接入，2A 不转换阶段或调用回调。

## 7. 字段

路径：`fields/<path>.json`

完整示例：

```json
{
  "format_version": 1,
  "game": "pixel_tzz:main",
  "version": 1,
  "name": {"text": "路线偏好"},
  "description": {"text": "只用于示例。"},
  "scope": "player",
  "type": "single_choice",
  "required": true,
  "default": "balanced",
  "editable_by": "player",
  "invalidates_ready": true,
  "roles": ["pixel_tzz:runner"],
  "phases": ["pixel_tzz:setup", "pixel_tzz:initializing"],
  "visible_when": "pixel_tzz:field/show_route_preference",
  "migration": "preserve",
  "options": ["safe", "balanced", "risky"]
}
```

### 7.1 公共字段

| 键 | 必填 | 默认值 | 说明 |
|---|---|---|---|
| `version` | 是 | 无 | 正整数；字段 Schema 语义变化时递增 |
| `name` | 是 | 无 | 非空原版文本组件 |
| `description` | 否 | 无 | 帮助文字 |
| `scope` | 否 | `player` | `flow` 或 `player` |
| `type` | 是 | 无 | 见下表 |
| `required` | 否 | `false` | 是否必须取得合法值 |
| `default` | 否 | 无 | 必须与类型及所有约束一致 |
| `editable_by` | 否 | `none` | `none`、`player`、`host`、`both` |
| `invalidates_ready` | 否 | `false` | 修改后是否应使玩家准备失效 |
| `roles` | 否 | `[]` | 适用身份；空数组表示不限制身份 |
| `phases` | 否 | `[]` | 适用阶段；空数组表示不限制阶段 |
| `visible_when` | 否 | 无 | 必须存在的数据包 predicate |
| `migration` | 否 | `preserve` | `preserve` 或 `reset_to_default` |

`roles` 内是 OR，`phases` 内是 OR；两个非空维度及 `visible_when` 共同使用 AND。2A 校验这些引用但不评估可见性。

`scope` 的语义：

- `flow`：只属于一次流程实例；
- `player`：玩家持久字段，可跨流程、重连和重启保留。

迁移策略：

- `preserve`：版本提升后优先保留既有值；默认策略；
- `reset_to_default`：版本提升后按显式默认值重置，因此必须提供 `default`。

迁移的实际执行不属于 2A；2A 只校验并冻结策略。

### 7.2 字段类型与约束

| `type` | 值 | 可用约束 |
|---|---|---|
| `boolean` | JSON 布尔值 | 无 |
| `integer` | 64 位整数 | `min`、`max` |
| `string` | JSON 字符串 | `min_length`、`max_length`，按 Unicode 码点计数 |
| `identifier` | 显式命名空间资源 ID 字符串 | 无 |
| `single_choice` | `options` 中的一个字符串 | 必须提供非空且无重复的 `options` |
| `multi_choice` | 无重复字符串数组 | 必须提供 `options`；可用 `min_selected`、`max_selected` |

约束只能用于对应类型。最小值不能大于最大值，长度不能为负，多选上下限必须落在选项数量内。`default` 还必须满足范围、长度、选项、选择数量及无重复要求。

当前示例不注册尚无实际用途的剧情字段；通用教程只依赖流程完成记录。

## 8. 流程与节点

路径：`flows/<path>.json`

```json
{
  "format_version": 1,
  "game": "pixel_tzz:main",
  "version": 1,
  "name": {"text": "通用教程"},
  "entry": "intro",
  "audience": {
    "roles": [],
    "teams": [],
    "life_states": ["pixel_tzz:alive"],
    "role_tags": ["pixel_tzz:general_initialization"],
    "team_tags": [],
    "life_state_tags": [],
    "exclude_host": true,
    "online_only": true
  },
  "required": true,
  "host_bossbar": {
    "name": [
      {"text": "{event}", "color": "gold"},
      {"text": ": ", "color": "gray"},
      {"text": "{completed}", "color": "green"},
      {"text": "/{total}", "color": "white"}
    ],
    "color": "purple",
    "style": "progress",
    "priority": 100,
    "completion_feedback": {
      "name": {"text": "{event}: 全员已完成", "color": "green"},
      "color": "green",
      "hold_ticks": 40,
      "fade_ticks": 12,
      "sound": "minecraft:entity.player.levelup"
    }
  },
  "on_start": "pixel_tzz:tutorial/start",
  "on_player_complete": "pixel_tzz:tutorial/player_complete",
  "on_all_complete": "pixel_tzz:tutorial/all_complete",
  "nodes": [
    {
      "id": "intro",
      "type": "page",
      "page": "pixel_tzz:tutorial/intro",
      "next": "ack"
    },
    {
      "id": "ack",
      "type": "confirm",
      "page": "pixel_tzz:tutorial/ack",
      "next": "done"
    },
    {
      "id": "done",
      "type": "complete"
    }
  ]
}
```

- `version` 必填且必须为正整数；
- `entry` 和节点 `id` 只能使用小写 `[a-z0-9_.-]+`；
- `required` 默认 `false`；
- `on_start`、`on_player_complete`、`on_all_complete` 均可省略，存在时必须引用实际 `mcfunction`；
- 2A 不调用任何回调。

### 8.1 受众

`audience` 可省略。省略时等价于：

```json
{
  "roles": [],
  "teams": [],
  "life_states": [],
  "role_tags": [],
  "team_tags": [],
  "life_state_tags": [],
  "exclude_host": true,
  "online_only": true
}
```

筛选语义：

- 同一个非空数组内是 OR；
- 不同非空数组之间是 AND；
- 空数组不增加限制；
- `role_tags` 匹配玩家当前身份的任一标签；
- `team_tags` 匹配玩家当前队伍的任一标签；
- `life_state_tags` 匹配玩家当前生存状态的任一标签；
- `exclude_host` 默认排除主持人；
- `online_only` 默认只在流程名单锁定时纳入在线玩家。

受众只决定流程参与者，不根据身份显示名称猜测规则。2A 校验实体引用，但不锁定名单或启动流程。

### 8.2 主持人 BossBar

`required: true` 的流程必须提供 `host_bossbar`。非必需流程也可以提供。

`host_bossbar`：

| 键 | 必填 | 默认值 | 约束 |
|---|---|---|---|
| `name` | 是 | 无 | 文本的可见内容必须同时包含 `{event}`、`{completed}`、`{total}` |
| `color` | 否 | `purple` | 原版 BossBar 颜色 |
| `style` | 否 | `progress` | 原版 BossBar 分段样式 |
| `priority` | 否 | `0` | `-1000..1000` |
| `completion_feedback` | 否 | 无 | 全员完成后的短暂反馈 |

可用原版颜色：

```text
pink, blue, red, green, yellow, purple, white
```

可用原版样式：

```text
progress, notched_6, notched_10, notched_12, notched_20
```

主标题模板在运行时用流程显示名、完成数和总数替换三个占位符。`priority` 用于多个进度条的稳定排序；2A 仅保存和校验，不创建 BossBar。

`completion_feedback`：

| 键 | 必填 | 默认值 | 约束 |
|---|---|---|---|
| `name` | 是 | 无 | 非空原版文本组件，可继续使用 `{event}` 等占位符 |
| `color` | 否 | `green` | 上述原版 BossBar 颜色之一 |
| `hold_ticks` | 否 | `40` | `0..1200` |
| `fade_ticks` | 否 | `12` | `0..100`；停留后逐帧收束退场 |
| `sound` | 否 | 无 | 显式命名空间声音事件 ID |

完成反馈沿用主 BossBar 的样式。`hold_ticks` 结束后，进度条会在 `fade_ticks` 内逐帧收束并隐藏；原版 BossBar 没有透明度通道，因此这里不会伪造 alpha 淡出。2A 只校验 `sound` 的资源 ID 语法，不验证声音事件是否存在，也不播放声音。

### 8.3 节点类型

| 类型 | 必需字段 | 作用域 | 说明 |
|---|---|---|---|
| `page` | `id`, `page`, `next` | 固定 `player` | 显示页面后前进 |
| `confirm` | `id`, `page`, `next` | 固定 `player` | 要求明确确认 |
| `choice` | `id`, `page`, `field`, `choices[]` | 固定 `player` | 保存单选字段并按值跳转 |
| `branch` | `id`, `cases[]`, `fallback` | 默认 `player` | 按数据包 predicate 分支 |
| `function` | `id`, `function`, `next` | 默认 `player` | 调用 `mcfunction` |
| `wait_players` | `id`, `next` | 固定 `event` | 等待锁定名单中的参与者 |
| `change_state` | `id`, `axis`, `value`, `next` | 默认 `player` | 修改一个状态维度 |
| `complete` | `id` | 默认 `player` | 完成个人或事件流程 |

`branch`、`function`、`change_state` 和 `complete` 可显式填写：

```json
"scope": "player"
```

或：

```json
"scope": "event"
```

节点细则：

- `choice.choices` 至少一项，每项格式为 `{"value":"alpha","next":"node_id"}`；
- `choice.field` 必须是同一游戏的 `single_choice` 字段；
- 每个路由值必须存在于字段 `options`，且路由值不能重复；
- `branch.cases` 至少一项，每项格式为 `{"predicate":"namespace:path","next":"node_id"}`；
- `branch` predicate 和 `function` 回调资源必须存在；
- `change_state.axis` 支持 `role`、`team`、`life_state`；
- `change_state.value` 必须引用同一游戏中与轴类型对应的定义。

### 8.4 流程图约束

首版流程图必须是非空的有向无环图：

- `nodes` 至少包含一个节点；
- 入口必须存在；
- 节点 ID 不得重复；
- 所有跳转目标必须存在；
- 至少有一个 `complete` 节点；
- 所有节点必须从入口可达；
- 每条分支都必须最终到达 `complete`；
- 不允许普通边形成循环。

未来确实需要循环时应新增显式、带上限的循环节点，不能放宽普通边。

### 8.5 完成记录与 `version`

流程完成状态按“流程 ID + 流程版本”判定：

- 玩家只在完成了当前已注册 `flow.version` 时，才算完成该流程；
- 数据包提高 `version` 后，旧版本完成记录继续保留作历史，但不满足当前版本的“已完成”；
- 因此旧版本记录会匹配当前流程的“未完成”筛选；
- 仅修改文本而不希望玩家重新完成时，不应提高 `version`；
- 改变必经节点、要求或完成语义时应提高 `version`。

2A 只注册正整数版本，不创建或迁移玩家完成记录。

## 9. 面板操作

路径：`panel_actions/<path>.json`

```json
{
  "format_version": 1,
  "game": "pixel_tzz:main",
  "surface": "host",
  "section": "roles",
  "order": 20,
  "label": {"text": "指定或重新初始化猎人"},
  "description": {"text": "选择一名或多名玩家执行猎人初始化。"},
  "icon": "minecraft:crossbow",
  "color": "#E94F64",
  "visible_when": "pixel_tzz:panel/show_role_tools",
  "enabled_when": "pixel_tzz:panel/can_assign_hunter",
  "disabled_reason": {"text": "当前阶段或目标状态不允许指定猎人。"},
  "phases": ["pixel_tzz:setup", "pixel_tzz:initializing"],
  "target": {
    "mode": "multiple",
    "min": 1,
    "max": 64,
    "filter": {
      "roles": ["pixel_tzz:runner", "pixel_tzz:hunter"],
      "teams": [],
      "life_states": ["pixel_tzz:alive"],
      "role_tags": [],
      "team_tags": [],
      "life_state_tags": [],
      "completed_flows": [],
      "incomplete_flows": [],
      "status_flows": ["pixel_tzz:general_tutorial"]
    }
  },
  "confirmation": {
    "title": {"text": "确认猎人身份调整"},
    "consequences": [
      {"text": "目标玩家将进入猎人初始化流程。"},
      {"text": "完成流程后，猎人身份才会正式生效。"}
    ]
  },
  "operation": {
    "type": "assign_role",
    "role": "pixel_tzz:hunter",
    "flow": "pixel_tzz:hunter_initialization",
    "apply": "after_flow",
    "completion_policy": "always"
  }
}
```

### 9.1 展示与动态状态

| 键 | 必填 | 默认值 | 说明 |
|---|---|---|---|
| `surface` | 是 | 无 | `host` 或 `player` |
| `section` | 是 | 无 | 小写 `[a-z0-9_.-]+` 分区 ID |
| `order` | 否 | `0` | `-10000..10000` |
| `label` | 是 | 无 | 按钮文本 |
| `description` | 是 | 无 | 操作说明 |
| `icon` | 否 | 无 | 显式命名空间图标资源 ID；2A 只校验语法 |
| `color` | 否 | 无 | `#RRGGBB` 语义色 |
| `visible_when` | 否 | 无 | 存在且为真的数据包 predicate 才显示 |
| `enabled_when` | 否 | 无 | 存在且为真的数据包 predicate 才可用 |
| `disabled_reason` | 条件必填 | 无 | `enabled_when` 存在时必填的用户可见原因 |
| `phases` | 是 | 无 | 至少一个同游戏阶段 |

即使没有 `enabled_when`，也可以提供 `disabled_reason`，供“无合格目标”“选择人数不足”等框架内置禁用状态显示。

2A 会验证 `visible_when` 和 `enabled_when` predicate 存在，但不会评估它们，也不会生成按钮。

### 9.2 目标选择与筛选

`target` 在所有操作中都必填。

| `mode` | 合法范围 | 默认范围 |
|---|---|---|
| `none` | 必须 `0..0` | `0..0` |
| `single` | 必须 `1..1` | `1..1` |
| `multiple` | `0..64` 且 `min <= max` | `1..64` |

`filter` 可省略。支持以下数组：

- `roles`
- `teams`
- `life_states`
- `role_tags`
- `team_tags`
- `life_state_tags`
- `completed_flows`
- `incomplete_flows`
- `status_flows`

完整组合语义：

- 同一个非空数组内是 OR；
- 不同非空数组之间是 AND；
- 空数组和缺失数组不增加限制；
- 身份、队伍、生存状态和流程引用必须存在且属于同一游戏；
- 标签是开放 ID，分别匹配玩家当前身份、队伍或生存状态的标签；
- `completed_flows` 中任一流程以当前注册版本完成即可满足该组；
- `incomplete_flows` 中任一流程的当前注册版本未完成即可满足该组；
- `status_flows` 只声明目标名单和二次确认要展示的流程完成状态，不参与候选资格筛选；
- 同一流程不能同时出现在 `completed_flows` 与 `incomplete_flows`；
- 两个流程组都非空时，必须同时满足“已完成组中的至少一个”和“未完成组中的至少一个”。

框架还会自动展示 `completed_flows`、`incomplete_flows` 中引用的流程，因此无需在
`status_flows` 重复声明。显示状态按流程当前注册版本计算，可区分“当前版本已完成”、
“仅旧版本完成”和“尚未完成”；数据包无需硬编码中文状态文案。

例如：

```json
{
  "roles": ["pixel_tzz:runner", "pixel_tzz:hunter"],
  "life_states": ["pixel_tzz:alive"],
  "completed_flows": [
    "pixel_tzz:general_tutorial",
    "pixel_tzz:hunter_initialization"
  ]
}
```

表示“当前身份是逃走者或猎人，且当前生存状态为存活，且已经完成当前版本通用教程或当前版本猎人初始化”。

`mode: "none"` 时不能提供资格筛选条件；`status_flows` 是纯展示声明，可以保留。

### 9.3 二次确认

`surface: "host"` 的操作必须提供 `confirmation`：

```json
{
  "title": {"text": "确认操作"},
  "consequences": [
    {"text": "第一条明确后果。"}
  ]
}
```

`consequences` 至少一条。玩家面板操作可以省略确认；实际执行器仍可根据风险增加框架级确认。

主持人认领、转交和 OP 接管属于模组系统权限，不由游戏数据包注册。

### 9.4 操作类型

支持：

```text
start_flow
assign_role
assign_team
assign_life_state
transition_phase
run_function
```

最小格式：

```json
{
  "type": "start_flow",
  "flow": "pixel_tzz:general_tutorial",
  "completion_policy": "if_incomplete"
}
```

```json
{
  "type": "assign_role",
  "role": "pixel_tzz:hunter",
  "flow": "pixel_tzz:hunter_initialization",
  "apply": "after_flow",
  "completion_policy": "always"
}
```

```json
{
  "type": "assign_team",
  "team": "pixel_tzz:runner_a",
  "apply": "immediate"
}
```

```json
{
  "type": "assign_life_state",
  "life_state": "pixel_tzz:alive",
  "apply": "immediate"
}
```

```json
{"type": "transition_phase", "phase": "pixel_tzz:ready"}
```

```json
{"type": "run_function", "function": "pixel_tzz:admin/custom_action"}
```

`assign_role` 和 `assign_team` 的 `apply` 默认是 `after_flow`；`assign_life_state` 默认是 `immediate`。

- `after_flow`：必须提供 `flow`，目标完成当前版本流程后才提交状态；
- `immediate`：二次确认后立即提交，可选是否附带 `flow`；
- `start_flow` 和带 `flow` 的 `assign_role` 可以声明 `completion_policy`：
  - `if_incomplete`：同一流程 ID 与版本已有自然完成记录的玩家直接计为完成；
  - `always`：忽略历史完成记录，本次仍要求重新完成；
  - `resume_only`：只恢复匹配的未结束实例，不创建新实例；
- `start_flow` 默认 `if_incomplete`，`assign_role` 默认 `always`；
- `completion_policy` 是向后兼容的可选字段，不提高 `format_version`；
- 所有实体、流程、阶段和函数引用都会校验；
- 2A 不提交状态、不启动流程、不转换阶段，也不执行函数。

2C 启动强制流程时会把实际使用的 predicate 文档冻结进实例。predicate 必须自包含；任意层级出现 `minecraft:reference` 都会以 `snapshot_invalid` 拒绝启动，因为该条件会在 `/reload` 后继续解析实时 predicate 注册表，破坏当前实例的冻结边界。需要复用条件时，应在数据包生成阶段展开为完整条件文档。

## 10. 原子重载与诊断

注册状态：

- `empty`：没有游戏定义；
- `ready`：非空候选全部通过并已发布；
- `invalid`：Pixel TZZ 定义校验失败；
- `platform_reload_failed`：Minecraft 本身的数据包重载失败。

行为：

1. 服务端启动及成功的 `/reload` 后读取最终生效资源；
2. 在局部候选中完成全部解析和交叉校验；
3. 零错误时一次性发布新的不可变 generation；
4. Pixel TZZ 候选失败时保留上一代快照，但标记为不健康并禁止发起新流程；
5. Minecraft 本身重载失败时，原版继续使用旧资源，Pixel TZZ 也保留旧快照和原健康状态；
6. 删除定义不会自动修改玩家已保存的身份、队伍、生存状态、字段 ID 或完成历史；
7. 只有非空、健康的新 generation 成功应用时才播放 ActionBar 加载成功动画。

诊断至少包含：

```text
错误码 + 源资源 ID + JSON Pointer + 原因
```

当前控制台显示第一条问题及全部问题的准确剩余数量。注册层只在内存中保留前 `100` 条详情，同时单独累计准确总数；服务端日志最多逐条写出这前 `100` 条诊断，超过时另写一条截断提示及未输出数量，不宣称日志包含全部问题。

## 11. 资源限制

- 最多 `1024` 个 Pixel TZZ 定义；
- 单个定义文件最多 `262144` 个字符；
- 所有定义文件合计最多 `8388608` 个字符；
- JSON 最大嵌套深度 `64`；
- 单流程最多 `256` 个节点；
- 单个文本组件 JSON 最多 `8192` 个字符；
- 面板批量目标上限 `64`；
- 不执行任意 Java 或客户端脚本；
- 2A 不执行任何已注册操作。

任一限制被突破都会拒绝整份候选注册表。

## 12. 示例

可运行示例位于：

```text
examples/pixel-tzz-base-datapack
```

当前跨里程碑示例共有 `35` 个文件，其中 `27` 个 Pixel TZZ 定义：

- `1` 个游戏；
- `3` 个身份；
- `2` 个生存状态；
- `3` 个阶段；
- `1` 个版本化字段；
- `2` 个流程；
- `4` 个面板操作；
- `1` 个主题；
- `10` 个页面，其中六个为 2B 预览页、四个为 2C 极短流程页。

示例暂未注册队伍；相应 Schema 仍由 2A 注册层和自检覆盖。2A 只负责注册这些定义，四个 2C 页面是否进入真实强制流程由后续权威执行层决定。
