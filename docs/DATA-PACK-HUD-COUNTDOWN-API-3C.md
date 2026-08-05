# Pixel TZZ Pro 数据包 HUD 与开局倒计时 API（3C）

状态：**契约与实现已冻结，自动验证已通过；当前代码已接受本文 V3C 资源，等待五客户端实机验收**

- 适用目标 Minecraft：`26.2`
- 当前模组版本：`0.1.0`
- 当前游戏 API：`api_version: 4`
- 当前网络协议：v13
- 当前世界状态：Schema v5
- 本文资源格式：`format_version: 1`
- 文档冻结日期：2026-08-04

本文是 V3C 常驻 HUD 信息坞、客户端可调权限和正式开局倒计时的数据包契约。当前 parser、网络、
世界状态、客户端设置与示例数据包均已接入这些字段；实机视觉、声音、同步与操作手感仍以
[`MILESTONE-3C-TESTING.md`](MILESTONE-3C-TESTING.md) 的五客户端结果为准。

V3A 玩家终端、V3B 动态消息与 2D 任务时间线继续遵循：

- [`DATA-PACK-PLAYER-TERMINAL-API-3A.md`](DATA-PACK-PLAYER-TERMINAL-API-3A.md)
- [`DATA-PACK-MESSAGE-API-3B.md`](DATA-PACK-MESSAGE-API-3B.md)
- [`DATA-PACK-TASK-API-2D.md`](DATA-PACK-TASK-API-2D.md)

产品边界、视觉线框、实施分段与完成定义见
[`MILESTONE-3C-PLAN.md`](MILESTONE-3C-PLAN.md)。

## 1. 设计边界

V3C 数据包 API 注册四类资源：

- HUD Component：一个可复用、安全、有界的显示组件；
- HUD Layout：一个常驻信息坞或倒计时覆盖层的根布局；
- HUD Profile：一个 Game 的路由、默认布局和客户端调整权限；
- Countdown：开局倒计时的权威时长、限制、掉线策略、表现和回调。

每名玩家同一时刻最多获得一个常驻根布局。布局内部可以组合多个组件，但不能让组件脱离信息坞
独立锚定屏幕。倒计时覆盖层是唯一中央例外。

数据包不能通过本文 API：

- 执行客户端脚本、Shader、网络请求或任意文件读取；
- 在 HUD 内创建按钮、输入框、滚动内容或游戏操作；
- 请求未授权字段、任意 Selector 或任意 NBT；
- 跳过、快进或依赖客户端完成权威倒计时；
- 修改其他模组或原版 HUD；
- 用 HUD 设置执行 `mcfunction`。

## 2. 资源路径、ID 与覆盖

```text
data/<namespace>/pixel_tzz_pro/hud_components/<path>.json
data/<namespace>/pixel_tzz_pro/hud_layouts/<path>.json
data/<namespace>/pixel_tzz_pro/hud_profiles/<path>.json
data/<namespace>/pixel_tzz_pro/countdowns/<path>.json
```

路径决定稳定资源 ID：

```text
data/pixel_tzz/pixel_tzz_pro/hud_components/gameplay/task_title.json
→ pixel_tzz:gameplay/task_title

data/pixel_tzz/pixel_tzz_pro/hud_layouts/gameplay/default.json
→ pixel_tzz:gameplay/default

data/pixel_tzz/pixel_tzz_pro/hud_profiles/main.json
→ pixel_tzz:main

data/pixel_tzz/pixel_tzz_pro/countdowns/opening/default.json
→ pixel_tzz:opening/default
```

每个文件必须声明：

```json
{"format_version": 1}
```

Minecraft 数据包优先级先决定同路径最终资源。高优先级文件完整覆盖低优先级同 ID 文件，不做
字段级深合并。引用不会自动退回低优先级被覆盖版本。

严格解析规则：

- 根必须是对象；
- 重复键、尾随内容、未知键和错误类型产生诊断；
- 资源 ID 必须显式包含命名空间；
- 本地 ID 匹配 `[a-z][a-z0-9_.-]{0,63}`；
- 所有组件、布局、降级和路由引用必须形成有限无环闭包；
- 同一资源无效时隔离该资源及其引用者；完整 generation 是否发布由 required 闭包决定；
- 失败 Reload 保留上一代有效快照。

颜色只接受 `#RRGGBB` 或 `#AARRGGBB`；八位格式的前两位是 Alpha。透明度还要与 Profile、
玩家偏好和全局可访问性范围相乘，数据包不能用 Alpha 0 绕过 `allow_hide: false`。时间复用 V3B
格式：整数 Tick 或带 `t`、`s`、`m` 后缀的字符串；内部最终统一为服务端 Tick。

## 3. Game 接入

使用 V3C 的 Game 必须声明 `api_version: 4`：

```json
{
  "format_version": 1,
  "api_version": 4,
  "content_version": 4,
  "name": {"text": "全员逃走中"},
  "initial_phase": "pixel_tzz:setup",
  "default_role": "pixel_tzz:runner",
  "default_life_state": "pixel_tzz:alive",
  "hud_profile": "pixel_tzz:main",
  "opening_countdown": {
    "definition": "pixel_tzz:opening/default",
    "required": true
  }
}
```

新增字段：

| 字段 | 必填 | 默认 | 说明 |
|---|---|---|---|
| `hud_profile` | 否 | 无 HUD | HUD Profile ID |
| `opening_countdown` | 否 | 2D 立即开局 | 倒计时引用对象 |
| `opening_countdown.definition` | 对象存在时是 | 无 | Countdown ID |
| `opening_countdown.required` | 否 | `true` | 无效时阻断批准；`false` 时明确降级为立即开局 |

兼容规则：

- 现有 `api_version: 3` Game 不声明上述字段，行为不变；
- `api_version: 3` 声明上述未知字段仍严格报错；
- `api_version: 4` 可以同时省略两项；
- `required: true` 的倒计时资源、布局或必需引用无效时，主持人批准失败；
- `required: false` 仅允许倒计时整体降级为立即开局，不允许使用半个有效定义；
- HUD Profile 无效时 HUD 关闭并向主持人诊断，不阻断游戏；若倒计时必需布局引用同一无效组件，
  仍按倒计时 required 规则阻断批准。

