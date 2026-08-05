# Pixel TZZ Pro 数据包动态消息 API（3B）

状态：**已实现并完成完整自动检查与用户逐项五客户端实机验收；已通过 PR #5 合并主线**

- 适用当前模组版本：`0.1.0`
- 目标 Minecraft：`26.2`
- 游戏 API：`api_version: 3`
- 网络协议：v12
- 世界状态：Schema v4
- 本文资源格式：`format_version: 1`
- v12 客户端播放计划格式：FORMAT 2

本文是 V3B 动态 Chat、Title、Subtitle、ActionBar、声音和服务端回调的当前数据包契约。
页面、可信字段和玩家历史的基础规则分别继续遵循
[`DATA-PACK-UI-2B.md`](DATA-PACK-UI-2B.md)、
[`DATA-PACK-PLAYER-TERMINAL-API-3A.md`](DATA-PACK-PLAYER-TERMINAL-API-3A.md) 和
[`DATA-PACK-TASK-API-2D.md`](DATA-PACK-TASK-API-2D.md)。

本文字段、默认值和上限以当前
`MessageDefinitions`、`MessageDefinitionParser`、`DefinitionCompiler` 与仓库示例数据包为准。
计划文档描述产品目标；当计划示意与本文的可解析格式不一致时，以本文为准。

## 1. 设计边界

一条 `message_cues` 资源是一段可调用的完整“文字演出”，可以在同一个稳定 ID 中打包：

- 一个或多个 Chat、Title、Subtitle、ActionBar 文本节点；
- 独立声音节点；
- 服务端函数回调；
- 节点延迟、依赖、有限重复、条件和语言变体；
- 受众、并发、屏幕、重连、Reload、辅助功能和历史策略。

模组负责严格解析、引用校验、服务端授权、最小权限投影、权威时间线、动画原语和错误隔离。
数据包负责正式文案、视觉预设、目标、参数、回调函数和历史内容。客户端不能提交任意文本、
任意函数或未注册受众；普通 `tellraw`、签名玩家聊天和页面静态文本不会自动进入 V3B。

动态 Chat 最终是一条真实的系统聊天记录。动画应原位更新同一条记录，不能通过逐帧追加新消息
伪造打字效果；结束后保留最终富文本。Title 系通道使用原版语义位置，不借 V3B 建立自由坐标
HUD。常驻 HUD 属于 V3C。

## 2. 资源路径、ID 与覆盖

```text
data/<namespace>/pixel_tzz_pro/text_effects/<path>.json
data/<namespace>/pixel_tzz_pro/message_cues/<path>.json
```

路径决定稳定资源 ID。例如：

```text
data/pixel_tzz/pixel_tzz_pro/text_effects/notice/success.json
→ pixel_tzz:notice/success

data/pixel_tzz/pixel_tzz_pro/message_cues/task/start.json
→ pixel_tzz:task/start
```

两个目录都允许子目录。每个文件必须声明：

```json
{"format_version": 1}
```

Minecraft 数据包优先级先决定同一路径的最终资源。高优先级文件**完整覆盖**低优先级同 ID
文件，不做字段级深合并。`message_cues` 引用 `text_effects` 时只允许一层明确预设引用，
不支持预设继承预设。

JSON 使用严格解析：

- 根必须是对象；
- 重复键、尾随内容、未知键和错误类型都会产生诊断；
- 所有命名空间 ID 都必须显式包含 `namespace:`；
- 本地 ID（参数、字段、节点、分组）必须匹配
  `[a-z][a-z0-9_.-]{0,63}`。

## 3. 时间、颜色与富文本通则

### 3.1 时间

所有时间都是字符串并必须带单位：

```text
10t
80ms
1.5s
```

规则：

- `t` 只能使用整数；
- `ms` 和 `s` 最多三位小数；
- 最大值为 7 天；
- 字符间隔最少 `1ms`；
- 只有延迟、停留、淡入淡出分段等明确允许零值的字段可写 `0t`；
- 正时长字段写零会得到 `OUT_OF_RANGE`。

视觉动画可以小于 1 Tick；服务端回调在到期后的完整 Tick 执行，不能提前。

### 3.2 颜色

颜色接受：

- `#RRGGBB`；
- 原版命名色：
  `black`、`dark_blue`、`dark_green`、`dark_aqua`、`dark_red`、`dark_purple`、
  `gold`、`gray`、`dark_gray`、`blue`、`green`、`aqua`、`red`、
  `light_purple`、`yellow`、`white`。

### 3.3 原版组件

`component` 使用当前版本原版文本组件格式，支持 `text`、`translate`、`keybind`、样式、
字体、子组件、`hover_event`、`click_event` 和 `insertion`。

模板里的静态 `component` 不允许直接包含 `score`、`selector` 或 `nbt`，因为这些内容必须在
服务端规定的时机锁定。请把它们放进动态字段的 `source.component`。字段值或参数类型为
`component` 时可以使用这些原版动态组件。

单个组件规范化后最大 65,536 UTF-8 字节。交互只覆盖已经显示的字符；V3B 不提升点击事件权限，
也不会把系统演出伪造成签名玩家聊天。

## 4. `text_effects`

### 4.1 顶层

```json
{
  "format_version": 1,
  "typewriter": {},
  "fade": {},
  "scramble": {},
  "gradient": {},
  "motion": {},
  "pulse": {},
  "character_sound": {},
  "max_character_sound_events": 256
}
```

除 `format_version` 外至少声明一个效果块。`max_character_sound_events` 默认 `256`，
范围 `1..1024`。

### 4.2 打字机

```json
{
  "typewriter": {
    "character_interval": "45ms",
    "cursor": {
      "enabled": true,
      "glyph": "█",
      "color": "#77E6FF",
      "blink": true,
      "blink_period": "400ms"
    },
    "fresh_color": "#FFF3A6",
    "restore_after_characters": 3,
    "restore_mode": "gradient"
  }
}
```

| 字段 | 必填 | 默认 | 限制与语义 |
|---|---|---|---|
| `character_interval` | 是 | 无 | 每个可见字素的间隔，至少 `1ms` |
| `cursor` | 否 | 整块禁用 | 声明对象后 `enabled` 默认 `true` |
| `cursor.enabled` | 否 | `true` | 只关闭光标，不关闭打字机 |
| `cursor.glyph` | 否 | `▌` | 1..8 个 Unicode code point |
| `cursor.color` | 否 | `#FFFFFF` | 合法颜色 |
| `cursor.blink` | 否 | `false` | 是否闪烁 |
| `cursor.blink_period` | 否 | 闪烁时 `500ms` | 仅在 `blink: true` 时允许 |
| `fresh_color` | 否 | 无 | 字符刚出现时的覆盖色；与光标颜色独立 |
| `restore_after_characters` | 否 | `0` | `0..256`；`0` 表示不保留新字色 |
| `restore_mode` | 否 | `instant` | `instant` 或 `gradient` |

字符离开新字色范围后必须恢复自身最终组件样式，不会把整句永久染成一种颜色。字符计数按字素，
不会拆坏中文、Emoji 或组合字符。

### 4.3 淡入淡出、乱码、渐变、滑入与脉冲

```json
{
  "fade": {
    "enter": "150ms",
    "hold": "1.5s",
    "exit": "300ms"
  },
  "scramble": {
    "character_interval": "1t",
    "characters": "█▓▒░ABC123"
  },
  "gradient": {
    "from_color": "dark_aqua",
    "to_color": "#FFF3A6",
    "duration": "1s"
  },
  "motion": {
    "slide_x": -8,
    "slide_y": 4,
    "start_scale": 0.9,
    "duration": "250ms"
  },
  "pulse": {
    "scale": 1.12,
    "duration": "300ms"
  }
}
```

| 效果 | 字段 | 默认/范围 |
|---|---|---|
| `fade` | `enter`、`hold`、`exit` | 各自默认 `0t`；至少一项非零 |
| `scramble` | `character_interval` | 必填，至少 `1ms` |
| `scramble` | `characters` | 默认 `█▓▒░ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789`；1..128 code point |
| `gradient` | `from_color`、`to_color`、`duration` | 三项必填；时长必须大于零 |
| `motion` | `slide_x`、`slide_y` | 默认 `0`；各 `-64..64` |
| `motion` | `start_scale` | 默认 `1.0`；`0.75..1.25` |
| `motion` | `duration` | 必填且大于零；位移和缩放不能全部保持默认 |
| `pulse` | `scale` | 默认 `1.08`；必须大于 `1.0`，最大 `1.5` |
| `pulse` | `duration` | 必填且大于零 |

### 4.4 字符音

```json
{
  "character_sound": {
    "sound": "minecraft:block.note_block.hat",
    "category": "ui",
    "every_characters": 2,
    "skip_whitespace": true,
    "skip_punctuation": true,
    "skip_newline": true,
    "volume": 0.45,
    "pitch": 1.15,
    "pitch_variation": 0.1,
    "attachment": "player",
    "missing": "fallback",
    "fallback": "minecraft:ui.button.click"
  }
}
```

| 字段 | 必填 | 默认 | 范围/枚举 |
|---|---|---|---|
| `sound` | 是 | 无 | 声音 ID |
| `category` | 否 | `ui` | `master`、`music`、`record`、`weather`、`block`、`hostile`、`neutral`、`player`、`ambient`、`voice`、`ui` |
| `every_characters` | 否 | `1` | `1..64` |
| `skip_whitespace` | 否 | `true` | 跳过空白 |
| `skip_punctuation` | 否 | `false` | 跳过标点 |
| `skip_newline` | 否 | `true` | 跳过换行 |
| `volume` | 否 | `1.0` | `0..4` |
| `pitch` | 否 | `1.0` | `0..2` |
| `pitch_variation` | 否 | `0.0` | `0..2` |
| `attachment` | 否 | `player` | `player`、`world_origin` |
| `missing` | 否 | `silent` | `silent`、`fallback`、`block_start` |
| `fallback` | 条件 | 无 | `missing: fallback` 时必填；其他模式禁止 |

