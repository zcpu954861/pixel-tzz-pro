# Pixel TZZ Pro 数据包倒计时 API（3C）

状态：**正式契约；实现、完整自动门与用户逐项五客户端实机验收均已通过**

- 适用目标 Minecraft：`26.2`
- 当前模组版本：`0.1.0`
- 目标游戏 API：`api_version: 4`
- 目标网络协议：v13
- 目标世界状态：Schema v5
- 本文资源格式：`format_version: 1`
- 最后整理：2026-08-06

本文是 V3C 倒计时定义、开局接入、权威状态、客户端表现、恢复和回调的数据包契约。产品
边界与实施顺序见 [`MILESTONE-3C-PLAN.md`](MILESTONE-3C-PLAN.md)，逐项五客户端验收入口见
[`MILESTONE-3C-TESTING.md`](MILESTONE-3C-TESTING.md)。

原“常驻 HUD + 倒计时”原型保留在 `codex/milestone-3c` 分支的 `e66b92d`，只作为未来可能
恢复常驻 HUD 时的参考。该原型的 HUD Component、Layout、Profile、路由和信息坞不是当前
V3C API，数据包不得依赖其字段。

V3A 玩家终端、V3B 动态消息和 2D 任务时间线继续分别遵循：

- [`DATA-PACK-PLAYER-TERMINAL-API-3A.md`](DATA-PACK-PLAYER-TERMINAL-API-3A.md)
- [`DATA-PACK-MESSAGE-API-3B.md`](DATA-PACK-MESSAGE-API-3B.md)
- [`DATA-PACK-TASK-API-2D.md`](DATA-PACK-TASK-API-2D.md)

## 1. 设计边界

V3C 提供一套服务端权威、可持久恢复的通用倒计时核心。当前版本唯一公开的业务入口是
Game 的 `opening_countdown`：主持人批准开局后运行倒计时，完成后进入冻结的热身或首个正式
任务。

通用核心不理解“热身”“任务间隔”或“猎人释放”。运行时以用途处理器承接完成动作，当前只
注册 `opening` 用途。未来需要任务间隔或猎人释放倒计时时，可以增加新的受控接入点并复用
同一核心；当前数据包不能自行填写用途、任意创建实例或调用内部完成处理器。

V3C 明确不提供：

- 常驻 HUD 信息坞、HUD Component、HUD Layout、HUD Profile 或路由；
- 任意坐标、任意组件树、客户端脚本、Shader、网络请求或文件读取；
- 同时运行多个倒计时；
- 新倒计时覆盖、替换、合并或排队接替当前实例；
- 玩家或主持人跳过、快进、立即完成或修改剩余时间；
- 依赖某个客户端动画完成来推动服务端状态；
- 通过表现配置执行 `mcfunction`。

全局同一时刻最多存在一个非终态权威倒计时。第二个启动请求必须明确失败并保留现有实例。

## 2. 资源路径、ID 与覆盖

Countdown 资源路径：

```text
data/<namespace>/pixel_tzz_pro/countdowns/<path>.json
```

路径决定稳定资源 ID：

```text
data/pixel_tzz/pixel_tzz_pro/countdowns/opening/default.json
→ pixel_tzz:opening/default
```

每个文件建议显式声明；省略时默认当前版本：

```json
{"format_version": 1}
```

覆盖与解析规则：

- Minecraft 数据包优先级先决定同路径最终资源；
- 高优先级文件完整覆盖低优先级同 ID 文件，不做字段级深合并；
- 根必须是对象；
- 重复键、尾随内容、未知键、错误类型和越界值产生诊断；
- 资源 ID 必须显式包含命名空间；
- Checkpoint 与 Callback 本地 ID 匹配 `[a-z0-9._-]{1,128}`；
- 无效资源被隔离；核心 definition generation 整体失败时保留上一代有效 generation；
- required 引用闭包无效时阻止对应开局批准；
- 正在运行的实例继续使用批准时冻结的定义，新 generation 只影响下一次批准。

### 2.1 `READY_WITH_WARNINGS` 与上一代安全回退