## 4. HUD Profile

推荐结构：

```json
{
  "format_version": 1,
  "default_layout": "pixel_tzz:gameplay/default",
  "routes": [
    {
      "id": "active_task",
      "tier": "task",
      "priority": 100,
      "when": {
        "task_statuses": ["starting", "running", "settling", "interval"]
      },
      "layout": "pixel_tzz:gameplay/task",
      "audience": {"participants": true}
    },
    {
      "id": "hunter_waiting",
      "tier": "context",
      "priority": 50,
      "when": {
        "roles": ["pixel_tzz:hunter"],
        "phases": ["pixel_tzz:warmup"]
      },
      "layout": "pixel_tzz:gameplay/hunter_waiting"
    }
  ],
  "client_policy": {
    "allow_hide": true,
    "default_anchor": "bottom_right",
    "allowed_anchors": ["bottom_right", "bottom_left"],
    "offset": {"max_x": 96, "max_y": 96},
    "scale": {"minimum": 0.75, "default": 1.0, "maximum": 1.25},
    "opacity": {"minimum": 0.55, "default": 0.92, "maximum": 1.0},
    "allow_component_management": true,
    "tab_collision": "dim"
  }
}
```

顶层字段：

| 字段 | 必填 | 默认 | 说明 |
|---|---|---|---|
| `format_version` | 是 | 无 | 当前只接受 `1` |
| `default_layout` | 否 | 无 | 无路由命中时使用 |
| `routes` | 否 | `[]` | 有序书写但不按书写顺序决胜 |
| `client_policy` | 否 | 安全默认值 | 玩家本地调整的全局包络 |

`default_layout` 不等于强制显示。布局经过受众投影后没有组件时，客户端不绘制空外框。

## 5. 路由

每条路由结构：

```json
{
  "id": "runner_task",
  "tier": "task",
  "priority": 120,
  "when": {},
  "layout": "pixel_tzz:gameplay/runner_task",
  "audience": {}
}
```

| 字段 | 必填 | 约束 |
|---|---|---|
| `id` | 是 | Profile 内唯一 local ID |
| `tier` | 是 | `task` 或 `context` |
| `priority` | 是 | 有界整数；数值越大越优先 |
| `when` | 否 | 缺省表示该 tier 的通用候选 |
| `layout` | 是 | `surface: dock` 的 HUD Layout |
| `audience` | 否 | V3B 同构受众对象 |

固定选择顺序：

1. 活动 Countdown 的 `surface: countdown` Layout；
2. `tier: task` 中最高唯一优先级；
3. `tier: context` 中最高唯一优先级；
4. `default_layout`；
5. clear。

同一 tier 有两个最高优先级候选可能同时命中属于编译错误。编译器必须分析静态可证明冲突；
运行时仍遇到无法静态证明的平局时 fail closed，清空该 Profile 投影并记录诊断，不能选 JSON
第一项。

### 5.1 `when`

允许字段：

```json
{
  "games": ["pixel_tzz:main"],
  "phases": ["pixel_tzz:running"],
  "tasks": ["pixel_tzz:mission/power"],
  "task_kinds": ["warmup", "main"],
  "task_statuses": ["starting", "running", "settling", "interval"],
  "roles": ["pixel_tzz:runner"],
  "teams": ["pixel_tzz:participants"],
  "life_states": ["pixel_tzz:alive"],
  "host": false,
  "online": true,
  "all": [],
  "any": [],
  "not": {}
}
```

同一对象的普通字段取 AND；数组内部取 OR。`all`、`any`、`not` 允许有限嵌套。条件只能读取
编译器批准的服务端上下文，不能运行命令、函数、任意 Predicate 或客户端脚本。

`tier: task` 必须至少约束当前任务存在、任务 ID、kind 或 status 之一；没有当前任务时不会命中。
`tier: context` 不允许通过任务条件伪装成更低优先级任务路由。

## 6. HUD Layout

推荐 dock Layout：

```json
{
  "format_version": 1,
  "surface": "dock",
  "audience": {"participants": true},
  "root": "pixel_tzz:gameplay/root_normal",
  "compact_root": "pixel_tzz:gameplay/root_compact",
  "summary_root": "pixel_tzz:gameplay/root_summary",
  "degradation_layout": "pixel_tzz:gameplay/minimal",
  "size": {
    "minimum_width": 132,
    "preferred_width": 212,
    "maximum_width": 244,
    "maximum_height": 124,
    "growth": "up"
  },
  "transition": {
    "enter": "rise_fade",
    "change": "crossfade_values",
    "exit": "fade",
    "enter_sound": {
      "event": "minecraft:block.note_block.pling",
      "volume": 0.4,
      "pitch": 1.1
    },
    "change_sound": {
      "event": "minecraft:block.note_block.hat",
      "volume": 0.2,
      "pitch": 1.0
    }
  }
}
```

Countdown Layout：

```json
{
  "format_version": 1,
  "surface": "countdown",
  "root": "pixel_tzz:countdown/root",
  "compact_root": "pixel_tzz:countdown/root_static",
  "size": {
    "minimum_width": 132,
    "preferred_width": 176,
    "maximum_width": 224,
    "maximum_height": 104,
    "growth": "center"
  },
  "transition": {
    "enter": "focus_fade",
    "change": "crossfade_values",
    "exit": "fade"
  }
}
```

字段：

| 字段 | 必填 | 说明 |
|---|---|---|
| `surface` | 是 | `dock` 或 `countdown` |
| `audience` | 否 | Layout 级受众 |
| `root` | 是 | 正常根 Component ID |
| `compact_root` | 否 | 空间或简化动态时的根 |
| `summary_root` | 否 | 进一步摘要根 |
| `degradation_layout` | 否 | 当前 Layout 无法合法布局时使用 |
| `size` | 是 | 参考画布逻辑尺寸约束 |
| `transition` | 否 | 受控动画枚举，不是脚本 |

约束：

- `dock` 只可被 HUD Profile 引用；`countdown` 只可被 Countdown 引用；
- `growth: up` 是首版 dock 唯一增长方向；Countdown 固定 `center`；
- degradation 引用必须无环且深度有界；
- `compact_root` 与 `summary_root` 不是自动缩放后的 `root`，而是数据包明确注册的语义摘要；
- `change` 只用于真实结构/值变化；相同快照、心跳和时钟插值不触发；
- `full`、`simplified`、`static` 动态偏好会安全降级 transition，数据包不能强制完整动画。

