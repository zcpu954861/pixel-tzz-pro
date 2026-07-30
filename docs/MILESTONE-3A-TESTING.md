# V3A 玩家终端实机验收手册

状态：**已完成并通过完整自动检查与用户逐项 Minecraft 多客户端实机验收**

- 记录日期：2026-07-29
- 收口日期：2026-07-30
- 模组版本：`0.1.0`
- Minecraft：`26.2`
- 网络协议：v11
- 世界状态：Schema v4

## 1. 验收原则

V3A 验收继续采用 2C/2D 已确认的节奏：

1. 主代理一次只发一项测试；
2. 用户完成后回复 `PASS` 或给出现象、截图和操作顺序；
3. 一轮内先完整记录问题，不零散修补；
4. 用户明确结束本轮后统一修复共享根因；
5. 修复后只回归受影响项及必要前置项。

自动检查只能证明 Schema、协议和权威边界，不能替代真实客户端的布局、动画、声音、静默刷新和多人同步验收。

## 2. 测试准备

### 2.1 自动门

不要启动 Minecraft。先在仓库根目录执行：

```powershell
.\gradlew.bat clean check build --warning-mode all --console=plain
git diff --check
```

V3A 重点任务：

```powershell
.\gradlew.bat foundationSelfCheck
.\gradlew.bat pageFrameworkSelfCheck
.\gradlew.bat protocolV11SelfCheck
.\gradlew.bat playerTerminalProjectorSelfCheck
.\gradlew.bat playerTerminalRouterSelfCheck
.\gradlew.bat playerTerminalSessionsSelfCheck
.\gradlew.bat playerTerminalRuntimeBridgeSelfCheck
.\gradlew.bat playerActionAuthoritySelfCheck
.\gradlew.bat statePersistenceSelfCheck
.\gradlew.bat worldStateColdStartSelfCheck
.\gradlew.bat timelineAuthoritySelfCheck
```

预期至少出现：

```text
FOUNDATION_SELF_CHECK=PASS
PAGE_FRAMEWORK_SELF_CHECK=PASS
PROTOCOL_V11_SELF_CHECK=PASS
PLAYER_TERMINAL_PROJECTOR_SELF_CHECK=PASS
PlayerTerminalRouterSelfCheck PASS
PlayerTerminalSessionsSelfCheck PASS
PLAYER_TERMINAL_RUNTIME_BRIDGE_SELF_CHECK=PASS
PLAYER_ACTION_AUTHORITY_SELF_CHECK=PASS
STATE_PERSISTENCE_SELF_CHECK=PASS
WORLD_STATE_COLD_START_SELF_CHECK=PASS
TIMELINE_AUTHORITY_SELF_CHECK=PASS
```

### 2.2 世界与数据包

只使用可丢弃的新世界或明确可重置的 V3A 验收世界，不在正式游玩存档测试。

完整复制：

```text
E:\minecraftserver\fabricmod\pixel-tzz-pro\examples\pixel-tzz-base-datapack
```

到：

```text
<验收世界>\datapacks\Pixel-Tzz-Base-0.1.0
```

不要只覆盖新增 `player_*` 目录。Game 已提升到 `api_version: 3`，任务事件也新增了玩家历史公开策略，必须使用完整同版副本。

配套资源包：

```text
E:\minecraftserver\fabricmod\pixel-tzz-pro\examples\pixel-tzz-base-resourcepack
```

加载后先确认：

```mcfunction
/reload
```

聊天与日志中没有数据包定义错误，模组加载 ActionBar 正常结束。

### 2.3 客户端

推荐：

| 玩家 | 权限 | 用途 |
|---|---|---|
| Player972 | OP | 主持人 |
| PlayerB | 普通玩家 | 逃走者终端 |
| PlayerC | 普通玩家 | 猎人终端 |
| PlayerD | OP、非主持人 | 普通终端加接管按钮 |
| PlayerE | 普通玩家 | 重连、受众与历史对照 |

批量启动：

```powershell
.\start-test-clients.cmd
```

脚本只负责按固定用户名和顺序启动客户端，不代表已经进入同一世界。

## 3. 当前夹具索引

### 3.1 页面