Countdown 是隔离目录。核心 Game/Role/Phase 等定义可以成功发布，而某个 Countdown 因未知字段、
越界值、缺失文件或引用闭包错误而失败。此时注册表使用 `READY_WITH_WARNINGS`：

- `healthy` 仍为 true，新的核心 generation 可以发布；该状态不等于整个数据包 Reload 失败；
- 主持人看到有界中文 Countdown ID、字段/原因、是否已回退以及 `/reload` 恢复指引；
- 普通玩家和非主持人 OP 不得到资源路径、机器异常或详细诊断；
- `READY_WITH_WARNINGS` 是模组投影状态，不是数据包可读取或分支的 JSON 字段。

若当前 Game 仍引用这个坏或缺失的 Countdown，注册表可以复用上一代同 ID 的最后已知可用定义，
但不是无条件保留整个旧目录：

1. 只考虑当前 Game `opening_countdown` 仍引用的 ID；
2. 使用新 generation 的 Function、Message Cue 和投影预算重新验证上一代定义闭包；
3. 只有闭包仍完整时才保留旧 Canonical JSON/SHA 与可执行定义，同时保留新候选的错误诊断；
4. 回退定义与其错误诊断按一个资源 ID 计数，不伪装成两份定义；
5. 没有上一代、上一代文档不可恢复或新闭包复核失败时，不回退；`required: true` 继续阻断批准，
   `required: false` 继续走明确审阅后的整体降级；
6. 下一次合法 Reload 会替换回退版本，并原子清除 warning 与 retained-fallback 标记。

活动实例不参与这套目录回退选择：它始终继续使用批准时已经冻结的定义和回调闭包。

颜色只接受 `#RRGGBB` 或 `#RRGGBBAA`，八位格式的 Alpha 位于末尾。时间字段接受整数 Tick，
或带 `t`、`s`、`m`、`h` 后缀的字符串。权威时长最终统一为服务端 Tick；客户端动画时长使用
整数毫秒字段。

## 3. Game 接入

使用 V3C 开局倒计时的 Game 声明 `api_version: 4`：

```json
{
  "format_version": 1,
  "api_version": 4,
  "content_version": 4,
  "name": {"text": "全员逃走中"},
  "initial_phase": "pixel_tzz:setup",
  "default_role": "pixel_tzz:runner",
  "default_life_state": "pixel_tzz:alive",
  "opening_countdown": {
    "definition": "pixel_tzz:opening/default",
    "required": true
  }
}
```

| 字段 | 必填 | 默认 | 说明 |
|---|---|---|---|
| `opening_countdown` | 否 | 保持 2D 立即开局 | 开局倒计时引用对象 |
| `opening_countdown.definition` | 对象存在时是 | 无 | Countdown ID |
| `opening_countdown.required` | 否 | `true` | 无效时阻断批准；`false` 可在明确审阅后降级为立即开局 |

兼容规则：

- 现有 `api_version: 3` Game 行为不变；
- v3 声明 v4 字段仍按未知字段报错；
- v4 可以省略 `opening_countdown`；
- `required: true` 的定义无效时（包括缺失 Checkpoint Cue 或必需 Callback 函数），主持人不能批准；
- `required: false` 只允许整个倒计时降级，不能运行半份有效定义；
- optional 降级必须在确认页明确写出“无倒计时立即开局”，不能静默跳过。

## 4. Countdown 顶层结构

完整结构示意：

```json
{
  "format_version": 1,
  "name": {"text": "正式开局倒计时"},
  "duration": "10s",
  "display_audience": {
    "include_host": true,
    "include_observers": false
  },
  "presentation": {},
  "disconnect": {
    "required_player": "pause",
    "host": "continue"
  },
  "restrictions": {},
  "checkpoints": [],
  "callbacks": {}
}
```

| 字段 | 必填 | 默认 | 说明 |
|---|---|---|---|
| `format_version` | 否 | `1` | 当前只接受 `1` |
| `name` | 是 | 无 | 主持人确认、诊断和审计显示名 |
| `duration` | 否 | `10s` | `1..72000` Tick |
| `display_audience` | 否 | 冻结参与者 | 只改变可见受众，不改变权威参与者 |
| `presentation` | 否 | 模组安全默认 | ActionBar 上方专用倒计时表现 |
| `disconnect` | 否 | player pause / host continue | 掉线策略 |
| `restrictions` | 否 | 安全默认 | 冻结参与者限制 |
| `checkpoints` | 否 | `[]` | 有界关键秒表现、声音或 V3B Cue |
| `callbacks` | 否 | 全空 | 服务端生命周期函数 |