`transition` 的可选声音字段：

| 字段 | 触发条件 | 结构 |
|---|---|---|
| `enter_sound` | 正式投影从无到有时一次 | `event`、`volume`、`pitch` |
| `change_sound` | 正式投影发生有意义的结构或值变化时一次 | `event`、`volume`、`pitch` |

`event` 必须是声音事件 ID；`volume` 范围为 `0..4`，`pitch` 范围为 `0.5..2`。声音只在
客户端播放，不执行函数或回调。心跳、计时器/进度插值、Resync、F1/Screen 恢复和相同快照不应
重播声音。最终播放音量还会乘以玩家自己的 HUD 或 Countdown 音量；玩家始终可以把两个通道
分别静音，数据包不能设置最低本地音量。

## 7. HUD Component 公共字段

每个 Component 文件：

```json
{
  "format_version": 1,
  "type": "text",
  "priority": "primary",
  "audience": {},
  "visible_when": {},
  "bindings": {},
  "style": {},
  "client_control": {
    "visibility": "allow_hide",
    "compact": "allow",
    "reorder_group": "details"
  }
}
```

公共字段：

| 字段 | 必填 | 默认 | 说明 |
|---|---|---|---|
| `format_version` | 是 | 无 | 当前只接受 `1` |
| `type` | 是 | 无 | 组件类型 |
| `priority` | 否 | `primary` | `critical`、`primary`、`secondary`、`decorative` |
| `audience` | 否 | 继承上层交集 | 组件受众 |
| `visible_when` | 否 | 始终 | 受限服务端条件 |
| `bindings` | 否 | `{}` | 具名类型化字段 |
| `style` | 否 | 安全默认 | 颜色、间距、边框、字重等 |
| `client_control` | 否 | 全部锁定 | 客户端组件管理权限 |

`priority` 只用于空间降级，不改变路由和权限。`critical` 组件若在合法尺寸内仍无法显示，必须
进入 degradation layout 或清空并诊断，不能裁成半行。

### 7.1 `client_control`

```json
{
  "visibility": "locked_shown",
  "compact": "locked",
  "reorder_group": null
}
```

- `visibility`：`locked_shown` 或 `allow_hide`；
- `compact`：`locked` 或 `allow`；
- `reorder_group`：省略/`null` 表示不可重排；同一非空组内才允许玩家改变顺序；
- Profile 的 `allow_component_management: false` 会关闭所有组件级调整；
- 客户端不能把 `locked_shown` 改为隐藏，也不能跨组移动；
- 权限后来收紧时，本地旧偏好保留在配置中但不生效；重新放宽后可以恢复。

### 7.2 `style`

公共 Style 只接受组件类型明确允许的下列字段子集：

```json
{
  "text_color": "#F1F5F7",
  "secondary_text_color": "#AAB4BE",
  "background_color": "#E61A232E",
  "border_color": "#40505F",
  "track_color": "#40505F",
  "fill_color": "#55D7E6",
  "marker_color": "#F2C14E",
  "opacity": 1.0,
  "font_weight": "normal",
  "padding": {"left": 0, "top": 0, "right": 0, "bottom": 0},
  "gap": 0,
  "width": null,
  "height": null
}
```

- `font_weight` 为 `normal` 或 `bold`；
- padding、gap、width、height 使用参考画布逻辑像素并受硬上限约束；
- `null` 表示由内容测量；固定尺寸不能小于 critical 内容的合法最小尺寸；
- 不支持任意 CSS、阴影表达式、负间距、变换矩阵和自定义 Shader；
- 组件专用顶层字段优先于同义 Style，Schema 不允许同时声明两套冲突值；
- 未列入对应组件白名单的 Style 键按未知键报错，不静默忽略。

## 8. 组件类型

### 8.1 `text`

```json
{
  "format_version": 1,
  "type": "text",
  "priority": "primary",
  "bindings": {
    "task_name": {
      "value_type": "component",
      "source": {"type": "task_fact", "value": "name"},
      "on_missing": {"mode": "hide_component"}
    }
  },
  "text": {
    "parts": [
      {"component": {"text": "当前任务  ", "color": "gray"}},
      {"field": {"scope": "component", "id": "task_name"}}
    ]
  },
  "layout": {
    "alignment": "left",
    "max_lines": 2,
    "overflow": "wrap"
  }
}
```

`text` 使用 V3B §10 的本地化模板与 part 结构，但 HUD 不执行打字效果、字段 capture 或消息历史。
允许 `overflow`：`wrap`、`ellipsis`、`scale_once`。`scale_once` 只能在可读下限内整体选择一个
静态字号，不得随值刷新来回缩放。

### 8.2 `image`

```json
{
  "format_version": 1,
  "type": "image",
  "texture": "pixel_tzz:textures/gui/hud/task.png",
  "size": {"width": 16, "height": 16},
  "tint": "#FFFFFFFF",
  "fit": "contain",
  "alt": {"text": "任务"}
}
```

只允许已加载资源 ID，不允许 URL 或文件路径。`alt` 必填且进入讲述人最终稳定描述。资源缺失按
组件 `on_asset_missing` 的 `hide_component`、`placeholder` 或 `degradation_layout` 处理。

### 8.3 `player_head`

```json
{
  "format_version": 1,
  "type": "player_head",
  "bindings": {
    "player": {
      "value_type": "player",
      "source": {"type": "player_fact", "value": "self"},
      "on_missing": {"mode": "hide_component"}
    }
  },
  "player": {"field": "player"},
  "size": 16,
  "layers": ["base", "hat"],
  "offline_style": "grayscale"
}
```

头像必须绘制基础层和帽子层。离线只灰度头像；在线绿点不是默认组成，除非数据包用独立 Badge
注册且玩家获准看到在线状态。

### 8.4 `counter`

```json
{
  "format_version": 1,
  "type": "counter",
  "bindings": {
    "alive": {
      "value_type": "integer",
      "source": {"type": "statistic", "id": "pixel_tzz:alive_runners"},
      "on_missing": {"mode": "placeholder", "value": {"text": "--"}}
    }
  },
  "label": {"text": "存活"},
  "value": {"field": "alive"},
  "suffix": {"text": " 人"}
}
```

