# Pixel TZZ Pro 数据包任务与独占选择 API（2D）

状态：**框架契约与自动检查已完成，待 Minecraft 实机验收**
适用当前模组版本：`0.1.0`
目标 Minecraft：`26.2`

本文描述当前 2D 数据包契约。当前工作树接受游戏 `api_version: 2`、`task_timeline`、`tasks/`、`exclusive_choice`、数据驱动 TAB 与阶段回调，并将运行状态保存为 Schema v3。本文示例只说明接口；仓库验收夹具不是正式任务或地图，用户 Minecraft 多客户端实机验收仍在进行。

既有页面、流程和面板格式继续遵循：

- [`DATA-PACK-API-2A.md`](DATA-PACK-API-2A.md)
- [`DATA-PACK-UI-2B.md`](DATA-PACK-UI-2B.md)

实施状态与实机脚本见：

- [`MILESTONE-2D-PLAN.md`](MILESTONE-2D-PLAN.md)
- [`MILESTONE-2D-TESTING.md`](MILESTONE-2D-TESTING.md)

## 1. 设计边界

2D 新增两项通用能力：

1. 一个游戏同一时刻只有一个活动任务的持久时间线；
2. 多名玩家不能占用同一选项的持久独占选择。

模组不注册具体任务，不理解“箱子”“认证”“猎人释放”等内容词义。数据包注册任务、结果、事件、统计、出生点及回调；模组只负责验证、冻结、计时、持久化、路由和安全调用。

## 2. 资源路径

新增：

```text
data/<namespace>/pixel_tzz_pro/tasks/<path>.json
```

扩展既有：

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

文件路径决定定义 ID，规则与 2A 相同。最高数据包优先级完整覆盖同路径低优先级 JSON，不深合并。

## 3. 版本规则

注册器当前明确支持：

- 保持定义顶层 `format_version: 1`；
- 游戏 `api_version` 提升为 `2` 才允许使用 `task_timeline`；
- `tasks/` 定义只允许属于 `api_version: 2` 的游戏；
- `exclusive_choice` 只允许属于 `api_version: 2` 的游戏；
- 世界持久化 Schema v3 与数据包 `api_version` 是两套不同版本，不能混用。

`format_version` 表示单文件外壳格式，`api_version` 表示游戏使用的跨定义能力。当前解析器严格拒绝未知字段；后续若需要不兼容扩展，必须显式提高版本，不能静默改变本文语义。

### 3.1 显示名称与 UI 标点

数据包在 `name` 等可见名称字段中注册没有外围括号的干净中文名称，例如 `"猎人出生点"`、`"北门"`，不要注册成 `"『猎人出生点』"` 或 `"「北门」"`。标点是统一渲染语义，不属于定义 ID 或名称正文：

- `『名称』` 表示数据包正式注册的名称；
- `「对象」` 表示当前正在查看、选择或操作的对象/选项；
- 渲染 helper 必须幂等：输入已经由同一对标点完整包裹时保持原样，不能生成 `『『名称』』` 或 `「「对象」」`；
- 英文只能作为弱化的辅助说明，不代替中文主名称，也不与中文标题争夺视觉层级。

同一注册名称在主持人控制台、强制页、二次确认、Subtitle 和回顾中都应通过这套 helper 统一呈现，数据包不应为了某个页面自行拼接外围标点。

## 4. 游戏定义扩展

```json
{
  "format_version": 1,
  "api_version": 2,
  "content_version": 1,
  "name": {"text": "全员逃走中"},
  "initial_phase": "pixel_tzz:setup",
  "default_role": "pixel_tzz:runner",
  "default_life_state": "pixel_tzz:alive",
  "readiness": {
    "phase": "pixel_tzz:awaiting_approval",
    "action": "pixel_tzz:open_player_readiness",
    "disconnect_invalidates": true,
    "host_can_force": false
  },
  "task_timeline": {
    "initial_task": "pixel_tzz:warmup/example",
    "pause_when_host_offline": false,
    "approval_phase": "pixel_tzz:awaiting_approval",
    "start_phase": "pixel_tzz:warmup",
    "pre_start_requirements": [
      {
        "type": "required_field",
        "audience": {
          "role_tags": ["pixel_tzz:hunter"],
          "exclude_host": true,
          "online_only": false
        },
        "field": "pixel_tzz:hunter_spawn"
      }
    ]
  }
}
```

### 4.1 `readiness`

`readiness` 把一个数据包定义的必需流程绑定为本局唯一的逐玩家准备事务。进入指定阶段时由服务端自动创建，不能由客户端或普通主持人操作伪造：

```json
{
  "phase": "pixel_tzz:awaiting_approval",
  "action": "pixel_tzz:open_player_readiness",
  "disconnect_invalidates": true,
  "host_can_force": false
}
```

| 键 | 必填 | 默认 | 说明 |
|---|---|---|---|
| `phase` | 是 | 无 | 自动开放准备事务的同游戏阶段 |
| `action` | 是 | 无 | 提供页面、受众与 BossBar 的同游戏主持人 `start_flow` 操作 |
| `disconnect_invalidates` | 否 | `true` | 已准备参与者掉线时是否清除其本次准备 |
| `host_can_force` | 否 | `false` | 保留的主持人代确认策略位；`0.1.0` 尚未提供代确认 UI，验收夹具固定为 `false` |