同一演出最多一个文本节点轨道使用 `character_sound_role: primary`。其他文本节点应保持
`muted`，避免 Chat、Subtitle 等并行时重复播放字符音。

## 5. 效果引用与局部覆盖

文本内容的 `effect` 有三种形式：

```json
"effect": "pixel_tzz:notice/success"
```

```json
"effect": {
  "preset": "pixel_tzz:notice/success",
  "overrides": {
    "typewriter": {
      "character_interval": "30ms",
      "cursor": {"color": "yellow"}
    },
    "fade": {"hold": "2s"},
    "max_character_sound_events": 48
  }
}
```

```json
"effect": {
  "overrides": {
    "typewriter": {"character_interval": "50ms"}
  }
}
```

对象形式必须有 `preset` 或至少一个非空 `overrides`。覆盖规则：

- `typewriter`、`cursor`、`fade`、`scramble`、`character_sound` 按其子字段显式覆盖；
- 未写的子字段继承预设；
- `gradient`、`motion`、`pulse` 是完整块替换，必须满足完整块的必填字段；
- 只写覆盖、不引用预设时，打字机与乱码的基础字符间隔为 `50ms`，淡入淡出基础时长为零；
- 独立 `character_sound` 覆盖必须声明 `sound`；
- 没有预设时，字符音事件预算默认 `256`；
- 当前格式不能用 `null` 删除预设中的整个效果；只可关闭 `cursor.enabled` 或改写具体参数；
- 预设在冻结 generation 中不存在时，该内容不能静默使用另一代预设。

动态字段的 `capture` 不属于 `effect.overrides`，应在 cue 或字段定义中配置。

## 6. `message_cues` 顶层

```json
{
  "format_version": 1,
  "game": "pixel_tzz:main",
  "required": false,
  "policies": {},
  "parameters": {},
  "fields": {},
  "nodes": [],
  "history": {},
  "static_fallback": {},
  "assets": [],
  "soft_limits": {}
}
```

| 字段 | 必填 | 默认 | 说明 |
|---|---|---|---|
| `format_version` | 是 | 无 | 当前只能为 `1` |
| `game` | 条件 | 无 | 所属游戏；`required: true` 时必填 |
| `required` | 否 | `false` | 无效且无满足条件的静态降级时是否阻止该游戏开局 |
| `policies` | 否 | 全部标准默认 | 正式策略对象 |
| `defaults` | 否 | 标准默认 | 兼容简写；不能与 `policies` 同时出现 |
| `parameters` | 否 | `{}` | 具名调用参数，最多 64 个 |
| `fields` | 否 | `{}` | 演出级动态字段，最多 64 个 |
| `nodes` | 是 | 无 | 至少一个，最多 128 个 |
| `history` | 否 | 不写历史 | 可回顾静态内容 |
| `static_fallback` | 否 | 无 | 定义不可用时的静态降级 |
| `assets` | 否 | `[]` | 字体、声音声明，最多 64 项 |
| `soft_limits` | 否 | 无额外软限制 | 数据包自设的更低预算 |

`defaults` 只提供旧式简写：

```json
{
  "defaults": {
    "audience": {},
    "clock": "presentation_time",
    "synchronization": "synchronized",
    "conflict": "queue",
    "duplicate": "ignore_while_active",
    "capture": "on_display",
    "priority": 0
  }
}
```

新资源应优先使用 `policies`。

## 7. 策略

### 7.1 完整结构与默认值

```json
{
  "policies": {
    "timing": {},
    "audience": {},
    "context": {},
    "concurrency": {},
    "delivery": {},
    "lifecycle": {},
    "accessibility": {},
    "control_groups": [],
    "capture": "on_display",
    "default_conflict": "queue"
  }
}
```

#### `timing`

| 字段 | 默认 | 可用值/说明 |
|---|---|---|
| `clock` | 未指定，由运行时用途决定 | `game_time`、`presentation_time` |
| `synchronization` | 未指定，由运行时用途决定 | `immediate`、`synchronized`、`per_player` |
| `synchronized_wait` | 无 | 仅允许配合 `synchronized` |
| `late_arrival` | `seek` | `seek`、`wait`、`static_final` |
| `synchronization_timeout` | `start_available` | `start_available`、`static_final`、`cancel` |

`game_time` 随游戏 Tick；`presentation_time` 使用单调表现时间。晚到客户端使用 `seek` 时跳到
标准时间线正确帧，不从头追赶。

#### `audience`

| 字段 | 默认 | 可用值/说明 |
|---|---|---|
| `target` | 所有在线玩家 | 受众对象，见 7.2 |
| `evolution` | `snapshot` | `snapshot`、`live_add`、`live_strict` |
| `sensitive` | `false` | `true` 时必须使用 `live_strict` |
| `on_loss` | `finalize` | `finalize`、`cancel` |

#### `context`

| 字段 | 默认 | 说明 |
|---|---|---|
| `games` | `[]` | 空表示不限 |
| `phases` | `[]` | 空表示不限 |
| `tasks` | `[]` | 空表示不限 |
| `recheck` | `before_start` | `before_start`、`live_strict` |

引用必须存在并与 cue 所属游戏一致。排队实例在实际开始前重新检查；`live_strict` 还会持续检查。

#### `concurrency`

| 字段 | 默认 | 可用值/说明 |
|---|---|---|
| `duplicate` | `ignore_while_active` | `allow`、`ignore_while_active`、`restart`、`queue`、`refresh` |
| `cooldown` | 无 | 可为零；调用冷却 |
| `dedupe_targets` | `true` | 目标集合是否参与去重 |
| `dedupe_parameters` | `[]` | 参与去重的已声明参数 |
| `refresh_key` | 无 | `refresh` 的稳定参数键，必须引用已声明参数 |
| `chat_interrupt` | `finalize` | `finalize`、`remove` |
| `priority` | `0` | `-10000..10000` |
| `groups` | `[]` | 最多 32 个本地 ID |

`restart` 产生明确替换；`refresh` 只通过注册的 `refresh_key` 地址原位更新，不扫描聊天历史。
调用结果区分拒绝、忽略、创建、刷新和排队，调用者不能把“被忽略”误认为新建实例。

#### `delivery`

| 字段 | 默认 | 可用值/说明 |
|---|---|---|
| `empty_audience` | `fail` | `fail`、`complete`、`callbacks_only` |
| `offline_targets` | `skip` | `skip`、`fail`、`queue`、`final_only` |
| `offline_ttl` | 无 | 仅允许配合 `queue` 或 `final_only` |

#### `lifecycle`

| 字段 | 默认 | 可用值 |
|---|---|---|
| `screen_start.chat` | `immediate` | `immediate`、`wait_gameplay`、`close_safe_screen`、`final_chat_only` |
| `screen_start.title/subtitle/action_bar` | `wait_gameplay` | 同上 |
| `obscured.chat` | `continue` | `continue`、`pause`、`finalize`、`cancel` |
| `obscured.title/subtitle/action_bar` | `pause` | 同上 |
| `screen_wait_timeout` | 无 | 正时长 |
| `screen_timeout_action` | `finalize` | `finalize`、`cancel`、`final_chat_only` |
| `max_pause` | 无 | 正时长 |
| `reload` | `finish_snapshot` | `finish_snapshot`、`finalize`、`cancel` |
| `restart` | `transient` | `transient`、`continue`、`finalize`、`cancel` |
| `external_conflict` | `yield` | `yield`、`pause`、`offset`、`overlay` |
| `reconnect` | `continue` | `continue`、`restart`、`final_only`、`drop` |
| `late_join` | `drop` | `continue`、`from_start`、`final_only`、`drop` |
| `resource_reload` | `finish` | `finish`、`finalize`、`cancel` |
| `respawn` | `continue` | `continue`、`finalize`、`cancel` |
| `attachment` | `player` | `player`、`world` |
| `dimension_exit` | `continue` | `continue`、`finalize`、`cancel` |

`screen_start` 和 `obscured` 都是按通道的映射；只写一项时其他通道继续使用默认值。普通演出
不能关闭强制流程页面。`reload: finish_snapshot` 表示活动实例按调用时冻结定义完成，新调用使用
新 generation。`resource_reload` 只控制客户端本地播放，不完成或取消服务端目标，也不推进回调。

`external_conflict` 处理原版或其他模组已经占用 Title、Subtitle、ActionBar 的本地冲突：

| 值 | 可见性与表现时钟 | 布局 |
|---|---|---|
| `yield` | 隐藏，表现时钟继续；冲突结束后跳到当前帧，隐藏期间的字符音不补播；若已结束则静默退休 | 注册位置不参与本次显示 |
| `pause` | 隐藏，表现时钟冻结；冲突结束后从原帧继续，并遵守 `max_pause` 与 `screen_timeout_action` | 注册位置不参与本次显示 |
| `offset` | 持续播放 | 使用通道安全偏移；动画、正文停留与静态尾帧始终使用同一偏移，冲突消失后恢复注册位置 |
| `overlay` | 持续播放 | 保持注册位置，允许原位叠加 |

这四项只改变客户端投影，不改变服务端标准时钟、字段捕获、受众或 callback。Chat 不使用这套
HUD 占用检测，仍按逻辑聊天条目与 `chat_interrupt` 处理。

`restart: continue` 的持久实例不会只比较 JVM 内 generation。检查点保存 cue 实际可达的有界
依赖清单；重启后会重新计算并要求定义、Function、Predicate 以及可达键集合完全一致。新增一个
能命中身份标签的角色、改变当前任务链，或增加其他新可达依赖，同样视为依赖变化。无法证明
依赖一致时，拥有合法 `static_fallback` 的实例降为冻结最终消息；没有合法静态降级则取消，绝不
读取新 generation 的字段、回调或历史正文。