### 8.5 `badge`

```json
{
  "format_version": 1,
  "type": "badge",
  "bindings": {
    "state": {
      "value_type": "identifier",
      "source": {"type": "player_fact", "value": "life_state"},
      "on_missing": {"mode": "hide_component"}
    }
  },
  "value": {"field": "state"},
  "variants": {
    "pixel_tzz:alive": {"label": {"text": "存活"}, "color": "#5FD18A"},
    "pixel_tzz:captured": {"label": {"text": "已捕获"}, "color": "#D94B5B"}
  },
  "unknown": {"mode": "hide_component"}
}
```

未声明 identifier 不回退显示机器键。

### 8.6 `progress`

```json
{
  "format_version": 1,
  "type": "progress",
  "bindings": {
    "current": {
      "value_type": "decimal",
      "source": {"type": "task_fact", "value": "progress_current"},
      "on_missing": {"mode": "hide_component"}
    },
    "maximum": {
      "value_type": "decimal",
      "source": {"type": "task_fact", "value": "progress_maximum"},
      "on_missing": {"mode": "hide_component"}
    }
  },
  "current": {"field": "current"},
  "maximum": {"field": "maximum"},
  "orientation": "horizontal",
  "segments": 0,
  "label": "current_over_max",
  "interpolation": "smooth"
}
```

`maximum <= 0` 按 missing 处理。视觉值在 `0..maximum` 内钳制，原始异常值只进主持人诊断。
平滑插值不会延迟服务端完成或伪造超过当前权威值的进度。

### 8.7 `timer`

```json
{
  "format_version": 1,
  "type": "timer",
  "bindings": {
    "clock": {
      "value_type": "clock",
      "source": {"type": "clock", "value": "task"},
      "on_missing": {"mode": "hide_component"}
    }
  },
  "clock": {"field": "clock"},
  "direction": "elapsed",
  "format": "m:ss",
  "paused_marker": {"text": "暂停"}
}
```

玩家可见格式只允许 `m:ss`、`h:mm:ss` 和 `localized_duration`；原始 Tick 仅允许主持人原始诊断，
不能作为正式 HUD 文案。`direction` 为 `elapsed` 或 `remaining`。

### 8.8 `separator`

```json
{
  "format_version": 1,
  "type": "separator",
  "orientation": "horizontal",
  "thickness": 1,
  "color": "#40505F"
}
```

### 8.9 `background`

```json
{
  "format_version": 1,
  "type": "background",
  "child": "pixel_tzz:gameplay/content",
  "fill": "#E61A232E",
  "border": {"color": "#40505F", "width": 1},
  "padding": {"left": 8, "top": 7, "right": 8, "bottom": 7}
}
```

### 8.10 `row` 与 `column`

```json
{
  "format_version": 1,
  "type": "column",
  "children": [
    "pixel_tzz:gameplay/task_title",
    "pixel_tzz:gameplay/task_progress",
    "pixel_tzz:gameplay/detail_row",
    "pixel_tzz:gameplay/spine"
  ],
  "gap": 4,
  "alignment": "stretch"
}
```

`row` 的 `alignment` 为垂直对齐，`column` 为水平对齐。子项消失后相邻 gap 一并收起，不能留下
空白格。可选 `weights` 只分配剩余空间，不允许负值。

### 8.11 `overlay`

```json
{
  "format_version": 1,
  "type": "overlay",
  "layers": [
    "pixel_tzz:countdown/rail",
    "pixel_tzz:countdown/number"
  ],
  "alignment": "center"
}
```

Overlay 层数有界，不能让子组件超出父组件测量框。它不是屏幕自由坐标系统。

### 8.12 `repeat`

```json
{
  "format_version": 1,
  "type": "repeat",
  "bindings": {
    "objectives": {
      "value_type": "list",
      "item_type": "object",
      "source": {"type": "task_fact", "value": "public_objectives"},
      "on_missing": {"mode": "hide_component"}
    }
  },
  "items": {"field": "objectives"},
  "item_component": "pixel_tzz:gameplay/objective_item",
  "layout": "column",
  "maximum_items": 3,
  "overflow_component": "pixel_tzz:gameplay/objective_overflow"
}
```

服务端先裁剪每个 item 的字段和受众，再发送有界列表。`item_component` 使用只读 `item.*` scope。
超过 `maximum_items` 时必须显示已注册 overflow 摘要或明确隐藏剩余项；不提供滚动。

## 9. Bindings

绑定结构：

```json
{
  "value_type": "integer",
  "source": {},
  "audience": {},
  "sensitive": false,
  "critical": false,
  "on_missing": {"mode": "hide_field"}
}
```

`value_type`：

```text
string, integer, decimal, boolean, identifier, player, component,
duration, clock, list, object
```

允许来源：

| `source.type` | 主要字段 | 说明 |
|---|---|---|
| `game_fact` | `value` | 游戏名称、状态、暂停、公开计时等 |
| `phase_fact` | `value` | 当前阶段 ID、名称及公开元数据 |
| `task_fact` | `value` | 当前任务名称、正文、状态、进度、公开目标等 |
| `player_fact` | `value` | self、身份、队伍、生存状态及明确公开事实 |
| `event` | `id`、`value` | 已授权事件数据 |
| `statistic` | `id` | 已授权统计 |
| `result` | `value` | 当前任务已公开结果 |
| `clock` | `value` | `game`、`task`、`interval`、`warmup`、`countdown` |
| `player_data` | `field` | V3A Player Data；必须允许 `hud` surface |
| `score` | `objective`、`holder` | 编译器批准的计分板读取 |
| `storage` | `storage`、`path` | 编译器批准的安全 Storage 路径 |
| `entity_data` | `target`、`path` | 受限目标与安全 Data 路径 |

所有来源在服务端解析。数据包不能把 `source` 定义发送给客户端让客户端自行查询。

### 9.1 Player Data 表面

V3C 为 V3A `player_data.surfaces` 增加目标值：

```json
{"surfaces": ["terminal", "dynamic_message", "hud"]}
```

