# V3B 动态文本与统一文字演出实机验收手册

状态：**全部必需实机项与完整自动门均已通过；V3B 已完成并通过 PR #5 合并主线**

- 编写日期：2026-07-31
- 模组版本：`0.1.0`
- Minecraft：`26.2`
- 网络协议：v12
- 客户端播放计划：FORMAT 2
- 世界状态：Schema v4

## 1. 使用方式

本手册是逐项测试主持脚本，不是一次性执行清单。

1. 主代理一次只向用户发送一项测试；
2. 用户完成后回复 `PASS`，或提供现象、截图、客户端、命令回显和操作顺序；
3. 收到反馈后才进入下一项；
4. 同一轮发现的问题先完整记录，用户明确结束该轮后再集中修复；
5. 修复后只回归受影响项目及必要前置项目；
6. 没有用户实机反馈的项目始终保持“未测试”，自动检查、客户端启动或日志无异常都不能替代实机结论。

每项只有一个通过标准。任何一个列出的客户端出现额外文字、重复声音、红色错误页、闪烁、越权内容或错误状态，该项就不能记为 PASS。

失败时至少记录：

- 项目编号；
- 发生问题的客户端；
- 操作前屏幕、操作命令或按钮路径、操作后屏幕；
- 是否只发生一次、是否可稳定复现；
- 命令完整回显；
- `logs/latest.log` 对应时间附近的首个异常；
- 涉及实例控制时记录实例 UUID；
- 涉及重启时记录退出前状态、离线时长和重连顺序。

## 2. 自动检查与实机边界

### 2.1 已有自动证据

当前实现阶段已经强制重编译并取得以下自动结果：

```text
MESSAGE_COLD_START_RECOVERY_CONTRACT_SELF_CHECK=PASS
MESSAGE_DELIVERY_LIFECYCLE_AUTHORITY_SELF_CHECK=PASS
MESSAGE_RUNTIME_PERSISTENCE_AUTHORITY_SELF_CHECK=PASS
MESSAGE_RUNTIME_PERSISTENCE_MAPPER_SELF_CHECK=PASS
MESSAGE_INSTANCE_AUTHORITY_SELF_CHECK=PASS
MESSAGE_FINAL_PROJECTION_TRANSPORT_CONTRACT_SELF_CHECK=PASS
MESSAGE_FINAL_ONLY_HANDSHAKE_POLICY_SELF_CHECK=PASS
MESSAGE_RECONNECT_PROJECTION_SELF_CHECK=PASS
MESSAGE_HISTORY_AUTHORITY_SELF_CHECK=PASS retained=128
MESSAGE_LIFECYCLE_POLICY_ROUTER_SELF_CHECK=PASS
```

这些结果只证明纯逻辑契约，例如绝对 TTL、恢复进度锚点、回调去重、四种重启动作、历史保留
上限、`final_only` 握手的固定 100 Tick 截止点、撤权后的更高 cycle `REFRESH`/无文本 `CANCEL`，
以及 `/reload` 与客户端资源 Reload 的权威边界。它们没有证明玩家实际看到的帧、声音、Chat
历史、页面时序、多人同步和兼容表现。

`messageFinalProjectionTransportContractSelfCheck` 还固定三条故障边界：同步 `STATIC_FINAL`
失败只关闭当前物理投影并保留权威 callback；`CANCEL` checkpoint 失败仍发送无文本清理；
`FINALIZE` checkpoint 失败降级为无文本 `CANCEL`。`messageReconnectProjectionSelfCheck` 固定
`connectionProjectionTerminal` / `connectionTerminalAction` 只属于当前物理连接，重连时重置，
并允许未终止逻辑实例通过新 `CREATE` 恢复而不重播历史声音或回调。

### 2.2 实机开始前的完整自动门

在仓库根目录执行：

```powershell
.\gradlew.bat clean check build --warning-mode all --console=plain
git diff --check
```

需要单独定位 V3B 失败时，可执行：

```powershell
.\gradlew.bat protocolV12SelfCheck
.\gradlew.bat messageDefinitionSelfCheck
.\gradlew.bat messageHookDefinitionSelfCheck
.\gradlew.bat messageHookFreezeClosureSelfCheck
.\gradlew.bat messageHookDispatchContractSelfCheck
.\gradlew.bat readinessAuthoritySelfCheck
.\gradlew.bat messagePolicySchemaSelfCheck
.\gradlew.bat messageFixtureSelfCheck
.\gradlew.bat messageTextTimelineSelfCheck
.\gradlew.bat messageFrameTimelineSelfCheck
.\gradlew.bat messagePrelayoutDurationSelfCheck
.\gradlew.bat messageVisualEffectsSelfCheck
.\gradlew.bat messageInstanceAuthoritySelfCheck
.\gradlew.bat messageFinalProjectionPlannerSelfCheck
.\gradlew.bat messageFinalProjectionTransportContractSelfCheck
.\gradlew.bat messageFinalOnlyHandshakePolicySelfCheck
.\gradlew.bat messageReconnectProjectionSelfCheck
.\gradlew.bat messageDeliveryLifecycleAuthoritySelfCheck
.\gradlew.bat messageSynchronizationAuthoritySelfCheck
.\gradlew.bat messageLifecyclePolicyRouterSelfCheck
.\gradlew.bat messageLiveContextAuthoritySelfCheck
.\gradlew.bat messageAssetAuthoritySelfCheck
.\gradlew.bat messageAssetPlaybackResolverSelfCheck
.\gradlew.bat messageServerAssetGateSelfCheck
.\gradlew.bat messageAssetProtocolSelfCheck
.\gradlew.bat messageHistoryAuthoritySelfCheck
.\gradlew.bat messageHistoryMaterializerSelfCheck
.\gradlew.bat messageHistoryReplaySanitizerSelfCheck
.\gradlew.bat messageRuntimePersistenceAuthoritySelfCheck
.\gradlew.bat messageRuntimePersistenceMapperSelfCheck
.\gradlew.bat messageColdStartRecoveryContractSelfCheck
.\gradlew.bat messageCommandsSelfCheck
.\gradlew.bat hostMessagePreviewSanitizerSelfCheck
.\gradlew.bat dynamicChatSpeakerSourceSelfCheck
.\gradlew.bat messageRegisteredEntitySpeakerSelfCheck
```

`examples/pixel-tzz-3b-pressure-datapack` 只由自动夹具使用。它会覆盖基础包同 ID 资源，不得安装进视觉验收世界。

## 3. 环境与固定分工

### 3.1 世界选择

推荐使用可丢弃的新世界，或已经通过 2D/V3A、可以随时恢复备份的验收世界。不得使用正式游玩存档。

- 新世界：状态最干净，适合历史、晚加入和冷启动四模式；安装基础包后，需按
  [`MILESTONE-2D-TESTING.md`](MILESTONE-2D-TESTING.md) 的 2D-01～2D-06 准备需要的
  `pixel_tzz:running` / `pixel_tzz:acceptance/main_branch` 上下文。
- 已有世界：可以直接复用身份和任务上下文，但开始前必须备份；旧消息历史、活动实例和旧测试 Storage 必须清楚记录，不能误当本轮结果。

完整复制仓库目录：

```text
examples/pixel-tzz-base-datapack
```

到验收世界：

```text
<验收世界>\datapacks\Pixel-Tzz-Base-0.1.0
```

必须复制完整同版目录，不能只覆盖 `message_cues` 或 `text_effects`。临时改策略时只修改这个“世界内副本”，绝不修改仓库中的示例源文件。

### 3.2 五客户端

在仓库根目录用 PowerShell 7 批量启动：

```powershell
.\start-test-clients.cmd
```

默认按 2 秒间隔和以下顺序启动，窗口为 `2560×1440`：

| 客户端 | 固定职责 |
|---|---|
| Player972 | 当前主持人、命令执行、主持人预览 |
| PlayerB | 普通逃走者、ESC/强制页/本地偏好 |
| PlayerC | 已初始化猎人、身份受众与历史 |
| PlayerD | 非主持人 OP、权限隔离与多人对照 |
| PlayerE | 晚加入、掉线、TTL 与重连 |

Player972 进入世界后开放到局域网；B～E 加入同一世界。需要测试“非主持人 OP”时只给 PlayerD 原版权限，不得把主持人身份转交给 D。

### 3.3 基础夹具

由要观察演出的玩家先执行：

```mcfunction
/function pixel_tzz:acceptance_3b/setup
```

清理本轮临时状态：

```mcfunction
/function pixel_tzz:acceptance_3b/reset
```

`reset` 会同时取消全部验收 cue、清除捕获夹具尚未触发的三个 `schedule`、测试标签、分数、
`bad_call` 与全部验收 Storage 证据位；重复执行 `setup` 也会先走同一清理路径，不应继承上一轮
实例或延迟写入。

辅助证据：

```mcfunction
/data get storage pixel_tzz:acceptance_3b call
/data get storage pixel_tzz:acceptance_3b all_call
/data get storage pixel_tzz:acceptance_3b layout_call
/data get storage pixel_tzz:acceptance_3b layout_all_call
/data get storage pixel_tzz:acceptance_3b state
/data get storage pixel_tzz:acceptance_3b last_callback
```

`field_capture.target` 是必填的单玩家参数：单目标命令可由唯一 `call_target` 自动补入；`to @a`
没有唯一目标，必须使用 `all_call`，布局压力的全体命令必须使用 `layout_all_call`。不得把
`call`／`layout_call` 误用于多目标命令，也不得用 `invoker` 充当隐式演出受众。

## 4. A 组：环境、资源与权限冒烟

### 3B-01 数据包安装与世界基线

**前置状态**

- 已决定使用新世界或已有可丢弃世界；
- 已制作世界备份；
- 世界内安装的是当前完整基础包，未安装压力覆盖包。

**操作客户端与路径**

1. Player972 进入世界并执行：

   ```mcfunction
   /reload
   /function pixel_tzz:acceptance_3b/setup
   /pixel_tzz_pro message list 1
   ```

2. B、C、D、E 依次加入。

**五端观察**

- Player972：`/reload` 无定义错误；目录中至少有 `pixel_tzz:acceptance/field_capture` 和 `pixel_tzz:acceptance/policy_matrix`；没有压力覆盖包文案。
- PlayerB：加入后不弹红色错误页，不自动播放验收演出。
- PlayerC：同 B。
- PlayerD：同 B，OP 身份不触发额外消息目录或预览。
- PlayerE：同 B。

**唯一通过标准**

五端协议握手成功、资源 generation 可用、两条基础 cue 可列出，且普通玩家没有看到错误页面或未调用内容。

**失败记录**

记录世界选择、数据包列表、`/reload` 首条错误、缺失 cue ID，以及发生异常的客户端日志。

状态：**通过**

### 3B-02 五客户端与静默资源同步

**前置状态**

- 3B-01 通过；
- 五端均在世界内且没有活动演出。

**操作客户端与路径**

1. 五端分别按 ESC，观察右上角同步状态；随后进入“选项”，确认“全员逃走中设置…”入口可达；
2. 保持页面 15 秒，不点击按钮；
3. 返回游戏，再保持 15 秒。

**五端观察**

- Player972：仍进入主持人控制台；模组设置入口位于原版选项页；无变化的资源/能力轮询不重建页面。
- PlayerB：普通玩家终端可达；原版选项页中的模组设置入口可达；同步徽标不周期闪烁。
- PlayerC：同 B。
- PlayerD：仍是玩家终端，只多接管主持人入口；不会看到主持人消息工具。
- PlayerE：同 B。

**唯一通过标准**

五端 30 秒内没有周期性蓝字、同步徽标、按钮或背景闪烁，也没有重复加载成功声音。

**失败记录**

记录闪烁间隔、闪动元素、是否伴随声音、停留页面和客户端。

状态：**通过**

### 3B-03 目录、审阅、预览与权限

**前置状态**

- Player972 是主持人；
- PlayerD 是 OP 但不是主持人；
- 已清除 `last_callback`。

**操作客户端与命令**

Player972 依次执行：

```mcfunction
/pixel_tzz_pro message list 1
/pixel_tzz_pro message inspect pixel_tzz:acceptance/field_capture
/pixel_tzz_pro message preview pixel_tzz:acceptance/field_capture with storage pixel_tzz:acceptance_3b call
/data get storage pixel_tzz:acceptance_3b last_callback
```

PlayerD 尝试相同的 `list`、`inspect` 和 `preview`。

**五端观察**

- Player972：目录分页、有限审阅和仅本人预览成功；审阅不泄露完整 JSON、回调函数名或隐藏参数；预览只在本端播放。
- PlayerB：没有收到预览、命令回显或历史。
- PlayerC：同 B。
- PlayerD：三种管理命令均被服务端拒绝；红色回显明确写明“当前玩家不是主持人”，并提示
  非主持人 OP 不会获得权限，而不是只显示原版未知命令/语法错误。
- PlayerE：同 B。

**唯一通过标准**

只有当前主持人能审阅和仅本人预览；预览不执行正式回调、不写历史、不影响任何其他客户端。

**失败记录**

记录具体越权命令、服务端回显、收到内容的错误客户端，以及 `last_callback` 是否被意外创建。

状态：**通过**

## 5. B 组：四通道、视觉与动态字段

### 3B-04 四通道统一播放

**前置状态**

- 五端均回到游戏画面；
- 字效设置恢复默认；
- 执行过 `acceptance_3b/setup`。

**操作客户端与命令**

Player972 执行：

```mcfunction
/pixel_tzz_pro message play pixel_tzz:acceptance/field_capture to @a with storage pixel_tzz:acceptance_3b all_call
```

**五端观察**

- Player972：看到一个原位增长的 Chat、一组 Title/Subtitle、一个 ActionBar 和一次主声音轨。
- PlayerB：与主持人内容一致，起点没有明显落后一整段。
- PlayerC：同 B。
- PlayerD：同 B，OP 身份不改变内容。
- PlayerE：同 B。

**唯一通过标准**

一次调用在五端各产生且只产生一套 Chat、Title、Subtitle、ActionBar 演出，四通道最终正文完整，主字符音不因多通道叠成双声。

**失败记录**

按客户端记录缺失/重复通道、起点差异、最终正文、声音次数和命令返回的实例 UUID。

2026-08-03 五端实机回归：一次全体调用在 Player972、PlayerB、PlayerC、PlayerD、PlayerE 各自产生
且只产生一套 Chat、Title、Subtitle 与 ActionBar 演出，正文、同步和单次主声音轨符合预期。

状态：**通过**

### 3B-05 Chat 原位记录与普通聊天共存

**前置状态**

- 五端打开聊天历史，保留 3B-04 的最终消息；
- 没有第二条活动演出。

**操作客户端与命令**

1. Player972 再次播放给 PlayerB：

   ```mcfunction
   /pixel_tzz_pro message play pixel_tzz:acceptance/field_capture to PlayerB with storage pixel_tzz:acceptance_3b call
   ```

2. 动画仍在进行时，PlayerC 连续发送两条普通聊天；
3. B 滚动聊天历史，再关闭并重开聊天；972 只核对命令回显和普通聊天，不应出现目标演出。

**五端观察**

- Player972：只看到命令回显与 C 的普通聊天，不看到面向 B 的动态 Chat。
- PlayerB：同样只有一个动态条目；最终文本留在历史中。
- PlayerC：自己的两条签名聊天正常，不被延迟、改写或删除；不接收目标演出。
- PlayerD：只看到 C 的普通聊天，不看到目标演出。
- PlayerE：同 D。

**唯一通过标准**