冷启动批准的冻结最终消息会连同当时的 definition generation 一起排队。它可能来自显式
`restart: finalize`，也可能来自上述 `continue` 降级。后者即使原实例使用过参数，也不要求玩家
加入时旧依赖重新恢复一致；实际投递边界改为要求“批准降级的 generation 仍是当前 generation”，
并重新校验游戏上下文、当前受众、节点可见性、参数受众以及整份静态降级的完整授权。玩家加入前
发生任何成功 `/reload` 都会改变 generation，使旧冻结最终消息静默 fail-closed；不会泄露旧文案，
也不会在后续重连再次尝试。

#### `accessibility` 与顶层策略

| 字段 | 默认 | 说明 |
|---|---|---|
| `accessibility.allow_manual_complete` | `false` | 只补全动画，不隐藏最终内容 |
| `accessibility.reduced_motion` | `simplified` | `simplified`、`static_final` |
| `control_groups` | `[]` | 最多 32 个可控分组 |
| `capture` | `on_display` | cue 和内容字段的默认取值模式 |
| `default_conflict` | 未指定 | `parallel`、`queue`、`replace`、`discard`；未指定时使用通道安全默认 |

通道安全默认是 Chat 并行，Title、Subtitle、ActionBar 排队。节点自己的 `conflict` 优先于
`default_conflict`。

### 7.2 受众对象

直接选择器：

```json
{
  "source": "call_targets",
  "roles": ["pixel_tzz:hunter"],
  "teams": [],
  "life_states": [],
  "role_tags": [],
  "team_tags": [],
  "life_state_tags": [],
  "exclude_host": true,
  "online_only": true
}
```

联合选择器：

```json
{
  "any_of": [
    {"source": "current_host"},
    {"source": "call_targets", "roles": ["pixel_tzz:hunter"]}
  ]
}
```

`any_of` 最多 16 项，不能与直接选择器字段混用。每个选择器字段：

| 字段 | 默认 | 说明 |
|---|---|---|
| `source` | `all` | `all`、`current_host`、`call_targets`、`invoker` |
| `roles`、`teams`、`life_states` | `[]` | 空表示不限；非空集合内部任一命中 |
| `role_tags`、`team_tags`、`life_state_tags` | `[]` | 空表示不限；非空集合内部任一命中 |
| `exclude_host` | `false` | 排除当前主持人 |
| `online_only` | `true` | 仅在线玩家 |

不同过滤类别同时声明时需要同时满足。所有身份、队伍、生命状态和标签由服务端按活动游戏解析。

## 8. 参数

```json
{
  "parameters": {
    "target": {
      "type": "player",
      "required": true,
      "sources": ["call_argument", "target"],
      "on_error": "fail",
      "dedupe": true,
      "allowed_nodes": ["intro"],
      "audience": {"source": "call_targets"},
      "sensitive": true
    },
    "task_id": {
      "type": "identifier",
      "default": "pixel_tzz:task/example",
      "identifier_kind": "task",
      "on_error": "use_default"
    }
  }
}
```

| 字段 | 必填 | 默认 | 说明 |
|---|---|---|---|
| `type` | 是 | 无 | 参数类型 |
| `required` | 否 | `false` | 必需参数不能同时声明 `default` |
| `default` | 否 | 无 | 必须匹配类型；`player` 不允许默认值 |
| `sources` | 否 | `["call_argument"]` | 按声明顺序尝试，1..8 项 |
| `on_error` | 否 | `fail` | 所有来源失败后的处理 |
| `dedupe` | 否 | `false` | 参数可参与稳定调用身份 |
| `allowed_nodes` | 否 | `[]` | 空表示不限；非空时只能在列出的节点引用，且会检查传递依赖 |
| `audience` | 否 | 无 | 参数允许投影的受众 |
| `sensitive` | 否 | `false` | 为真时必须声明 `audience`，cue 受众必须 `live_strict` |
| `identifier_kind` | 否 | `any` | 只对 `identifier` 有效 |

参数类型：

```text
string, integer, decimal, boolean, identifier, player, component, duration
```

`identifier_kind`：

```text
any, game, phase, task, field, role, team, life_state,
player_data, event, statistic
```

非 `any` 的默认值和回退值会在编译期检查资源是否存在、是否属于同一游戏。`event` 与
`statistic` 使用 `<task-id>/<local-id>` 形式。

`allowed_nodes` 的“引用”包括字段 `source.argument`、`source.binding: argument.<id>`、节点与
变体条件、`player_parameter` 说话者，以及经 `after_node` 暴露的传递时序依赖。比如节点 B
声明在节点 A 之后播放，则 A 使用的参数也必须允许 B，否则 B 是否出现本身就可能泄露参数。

参数 `audience` 采用同一份闭包：服务端在给某名玩家投影节点前，会把该节点直接或传递使用的
所有参数受众取交集。未授权节点连静态文本、声音、说话者、字段槽和出现时机都不会发到该
客户端；不是把字段值替换成占位符后继续下发节点。变体条件和 `after_node` 因此也不能成为
旁路。历史标题、正文、保存字段与 `task_parameter` 会单独计算历史闭包，并在落盘前再次按
查看者校验参数受众。

### 8.1 参数来源

简单来源可以写成字符串：

```json
"sources": ["call_argument", "invoker", "origin", "target"]
```

结构化来源：

```json
{
  "sources": [
    {"type": "score", "objective": "pixel_tzz_demo", "holder": "@s"},
    {"type": "storage", "storage": "pixel_tzz:runtime", "path": "message.value"},
    {"type": "game_context", "value": "task"},
    {"type": "player_data", "field": "pixel_tzz:current_task_name"}
  ]
}
```

| `type` | 额外字段 | 说明 |
|---|---|---|
| `call_argument` | 无 | 调用时提供的同名参数 |
| `invoker` | 无 | 最初调用者 |
| `origin` | 无 | 调用原点上下文 |
| `target` | 无 | 当前目标玩家 |
| `score` | `objective` 必填，`holder` 可选 | objective 匹配 `[A-Za-z0-9_.+-]{1,16}`；holder 最长 128 |
| `storage` | `storage`、`path` 必填 | 路径最长 256，只允许安全路径字符 |
| `game_context` | `value` 必填 | `game`、`phase`、`task`、`event`、`statistic` |
| `player_data` | `field` 必填 | 引用 V3A `player_data` |

`player_data` 必须存在、属于同一游戏，并在自身 `surfaces` 中显式允许 `dynamic_message`。

### 8.2 错误策略

字符串形式：

```json
"on_error": "fail"
```

对象形式：

```json
"on_error": {
  "mode": "use_fallback",
  "fallback": "未知任务"
}
```

| 模式 | 要求 |
|---|---|
| `fail` | 不允许 `fallback` |
| `use_default` | 参数必须有合法 `default`，不允许额外 `fallback` |
| `use_fallback` | 必须声明类型匹配的 `fallback` |

解析失败不能把内部占位符或机器键直接显示给玩家。

## 9. 动态字段与三种取值时机

演出级字段位于 cue 顶层 `fields`；内容级字段位于某个文本 `content.fields`。模板分别使用
`scope: cue` 和 `scope: content` 引用。

```json
{
  "fields": {
    "target_name": {
      "capture": "on_first_field",
      "source": {"argument": "target"},
      "fallback": {"text": "未知玩家"},
      "reservation": {
        "max_graphemes": 32,
        "estimated_width": 192,
        "max_lines": 1
      }
    },
    "task_name": {
      "source": {"binding": "task/current/name"}
    },
    "score_now": {
      "capture": "per_field",
      "source": {
        "component": {
          "score": {"name": "@s", "objective": "pixel_tzz_demo"}
        }
      },
      "fallback": {"text": "0"}
    }
  }
}
```

| 字段 | 必填 | 默认 | 说明 |
|---|---|---|---|
| `capture` | 否 | cue 的 `policies.capture` | `on_display`、`on_first_field`、`per_field` |
| `source` | 是 | 无 | 恰好声明一个来源 |
| `fallback` | 否 | 无 | 原版组件；取值失败时使用 |
| `reservation` | 否 | 无 | 未来字段的预排版空间 |

`source` 恰好选择一项：

| 形式 | 说明 |
|---|---|
| `{"argument": "parameter_id"}` | 引用已声明参数 |
| `{"binding": "trusted/path"}` | 引用活动游戏现有可信 BindingRuntime 路径 |
| `{"component": {...}}` | 锁定原版动态组件，例如 `score`、`selector`、`nbt` |

`binding` 不是读取任意对象、任意 Storage 的权限。它最长 256 字符、不能含空白，并仍由活动
游戏的可信绑定注册表授权。`personal/<player_data-id>` 与
`personal_meta/<player_data-id>/name|value_name` 还会执行 V3A 的同游戏、表面授权和显示名
检查；其他路径继续复用现有 `BindingRuntime`，不会建立消息专用的第二套绑定系统。

### 9.1 Capture 语义

| 模式 | 锁定时机 |
|---|---|
| `on_display` | 内容块开始显示前的准备 Tick 一次性锁定该范围所需字段；首帧仍视为该内容块的显示时刻 |
| `on_first_field` | 第一个动态字段的光标出现前 1 Tick 一次性锁定该范围所需字段 |
| `per_field` | 每个字段在自己的光标出现前 1 Tick 取得最新值，开始显示后永久锁定 |

同一字段在同一范围内重复引用共享第一次锁定值。演出级字段在通道间共享；内容级字段由各内容
自己的时间线获取。需要同一数据源在后文重新取值时，请注册另一个字段 ID。

`fallback` 只在真实取值失败时使用，不应先闪现再被新值覆盖。服务端向每名玩家只投影已授权、
已命中分支的最终字段；客户端不能自行读取服务器绑定。

### 9.2 预留空间

```json
{
  "reservation": {
    "max_graphemes": 32,
    "estimated_width": 192,
    "max_lines": 2
  }
}
```

至少声明一项：

- `max_graphemes`：`1..4096`；
- `estimated_width`：`1..16384`；
- `max_lines`：`1..128`。

预留用于防止 `per_field` 的未来值让已经显示的文字反复跳行；它不是截断字段值的授权，最终仍
按文本节点 `layout.overflow` 处理。

