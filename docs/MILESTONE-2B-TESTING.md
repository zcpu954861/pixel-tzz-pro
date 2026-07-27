# 第二里程碑 2B 验收记录

状态：**最终完整构建和协议 v5 独立服务端回归已通过；用户客户端实机验收尚待完成**

记录日期：2026-07-27  
模组版本：`0.1.0`  
Minecraft：`26.2`  
模组线协议：`5`

## 1. 验收边界

本里程碑验证数据驱动页面框架及其预览能力，包括：

- 页面和主题资源的严格解析、交叉引用校验及不可变 generation 发布；
- 服务端按需下发页面与主题文档，客户端按 generation 和内容哈希缓存；
- 数据驱动布局、样式、字体、Motion、音效、绑定、FieldInput、Repeat 和 Scroll；
- 数据包候选无效时拒绝整代候选，并保留上一代健康定义；
- `/reload` 只更新定义 generation，不修改持久化游戏状态。

2B 仍然只提供页面框架、预览和模拟反馈，不执行真实初始化、身份调整、准备、开局、捕获或其他权威游戏操作。相关按钮即使能够交互，也不得越过这一边界。

## 2. 当前结论

| 项目 | 当前状态 | 结论 |
| --- | --- | --- |
| 最终自动自检 | 已通过 | 定义注册、基础契约和页面框架自检均通过 |
| 最终 `clean check build` | 已通过 | 最终代码的 11 项任务全部执行成功 |
| 协议 v5 独立服务端启动 | 已通过 | 加载 `1` 个游戏、`22` 项定义 |
| 有效/无效/恢复 `/reload` | 已通过 | generation 为 `1 → 2 → 2 → 3` |
| 世界状态保护 | 已通过 | 全过程长度、SHA-256 和修改时间不变 |
| 示例与独服数据包一致性 | 已通过 | 示例、开发客户端与开发服务端均为 `31` 个文件，逐路径哈希差异为 `0` |
| 正常停服和端口释放 | 已通过 | 25565、25575 均已关闭 |
| 客户端视觉、交互、动画和音效 | **验收中** | 已修复规则页溢出和目录/页面打开音重叠；音效听感仍待用户复验 |
| 32/64 人页面性能 | **验收中** | 32 人压力页已完成一次打开、等待、滚动和返回回归；64 人及完整性能结论仍待验收 |

## 3. 自动验证证据

最终代码于 2026-07-27 重新执行：

```powershell
cd E:\minecraftserver\fabricmod\pixel-tzz-pro
.\gradlew.bat clean check build --console=plain
```

真实输出：

```text
DEFINITION_REGISTRY_SELF_CHECK=PASS
FOUNDATION_SELF_CHECK=PASS
PAGE_FRAMEWORK_SELF_CHECK=PASS
BUILD SUCCESSFUL
```

本次为 `clean` 后的完整验证，共有 `11 actionable tasks: 11 executed`。它覆盖编译、定义注册检查、基础契约检查、页面框架检查、测试、打包和 sources jar；不代替客户端视觉、声音、键鼠或性能实机验收。

## 4. 协议 v5 独立服务端回归

### 4.1 回归环境

- 独立服务端目录：`run/server`
- 世界目录：`run/server/world`
- 服务端数据包：
  `run/server/world/datapacks/Pixel-Tzz-Base-0.1.0`
- 回归所用模组线协议常量：`NetworkProtocol.CURRENT_VERSION = 5`
- 服务端绑定：`127.0.0.1:25565`
- RCON 绑定：`127.0.0.1:25575`

这次独服回归验证的是协议 v5 构建下的服务端生命周期和定义重载。独立服务端自身不能代替客户端握手测试；协议 v5 的实际客户端握手仍属于后文的实机验收项。

### 4.2 Generation 序列

服务端启动后成功加载：

```text
Applied Pixel TZZ definition generation 1: 1 game(s), 22 total definition(s)
Loaded Pixel TZZ world state: phase=idle, stateRevision=0
```

第一次使用健康数据包执行 `/reload`：

```text
Applied Pixel TZZ definition generation 2: 1 game(s), 22 total definition(s)
Data packs reloaded; Pixel TZZ definition status=ready, load sequence=1
```