`duration: 0t` 无效。不需要倒计时时应省略 Game 引用，而不是用零时长模拟跳过。

## 5. Presentation

Countdown 不再引用任意 HUD Layout。`presentation` 是专用、有界、不可交互的表现对象：

```json
{
  "title": {"text": "正式游戏即将开始"},
  "prefix": {"text": ""},
  "suffix": {"text": ""},
  "waiting_text": {"text": "等待所需玩家返回"},
  "paused_text": {"text": "倒计时已暂停"},
  "complete_text": {"text": "开始"},
  "time": {
    "format": "mm_ss",
    "precision": "seconds",
    "leading_zero": true,
    "separator": ":",
    "color": "#F4F7FA",
    "scale": 1.0,
    "shadow": true
  },
  "panel": {},
  "digits": {},
  "progress": {},
  "enter": {},
  "exit": {},
  "completion": {}
}
```

渲染顺序由模组固定：`title → prefix → time → suffix` 在一条紧凑主行内排列，进度细线位于
主行下方。空间不足时先收起空项和非必需前后缀，再对数据包正文执行有界换行或省略；不能把
数字裁掉、挤出屏幕或借滚动正文掩盖布局失败。

`waiting_text`、`paused_text` 和 `complete_text` 分别在对应权威状态替换时间区域或整条主行，
具体替换方式由专用渲染器控制，不允许数据包重排组件。所有 RichText 最多 16 KiB、纯文本最多
256 个字符；为避免客户端自行读取权威数据，`score`、`selector` 和 `nbt` 动态组件不允许出现
在 Countdown Presentation 中。

## 6. 固定位置与 Panel

倒计时由模组绘制在原版 ActionBar 基准线上方。它不写入、抢占、清除或改写原版/V3B
ActionBar。ActionBar 是否有内容都不会改变倒计时基线，避免两条信息出现或消失时上下跳动。

`panel` 可配置：

| 字段 | 类型 | 默认 | 目标安全范围 |
|---|---|---|---|
| `background_color` | color | `#081018D0` | 合法颜色 |
| `border_color` | color | `#33424ECC` | 合法颜色 |
| `accent_color` | color | `#F2C14E` | 合法颜色 |
| `opacity` | number | `0.88` | `0.20..1.0` |
| `padding_x` | integer | `6` | `2..24` 逻辑像素 |
| `padding_y` | integer | `3` | `1..12` 逻辑像素 |
| `gap_above_actionbar` | integer | `6` | `2..24` 逻辑像素 |
| `max_width` | integer | `320` | `96..480` 逻辑像素 |

位置、Anchor 和任意 X/Y 不可配置。文字大小参考原版 ActionBar；数据包只能在安全范围内调整
数字强调比例，不能把倒计时做成遮挡准星或大面积占屏的中央卡片。

玩家不能通过模组选项隐藏倒计时、把透明度调为零、移出屏幕或写旧配置绕过显示。原版 F1
仍按 Minecraft 习惯隐藏整个 HUD，V3C 尊重该行为；“不可隐藏”特指模组设置不提供长期关闭
入口。

## 7. 时间格式与 0.01 秒视觉精度

`time` 字段：

| 字段 | 值 | 默认 |
|---|---|---|
| `format` | `seconds`、`mm_ss` | `mm_ss` |
| `precision` | `seconds`、`tenths`、`hundredths` | `seconds` |
| `leading_zero` | Boolean | `true` |
| `separator` | 1～2 个字符 | `:` |
| `color` | color | `#F4F7FA` |
| `scale` | `0.75..1.50` | `1.0` |
| `shadow` | Boolean | `true` |

显示示例：