Chat 的预留只进入客户端内部测量投影：原版按真实聊天宽度完成分行后，占位字形会在生成
`GuiMessage.Line` 前全部过滤，未来的整行只保留不可交互的空行槽。`GuiMessage.content`、原版
聊天日志、状态保存、最终历史、hover、click 与可复制正文始终只包含真实可见文本。

## 10. 文本模板、多语言和分段

### 10.1 模板

简写模板：

```json
{
  "parts": [
    {
      "component": {
        "text": "『任务开始』",
        "color": "aqua",
        "bold": true
      },
      "pause_after": "2t",
      "emphasis": true
    },
    {
      "component": {"text": " "}
    },
    {
      "field": {"scope": "cue", "id": "task_name"},
      "pause_before": "1t"
    }
  ]
}
```

每个 `parts` 项必须恰好声明 `component` 或 `field`。可选分段字段：

| 字段 | 默认 | 说明 |
|---|---|---|
| `pause_before` | 无 | 该段前停顿，可为 `0t` |
| `pause_after` | 无 | 该段后停顿，可为 `0t` |
| `emphasis` | `false` | 标记重点段，供脉冲等效果使用 |

一个模板需要 1..128 个 part，并且至少包含可见静态文字或动态字段。

### 10.2 多语言

```json
{
  "fallback": {
    "parts": [
      {"component": {"text": "Task started: "}},
      {"field": {"scope": "cue", "id": "task_name"}}
    ]
  },
  "locales": {
    "zh_cn": {
      "parts": [
        {"component": {"text": "任务开始："}},
        {"field": {"scope": "cue", "id": "task_name"}}
      ]
    },
    "en_us": {
      "parts": [
        {"component": {"text": "Task started: "}},
        {"field": {"scope": "cue", "id": "task_name"}}
      ]
    }
  }
}
```

如果对象直接包含 `parts`，它就是无额外语言表的 fallback。长格式必须声明 `fallback`，
最多 32 个 locale；locale 键匹配 `[a-z]{2,3}_[a-z0-9]{2,8}`。

所有语言模板中的字段引用都必须合法。客户端按自己的最终译文长度渲染；服务端回调不相信
客户端上报的文本长度。严格多人同步优先使用 `fixed_total`。

## 11. 节点、调度、条件与有限重复

### 11.1 公共节点字段

```json
{
  "id": "intro",
  "type": "text",
  "schedule": {
    "type": "cue_start",
    "offset": "2t"
  },
  "audience": {"source": "call_targets"},
  "priority": 20,
  "conflict": "replace",
  "when": {
    "evaluate_at": "node_start",
    "predicate": "pixel_tzz:is_hunter"
  },
  "repeat": {
    "count": 2,
    "interval": "10t",
    "capture": "per_repeat"
  }
}
```

| 字段 | 必填 | 默认 | 说明 |
|---|---|---|---|
| `id` | 是 | 无 | cue 内唯一的本地 ID |
| `type` | 是 | 无 | `text`、`sound`、`callback` |
| `schedule` | 否 | cue 起点 `0t` | 正式调度对象 |
| `at` | 否 | `0t` | 兼容简写；不能与 `schedule` 同时使用 |
| `audience` | 否 | 继承 cue | 节点级受众覆盖 |
| `priority` | 否 | 继承 cue | `-10000..10000` |
| `conflict` | 否 | 继承 cue/通道默认 | `parallel`、`queue`、`replace`、`discard` |
| `when` | 否 | 总是执行 | 条件 |
| `repeat` | 否 | 一次 | 有限重复 |

### 11.2 调度

从 cue 起点：

```json
"schedule": {"type": "cue_start", "offset": "5t"}
```

等待另一个节点完成：

```json
"schedule": {"type": "after_node", "node": "intro", "offset": "5t"}
```

监听 cue 事件：

```json
"schedule": {"type": "cue_event", "event": "all_complete", "offset": "1t"}
```

`offset` 默认 `0t`。事件值：

```text
start, target_complete, all_complete, interrupt
```

`after_node` 引用必须存在，不能指向自身，所有依赖必须形成 DAG；循环会产生
`CYCLIC_REFERENCE`。同一 cue 展开重复后最多 256 个节点 occurrence。

### 11.3 条件

包装形式必须恰好声明 `expression` 或 `predicate`：

```json
{
  "evaluate_at": "node_start",
  "expression": {
    "eq": [
      {"bind": "player.role.id"},
      {"literal": "pixel_tzz:hunter"}
    ]
  }
}
```

```json
{
  "evaluate_at": "node_start",
  "predicate": "pixel_tzz:acceptance_3b/is_hunter"
}
```

也可直接写 2B 条件表达式对象，此时使用所在位置的默认判断时机。`evaluate_at`：

```text
cue_start, node_start, field_capture
```

条件表达式继续使用现有受限 AST，不执行 JavaScript。Message 条件允许
`player.role.id`、`task/current/name` 等外部可信绑定路径进入 AST，但编译器和运行时仍负责
引用、游戏归属和授权。分支只在服务端求值，未命中内容不会下发客户端。

### 11.4 变体

文本节点可声明最多 16 个变体：

```json
{
  "variants": [
    {
      "when": {
        "predicate": "pixel_tzz:is_hunter"
      },
      "content": {
        "text": {
          "parts": [
            {"component": {"text": "猎人专属提示", "color": "gold"}}
          ]
        }
      }
    }
  ]
}
```

变体按顺序匹配并选择第一项命中结果。`content` 是完整内容对象，不与基础 `content` 深合并；
需要的 `text`、字段、效果、布局等应在变体里明确写出。

每节点最多 16 个变体，整个 cue 最多 128 个。

### 11.5 重复

```json
"repeat": {
  "count": 3,
  "interval": "1s",
  "capture": "per_repeat"
}
```

- `count` 默认 `1`，范围 `1..32`；
- `interval` 默认 `0t`；
- `capture` 为 `keep` 或 `per_repeat`，默认 `keep`；
- `count: 1` 时声明非零 interval 是矛盾配置；
- 不支持无限循环。

`keep` 复用该节点第一次的锁定值；`per_repeat` 允许每次 occurrence 按字段自己的 capture 规则
重新取值。

## 12. 文本节点

推荐结构：

```json
{
  "id": "intro",
  "type": "text",
  "channel": "subtitle",
  "content": {
    "text": {
      "parts": [
        {"component": {"text": "目标："}},
        {"field": {"scope": "cue", "id": "target_name"}}
      ]
    },
    "fields": {},
    "effect": "pixel_tzz:notice/success",
    "layout": {},
    "duration": {},
    "speaker": {},
    "character_sound_role": "muted",
    "hold": "2s"
  },
  "variants": []
}
```

| 字段 | 必填 | 默认 |
|---|---|---|
| `channel` | 是 | `chat`、`title`、`subtitle`、`action_bar` 之一 |
| `content.text` | 是 | 本地化模板 |
| `content.fields` | 否 | `{}` |
| `content.effect` | 否 | 无效果 |
| `content.layout` | 否 | 标准布局 |
| `content.duration` | 否 | 内容驱动 |
| `content.speaker` | 否 | 系统 |
| `content.character_sound_role` | 否 | `muted` |
| `content.hold` | 否 | 无额外停留 |
| `variants` | 否 | `[]` |

兼容旧式节点仍可直接写 `text`、`fields`、`effect`、`hold`，但不能同时再写 `content`。
旧式内容固定使用标准布局、内容驱动、系统发言者和静音字符轨。新资源建议使用 `content`。

### 12.1 布局

```json
{
  "layout": {
    "alignment": "left",
    "offset_x": 12,
    "offset_y": -8,
    "max_width": 320,
    "max_lines": 4,
    "line_spacing": 1.15,
    "overflow": "ellipsis"
  }
}
```

| 字段 | 默认 | 范围/枚举 |
|---|---|---|
| `alignment` | `center` | `left`、`center`、`right` |
| `offset_x`、`offset_y` | `0` | `-256..256` |
| `max_width` | 无 | `1..8192` |
| `max_lines` | 无 | `1..128` |
| `line_spacing` | `1.0` | `0.5..4.0` |
| `overflow` | `wrap` | `wrap`、`scale`、`ellipsis`、`static_final` |

换行使用客户端真实字体、窗口和 GUI 缩放；已解析但未显示字符参与预排版。

### 12.2 时长

```json
{
  "duration": {
    "mode": "fixed_total",
    "total": "3s",
    "language_basis": "fixed",
    "minimum_speed": 4.0,
    "maximum_speed": 40.0
  }
}
```

| 字段 | 默认 | 说明 |
|---|---|---|
| `mode` | `content_driven` | `content_driven`、`fixed_total` |
| `total` | 无 | `fixed_total` 必填；`content_driven` 禁止 |
| `language_basis` | `longest_declared` | `longest_declared`、`fixed` |
| `minimum_speed` | 无 | `0.01..1000` |
| `maximum_speed` | 无 | `0.01..1000`，不能小于 minimum |

`language_basis: longest_declared` 以 fallback 与所有已声明 locale 中估算时长最长者作为权威
依赖调度基准；`fixed` 则固定以 `text.fallback` 模板作为基准。两者都仍应用字段预留、分段暂停、
速度上下限和效果生命周期；`fixed` 不会以缩短或截断其他 locale 的实际文字来强行满足基准。

固定总时长在速度上下限内调整打字速度，并用停留补齐；不会为赶时长截断最终文字。

### 12.3 发言者

```json
{"speaker": {"kind": "system"}}
```

```json
{"speaker": {"kind": "player_parameter", "parameter": "target"}}
```

```json
{"speaker": {"kind": "registered_entity", "entity": "minecraft:villager"}}
```

`kind` 可为 `system`、`narrator`、`player_parameter`、`registered_entity`。后两项分别必须且只能
声明 `parameter` 或 `entity`；玩家参数必须是已声明的 `player` 类型。该字段只控制系统演出的
视觉来源，不产生伪造签名聊天。