每个目标客户端的动态 Chat 从始至终只占一个逻辑条目，普通聊天可正常顶起它，完成后最终正文仍在原版聊天历史。

**失败记录**

记录动态条目数量、普通聊天是否丢失/延迟、滚出可视区后的状态和 Chat 设置。

2026-08-03 五端实机回归：PlayerB 的动态 Chat 始终只占一个逻辑条目，PlayerC 的两条普通聊天
正常顶起该条目且未丢失、延迟或改写；动画结束后最终正文仍保留在原版聊天历史，非目标端未收到
目标演出。

状态：**通过**

### 3B-06 光标、新字覆盖色与回色

**前置状态**

- PlayerB 使用默认字效偏好；
- 只让 B 观察，其他端作为泄漏对照。

**操作客户端与命令**

Player972 执行：

```mcfunction
/pixel_tzz_pro message play pixel_tzz:acceptance/field_capture to PlayerB with storage pixel_tzz:acceptance_3b call
```

**五端观察**

- Player972：只看到命令回显，不接收面向 B 的演出。
- PlayerB：观察块状光标、刚显文字临时色和渐变回色；光标块与新字颜色是两个独立元素；中文、空格、标点不破碎；已有多色样式最终恢复正确。
- PlayerC：不接收本次目标内容。
- PlayerD：同 C。
- PlayerE：同 C。

**唯一通过标准**

目标端没有旧值/占位闪烁；光标、新字临时色和渐变回色顺序正确，结束后只剩注册的最终富文本样式。

**失败记录**

2026-08-01 实机复测未通过：

- Chat 演出期间实际为“蓝色动态文本及符号一行 + 正文一行 + 额外空白一行”，最下方空白行必须移除；
- 演出结束写入原版历史后，两行内容被重新压缩成一行，产生明显的保存跳变；
- 期望动画态与历史态复用完全相同的行布局：若注册内容在演出中占两行，历史记录也必须保持相同两行和相同断行，让玩家无法察觉从演出转为保存记录；
- 本项暂只记录，等待本轮验收结束后统一修复。

2026-08-03 五端实机回归：PlayerB 的光标块、新字覆盖色与渐变回色顺序正确，中文、空格和标点
未破碎；动画期间无额外空白行，动画态与历史态保持相同断行和布局，非目标端未收到演出。

2026-08-03 后续长文本复核发现：动画正文下方仍存在一条额外空白行；历史落盘虽然不再压缩布局，
但把这条空白行一并保存，因此动画态与历史态“看似一致”掩盖了无效第三行。必须同时移除动画态
和历史态的尾部空白行，不能仅靠保存相同空白来满足无跳变标准。

同轮继续核对确认问题具有统一性：当前所有带 `『xxx』` 标签的动态 Chat 都倾向于让标签独占
第一行、正文从第二行开始，即使整条最终文本在当前聊天宽度内足以放入一行；较长字段还会在右侧
仍有大量空间时提前产生第三行。`『xxx』` 只是数据包注册的连续文本标签，不代表换行语义。除非
数据包组件显式声明换行，否则最终内容能放入一行就必须只占一行；自然宽度确实不足时才允许按
原版规则换行。修复必须覆盖所有动态 Chat，而不是只针对 `field_capture` 或 `policy_matrix` 文案。

2026-08-03 本轮统一修复已落地：Chat 分行现在只使用真实已解析正文与显式换行，隐藏测量预留
不再参与原版分行或进入历史；动画与注册停留期间保持完全不透明，停留结束后重置消息年龄，再
交还原版渐隐。HUD 预排版边界保持不变。尾部空白行、虚假提前换行、动画态与历史态断行跳变及
过早渐隐仍待五端实机复测。

2026-08-04 首次回归被客户端崩溃阻断：Player972 开放局域网后，PlayerB 加入使两端依次处理
“加入游戏／退出游戏”原版系统 Chat；`ChatComponentProjectionMixin` 的两个集合字段依赖 Mixin
隐式构造器初始化，但该初始化没有进入原版 `ChatComponent` 构造流程，运行时字段保持 `null`。
系统 Chat 队列整理首先触发 NPE 并断线，随后断线清理再次触发 NPE，最终两端同时崩溃。修复已将
投影表与强制不透明集合改为各入口共享的惰性初始化，覆盖普通系统 Chat、原版重建、断线清理和
动态消息入口；结构回归检查已加入 `messagePrelayoutDurationSelfCheck`，待重新实机复测。

2026-08-04 修复后实机回归通过：Player972 在现有 3A V1 开放局域网后，PlayerB 正常加入，两端
均未崩溃，普通加入系统 Chat 正常；统一动态 Chat 的尾部空白行、虚假提前换行以及动画态／历史态
断行跳变也未再复现。

状态：**通过**

### 3B-07 三种动态字段捕获时机

**前置状态**

- PlayerB 在游戏画面；
- `pixel_tzz_demo` 已由 setup 建立；
- 没有同 ID 活动实例。

**操作客户端与命令**

Player972 执行：

```mcfunction
/execute as PlayerB run function pixel_tzz:acceptance_3b/capture/start
```

播放结束后执行：

```mcfunction
/data get storage pixel_tzz:acceptance_3b state
/data get storage pixel_tzz:acceptance_3b last_callback
```

**五端观察**

- Player972：不应因命令来源看到 B 的私有目标演出；只看到命令回显。
- PlayerB：`target_name` 在显示开始锁定；Chat 的 `score_now` 在第一个动态字段前锁定为 `25`；Subtitle 的 `storage_note` 显示“最新 Storage 数据（per_field 锁定）”。
- PlayerC：不接收 B 的演出。
- PlayerD：同 C。
- PlayerE：同 C。

**唯一通过标准**

三种字段分别在 `on_display`、`on_first_field`、`per_field` 的注册时机取到预期值，字段出现前不闪 fallback，出现后不再跳值，完成回调只生成一次。

**失败记录**

记录错误字段、实际值、闪过的旧值、发生 Tick 先后、最终 `state` 和 `last_callback`。

2026-08-01 实机复测通过：仅 PlayerB 可见；Subtitle 与 Chat 字段正常，Chat 锁定为 `25`；
最终 `state.step` 为 `capture_finished`，`latest_score` 为 `25`，完成回调只记录一次。

状态：**通过**

### 3B-08 长中文、Emoji、换行与富文本交互

**前置状态**

- B 为主要观察端；
- 没有活动实例。

**操作客户端与命令**

Player972 执行：

```mcfunction
/data modify storage pixel_tzz:acceptance_3b call.label set value '{"text":"很长的中文字段🙂‍↕️｜组合字素 é｜第二段内容用于检查自动换行与最终富文本样式","color":"aqua"}'
/pixel_tzz_pro message play pixel_tzz:acceptance/field_capture to PlayerB with storage pixel_tzz:acceptance_3b call
```

在 Chat 已显示的 `『动态文本』` 上悬停。

这里的 `『动态文本』` 只是
`message_cues/acceptance/field_capture.json` 注册的验收示例文案；数据包可以修改或删除它，
模组 Java 代码没有硬编码该标题。

**五端观察**

- Player972：只看到命令回显，不接收面向 B 的演出。
- PlayerB：可见内容按真实字体预排版，不随每个字符反复挤动已显示行；Emoji、组合字符和中文不被拆坏；换行稳定；只在已显示文字上出现 hover 区域；
  原版历史、复制内容和客户端聊天日志都没有预排版占位字形，结束后只保留完整最终正文。
- PlayerC：不接收目标演出。
- PlayerD：同 C。
- PlayerE：同 C。

**唯一通过标准**

长中文、Emoji、组合字素、自动换行和 hover 富文本在目标端完整稳定，未显示文字与光标没有
不可见交互区域，测量占位不进入正文、日志、保存状态或复制结果。

**失败记录**

2026-08-01 实机复测未通过：

- 命令明确指定 `to PlayerB`，但 Player972 也收到了演出；PlayerC、D、E 未收到；
- Player972 收到的 Chat 动态计分为 `0`，PlayerB 为预期的 `25`；后续源码核对确认并非运行时擅自
  扩大受众，而是示例 `field_capture` 明确把 `call_targets` 与 `invoker` 注册为并集，导致 972
  合法进入了该夹具的冻结受众并按自身上下文解析字段；
- 3B-13 再次用两个独立的 `to PlayerB`／`to PlayerC` 实例复现，证明数据包夹具受众声明与“显式
  `to` 只投影给目标”的验收预期冲突；统一修复时应移除该示例的 `invoker` 受众项，不修改通用
  服务端受众合并语义；
- 长中文示例中的空格显示正常；异常集中在 Emoji／组合字素解析，需要单独核对字素分段、字体测量与组件样式边界；
- 3B-06 已记录的动画态/历史态断行变化仍作为独立问题处理；
- 本项暂只记录，等待本轮验收结束后统一修复。

2026-08-03 五端实机回归：PlayerB 的长中文、Emoji、组合字素、自动换行、Hover 富文本与目标受众
均符合预期；未显示内容没有提前产生交互区域。另发现的尾部空白行属于 3B-06 共享 Chat 布局问题，
已单独回记，不影响本项字素与富文本结论。

状态：**通过**

## 6. C 组：并发、刷新与主动控制

### 3B-09 同 ID 去重

**前置状态**

- 恢复默认 `call`：

  ```mcfunction
  /function pixel_tzz:acceptance_3b/setup
  /data remove storage pixel_tzz:acceptance_3b last_callback
  ```

- 五端都在游戏画面。

**操作客户端与命令**

Player972 执行：

```mcfunction
/function pixel_tzz:acceptance_3b/conflict/ignore_while_active
```

**五端观察**

- Player972：函数内部两次调用不透传嵌套命令回显；通过最终只有一套内容、一次回调和实例列表确认第二次被忽略。
- PlayerB：不接收只面向 Player972 这一显式 `call_target` 的内容。
- PlayerC：同 B。
- PlayerD：同 B。
- PlayerE：同 B。

**唯一通过标准**

同 Tick 重复调用只创建一个实例、一个 Chat 条目、一次主字符音和一次完成回调，忽略结果不伪装成第二个实例。

**失败记录**

记录外层 `/function` 是否进入、实例 UUID、Chat 数量、声音次数和回调次数；不要要求或等待内部
`message play` 命令反馈透传，也不要把没有内部回显误判为运行时无反馈。

2026-08-01 实机复测通过：重复调用最终只有一个实例、一个 Chat 条目、一次声音和一次完成回调。
由于两次调用封装在数据包函数内，内部“相同演出正在活动”反馈没有透传到玩家聊天；这是当前验收
夹具的可观测性限制，不作为运行时去重失败。

状态：**通过**

### 3B-10 跨实例通道排队

**前置状态**

- 五端在游戏画面；
- 没有活动 `field_capture`。

**操作客户端与命令**

Player972 执行一次自动验收入口；函数在同一 Tick 创建两个目标集合不同的实例，不要求测试者
手动抢时间连续输入：

```mcfunction
/function pixel_tzz:acceptance_3b/queue/run
```

两次调用的目标集合不同，因此应创建两个实例。

**五端观察**

- Player972：Chat 可并行存在两条真实记录；Title、Subtitle 和 ActionBar 按通道策略排队，不互相覆盖成半句。
- PlayerB：只收到第二个实例的一套四通道内容。
- PlayerC：同 B。
- PlayerD：同 B。
- PlayerE：同 B。

**唯一通过标准**

Player972 的两个实例都完整结束，Chat 并行而三个 HUD 文字通道有序排队；B～E 各只收到面向全体的第二个实例。

**失败记录**

记录两个 UUID、各通道先后、被覆盖正文、队列停滞和错误受众。

2026-08-01 实机复测：跨实例排队本身通过，三个 HUD 通道均按顺序完整播放。

同时发现独立的 ChatScreen 问题：PlayerB 按 `T` 打开原版聊天框后，看不到正在进行的 Chat
打字动画；关闭聊天框后才突然看到最终历史记录。期望聊天框打开期间也继续显示同一条原位动画，
关闭后无补跳、无重复、无重新创建历史条目。本问题暂只记录，待本轮验收结束后统一修复。

2026-08-03 补充可见性要求：Chat 原位打字动画播放期间必须禁止原版聊天条目渐隐；动画完成后
还必须按注册的正文停留时间保持完全不透明，不能刚显示最终正文便开始渐隐。停留结束后才允许进入
原版渐隐流程。本问题暂只记录，待本轮验收结束后统一修复。

同日补充版式复现：演出过程中 Chat 有时会临时重排为三行，随后又缩回较少行数，产生明显跳动。
该现象与此前额外空白行、动画态和历史态换行不一致属于同一类问题；最终修复必须让一条消息从首个
可见帧到历史落定共用同一行布局，不因字符逐步出现或保存为历史而增减行数。

另发现生命周期 Subtitle 排队问题：已有 Subtitle 播放时执行“结束赛前任务”的数据包函数，后续
任务／阶段 Hook 生成的 Subtitle 会等待前一条消失，但取得通道后只闪一下便直接显示最终正文，
没有从头播放打字动画。期望排队等待期间不消耗该条可见动画时间，轮到后从第一帧完整播放；
任务和阶段 Hook 接入 3B 演出系统后必须保持此前已验收的 Subtitle 观感：等待前项结束、完整
打字、正文正常停留后再退出，不得因接入新系统而闪帧、跳最终态或缩短原有停留时间；
后续反证：取消故障的 `policy_matrix` 实例后，队列中的“由主持人暂停任务”Subtitle 随即从头
完整播放，说明通用 Subtitle 队列并未全面失效；此前闪最终态应重点检查阶段／任务切换批量 Hook
的计划时间、迟到策略或队首阻塞恢复，而不是整体替换队列机制；
本问题暂只记录，待本轮验收结束后统一修复。

2026-08-03 上述 Chat 渐隐与布局修复已经落地；3B-10 的跨实例排队结论不受影响，Chat 动画
期间及停留结束边界仍随统一 Chat 回归一起实机复测。

2026-08-03 五端实机回归：单函数在同一 Tick 创建两个目标集合不同的实例；Player972 的 Chat
并行保留两条记录，Title、Subtitle、ActionBar 依次完整播放且没有闪帧、抢占或停滞；B～E 各自
只收到面向全体的第二个实例。

状态：**通过**

### 3B-11 原位 refresh

**前置状态**

- 界面显示游戏阶段为「任务验收」（内部注册名 `pixel_tzz:running`）；
- 界面显示当前任务为「2D 验收 · 分支任务」（内部注册名 `pixel_tzz:acceptance/main_branch`）；
- PlayerC 是已初始化猎人；
- 没有活动 `policy_matrix`。

**操作客户端与命令**

Player972 执行：

```mcfunction
/function pixel_tzz:acceptance_3b/conflict/refresh
```

**五端观察**

- Player972：函数内部的“已原位刷新”不透传到聊天；Chat 稳定更新、不新增第二条，并通过实例列表确认身份不变、cycle 增加。
- PlayerB：不是本次授权受众，不接收内容。
- PlayerC：该函数用 `to @s` 把函数执行者 Player972 写入唯一 `call_target`，因此 C 不接收本轮主持人 refresh 内容。
- PlayerD：不因 OP 收到内容。
- PlayerE：不接收内容。

**唯一通过标准**

合法上下文中第二次调用只刷新现有稳定实例和 Chat 条目，不重建第二实例、不重复已过去声音、不重复已执行回调。

**失败记录**

记录上下文、实际投影、UUID/cycle、Chat 条目数、声音和回调；不要要求 `/function` 透传内部
命令反馈。若要直接核对回显，应改用两条直接 `message play` 命令。