| ID | 说明 |
|---|---|
| `pixel_tzz:player/home` | 默认玩家首页 |
| `pixel_tzz:player/task_runner` | 逃走者当前任务 |
| `pixel_tzz:player/task_hunter` | 猎人当前任务 |
| `pixel_tzz:player/history` | 左侧过去任务轨迹与右侧原位回顾档案组成的玩家过去事件页 |
| `pixel_tzz:player/history_detail` | 保留的独立详情兼容示例；当前夹具不从历史页跳转至此 |
| `pixel_tzz:player/profile` | 本人授权数据 |
| `pixel_tzz:player/help` | 文档轮播入口 |
| `pixel_tzz:player/help_catalog` | 可直接选择的帮助目录 |
| `pixel_tzz:player/help_terminal` | 终端基础 |
| `pixel_tzz:player/help_visibility` | 可见内容与授权 |
| `pixel_tzz:player/help_history` | 事件回顾 |
| `pixel_tzz:player/help_actions` | 安全操作与函数验收 |

### 3.2 路由

| ID | 目标 |
|---|---|
| `pixel_tzz:runner_task` | 活动任务中的逃走者页 |
| `pixel_tzz:hunter_task` | 活动任务中的猎人页 |
| `pixel_tzz:ended_history` | 结束阶段的历史页 |

### 3.3 玩家函数夹具

按钮操作：

```text
pixel_tzz:acceptance_ping
```

函数：

```text
pixel_tzz:acceptance_3a/player/ping
```

证据：

```mcfunction
/data get storage pixel_tzz:acceptance_3a last_player_action
```

该 storage 不是权威游戏状态，不会推进阶段、任务或身份。

## 4. 测试顺序

### 3A-01 入口优先级

前置：

- Player972 是主持人；
- PlayerB 是普通玩家；
- PlayerD 是 OP，但不是主持人；
- 当前没有玩家处于强制流程。

操作：

1. 三人分别按 ESC；
2. 观察新增入口文字；
3. 分别点击。

预期：

- Player972 打开主持人控制台；
- PlayerB 打开普通玩家终端；
- PlayerD 也打开普通玩家终端，只额外看到低优先级接管按钮；
- PlayerD 不会因为 OP 身份看到主持人任务线、玩家全名单、回调审计或高风险操作；
- 右上角只有一处统一同步徽标，正文不重复“已同步服务端”。

### 3A-02 强制流程优先

前置：主持人对 PlayerB 发起尚未完成的通用初始化。

操作：

1. PlayerB 停留在不可关闭强制页；
2. 按 ESC；
3. 尝试关闭或进入普通玩家终端。

预期：

- ESC 只返回当前强制页；
- 不能打开或用普通终端覆盖强制流程；
- 完成流程后强制状态解除；
- 完成不会自动弹出普通玩家终端。

### 3A-03 默认首页与普通关闭

前置：PlayerB 没有活动强制流程，当前时间线尚未启动。

操作：

1. 从 ESC 打开玩家终端；
2. 查看首页；
3. 按 ESC 关闭终端；
4. 再按 ESC 打开。

预期：

- 默认进入 `pixel_tzz:player/home`；
- 首页单屏平铺，不出现整页滚动、重复“玩家终端”标题或大面积无意义空白；
- 当前阶段与数据包已公开的当前任务共用顶部进程区，过去事件、个人信息和终端帮助使用明确的平铺入口；
- 玩家头像为真实皮肤基础层加帽子层；
- 中文主标题清晰，英文只作弱化衬托；
- 身份、生命、准备等只显示已授权内容；
- 根页只在外壳左下角提供一个“退出终端”，正文不重复关闭入口，也不留下空底栏；
- 关闭回到游戏，不出现成功占页；
- 再次打开生成新 page instance，旧按钮请求不能复用。
- 打开终端的首帧只允许显示正常终端或同壳加载状态，不闪现红色安全错误页。

### 3A-04 页面导航与页面栈

操作：

1. 首页依次打开“过去事件”“个人信息”“终端帮助”；
2. 使用终端外壳唯一的“返回”；
3. 快速点击同一导航按钮两次；
4. 连续进入多个页面并返回。