不写 `speaker` 时固定使用 `system`，因此真实玩家/实体来源以及可能由 Chat Heads 一类装饰模组
显示的头像元数据默认都不开启。只有数据包显式选择 `player_parameter` 或
`registered_entity` 才会附带经服务端验证的视觉来源。该兼容行为不是权限来源：客户端是否
能够安全原位更新 Chat 仍只作为表现能力上报；不支持时该 Chat 节点降级为一次性最终静态文本，
其他通道和服务端回调不受影响。

`registered_entity.entity` 必须是服务端实体类型注册表中的真实 ID。运行时会从已注册
`EntityType` 的权威描述组件生成可见名称；不存在的 ID、空描述或超出协议边界的描述都会
fail-closed 为匿名 `system`，绝不会把原始 ID 当作可见发言者名称。当前客户端对通用实体只需要
这份经验证的名称，因此网络模型不携带实体类型 ID，也不会为通用实体虚构玩家 UUID、皮肤、
帽子层或签名聊天身份。

## 13. 声音与回调节点

### 13.1 独立声音

```json
{
  "id": "notice_sound",
  "type": "sound",
  "at": "1.25s",
  "sound": "minecraft:block.note_block.pling",
  "category": "ui",
  "volume": 0.7,
  "pitch": 1.2,
  "attachment": "player",
  "missing": "silent"
}
```

| 字段 | 必填 | 默认/范围 |
|---|---|---|
| `sound` | 是 | 声音 ID |
| `category` | 否 | `master`；枚举同字符音 |
| `volume` | 否 | `1.0`，`0..4` |
| `pitch` | 否 | `1.0`，`0..2` |
| `attachment` | 否 | `player`；也可 `world_origin` |
| `missing` | 否 | `silent`；也可 `fallback`、`block_start` |
| `fallback` | 条件 | `missing: fallback` 时必填 |

实例被替换或取消后，尚未到点的声音同步取消；已经播放的声音不会倒放或重复。

### 13.2 服务端回调

```json
{
  "id": "complete_callback",
  "type": "callback",
  "schedule": {
    "type": "cue_event",
    "event": "all_complete",
    "offset": "1t"
  },
  "function": "pixel_tzz:message/complete",
  "execution": "as_server",
  "location": "origin"
}
```

| 字段 | 必填 | 默认/枚举 |
|---|---|---|
| `function` | 是 | 必须是数据包中真实存在的函数 ID |
| `execution` | 否 | `as_server`；也可 `as_invoker`、`as_target` |
| `location` | 否 | `origin`；也可 `target` |

回调沿服务端标准时间线执行，不等待客户端完成报告。`target_complete` 为每个目标建立独立事件；
`all_complete` 在权威目标集合全部完成后触发；`interrupt` 只在取消/中断路径触发。

当前回调账本保证：

1. 先持久化 `PREPARED` 再执行函数；
2. 成功回调不会再次准备；
3. 失败只记录一次，默认不自动重试；
4. 崩溃遗留的 `PREPARED` 恢复为 `OUTCOME_UNKNOWN`，不会冒险重放世界修改。

客户端演出包不会包含回调函数名。主动“补全”视为正常完成并执行完成回调；“取消”只进入中断
回调。

## 14. 历史、静态降级、资产与软限制

### 14.1 历史

```json
{
  "history": {
    "title": {
      "parts": [
        {"component": {"text": "『任务提示回顾』"}}
      ]
    },
    "body": {
      "parts": [
        {"field": {"scope": "cue", "id": "target_name"}}
      ]
    },
    "task_source": "parameter",
    "task_parameter": "task_id",
    "timestamp_source": "game_time",
    "audience": {"source": "call_targets"},
    "saved_fields": ["target_name"],
    "replay_allowed": false
  }
}
```

| 字段 | 必填 | 默认 | 说明 |
|---|---|---|---|
| `title`、`body` | 是 | 无 | 本地化模板；只能引用 cue 级字段 |
| `task_source` | 否 | `none` | `none`、`current_task`、`parameter` |
| `task_parameter` | 条件 | 无 | `task_source: parameter` 时必填；必须是 task identifier 参数 |
| `timestamp_source` | 否 | `completion` | `game_time`、`presentation_time`、`cue_start`、`completion` |
| `audience` | 否 | cue 默认受众 | 历史可见者；当前不得包含 `current_host` 选择器 |
| `saved_fields` | 否 | `[]` | 只允许 cue 级字段，最多 64 个 |
| `replay_allowed` | 否 | `false` | 是否允许历史入口重播 |

历史保存字段当时锁定的最终值，默认显示静态内容，不自动重播动画。

V3B 历史由 V3A 玩家终端的“过去事件”消费；主持人控制台和 2D 任务时间线不是玩家终端，
也没有接收 V3B 历史正文、保存字段或重播 key 的投影协议。因此当前版本会以
`UNREACHABLE_HISTORY_AUDIENCE` 在编译期拒绝任何包含 `current_host` 选择器的
`history.audience`，而不是静默写入主持人无法打开的记录。这个限制只作用于历史受众；cue
本身的播放受众仍可使用 `current_host`。需要保存给实际观看者时请使用 `call_targets`，或使用
其他确实会进入玩家终端的受众，并继续遵守字段权限与最小投影规则。
省略 `history.audience` 时仍会继承 cue 默认受众；若继承结果包含 `current_host`，同样会被拒绝。

历史不是 cue 节点，不能写进 `allowed_nodes`。因此，只要参数的 `allowed_nodes` 非空，该参数
就不得直接或经 cue 字段用于历史 `title`、`body`、`saved_fields` 或 `task_parameter`；编译器会
以 `UNAUTHORIZED_REFERENCE` 拒绝整份 cue。节点演出和历史都需要同一个逻辑值时，应声明
两个来源相同的参数：节点参数保留非空 `allowed_nodes`，历史专用参数保持 `allowed_nodes: []`
（或省略该字段），再由历史专用 cue 字段引用后者。

### 14.2 静态降级

```json
{
  "static_fallback": {
    "channel": "chat",
    "component": {"text": "任务提示已完成"},
    "fallback_satisfies_required": true
  }
}
```

`channel` 默认 `chat`；`component` 必填且必须有可见文本；
`fallback_satisfies_required` 默认 `false`。

当一个 `required` cue 因跨资源引用等编译错误被禁用时，只有成功保留下来的静态降级且明确
`fallback_satisfies_required: true` 才能避免阻断所属游戏开局。JSON 自身无法解析时，编译器
不能安全提取该降级。

运行时把 `static_fallback` 视为“整份最终表现”，而不是可以逐节点删减的替代文本。投递前必须
确认该玩家仍被允许看到冻结调用中原本可能出现的每一个文本 occurrence，并同时通过节点受众、
参数 `audience` 与 `allowed_nodes` 的闭包校验；至少存在一个仍授权的可见文本 occurrence 才能
发送。一个公开节点不能替另一个已经撤权的节点为整份 fallback 背书。不能证明完整覆盖与完整
授权时必须静默结算或取消，不能发送部分静态正文。

### 14.3 资产

```json
{
  "assets": [
    {
      "type": "font",
      "id": "minecraft:default",
      "missing": "fallback",
      "fallback": "minecraft:uniform",
      "preload": true,
      "required_for_start": false
    },
    {
      "type": "sound",
      "id": "minecraft:block.note_block.hat",
      "missing": "block_start",
      "preload": true,
      "required_for_start": true
    }
  ]
}
```

| 字段 | 必填 | 默认/枚举 |
|---|---|---|
| `type` | 是 | `font`、`sound` |
| `id` | 是 | 资源 ID |
| `missing` | 否 | `silent`、`fallback`、`block_start`；默认 `silent` |
| `fallback` | 条件 | 仅 `missing: fallback` 时允许且必填 |
| `preload` | 否 | `false` |
| `required_for_start` | 否 | `false`；`block_start` 时必须为真 |

同一 `type + id` 不能重复声明。`preload: true` 的资产会在玩家连接或资源 Reload
完成后进入首份静默清单；`required_for_start: true` 的资产无论是否预加载，也会进入首份
清单以完成开局门禁。其余资产只在对应 cue 确实开始向该玩家投影时，追加到该连接的累计
清单。累计清单同时携带定义 `generation` 与连接内严格递增的清单序号；客户端报告必须完整
对应最新序号，旧报告不能覆盖新结果。预加载和增量声明都只包含非敏感渲染资源，不提前
下发隐藏正文、参数或回调，也不强制解码声音缓冲。

`final_only` 在新物理连接上会优先等待客户端能力与对应资产报告，但等待截止点在首次发现握手
未完成时冻结，最多为 `100` 个游戏 Tick（20 TPS 下约 `5` 秒），后续轮询不能滑动延长。握手
及时完成时发送正常的最小动态最终投影；到达精确截止 Tick 仍未完成时，只能尝试已经注册的原版
静态降级，而且仍须通过 14.2 的完整授权校验。没有完整授权文本时静默结算，不得让一个未上报的
客户端无限阻塞实例、回调或其他接收者。

### 14.4 数据包软限制

```json
{
  "soft_limits": {
    "max_visible_graphemes": 512,
    "max_lines": 8,
    "max_nodes": 8,
    "max_sound_nodes": 2,
    "max_callback_nodes": 2,
    "max_variants": 4,
    "max_active_instances": 8,
    "max_queue_depth": 16,
    "max_total_duration": "30s",
    "max_persisted_instances": 4,
    "max_pending_offline": 8
  }
}
```

| 字段 | 可配置范围 |
|---|---:|
| `max_visible_graphemes` | `1..32768` |
| `max_lines` | `1..256` |
| `max_nodes` | `1..128` |
| `max_sound_nodes` | `0..128` |
| `max_callback_nodes` | `0..128` |
| `max_variants` | `0..128` |
| `max_active_instances` | `1..1024` |
| `max_queue_depth` | `0..1024` |
| `max_total_duration` | 正时长，最大 7 天 |
| `max_persisted_instances` | `0..1024` |
| `max_pending_offline` | `0..4096` |