随后只在独服安装副本的以下页面根对象中加入临时未知键：

```text
run/server/world/datapacks/Pixel-Tzz-Base-0.1.0/data/pixel_tzz/pixel_tzz_pro/pages/tutorial/welcome.json
```

临时键为：

```json
"intentional_2b_v5_invalid": true
```

再次执行 `/reload` 后，候选被严格拒绝：

```text
Pixel TZZ definition error: UNKNOWN_KEY pixel_tzz:pixel_tzz_pro/pages/tutorial/welcome.json/intentional_2b_v5_invalid: unknown key
Data packs reloaded, but Pixel TZZ definitions were rejected; generation 2 remains active
```

这次无效候选没有发布 generation 3，也没有覆盖上一代健康快照。

精确恢复页面原文件并再次执行 `/reload` 后：

```text
Applied Pixel TZZ definition generation 3: 1 game(s), 22 total definition(s)
Data packs reloaded; Pixel TZZ definition status=ready, load sequence=2
```

因此本次真实 generation 序列为：

```text
启动 1 → 有效 reload 2 → 无效候选仍为 2 → 修复 reload 3
```

### 4.3 世界状态不变量

世界状态文件：

```text
run/server/world/data/pixel-tzz-pro/world_state.dat
```

启动前、启动完成、有效重载、无效候选重载、恢复重载和正常停服后的采样结果一致：

```text
Length:           90 bytes
SHA-256:          4D3685AB647C663C64BABF7CB28CAD6EC15AE23BFB4A823F9466399C14782060
LastWriteTimeUtc: 2026-07-26T04:33:03.3148960Z
```

长度、内容哈希和修改时间全过程未变化。这证明本次定义重载回归没有清除或重写 Pixel TZZ 世界状态。

### 4.4 数据包一致性

对以下两个目录按相对路径、文件长度和 SHA-256 逐文件比较：

```text
examples/pixel-tzz-base-datapack
run/server/world/datapacks/Pixel-Tzz-Base-0.1.0
```

结果：

```text
仓库示例文件数: 30
独服副本文件数: 30
逐路径哈希差异: 0
```

无效候选测试结束后，独服副本已经精确恢复，不含临时未知键。

### 4.5 正常停服

服务端正常输出：

```text
Stopping server
Saving worlds
Thread RCON Listener stopped
```

Gradle 服务端进程退出后：

```text
25565: closed
25575: closed
```

## 5. 用户客户端实机验收清单

以下项目尚未验收。测试时应记录 Minecraft 窗口尺寸、GUI 缩放、资源包状态、测试页面、操作结果及异常截图；不能仅凭“客户端成功启动”判定通过。

### 5.0 2026-07-27 客户端问题回归

本轮实机测试集中修复了三个问题：

| 问题 | 根因 | 修复 | 当前证据 |
| --- | --- | --- | --- |
| `tutorial/rules` 的卡片越出内容区 | 横排 Card 使用固有宽度，两个 Card 的总宽度超过可用空间 | 标准/宽视口改为等权宽度和填充高度；紧凑视口改为填充宽度和等权高度 | 修复后的两个 Card 等宽，右侧内边距恢复 |
| 从页面目录打开“组件展示”时两个点击音重叠 | 目录按钮和目标页面 `panel_open` 同时播放 `minecraft:ui.button.click` | 页面目录条目关闭按钮默认点击音，由目标页面独占打开音 | 代码路径只剩页面打开音；最终听感待用户亲耳复验 |
| 打开“玩家列表压力预览”后卡死并崩溃 | Repeat 虚拟滚动占位节点没有 definition，样式解析仍访问其 responsive 字段并触发空指针 | 无 definition 的虚拟节点直接解析为空样式，并增加回归自检 | 32 人页面显示 `Repeat 8 visible`，等待、滚动和返回均正常；没有生成新崩溃报告 |

对应的回归检查已加入页面框架自检，覆盖 Repeat 虚拟占位节点和 Rules 页标准/紧凑布局约束。修复后的最终完整构建结果为：

```text
DEFINITION_REGISTRY_SELF_CHECK=PASS
FOUNDATION_SELF_CHECK=PASS
PAGE_FRAMEWORK_SELF_CHECK=PASS
BUILD SUCCESSFUL
11 actionable tasks: 11 executed
```