2026-08-01 实机复测未通过：函数执行后没有任何可见投影。服务端日志确认同一实例
`ae729253-fc40-4bb7-8258-37c6b11baaf5` 已创建，第二次调用也将其刷新至 cycle 1；但依赖冻结持续失败，
明确报错 `message hook dependency predicate has no retained document: pixel_tzz:acceptance_3b/is_hunter`，
导致计划无法投影、完成回调的 PREPARED 标记也无法写入，并产生逐 Tick 重试日志。本项不是上下文或
受众观察错误，需要修复谓词依赖文档的保留／冻结链路后重测。
执行 `message control cue ... cancel` 后明确回显取消 1 个实例，逐 Tick 错误停止；被其阻塞的后续
Subtitle 正常获得通道并完整播放，当前分支任务未受取消操作影响。

2026-08-03 实机回归发现新的 Chat 预排版问题：最终内容 `『策略验收』 Player972 · 2D 验收 ·
分支任务` 本身没有换行且足以放入当前聊天宽度，却被统一拆成“标签第一行、正文第二行”，随后在
第二行右侧仍有大量可用宽度时又把“分支任务”单独提前折到第三行。数据包任务名称也确认是单一
连续文本。当前证据指向动态字段 `detail` 的预留宽度在最终可见帧仍参与原版分行，隐藏预留字形被
移除后留下了肉眼可见的空余；最终历史不能为了维持动画布局而保留这种虚假提前换行。该问题与
3B-06 尾部空白行同属统一 Chat 预排版投影 BUG，待本轮结束后统一修复。

2026-08-03 Predicate 冻结修复与统一 Chat 预排版修复均已落地；合法 refresh 的实例身份、cycle、
原位条目、真实换行及历史布局待实机回归。

2026-08-04 修复后实机回归通过：在现有 `running/main_branch` 上下文执行专用 refresh 函数，
同一目标只保留一条「策略验收」Chat，未创建重复历史条目；非目标端未收到内容。

状态：**通过**

### 3B-12 实例级暂停、继续、补全与取消

**前置状态**

- 清除 `last_callback`；
- 记录播放命令返回的实例 UUID。

**操作客户端与命令**

第一轮：

```mcfunction
/function pixel_tzz:acceptance_3b/control/start_paused
/pixel_tzz_pro message control instance <第一轮 UUID> resume
/pixel_tzz_pro message control instance <第一轮 UUID> complete
/data get storage pixel_tzz:acceptance_3b last_callback
```

第二轮：

```mcfunction
/data remove storage pixel_tzz:acceptance_3b last_callback
/function pixel_tzz:acceptance_3b/control/start_paused
/pixel_tzz_pro message control instance <第二轮 UUID> cancel
/data get storage pixel_tzz:acceptance_3b last_callback
```

**五端观察**

- Player972：暂停时文字与字符音冻结同一进度；继续原位恢复；补全立即显示最终正文；取消移除/终止活动内容。
- PlayerB：不接收主持人单目标实例。
- PlayerC：同 B。
- PlayerD：同 B。
- PlayerE：同 B。

**唯一通过标准**

实例级四种控制均只影响指定 UUID；`complete` 产生一次正常完成回调，`cancel` 不产生正常完成回调，重复控制保持幂等。

**失败记录**

记录每轮 UUID、控制回显、暂停帧、声音续播位置、最终 Chat 和 `last_callback`。

2026-08-01 第一轮实机观察：实例成功创建并在首字符前暂停，指定 UUID `resume` 后可正常开始播放；
但 Subtitle 动画态曾完整占满两行，播放完成后最终静态态重新排版为“第一行占满、第二行只保留
少量字符后省略”，已经显示的后半正文被突然收回。期望最终静态态与动画最后一帧完全一致：能在
注册的两行内放下就完整保留；确需省略时也必须从动画开始便使用同一最终布局，绝不能先展示再隐藏。
第二个实例 `4dfffcf4-76af-4e8f-8128-8dd10b591d06` 实机确认实例级暂停通过：文字、光标和
字符音在同一进度冻结，持续观察 3 秒均未推进；
同一实例恢复后从原进度继续而非从头播放，实机通过；本轮未能在自然播放结束前执行手动
`complete`，因此截图中的单次正常 `last_callback` 只证明自然完成链路，手动补全仍待单独测试。
恢复瞬间 Subtitle 观感较突兀，但用户明确不将其列为本轮阻断问题；
第三个暂停实例 `c6c89c37-bfeb-4ddc-bc8e-1ed3aad621d0` 执行实例级 `complete` 后立即显示
最终正文，并只产生一次正常完成回调，手动补全通过；
第四个暂停实例 `c7319a76-8f45-45af-95d2-45826ba78662` 执行实例级 `cancel` 后明确取消
1 个实例，`last_callback` 路径不存在，取消未伪装成正常完成。暂停、恢复、补全与取消的实例级
控制语义全部通过；Subtitle 完成态重排问题暂只记录、待统一修复。

2026-08-03 修复后实机回归：实例 `88d5f643-7b8d-4132-ad59-c1b8b2ac382f` 暂停后恢复并从原进度
继续；实例 `5aa141d4-6e36-4b69-8f6f-90ea273c3f22` 在暂停态手动补全后立即显示最终正文，且
`last_callback` 只记录该实例一次正常完成；清空观察位后，实例
`19fb5877-6ed7-4f39-a526-9da878f03433` 被取消并未产生正常完成回调。四种实例级控制均通过。

测试工具另有一处文档问题：`message list` 展示的是注册消息目录，不是活动实例；包装函数又不会
透传内部 `play` 返回的 UUID。本轮从本地日志取得 UUID 完成验证，后续应为验收包装或管理命令补充
可直接取得活动实例身份的入口，避免依赖日志。

状态：**通过**

### 3B-13 cue、group 与 target 控制

**前置状态**

- 清空活动 `field_capture`；
- B、C 在游戏画面。

**操作客户端与命令**

按三轮分别执行，不要混在同一轮：

1. cue 轮：启动后执行

   ```mcfunction
   /pixel_tzz_pro message control cue pixel_tzz:acceptance/field_capture pause
   /pixel_tzz_pro message control cue pixel_tzz:acceptance/field_capture resume
   ```

2. group 轮：执行单命令入口；函数在同一 Tick 创建实例并按 `acceptance_capture` 分组暂停，
   随后手动按同一分组补全：

   ```mcfunction
   /function pixel_tzz:acceptance_3b/control/start_group_paused
   /pixel_tzz_pro message control group acceptance_capture complete
   ```

3. target 轮：执行单命令入口，在同一 Tick 分别创建 `to PlayerB` 和 `to PlayerC` 的实例，
   随后只按 PlayerB 寻址补全：

   ```mcfunction
   /function pixel_tzz:acceptance_3b/control/start_targets
   /pixel_tzz_pro message control target PlayerB complete
   ```

**五端观察**

- Player972：cue/group 匹配全部相关活动实例；target 只匹配冻结受众中含 B 的完整实例。
- PlayerB：target 轮被补全。
- PlayerC：target 轮继续自己的实例，不被 B 的控制连带补全。
- PlayerD：无匹配实例时不受影响。
- PlayerE：无匹配实例时不受影响。

**唯一通过标准**

cue、group、target 三种寻址都控制正确的服务端完整实例集合，没有仅客户端伪暂停、漏控或串控。

**失败记录**

按轮记录活动 UUID、冻结目标、控制匹配数量和五端实际结果。

2026-08-01 cue 轮实机通过：`field_capture` cue 级暂停冻结文字、效果与字符音，继续后从原位置
恢复，未影响无关实例。group 轮实机通过：`acceptance_capture` group 级暂停与补全均只匹配
1 个实例，暂停冻结正常，补全后只产生一次完成回调。target 轮实机通过：分别面向 PlayerB 与
PlayerC 的两个实例并行存在，按 PlayerB 寻址只补全 B 的完整实例，C 保持原进度。但两条明确
指定 `to PlayerB`／`to PlayerC` 的播放命令都把实际演出内容额外投影给了命令执行者 Player972。
源码核对确认这是示例 `field_capture` 将 `call_targets` 与 `invoker` 注册为受众并集所致，不是
通用运行时越权；该夹具声明与本项显式目标预期冲突，统一修复时应从示例中移除 `invoker`。

2026-08-03 修复后实机回归：cue 暂停与恢复均只匹配 1 个实例并从原进度继续；group 单命令
包装在创建实例的同一 Tick 完成暂停，随后按 `acceptance_capture` 补全只影响 1 个实例；target
单命令包装同时建立 PlayerB 与 PlayerC 的独立实例，按 PlayerB 寻址补全只影响 B，C 继续自己的
进度，Player972、D、E 未收到目标演出。三种服务端完整实例寻址均通过。

状态：**通过**

## 7. D 组：玩家偏好、辅助功能与聊天兼容

### 3B-14 本地偏好独立生效且不改变权威时钟

**前置状态**

- 五端均可从 `ESC → 选项 → 全员逃走中设置… → 文字效果` 进入文字效果页；
- 先在五端点击“恢复默认”；
- 清除 `last_callback`。

**操作客户端与路径**

1. Player972：保持默认；
2. PlayerB：沿上述路径把“动画速度”调为 `0.5×`；
3. PlayerC：调为 `3.0×`；
4. PlayerD：开启“减少动态效果”；
5. PlayerE：关闭“字符音”；
6. Player972 播放给全体：

   ```mcfunction
   /pixel_tzz_pro message play pixel_tzz:acceptance/field_capture to @a with storage pixel_tzz:acceptance_3b all_call
   ```

**五端观察**

- Player972：默认完整动画和声音；服务端标准完成时间不跟随某个玩家的本地速度。
- PlayerB：动画更慢，但不漏最终正文。
- PlayerC：动画更快，但不提前/重复服务端回调。
- PlayerD：使用 `simplified` 降级，最终正文仍完整。
- PlayerE：没有字符音，但独立时间线声音仍按注册语义处理，正文不受影响。

**唯一通过标准**

五种本地偏好只改变各自表现，五端最终正文一致，受众、字段值、服务端回调时间和次数完全不被本地设置改变。

**失败记录**

2026-08-03 PlayerB 使用 `0.5×` 速度逐端观察时发现：播放接近结束，原版 Title 区域上方原本
以小字号／缩放显示的文字突然放大为原版 Title 尺寸。动画最后一帧、正文停留阶段与最终态必须
保持同一字号、缩放和位置；本地减速只能改变播放速度，不能触发完成态渲染路径或尺寸切换。
PlayerC 使用 `3.0×` 时，Subtitle 第一个动态字段正常，但后续字段显示“附近无人”等 fallback，
Chat 正常。该模式表明高速本地时间线先于后续字段增量到达字段边界；客户端必须在未解析字段边界
等待真实值或由服务端提前供值，绝不能显示 fallback，同时不得用本地等待反向改变服务端回调时机。
PlayerD 开启“减少动态效果”后表现简化且正文完整，实机通过。PlayerE 关闭“字符音”后无逐字声，
独立时间线音符盒提示仍正常播放，符合两个声音节点独立配置的设计，实机通过。Player972 默认表现
正常。最终全体实例 `cc78c429-1367-42d4-a223-a03804be33ae` 覆盖 5 名目标；
`last_callback` 记录 `cycle: 0`、`occurrence: 0`、`reason: timeline_callback`，服务端只回调一次，
没有受到五端本地偏好的影响。执行播放前出现的“没有匹配的动态消息实例”来自清理命令在当时没有
活动实例，和随后成功发起的全体实例无关。本问题暂只记录，等待本轮验收结束后统一修复。

2026-08-03 定向实机回归：PlayerB 使用 `0.5×` 时动画、正文停留和最终态的 Title 字号、缩放与
位置保持一致，没有在末帧突然放大；PlayerC 使用 `3.0×` 时所有动态字段均等待并显示真实值，未再
出现“附近无人”“暂无记录”等 fallback。两端最终正文一致，本地速度未改变服务端回调语义。

状态：**通过**

### 3B-15 光标、对比度、字符音量与偏好持久化

**前置状态**

- 3B-14 结束且没有活动演出。

**操作客户端与路径**

1. PlayerB 关闭“光标闪烁”；
2. PlayerC 开启“高对比度”；
3. PlayerD 打开字符音但把独立音量调为 `0%`；
4. PlayerE 点击“恢复默认”；
5. 四端关闭设置页并重新打开，确认值没有回跳；
6. Player972 再播放一次全体演出。

**五端观察**

- Player972：默认光标闪烁、默认色和默认音量。
- PlayerB：光标常亮而不是消失；正文仍正常推进。
- PlayerC：光标和新字临时色对比增强，最终注册颜色不被永久覆盖。
- PlayerD：字符音静音，其他非字符声音不被错误清除。
- PlayerE：所有值回到默认并正常播放。

**唯一通过标准**

各项偏好在设置页关闭/重开后保持，且只影响对应视觉或声音维度，不相互串扰、不改变最终富文本。

**失败记录**

2026-08-03 四端完成设置后关闭设置页并重新打开：PlayerB“光标闪烁”保持关闭，
PlayerC“高对比度”保持开启，PlayerD“字符音”保持开启且音量保持 `0%`，PlayerE 各项保持默认；
没有数值回跳，偏好持久化实机通过。PlayerB 关闭“光标闪烁”后的逐端演出中，光标常亮、
正文正常推进，实机通过。PlayerC 开启“高对比度”后逐端播放，未观察到光标、新字临时色或
其他演出颜色与默认表现存在可辨识差异；该设置虽然持久化，但实际渲染效果不可验证或未生效，
本项不通过，统一修复时须提供明确且不覆盖最终注册颜色的高对比度表现。PlayerD 开启字符音但将
音量设为 `0%` 后，逐字声静音，独立音符盒提示音与文字演出正常，实机通过。PlayerE“恢复默认”
后的光标、速度、颜色和字符音均恢复正常，未残留此前关闭字符音的设置，实机通过。

2026-08-03 定向实机回归：PlayerC 恢复默认后只开启“高对比度”，与默认 Player972 同轮对比；
PlayerC 的光标和刚出现文字具有清晰可辨的增强对比，最终稳定后的注册颜色恢复正确，正文、速度、
换行和声音未被该偏好串扰。其余持久化、光标和独立音量子项沿用此前实机通过结论。

状态：**通过**

### 3B-16 手动补全按键

**前置状态**

- 五端点击“恢复默认”；
- 默认“立即补全文字演出”按键未绑定；
- B、C 均在游戏画面。

**操作客户端与路径**

1. PlayerB：`ESC → 选项 → 全员逃走中设置… → 按键与操作`，只为“立即补全文字演出”绑定一个临时按键；
2. PlayerC 保持未绑定；
3. Player972 清除回调并播放给 B、C：

   ```mcfunction
   /data remove storage pixel_tzz:acceptance_3b last_callback
   /pixel_tzz_pro message play pixel_tzz:acceptance/field_capture to @a[name=PlayerB,limit=1] with storage pixel_tzz:acceptance_3b call
   /pixel_tzz_pro message play pixel_tzz:acceptance/field_capture to @a[name=PlayerC,limit=1] with storage pixel_tzz:acceptance_3b call
   ```

4. B 在游戏画面按一次绑定键；立即查询 `last_callback`，再等待正常完成后再查一次。

**五端观察**

- Player972：B 的本地补全不伪造客户端确认，也不提前推进其他实例。
- PlayerB：获准的动画立即补齐最终内容，不隐藏正文。
- PlayerC：自己的演出按原速度继续，不被 B 的按键影响。
- PlayerD：无活动实例，不受影响。
- PlayerE：无活动实例，不受影响。