被引用的 `action` 仍采用普通 `panel_actions/` 格式，但必须同时满足：

- `surface: "host"`、`target.mode: "none"`，并包含 `readiness.phase`；
- `operation.type: "start_flow"` 且 `completion_policy: "always"`；
- 指向同游戏的 `required: true` 流程；
- 流程只包含 `page`、`confirm`、`complete` 节点，不允许回调，也不允许借准备页写入持久字段。

该操作只作为数据包注册入口。框架会从主持人的普通操作列表隐藏它，并在阶段迁移成功时原子开放；伪造普通面板请求也会被拒绝。

服务端冻结该流程的定义、页面、主题与受众规则，并从本局已注册玩家中计算准备成员。验收夹具的受众标签为 `pixel_tzz:ready_participant`，当前逃走者与猎人均带此标签；主持人与旁观者不计入分母。身份、队伍或声明了 `invalidates_ready: true` 的玩家字段发生变化时，服务端会按冻结的受众规则重算资格：仍符合者需要重新确认，不再符合者移出分母，新符合者加入分母。

玩家确认后不能主动取消。启用 `disconnect_invalidates` 时，游戏正常运行期间掉线会清除该玩家准备，重连后自动恢复不可关闭页面；正常停服与重启不会被误判为集体掉线。`/reload`、存档重载和正常服务器重启继续使用持久化的冻结快照，不会静默替玩家完成准备；旧世界已经停在准备阶段但缺少准备实例时，会在完成握手后自动补建。

全员完成只会结束准备事务并开放主持人最终审批，不会自动开始游戏。审批入口与最终提交都会再次校验游戏实例、阶段、来源操作、流程身份、完成状态和 `completed == total`，客户端隐藏按钮不能替代这项服务端门槛。

### 4.2 `task_timeline`

| 键 | 必填 | 默认 | 说明 |
|---|---|---|---|
| `initial_task` | 是 | 无 | 时间线入口任务 |
| `pause_when_host_offline` | 否 | `false` | 主持人离线时是否暂停有效 Tick |
| `approval_phase` | 是 | 无 | 允许批准开局的阶段 |
| `start_phase` | 是 | 无 | 原子批准后进入的游戏阶段；没有赛前环节时可直接是运行阶段 |
| `pre_start_requirements` | 否 | `[]` | 开局前必须满足的权威要求 |

一个游戏至多一个 `task_timeline`。没有该块的游戏仍可使用 2A/2C 能力，但不能启动 2D 时间线。

### 4.3 开局前要求

2D 首版只增加确有用途的 `required_field`：

```json
{
  "type": "required_field",
  "audience": { "...": "..." },
  "field": "pixel_tzz:hunter_spawn"
}
```

- `audience` 使用第 11 节的共享受众格式；
- `online_only` 建议为 `false`，避免离线参与者绕过要求；
- 字段必须是同游戏、`scope: "player"` 且 `required: true`；
- 字段值必须对当前版本有效；
- `exclusive_choice` 还必须处于 `locked` 状态；
- 要求只阻止批准开局，不自动打开流程或修改玩家。

不添加通用布尔表达式型开局要求；已有流程完成、准备状态和强制流程由核心状态机直接检查。

## 5. Task 定义

路径：`tasks/<path>.json`

完整结构：

```json
{
  "format_version": 1,
  "game": "pixel_tzz:main",
  "version": 1,
  "kind": "main",
  "name": {"text": "示例任务", "color": "#F1C96A"},
  "description": {"text": "用于验证任务框架，不是正式任务。"},
  "page": "pixel_tzz:tasks/example",
  "audience": {
    "role_tags": ["pixel_tzz:active_participant"],
    "exclude_host": true,
    "online_only": false
  },
  "completion_policy": "early_or_timeout",
  "duration_ticks": 12000,
  "counts_toward_game_time": true,
  "live_visibility": {
    "future": "teaser",
    "history": "hidden",
    "candidate_visibility": "names_only"
  },
  "recap_visibility": "participants",
  "callbacks": {
    "on_start": "pixel_tzz:task/example/start",
    "on_pause": "pixel_tzz:task/example/pause",
    "on_resume": "pixel_tzz:task/example/resume",
    "on_timeout": "pixel_tzz:task/example/timeout",
    "on_settled": "pixel_tzz:task/example/settled"
  },
  "on_start_players": {
    "function": "pixel_tzz:task/example/deploy_player",
    "audience": {
      "role_tags": ["pixel_tzz:hunter"],
      "exclude_host": true,
      "online_only": false
    },
    "fields": {
      "spawn_point": "pixel_tzz:hunter_spawn"
    }
  },
  "results": [
    {
      "id": "done",
      "name": {"text": "完成"},
      "semantic": "neutral",
      "route": {"end_phase": "pixel_tzz:ended"}
    }
  ],
  "events": [],
  "statistics": []
}
```

### 5.1 公共字段