真实客户端于 11:53 启动并进入集成服务端测试世界，协议 v5 握手成功。回归结束时，最新崩溃报告仍为修复前的 `crash-2026-07-27_11.40.00-client.txt`，未产生新报告；`latest.log` 也未记录新的页面异常。日志中的 Mojang/Realms 401 来自开发客户端离线账户，不属于本次页面问题。

### 5.1 启动和协议握手

1. 在资源包界面启用 `Pixel TZZ Pro` 示例资源包。
2. 从仓库目录启动开发客户端：

   ```powershell
   cd E:\minecraftserver\fabricmod\pixel-tzz-pro
   .\gradlew.bat runClient
   ```

3. 进入开发测试世界，等待客户端与集成服务端完成握手。
4. 打开 ESC 菜单和“全员逃走中”入口。
5. 确认控制台显示在线、定义健康、`1` 个游戏和 `23` 项定义。
6. 若显示协议不匹配、握手超时或缺少服务端通道，记录完整界面和日志，不能继续判定页面验收通过。

### 5.2 页面与排版

依次打开当前 generation 的五个示例页面：

- `pixel_tzz:tutorial/welcome`：组件、样式和动画综合展示；
- `pixel_tzz:tutorial/rules`：响应式布局；
- `pixel_tzz:tutorial/acknowledge`：FieldInput；
- `pixel_tzz:hunter/briefing`：64 人响应式玩家档案 Repeat 与 Scroll；
- `pixel_tzz:hunter/acknowledge`：可选资源缺失回退。

每个页面至少检查：

1. 标题、正文、按钮、输入框、进度、分隔线和列表没有重叠、截断或越出面板。
2. 页面字体、颜色、间距、圆角和层级符合统一主题。
3. 改变窗口尺寸和 GUI 缩放后，响应式布局能重新排列，焦点与滚动位置不会无故丢失。
4. 长文本和空数据状态仍可阅读、可退出，不出现无法操作的死页。
5. 页面关闭后不继续播放声音、响应输入或更新不可见内容。
6. 玩家档案页在 Compact 为单列，在 Standard/Wide 为双列；顶部摘要和底部操作栏固定，只有中部名册滚动。
7. 每张玩家卡应清楚区分身份色轨、头像、名称、身份、队伍、生存、准备和在线状态，不重复显示同一状态。

### 5.3 鼠标反馈

1. 从页面目录打开“按钮控件”，确认主操作、次操作、导航、危险操作和禁用状态均有独立样例。
2. 将鼠标移入、移出每类按钮，确认 normal、hover、focused 和 disabled 状态视觉明确。
3. 按下并保持鼠标，确认短按压反馈出现；松开后恢复正确状态。
4. 检查导航和返回按钮：本体位置及命中区域保持固定，只有两侧箭头向外展开。
5. 使用滚轮操作长列表，确认滚动连续、不会穿透到错误区域，也不会重置无关控件。
6. 快速重复悬停和点击，确认动画不会卡在中间状态；悬停静音，按下音效只播放一次。

### 5.4 键盘与输入

1. 使用 `Tab` 和 `Shift+Tab` 遍历控件，确认顺序符合视觉阅读顺序。
2. 使用 `Enter` 或 `Space` 激活按钮，结果应与鼠标点击一致。
3. 确认焦点样式清楚，焦点不会进入隐藏或禁用节点。
4. 在 FieldInput 中测试输入、删除、方向键、Home/End、复制粘贴和中文输入。
5. 从输入框切走再返回，确认草稿和值不会无故丢失。
6. 测试 `Esc` 和页面返回按钮；禁止关闭的页面不得被 Esc 绕过，允许退出的预览页必须能安全返回。

### 5.5 Motion 与 Reduced Motion