预期：

- 页面切换、返回和关闭动画完整且方向正确；
- 二级页不再重复提供“返回玩家首页”“关闭终端”等同义按钮；
- 不插入风格不一致的“等待服务器”中间页；
- 快速双击不创建两个相同结果或破坏页面栈；
- 页面切换声音每次只播放一次；
- 栈深保护不会卡死客户端。

### 3A-05 身份专属任务路由

前置：

- PlayerB 为已初始化逃走者；
- PlayerC 为已初始化猎人并锁定出生点；
- 完成逐玩家准备，由主持人批准开局进入赛前任务。

操作：

1. B、C 分别从 ESC 打开终端；
2. 对比页面标题、语义色、任务说明和个人内容。

预期：

- B 进入 `pixel_tzz:player/task_runner`；
- C 进入 `pixel_tzz:player/task_hunter`；
- 猎人页显示本人锁定出生点，逃走者页没有该字段；
- 两页都不显示未来任务、候选分支、其他玩家字段或内部 ID；
- 主持人仍进入主持人控制台。

### 3A-06 当前任务静默更新

赛前任务中执行：

```mcfunction
/function pixel_tzz:acceptance_2d/warmup/complete
```

进入分支任务后，在 B、C 的任务页保持打开，执行：

```mcfunction
/function pixel_tzz:acceptance_2d/main/record_terminal
```

预期：

- 路由真实变化时只发生一次正常换页；
- 分支任务名称、说明、计时、状态和公开进度正确；
- 终端次数从 `0` 更新为 `1`；
- 更新只改变绑定和右上角状态，不使其他按钮闪烁、重新进入或重播声音；
- 暂停/继续时任务状态原地更新，不重建整页。

### 3A-07 当前任务与逐事件兼容边界

完成 3A-06 后打开“过去事件”。

预期：

- 示例 Game 使用 `history_source: "tasks"`，当前尚未结束的分支任务不会伪装成“过去任务”；
- 页面显示中性空状态，不在首次进入时闪出红色“记录失效”；
- 左侧只滚动任务轨迹区域，右侧详情区保持独立，不滚动整张玩家页面；
- 列表不出现回调 ID、任务实例 UUID、隐藏分支或完整后台统计；
- B、C 均只接收符合各自受众的裁剪版本。

需要额外验证旧逐事件模式时，在可丢弃的新验收世界把 Game 改为
`history_source: "events"`：此时“验收终端已触发”应在事件发生后立即出现；完成兼容检查后恢复
`tasks`。无论哪种模式，页面显示 `MM:SS` / `H:MM:SS`，不向玩家展示 `T+100 tick`；原始 Tick
仍保留在绑定中用于兼容与排序。

当左侧存在可查看项时：

- 只有服务端投影 `item.detail_available: true` 的记录显示详情按钮；没有详情配置的记录不占用空白按钮位；
- 第一次进入自动在右侧显示最新合法项，不需要先点一次“查看”，也不出现红色错误；
- 点击其他项只在 `pixel_tzz:player/history` 右侧原位替换详情，中间不出现客户端选页；
- 左侧列表选择保持可见，页面栈深度不增长，终端外壳的“返回”仍直接回上一层；
- 标题、摘要、时间、任务和触发者与列表中的同一记录一致；
- 详情元数据为紧凑键值行，长值自动换行，不是固定四格信息卡；
- 客户端绑定中只有 Boolean `detail_available`，没有事件配置的 `detail_page`；
- 请求只包含有界、不透明的记录键，不包含目标 page ID、任务实例 UUID、事件 ID 或整条 item JSON；
- 点击另一条事件只替换右侧详情；退出历史页后再返回，列表本身仍是服务端重新投影的权威内容；
- 让当前选择不再符合受众时，同页历史仍有其他合法项则自动回退并显示一次中性恢复说明；没有替代项时显示空状态；
- 其他不符合受众的玩家不能用复制的记录键打开详情。

旧记录拒绝是安全补充检查：在测试构建或协议验收工具中保留一条曾经投影的记录键，再构造超过 64 条
对同一查看者可见的记录，使该旧条目退出当前 `history.items`，随后重放原详情请求。预期服务端拒绝请求并
失效旧会话，客户端不打开详情；即使后台记录仍存在，也不能绕过当前精确投影窗口。普通玩家界面不需要
提供伪造或重放入口。