**唯一通过标准**

绑定键只补全 B 当前获准实例的本地动画；C 继续播放，最终正文不被跳过，服务端回调仍按权威时间线且恰好一次。

**失败记录**

2026-08-03 PlayerB 绑定“立即补全文字演出”后，与未绑定的 PlayerC 同时播放独立实例。
PlayerB 按键后仅自己的获准动画立即补齐最终正文，PlayerC 继续按原速度播放，其他端不受影响；
本地补全没有提前伪造客户端确认。最终 `last_callback` 显示 `cycle: 0`、`occurrence: 0`、
`reason: timeline_callback`，服务端仍按权威时间线单次完成，实机通过。

状态：**通过**

### 3B-17 原版 Chat 与 Chat Heads 兼容

**前置状态**

- 游戏处于 `pixel_tzz:running` / `pixel_tzz:acceptance/main_branch`；
- PlayerC 是猎人；
- PlayerC 安装与 Minecraft `26.2` 兼容的 Chat Heads 客户端版本，其他端可保持未安装；
- 如果当前没有兼容构建，本项必须记为 `BLOCKED`，不能用自动检查冒充 PASS。

**操作客户端与命令**

Player972 执行：

```mcfunction
/pixel_tzz_pro message play pixel_tzz:acceptance/verified_player_speaker to PlayerC
/pixel_tzz_pro message play pixel_tzz:acceptance/registered_entity to PlayerC
```

动画期间 PlayerB 发送一条普通聊天。

**五端观察**

- Player972：只看到两条直接命令的有限回显和 B 的正常签名聊天，不接收面向 C 的玩家／实体演出；命令来源不会以 `invoker` 身份自动加入 cue 受众。
- PlayerB：普通聊天正常发送，不收到猎人目标内容。
- PlayerC：专用 cue 使用服务端验证的 `player_parameter(target)` 发言者，Chat Heads 为 PlayerC 装饰正确玩家头像；随后合法村民来源只显示服务端验证的
  实体名且没有玩家头像，无效实体来源匿名降级为普通系统消息；动态条目仍原位更新，不逐帧刷屏。
- PlayerD：不因 OP 收到隐藏演出，只看到普通聊天。
- PlayerE：同 D。

**唯一通过标准**

Chat Heads 只装饰真实原版逻辑条目，不破坏动态原位更新、系统消息身份、普通签名聊天或受众隔离；
注册实体不会携带玩家 UUID、皮肤、帽子层或伪造签名身份，无效实体 ID 不会显示给玩家。

**失败记录**

2026-08-03 仅 PlayerC 安装 Modrinth Chat Heads `1.2.5`（Fabric；项目将该构建标记为兼容
Minecraft `26.2`），客户端日志确认模组与资源成功加载。PlayerB 在演出期间发送普通聊天，
PlayerC 正常收到并显示聊天内容，普通玩家聊天路径兼容。猎人受众矩阵演出未出现；服务端日志明确
报错 `message hook dependency predicate has no retained document: pixel_tzz:acceptance_3b/is_hunter`，
实例 `dcd8f4d4-e3af-4d8a-81f0-af03c34d1750` 无法生成投影并持续重试。这是 3B-11 已记录的
依赖快照缺陷复现，并非 Chat Heads 拦截演出；实例经控制命令取消后持续重试正常停止。
随后单独播放“注册实体来源演出”，PlayerC 看到合法来源以本地化名称“村民”显示且无玩家头像，
无效来源匿名降级为普通系统消息，没有暴露原始实体注册 ID，也无玩家头像；其他端未收到目标内容，
实体来源与受众隔离实机通过。动态原位更新仍待 3B-11 缺陷修复后验证。

2026-08-03 修复后实机回归：PlayerC 的已验证玩家来源由 Chat Heads 正确显示 PlayerC 头像，
动态内容始终原位更新；合法注册实体显示本地化名称“村民”且不携带玩家头像，无效实体来源匿名
降级，Player972、B、D、E 均未收到目标演出。共享 Chat 提前换行问题继续归入 3B-06/3B-11，
不改变本项身份与兼容性结论。

状态：**通过**

## 8. E 组：ESC、强制页面、布局与低 FPS

### 3B-18 播放中打开 ESC 与玩家终端

**前置状态**

- PlayerB 没有强制流程；
- 五端均从“全员逃走中设置… → 文字效果”恢复默认。

**操作客户端与命令**

1. Player972 播放给 B：

   ```mcfunction
   /pixel_tzz_pro message play pixel_tzz:acceptance/field_capture to PlayerB with storage pixel_tzz:acceptance_3b call
   ```

2. B 在 HUD 文字仍播放时按 ESC，再进入玩家终端；停留约 2 秒；
3. B 按 ESC 返回上一级，最终回到游戏。

**五端观察**

- Player972：只看到命令回显；B 的屏幕状态不能改变回调权威时间。
- PlayerB：Chat 继续进入历史；Title/Subtitle/ActionBar 按默认遮挡策略暂停并在回到游戏后从正确进度恢复；终端不被普通演出擅自关闭。
- PlayerC：不接收目标演出。
- PlayerD：同 C。
- PlayerE：同 C。

**唯一通过标准**

B 的 ESC/终端页面栈保持正常，普通文字演出不夺取页面；返回游戏后 HUD 通道只恢复一次、无快进双声和红色中间页。

**失败记录**

2026-08-03 PlayerB 在文字演出期间打开 ESC 并进入玩家终端，停留后按 ESC 逐级返回暂停菜单与
游戏画面。终端未被普通演出强制关闭，页面栈正常；Chat 进入历史，HUD 通道回到游戏后仅恢复一次，
进度、声音正常，没有快进、重复声音或红色中间页，实机通过。

状态：**通过**

### 3B-19 强制页面优先级

**前置状态**

- PlayerB 可被发起通用初始化；若已有完成凭据，改用干净世界或可重置玩家；
- B 尚未完成强制页。

**操作客户端与路径**

1. Player972：`主持人控制台 → 快捷操作/全部操作 → 通用初始化 → 选择 PlayerB → 审阅并执行`；
2. B 停留在不可关闭强制页；
3. Player972 播放给 B：

   ```mcfunction
   /pixel_tzz_pro message play pixel_tzz:acceptance/field_capture to PlayerB with storage pixel_tzz:acceptance_3b call
   ```

4. B 按 ESC，再完成强制流程并明确回到游戏。

**五端观察**

- Player972：主持人控制台和流程状态不被文字演出重建；回调仍按服务端时钟。
- PlayerB：强制页始终在最上层且不能被 ESC/演出绕过；Chat 可按策略进入历史，HUD 通道在真正回到游戏后才开始或安全降级。
- PlayerC：不接收 B 的目标内容。
- PlayerD：同 C。
- PlayerE：同 C。

**唯一通过标准**

普通 V3B 演出绝不能关闭、覆盖或绕过 B 的强制页面；流程完成后等待的内容只启动一次，没有强制页下偷播完的 HUD 动画。

**失败记录**

2026-08-03 因通用初始化只能全局启动，改用只面向 PlayerB 的猎人初始化强制页验证同一优先级语义。
PlayerB 停留在不可关闭页面时发起 `field_capture`；完成强制页并回到游戏后，猎人初始化自身产生的
流程文字覆盖了等待中的 V3B Title／Subtitle／ActionBar，只有 Chat 内容保留。普通演出没有与
生命周期提示进入同一通道调度：流程提示可以优先播放，但结束后必须恢复等待演出，不能吞掉已排队的
HUD 内容或直接落到最终态。继续等待至猎人初始化提示完全消失后，原 V3B Title／Subtitle／ActionBar
仍未恢复，确认排队内容被永久吞掉，并非优先级切换期间的短暂覆盖。本项不通过，等待本轮结束后统一修复。

2026-08-03 修复后实机回归仍未通过：PlayerB 停留在猎人初始化强制页时发起 `field_capture`，
Title、Subtitle 与 ActionBar 在流程完成并明确回到游戏后仍未恢复；Chat 直接显示静态最终正文，
没有正常逐字动画。普通演出虽未覆盖强制页，但等待/超时路径仍把 HUD 演出吞掉并把 Chat 提前
降级，未满足流程完成后只恢复一次的要求。

同次退出强制页时还连续出现多条猎人选择相关 Title。客户端日志确认 `18:51:48` 同一 Tick 内
至少创建了 4 个独立的单节点生命周期消息实例，并同时投影给 Player972 与 PlayerB，不是同一
Title 的单纯渲染重影。流程退出时必须合并、去重或按可读停留间隔调度这些提示，不能把多个猎人
初始化状态变化集中挤入 HUD；同时不能让这些提示永久吞掉此前等待的普通 V3B 演出。

进一步确认根因不是仅需增加间隔，而是 3B 接入后的反馈粒度错误：主持人当前会收到每名玩家、
每个初始化节点的 Subtitle，造成大量无决策价值的重复播报。修复必须恢复 3A 已验收的信息密度：
默认只向主持人播报需要关注的流程级开始、完成、失败或汇总结果；逐玩家、逐节点状态保留在主持人
控制台与审计数据中，不自动转为 HUD 演出。只有数据包明确为某个节点注册主持人 HUD 提示时才允许
单独播放，不能因接入 V3B 就把所有内部节点一律外显。

2026-08-03 本轮修复已落地：强制页面阻挡期间不再提前消耗同步等待预算或把字段捕获降级为静态
最终态；逐节点强制页面门控继续保留。权威强制状态、强制数据页及过渡页会优先识别为 `FORCED`；
逐玩家生命周期提示默认只投影给调用目标，不再自动向主持人逐节点密集播报。等待中的 HUD 演出、
Chat 动画恢复及主持人信息密度待实机复测。

2026-08-04 修复后实机复测：PlayerB 停留在猎人初始化强制页面时，目标 Chat 按已注册策略继续
播放；完成流程并返回游戏后可看到该条 Chat 的最终历史，ActionBar 等此前被强制页阻挡的 HUD
内容此时才开始播放。该通道差异符合现有设计，不属于本项缺陷，不要求把 Chat 一并延后。

同时确认基础数据包新增的 V3B 生命周期 Hook 仍重复叠加在 3A 原有反馈之上。主持人先收到
“『猎人身份初始化』已发起”等额外 Subtitle，随后又收到原有 3A Subtitle；PlayerB 在完成猎人
初始化后也额外收到三条 3B Subtitle，而 3A 基线不会向玩家播放这些内容。来源已定位为
`hunter_initialization.json` 的 `start` / `player_complete` / `all_complete` Hook，以及
`roles/hunter.json` 的 `role_changed` / `initialization` Hook。期望保留通用 Hook 注册能力，但基础
数据包不得在 3A 已有 Subtitle 的基础上，再向主持人或玩家追加这些重复自动播报；只修正 Subtitle
内容与受众，不改变 Chat、Title、ActionBar 或强制页面调度。

2026-08-04 集中修复：基础数据包已移除 `hunter_initialization.json` 的三个 V3B Subtitle Hook 和
`roles/hunter.json` 的两个 V3B Subtitle Hook，并增加夹具回归检查，确保猎人初始化继续保持 3A
已验收的信息密度。通用 `message_hooks` 能力及其他数据包显式注册均保留；Chat、Title、ActionBar、
强制页面和消息运行时未改动。待实机确认主持人与 PlayerB 均只看到 3A 原有 Subtitle。

2026-08-04 实机定向复测通过：主持人与 PlayerB 均只保留 3A 原有 Subtitle，未再出现
流程发起、玩家完成、全员完成、身份变化或身份初始化的额外 V3B 重复播报。

状态：**通过**

### 3B-20 窄窗口与 GUI Scale

**前置状态**

- 无强制流程；
- 执行 setup；
- 五端可分别调整窗口与 GUI Scale。

**操作客户端与命令**

1. Player972 保持 `2560×1440 + GUI Scale 4`；
2. B 调为约 `1280×720 + GUI Scale 2`；
3. C 调为约 `960×540 + GUI Scale 3`；
4. D 使用 GUI Scale 1；E 使用当前允许的最大 GUI Scale；
5. Player972 先执行第一条并等待它结束，再执行第二条：

   ```mcfunction
   /function pixel_tzz:acceptance_3b/layout/pressure
   /pixel_tzz_pro message play pixel_tzz:acceptance/field_capture to @a with storage pixel_tzz:acceptance_3b layout_all_call
   ```

**五端观察**

- Player972：2K 基准布局与已确认比例一致。
- PlayerB：各通道保持安全边距，无右侧/底部溢出。
- PlayerC：长 Subtitle 和 ActionBar 按注册的换行/省略策略收束，保留正文不被吞掉。
- PlayerD：小 GUI Scale 不使光标、文字或点击区域异常放大。
- PlayerE：最大 GUI Scale 下 Title/Subtitle 不越屏，Chat 仍遵守原版聊天宽度。

**唯一通过标准**

五种尺寸下四通道均在各自注册边界内，文字、光标和效果等比稳定；无裁切、重叠、越屏或不可读缩放。

**失败记录**

2026-08-03 Player972 在 `2560×1440 + GUI Scale 4` 下播放布局压力夹具，已登记的既有演出问题
仍可观察但不作为本项重复发现；除既有问题外，四通道没有新增裁切、越界、重叠或比例异常，
2K 基准布局实机通过。PlayerB 在约 `1280×720 + GUI Scale 2` 下四通道保持安全边距，
没有新增右侧／底部溢出、裁切或重叠，实机通过。PlayerC 在约 `960×540 + GUI Scale 3` 下，
长 Subtitle 与 ActionBar 按策略收束，没有新增越屏、裁切、重叠或异常缩放，实机通过。
PlayerD 在 GUI Scale 1 下文字、光标和动画比例稳定，四通道安全边距正常，没有新增裁切、
重叠或越屏，实机通过。PlayerE 在当前最大 GUI Scale 下 Title、Subtitle、ActionBar 均未越屏，
Chat 遵守原版宽度，文字与光标没有新增裁切、重叠或不可读缩放，实机通过。

2026-08-03 收口核对：五种尺寸的真实视觉结果此前已经逐端实机通过；后续只修改了多目标参数
夹具，当前 3B-04 已确认修复后的 `all_call` 能正确建立全体实例。布局实现本身未再修改，因此不
重复要求五端重新调整分辨率与 GUI Scale。

状态：**通过**

### 3B-21 低 FPS 时间线与声音跳过

**前置状态**

- PlayerB 在视频设置把最大帧率临时限制为 `10 FPS`；
- PlayerC 保持正常帧率；
- 其余设置一致。

**操作客户端与命令**

Player972 同时播放给 B、C：

```mcfunction
/pixel_tzz_pro message play pixel_tzz:acceptance/field_capture to @a[name=PlayerB,limit=1] with storage pixel_tzz:acceptance_3b call
/pixel_tzz_pro message play pixel_tzz:acceptance/field_capture to @a[name=PlayerC,limit=1] with storage pixel_tzz:acceptance_3b call
```

结束后把 B 的帧率恢复。

**五端观察**

- Player972：两个实例的服务端标准时间线不受 B 的 FPS 影响。
- PlayerB：低 FPS 时直接呈现当前正确帧，不逐帧追赶；已过去的字符音不在一卡后集中爆发。
- PlayerC：正常平滑播放，和 B 最终完成状态一致。
- PlayerD：无目标内容。
- PlayerE：无目标内容。

**唯一通过标准**

低 FPS 只降低平滑度，不延长权威时间线、不积压声音、不丢最终正文，也不拖住正常 FPS 客户端。

**失败记录**