只有显式包含 `hud` 的字段可以被 HUD 绑定。字段还必须通过自身、组件、Layout、Route 与实时
上下文受众交集。未下发值不会因为玩家打开设置预览而出现。

### 9.2 `on_missing`

```jsonc
{"mode": "hide_field"}
{"mode": "hide_component"}
{"mode": "placeholder", "value": {"text": "暂无数据"}}
{"mode": "degradation_layout", "layout": "pixel_tzz:gameplay/minimal"}
{
  "mode": "retain_stale",
  "ttl": "5s",
  "indicator": {"text": "数据待同步"}
}
```

规则：

- `placeholder` 必须与 `value_type` 可显示类型匹配，不显示字段 ID、路径或异常文本；
- `retain_stale` 只允许 `sensitive: false` 且 `critical: false`；
- `countdown` clock、权限、当前关键任务状态和身份安全字段禁止 `retain_stale`；
- TTL 到期后继续执行该绑定声明的终止降级，不能无限保留；
- 权限撤销绕过 `on_missing` 并立即 clear；
- `degradation_layout` 必须与当前 surface 相同且引用闭包合法。

## 10. Audience 与最小投影

Route、Layout、Component、Binding、数据源定义与实时游戏上下文均可收窄受众。有效受众为全部
层级交集，任何层级不能扩大上一层。

受众对象复用 V3B §7.2 的服务端解析语义，支持主持人、参与者、身份、队伍、生存状态、冻结
UUID 集、在线要求与排除。非主持人 OP 不获得隐式主持人权限。

服务端对每名玩家独立完成：

1. 选择唯一根 Layout；
2. 展开无环 Component 闭包；
3. 校验每个 Component、Binding、条件与数据源受众；
4. 解析类型化值；
5. 应用 missing 与降级；
6. 删除无权组件和字段；
7. 只编码最终布局节点、允许的样式、值和客户端权限。

客户端不会收到：

- 未命中路由、隐藏 Layout 和未来任务 Layout；
- 未授权字段值、占位前的真实值或 stale 权限历史；
- Score holder、Storage/NBT 路径、Selector、函数 ID 和服务器异常；
- 其他玩家的组件管理与布局偏好。

权限撤销发送高版本原子替换或 clear。客户端断线、切换服务器、协议失配或 active game 清空时
必须清除全部 HUD/Countdown 投影。

## 11. Client Policy

完整结构：

```json
{
  "allow_hide": true,
  "default_anchor": "bottom_right",
  "allowed_anchors": ["bottom_right", "bottom_left"],
  "offset": {"max_x": 96, "max_y": 96},
  "scale": {"minimum": 0.75, "default": 1.0, "maximum": 1.25},
  "opacity": {"minimum": 0.55, "default": 0.92, "maximum": 1.0},
  "allow_component_management": true,
  "tab_collision": "dim"
}
```

锚点：`bottom_right`、`bottom_left`、`top_right`、`top_left`。验收示例默认只开放右下和左下；
`center` 不允许用于持久 dock。Countdown 固定中央，不接受玩家拖到屏幕角落。

Profile 范围与模组全局安全范围取交集。数据包给出更宽范围时被全局范围钳制并产生主持人诊断；
不能通过把 opacity minimum 设为 `0` 绕过 `allow_hide: false`。

`tab_collision`：`dim`、`hide` 或 `keep`。`keep` 仍必须遵守硬安全区，发生不可解决重叠时走
Layout 降级，而不是绘制到 TAB 之上。

本地设置只保存资源 ID、Profile 内容版本和获准偏好；定义升级后先校验再迁移。未知组件偏好
隔离，不按旧数组索引套用到新组件。

## 12. 屏幕与避让规则

以下行为由模组固定，数据包不能放宽以覆盖敏感页面：

| 场景 | Dock |
|---|---|
| 正常游戏 | 显示 |
| 聊天输入 | 显示并避让聊天 |
| F1 | 隐藏 |
| 库存、容器 | 隐藏 |
| 设置、终端、控制台、数据页 | 隐藏 |
| 强制流程页面 | 隐藏 |
| 普通暂停菜单或未知 Screen | 默认隐藏 |
| TAB 碰撞 | 按 `tab_collision` |

客户端排除矩形使用参考画布归一化坐标并带来源 ID、启用状态和可选说明。玩家配置只影响自己。
排除矩形不能移动、隐藏或修改第三方 HUD，只参与本信息坞的安全区求解。

## 13. v13 投影语义

V3C 目标协议 v13 在 v12 基础上增加语义帧：

```text
HUD_REPLACE
HUD_PATCH
HUD_CLEAR
COUNTDOWN_REPLACE
COUNTDOWN_PATCH
COUNTDOWN_CLEAR
HUD_RESYNC_REQUEST
HUD_PREVIEW_REPLACE
```

具体 Java payload 名可以不同，但线上语义必须满足：

- 每帧携带 game instance、definition generation、HUD epoch、context version 和 sequence；
- `REPLACE` 是完整原子投影；
- `PATCH` 只适用于同 epoch/context 且 base sequence 匹配的值变化；
- `CLEAR` 高于同 epoch 的旧 Replace/Patch；
- 乱序、重复、跨 epoch、超限或引用未知节点的帧不应用；
- 缺 Patch 时客户端请求一次有节流的 Replace，不连续刷请求；
- 结构、权限、路由与客户端 policy 变化必须 Replace，不能用半结构 Patch；
- 计时器携带服务端 Tick 锚点、基值、速率和 paused 状态，不每 Tick 发包；
- Preview 使用独立 namespace/epoch，不覆盖正式 HUD，也不能写回服务端状态。

HUD 更新不会复用 V3B Title/Subtitle 队列，Countdown 生命周期提醒也不能抢占已有 3A 提示；只有
数据包显式注册的 V3B Cue 才进入文字调度器。

## 14. Countdown 资源

推荐结构：