软限制只能收紧，不能突破模组硬上限。`max_nodes`、声音、回调、变体会在编译期检查；活动实例、
队列、持久化和离线等待由运行时检查。

## 15. 硬上限

### 15.1 资源与 Schema

| 对象 | 硬上限 |
|---|---:|
| 一次编译的全部 `pixel_tzz_pro` 定义 | 1,024 个 |
| 一次编译的全部定义源文本 | 8,388,608 字符 |
| 单个定义源文本 | 262,144 字符 |
| 单个消息规范化文档 | 262,144 UTF-8 字节 |
| 单个原版组件规范化结果 | 65,536 UTF-8 字节 |
| JSON 深度 | 64 |
| 单资源保留的诊断详情 | 100 条 |
| 参数 / cue 级字段 | 各 64 个 |
| 原始节点 | 128 个 |
| 单模板 `parts` | 128 个 |
| 单节点变体 / cue 总变体 | 16 / 128 个 |
| 单节点重复次数 | 32 次 |
| 展开后的节点 occurrence | 256 个 |
| locale / asset / control group | 32 / 64 / 32 个 |
| 单参数来源 | 8 个 |
| `audience.any_of` | 16 个选择器 |
| 通用命名空间 ID 列表 | 128 项 |

| 字符串或效果 | 硬上限 |
|---|---:|
| 本地 ID | 64 字符 |
| Binding 路径 | 256 字符 |
| 光标字形 | 8 Unicode code point |
| 乱码字符表 | 128 Unicode code point |
| `restore_after_characters` | 256 个字素 |
| 字符音事件 | 默认 256，最高 1,024 |
| 单时间值 | 7 天 |
| 优先级 | `-10000..10000` |

`reservation`、布局、声音数值和 `soft_limits` 的字段级边界见前文对应章节。边界值本身合法；
超过边界必须产生 `RESOURCE_LIMIT` 或 `OUT_OF_RANGE`，不能静默截断成另一种演出。

### 15.2 网络与客户端收件箱

当前 v12 使用 `MessagePlaybackPlan.FORMAT_VERSION = 2`（下称 FORMAT 2）。FORMAT 2 是
服务端投影给客户端的已解析播放计划格式，不是数据包 JSON 的 `format_version`；数据包
`text_effects` / `message_cues` 当前仍必须写 `format_version: 1`。FORMAT 2 还有独立的防御性
边界：

| 对象 | 当前边界 |
|---|---:|
| 单个播放计划 | 256 KiB |
| 计划节点 | 256 个 |
| 单节点分段 / 总分段 | 256 / 4,096 个 |
| 动态字段 slot | 512 个 |
| 线格式单组件 | 16,384 字符且 16,384 UTF-8 字节 |
| 单计划时间线 | 24 小时 |
| 单次字段增量 / 最终补全控制 | 128 KiB |
| 客户端活动实例 | 32 个 |
| 单实例待处理事件 / 全局待处理事件 | 128 / 512 个 |
| 客户端收件箱缓冲 | 4 MiB |

这些是线格式和恶意包防护边界，不是允许数据包绕过资源上限的第二套 Schema。资源定义若不能
安全投影成当前 v12 计划，服务端桥接必须显式拒绝、分段或使用已经注册的静态降级；不能截断正文
或丢掉回调。资源允许的 7 天时间值也不表示可以把 7 天演出一次塞进单个 24 小时网络计划。

### 15.3 FORMAT 2 最小投影

FORMAT 2 不是把原始 cue JSON 发给客户端。每名玩家只收到：

- 实例、cue、generation、严格递增 sequence 和刷新 cycle；
- `CREATE`、`REFRESH` 或带旧实例 ID 的 `REPLACE`；
- 已确定的游戏 Tick / 表现纳秒时钟锚点；
- 只影响本地播放的屏幕、遮挡、资源 Reload、减少动态和手动补全策略；
- 已解析为绝对起点的文本节点与声音节点；
- 静态富文本分段，或只含整数 slot、fallback 和预留空间的动态字段分段；
- 合并完成的效果、布局、时长和经验证的发言者；
- 已验证的世界原点。

FORMAT 2 明确不包含 callback、Predicate、原始条件、受众表达式、参数来源、Storage 路径、Score
表达式、变体或数据包完整定义。重复与依赖在投影前展平，未命中的分支不下发。

动态字段最终值通过独立 `message_field_delta_s2c` 按 slot 投影；`FINALIZE` 与内部自然终结控制
`FINISH` 都可以携带仍未锁定 slot 的最终值。`FINALIZE` 用于主动补全、撤权补全等需要立即折叠到
最终帧的边界；`FINISH` 只关闭服务端权威流，客户端仍须把已授权且因通道或安全页面排队的动画与
停留时间完整排空。两者都不延迟权威回调，也不允许客户端请求或补入新内容。

这里的“客户端排空”是一个严格受限的表现所有权边界：只有本地内建演出，或已经收到服务端
`FINISH` 的网络实例，才会在重生、维度切换等客户端可观察生命周期事件上自行应用投影中的策略。
尚未收到 `FINISH` 的普通网络实例仍等待服务端权威控制，不得仅凭客户端事件自行补全或取消。

`FINISH` 下发前，服务端已经完成节点授权、受众过滤、字段最终值锁定和生命周期策略归一化；客户端
排空只消费这份投影，不能重新读取数据包定义、Predicate、参数来源、隐藏字段或 callback。尤其是
`attachment: player` 会在投影时把有效 `dimension_exit` 归一为 `continue`；世界绑定实例也只有从
冻结 `origin.dimension` 实际离开时才应用 `dimension_exit`。客户端使用原版重生包的保留数据标志
区分真实重生与纯维度切换，避免同一次事件同时触发两套策略。排空期间发生的本地 `finalize` 或
`cancel` 只结束剩余表现，不改变已经按服务端标准时间线提交的权威回调。

`PAUSE`、`RESUME`、`FINALIZE`、`FINISH`、`CANCEL` 与字段增量都必须严格接在同一实例的上一
sequence 后，客户端收件箱拒绝乱序、重复、跨 generation 和尾随字节。客户端能力报告和屏幕状态
报告只能选择安全表现降级，不能改变受众、回调或字段结果。

若同一物理连接已经持有的最终投影包含随后撤权或只属于历史连接状态的节点，服务端不能用
`APPEND` 假装收回内容。它必须为同一实例发送更高 `cycle` 的原子 `REFRESH`，其中只保留当前
仍授权的节点、字段槽和锁定值；若当前没有任何可见节点，或无法安全构造该 `REFRESH`，则发送
不含文本与字段值的 `CANCEL` fail-closed。发送 `FINALIZE` 或 `FINISH` 前还必须确认连接中所有节点
继续授权，且最终字段值精确覆盖该连接仍保留的字段槽；终态控制之后不得再发送任何节点、字段或
控制包。

## 16. 诊断与错误隔离

### 16.1 诊断格式

每条消息诊断保留：

- 定义类型、资源 ID、来源数据包；
- RFC 6901 风格 JSON Pointer，例如 `/nodes/2/content/text/parts/0`；
- 稳定大写错误码；
- 面向数据包作者的有限长度说明。

Pointer 中的 `~` 和 `/` 分别转义为 `~0` 和 `~1`。单资源最多保留前 100 条详情，同时保留真实
总数和“详情已截断”状态；不能因为错误过多无限占用内存。

解析器当前可能产生：

```text
CONTRADICTORY_VALUE
CYCLIC_REFERENCE
DUPLICATE_NODE_ID
DUPLICATE_VALUE
DYNAMIC_STATIC_COMPONENT
EMPTY_DEFINITION
EMPTY_TEXT
INSECURE_POLICY
INVALID_BINDING
INVALID_COLOR
INVALID_ENUM
INVALID_IDENTIFIER
INVALID_LOCAL_ID
INVALID_LOCALE
INVALID_ONE_OF
INVALID_PATH
INVALID_SCORE_OBJECTIVE
INVALID_TEXT_COMPONENT
INVALID_TIME
JSON_SYNTAX
MISSING_REFERENCE
MISSING_REQUIRED
MULTIPLE_PRIMARY_TRACKS
OUT_OF_RANGE
PARSE_FAILED
RESOURCE_LIMIT
SOFT_LIMIT_EXCEEDED
TYPE_MISMATCH
UNAUTHORIZED_REFERENCE
UNKNOWN_KEY
UNKNOWN_NODE_TYPE
UNSUPPORTED_FORMAT
UNSUPPORTED_VALUE
```

跨资源编译还会补充：

```text
CONFLICTING_GAME_CONTEXT
CROSS_GAME_REFERENCE
DUPLICATE_DEFINITION
INVALID_DEFINITION
MISSING_EXTERNAL_RESOURCE
UNKNOWN_BINDING
UNTRUSTED_ENVELOPE
```

错误码用于稳定分类；说明文字可以改善，数据包逻辑不应解析英文说明来决定行为。

### 16.2 隔离、`required` 与静态降级

- 无效的可选 `text_effects` 或 `message_cues` 只禁用自己的 ID；其他合法消息和游戏定义继续
  发布。
- 引用无效预设、字段、游戏资源、Predicate 或 Function 的 cue 被整体禁用，不把一半有效的
  节点混入运行时。
- 成功解析后才发现外部引用错误的 cue 可以保留已经验证的 `static_fallback`。
- `required: true` 的 cue 必须声明 `game`。它无效且没有
  `fallback_satisfies_required: true` 的合法静态降级时，所属游戏进入开局阻断集合。
- JSON 自身损坏时不能相信文件里声称的 `required`、`game` 或 fallback。无法验证的 envelope
  以可选、无所属游戏隔离，并记录 `UNTRUSTED_ENVELOPE`，避免一个损坏文件错误阻断任意游戏。