| 键 | 必填 | 默认 | 约束 |
|---|---|---|---|
| `version` | 是 | 无 | 正整数 |
| `kind` | 是 | 无 | `warmup` 或 `main` |
| `name` | 是 | 无 | 原版文本组件 |
| `description` | 否 | 无 | 原版文本组件 |
| `page` | 否 | 无 | 已注册页面 ID |
| `audience` | 否 | 默认受众 | 任务参与者筛选 |
| `completion_policy` | 是 | 无 | 见第 6 节 |
| `duration_ticks` | 条件必填 | 无 | 正整数且不超过实现上限 |
| `counts_toward_game_time` | 否 | `kind == main` | 是否推进正式游戏时钟 |
| `live_visibility` | 否 | 全隐藏 | 玩家实时可见性 |
| `recap_visibility` | 否 | `participants` | `participants`、`host_only` 或 `hidden` |
| `callbacks` | 否 | 空 | 全局函数钩子 |
| `on_start_players` | 否 | 无 | 启动时逐玩家回调 |
| `results` | 是 | 无 | 至少一个结果 |
| `events` | 否 | `[]` | 任务事件 |
| `statistics` | 否 | `[]` | 回顾统计 |

任务 `version` 改变时，新开始的游戏冻结新版本；已经批准的游戏继续使用旧快照。任务版本不用于热迁移当前实例。

### 5.2 `kind`

- `warmup`：界面显示“赛前环节”，通常不计正式游戏时间；
- `main`：界面显示“任务”。

两者使用同一状态机、结果、分支、事件和恢复逻辑。`warmup` 不是“任务零”，也不会获得特殊 Java 类。

## 6. 完成策略

### 6.1 `timeout_only`

```json
{
  "completion_policy": "timeout_only",
  "duration_ticks": 12000
}
```

- `duration_ticks` 必填；
- 运行时不接受提前结果提交；
- 到时进入结算并调用 `on_timeout`；
- `on_timeout` 必须通过受控接口提交一个结果。

### 6.2 `early_or_timeout`

```json
{
  "completion_policy": "early_or_timeout",
  "duration_ticks": 12000
}
```

- `duration_ticks` 必填；
- 运行中允许数据包提前提交结果；
- 未提前完成则到时进入结算。

### 6.3 `event_only`

```json
{
  "completion_policy": "event_only"
}
```

- 不允许 `duration_ticks`；
- 不自动结束；
- 只能由数据包受控提交结果或紧急终止整局。

## 7. Results、Route 与 Intermission

任务 `results`：

```json
[
  {
    "id": "success",
    "name": {"text": "任务成功", "color": "#65D68A"},
    "semantic": "success",
    "recap": {"text": "逃走者完成了认证。"},
    "on_apply": "pixel_tzz:task/example/apply_success",
    "on_apply_players": {
      "function": "pixel_tzz:task/example/reward_player",
      "audience": {
        "role_tags": ["pixel_tzz:active_participant"],
        "exclude_host": true,
        "online_only": false
      },
      "fields": {}
    },
    "route": {
      "next_task": "pixel_tzz:main/next",
      "phase": "pixel_tzz:running",
      "transition_timing": "before_intermission",
      "intermission": {
        "duration_ticks": 1200,
        "counts_toward_game_time": true,
        "name": {"text": "任务间隔"},
        "on_start": "pixel_tzz:task/intermission/start",
        "on_complete": "pixel_tzz:task/intermission/complete"
      }
    }
  }
]
```

### 7.1 Result

| 键 | 必填 | 默认 | 说明 |
|---|---|---|---|
| `id` | 是 | 无 | 任务内唯一 `[a-z0-9_.-]+` |
| `name` | 是 | 无 | 显示名 |
| `semantic` | 是 | 无 | `success`、`failure`、`neutral` 或数据包命名空间 ID |
| `recap` | 否 | `name` | 赛后描述 |
| `on_apply` | 否 | 无 | 全局结算函数 |
| `on_apply_players` | 否 | 无 | 逐玩家结算函数 |
| `allow_host_fallback` | 否 | `false` | 结算异常时是否允许主持人指定该结果 |
| `route` | 是 | 无 | 下一任务或结束阶段 |

`semantic` 只用于主题样式和回顾筛选，不决定路由，也不允许 `skipped`。自定义值必须是显式命名空间 ID。

任务只要存在至少一个 `allow_host_fallback: true` 的结果，就显式开启有限人工兜底。该入口只在任务已经进入结算异常、尚未冻结结果时出现；主持人只能从这些允许结果中二次确认选择，不能借此提前结束或跳过任务。

### 7.2 Route

`route` 必须且只能包含一种终点：

```json
{"next_task": "pixel_tzz:main/next"}
```

或：

```json
{"end_phase": "pixel_tzz:ended"}
```

可选：

| 键 | 默认 | 说明 |
|---|---|---|
| `phase` | 保持当前阶段 | 结果路由关联的阶段 |
| `transition_timing` | `after_intermission` | `before_intermission` 或 `after_intermission` |
| `intermission` | 无 | 结果特定间隔 |

`end_phase` 已经表示最终阶段时，不能再填写 `phase`。没有 `intermission` 时不能填写 `transition_timing`，阶段在结算回调全部成功后立即转换。

### 7.3 Intermission

```json
{
  "duration_ticks": 1200,
  "counts_toward_game_time": true,
  "name": {"text": "任务间隔"},
  "on_start": "pixel_tzz:task/intermission/start",
  "on_pause": "pixel_tzz:task/intermission/pause",
  "on_resume": "pixel_tzz:task/intermission/resume",
  "on_complete": "pixel_tzz:task/intermission/complete"
}
```

- `duration_ticks` 必须大于零；
- 到零后默认自动执行 `on_complete` 并进入已冻结下一步；
- 回调失败则阻塞，不启动下一任务；
- 间隔定义冻结在结果路由中，不另建全局 interval 资源类型。