```json
{
  "format_version": 1,
  "name": {"text": "正式开局倒计时"},
  "duration": "10s",
  "layout": "pixel_tzz:countdown/opening",
  "display_audience": {"participants": true},
  "disconnect": {
    "required_player": "pause",
    "host": "continue"
  },
  "restrictions": {
    "movement": "freeze",
    "attack": "block",
    "interact": "block",
    "items": {
      "use": "block",
      "drop": "block",
      "swap": "block",
      "inventory": "block"
    },
    "damage": "immune",
    "camera": "allow",
    "chat": "allow",
    "escape": "allow"
  },
  "checkpoints": [
    {
      "id": "five_seconds",
      "remaining": "5s",
      "sound": {
        "event": "minecraft:block.note_block.hat",
        "volume": 0.75,
        "pitch": 1.0
      }
    },
    {
      "id": "start",
      "remaining": "0t",
      "sound": {
        "event": "minecraft:block.note_block.pling",
        "volume": 0.9,
        "pitch": 1.25
      }
    }
  ],
  "callbacks": {
    "start": [],
    "pause": [],
    "resume": [],
    "cancel": [],
    "complete": []
  }
}
```

顶层字段：

| 字段 | 必填 | 默认 | 说明 |
|---|---|---|---|
| `format_version` | 是 | 无 | 当前只接受 `1` |
| `name` | 是 | 无 | 主持人确认与诊断显示名 |
| `duration` | 否 | `10s` | 正整数 Tick/秒/分钟时长 |
| `layout` | 是 | 无 | `surface: countdown` Layout |
| `display_audience` | 否 | 冻结参与者 | 只影响表现，不改变权威参与者 |
| `disconnect` | 否 | pause/continue | 人员掉线策略 |
| `restrictions` | 否 | 安全默认 | 倒计时参与者限制 |
| `checkpoints` | 否 | `[]` | 有界声音或无回调 Cue 检查点 |
| `callbacks` | 否 | 全空 | 服务端生命周期回调 |

`duration` 最短与最长值在实现压力测试后冻结；`0t` 无效，不能用零时长模拟“可选倒计时”。
不需要倒计时应省略 Game 引用。

### 14.1 `countdown.*` 绑定

Countdown Layout 可以绑定：

```text
countdown.name
countdown.total_ticks
countdown.remaining_ticks
countdown.elapsed_ticks
countdown.progress
countdown.state
countdown.waiting_reason
countdown.missing_player_count
countdown.paused
```

普通玩家显示必须使用本地化/格式化组件，不直接显示 Tick。`waiting_reason` 为服务端裁剪后的公开
枚举与数据包文案，不包含玩家无权看到的名单。主持人控制台可以通过独立权威视图查看完整原因。

### 14.2 Disconnect 策略

```json
{
  "required_player": "pause",
  "host": "continue"
}
```

值：`pause`、`cancel`、`continue`。

- `required_player` 只针对本局冻结参与者中仍被要求参加开局者；默认 `pause`；
- `pause` 冻结剩余 Tick，原 UUID 回来且连接协调完成后自动继续；
- `cancel` 走正式取消状态和回调；
- `continue` 不改变冻结参与者，也不把掉线玩家移出最终任务；
- `host` 默认 `continue`；主持人接管不会重建 Countdown；
- 数据包不能允许普通 OP 通过掉线策略获得主持人控制。

## 15. Restrictions

字段与允许值：

| 字段 | 值 | 默认 |
|---|---|---|
| `movement` | `freeze`、`allow` | `freeze` |
| `attack` | `block`、`allow` | `block` |
| `interact` | `block`、`allow` | `block` |
| `items.use` | `block`、`allow` | `block` |
| `items.drop` | `block`、`allow` | `block` |
| `items.swap` | `block`、`allow` | `block` |
| `items.inventory` | `block`、`allow` | `block` |
| `damage` | `immune`、`normal` | `immune` |
| `camera` | 只允许 `allow` | `allow` |
| `chat` | 只允许 `allow` | `allow` |
| `escape` | 只允许 `allow` | `allow` |

V3C 不接受 `camera: block`、`chat: block` 或 `escape: block`；镜头控制属于 V3D，聊天与 ESC 是
倒计时安全出口。限制只作用于冻结参与者，不自动作用于观战者或非参与主持人。

限制必须通过服务端事件门和独立状态实现，不能注册药水效果、改游戏模式或把属性写成固定值。
完成、取消、玩家移出、重启恢复失败和紧急终止都调用同一个幂等解除边界。

## 16. Checkpoints

```json
{
  "id": "three_seconds",
  "remaining": "3s",
  "sound": {
    "event": "minecraft:block.note_block.hat",
    "volume": 0.75,
    "pitch": 1.1
  },
  "cue": "pixel_tzz:countdown/three"
}
```

每个 checkpoint 必须有唯一 ID 和唯一 `remaining`。`remaining` 可为 `0t`，但不能大于 duration。
倒计时跨过多个 checkpoint 时按剩余时间顺序只投影仍有意义的最终状态，不能在低 FPS 后一次
爆发播放全部声音。

`cue` 可选，引用 V3B Message Cue。用于 checkpoint 的 Cue 必须：

- 不含服务端 callback；
- 不写任务历史；
- 不改变 Countdown 权威状态；
- 受众不得大于 `display_audience`；
- 资源缺失时不阻止服务端计时，除非 Countdown 整体 required 闭包显式把该 Cue 标为必需资产。

客户端 HUD/Countdown 音量分别作用于组件音和 checkpoint 声音。将本地音量设为零不跳过 checkpoint。

## 17. Callbacks

结构：

```json
{
  "complete": [
    {
      "id": "open_warmup",
      "scope": "global",
      "function": "pixel_tzz:countdown/open_warmup",
      "required": true
    },
    {
      "id": "prepare_players",
      "scope": "each_participant",
      "function": "pixel_tzz:countdown/prepare_player",
      "required": true
    }
  ]
}
```

Slot：`start`、`pause`、`resume`、`cancel`、`complete`。

| 字段 | 必填 | 说明 |
|---|---|---|
| `id` | 是 | slot 内稳定唯一键 |
| `scope` | 是 | `global` 或 `each_participant` |
| `function` | 是 | 注册 `mcfunction` ID |
| `required` | 否 | 默认 `true`；失败是否阻断该状态提交 |

服务端函数使用冻结宏上下文：

```text
game_id
game_instance_id
countdown_id
countdown_instance_id
callback_slot
callback_id
reason
total_ticks
remaining_ticks
participant_uuid        # each_participant only
participant_name        # each_participant only, current verified name
```