2026-08-03 PlayerB 将最大帧率限制为 `10 FPS`，PlayerC 保持正常帧率，两端同时播放独立实例。
PlayerB 低帧率下直接呈现当前正确进度，没有逐帧追赶或卡顿后集中爆发字符音；PlayerC 正常平滑
播放且未被拖慢，B/C 最终正文与结束时机一致，其他端未收到内容，实机通过。测试后恢复 B 的帧率上限。

状态：**通过**

### 3B-21A 外部 HUD 冲突的让出、暂停、偏移与叠加

**前置状态**

- 备份世界内 `message_cues/acceptance/field_capture.json`；
- PlayerB 在游戏画面，字效设置恢复默认，没有活动 `field_capture`；
- 每轮只在 `policies.lifecycle` 中把 `external_conflict` 依次设为 `yield`、`pause`、`offset`、
  `overlay`，每次修改后执行 `/reload`。

**操作客户端与命令**

每种模式各执行一轮：

1. PlayerB 只执行一次自动验收入口；函数内部仅把 V3B 播放命令切换为 Player972 发起，随后使用
   计分板逐 Tick 计时，在第 `24` Tick 注入外部 Title、Subtitle 与 ActionBar，第 `60` Tick 刷新
   ActionBar，第 `100` Tick 自动清除，因此测试者不需要切换客户端；
   测试中再次由 PlayerB 调用同一函数会先取消旧实例，并在下一服务端 Tick 强制重新开始；

   ```mcfunction
   /function pixel_tzz:acceptance_3b/external_hud/run
   ```

2. PlayerB 全程只观察：记录冲突前最后一帧、冲突期间的可见性、字符音和位置，以及第 `100`
   Tick 自动解除后的首帧；不要求测试者手动抢时间执行多条 `/title`；
3. 若需中途终止，执行 `/function pixel_tzz:acceptance_3b/clear`；四轮结束后恢复备份并 `/reload`。

**五端观察**

- Player972：只看到直接命令的有限回显，不接收只面向 B 的原版 HUD 或 V3B 演出；
- PlayerB：`yield` 隐藏但时钟继续，解除后直接到当前帧且不补播字符音；若隐藏期间已经结束则不
  闪回。`pause` 隐藏且冻结，解除后从原帧继续。`offset` 在动画、正文停留和静态尾帧始终保持同一
  安全偏移，解除后才回到注册位置。`overlay` 全程保持注册位置原位播放；
- PlayerC：不接收目标演出；
- PlayerD：不因 OP 身份收到目标演出；
- PlayerE：不接收目标演出。

**唯一通过标准**

四种模式分别满足“隐藏且继续”“隐藏且冻结”“持续安全偏移”“原位叠加”；任何模式都不改变
服务端标准回调时机、字段最终值或受众。OFFSET 不得只偏移动画后在静态尾帧跳回；YIELD 不得解除
后从旧帧补播；PAUSE 不得在隐藏期间偷跑；OVERLAY 不得自动让出或偏移。

**失败记录**

按模式记录冲突前后可见字符、隐藏 Tick、字符音、动画与静态尾帧坐标、解除冲突后的首帧、
`last_callback` 和任何错误受众。

2026-08-03 `yield`：PlayerB 使用单函数计时入口实机观察，外部 HUD 出现时 V3B 独占 HUD 立即
让出，隐藏期间时钟继续；自动解除后没有旧帧闪回、续播或补响，Chat 与其他客户端受众符合预期，
子项通过。

2026-08-03 `pause`：外部 HUD 出现时 PlayerB 的 V3B 独占 HUD 立即隐藏并冻结；自动解除后从原帧
继续，没有偷跑、跳到结束或从头开始，Chat 与其他客户端受众符合预期，子项通过。

2026-08-03 `offset`：外部 HUD 与 V3B 同时可见，V3B 动画、正文停留和静态尾帧持续位于安全偏移
位置，没有在终态跳回注册位置；Chat、声音和其他客户端受众符合预期，子项通过。

2026-08-03 `overlay`：PlayerB 的 V3B Title、Subtitle 与 ActionBar 在外部 HUD 出现期间不隐藏、不
冻结、不偏移，始终保持注册位置原位叠加；Chat、声音和其他客户端受众符合预期，子项通过。

状态：**通过**

## 9. F 组：连接、晚加入与 Reload

本组共同遵守以下安全边界：

- `delivery.offline_targets=final_only`、`reconnect=final_only` 或 `late_join=final_only` 在新连接上
  等待 capability/asset 握手最多 `100` 个游戏 Tick（20 TPS 下约 `5` 秒），截止点不能被重复
  轮询向后滑动；握手未完成时只能投递已经完整授权的原版静态降级，零授权文本应静默结算；
- 已建立连接若失去部分节点或参数权限，必须看到同实例更高 cycle 的原子 `REFRESH` 结果；若
  已无任何可见节点或无法安全刷新，则只允许无文本 `CANCEL`，旧字段不能被后续 `FINALIZE`
  重新带回；
- 冷启动冻结最终消息绑定启动时批准它的 definition generation。离线玩家加入前若成功执行
  `/reload`，该消息必须静默 fail-closed；generation 未变时不要求导致降级的原依赖重新一致，
  但加入边界仍须重新通过当前上下文、受众、节点、参数和完整 fallback 覆盖校验。
- 当前连接收到终态控制后不得再收到 APPEND、字段增量或后续控制；断线重连属于新物理连接，
  连接终态必须清空，仍有效实例只能通过新的 `CREATE` 建立。

### 3B-22 掉线后及时重连

**前置状态**

- PlayerE 在线；
- 没有同 ID 活动实例；
- 五端默认偏好。

**操作客户端与命令**

1. Player972 播放并立即暂停：

   ```mcfunction
   /pixel_tzz_pro message play pixel_tzz:acceptance/field_capture to PlayerE with storage pixel_tzz:acceptance_3b call
   /pixel_tzz_pro message control cue pixel_tzz:acceptance/field_capture pause
   ```

2. PlayerE 退出服务器，5 秒内重新加入；
3. Player972 执行：

   ```mcfunction
   /pixel_tzz_pro message control cue pixel_tzz:acceptance/field_capture resume
   ```

**五端观察**

- Player972：实例保持同一 UUID；E 离线不会重放已执行回调。
- PlayerB：不接收目标内容。
- PlayerC：同 B。
- PlayerD：同 B。
- PlayerE：重连后只恢复仍有效实例，按策略从权威位置继续；不会重复已经过去的开始音。

**唯一通过标准**

E 在短时重连后恢复同一服务端实例的正确状态，既不从错误位置重播，也不产生第二实例、重复声音或重复回调。

**失败记录**

2026-08-03 PlayerE 的 `field_capture` 实例发起后立即暂停，E 退出并在 5 秒内重新加入，随后恢复。
E 从同一服务端实例的正确权威位置继续，没有从头重播开始音、没有产生第二实例或重复回调；
B/C/D 未收到目标内容，实机通过。

状态：**通过**

### 3B-23 晚加入 `live_add`

**前置状态**

- 仅修改世界内已安装副本的
  `data/pixel_tzz/pixel_tzz_pro/message_cues/acceptance/field_capture.json`；
- 先备份该文件；
- 把 `policies.audience` 临时改为：

  ```json
  {
    "target": {"source": "all", "online_only": true},
    "evolution": "live_add"
  }
  ```

- 在 `policies` 中临时加入：

  ```json
  "lifecycle": {"late_join": "from_start"}
  ```

- `/reload` 成功；PlayerE 此时离线，972/B/C/D 在线。

**操作客户端与命令**

1. Player972 执行：

   ```mcfunction
   /function pixel_tzz:acceptance_3b/control/start_paused
   ```

2. PlayerE 加入世界；
3. Player972 执行：

   ```mcfunction
   /pixel_tzz_pro message control cue pixel_tzz:acceptance/field_capture resume
   ```

4. 测试后立即恢复备份并 `/reload`。

**五端观察**

- Player972：原目标集合保持，E 通过 `live_add` 合法加入；实例不重建。
- PlayerB：从原暂停位置继续。
- PlayerC：同 B。
- PlayerD：同 B。
- PlayerE：加入后按 `from_start` 获得自己的播放计划；不收到加入前其他玩家的私有字段或回调信息。

**唯一通过标准**

E 是唯一新增受众，按注册晚加入策略播放；原四端不重启，服务端实例和回调仍只有一份。

**失败记录**

2026-08-03 临时将验收世界 `field_capture` 的受众改为在线全体、`live_add`，并注册
`late_join: from_start`；PlayerE 离线时 `/reload` 成功。实例以暂停状态创建后 E 加入，恢复时
E 被加入同一实例并从开头正常播放；原有四端没有因 E 加入而重启，也未产生第二实例、重复声音或
重复回调，实机通过。测试后原文件已按备份恢复，SHA-256 一致；恢复后的 `/reload` 成功。

状态：**通过**

### 3B-24 离线 TTL 到期

**前置状态**

- 只在世界内 `field_capture.json` 的 `policies` 临时加入：

  ```json
  "delivery": {"offline_targets": "queue", "offline_ttl": "5s"},
  "lifecycle": {"reconnect": "continue"}
  ```

- `/reload` 成功；E 在线；无旧实例。

**操作客户端与命令**

1. 播放给 E 并暂停；
2. E 退出，等待至少 7 秒；
3. E 重连；
4. Player972 恢复 cue；
5. 测试后恢复仓库同版文件到世界副本并 `/reload`。

**五端观察**

- Player972：过期目标不无限阻塞实例或回调；TTL 沿绝对截止点计算。
- PlayerB：无目标内容。
- PlayerC：无目标内容。
- PlayerD：无目标内容。
- PlayerE：过期后不从头收到旧动画、旧声音或旧 HUD；新调用仍可正常接收。

**唯一通过标准**

E 超过 TTL 后旧待投递只过期一次，既不补播也不拖住其他目标；重连后新的演出仍正常。

**失败记录**

2026-08-03 在验收世界临时注册 `offline_targets: queue`、`offline_ttl: 5s` 与
`reconnect: continue`，重载成功。PlayerE 的实例播放后暂停，E 离线超过 7 秒再重连；恢复 cue 后
E 没有补播旧动画、旧声音或旧 HUD，随后新建演出可正常收到，其他端未收到 E 的目标内容，
实机通过。测试后原文件已按备份恢复且 SHA-256 一致；恢复后的 `/reload` 成功。

状态：**通过**

### 3B-25 数据包 `/reload` 与冻结 generation

**前置状态**

- 备份世界内 `field_capture.json`；
- 没有旧实例。

**操作客户端与命令**

1. Player972 启动一条并立刻暂停；
2. 只在世界副本中把静态标题 `『动态文本』` 临时改成 `『动态文本·新定义』`；
3. 执行 `/reload`；
4. 恢复旧实例，等待完成；
5. 再新建一条同 ID 演出；
6. 恢复备份并 `/reload`。

**五端观察**

- Player972：活动实例继续使用旧冻结文本；新实例使用新定义；同步徽标不周期闪烁。
- PlayerB：无目标内容。
- PlayerC：无目标内容。
- PlayerD：无目标内容。
- PlayerE：无目标内容。

**唯一通过标准**

`/reload` 原子发布新 generation：旧实例完整使用旧快照，新调用使用新定义，二者不混帧、不留半句、不重复回调。

**失败记录**

2026-08-03 旧 `field_capture` 实例启动并暂停后，将世界副本标题临时改为
`『动态文本·新定义』` 并成功 `/reload`。恢复旧实例时仍完整显示旧标题 `『动态文本』`；旧实例
结束后新建同 ID 演出，完整显示新标题，两代定义没有混帧、半句或异常周期同步闪烁，实机通过。
测试后原文件已按备份恢复且 SHA-256 一致；恢复后的 `/reload` 成功。

状态：**通过**

### 3B-26 客户端资源 Reload

**前置状态**

- 世界副本已恢复原样；
- B、C 在游戏画面；
- 清除 `last_callback`。

**操作客户端与命令**

1. Player972 分别为 B、C 创建活动实例；
2. 动画中只有 PlayerB 按 `F3+T` 执行客户端资源 Reload；
3. C 不做任何操作；
4. 等待服务端标准完成后查询 `last_callback`。

**五端观察**

- Player972：B 的资源状态报告不能完成/取消 C，也不能提前推进服务端回调。
- PlayerB：本地按注册 `resource_reload` 策略安全完成、补全或取消表现；不闪红色错误窗口。
- PlayerC：动画继续自己的时间线，不因 B Reload 重启或停止。
- PlayerD：无目标内容。
- PlayerE：无目标内容。

**唯一通过标准**

资源 Reload 只影响 B 的本地表现；C、服务端目标集合、字段值、历史与回调账本不变，最终回调至多一次。

**失败记录**

2026-08-03 PlayerB、PlayerC 同时播放独立实例，只有 B 在动画期间执行 `F3+T` 客户端资源重载。
B 按注册策略安全处理本地演出，没有红色错误窗口；C 的动画继续原时间线，没有重启、停止或受 B
影响。服务端目标、字段与回调未被本地资源状态改变，D/E 未收到内容，实机通过。

状态：**通过**

## 10. G 组：服务器正常重启与四种 restart

本组每项都执行一次正常保存退出和同世界重开。禁止杀进程模拟崩溃；Player972 使用“保存并退出到标题画面”，等待集成服务端完全停止，再进入同一世界并重新开放局域网。B～E 观察断线页，随后按 B、C、D、E 顺序重连。

需要 `policy_matrix` 的项目都要求当前世界处于 `pixel_tzz:running` / `pixel_tzz:acceptance/main_branch`，且 PlayerC 为猎人。每项开始前删除：

```mcfunction
/data remove storage pixel_tzz:acceptance_3b last_callback
```

### 3B-27 `restart=transient`

**前置状态**

- 使用未声明 `restart` 的原版 `field_capture`，其默认值为 `transient`；
- 五端在线。

**操作客户端与命令**

1. Player972 执行：

   ```mcfunction
   /function pixel_tzz:acceptance_3b/control/start_paused
   ```

2. 确认画面停在中途后正常保存退出；
3. 重开同一世界并让 B～E 重连；
4. 查询 `last_callback`。

**五端观察**

- Player972：旧临时实例不恢复，不补全，不执行完成回调。
- PlayerB：不出现旧内容。
- PlayerC：不出现旧内容。
- PlayerD：不出现旧内容。
- PlayerE：不出现旧内容。

**唯一通过标准**

正常重启后 transient 实例完全丢弃，五端均无旧视觉/声音/回调；新演出仍可正常创建。

**失败记录**

2026-08-03 `field_capture` 实例在暂停状态下正常保存退出，集成服务器完全停止后重开同一世界，
B～E 依次重连。五端均未出现旧视觉、旧声音或旧完成回调；旧 transient 实例完全丢弃，随后
PlayerB 可正常收到新建演出，实机通过。

状态：**通过**

### 3B-28 `restart=continue`

**前置状态**

- 世界内 `policy_matrix.json` 保持仓库默认 `"restart": "continue"`；
- 当前上下文合法。

**操作客户端与命令**

1. Player972 执行单命令入口；函数面向当前猎人 PlayerB 播放，并在 `10 Tick` 后自动按 cue 暂停，
   随后向主持人明确提示可以退出。PlayerB 记住暂停时最后一个可见字符、进度与已经播放的声音，
   测试者不需要抢时间切换窗口：

   ```mcfunction
   /function pixel_tzz:acceptance_3b/restart/start_paused
   ```