### 3A-08 任务结束公开历史

在分支任务提交成功结果：

```mcfunction
/function pixel_tzz:acceptance_2d/main/succeed
```

等待 60 秒结果间隔，再等待 `timeout_only` 的 10 秒自然到时及结果提交。

预期：

- `timeout_only/deadline_reached` 在任务结束前不提前泄露；
- 每个已结束任务在左侧只占一项，当前任务和未来任务不出现；
- “任务历史”项只汇总当前查看者实际可见且声明 `show_task: true` 的事件，计数不包含其他受众的记录；
- 任务结束后加入“仅超时任务完成”，右侧默认定位到最新合法任务；
- 左侧与右侧时间直接显示“开局后 00:58”“任务开始后 00:58”等玩家可读前缀；详情元数据使用
  “发生时间 / 任务内时间”，不显示原始 Tick 或含义不明的“游戏时刻”；
- 重新打开页面、重连和 `/reload` 后记录仍存在；
- 顺序按权威游戏 Tick，不按客户端收到时间；
- 结束阶段重新打开终端时，路由进入历史页。

### 3A-09 个人数据裁剪

分别查看 B、C、D 的“个人信息”。

预期：

- 每人只看到自己的 UUID 对应头像、身份、生命、初始化和准备；
- C 额外看到自己的已锁定猎人出生点；
- 该卡片标题中的“猎人出生点”来自
  `personal_meta/pixel_tzz:hunter_spawn/name`，不是页面内硬编码的中文字段名；
- 选中值显示“北门 / 中央站 / 南侧场地”等数据包 `options[].name`，来自
  `personal_meta/pixel_tzz:hunter_spawn/value_name`，不显示 `north_gate` 等机器值；
- 即使选项显示名因投影预算或 reload 变化暂时缺席，也只显示“出生点已锁定”等安全状态，
  不能回退显示原始机器值；
- B、D 不收到 C 的出生点；
- B、D 的绑定文档中也不出现孤立的 `personal_meta/pixel_tzz:hunter_spawn/name`；
- 缺值显示数据包定义的空状态，不显示 Java `null`、注册 ID 或错误堆栈；
- 客户端日志和绑定中没有未授权个人字段。

针对名称绑定可在可丢弃的数据包副本中，把
`player_data/hunter_spawn.json` 的 `name` 临时改为另一个不带外围括号的中文名称，并在没有活动冻结
时间线的新验收世界执行 `/reload`。预期 C 的同一张卡片只更新展示名称，不改变出生点实际值或页面
JSON；删除值、取消授权或让值超出投影预算时，名称也随值一起消失。完成后恢复示例文件。

### 3A-10 注册函数单击、冷却与宏

PlayerB 打开“终端帮助”，切换到“安全操作”文档并进入正文，点击“运行一次终端自检”，主持人读取：

```mcfunction
/data get storage pixel_tzz:acceptance_3a last_player_action
```

记录 `request_id`，随后：

1. 快速双击按钮；
2. 在 40 Tick 冷却内再次点击；
3. 等待超过 40 Tick 后再点一次并重新读取 storage；
4. 再触发一次冷却，正常退出并重启服务端，在剩余 40 Tick 内重新进入并点击。

预期：

- 首次只执行一次并显示成功反馈；
- 服务端短暂等待只改变右上角同步徽标；最终成功或失败使用短时紧凑提示，不插入新页面；
- storage 包含服务端生成的玩家、页面、节点、游戏实例、revision 和 request UUID；
- 快速双击与重复 request 不会执行两次；
- 冷却内稳定拒绝，不重绘整页；
- 冷却后新请求成功且 `request_id` 改变；
- 正常重启不会清空冷却；`world_tick` 延续主世界持久 `gameTime`，而不是从零开始；
- 函数不改变模组身份、任务结果或准备状态。

### 3A-11 数据包 reload

任一玩家保持普通终端打开，执行：

```mcfunction
/reload
```

预期：