## 8. Tick 时钟

数据包不负责运行 `schedule` 计时。当前 2D 回调上下文提供：

```text
game_elapsed_ticks
task_elapsed_ticks
```

间隔剩余时间和暂停状态由服务端权威时间线投影给主持人 UI；当前 2D 不额外暴露可由
数据包轮询的查询命令。数据包应使用 `on_pause`、`on_resume`、`on_start`、`on_complete`
等对应生命周期回调响应状态变化。

规则：

- 服务器每个有效 Tick 最多递增一次；
- 主持人暂停时不递增；
- 服务器停机期间不补算；
- `/reload` 不暂停、不归零；
- `counts_toward_game_time=false` 时当前环节时钟仍递增，正式游戏时钟不递增；
- 数据包不得通过记分板回写权威时间。

## 9. 回调

### 9.1 全局回调

支持：

```text
task.callbacks.on_start
task.callbacks.on_pause
task.callbacks.on_resume
task.callbacks.on_timeout
task.callbacks.on_settled
result.on_apply
intermission.on_start
intermission.on_pause
intermission.on_resume
intermission.on_complete
```

宏参数：

```json
{
  "game_id": "pixel_tzz:main",
  "timeline_instance_id": "...",
  "task_id": "pixel_tzz:main/example",
  "task_instance_id": "...",
  "result_id": "success",
  "game_elapsed_ticks": 1234,
  "task_elapsed_ticks": 456
}
```

不适用字段省略，不传空的伪值。

### 9.2 逐玩家回调

格式：

```json
{
  "function": "pixel_tzz:task/deploy_hunter",
  "audience": {
    "role_tags": ["pixel_tzz:hunter"],
    "exclude_host": true,
    "online_only": false
  },
  "fields": {
    "spawn_point": "pixel_tzz:hunter_spawn"
  }
}
```

| 键 | 必填 | 默认 | 说明 |
|---|---|---|---|
| `function` | 是 | 无 | 存在的 `mcfunction` |
| `audience` | 否 | 任务受众 | 冻结玩家集合 |
| `fields` | 否 | `{}` | 宏名到玩家字段 ID |

每名玩家以本人作为命令源调用一次，宏增加：

```json
{
  "player_uuid": "...",
  "player_name": "PlayerB",
  "player_field": {
    "spawn_point": "north_gate"
  }
}
```

约束：

- 宏名使用 `[a-z][a-z0-9_]{0,31}`；
- 字段必须是 `scope: "player"`；
- 值必须满足冻结字段定义；
- `exclusive_choice` 只暴露锁定值；
- 必填字段缺失时回调不执行并进入技术阻塞；
- 每名玩家独立记录成功；
- 目标必须在线并以本人作为命令源；离线时该玩家步骤保持未执行并阻塞推进，重连后可重试。

当前 `TaskCallbackRunner` 已按冻结受众逐玩家执行，并只把定义中显式绑定且通过验证的字段值加入宏上下文。

全局 `on_start` 成功后才执行 `on_start_players`，所有必需启动回调成功后才进入 `running` 并开始任务计时。结算时先执行结果 `on_apply`，再执行 `on_apply_players`，最后执行任务 `on_settled`。每一步和每名玩家独立记录成功；失败会阻止继续推进，主持人重试时不能重放已成功项，已经冻结的结果也不能更换。

## 10. 受控命令

当前实现注册在 `/pixel_tzz task` 下。命令只接受当前任务实例 UUID；时间线、任务定义、状态、策略和 revision 均由服务端重新解析与验证。

### 10.1 提交结果

命令：

```text
/pixel_tzz task submit_result <task_instance_uuid> <result_id>
```

只允许服务器函数或具备明确权限的服务端源调用。服务端验证当前实例、状态、策略和冻结结果。

### 10.2 记录事件

```text
/pixel_tzz task record_event <task_instance_uuid> <event_id> [player]
```

省略玩家表示系统事件。玩家必须属于事件定义允许的受众。

### 10.3 写回顾统计

```text
/pixel_tzz task set_statistic <task_instance_uuid> <statistic_id> <value>
```

值必须符合定义类型和写入策略。`boolean` 使用 `true`/`false`，整数与时长使用十进制整数，`identifier` 使用完整资源 ID，`player` 使用已注册玩家 UUID，`string` 使用剩余整段文本。

### 10.4 受控状态变更

任务回调需要修改身份、队伍、生存状态或持久字段时，使用模组既有或扩展的受控动作；不能编辑世界持久文件。每项动作必须重新验证目标、阶段和定义。

## 11. 共享 Audience

2D 复用 2A 的受众语义：

```json
{
  "roles": [],
  "teams": [],
  "life_states": [],
  "role_tags": [],
  "team_tags": [],
  "life_state_tags": [],
  "exclude_host": true,
  "online_only": false
}
```

- 同一非空数组内 OR；
- 不同非空数组间 AND；
- 空数组不增加限制；
- `exclude_host` 默认 `true`；
- 任务、回调和开局要求默认 `online_only: false`，因为掉线不能自动脱离本局责任；
- 实际参与者在批准开局或创建任务时冻结；
- 受众只用于校验、统计、回顾和回调，不自动传送或改状态。

任务 `audience` 默认：

```json
{
  "role_tags": ["pixel_tzz:active_participant"],
  "exclude_host": true,
  "online_only": false
}
```