- 超大但结构完整的 cue 仍可通过有界 envelope 扫描保留其 `required` 和 `game`；完整正文及
  静态降级不会从截断文本中重建。

普通玩家不看到红色解析弹窗。诊断面向日志、开发自检和后续主持人诊断工具；正式调用只返回
有限、稳定、不会泄露隐藏定义的失败原因。

## 17. `/reload`、generation 与覆盖

Minecraft 先按数据包优先级解析同一路径覆盖；注册表随后对整批候选进行严格编译。成功时发布
一个新的不可变 generation：

1. 新调用只读取新 generation；
2. 使用 `reload: finish_snapshot` 的活动实例继续使用调用时冻结的定义；
3. 可选消息资源的局部错误留在新 generation 的 `messageCatalog.disabled`，不回滚其他合法
   定义；
4. 非消息核心定义导致整批编译失败，或平台数据包 Reload 本身失败时，旧 generation 保留；
5. 定期状态同步和无变化的客户端能力上报不能触发可见闪烁、重播或重新播放字符音。

`finish_snapshot` 只冻结已经授权的内容和时间线，不允许旧实例在 Reload 后重新读取新的隐藏
字段或回调。服务端与客户端运行时接线已经落地，但仍须以多客户端 Minecraft 实机回归确认可见
排空、重生和跨维度行为；自动检查不能替代该验收。

冷启动时已经批准的冻结最终消息另有更严格边界：排队记录绑定批准时的 definition generation。
若离线接收者加入前发生成功 `/reload`，当前 generation 已不同，旧消息即使正文和依赖表面未变
也必须静默 fail-closed。若 generation 未变，则无需把导致 `continue` 降级的原依赖重新变回一致，
但仍必须在加入边界复核当前上下文、受众、节点与参数权限以及完整静态降级覆盖。

## 18. 完整多通道示例

下面两份文件可以一起放入示例数据包。第一份保存为：

```text
data/pixel_tzz/pixel_tzz_pro/text_effects/docs/full.json
```

```json
{
  "format_version": 1,
  "typewriter": {
    "character_interval": "45ms",
    "cursor": {
      "enabled": true,
      "glyph": "█",
      "color": "#77E6FF",
      "blink": true,
      "blink_period": "400ms"
    },
    "fresh_color": "#FFF3A6",
    "restore_after_characters": 3,
    "restore_mode": "gradient"
  },
  "fade": {
    "enter": "100ms",
    "hold": "1s",
    "exit": "250ms"
  },
  "character_sound": {
    "sound": "minecraft:block.note_block.hat",
    "category": "ui",
    "every_characters": 2,
    "skip_whitespace": true,
    "volume": 0.4,
    "pitch": 1.0
  },
  "max_character_sound_events": 128
}
```

第二份保存为：

```text
data/pixel_tzz/pixel_tzz_pro/message_cues/docs/full.json
```

它同时包含 Chat、Title、Subtitle、ActionBar、独立声音、回调、三种字段捕获、历史、静态降级
和资产声明：

```json
{
  "format_version": 1,
  "required": false,
  "policies": {
    "timing": {
      "clock": "presentation_time",
      "synchronization": "synchronized",
      "synchronized_wait": "2s",
      "late_arrival": "seek",
      "synchronization_timeout": "start_available"
    },
    "audience": {
      "target": {
        "source": "call_targets",
        "online_only": true
      },
      "evolution": "snapshot"
    },
    "concurrency": {
      "duplicate": "queue",
      "priority": 20
    },
    "capture": "on_display",
    "default_conflict": "queue"
  },
  "parameters": {
    "target": {
      "type": "player",
      "required": true,
      "sources": ["call_argument"],
      "on_error": "fail"
    }
  },
  "fields": {
    "target_name": {
      "capture": "on_first_field",
      "source": {
        "argument": "target"
      },
      "fallback": {
        "text": "未知玩家",
        "color": "gray"
      },
      "reservation": {
        "max_graphemes": 32,
        "estimated_width": 192,
        "max_lines": 1
      }
    },
    "live_score": {
      "capture": "per_field",
      "source": {
        "component": {
          "score": {
            "name": "@s",
            "objective": "pixel_tzz_demo"
          }
        }
      },
      "fallback": {
        "text": "0"
      }
    }
  },
  "nodes": [
    {
      "id": "chat",
      "type": "text",
      "channel": "chat",
      "content": {
        "text": {
          "parts": [
            {
              "component": {
                "text": "『任务开始』 ",
                "color": "aqua",
                "bold": true
              }
            },
            {
              "field": {
                "scope": "cue",
                "id": "target_name"
              }
            }
          ]
        },
        "effect": "pixel_tzz:docs/full",
        "speaker": {
          "kind": "player_parameter",
          "parameter": "target"
        },
        "character_sound_role": "primary"
      }
    },
    {
      "id": "title",
      "type": "text",
      "schedule": {
        "type": "cue_start",
        "offset": "250ms"
      },
      "channel": "title",
      "content": {
        "text": {
          "parts": [
            {
              "component": {
                "text": "准备行动",
                "color": "green"
              }
            }
          ]
        },
        "effect": "pixel_tzz:docs/full",
        "hold": "1.5s"
      }
    },
    {
      "id": "subtitle",
      "type": "text",
      "schedule": {
        "type": "cue_start",
        "offset": "500ms"
      },
      "channel": "subtitle",
      "content": {
        "text": {
          "parts": [
            {
              "component": {
                "text": "当前计分：",
                "color": "gray"
              }
            },
            {
              "field": {
                "scope": "cue",
                "id": "live_score"
              }
            }
          ]
        },
        "effect": {
          "preset": "pixel_tzz:docs/full",
          "overrides": {
            "typewriter": {
              "character_interval": "30ms",
              "cursor": {
                "color": "yellow"
              }
            }
          }
        },
        "hold": "2s"
      }
    },
    {
      "id": "action",
      "type": "text",
      "schedule": {
        "type": "after_node",
        "node": "subtitle",
        "offset": "1t"
      },
      "channel": "action_bar",
      "content": {
        "text": {
          "parts": [
            {
              "component": {
                "text": "按 ",
                "color": "gray"
              }
            },
            {
              "component": {
                "keybind": "key.inventory",
                "color": "yellow"
              }
            },
            {
              "component": {
                "text": " 查看物品栏",
                "color": "gray"
              }
            }
          ]
        },
        "hold": "1s"
      }
    },
    {
      "id": "sound",
      "type": "sound",
      "schedule": {
        "type": "cue_start",
        "offset": "4t"
      },
      "sound": "minecraft:block.note_block.pling",
      "category": "ui",
      "volume": 0.7,
      "pitch": 1.2
    },
    {
      "id": "callback",
      "type": "callback",
      "schedule": {
        "type": "cue_event",
        "event": "all_complete",
        "offset": "1t"
      },
      "function": "pixel_tzz:acceptance_3b/message_complete",
      "execution": "as_server",
      "location": "origin"
    }
  ],
  "history": {
    "title": {
      "parts": [
        {
          "component": {
            "text": "『任务提示回顾』"
          }
        }
      ]
    },
    "body": {
      "parts": [
        {
          "component": {
            "text": "目标："
          }
        },
        {
          "field": {
            "scope": "cue",
            "id": "target_name"
          }
        }
      ]
    },
    "task_source": "none",
    "timestamp_source": "completion",
    "audience": {
      "source": "call_targets"
    },
    "saved_fields": ["target_name", "live_score"],
    "replay_allowed": false
  },
  "static_fallback": {
    "channel": "chat",
    "component": {
      "text": "任务提示已完成",
      "color": "gray"
    },
    "fallback_satisfies_required": true
  },
  "assets": [
    {
      "type": "font",
      "id": "minecraft:default",
      "missing": "fallback",
      "fallback": "minecraft:uniform",
      "preload": true,
      "required_for_start": false
    },
    {
      "type": "sound",
      "id": "minecraft:block.note_block.pling",
      "missing": "silent",
      "preload": true,
      "required_for_start": false
    }
  ]
}
```

仓库中用于自动自检的事实样例还包括：

- `message_cues/acceptance/field_capture.json`：四个文字通道、声音、回调与三种 capture；
- `message_cues/acceptance/policy_matrix.json`：完整策略、历史、降级、资产与软限制；
- `message_cues/acceptance/restart_finalize.json`：快照受众的多目标
  `restart=finalize` 持久化与逐玩家消费验收；
- `text_effects/acceptance.json`：光标、新字覆盖色、渐变回色与字符音；
- `text_effects/decode_notice.json`：乱码、渐变、位移与脉冲。

## 19. 服务端调用与审阅入口

### 19.1 正式播放

稳定播放命令为：

```mcfunction
pixel_tzz_pro message play <message_id>
pixel_tzz_pro message play <message_id> with storage <storage> <path>
pixel_tzz_pro message play <message_id> to <targets>
pixel_tzz_pro message play <message_id> to <targets> with storage <storage> <path>
```

`Storage` 路径必须解析为唯一对象，键只能对应 cue 已声明的调用参数。`to` 只接受玩家选择器，
并冻结为本次调用的 `call_targets`。所有入口都会返回实例 UUID，供实例级控制命令使用。

当注册 cue 限制了 `game`、`policies.context.games`、`phases` 或 `tasks`，只有保留
`PermissionContext.Type.SYSTEM` 来源的服务端控制台或数据包函数可以在命令末尾显式追加
`bypass context`：

```mcfunction
pixel_tzz_pro message play <message_id> bypass context
pixel_tzz_pro message play <message_id> with storage <storage> <path> bypass context
pixel_tzz_pro message play <message_id> to <targets> bypass context
pixel_tzz_pro message play <message_id> to <targets> with storage <storage> <path> bypass context
```

这是唯一稳定顺序：`bypass context` 始终位于现有播放形态末尾。绕过只跳过已注册的游戏、阶段
和任务上下文验证；参数解析、受众最小权限、在线/空受众、资源门禁、权限、并发、去重、冷却、
队列和回调规则仍照常执行。排队实例会冻结这项选择，实际启动前不再因游戏上下文复验被移除。