1. 检查页面 show/hide、按钮 hover、focus、press、字段 value_change，以及模拟 success/error 动画。
2. 确认 opacity、平移、以节点中心进行的缩放、颜色、从左到右的 `clip_progress` 和 Progress 显示动画符合定义。
3. 动画中的节点、子节点、裁剪、调试边界和点击区域必须使用同一变换。
4. 滚动或局部重建页面后，已经完成的 show 动画不得重新播放。
5. 启用 Reduced Motion 后，分别验证 final、fade 和 keep 策略；页面必须直接进入安全可操作状态，不能因跳过动画而不可见或无法点击。
6. 从 ESC 打开控制台，确认根页面轻微上浮并稳定展开；关闭时反向收束后才恢复原版 ESC。
7. 从控制台进入页面目录，再返回控制台：前进应向左退出并从右进入，返回应严格反向。
8. 从目录进入数据包页面再返回，确认目录的滚动位置仍保留，且页面内部 show/hide 不与系统转场重复播放。
9. 转场期间快速点击、按 Enter、Esc 和滚轮，确认不会重复开页、越级返回或触发两次动作。
10. 将原版“屏幕效果强度”调为 `0`，再次走完整链路；位移和缩放应消失，只保留短淡化，页面仍可立即安全操作。

### 5.6 音效

1. 分别触发 `panel_open`、`button_press`、`success` 和 `error`；默认按钮悬停不得播放声音。
2. 每个事件只应播放约定的一次音效，快速操作不能产生明显音效风暴。
3. 缺失可选音效时应静默继续，页面仍可操作。
4. 音效不得延迟按钮权限判断、动作结果或错误反馈。
5. 记录声音类别音量设置，避免把系统静音误判为模组故障。

### 5.7 Reload、缓存与资源异常

1. 打开一个页面后执行 `/reload`。
2. 确认当前活动页面仍使用创建时冻结的 generation，不被中途热替换。
3. 关闭并重新打开或选择“重新加载最新定义”，确认新实例切换到最新健康 generation。
4. 使用无效页面候选重复一次客户端测试：新候选应显示有界诊断，上一代健康定义仍可查询。
5. 恢复候选后再次 `/reload`，确认页面恢复且 generation 只增加一次。
6. 删除可选纹理时应出现占位资源，页面仍可操作。
7. 使用必需资源缺失夹具时应进入模组内置安全错误页，并能重试、查看诊断和退出。
8. 恢复资源并重载后，页面应能重新正常打开。

### 5.8 32/64 人性能

1. 分别以 0、1、32、64 条模拟玩家数据打开 Repeat 页面。
2. 0 人时确认显示专用空状态；1 人时不出现多余占位卡或异常半行。
3. 在 Compact 单列和 Standard/Wide 双列下分别测试 32、64 人，检查首行、末行和滚动中间区域没有重复、缺项、跨列错位或错误空白高度。
4. 每种规模至少执行：首次打开、滚动到底、快速反向滚动、焦点切换、单项状态更新、关闭后重新打开。
5. 记录首次打开耗时、明显卡顿位置、测试前后 FPS、滚动是否连续及输入是否丢失。
6. 64 人页面不应一次实例化不可见列表的全部昂贵内容；滚动期间不应出现可感知的长时间冻结。
7. 单个玩家状态变化不应重建全部列表，也不应重置无关项目的焦点和滚动位置。
8. 连续打开多个页面使缓存接近上限，确认旧缓存按策略回收，活动页面不被错误驱逐。
9. 页面关闭后观察一段时间，确认 FPS 和输入延迟恢复，后台不再持续求值或播放声音。

### 5.9 2B 权威边界

1. 点击所有预览、模拟成功、模拟失败和页面导航按钮。
2. 确认不会真实分配猎人、修改玩家身份、开始初始化、进入准备、开始游戏或改变生存状态。
3. 测试前后检查世界阶段、主持人和核心状态 revision；任何变化都应视为越过 2B 边界。

## 6. 完成条件

2B 只有在以下条件全部满足后才能正式收口：

1. [x] 最终代码重新通过 `clean check build`，并记录完整 PASS 输出。
2. 用户完成客户端协议握手、UI、音效、鼠标、键盘、动画、资源异常和 32/64 人性能实机验收。
3. 实机发现的问题修复后，再按风险范围重新执行自动检查、独服回归和受影响的客户端项目。
4. 用户明确确认客户端体验通过。

在用户完成其余条件前，本里程碑只能表述为“最终自动验证和独服回归已通过，客户端实机验收待完成”，不能表述为 2B 已全部通过。