| format | precision | 示例 |
|---|---|---|
| `seconds` | `seconds` | `10` |
| `seconds` | `tenths` | `09.9` |
| `seconds` | `hundredths` | `09.99` |
| `mm_ss` | `seconds` | `00:10` |
| `mm_ss` | `hundredths` | `00:09.99` |

权威精度仍是服务端 20 TPS。客户端接收剩余 Tick、服务端 Tick 锚点、速率和 paused 状态，使用
单调时钟按渲染帧插值。`hundredths` 只表示最高 `0.01s` 的视觉分辨率：

- 不发送每秒 100 个网络包；
- 不改变服务端完成、暂停、回调或开局 Tick；
- 低 FPS 时直接追到当前显示值，不排队补播错过的百分位；
- 显示按当前粒度向上取整，尚未权威完成时不提前显示负数；
- 客户端动画或 `00.00` 不能自行触发完成；
- 权威 `complete` 才能结束实例并进入热身或正式任务。

所有数字位固定宽度，精度和数值变化不能造成整条文字左右跳动。

## 8. 逐位数字动画

`digits` 可配置：

| 字段 | 值或范围 | 默认 |
|---|---|---|
| `transition` | `roll`、`fade`、`instant` | `roll` |
| `direction` | `down`、`up` | `down` |
| `duration_ms` | `0..2000` | `240` |
| `distance` | `4..24` 逻辑像素 | `10` |
| `easing` | `linear`、`ease_out_cubic`、`ease_in_out_cubic` | `ease_out_cubic` |
| `stagger_ms` | `0..100` | `0` |
| `animate_fractional` | Boolean | `false` |
| `incoming_color` | color | `#FFFFFF` |
| `outgoing_color` | color | `#8A9AA8` |
| `incoming_alpha` | `0.20..1.0` | `1.0` |
| `outgoing_alpha` | `0..1` | `0.55` |

默认 `down` 的确定语义：

- 每一位数字拥有独立裁切窗口；
- 新数字从上方进入，旧数字同步向下退出；
- 未改变的数字完全不动；
- 冒号和小数点不参与滚动；
- `10 → 09`、`01:00 → 00:59` 只滚动真正变化的位；
- 暂停时立即落在完整数字位置，不能卡在两个字符之间；
- 重连、重同步、低 FPS 或大幅校正直接追到最新值，不补播旧数字；
- `animate_fractional: false` 时小数位直接更新，整秒位仍可滚动。

客户端动态等级拥有最终降级权：

- `full`：按数据包配置播放有界动画；
- `simplified`：缩短或移除非必要位移，默认只滚整秒位；
- `static`：数字直接切换，但内容、剩余时间和完成时序不变。

## 9. 进度线与生命周期动画

`progress`：

| 字段 | 值或范围 | 默认 |
|---|---|---|
| `mode` | `none`、`line` | `line` |
| `color` | color | `#F2C14E` |
| `track_color` | color | `#33424E60` |
| `thickness` | `1..4` 逻辑像素 | `1` |
| `smooth` | Boolean | `true` |

当前固定紧凑位置不支持进度环。进度线按客户端渲染帧平滑插值，但最终进度仍来自同一权威
时钟，不能独立漂移。

`enter` 与 `exit` 使用相同有界结构：

```json
{
  "mode": "slide",
  "duration_ms": 180,
  "easing": "ease_out_cubic"
}
```

`mode` 只接受 `fade`、`slide`、`instant`；持续时间 `0..2000ms`。
`enter` 默认 `fade / 180ms / ease_out_cubic`，`exit` 默认
`fade / 220ms / ease_out_cubic`。
数据包不能注册频闪、循环抖动或无限动画。

`completion` 可配置：

```json
{
  "hold_ms": 650,
  "color": "#F2C14E"
}
```

`hold_ms` 默认为 `600`，范围为 `0..5000`；`color` 默认为 `#F2C14E`。完成表现不能延迟服务端进入下一环节；
如果动画尚未结束，客户端应以权威状态直接收口，不要求服务器等待。

`presentation.sounds` 可为 `start`、`pause`、`resume`、`cancel`、`complete` 各注册一个
`event`、`volume` 和 `pitch`，字段结构与 Checkpoint sound 相同；`volume` 与 `pitch` 都默认
`1.0`。