账本键至少包含 Countdown instance、slot、callback ID、scope 和 participant UUID。成功项至多执行
一次；失败项在同一冻结上下文中重试。`complete` 的全部 required 项成功后才启动热身/正式任务；
`cancel` required 失败时停在可恢复 `canceling`，不能返回 running 重播最后几秒。

Preview、HUD 刷新、客户端设置、客户端确认和网络重同步都不会执行 callback。

## 18. 权威状态迁移

有 Countdown：

```text
AWAITING_APPROVAL
  └─ host approve + freeze
       └─ COUNTDOWN/running
            ├─ required player lost → waiting_for_players
            │    └─ restored → running
            ├─ server restart → recovery_wait
            │    └─ connection reconciliation → running/waiting_for_players
            ├─ host cancel → canceling → AWAITING_APPROVAL
            └─ remaining 0 → completing → WARMUP or first MAIN task
```

无 Countdown：

```text
AWAITING_APPROVAL → existing 2D atomic approval → WARMUP or first MAIN task
```

状态规则：

- running 只由服务端 Tick 减少 remaining；
- waiting/recovery 不减少 remaining；
- `/reload` 不切换实例、不替换冻结定义；
- 服务器离线时长不折算成 elapsed；
- cancel/complete 都是幂等多步提交；
- 同一实例不能同时拥有 cancel 与 complete 终态；
- complete 之后不能恢复旧 Countdown；
- 所有参与者准备、开局前字段和冻结计划在批准时再次验证。

## 19. 热身与全局时钟

Countdown 本身从不计入 `game_elapsed_ticks`。完成后：

- 首任务为 `kind: warmup` 时启动 warmup/task clock，`game_elapsed_ticks` 保持不动；
- 连续赛前环节继续遵循每个 Task 的 `counts_toward_game_time`；
- 第一个 `kind: main` 且计入全局时长的任务开始时，原子启动 game clock；
- 没有 warmup 时，Countdown complete 与第一个 main task/game clock 在同一权威提交中开始；
- 暂停、间隔和 Task 自身时钟继续遵循 2D 双 Tick 契约；
- HUD `clock: game` 显示人类可读分秒，不显示原始 Tick。

## 20. `/reload`、重启与取消

### 20.1 `/reload`

- 正在运行的 Countdown 使用批准时冻结资源闭包；
- 新 generation 的同 ID 文件不改变当前布局、duration、checkpoint、限制或 callback；
- 当前实例 Snapshot 可以因实时数据/受众变化更新，但结构来自冻结定义；
- 无效新 generation 不破坏当前实例或上一代目录；
- 下一次批准才读取最新有效 generation。

### 20.2 服务器重启

Schema v5 持久化：

```text
countdown instance id
definition id + frozen generation/closure
state + state version
total/remaining ticks
frozen participants
disconnect policies
restriction restoration markers
callback ledger
last server tick anchor
recovery reason
```

启动后无条件进入 `recovery_wait`，不能按墙钟补扣离线时间。冻结闭包损坏、迁移失败或回调账本
不一致时 fail closed，保留主持人诊断与安全取消，不立即开始 Task。

### 20.3 主持人取消

取消使用现有二次确认框架，确认摘要至少显示：

- 操作：取消正式开局倒计时；
- Countdown 显示名与实例；
- 剩余时间；
- 影响：返回等待批准、保留准备与开局前字段、丢弃本次冻结候选；
- 不可逆部分：已成功的外部 callback 不会回滚；
- 当前 required callback/人员阻塞；
- 令牌到期和状态版本。

不提供“跳过”“立即完成”或“减到 3 秒”操作。

## 21. 完整最小示例

### 21.1 Component：任务标题

`data/pixel_tzz/pixel_tzz_pro/hud_components/gameplay/task_title.json`

```json
{
  "format_version": 1,
  "type": "text",
  "priority": "critical",
  "bindings": {
    "task_name": {
      "value_type": "component",
      "source": {"type": "task_fact", "value": "name"},
      "critical": true,
      "on_missing": {"mode": "hide_component"}
    }
  },
  "text": {
    "parts": [
      {"component": {"text": "任务  ", "color": "#55D7E6"}},
      {"field": {"scope": "component", "id": "task_name"}}
    ]
  },
  "layout": {"alignment": "left", "max_lines": 2, "overflow": "wrap"},
  "client_control": {"visibility": "locked_shown", "compact": "locked"}
}
```

### 21.2 Component：赛程脊线

`data/pixel_tzz/pixel_tzz_pro/hud_components/gameplay/spine.json`

```json
{
  "format_version": 1,
  "type": "progress",
  "priority": "primary",
  "bindings": {
    "current": {
      "value_type": "decimal",
      "source": {"type": "task_fact", "value": "timeline_position"},
      "on_missing": {"mode": "placeholder", "value": 0}
    },
    "maximum": {
      "value_type": "decimal",
      "source": {"type": "task_fact", "value": "timeline_extent"},
      "on_missing": {"mode": "placeholder", "value": 1}
    }
  },
  "current": {"field": "current"},
  "maximum": {"field": "maximum"},
  "orientation": "horizontal",
  "segments": 0,
  "label": "none",
  "interpolation": "smooth",
  "style": {
    "track_color": "#40505F",
    "fill_color": "#55D7E6",
    "marker_color": "#F2C14E",
    "height": 2
  }
}
```

### 21.3 Component：根 Column

`data/pixel_tzz/pixel_tzz_pro/hud_components/gameplay/root.json`

```json
{
  "format_version": 1,
  "type": "column",
  "priority": "primary",
  "children": [
    "pixel_tzz:gameplay/task_title",
    "pixel_tzz:gameplay/task_progress",
    "pixel_tzz:gameplay/details",
    "pixel_tzz:gameplay/spine"
  ],
  "gap": 4,
  "alignment": "stretch"
}
```

### 21.4 Layout

`data/pixel_tzz/pixel_tzz_pro/hud_layouts/gameplay/default.json`