数据包必须为实际参与身份提供 `pixel_tzz:active_participant` 或显式覆盖受众。模组不根据身份名称推断。

## 12. 实时可见性

```json
{
  "live_visibility": {
    "future": "teaser",
    "history": "hidden",
    "candidate_visibility": "names_only"
  }
}
```

当前 `0.1.0` 不向普通参与者提供游戏进行中的通用时间线入口；服务端会直接拒绝非主持人的非终局时间线请求。因此以下字段是被冻结的“最多可公开到什么程度”元数据，不会自行授予入口或绕过服务端权限。主持人视图不受其限制；终局参与者使用第 14 节及各项 `recap_visibility` 裁剪后的回顾。

### 12.1 `future`

| 值 | 未来显式授权的任务页面最多可见内容 |
|---|---|
| `full` | 名称、说明和页面 |
| `teaser` | 名称和简短说明 |
| `hidden` | 不显示 |

### 12.2 `history`

- `full`：游戏中可看已发生公开记录；
- `summary`：只看任务名和结果；
- `hidden`：游戏中不提供历史入口。

玩家默认 `hidden`。当前版本无论该值为何都不会生成通用实时历史入口；主持人不受该限制。

### 12.3 未决分支

`candidate_visibility`：

- `none`：只显示“由本次结算决定”；
- `names_only`：列出允许公开的可达下一任务名称；

任何玩家界面都不能显示结果 ID 到下一任务的映射。结果冻结后才显示确切下一任务。

## 13. Task Events

定义：

```json
{
  "id": "terminal_activated",
  "name": {"text": "认证终端已启动"},
  "policy": "per_player",
  "allowed_states": ["running"],
  "audience": {
    "role_tags": ["pixel_tzz:active_participant"],
    "exclude_host": true,
    "online_only": false
  },
  "recap_visibility": "participants"
}
```

重复事件：

```json
{
  "id": "crate_opened",
  "name": {"text": "物资箱开启"},
  "policy": "repeatable",
  "max_records": 32,
  "recap_visibility": "host_only"
}
```

| 键 | 必填 | 默认 | 说明 |
|---|---|---|---|
| `id` | 是 | 无 | 任务内唯一 |
| `name` | 是 | 无 | 冻结到回顾 |
| `policy` | 是 | 无 | `once`、`per_player`、`repeatable` |
| `max_records` | 条件必填 | 无 | `repeatable` 必填 |
| `allowed_states` | 否 | `["running"]` | `running`、`settling`、`intermission` 的非空无重复数组 |
| `audience` | 否 | 任务受众 | 允许的玩家触发者 |
| `recap_visibility` | 否 | `participants` | `participants`、`host_only`、`hidden` |

每条记录由服务端补充任务实例、两个 Tick、发生状态、玩家 UUID 与当时名字。客户端按 UUID 渲染真实皮肤基础层和帽子层；离线状态只改变头像显示，不改冻结事实。数据包不能提交自定义时间戳。

事件记录本身不会自动广播聊天、Title、音效或动画；数据包继续负责现场演出。

## 14. Recap Statistics

定义：

```json
{
  "id": "rescued_players",
  "name": {"text": "成功救援"},
  "type": "integer",
  "min": 0,
  "max": 64,
  "write": "replace",
  "recap_visibility": "participants"
}
```

首版类型：

```text
boolean
integer
duration_ticks
string
identifier
player
```

`write`：

- `once`：第一次合法值后冻结；
- `replace`：任务结算完成前可覆盖；
- `increment`：只允许整数和时长，按受控增量累加。

统计：

- 只属于当前任务实例；
- 不参与结果路由；
- 不能替代任务事件；
- 在任务结算完成时冻结；
- 必须有值和长度上限；
- 玩家统计保存 UUID 与当时名字；头像继续由客户端按 UUID 渲染。

## 15. `exclusive_choice` 字段

字段示例：

```json
{
  "format_version": 1,
  "game": "pixel_tzz:main",
  "version": 1,
  "name": {"text": "猎人出生点"},
  "description": {"text": "选择本局猎人部署位置。"},
  "scope": "player",
  "type": "exclusive_choice",
  "required": true,
  "editable_by": "player",
  "invalidates_ready": true,
  "roles": ["pixel_tzz:hunter"],
  "phases": ["pixel_tzz:setup", "pixel_tzz:initializing"],
  "migration": "preserve",
  "reservation_scope": "game_instance",
  "release_when_role_mismatch": true,
  "show_occupant": true,
  "options": [
    {
      "value": "north_gate",
      "name": {"text": "北门"},
      "description": {"text": "靠近北侧入口。"},
      "icon": "minecraft:iron_door",
      "preview_page": "pixel_tzz:spawn/north_gate"
    },
    {
      "value": "station",
      "name": {"text": "车站"},
      "description": {"text": "靠近中央车站。"},
      "icon": "minecraft:minecart",
      "preview_page": "pixel_tzz:spawn/station"
    }
  ]
}
```

### 15.1 字段规则

| 键 | 必填 | 默认 | 说明 |
|---|---|---|---|
| `reservation_scope` | 否 | `game_instance` | 2D 首版只接受该值 |
| `release_when_role_mismatch` | 否 | `false` | 玩家不再匹配字段 `roles` 时释放 |
| `show_occupant` | 否 | `true` | 选项被占用时是否向同流程参与者显示占用者名字和头像 |
| `options` | 是 | 无 | 非空、值唯一的对象数组 |