## 10. Audience 与不可隐藏下限

```json
{
  "include_host": true,
  "include_observers": false
}
```

- 冻结参与者永远属于显示受众，不暴露可关闭字段；
- 数据包不能让被冻结参与者看不到关键开局倒计时；
- `include_host` 只控制不属于参与者的当前合法主持人；
- `include_observers` 只控制当前玩家记录的身份在本次冻结定义中带有
  `pixel_tzz:spectator` 标签的已连接玩家；完成握手、拥有 OP 或执行命令都不算观察授权；
- 当前合法主持人即使身份同时带观察标签，也仍只由 `include_host` 控制；
- 显示受众不改变冻结参与者、限制目标、回调目标或最终 LaunchPlan；
- 执行命令的人不会被隐式加入受众；
- 非主持人 OP 不因 OP 身份自动收到主持人专属诊断。

倒计时只下发专用表现字段与裁剪后的公开等待原因，不下发 Selector、NBT、函数路径、隐藏名单
或数据包内部异常。主持人通过独立控制台查看完整诊断。

## 11. Disconnect

```json
{
  "required_player": "pause",
  "host": "continue"
}
```

值：`pause`、`cancel`、`continue`。

- required player 默认 `pause`，冻结剩余 Tick，等待同一 UUID 返回并完成连接协调；
- `cancel` 进入正式 canceling 路径并执行取消回调；
- `continue` 不改变冻结参与者，也不把缺失玩家移出最终任务；
- host 默认 `continue`；
- 合法主持人接管不会重建实例或修改剩余时间；
- 普通 OP 不会因掉线策略获得主持人权限。

手动暂停不作为 3C 公开操作。暂停只由冻结策略、恢复协调或内部安全门触发。

## 12. Restrictions

| 字段 | 值 | 默认 |
|---|---|---|
| `movement` | `freeze`、`allow` | `freeze` |
| `attack` | `block`、`allow` | `block` |
| `interact` | `block`、`allow` | `block` |
| `items.use` | `block`、`allow` | `block` |
| `items.drop` | `block`、`allow` | `block` |
| `items.swap` | `block`、`allow` | `block` |
| `items.inventory` | `block`、`allow` | `block` |
| `damage` | `immune`、`allow` | `immune` |
| `camera` | 只允许 `allow` | `allow` |
| `chat` | 只允许 `allow` | `allow` |
| `escape` | 只允许 `allow` | `allow` |

限制只作用于冻结参与者，不自动作用于观战者或非参与主持人。V3C 不锁镜头、不屏蔽聊天、
不禁止 ESC，也不通过永久效果、改游戏模式或覆盖无关属性模拟限制。完成、取消、参与者移出、
重启恢复失败和紧急终止调用同一幂等解除边界。

## 13. Checkpoints

```json
{
  "id": "three_seconds",
  "remaining": "3s",
  "text": {"text": "最后准备"},
  "color": "#F2C14E",
  "scale": 1.18,
  "sound": {
    "event": "minecraft:block.note_block.hat",
    "volume": 0.75,
    "pitch": 1.1
  },
  "cue": "pixel_tzz:countdown/three"
}
```

- 每个 ID 和 `remaining` 在定义内唯一；
- `remaining` 可为 `0t`，但不能大于 duration；
- 最多 64 个 checkpoint；
- Checkpoint 只接受可选 `text`、`color` 和 `scale`，不能嵌入完整 Presentation；
- `scale` 范围为 `1.0..1.5`，仍受客户端 simplified/static 降级；
- 声音音量为 `0..4`，音高为 `0.5..2.0`；
- 本地倒计时音量为零只静音，不跳过 checkpoint；
- 低 FPS 跨过多个节点时不得爆发补播全部旧声音或动画；
- `cue` 引用 V3B Message Cue，但不得修改 Countdown 权威状态、写入倒计时回调账本或扩大受众。

## 14. Callbacks

支持 `start`、`pause`、`resume`、`cancel`、`complete` 五个槽：

```json
{
  "complete": [
    {
      "id": "prepare_players",
      "scope": "each_participant",
      "function": "pixel_tzz:countdown/prepare_player",
      "required": true
    }
  ]
}
```