- reload 不清除世界、任务、历史、动作次数或冷却；
- 普通终端按新定义重新路由或重发，不能继续操作旧 page instance；
- 活动时间线的任务名、历史公开策略和授权使用开局冻结版本；
- 即使故意让当前 `/reload` 编译失败，活动时间线已打开的终端仍使用冻结定义并保持可用；修复并恢复健康后，仅未开始的新局采用新 live 定义；
- 无变化的后台轮询不造成蓝字、同步徽标、按钮或页面周期闪烁；
- 玩家不需要先打开其他面板才能刷新 ESC 入口。

### 3A-12 掉线、重连与离线清理

1. PlayerB 在普通终端中掉线；
2. 等待主持人终端确认离线；
3. B 重连；
4. 不按 ESC 观察；
5. 再按 ESC 主动打开。

预期：

- 掉线清理普通终端会话、页面栈、摘要和确认挑战；
- 普通终端不会在重连时自动弹出；
- 若 B 仍在强制流程，强制页可以自动恢复；
- 主动打开后按当前权威路由生成全新会话；
- 不播放无关的数据包加载成功打字音。

### 3A-13 非主持人 OP 接管入口

PlayerD 为 OP、非主持人。

操作：

1. 在尚未认领主持人的新世界中，由 PlayerD 打开 ESC 玩家终端；
2. 确认系统入口为“认领主持人”，取消二次确认；
3. 由 Player972 认领主持人；
4. PlayerD 保持 OP、再次打开玩家终端；
5. 确认同一位置变为“接管主持人”，取消二次确认。

预期：

- 仍是与普通玩家相同的终端内容；
- 无主持人时只额外出现低优先级“认领主持人”；
- 已有其他主持人时同一入口变为“接管主持人”；
- 认领与接管均走既有权限重验、详细二次确认、服务器令牌和审计；接管再执行原子主持人替换；
- 取消确认后返回原玩家终端；
- 非主持人 OP 即使主动请求主持人控制台快照，服务端也不会下发完整 `ConsoleSnapshot`；
- 非主持人 OP 主动请求主持人页面预览目录或页面文档同样被拒绝；
- PlayerD 始终看不到主持人任务线、完整玩家名单、回调审计或高风险操作；
- 数据包页面不能伪造相同系统按钮或绕过确认。

### 3A-14 小窗口与 GUI 缩放

至少测试：

- GUI 缩放：自动、2、4、最大；
- 2560×1440；
- 明显缩小的窗口；
- 长玩家名；
- 历史 8 条以上。

预期：

- 在 `2560×1440 + GUI Scale 4` 下，普通玩家终端必须与固定化前已经验收的 `620×340`
  逻辑外壳、控件尺寸、间距和换行完全一致；其他尺寸只等比缩放这套 2K 母版，不切换卡片排列；
- 主持人控制台、全部操作、玩家名单、活动流程、时间线、页面目录、目标选择、二次确认、
  受影响玩家、数据驱动强制页、受限暂停页与安全错误页必须复用 `640×360` 母版；在同一
  `2560×1440 + GUI Scale 4` 环境下保持改造前的尺寸、位置、换行与动画；
- 大屏达到参考尺寸后停止放大并居中，小屏把外壳、文字、裁剪、滚动条和点击区域一起等比缩小；
- 小窗口中的数据驱动正文必须与玩家终端或强制流程外壳使用同一缩放比例；任务区、三个终端入口、身份说明和底部操作不能保持 2K 字号后被缩小的外壳裁切；
- 宽高比不匹配时只保留安全边距，不横向拉伸；核心按钮始终完整可达；
- 页面切换与返回动画、鼠标悬停、点击、滚轮和滚动条拖动必须与缩放后的可见位置一致；页面背景
  遮罩仍铺满整个实际窗口，不随母版缩成一块；