选项：

| 键 | 必填 | 说明 |
|---|---|---|
| `value` | 是 | 稳定 `[a-z0-9_.-]+` |
| `name` | 是 | 显示名 |
| `description` | 否 | 说明 |
| `icon` | 否 | 图标资源 ID |
| `preview_page` | 否 | 预览页面 |

`exclusive_choice`：

- 只能是 `scope: "player"`；
- 不能提供普通 `default`，避免多人默认占用同一项；
- 选项删除或字段版本变化不会抢占替代值；
- 已有值在新定义中不存在时标记无效并阻止依赖该值的新操作；
- 当前游戏实例的冻结字段选项决定正在执行流程，不受 `/reload` 热替换。

### 15.2 预约语义

- 卡片点击时立即向服务端提交权威 `HOLD`，不等待“继续”或整个页面提交；
- 再次点击当前玩家自己的 `HELD` 卡片时立即提交 `RELEASE`；
- 从一个选项改到另一个选项时由服务端原子交换，不能先释放旧值再竞争新值；
- 流程完成时变为 `locked`；
- 两个并发请求只能有一个成功；
- 失败响应必须返回“已被占用”，以及允许公开时的占用者名字和头像；
- 普通 `RELEASE` 只能释放当前流程属于请求玩家的 `HELD`，不能删除既有 `LOCKED` 值；
- `HOLD`、`RELEASE`、原子改选、冲突和最终锁定后，服务端都向仍在同一流程中的相关玩家广播最新权威页面状态；
- 掉线、重启和 `/reload` 不释放；
- 主持人取消本次未完成重新选择时释放新临时值；
- 身份不再匹配且 `release_when_role_mismatch=true` 时释放锁定值。

即时 mutation 必须校验当前游戏、活动普通强制流程、页面实例、flow/node/member revision、字段引用、成员资格、选项值与当前预约状态。客户端本地选中值只用于即时视觉反馈，不是预约事实源；收到服务端冲突或新的权威页面包后，如果本地值已经失效，必须立即清除。

## 16. Flow `choice` 扩展

既有 `choice` 节点允许引用 `single_choice`；当前实现也允许引用 `exclusive_choice`：

```json
{
  "id": "choose_spawn",
  "type": "choice",
  "page": "pixel_tzz:hunter/choose_spawn",
  "field": "pixel_tzz:hunter_spawn",
  "choices": [
    {"value": "north_gate", "next": "confirm_spawn"},
    {"value": "station", "next": "confirm_spawn"}
  ]
}
```

执行差异：

- 页面打开时显示实时占用状态；
- 占用者头像使用真实皮肤基础层和帽子层，离线时对同一头像做灰度处理；
- 玩家点击卡片时即执行第 15.2 节的权威预约 mutation；
- 已占用选项禁用；
- 当前玩家自己的临时或旧锁定值可识别；
- 再次点击自己的临时值会释放，点击另一可用项会原子改选；
- `complete` 节点原子锁定最终值；
- 流程异常时不把临时值伪装成锁定值。

页面排版、字段、选项名称、说明、图标和用途均由数据包注册。模组只提供通用 `exclusive_choice` 交互、权威 mutation 以及选项状态、占用者和当前选择的绑定上下文；不得写死“出生点页面”、北门/车站等选项或猎人专用逻辑。

后续确认页可以使用 `flow/fields/<namespace:field>` 读取当前成员在本次流程中的暂存值；这也适用于最终会写回玩家数据的 `scope: "player"` 字段。`fields/<namespace:field>` 仍表示流程开始前已经持久化的玩家值，重新初始化时不要用它冒充本次新预约。

`confirm` 节点可以声明可选的 `back` 目标，让最终确认页返回选择节点：

```json
{
  "id": "confirm_spawn",
  "type": "confirm",
  "page": "pixel_tzz:hunter/confirm",
  "next": "complete",
  "back": "choose_spawn"
}
```

对应确认页面提供 `name: "back"` 的动作后，玩家可以返回重选。返回只切换节点并保留当前 `HELD`，不会制造无保护窗口；只有再次点击自己的卡片、原子改选、流程取消/成员移除或最终完成才按各自规则改变预约。没有 `back` 的既有 `confirm` 节点保持原行为。

流程启动预检时，对每个引用的 `exclusive_choice` 字段计算“未被本次目标之外玩家锁定的选项 + 本次目标自己已有的锁定选项”。可用不同选项数少于需要完成该字段的目标人数时，整个流程启动请求拒绝，不让玩家进入注定无法全员完成的流程。

主持人的活动流程成员行必须显示该字段的有界摘要：尚未选择、`临时预约：<中文选项名>` 或 `最终锁定：<中文选项名>`。摘要来自服务端权威流程快照，并随上述广播自动更新；内部字段 ID、选项 value 和协议状态名只留在开发与诊断信息中。

## 17. 身份定义与 TAB 扩展

身份：

```json
{
  "format_version": 1,
  "game": "pixel_tzz:main",
  "name": {"text": "猎人"},
  "initialization_flow": "pixel_tzz:hunter_initialization",
  "tags": ["pixel_tzz:hunter", "pixel_tzz:active_participant"],
  "tab": {
    "prefix": {"text": "[猎人] "},
    "color": "#E94F64",
    "sort_order": 100,
    "prefix_mode": "replace",
    "color_mode": "replace"
  }
}
```