2. 正常保存退出并重开；
3. B～E 重连，按 cue 恢复暂停实例并等待旧实例自然完成；
4. 再启动一条同 cue，正常保存退出并等集成服务端完全停止；
5. 服务端关闭期间，只在世界副本中修改该 cue 会读取的一项定义或 Predicate 文档，再进入同一世界；
6. B～E 重连，记录旧实例是使用冻结 `static_fallback` 安全收尾还是被取消；随后恢复文件并 `/reload`；
7. 如需核对实例，可执行：

   ```mcfunction
   /pixel_tzz_pro message list 1
   /data get storage pixel_tzz:acceptance_3b last_callback
   ```

**五端观察**

- Player972：第一次恢复同一 UUID、冻结定义、字段值和 cycle；正常停服前最后一 Tick 已落盘。第二次定义变化后，旧实例不读取新定义。
- PlayerB：不是授权目标，不收到内容。
- PlayerC：第一次重连后从最后检查点继续，不回退到上一个周期检查点，也不从头重放已过去声音；第二次只看到冻结静态最终内容或完全取消，不混入新定义。
- PlayerD：不因 OP 收到内容。
- PlayerE：不接收内容。

**唯一通过标准**

未改变依赖时，continue 实例从正常停服的最终检查点恢复且不回退、不重响；关闭期间依赖变化时只按冻结静态内容收尾或取消，绝不读取新 generation；所有路径的回调与历史至多一次。

**失败记录**

2026-08-03 本项依赖 `policy_matrix`，但该 cue 当前会稳定触发 3B-11 已记录的
`message hook dependency predicate has no retained document: pixel_tzz:acceptance_3b/is_hunter`，
实例无法生成投影或可靠落盘。继续执行正常重启不能产生有效 `restart=continue` 证据，暂缓至依赖
快照缺陷修复后回归。

2026-08-03 复验：先验证依赖未变化的正常停服恢复路径通过；随后将有效暂停实例完整保存退出，
仅离线修改世界副本中该 cue 实际读取的文案。冷启动明确记录依赖代际无法证明并降级为冻结收尾，
PlayerB 未显示离线新文案，也未混读新 generation；实例安全结算且回调/历史未重复。恢复原文件并
健康 `/reload` 后 generation 正常递增。本项实机通过。

状态：**通过**

### 3B-29 `restart=finalize`

**前置状态**

- 使用专用 `pixel_tzz:acceptance/restart_finalize`：`restart=finalize`、
  `snapshot` 受众、显式 `call_targets` 且无敏感参数，不改动 `policy_matrix` 的
  `live_strict` 安全语义；该纯重启夹具只限定游戏，不依赖具体阶段或任务；
- `/reload` 成功；PlayerC、PlayerE 在线。

**操作客户端与命令**

1. 由验收函数给 C、E 加临时目标标签，以同一个多目标实例播放并立即暂停：

   ```mcfunction
   /function pixel_tzz:acceptance_3b/restart/finalize_start_group_paused
   ```

2. PlayerE 退出服务器；
3. 正常保存退出并重开；
4. 只让 B、C、D 重连，E 保持离线；C 停留 10 秒；
5. 在 E 仍离线时再次正常保存退出，等待集成服务端完全停止后重开同一世界；
6. 先让 B、C、D 重连，再让 E 最后重连并停留 10 秒；
7. E 再退出并重连一次，确认第一条冻结最终内容不重放；
8. E 在线时用单函数再创建一条只给 E 的专用 cue 实例，函数会在同一 Tick 自动暂停；
   随后让 E 退出并正常保存退出世界：

   ```mcfunction
   /function pixel_tzz:acceptance_3b/restart/finalize_generation_start_paused
   ```

9. 重开同一世界但先不让 E 加入，Player972 先执行一次健康 `/reload`，确认 definition
   generation 已增加，再让 E 加入并停留 10 秒；
10. E 再退出并重连一次；查询 `last_callback`，最后移除两人的临时标签。

**五端观察**

- Player972：不接收目标正文；能观察到 C 在第一次重启消费、E 在第二次重启后消费；第二轮中
  `/reload` 后待 E 消费的旧冻结消息静默退休，且两轮都不执行正常完成回调。
- PlayerB：不接收旧内容。
- PlayerC：第一次重启后只消费一次静态最终内容；第二次完整停服后不再重放。
- PlayerD：不接收旧内容。
- PlayerE：第一轮第一次重启全程离线；经历第二次完整停服后首次加入，只消费一次完整授权的
  冻结静态内容，再次重连不重放。第二轮在加入前发生 `/reload`，旧审批 generation 已失效，
  因此 E 完全看不到该旧内容，后续重连也不补发。

**唯一通过标准**

generation 未变化时，finalize 的未消费目标跨正常停服持久化，每名当前仍完整授权的玩家至多
消费一次冻结最终内容；加入前 generation 变化时旧消息静默 fail-closed。两条路径都不播放旧
声音、不执行正常完成回调、不泄露部分 fallback，也不因再次重连重放。

**失败记录**

记录各次停服时 E 的离线状态、批准/加入时 generation、C/E 最终内容次数、二次重连结果、
回调、历史和实例是否仍可控制。2026-08-03 本项同样依赖无法生成投影的 `policy_matrix`；在
3B-11 依赖快照缺陷修复前，多目标冻结最终内容与 generation 失效路径均无法形成有效验收证据。

2026-08-03 复验未通过：专用夹具在创建 C/E 同一实例后同 Tick 暂停，落盘实例为
`299564a7-e693-4f01-ba6d-a885b12364ef`，`restart=finalize`、`paused`、elapsed=0，且没有任何
emitted occurrence。第一次冷启动生成了“按 restart=finalize 补全静态最终内容”的 completed
tombstone，但在线 PlayerC 未收到应有的一次冻结静态内容，离线 PlayerE 的待消费工作也未保留；
B/C/D 客户端日志均无“策略演出已完成”。第一条核心通过标准已经失败，未继续第二次停服和
generation 失效子路径，留待本轮结束后统一修复。

2026-08-03 本轮修复已落地：冷启动 durable finalize 不再在普通 JOIN 早期消费；改为等待握手
完成且玩家权威身份恢复后，再在逐玩家 generation、上下文、受众、节点、参数与静态 fallback
授权边界内幂等写入历史、提交一次性消费并投递。单条授权或历史异常会 fail-closed，且不会中断
同批其他恢复消息或后续握手。在线目标一次投递、离线待消费跨停服持久化、二次停服与 generation
失效路径待实机复测。

2026-08-04 修复后实机回归第一段通过：临时验收 cue 适配当前 `setup` 上下文与 C/E 的逃走者
身份后，以同一多目标实例发起并同 Tick 暂停；E 离线、正常保存退出并重开后，只有在线 C 收到
一次冻结静态最终内容，无打字动画或旧声音，B/D 未收到，E 的未消费目标仍保持离线待投递。

第二段不通过：保持 E 离线再次完整停服并重开后，E 第一次及第二次重连始终没有收到冻结最终
内容。服务端日志确认两次都完成 Pixel TZZ 客户端握手，但没有向 E 发送恢复计划；当前持久化状态
只保留 C 的一条 `policy_matrix` 历史和实例 completed tombstone，已经不存在 E 的待消费项。说明
第一轮 C 消费后实例被错误整体退休，离线 E 没有跨第二次停服保留；不是客户端漏看，无需重复测试。

2026-08-04 根因复核：原验收复用了 `policy_matrix`，其
`live_strict + sensitive + online_only + on_loss=cancel` 会在 E 离线后的权威重授权中按设计撤销 E，
所以停服快照只剩 C，C 消费后生成 completed tombstone 是正确安全行为，并非逐目标消费器错误。
现改用独立、无敏感参数的 `snapshot` cue 冻结初始 C/E 受众，不削弱 `policy_matrix`。恢复最终工作
仍以冻结 `cueTargets - completedTargets` 为唯一权威来源；C 的逐目标消费写入 completedTargets，
第二次停服检查点继续保留同一 FINALIZE 实例，直到 E 消费后才整体墓碑化。纯自检覆盖 C 部分消费、
第二次完整停服与恢复、E 最后消费，以及 definition generation 变化时先于披露 fail-closed。

2026-08-04 首次新夹具实机复测未收到内容。日志确认调用时世界仍处于 `setup`，
而夹具误限定为 `running/acceptance/main_branch`；包装函数又无条件打印“已自动暂停”，
导致创建拒绝被伪装为成功。现已移除该纯重启验收的阶段／任务绑定，并让两个包装函数
分别捕获 play 与 pause 的真实命令结果；只有两者都成功才显示蓝色成功提示，否则明确红色拒绝。

2026-08-04 修正后五端实机定向复测通过：单函数真实创建并暂停 C/E 同一快照受众实例；
E 离线后第一次正常停服／恢复只向 C 投递一次静态最终 Chat，E 保持待消费；E 继续离线的
第二次完整停服／恢复后，首次加入只收到一次同一静态内容，C 不重放，E 再次重连也不重放。
另一条只面向 E 的暂停实例在冷启动后、E 加入前执行 `/reload`；E 首次与再次加入均无任何内容、
动画或声音，证明 generation 失效实例已在披露前 fail-closed 并一次性静默退休。

状态：**通过**

### 3B-30 `restart=cancel`

**前置状态**

- 只把世界内 `policy_matrix.json` 的 `policies.lifecycle.restart` 临时改为 `cancel`；
- `/reload` 成功；当前上下文合法。

**操作客户端与命令**

1. 播放给 PlayerC 并立即暂停；
2. 正常保存退出并重开；
3. B～E 重连并查询 `last_callback`；
4. 尝试按旧 UUID 执行一次控制；
5. 测试后把世界副本恢复为仓库默认 `continue` 并 `/reload`。

**五端观察**

- Player972：旧实例被取消并退休；旧 UUID 不再是活动实例；无正常完成回调。
- PlayerB：无旧内容。
- PlayerC：无旧动画、最终补文或声音。
- PlayerD：无旧内容。
- PlayerE：无旧内容。

**唯一通过标准**

cancel 冷启动只执行一次取消和清理，不补最终正文、不写正常完成回调、不留下可控制活动实例，也不影响新实例。

**失败记录**

记录旧 UUID 控制回显、五端旧内容、回调、历史和新调用是否受影响。2026-08-03 本项依赖
`policy_matrix` 的可持久化活动实例；当前实例在冷启动前已经因 3B-11 依赖快照缺陷失效，无法区分
依赖失败清理与 `restart=cancel` 语义，暂缓至统一修复后回归。

2026-08-03 复验未通过：单目标实例 `7ed7af7a-9f43-43eb-aa5f-f8084f105168` 以
`restart=cancel`、`paused`、elapsed=0 正常落盘；冷启动后五端均无旧内容，但服务端明确记录该实例
因“冻结去重或刷新地址与 cue/参数不一致”恢复映射错误而被异常取消，并非按 `restart=cancel`
正常结算。表面结果相同但权威原因错误，不能证明 cancel 语义，留待本轮结束后统一修复。

2026-08-03 本轮修复已落地：冻结去重／刷新地址校验仅用于 `restart=continue` 恢复；
`restart=cancel` 不再因无关恢复映射错误异常取消，而是按注册策略正常执行权威取消。冷启动原因、
无补文、无回调和旧 UUID 退休待实机复测。

2026-08-04 修复后实机回归通过：在当前 `setup` 上下文以 PlayerC 为目标创建并同 Tick 暂停
`restart=cancel` 实例，正常保存退出并重开后五端均无旧动画、最终补文或声音；`last_callback`
不存在，按 cue 恢复明确报告没有匹配的动态消息实例。旧实例按注册策略取消并退休，未留下可控制
活动实例。测试后世界副本已恢复仓库默认 `restart=continue` 配置。

状态：**通过**

### 3B-31 玩家重生 `respawn`

**前置状态**

- 备份世界内 `policy_matrix.json`；
- PlayerB、C、E 都已初始化，C 的复活点可控；
- 每个子项开始前清除旧实例、历史与 `last_callback`；
- 本项分别把 `policies.lifecycle.respawn` 改为 `continue`、`finalize`、`cancel` 并逐次 `/reload`，其他配置保持不变。

**操作客户端与命令**

1. 每种模式先只播放给 PlayerB，正文播放中执行 `/kill PlayerB`，B 点击重生；
2. 再给 PlayerC、PlayerE 加同一临时标签，以一个多目标实例播放；正文播放中只执行 `/kill PlayerC`，E 不做任何操作；
3. `finalize` 子项要在至少一个动态字段尚未锁定时击杀 C，观察最终内容取值；
4. 额外做一次跨维度重生：Player972 与 C 位于同一非主世界维度时创建 `attachment=world` 实例，让 C 死亡后回到主世界；该次临时设为 `respawn=finalize`、`dimension_exit=cancel`；
5. 额外验证“服务端已自然 `FINISH`、客户端仍在排空”的重生边界，只观察 PlayerC 窗口：
   - 把 C 的本地播放速度临时设为 `0.5×`；在 `lifecycle/player_notice.json` 的 `policies.lifecycle`
     中临时注册 `respawn: cancel`；
   - 准备第二条短提示参数：

     ```mcfunction
     /data modify storage pixel_tzz:acceptance_3b drain_call set value {player_name:"PlayerC",message:"FINISH 排空生命周期验证"}
     ```

   - 先播放长 Subtitle，等 C 端已经开始逐字显示后，立即播放第二条：

     ```mcfunction
     /pixel_tzz_pro message play pixel_tzz:acceptance/field_capture to PlayerC with storage pixel_tzz:acceptance_3b layout_call
     /pixel_tzz_pro message play pixel_tzz:lifecycle/player_notice to PlayerC with storage pixel_tzz:acceptance_3b drain_call
     ```

   - 第一条长提示结束、第二条“FINISH 排空生命周期验证”刚开始显示时立刻 `/kill PlayerC` 并重生。
     第二条的服务端标准时间线此时已经结束，它只能来自客户端保留的已授权排空队列；重生后必须
     按 `cancel` 立即消失，不继续打字、不补最终帧、不重播字符音；
   - 恢复 C 的本地速度与 `player_notice.json`，删除 `drain_call` 后 `/reload`。
6. 每个子项结束后查询 `last_callback` 和实例列表；最后恢复备份并 `/reload`。

**五端观察**

- Player972：重生决定来自服务端；单目标和多目标都只改变实际重生者。跨维度重生先采用 `respawn=finalize`，不会随后又被 `dimension_exit=cancel` 覆盖。
- PlayerB：`continue` 从权威位置继续；`finalize` 只得到一次服务端确认的最终内容；`cancel` 无补文、无旧声音。客户端不能在收到服务端决定前自行提前 fallback。
- PlayerC：多目标与跨维度子项遵循对应模式；`finalize` 中尚未锁定的动态字段按服务端最终捕获规则解析一次。
- PlayerD：非目标 OP 无内容，也不能影响重生决定。
- PlayerE：C 重生时自己的同一实例连续播放，不被暂停、补全、取消或重建。

**唯一通过标准**

`continue`、`finalize`、`cancel` 在单目标与多目标下都只作用于重生玩家；跨维度重生只结算一次且
respawn 先于 dimension-exit；普通网络实例等待服务端决定，已经收到 `FINISH` 的本地排空实例则
只应用已投影的 `respawn` 策略；动画、声音、目标完成与回调均不重复，实例级回调恰好一次。

**失败记录**