普通玩家、非主持人 OP 和以玩家权限执行命令的当前主持人都不能使用该后缀。数据包函数经过
`execute as` / `execute at` 后仍以服务端函数来源接受授权，但选择出的实体和坐标仍只作为
invoker/origin 解析输入，不能把玩家命令权限提升为绕过权限。旧存档没有 `bypass_context` 时
按 `false` 读取；服务端回调宏会以布尔字段 `context_bypassed` 明确记录本实例是否使用过绕过。

### 19.2 目录、审阅、控制与主持人预览

```mcfunction
pixel_tzz_pro message list [page]
pixel_tzz_pro message inspect <message_id>
pixel_tzz_pro message preview <message_id>
pixel_tzz_pro message preview <message_id> with storage <storage> <path>
```

`list` 每页最多 12 项，并同时列出可用和被隔离的 cue。`inspect` 只输出有界的来源、通道、
参数类型、受众来源、上下文、资源数量和诊断摘要，不输出完整规范 JSON、敏感参数值或回调函数名。

`preview` 始终只投影给当前在线主持人，没有 `to` 形态。预览器会自动移除正式回调、历史写入、
游戏上下文限制、去重/刷新/排队关系和控制分组；因此主持人预览不使用正式播放的显式
`bypass context` 后缀，也不会干扰同 ID 的正式实例。

活动演出可以按实例、演出 ID、注册控制分组或当前在线目标玩家控制：

```mcfunction
pixel_tzz_pro message control instance <instance_uuid> <pause|resume|complete|cancel>
pixel_tzz_pro message control cue <message_id> <pause|resume|complete|cancel>
pixel_tzz_pro message control group <group> <pause|resume|complete|cancel>
pixel_tzz_pro message control target <player> <pause|resume|complete|cancel>
```

`group` 同时匹配 `policies.control_groups` 和并发 `groups`。`target` 匹配当前演出冻结受众中包含
该在线玩家的全部实例；它控制匹配到的完整实例，不会只在单个客户端伪造暂停或完成。需要控制
离线目标时使用实例、cue 或 group 入口。重复控制保持幂等，完成走正常完成回调，取消只走中断
回调。

共同安全边界：

- 只接受已编译 cue ID、合法玩家选择器和有界 Storage 参数对象；
- 不接受临时正文、临时函数、客户端指定受众或未声明参数；
- `execute as` / `execute at` 的调用上下文应成为 invoker 和 origin；
- 普通玩家不能直接调用；非主持人 OP 不因原版权限等级自动取得 V3B 管理权；
- 主持人预览不能执行正式回调或写历史。

### 19.3 权威生命周期 `message_hooks`

Game、Phase、Task、Task Event、Flow、Readiness 和 Role 可以在自己的定义中声明
`message_hooks`。Hook 只负责在权威事务成功提交后调用一条已注册 cue；正式文案、受众、
动画、排队、历史和回调仍全部由该 cue 自己声明，模组不会硬编码游戏文案。

最简写法直接填写 cue ID；需要固定参数时使用对象写法：

```json
{
  "message_hooks": {
    "start": "pixel_tzz:lifecycle/game_start",
    "end": {
      "cue": "pixel_tzz:lifecycle/game_end",
      "arguments": {
        "message": "本局游戏已结束",
        "tone": "success"
      }
    }
  }
}
```

Readiness 位于 Game 的 `readiness` 对象内；Task Event 位于 Task 的单个 `events[]` 条目内：

```json
{
  "readiness": {
    "message_hooks": {
      "complete": "pixel_tzz:lifecycle/all_ready"
    }
  }
}
```

每类定义只接受与自己生命周期相符的事件：

| 挂载位置 | 允许事件 |
|---|---|
| Game | `start`、`pause`、`resume`、`end` |
| Phase | `enter`、`exit` |
| Task | `start`、`complete`、`interrupt` |
| Task `events[]` | `trigger` |
| Flow | `start`、`player_complete`、`all_complete` |
| Game `readiness` | `complete` |
| Role | `initialization`、`role_changed` |

一个拥有者最多声明 8 个 Hook；每个 Hook 最多声明 32 个固定参数。固定参数名必须匹配
`[a-z][a-z0-9_.-]{0,63}`，值只能是字符串、数字或布尔值，编译后都按字符串传入 cue。
Hook 引用的 cue 必须显式属于同一个 Game；缺失或跨 Game 引用会产生精确到 Hook 路径的诊断。

服务端会在调用前追加当前事务的权威事实。所有事件都提供 `hook_event`，并在事实存在时提供
以下参数：

- 公共上下文：`game_id`、`game_instance_id`、`phase_id`、`state_revision`、`server_tick`、
  `game_elapsed_ticks`、`initiator`；
- Phase：`previous_phase_id`、`phase_id`；
- Task：`task_id`、`task_instance_id`、`task_kind`、`task_elapsed_ticks`、`result_id`、
  `result_semantic`、`interrupt_reason`；
- Task Event：`event`、`event_state`、`event_player`、`player`、`player_name`；
- Flow / Readiness：`flow_id`、`flow_version`、`flow_instance_id`、`source_action_id`、
  `initiated_by`、`completed`、`total`、`readiness_instance_id`；
- Flow `player_complete`：另提供本次完成者的 `player`、`player_name` 和 `target`；
- Role：`player`、`player_name`、`target`、`previous_role_id`、`role_id`、`initialization`；
- Game 结束或暂停：`end_status`、`end_reason`、`pause_reason`。

这些参数都是可选事实：某次事件没有对应事实时不会伪造空值。固定参数先复制，服务端自动参数
后覆盖；数据包不能在 `arguments` 中声明上述保留名，也不能伪造权威 ID、玩家、进度或结果。
cue 仍须在自己的 `parameters` 中声明实际要读取的参数及类型。

Hook 只在事务成功提交后的业务上升沿触发。失败、拒绝、重试、普通状态投影、定期同步、
Reload 恢复、冷启动恢复和页面重绘都不会补播。强制流程与任务时间线会把 Hook 实际引用的
cue、效果和必要依赖一起冻结，因此活动实例不会在 `/reload` 后偷读另一代文案或回调。

演出层与业务层严格隔离：未声明 Hook 时没有副作用；cue 被隔离、受众为空、参数不合法、
资源门禁失败或播放运行时抛出异常时，只记录有界服务端警告并跳过该演出，不向普通玩家弹出
红色页面，也不能回滚、重复提交或改变已经完成的游戏、阶段、任务、流程、准备或身份事务。

## 20. 兼容、偏好与当前验证边界

### 20.1 Chat 与 Chat Heads

- 动态 Chat 保持一个真实原版系统消息条目并尝试原位换帧；
- 客户端只上报 `IN_PLACE` 或 `STATIC_ONLY` 表现能力，这个上报不能提升权限；
- 原位更新不可用时只插入一次最终静态文本，不逐帧刷屏；
- `speaker` 默认 `system`，玩家/实体视觉来源默认关闭；
- 显式启用玩家来源时只使用服务端验证的 UUID 和显示名；
- Chat Heads 一类仅装饰原版条目的模组理论上不应受影响，但仍属于待做的 Minecraft 兼容矩阵，
  当前自动检查不能代替实机结论。

### 20.2 玩家偏好

当前客户端偏好安全默认值为：

| 偏好 | 默认 |
|---|---:|
| 动画速度 | `1.0` |
| 减少动态 | `false` |
| 字符音 | `true` |
| 字符音音量 | `1.0` |
| 光标闪烁 | `true` |
| 高对比度 | `false` |

这些偏好只能让已授权演出更快、更安静或更易读；不能隐藏最终正文、改变受众、重新解析字段、
延迟回调或跳过流程。手动补全还必须同时满足 cue 的
`accessibility.allow_manual_complete: true`；未声明时默认不允许。

### 20.3 推荐自动检查

资源和编译闭环：

```powershell
.\gradlew.bat messageDefinitionSelfCheck messageHookFreezeClosureSelfCheck messagePolicySchemaSelfCheck messageFixtureSelfCheck --no-daemon
```

v12、时间线、视觉效果、命令和服务端实例权威：

```powershell
.\gradlew.bat protocolV12SelfCheck messageTextTimelineSelfCheck messageFrameTimelineSelfCheck messagePrelayoutDurationSelfCheck messageVisualEffectsSelfCheck messageInstanceAuthoritySelfCheck messageCommandsSelfCheck messageRuntimePersistenceMapperSelfCheck messageRegisteredEntitySpeakerSelfCheck --no-daemon
```

提交前还应执行：

```powershell
git diff --check
.\gradlew.bat check --no-daemon
```

自动检查通过只证明解析、编译和纯逻辑边界，不证明真实 Minecraft Chat 条目、字体、声音、
多人同步、低 FPS、Reload、Chat Heads 或页面遮挡已经实机通过。

## 21. 当前实现状态

V3B 已于 2026-08-04 完成：

- `text_effects`、`message_cues` 的资源格式、默认值、引用与隔离契约已冻结并实现；
- 三种动态字段捕获、光标覆盖色、新字覆盖色和瞬切/渐变回色已通过自动检查与实机验收；
- 编译、v12 payload、FORMAT 2、客户端收件箱、服务端权威实例、恢复和生命周期账本均已落地；
- Chat、Title、Subtitle、ActionBar、声音、回调、历史、预览、命令与本地偏好均已逐项验收；
- `final_only` 握手、撤权清理、静态降级、冷启动、重连、Reload 和四种重启策略均有自动与实机证据；
- Chat Heads 兼容项已按验收手册通过；其他第三方聊天重写仍只在对应组合实测后形成兼容承诺；
- 完整自动门与五客户端记录见 [`MILESTONE-3B-TESTING.md`](MILESTONE-3B-TESTING.md)；
- 功能提交 `ffb1309` 已通过 PR #5 合并，主线合并提交为 `d371dff`。