### 17.1 `initialization_flow`

- 可省略；
- 存在时必须是同游戏流程；
- 身份提交时记录本次成功流程实例、流程 ID 和版本作为该身份初始化凭据；仅有更早的历史完成记录不能绕过本次 `always` 重新初始化；
- 当前身份拥有匹配的初始化凭据后才视为已经初始化；
- 身份事务可以先写入待身份，但 TAB 在流程完成前不公开；
- 无初始化流程的身份立即公开。

### 17.2 `tab`

role、team 和 life state 都可使用：

| 键 | 默认 | 说明 |
|---|---|---|
| `prefix` | 无 | 原版文本组件 |
| `color` | 无 | `#RRGGBB` |
| `sort_order` | role 为 `0`，后续层继承 | `-10000..10000`，数值越小越靠前；后续层显式值覆盖前层 |
| `prefix_mode` | 有 `prefix` 时 `append`，否则 `inherit` | `append`、`replace`、`inherit` |
| `color_mode` | 有 `color` 时 `replace`，否则 `inherit` | `replace`、`inherit` |

合成层级：

```text
role → team → life_state → host system override
```

相同 `sort_order` 使用自然玩家列表顺序和 UUID 作为稳定后备。未公开 role 不应用其排序；主持人系统样式永远最高优先级。

`inherit` 表示该层不改变已有值，因此对应 `prefix` 或 `color` 应省略；注册器应拒绝“提供值但声明继承”的无效组合。

不再通过 `pixel_tzz:backstage`、`pixel_tzz:spectator` 等写死标签推断显示。

## 18. Phase 扩展与兼容

阶段仍由 `phases/` 注册：

```json
{
  "format_version": 1,
  "game": "pixel_tzz:main",
  "name": {"text": "游戏进行中"},
  "transitions": ["pixel_tzz:ended"],
  "on_enter": "pixel_tzz:phase/running_enter",
  "on_exit": "pixel_tzz:phase/running_exit"
}
```

2D 要求：

- 客户端、控制台、面板可见性和任务路由都传递完整阶段 ID；
- 不要求阶段 ID 落在 Java 枚举；
- 固定 `GamePhase` 只用于旧世界解码；
- 当前阶段缺失时阻止新操作并给主持人诊断；
- 不能自动替换为 `IDLE` 或其他猜测阶段。

## 19. 快照、Reload 与持久化

### 19.1 批准开局时冻结

冻结从 `initial_task` 可达的：

- task 版本、名称、说明、页面；
- 受众；
- 完成策略和时长；
- 结果及路由；
- 间隔；
- 回调函数 ID；
- 逐玩家字段绑定；
- 可见性；
- 事件；
- 统计；
- 独占字段选项；
- 阶段显示所需最小定义。

### 19.2 `/reload`

- 新资源完整校验后发布为“下一次运行候选”；
- 当前时间线继续使用冻结计划；
- 当前流程和独占选择使用各自冻结定义；
- 新注册任务不能插入当前 DAG；
- 删除当前任务定义不影响当前实例；
- 新定义错误不清除当前数据。

### 19.3 持久化

必须保存：

- 当前任务与状态；
- 两个 Tick；
- 结果与冻结路由；
- 回调账本；
- 事件和统计；
- 回顾；
- 独占预约；
- revision 和审计。

服务器停机时间不补算。恢复时不重放已成功回调。

冻结时间线正文可能接近 2 MiB，Schema v3 将规范化 JSON 以有界 UTF-8 分块保存，并同时保存
SHA-256；加载时必须完整重组并校验后才能恢复。正文为空、分块缺失或摘要不匹配均进入只读保护，
不得依据当前数据包猜测性重建正在进行的旧时间线。该存储格式属于模组内部实现，数据包不得直接依赖
NBT 键名。

## 20. 主持人操作

系统级任务操作不由每个数据包重复注册按钮，但是否可用由当前冻结定义和状态决定：

| 操作 | 条件 | 二次确认 |
|---|---|---|
| 暂停时间线 | 运行或间隔 | 否 |
| 继续时间线 | 主持人暂停 | 否 |
| 延长间隔 | 间隔 | 是 |
| 提前结束间隔 | 间隔 | 是 |
| 重试失败回调 | 技术阻塞 | 是 |
| 人工指定结果 | 结算异常且结果允许 | 是 |
| 紧急终止整局 | 未完成时间线 | 是 |
| 重置本局进程 | 非执行中的确认安全点 | 是 |
| 清空全部数据 | 任意可管理状态 | 是 |

猎人出生点重选不属于时间线系统操作。数据包应注册类似
`pixel_tzz:reinitialize_hunter` 的高风险面板操作，让选中的猎人重新进入对应初始化流程；
该操作仍需二次确认，独占值由流程中的原子改选语义处理。

确认页必须显示：

- 操作名称；
- 当前任务和状态；
- 影响玩家；
- 会发生什么；
- 不会发生什么；
- 是否可恢复；
- 当前实例与 revision。

透明令牌续期不得让用户为了内部过期再次点击，也不得重建确认页面。

## 21. 错误与诊断

当前 2D 相关稳定拒绝码包括：