| 字段 | 必填 | 说明 |
|---|---|---|
| `id` | 是 | slot 内稳定唯一键 |
| `scope` | 是 | `global` 或 `each_participant` |
| `function` | 是 | 注册 `mcfunction` ID |
| `required` | 否 | 默认 `true`；失败是否阻断该状态提交 |

函数使用冻结宏上下文：

```text
game_instance_id
countdown_id
countdown_instance_id
callback_slot
callback_id
reason
total_ticks
remaining_ticks
participant_uuid        # each_participant only
participant_name        # each_participant only
```

账本键至少包含 instance、slot、occurrence、callback ID、scope 和 participant UUID。成功项至多
执行一次；尚未执行的 `PENDING` 项在冷启动后继续使用原 occurrence，不创建替代 occurrence。
已经持久化为 `PREPARED`、但服务器在结果入账前退出的项恢复为 `OUTCOME_UNKNOWN`：普通失败重试
必须排除它，只有主持人完成独立的高风险确认、明确接受“外部副作用可能重复”后才可再次执行。
明确失败的项仍在同一冻结上下文中重试。

`complete` 的全部 required 项成功后，opening 用途处理器才提交冻结 LaunchPlan；`cancel` required
失败时停在可恢复 `canceling`，不能返回 running 重播最后几秒。若倒计时已经 `completed`、但冻结
LaunchPlan 交接失败，实例、诊断和限制保持可见；主持人可以重试同一冻结候选，或通过单独的
高风险确认放弃交接并回到等待批准。放弃不会回滚已经执行的回调或外部副作用。

Preview、客户端设置、渲染、重同步和数字动画永不执行 callback。

## 15. 权威状态与单实例规则

状态至少包括：

- `running`
- `waiting_for_players`
- `recovery_wait`
- `completing`
- `canceling`
- `completed`
- `canceled`

迁移：

```text
AWAITING_APPROVAL
  └─ host approve + freeze
       └─ COUNTDOWN/running
            ├─ required player lost → waiting_for_players
            │    └─ restored → running
            ├─ server restart → recovery_wait
            │    └─ reconciliation → running/waiting_for_players
            ├─ host cancel → canceling → AWAITING_APPROVAL
            └─ remaining 0 → completing → WARMUP or first MAIN task
```

规则：

- running 只由服务端 Tick 减少 remaining；
- waiting/recovery 不减少 remaining；
- 服务器离线时长不折算成 elapsed；
- cancel/complete 是幂等多步提交；
- 同一实例不能同时拥有 cancel 与 complete 终态；
- complete 后不能恢复旧实例；
- 全局已有非终态实例时拒绝任何第二次启动；
- 没有“覆盖现有”“排队下一条”“跳过”“快进”或“设置剩余时间”操作。

## 16. Opening 特化与时间线

主持人批准时必须原子冻结：

- Game 与 game instance；
- 固定参与者 UUID；
- 完整可达 Task 计划与首个节点；
- 开局前字段和准备结果；
- Countdown definition、presentation、限制、Checkpoint 和 Callback；
- definition generation；
- opening `LaunchPlan`。

完成顺序：

1. 倒计时达到权威零点；
2. 进入 `completing`；
3. 必需 complete callbacks 全部成功；
4. opening purpose handler 提交冻结 LaunchPlan；
5. 有 warmup 时启动第一个 warmup；
6. warmup 全部结束后，原子启动全局游戏时钟与第一个计时 main task；
7. 无 warmup 时，直接原子启动 game clock 与第一个 main task。

倒计时与所有 `kind: warmup`、`counts_toward_game_time: false` 的赛前环节都不计入
`game_elapsed_ticks`。Warmup 可以拥有自己的 Task clock。完成时不得重新选择玩家、重新检查
readiness、重新编译当前 generation 或重新生成首任务实例。

## 17. 取消、Reload 与重启

主持人取消：