```json
{
  "format_version": 1,
  "surface": "dock",
  "audience": {"participants": true},
  "root": "pixel_tzz:gameplay/root",
  "compact_root": "pixel_tzz:gameplay/root_compact",
  "summary_root": "pixel_tzz:gameplay/root_summary",
  "size": {
    "minimum_width": 132,
    "preferred_width": 212,
    "maximum_width": 244,
    "maximum_height": 124,
    "growth": "up"
  },
  "transition": {
    "enter": "rise_fade",
    "change": "crossfade_values",
    "exit": "fade"
  }
}
```

### 21.5 Profile

`data/pixel_tzz/pixel_tzz_pro/hud_profiles/main.json`

```json
{
  "format_version": 1,
  "default_layout": "pixel_tzz:gameplay/default",
  "routes": [],
  "client_policy": {
    "allow_hide": true,
    "default_anchor": "bottom_right",
    "allowed_anchors": ["bottom_right", "bottom_left"],
    "offset": {"max_x": 96, "max_y": 96},
    "scale": {"minimum": 0.75, "default": 1.0, "maximum": 1.25},
    "opacity": {"minimum": 0.55, "default": 0.92, "maximum": 1.0},
    "allow_component_management": true,
    "tab_collision": "dim"
  }
}
```

Countdown Layout 还需按 §6 注册 `surface: countdown` 的 Layout 与对应 Component。Game 引用
Profile 和 Countdown 后，完整闭包才可发布。

## 22. 诊断与错误隔离

主持人简洁诊断至少包含：

```text
严重级别 · 资源显示名/类型 · 中文原因 · 影响范围 · 建议操作
```

原始诊断可额外包含资源 ID、文件路径、JSON Pointer、引用链、generation、错误码和硬限制值。
普通玩家只看到已注册的安全降级或无 HUD，不看到红字页面、机器键、路径与异常栈。

主要错误码语义：

```text
HUD_RESOURCE_INVALID
HUD_REFERENCE_MISSING
HUD_REFERENCE_CYCLE
HUD_ROUTE_AMBIGUOUS
HUD_AUDIENCE_CLOSURE_INVALID
HUD_BINDING_UNAUTHORIZED
HUD_LAYOUT_UNRESOLVABLE
HUD_PAYLOAD_TOO_LARGE
COUNTDOWN_REQUIRED_INVALID
COUNTDOWN_STATE_RECOVERY_BLOCKED
COUNTDOWN_CALLBACK_BLOCKED
COUNTDOWN_RESTRICTION_RECOVERY_BLOCKED
```

错误隔离：

- 单个非 required HUD Component 无效时隔离其引用闭包；
- Profile 仍有合法 degradation/default 时可以发布；
- required Countdown 的任何必需闭包无效时阻止批准；
- 当前活动 Countdown 永远使用冻结闭包，不因 Reload 新错误中断；
- 客户端布局异常只清除本地 HUD 并请求一次重同步，不能修改服务端状态；
- 同一错误日志与玩家提示节流，不能每 Tick 刷屏。

## 23. 硬上限冻结门

本文在代码开始前冻结了类别与失败语义，但不凭空指定所有数值。实现合并前必须通过压力测试在
本节补齐并由 SelfCheck 固定：

| 类别 | 必须冻结的值 |
|---|---|
| 资源 | 每代 Profile/Layout/Component/Countdown 数量 |
| 引用 | 最大深度、展开节点、降级链、容器子项、Overlay 层数 |
| 内容 | 文本 part/字素/行、图像尺寸、repeat 项与摘要 |
| 网络 | Replace/Patch 字节、每秒补丁、乱序窗口、重同步节流 |
| 客户端 | 排除矩形、草稿配置、缓存 epoch、动画实例 |
| 倒计时 | 时长、checkpoint、callback、账本与恢复记录 |
| 诊断 | 每资源、每 generation 与全局条数 |

数据包软限制可以更低，不能更高。超限不截断 critical 内容后继续假装成功；资源隔离并保留上一
代有效 generation。

## 24. 推荐自动检查

实现后至少提供：

```powershell
.\gradlew.bat hudDefinitionSelfCheck
.\gradlew.bat hudRouteClosureSelfCheck
.\gradlew.bat hudProjectionCompilerSelfCheck
.\gradlew.bat hudMinecraftBindingSourceAccessSelfCheck
.\gradlew.bat hudPreviewBindingSourceAccessSelfCheck
.\gradlew.bat hudPreviewServerRuntimeContractSelfCheck
.\gradlew.bat hudSnapshotProtocolSelfCheck
.\gradlew.bat hudLayoutPolicySelfCheck
.\gradlew.bat hudAnimationLifecycleSelfCheck
.\gradlew.bat hudClientPreferencesSelfCheck
.\gradlew.bat countdownDefinitionSelfCheck
.\gradlew.bat countdownAuthoritySelfCheck
.\gradlew.bat countdownRestrictionSelfCheck
.\gradlew.bat countdownPersistenceSelfCheck
.\gradlew.bat countdownCallbackLedgerSelfCheck
.\gradlew.bat protocolV13SelfCheck
.\gradlew.bat clean check build --console=plain --no-daemon
```

上述任务已注册并由 `check` 汇总。自动检查不能证明 Minecraft 内真实布局、声音、同步、避让
和手感，实机步骤见 [`MILESTONE-3C-TESTING.md`](MILESTONE-3C-TESTING.md)。

## 25. 当前实现状态

截至 2026-08-04：

- v13、Schema v5 与游戏 API v4 已实现，并保留旧世界迁移和无 Countdown 的 2D 立即开局路径；
- parser 已接受 `hud_components`、`hud_layouts`、`hud_profiles`、`countdowns`，并执行引用闭包、
  受众、容量和必需资源校验；
- HUD/Countdown 正式投影、Patch/Clear、重同步、客户端动画降级与独立预览 Surface 已接通；
- 主持人批准可冻结 Countdown 与后续 Timeline 启动计划，完成后不重新读取在线名单或 readiness；
- “HUD 与倒计时”设置、普通预览、主持人只读开发预览、中英文案和本地偏好已实现；
- 示例数据包、验收函数与自动 SelfCheck 已提供，`clean check build` 已通过；
- 当前唯一未完成门槛是 [`MILESTONE-3C-TESTING.md`](MILESTONE-3C-TESTING.md) 的五客户端实机验收，
  任何项目都不得在用户明确反馈前标记为 PASS。