记录三种配置、单/多目标 UUID、死亡维度与重生维度、死亡前帧、重生首帧、未锁字段最终值、各端声音和
`last_callback`。2026-08-03 实机复验结果：普通网络实例的 `continue`、`finalize`、`cancel`
单目标与多目标路径均通过；跨维度重生中 `respawn` 优先于 `dimension_exit=cancel` 的路径通过。
FINISH 排空边界的首次复验暂不能定论：原夹具使用黄色超长 `field_capture` 占用 Subtitle 通道，
PlayerC 重生后持续显示并随后自然消失的仍是这条占位消息，而不是已经投影
`respawn=cancel` 的第二条 `player_notice`。该现象不证明第二条取消失败；需改用短且视觉可区分的
占位消息，确认第二条真正开始显示后再触发重生。

最终夹具去除跨 cue 占位，只投影一条长 `player_notice` 并在 2 秒时击杀 PlayerC。服务端已经
自然 `FINISH`、客户端仍在排空时，C 点击重生立即清除目标 Subtitle 并停止声音，之后没有补最终帧
或重新出现；修正后的 FINISH 排空 `respawn=cancel` 实机通过。

状态：**通过**

### 3B-32 离开冻结维度 `dimension_exit`

**前置状态**

- 备份世界内 `policy_matrix.json`；
- PlayerC、E 在线且可使用 `/execute in ... run tp ...` 在主世界、下界与末地间切换；
- 每个子项开始前清除旧实例、历史与 `last_callback`。

**操作客户端与命令**

1. 把 `attachment` 设为 `world`，依次把 `dimension_exit` 设为 `continue`、`finalize`、`cancel`；每种模式先对 C 做一次单目标调用，再对 C、E 做一次同实例多目标调用；
2. 实例在主世界创建后，只让 C 从主世界进入下界；E 留在主世界；
3. 在 `continue` 子项中继续让 C 从下界进入末地、返回主世界、再次离开，核对只有“离开冻结 origin 维度”才算事件；
4. 把 `attachment` 改为 `player`，保留最严格的 `dimension_exit=cancel`，让 C 连续跨越三个维度；
5. 额外验证 `FINISH` 排空中的 attachment 归一化，只观察 PlayerC 窗口：
   - 沿用 3B-31 的 `drain_call` 与“长 `field_capture` 先占用 Subtitle、短
     `lifecycle/player_notice` 随后排队”的两条命令，并保持 C 为 `0.5×`；
   - 先把 `player_notice.json` 临时设为 `attachment: world`、`dimension_exit: cancel`。等第二条
     “FINISH 排空生命周期验证”刚开始显示时，把 C 从冻结 origin 维度传送到下界；该排空提示必须立即
     取消；
   - 再把同一文件临时设为 `attachment: player`、仍保留 `dimension_exit: cancel`，`/reload` 后
     从头创建两条提示；第二条刚开始显示时再次跨维度，它必须继续完整播放，因为服务端投影已把
     玩家绑定的有效 `dimension_exit` 归一为 `continue`；
   - 恢复 C 的本地速度、`player_notice.json` 与 `drain_call` 后 `/reload`。
6. 每个子项查询实例与 `last_callback`；最后恢复备份并 `/reload`。

**五端观察**

- Player972：world 绑定只对真正离开冻结 origin 维度的目标路由策略；非 origin 维度之间切换、返回 origin 都不误触发。多目标中 E 始终保持原状态。
- PlayerB：非目标无内容。
- PlayerC：world+continue 跨维度继续；world+finalize 只补一次最终内容；world+cancel 立即取消且无补文；player 绑定在任何跨维度路径都继续。
- PlayerD：非目标 OP 无内容。
- PlayerE：C 换维度时自己的播放、声音和完成状态不变化；只有 E 自己离开 origin 才应用 E 的目标策略。

**唯一通过标准**

`attachment=world` 仅在目标离开冻结 origin 维度时按 `continue/finalize/cancel` 处理该目标；
`attachment=player` 始终跨维度继续；普通网络实例等待服务端决定，`FINISH` 后的客户端排空只消费
已经归一化的 attachment 策略；返回 origin、非 origin 间切换和其他玩家换维度都不误触发、
不重复回调。

**失败记录**

记录 attachment 与三种策略、origin/previous/current 维度、单/多目标 UUID、各端首末帧、声音、
最终内容次数和 `last_callback`。2026-08-03 本项所有 attachment 与 dimension-exit 组合均依赖
`policy_matrix`；当前 cue 被 3B-11 依赖快照缺陷阻止生成投影，暂缓至统一修复后回归。

2026-08-03 实机复验进度：`attachment=world + dimension_exit=continue` 单目标与 C、E 多目标
隔离路径均通过；PlayerC 离开冻结 origin 维度后演出按原进度继续，PlayerE 不受影响，均无取消、
补全或重播。其余组合继续验收。

同日继续复验：`attachment=world + dimension_exit=finalize` 单目标与 C、E 多目标隔离路径均通过；
PlayerC 离开 origin 维度时只收束为一次最终内容，PlayerE 继续原动画，双方互不影响，未出现继续
动画、消失、重播或重复发声等错误。

同日继续复验：`attachment=world + dimension_exit=cancel` 单目标与 C、E 多目标隔离路径均通过；
PlayerC 离开 origin 维度时演出与声音立即取消，PlayerE 继续原动画，双方互不影响，无最终正文、
重播或延迟重新出现。

同日继续复验：`attachment=player + dimension_exit=cancel` 归一化路径通过；PlayerC 自动连续跨越
末地、下界和主世界时，演出及声音始终按原进度继续，未触发取消、补全、重播或停顿。

FINISH 排空的首次维度复验同样不能定论：跨维度后仍显示的是黄色超长 `field_capture` 占位消息，
并非配置了 `attachment=world + dimension_exit=cancel` 的第二条 `player_notice`。原夹具在第二条
取得 Subtitle 通道前就触发了维度事件；需缩短第一条并延后触发，确保观察对象明确后重测。

第二版夹具仍无效，但原因进一步明确：优先级 42 的 `player_notice` 先于优先级 20 的
`field_capture` 取得 Subtitle 通道，目标消息已经自然结束后才播放所谓占位消息；8 秒的维度事件
因此没有任何目标消息可取消。最终夹具改为只创建一条长 `player_notice`，并在 2 秒时触发事件，
不再依赖跨 cue 排队顺序。

最终夹具实机复验通过：服务端已自然 `FINISH`、PlayerC 客户端仍在排空长 `player_notice` 时，
`attachment=world + dimension_exit=cancel` 在跨维度瞬间立即清除目标 Subtitle 并停止声音，之后
没有补最终帧或重新出现。

同一 FINISH 排空边界的 `attachment=player + dimension_exit=cancel` 归一化路径也通过；PlayerC
跨维度后从原位置、原进度继续播放，完整自然结束，无取消、补全、重播或重复声音。当前主持人
Player972 同时看到该提示属于 `player_notice` 明确注册的 `current_host` 受众，不是命令执行者泄漏。

状态：**通过**

## 11. H 组：历史、错误隔离与最终回归

### 3B-33 演出历史的受众、持久化与不可重播

**前置状态**

- 世界副本已恢复仓库默认；
- 当前上下文合法；
- PlayerC 是猎人；
- 五端“过去事件”已记录现有内容，便于区分新项。

**操作客户端与命令**

1. Player972 播放并等待自然完成：

   ```mcfunction
   /pixel_tzz_pro message play pixel_tzz:acceptance/policy_matrix to PlayerC with storage pixel_tzz:acceptance_3b call
   ```

2. 五端分别打开：`ESC → 玩家/主持人终端 → 过去事件`；
3. 执行 `/reload` 后再看；
4. 让 PlayerC 退出并重连后再看。

**五端观察**

- Player972：能在主持人技术视图确认实例，但不能因为主持人身份获得仅 call target 的玩家历史副本。
- PlayerB：不出现该记录。
- PlayerC：出现一条已解析、静态的“任务提示回顾”，保存的是完成时字段值；没有重播入口。
- PlayerD：不因 OP 出现该记录。
- PlayerE：不出现该记录。

**唯一通过标准**

历史只向数据包声明的 PlayerC 保存一次，内容为当时锁定最终值；`/reload` 和重连后仍在，其他四端始终不可见且不能重播。

**失败记录**

记录五端历史列表、标题/正文/时间、重复项、重连前后变化和任何越权内容。2026-08-03 本项以
`policy_matrix` 自然完成产生不可重播历史，但该 cue 当前被 3B-11 依赖快照缺陷阻止生成投影，
因此无法产生待核对记录；暂缓至统一修复后回归。独立的可重播历史夹具 3B-33A 不依赖该 cue，继续测试。

2026-08-03 实机复验进度：`policy_matrix` 自然完成后，PlayerC 的“过去事件”新增并默认选中一条
静态“『策略演出回顾』”，内容为完成时最终值且没有重播按钮；Player972、B、D、E 均无该记录，
目标历史受众隔离通过。正文中的 `pixel_tzz:acceptance/main_branch` 是验收 cue 主动展示的字段值，
所属任务名称仍正确显示为数据包声明的中文“2D 验收 · 分支任务”，不属于汉化缺失。Reload 与重连
后该记录仍只有一条，内容、发生时间、详情与不可重播状态均保持不变，完整实机通过。

状态：**通过**

### 3B-33A 已授权历史安全重播

**前置状态**

- 3B-33 已完成，PlayerC 仍可进入“过去事件”；
- 执行过 `acceptance_3b/setup`，当前上下文仍是 `running/main_branch`；
- 记录 PlayerC 当前历史条目数量，并清除旧回调观察位。

**操作客户端与命令**

1. Player972 执行并等待自然完成：

   ```mcfunction
   /data remove storage pixel_tzz:acceptance_3b last_callback
   /pixel_tzz_pro message play pixel_tzz:acceptance/history_replay to PlayerC with storage pixel_tzz:acceptance_3b call
   /data get storage pixel_tzz:acceptance_3b last_callback
   ```

2. PlayerC 打开 `ESC → 玩家终端 → 过去事件`，默认选中最新的“『安全重播验收』”；
3. 进入详情，点击一次“重新播放”，完整观察文字与结束音；
4. 再查询 `last_callback`，确认 `instance`、`game_time` 和整份内容没有变化；
5. 关闭并重开终端，再执行一次 `/reload`，确认记录仍只有一条且详情仍可打开；
6. PlayerC 退出并重连，从同一条记录再重播一次。

**五端观察**

- Player972：正式调用只执行一次 callback；每次重播都不改写 `last_callback`，也不新增历史；
- PlayerB：没有收到正式播放或重播；
- PlayerC：只有自己能看到按钮；重播使用保存字段的最终值，不重新读取当前玩家名/Score/Storage，
  不出现红页，不跳回错误历史页；
- PlayerD：即使是 OP，也不能看到或重播 PlayerC 的记录；
- PlayerE：无目标内容。

**唯一通过标准**

获准查看者可以从同一条 opaque 历史记录安全重播文字与声音；重播不执行正式 callback、不写第二
条历史、不扩大受众，并在 `/reload`、终端重开和同一玩家重连后保持权威可用。

**失败记录**

记录正式/重播的实例 UUID、历史数量、三次 `last_callback`、五端可见性、重播字段正文、声音次数
及 Reload/重连后的按钮状态。2026-08-03 首次尝试在世界重开后的 `pixel_tzz:setup` 阶段发起，
服务端按 context 策略明确拒绝“当前游戏、阶段或任务不允许发起该动态消息”，没有创建半成品实例
或历史；这属于前置状态未满足，不计产品失败。3B-36 推进至 `running/main_branch` 后再次发起，
正式演出自然完成且只为 PlayerC 新增一条“『安全重播验收』”，详情与重播按钮可正常打开；
Player972/B/D/E 均无该记录，第一阶段实机通过。重播、Reload 与重连持久化待继续验证。

PlayerC 第一次点击“重新播放”后，保存文字与结束音正常播放；`last_callback` 仍保持正式演出的
instance、game_time、occurrence 与 cycle，没有被重播改写，历史未新增第二条，其他四端未收到
重播内容，实机通过。终端重开、`/reload` 与 PlayerC 重连后仍只有同一条记录，详情和按钮保持
可用；再次重播同样未改写 callback、未新增历史、未重新读取实时字段或扩大受众，实机通过。

状态：**通过**

### 3B-34 单次调用错误隔离

**前置状态**

- 没有旧 `bad_call`；
- 先启动一条合法、较长的 PlayerB 演出。

**操作客户端与命令**

动画进行时执行：

```mcfunction
/data modify storage pixel_tzz:acceptance_3b bad_call set value {count:"not-an-integer"}
/pixel_tzz_pro message play pixel_tzz:acceptance/field_capture to PlayerC with storage pixel_tzz:acceptance_3b bad_call
```

随后再对 PlayerC 执行一条合法调用。

**五端观察**

- Player972：坏调用明确拒绝并只向命令源给出有限错误；合法实例继续。
- PlayerB：原合法演出不中断、不重启。
- PlayerC：坏调用不产生半成品；随后的合法调用正常。
- PlayerD：不出现错误页或错误正文。
- PlayerE：同 D。

**唯一通过标准**

一个参数类型错误只拒绝该调用，不取消、污染或阻塞其他活动/后续合法实例，普通玩家端无红色错误 UI。

**失败记录**

2026-08-03 PlayerB 的长合法演出播放期间，以字符串 `count` 发起 PlayerC 坏调用。服务端只向
命令源明确拒绝该参数类型错误，没有创建半成品；PlayerB 原演出不中断、不重启，PlayerC 随后的
合法调用正常，D/E 无错误页或错误正文，实机通过。

状态：**通过**

### 3B-35 单资源注册错误隔离

**前置状态**

- 只在世界内数据包副本新增：

  ```text
  data/pixel_tzz/pixel_tzz_pro/message_cues/acceptance/broken_probe.json
  ```

- 文件内容：

  ```json
  {
    "format_version": 999,
    "required": false,
    "nodes": []
  }
  ```

**操作客户端与命令**

Player972 执行：

```mcfunction
/reload
/pixel_tzz_pro message list 1
/pixel_tzz_pro message inspect pixel_tzz:acceptance/broken_probe
/function pixel_tzz:acceptance_3b/play/default
```

随后删除世界副本中的 `broken_probe.json` 并再次 `/reload`。

**五端观察**

- Player972：坏资源被单独隔离，诊断包含资源 ID、文件/字段位置和有限错误；合法 cue 仍可播放。
- PlayerB：不弹错误页，不收到诊断正文。
- PlayerC：同 B。
- PlayerD：同 B，即使是 OP 也不自动收到完整诊断。
- PlayerE：同 B。

**唯一通过标准**

一个非关键无效 cue 只禁用自己，合法 generation、现有实例和普通玩家体验继续可用；删除坏文件后诊断干净恢复。

**失败记录**

2026-08-03 在验收世界新增可选坏资源 `broken_probe`，其 `format_version: 999` 且节点为空。
`/reload` 后该资源单独显示“已禁用 · 可选”，诊断准确指出格式版本期望 1、实际 999，以及 nodes
至少需要一个节点；其他合法 cue 保持可用，合法动态演出正常发起。完整诊断只对命令源可见，
PlayerB/C/D/E 均无红色诊断、错误页或错误正文，实机通过。临时坏文件删除后再次 `/reload`
成功，资源不再出现在目录中，inspect 明确报告不存在且没有残留旧诊断。

状态：**通过**

### 3B-36 数据包生命周期 Hook 与业务隔离

**前置状态**

- 世界内基础包与仓库同版，未安装压力覆盖包；
- 没有残留的活动强制流程或任务时间线；
- Player972 是主持人，B 为逃走者，C 为猎人，D/E 为普通对照玩家；
- 五端都已返回游戏画面，没有终端或强制页遮挡 Subtitle。