- 只在阶段感知主持人控制台提供；
- 使用绑定 game instance、countdown instance、purpose、状态版本和到期 Tick 的高风险确认；
- 摘要写明剩余时间、返回等待批准、保留准备/字段、丢弃冻结候选和回调不可回滚部分；
- 状态变化或过期后旧令牌失效，重新审阅直接打开当前确认；
- 取消成功后不自动开始下一轮；
- 不提供跳过、快进或立即完成按钮。

`/reload`：

- 活动实例继续使用批准时冻结 generation；
- 不用新文件替换 duration、presentation、声音、限制或 callback；
- Reload 时间不额外扣除或补偿 Tick；
- 新 generation 只影响下一次批准；
- 无效新 generation 不破坏活动实例或上一代目录。

正常服务器关闭与重启：

- 持久化 instance、purpose、state/version、total/remaining、冻结参与者、定义、LaunchPlan、
  限制恢复标记和 callback ledger；
- 启动后先进入 `recovery_wait`；
- 客户端和所需玩家尚未完成连接协调时不倒数；
- 恢复同一实例，不重播已成功 start callback、已过 checkpoint 或完整进入动画；
- 冻结快照损坏时 fail closed，由主持人查看诊断并安全取消，不能立即开局。

## 18. 网络投影与客户端时钟

v13 Countdown 投影提供 Replace、Patch、Clear 和 Resync：

- 每名获准客户端拥有 game epoch、countdown instance、purpose、state version 和 sequence；
- Replace 提供完整专用 Presentation 和权威时钟锚点；
- Patch 只更新状态、时钟、公开等待原因和真正变化的表现；
- Clear 使用更高序列的墓碑，迟到旧帧不能复活已结束或跨局实例；
- 乱序、重复、未知实例和旧 epoch 帧被丢弃；
- 客户端检测序列缺口后发送节流 Resync，不循环请求；
- 重同步 Replace 不重播进入动画、Checkpoint 或声音；
- 服务端不每 Tick 给每位玩家发送完整快照；
- 客户端单调时钟只负责插值，不拥有权威完成权。

F1 隐藏遵循原版。正常游戏、聊天框和 ESC 打开时倒计时保持自身紧凑位置；库存被限制时不能
打开，若数据包允许库存，倒计时仍不修改 Screen 或吞掉输入。第三方 Screen 完全覆盖 HUD 时
不承诺强行绘制在其上方，但退出后必须直接恢复当前状态且不重播。

## 19. 客户端设置与预览

设置入口固定为：

```text
选项 → 全员逃走中设置 → 开局倒计时
```

提供：

- 动态强度：full / simplified / static；
- 高对比；
- 倒计时音量；
- 本地安全预览；
- 重置倒计时偏好与诊断。

不提供：

- 完全隐藏；
- 位置、Anchor 或自由拖动；
- 透明度 0；
- 修改剩余时间、精度、受众或数据包文案；
- 跳过、快进或手动完成。

普通预览只使用内置样例或当前已授权定义，不写世界状态、不发其他客户端、不执行回调、不
建立历史、不创建正式实例。主持人诊断可预览起点、普通秒、借位、百分位、等待、暂停、恢复
和完成，但原始资源路径及异常详情只在主持人原文模式显示。

## 20. 硬上限与错误语义

目标硬上限：

| 类别 | 上限或要求 |
|---|---|
| Countdown 定义 | 每 generation 最多 128 |
| 权威活动实例 | 全局最多 1 |
| duration | `1..72000` Tick |
| checkpoint | 每定义最多 64 |
| callback | 每 slot 最多 64 |
| 客户端动画 | 单段 `0..2000ms` |
| digit stagger | `0..100ms` |
| 文本 | 单 RichText 最大 16 KiB、纯文本 256 字符 |
| 资源文档 | Canonical JSON 最大 256 KiB |
| 网络 | Replace/Patch 字节、频率、乱序窗口和 Resync 均有硬上限 |
| 诊断 | 每资源、generation 和全局数量有界 |

数据包可以声明更低软限制，不能突破模组硬限制。超限定义应隔离并给主持人明确诊断；不能
裁掉关键数字后继续假装定义有效，也不能向普通玩家投影路径、机器 ID 或异常栈。