- 只有任务正文、本局数据、历史轨迹、历史详情等明确区域可以独立滚动，标题和底部操作不被裁切；
- 滚动不超过最后一项；
- 首页、个人信息和帮助轮播不出现整页滚动；
- 历史页仅左侧时间线和右侧详情各自滚动；
- 逃走者/猎人任务页不依赖整页滚动，且共享顶栏之外不重复头像、页面标题或返回按钮；
- 任务根页底栏保持“退出终端 → 三个数据包入口 → 可选接管主持人”的视觉与键盘顺序；
- 三个底栏入口保留各自的悬停、按压动画、声音、Tooltip 和服务端重新绑定，不因移入外壳失效；
- 帮助轮播左右箭头不遮挡文档正文，切换期间旧文档不可点击，返回后保持上次选中项；
- 英文不抢中文层级；
- 页面正文没有第二个同步状态。

### 3A-15 主持人转交时的即时降权

前置：Player972 是主持人；PlayerD 是 OP、非主持人。

操作：

1. Player972 打开“全部操作”，进入任一包含目标玩家信息的选择页并保持不动；
2. PlayerD 从普通玩家终端发起“接管主持人”并完成二次确认；
3. 同时观察 Player972 当前页面、ESC 入口和 PlayerD 页面。

预期：

- Player972 不会继续看到旧的主持人操作、目标名单或玩家缓存；
- 根控制台或任意子页面都会退出到普通玩家终端，不要求先逐级返回或重新开 ESC；
- 已提交的转交结果仍正常显示一次，不被降权同步吞掉；
- Player972 若仍是 OP，只在普通终端保留低优先级“接管主持人”；
- PlayerD 成为唯一当前主持人，并能打开完整主持人控制台；
- 快速重复点击不能让旧主持人继续执行缓存动作。

## 5. 人工压力覆盖包

正常流程难以自然生成十项长授权字段与十二条玩家历史，因此额外提供：

```text
examples/pixel-tzz-3a-pressure-datapack
```

把它复制到 `3A V1/datapacks/pixel-tzz-3a-pressure`，确保优先级高于基础包，执行 `/reload`
并重置验收局。聊天和日志中必须明确加载为：

```text
全员逃走中 · 3A 压力夹具
```

三项压力回归仍逐项验收：

1. 三个猎人出生点使用超长中文名称，仍保持三列、完整换行、占用头像和可达按钮；
2. 进入 `pixel_tzz:acceptance/main_branch` 后，个人信息显示十项长字段，左右区域分别滚动，
   标题和底栏固定，滚动末尾没有过量空白；
3. 在同一任务中执行：

   ```mcfunction
   /function pixel_tzz:acceptance_3a/pressure/record_twelve
   ```

   过去事件应出现 `压力记录 01` 至 `压力记录 12`，首次打开默认选中 12；左侧能准确滚到最后一项，
   右侧长详情独立滚动，切换选中项不会跳页或重置另一侧滚动。

测试完成后禁用覆盖包并 `/reload`，再重置验收局；基础包应恢复任务汇总历史、普通个人信息页面
和短出生点显示名。

## 6. 失败留证

每个失败至少记录：

- 测试编号；
- 精确点击顺序；
- 玩家 UUID、身份、OP/主持人状态；
- 当前阶段、任务和任务状态；
- 是否刚执行 `/reload`、重连、暂停或结果提交；
- 页面截图或短视频；
- 客户端与服务端 `latest.log` 中的稳定拒绝码；
- 必要时只读：

```mcfunction
/data get storage pixel_tzz:acceptance_3a
/data get storage pixel_tzz:acceptance_2d
```

不要通过手改 storage 推进测试；它不是模组权威状态。

## 7. 收口条件

V3A 只有同时满足以下条件才可标记完成：

- 全量 Gradle `check build` 通过；
- 3A-01～3A-15 必需项通过；
- 强制流程、主持人控制台和 2D 时间线无回归；
- 无未授权任务、历史或个人数据下发；
- 注册函数具备页面重新绑定、权限、次数、冷却、确认与防重放；
- reload、掉线、重连和普通后台刷新不造成整页闪烁或重复声音；
- 小窗口与常用 GUI 缩放下核心操作可达；
- 用户明确确认 V3A 实机验收完成后，才允许提交和推送。

收口结果：以上条件及人工压力覆盖包的超长选择值、十项长授权字段、十二条历史记录均已由用户
逐项确认 `PASS`；`gradlew check build --rerun-tasks` 全量通过。