```text
timeline_not_active
timeline_already_active
timeline_completed
task_not_current
task_state_mismatch
result_not_registered
result_already_frozen
completion_policy_mismatch
callback_failed
intermission_not_active
event_not_registered
event_duplicate
event_limit_reached
statistic_not_registered
statistic_type_mismatch
exclusive_option_taken
exclusive_value_invalid
exclusive_requirement_incomplete
snapshot_invalid
phase_missing
revision_mismatch
permission_denied
```

玩家只看到可行动说明。主持人诊断显示定义 ID、实例 ID、函数、玩家、步骤和服务端原因；不得把堆栈直接暴露给普通玩家。

## 22. 资源上限

当前注册与持久边界为：

| 项目 | 上限 |
|---|---:|
| 全部 Pixel TZZ 定义 | `1024` |
| 每游戏任务 | `256` |
| 每任务结果/出边 | `32` |
| 单任务时长 | `51,840,000 Tick` |
| 单次间隔时长 | `12,096,000 Tick` |
| 每任务事件定义 | `64` |
| 单个 `repeatable` 事件记录 | `4096` |
| 每任务统计定义/持久值 | `64` |
| 每个逐玩家回调字段绑定 | `32` |
| 每个独占字段选项 | `128` |
| 单个冻结快照 | `2 MiB UTF-8` |
| 单时间线任务历史 | `256` |
| 单任务事件事实 | `4096` |
| 全局独占预约 | `4096` |

主持人时间线/玩家回顾网络投影另有独立有界预算：最多 `256` 个任务、`512` 条事件、`256` 个统计、`256` 个参与者头像、`512` 条回调以及 `524,288` 个文本组件字符。

不得使用“尽力而为”的无限列表。达到运行上限时保留已有记录，拒绝新增并报告诊断。

## 23. 核心契约片段

以下片段只展示 2D 新增字段，不是可独立编译的数据包。它还依赖 2A 定义的身份、生存状态、
阶段、字段与页面，以及所有被引用的 `mcfunction`。可直接编译和验收的完整实例见
[`../examples/pixel-tzz-base-datapack`](../examples/pixel-tzz-base-datapack)。

### 23.1 游戏入口

```json
{
  "format_version": 1,
  "api_version": 2,
  "content_version": 1,
  "name": {"text": "2D 验收游戏"},
  "initial_phase": "pixel_tzz:setup",
  "default_role": "pixel_tzz:runner",
  "default_life_state": "pixel_tzz:alive",
  "task_timeline": {
    "initial_task": "pixel_tzz:warmup/check",
    "pause_when_host_offline": false,
    "approval_phase": "pixel_tzz:awaiting_approval",
    "start_phase": "pixel_tzz:warmup",
    "pre_start_requirements": [
      {
        "type": "required_field",
        "audience": {
          "role_tags": ["pixel_tzz:hunter"],
          "exclude_host": true,
          "online_only": false
        },
        "field": "pixel_tzz:hunter_spawn"
      }
    ]
  }
}
```

### 23.2 赛前环节

```json
{
  "format_version": 1,
  "game": "pixel_tzz:main",
  "version": 1,
  "kind": "warmup",
  "name": {"text": "赛前确认"},
  "completion_policy": "event_only",
  "counts_toward_game_time": false,
  "recap_visibility": "participants",
  "results": [
    {
      "id": "ready",
      "name": {"text": "准备完成"},
      "semantic": "success",
      "route": {
        "next_task": "pixel_tzz:main/first",
        "phase": "pixel_tzz:running"
      }
    }
  ]
}
```

### 23.3 首个主任务

```json
{
  "format_version": 1,
  "game": "pixel_tzz:main",
  "version": 1,
  "kind": "main",
  "name": {"text": "第一项任务"},
  "description": {"text": "仅用于 2D 验收。"},
  "completion_policy": "early_or_timeout",
  "duration_ticks": 2400,
  "counts_toward_game_time": true,
  "live_visibility": {
    "future": "teaser",
    "history": "hidden",
    "candidate_visibility": "names_only"
  },
  "recap_visibility": "participants",
  "callbacks": {
    "on_start": "pixel_tzz:task/first/start",
    "on_timeout": "pixel_tzz:task/first/timeout"
  },
  "results": [
    {
      "id": "success",
      "name": {"text": "成功"},
      "semantic": "success",
      "route": {
        "end_phase": "pixel_tzz:ended",
        "intermission": {
          "duration_ticks": 200,
          "counts_toward_game_time": false,
          "name": {"text": "最终结算"}
        }
      }
    },
    {
      "id": "failure",
      "name": {"text": "失败"},
      "semantic": "failure",
      "allow_host_fallback": true,
      "route": {
        "end_phase": "pixel_tzz:ended",
        "intermission": {
          "duration_ticks": 200,
          "counts_toward_game_time": false,
          "name": {"text": "最终结算"}
        }
      }
    }
  ],
  "events": [
    {
      "id": "terminal_activated",
      "name": {"text": "认证终端已启动"},
      "policy": "once",
      "recap_visibility": "participants"
    }
  ],
  "statistics": [
    {
      "id": "remaining_players",
      "name": {"text": "剩余逃走者"},
      "type": "integer",
      "min": 0,
      "max": 64,
      "write": "replace",
      "recap_visibility": "participants"
    }
  ]
}
```

该示例只说明最小契约，不是正式玩法，不规定地图、任务成功条件或现场演出。