局部 Countdown 隔离不得让主持人只看到无差别的“已就绪”。有安全上一代时，诊断必须明确正在
继续使用已验证旧版本；没有安全上一代时，诊断必须明确对应 required 开局会被阻断。两种情况都
不能静默修剪超限字段后运行新候选，也不能因为保留旧版本而丢弃新候选的错误证据。

## 21. 完整示例

```json
{
  "format_version": 1,
  "name": {"text": "正式开局倒计时"},
  "duration": "10s",
  "display_audience": {
    "include_host": true,
    "include_observers": false
  },
  "presentation": {
    "title": {"text": "正式游戏即将开始"},
    "waiting_text": {"text": "等待所需玩家返回"},
    "paused_text": {"text": "倒计时已暂停"},
    "complete_text": {"text": "开始"},
    "time": {
      "format": "mm_ss",
      "precision": "hundredths",
      "leading_zero": true,
      "separator": ":",
      "color": "#F4F7FA",
      "scale": 1.0,
      "shadow": true
    },
    "panel": {
      "background_color": "#081018D0",
      "border_color": "#33424ECC",
      "accent_color": "#55D7E6",
      "opacity": 0.88,
      "padding_x": 6,
      "padding_y": 3,
      "gap_above_actionbar": 6,
      "max_width": 320
    },
    "digits": {
      "transition": "roll",
      "direction": "down",
      "duration_ms": 240,
      "distance": 10,
      "easing": "ease_out_cubic",
      "stagger_ms": 0,
      "animate_fractional": false,
      "incoming_color": "#FFFFFF",
      "outgoing_color": "#8A9AA8",
      "incoming_alpha": 1.0,
      "outgoing_alpha": 0.55
    },
    "progress": {
      "mode": "line",
      "color": "#55D7E6",
      "track_color": "#40505F",
      "thickness": 1,
      "smooth": true
    },
    "enter": {
      "mode": "slide",
      "duration_ms": 180,
      "easing": "ease_out_cubic"
    },
    "exit": {
      "mode": "fade",
      "duration_ms": 220,
      "easing": "ease_out_cubic"
    },
    "completion": {
      "hold_ms": 650,
      "color": "#F2C14E"
    },
    "sounds": {
      "start": {
        "event": "minecraft:block.note_block.hat",
        "volume": 0.7,
        "pitch": 1.0
      },
      "complete": {
        "event": "minecraft:block.note_block.pling",
        "volume": 0.9,
        "pitch": 1.25
      }
    }
  },
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
      "id": "three_seconds",
      "remaining": "3s",
      "text": {"text": "最后准备"},
      "color": "#F2C14E",
      "scale": 1.18,
      "sound": {
        "event": "minecraft:block.note_block.hat",
        "volume": 0.75,
        "pitch": 1.1
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

## 22. 推荐自动检查

实现完成后至少覆盖：

```powershell
.\gradlew.bat countdownDefinitionSelfCheck
.\gradlew.bat countdownAuthoritySelfCheck
.\gradlew.bat countdownRestrictionSelfCheck
.\gradlew.bat countdownPersistenceSelfCheck
.\gradlew.bat countdownCallbackLedgerSelfCheck
.\gradlew.bat countdownCallbackRetryConfirmationSelfCheck
.\gradlew.bat protocolV13SelfCheck
.\gradlew.bat settingsUiContractSelfCheck
.\gradlew.bat check build --console=plain --no-daemon
```

任务名以最终 `build.gradle` 注册结果为准，文档和实现必须同步。自动检查不能证明真实 Minecraft
中的 ActionBar 间距、逐位滚动、声音、五端同步、窗口缩放和操作手感；这些项目只能由
[`MILESTONE-3C-TESTING.md`](MILESTONE-3C-TESTING.md) 的实机结果确认。

## 23. 当前实现状态

本文已经冻结并交付新的纯倒计时目标与字段；archived 原型中的服务端权威状态机、限制、
回调账本、LaunchPlan、持久化和恢复语义已迁移，专用 Countdown Definition、网络运行时和
ActionBar 上方渲染器已替代 HUD 组件树。实现、示例数据包、完整自动门与用户逐项五客户端
实机验收已于 2026-08-06 全部通过，V3C 已完成。