新世界的首个 `setup` Phase `enter` 在服务器首次成功提交 Game Instance 时产生；此时通常尚无
在线主持人，因此空受众按 cue 策略静默完成，不要求玩家端补播。它的“首次提交恰好一次、
Reload/冷启动不补播”由 `messageHookDispatchContractSelfCheck` 与
`worldActivityInitializationSelfCheck` 共同固定；本项实机观察从已有在线
主持人的后续业务边缘开始。

**操作客户端与路径**

沿用已经验收的 2D 主持人控制顺序完成一条最短业务链，并分段观察，不要一次连续点完：

1. 对 PlayerC 发起并完成猎人身份初始化；
2. 发起普通强制流程，让 PlayerB 单独完成，再让剩余目标完成；
3. 进入玩家准备并让所有必需玩家逐一完成；记录准备 Flow 的 `start`、每名玩家各一次
   `player_complete`、最后一名完成后的 `all_complete`，以及随后专用 Readiness `complete` 的顺序；
4. 主持人批准开局，使阶段、Game 和首个 Task 正常开始；
5. 首个 `pixel_tzz:acceptance/warmup` 没有 Task Event。先执行下面的正式数据包入口，等待
   `pixel_tzz:acceptance/main_branch` 开始：

   ```mcfunction
   /function pixel_tzz:acceptance_2d/warmup/complete
   ```

6. 在 `main_branch` 运行时记录一次 `terminal_activated`，再从主持人终端暂停、继续当前游戏：

   ```mcfunction
   /function pixel_tzz:acceptance_2d/main/record_terminal
   ```

   该包装函数会从回调缓存读取当前任务 UUID，并依次调用权威 `record_event` 与
   `set_statistic`。若需同时验收带玩家来源的自动参数，先只读取缓存：

   ```mcfunction
   /data get storage pixel_tzz:acceptance_2d session.current.task_instance_id
   /pixel_tzz task record_event <上一步显示的 UUID> terminal_activated PlayerB
   ```

7. 继续后正常提前结算当前任务，并等待任务完成提示进入队列：

   ```mcfunction
   /function pixel_tzz:acceptance_2d/main/succeed
   ```

8. 任务已离开 `running` 后再次执行 `main/record_terminal`，确认命令被拒绝且不补播事件
   Hook；随后执行一次 `/reload`，同样不能补播任何已经发生的生命周期 Hook。

每一步都等当前 Subtitle 队列完整展示后再继续。若页面仍打开，先明确返回游戏再计时。

**五端观察**

- Player972：依次看到数据包注册的中文身份、流程、准备、阶段、游戏、任务与事件提示；同 Tick
  产生的多条消息排队展示，每条正文都有可读停留时间；准备开始只出现一次，最后一名完成时
  Flow 全员完成先于 Readiness 完成。
- PlayerB：只收到自己有权看到的 `player_notice`；完成强制页前不在页面后偷播过期成功提示，
  返回游戏后只播放一次。
- PlayerC：只在自己的身份初始化/变化等目标事件收到玩家提示，不收到 B 的专属提示。
- PlayerD：即使是非主持人 OP，也不因权限等级收到主持人专属生命周期消息。
- PlayerE：不属于目标的事件不显示；全局业务仍按 cue 自己声明的受众投影。

**唯一通过标准**

七类定义拥有者的 Hook 都只在权威事务成功提交后的对应上升沿播放一次，固定中文参数与服务端
玩家/进度/结果事实正确合并；拒绝操作、页面重绘、定期同步和 `/reload` 不补播。任何 cue
播放失败都不能回滚、重复推进或改变已经提交的身份、流程、准备、阶段、游戏、任务和事件状态。
准备使用的 Flow 必须完整保留 Flow 自己的三个 Hook，并额外触发 Readiness `complete`；Reload、
冷启动恢复或准备页面重建不得补播 `start`。

**失败记录**

记录拥有者类型、Hook 事件、触发按钮/命令、事务前后状态、五端实际受众、Subtitle 顺序与次数、
是否仍有页面遮挡，以及 `latest.log` 中首条 `V3B declarative message hook` 警告。

2026-08-03 第 1 步：Player972 对 PlayerC 发起并完成猎人初始化。主持人与 C 分别收到获准的
中文身份／初始化生命周期提示，B/D/E 未收到 C 的专属提示；提示只播放一次，页面同步未导致重复，
Subtitle 正文停留与队列顺序正常，实机通过。

第 2 步前半：通用初始化全局强制流程只触发一次 start。仅 PlayerB 完成时，只产生一次 B 的
player_complete；提示在 B 明确返回游戏后播放，C/D/E 未收到 B 的个人提示，流程未提前产生
all_complete，实机通过。

第 2 步后半：剩余目标逐一完成，最后 PlayerE 的 player_complete 先播放，随后才产生流程
all_complete；两条各一次且未互相覆盖。主持人与各玩家只收到获准提示，尚未进入玩家准备时没有
提前产生 Readiness complete，实机通过。

第 3 步：玩家准备 Flow 的 start 只出现一次，B/C/D/E 依次完成时各自产生一次 player_complete；
最后 PlayerE 完成后严格按 E player_complete → Flow all_complete → Readiness complete 播放，
三类提示没有覆盖或重复，页面、BossBar 与同步刷新没有补播 start，实机通过。

第 4 步：主持人批准开局后，阶段、Game 与首个热身 Task 的中文生命周期提示按权威上升沿排队，
每条正文停留可读且各一次；五端受众符合数据包声明，非主持人 OP 未获得主持人专属提示，页面与
同步刷新未造成补播，实机通过。

第 5 步：执行热身完成入口后，前一条热身完成提示播放时，后一条主线任务开始提示仅闪现后消失；
观察上前一条疑似覆盖了后一条的动画，主线开始正文没有获得可读停留时间。相邻 Task 完成／开始
Hook 未进入稳定的统一 Subtitle 队列，本步视觉验收不通过；业务状态是否已正确进入 main_branch
继续由后续合法上下文调用确认。

第 6 步前半：在 main_branch 执行 `main/record_terminal`，terminal_activated 任务事件只提交并
提示一次，中文事实与受众正确，非主持人 OP 未收到主持人专属消息，任务、身份与阶段未意外变化，
实机通过。

第 6 步后半：主持人从控制台暂停并继续当前游戏，两次操作各产生一次对应中文 Hook；提示在明确
返回游戏后依序完整播放，没有页面后偷播、覆盖或闪现，主持人主面板未整页重绘，五端受众正确，
页面与同步更新未补播，实机通过。

第 7 步：执行 `main/succeed` 后，结果提交、任务完成与相关阶段提示按权威事务成功顺序完整排队，
各播放一次，没有闪现或覆盖；中文任务名、结果与参数正确，五端受众符合声明，业务状态正常离开
running，实机通过。

第 8 步：任务离开 running 后再次执行 `main/record_terminal` 被明确拒绝，没有提交事件或播放
新 Hook；随后 `/reload` 未补播该拒绝事件，也未重放此前身份、流程、准备、阶段、Game、Task 或
事件 Hook，同步状态更新后保持稳定，实机通过。

2026-08-03 修复后定向实机回归通过：相邻生命周期 Subtitle 按队列顺序依次从首帧完整播放，
后一条不再闪现、被前项覆盖或直接跳到最终态；正文停留、受众和业务隔离均未回退。

状态：**通过**

### 3B-37 最终清理与 V3A/2D 回归

**前置状态**

- 3B-01～3B-36 均已实机通过，不存在待实机复测或 BLOCKED 项；
- 所有临时世界副本修改已恢复；
- Chat Heads 等兼容模组按后续用途决定是否移除。

**操作客户端与命令**

1. Player972 执行：

   ```mcfunction
   /function pixel_tzz:acceptance_3b/reset
   /reload
   ```

2. 五端分别检查 ESC 入口、主持人/玩家终端、当前任务、过去事件和强制流程；
3. Player972 再执行一次基础四通道调用；
4. 退出所有客户端后再次运行完整自动门。

**五端观察**

- Player972：主持人控制台、2D 时间线和 V3A 玩家投影没有被 V3B 污染；基础演出仍正常。
- PlayerB：普通终端、ESC 返回和强制页优先级保持 V3A/2D 行为。
- PlayerC：猎人身份投影、当前任务和个人字段正常。
- PlayerD：仍只有玩家终端加接管入口，不获得主持人消息权限。
- PlayerE：重连后终端和消息入口正常，无旧待投递。

**唯一通过标准**

临时夹具全部清理，世界副本回到仓库同版定义，V3B 基础调用与既有 V3A/2D 核心入口同时正常，完整 `clean check build` 再次通过。

**失败记录**

2026-08-03 执行 `acceptance_3b/reset` 与 `/reload` 后，3B 活动实例、临时 Storage、Score 与
回调观察位已清理，五端无旧待投递演出；V3A/2D 游戏、玩家、任务与历史业务数据未被重置，
Reload 成功且无临时坏资源或定义错误。五端入口、基础演出与自动门待继续验证。

五端随后检查旧功能：Player972 主持人控制台、2D 时间线、玩家名单与回顾正常；PlayerB 普通终端
与 ESC 逐级返回正常且无残留强制页；PlayerC 猎人身份、个人信息与结束后任务／历史投影正常；
PlayerD 仅有玩家终端和接管入口，没有主持人权限；PlayerE 重连后入口正常且无旧待投递，实机通过。

最终基础四通道调用正常显示 Chat、Title、Subtitle 与 ActionBar；reset 后计分字段使用默认 `0`，
Subtitle 使用安全兜底“暂无记录”，没有读取旧 Storage、Score、回调或恢复旧实例，实机通过。

关闭五个客户端与集成服务器后执行 `gradlew clean check build --console=plain --no-daemon`，
80 个任务全部实际执行，所有 SelfCheck 与测试通过，最终输出 `BUILD SUCCESSFUL in 2m 39s`。
HostFlowBossBar 的预期畸形 JSON 自检路径打印警告栈但该 SelfCheck 明确 PASS，不属于构建失败。

2026-08-03 集中修复与 FINISH 排空验收收口后再次执行同一条完整命令，80 个任务全部重新执行，
最终输出 `BUILD SUCCESSFUL in 2m 48s`。新增玩家发言者夹具后的基础包规模为 150 个文件、
96 项定义；HostFlowBossBar 仍只打印其预期畸形 JSON 回退警告，SelfCheck 明确 PASS。

2026-08-03 本轮第一轮集中修复后再次执行完整 `clean check build`，81 个任务全部实际执行，
基础数据包统计为 179 个文件、96 项定义、80 个函数，最终输出 `BUILD SUCCESSFUL in 2m 58s`。
该结果只证明自动门通过，不替代 Chat、3B-19、3B-29 和 3B-30 的五端实机复测；后续安全审查
追加的修复仍须再执行同一完整自动门。

2026-08-03 本轮最终集中修复后执行完整 `gradlew clean check build --console=plain --no-daemon`，
81 个任务全部实际执行，所有 SelfCheck 与测试通过；基础数据包统计为 179 个文件、96 项定义、
80 个函数，最终输出 `BUILD SUCCESSFUL in 2m 56s`。该结果只证明自动门通过，不替代 Chat、
3B-19、3B-29 和 3B-30 的五端实机复测。

2026-08-04 修复局域网加入时的 Chat Mixin 空状态崩溃后，再次执行同一完整命令；81 个任务全部
实际执行，所有 SelfCheck 与测试通过，最终输出 `BUILD SUCCESSFUL in 2m 47s`。该结果不替代
Player972 开放局域网、PlayerB 加入／退出及普通系统 Chat 的实机回归。

2026-08-04 移除猎人初始化重复 Subtitle Hook、新增专用 `restart_finalize` 快照受众夹具，
并补齐单函数暂停入口后，再次执行完整 `clean check build`。81 个任务全部实际执行，
基础数据包统计为 181 个文件、97 项定义、81 个函数，最终输出 `BUILD SUCCESSFUL in 2m 48s`。
该结果只证明自动门通过；3B-19 与 3B-29 仍须下方两项实机定向复测。

2026-08-04 完成 3B-19 与 3B-29 五端定向实机回归后，再次执行完整
`gradlew clean check build --console=plain --no-daemon`。81 个任务全部实际执行，所有 SelfCheck
与测试通过；基础数据包统计为 181 个文件、97 项定义、81 个函数，最终输出
`BUILD SUCCESSFUL in 2m 36s`。数据包同步到 `3A V1` 后核对源与目标均为 181 个文件，
`missing=0`、`changed=0`、`extra=0`。最后执行 `/reload` 与
`/tag @a remove acceptance_3b_target`，命令无报错，现有验收世界不再残留临时目标标签。

状态：**通过**

## 12. 实机状态总表

| 编号 | 项目 | 状态 |
|---|---|---|
| 3B-01 | 数据包安装与世界基线 | 通过 |
| 3B-02 | 五客户端与静默资源同步 | 通过 |
| 3B-03 | 目录、审阅、预览与权限 | 通过 |
| 3B-04 | 四通道统一播放 | 通过 |
| 3B-05 | Chat 原位记录与普通聊天共存 | 通过 |
| 3B-06 | 光标、新字覆盖色与回色 | 通过 |
| 3B-07 | 三种动态字段捕获时机 | 通过 |
| 3B-08 | 长中文、Emoji、换行与富文本 | 通过 |
| 3B-09 | 同 ID 去重 | 通过 |
| 3B-10 | 跨实例通道排队 | 通过 |
| 3B-11 | 原位 refresh | 通过 |
| 3B-12 | 实例级主动控制 | 通过 |
| 3B-13 | cue/group/target 控制 | 通过 |
| 3B-14 | 本地偏好与权威时钟 | 通过 |
| 3B-15 | 对比度、声音与偏好持久化 | 通过 |
| 3B-16 | 手动补全按键 | 通过 |
| 3B-17 | Chat Heads 兼容 | 通过 |
| 3B-18 | ESC 与玩家终端遮挡 | 通过 |
| 3B-19 | 强制页面优先级 | 通过 |
| 3B-20 | 窄窗口与 GUI Scale | 通过 |
| 3B-21 | 低 FPS 时间线 | 通过 |
| 3B-21A | 外部 HUD 冲突策略 | 通过 |
| 3B-22 | 掉线后及时重连 | 通过 |
| 3B-23 | 晚加入 live_add | 通过 |
| 3B-24 | 离线 TTL 到期 | 通过 |
| 3B-25 | 数据包 /reload 与冻结 generation | 通过 |
| 3B-26 | 客户端资源 Reload | 通过 |
| 3B-27 | restart=transient | 通过 |
| 3B-28 | restart=continue | 通过 |
| 3B-29 | restart=finalize | 通过 |
| 3B-30 | restart=cancel | 通过 |
| 3B-31 | 玩家重生 respawn | 通过 |
| 3B-32 | 离开冻结维度 dimension_exit | 通过 |
| 3B-33 | 演出历史 | 通过 |
| 3B-33A | 已授权历史安全重播 | 通过 |
| 3B-34 | 单次调用错误隔离 | 通过 |
| 3B-35 | 单资源注册错误隔离 | 通过 |
| 3B-36 | 生命周期 Hook 与业务隔离 | 通过 |
| 3B-37 | 最终清理与旧功能回归 | 通过 |

V3B 的全部必需实机项均已逐项取得用户反馈，失败项已完成修复回归，完整自动门再次通过；
功能提交 `ffb1309` 已通过 PR #5 合并主线，合并提交为 `d371dff`。
