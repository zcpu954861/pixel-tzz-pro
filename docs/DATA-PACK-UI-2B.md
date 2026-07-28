# Pixel TZZ Pro 数据驱动页面 API（2B 实现基线）

状态：**格式与核心实现已落地，等待用户实机验收**  
页面 `format_version`：`1`  
依赖注册 API：`DATA-PACK-API-2A.md` 的 `api_version = 1`  
网络协议：`5`  
适用模组版本：`0.1.0`  
目标 Minecraft：`26.2`

## 1. 目标与边界

2B 建设一套可由数据包注册、由服务端校验、由客户端安全渲染的通用页面框架。它既服务于开局前的初始化和准备，也要能复用于后续任务、主持人面板及未来的新游戏配置。

2B 包含：

- 页面和主题资源的发现、严格解析、交叉引用校验及不可变 generation 发布；
- 服务端按需下发已验证页面，客户端按 generation 缓存；
- 安全的组件树、布局、样式、绑定、响应式、动画和音效描述；
- 客户端资源包资产预检、缺失诊断和内置安全错误页；
- 管理员页面预览器、模拟数据及布局检查；
- 可运行的自动检查和进游戏验收样例。

2B 不包含：

- 真实执行初始化、准备或猎人指定流程；
- 修改身份、队伍、生存状态、阶段、字段或准备状态；
- 执行 2A 注册的面板操作；
- 创建主持人 BossBar；
- 活动游戏选择；
- 正式任务、地图、倒计时、捕获、复活或运镜；
- 拖拽式页面编辑器。

预览器中的按钮只展示交互反馈和动作请求内容，不修改任何权威状态。真实流程执行和状态提交在后续里程碑接入。

## 2. 权威边界与职责

### 2.1 数据包

数据包负责：

- 页面结构；
- 主题、命名样式和视觉令牌；
- 文本和数据绑定；
- 响应式覆盖；
- 动画及音效引用；
- 按钮所引用的受控动作；
- 资源是否必需及其安全回退。

数据包不能：

- 执行客户端脚本、Java、文件访问或网络访问；
- 直接运行命令或 `mcfunction`；
- 读取任意 NBT、记分板或未公开客户端状态；
- 自行声明页面为强制页面；
- 绕过服务端权限、字段或流程验证。

### 2.2 服务端模组

服务端是页面定义和权威状态的来源，负责：

- 将页面和主题纳入 2A 的严格候选 generation；
- 校验 Schema、引用、复杂度、动作和绑定类型；
- 编译为不可变页面定义；
- 为页面实例生成唯一 ID；
- 按需下发页面及最小绑定快照；
- 校验所有客户端动作请求；
- 管理高风险操作的一次性确认令牌；
- 收集有界的客户端资源能力报告。

### 2.3 客户端模组

客户端负责：

- 缓存并渲染服务端下发的已验证页面；
- 布局、输入、动画、音效、键盘导航和讲述人；
- 检查本地资源包资产；
- 只提交玩家意图和有类型的输入值；
- 在缺少必需资源或渲染失败时进入内置安全错误页；
- 关闭页面后取消不再需要的数据订阅。

客户端不能把本地显示状态当作服务端最终状态。

### 2.4 资源包

资源包只提供表现资产：

- 纹理和图标；
- 字体；
- 语言翻译条目；
- 声音事件及音频文件。

页面 JSON 和主题 JSON 不在资源包中保留第二份副本。

## 3. 资源路径、ID 与覆盖

页面：

```text
data/<namespace>/pixel_tzz_pro/pages/<path>.json
```

主题：

```text
data/<namespace>/pixel_tzz_pro/themes/<path>.json
```

文件路径直接决定资源 ID，JSON 内不重复填写 `id`：

```text
data/pixel_tzz/pixel_tzz_pro/pages/tutorial/intro.json
→ pixel_tzz:tutorial/intro

data/pixel_tzz/pixel_tzz_pro/themes/control_console.json
→ pixel_tzz:control_console
```

所有引用必须显式携带命名空间。最高优先级数据包中的同路径文件完整覆盖低优先级文件，不做深合并。

页面属于一个 2A 游戏定义。主题不绑定游戏，可以被同一候选 generation 中的多个游戏复用。

页面和主题使用与 2A 相同的严格 JSON 规则：

- 拒绝注释、尾随逗号、重复键和未知键；
- 拒绝错误类型、无效枚举、越界数字和无命名空间资源 ID；
- 任一页面或主题失败都会使整份 Pixel TZZ 候选失败；
- 失败时保留上一代不可变快照，同时禁止在错误资源环境中发起新页面实例。

## 4. Generation、下发与缓存

当前页面下发、预览目录和资源报告使用网络协议 v5。

1. 页面和主题与 2A 的其他定义一起编译并原子发布。
2. 页面实例冻结其创建时的 definition generation。
3. `/reload` 不热替换已经打开的流程页面；该实例继续使用冻结定义。
4. 新页面实例使用最新健康 generation。
5. 服务端只在客户端需要页面时下发编译后的页面文档，不登录即广播所有页面。
6. 下发标识至少包含：
   - 页面 ID；
   - definition generation；
   - 页面内容 SHA-256；
   - 页面格式版本；
   - 页面实例 ID；
   - 服务端状态 revision。
7. 客户端以 `generation + page ID + SHA-256` 为缓存键。
8. generation 失效后，未被活动页面实例引用的旧缓存按 LRU 回收。
9. 页面关闭后释放数据订阅，但活动强制流程的恢复信息仍由服务端保存。

客户端不得向服务端上传或替换页面文档。

## 5. 页面根格式

每个页面是单根组件树：

```json
{
  "format_version": 1,
  "game": "pixel_tzz:main",
  "title": {"text": "初始化"},
  "theme": "pixel_tzz:control_console",
  "breakpoints": {
    "compact_max": 479,
    "wide_min": 721
  },
  "root": {
    "type": "column",
    "layout": {
      "width": {"mode": "fill"},
      "height": {"mode": "fill"},
      "gap": 8
    },
    "children": []
  }
}
```

字段：

| 键 | 必填 | 默认值 | 说明 |
|---|---|---|---|
| `format_version` | 是 | 无 | 当前只能为 `1` |
| `game` | 是 | 无 | 必须引用同 generation 中存在的游戏 |
| `title` | 是 | 无 | 非空静态原版文本组件；用于讲述人、诊断和安全回退 |
| `theme` | 否 | `pixel-tzz-pro:default` | 必须引用有效主题 |
| `breakpoints` | 否 | 内置默认值 | 页面响应式阈值 |
| `root` | 是 | 无 | 单个根组件节点 |

页面不能声明 `forced`。是否强制由服务端页面实例的流程上下文决定。

## 6. 节点公共字段

所有节点包含 `type`，并按具体组件严格限制可用字段。公共字段为：

| 键 | 必填 | 说明 |
|---|---|---|
| `type` | 是 | 组件类型 |
| `id` | 否（Button 例外） | 页面内唯一稳定 ID，供绑定、诊断、焦点和动作来源使用；Button 必填 |
| `layout` | 否 | 尺寸、间距、对齐及容器专用布局 |
| `style` | 否 | 当前主题中的命名样式 |
| `style_overrides` | 否 | 受限的节点级样式覆盖 |
| `visible_when` | 否 | 结构化布尔条件 |
| `responsive` | 否 | `compact`、`standard`、`wide` 下的表现覆盖 |
| `events` | 否 | UI 事件对应的命名动画和音效 |

只有容器节点拥有 `children` 或 `child`。叶子节点出现子节点会被拒绝。

`id`：

- 使用小写 `[a-z0-9_.-]+`；
- 页面内不能重复；
- 只有需要稳定引用时才填写，不要求每个纯布局节点都带 ID。
- Button 是明确例外：每个 Button 都必须填写稳定 `id`，否则页面候选编译失败。

节点的可见性改变只影响渲染和焦点；它不能改变服务端对动作或字段的最终判断。

## 7. 尺寸与通用布局

### 7.1 SizeSpec

宽度和高度使用显式模式：

```text
{"mode": "content"}
{"mode": "fixed", "value": 24}
{"mode": "fill"}
{"mode": "percent", "value": 50}
{"mode": "weight", "value": 1}
```

模式：

| 模式 | 语义 |
|---|---|
| `content` | 按内容测量 |
| `fixed` | 固定 Minecraft 缩放后 GUI 单位 |
| `fill` | 占满父容器可用空间 |
| `percent` | 占父容器可用空间的百分比 |
| `weight` | Row、Column 或 Grid 内按权重分配剩余空间 |

规则：

- `fixed` 必须是非负有限整数；
- `percent` 范围为 `0..100`；
- `weight` 必须是正有限数；
- `weight` 只能出现在支持剩余空间分配的容器子项中；
- 根节点默认宽高均为 `fill`；
- 测量使用 Minecraft 缩放后的 GUI 单位，不使用物理屏幕像素。

### 7.2 Layout 公共字段

```json
{
  "width": {"mode": "fill"},
  "height": {"mode": "content"},
  "min_width": 120,
  "max_width": 320,
  "min_height": 24,
  "max_height": 200,
  "margin": {"top": 4, "right": 6, "bottom": 4, "left": 6},
  "padding": {"vertical": 6, "horizontal": 8},
  "align_self": "stretch"
}
```

支持：

- 最小/最大宽高；
- 四边或水平/垂直简写的 margin 与 padding；
- `align_self`：`auto`、`start`、`center`、`end`、`stretch`。

若最小值大于最大值、约束无法满足或测量产生溢出，加载检查或预览器必须给出节点 JSON Pointer。不能静默使用负尺寸。

## 8. 布局容器

### 8.1 Row

```json
{
  "type": "row",
  "layout": {
    "gap": 8,
    "main_align": "space_between",
    "cross_align": "center"
  },
  "children": []
}
```

Row 水平排列子节点。支持：

- `gap`；
- `main_align`：`start`、`center`、`end`、`space_between`；
- `cross_align`：`start`、`center`、`end`、`stretch`；
- 子项 `weight`。

### 8.2 Column

Column 与 Row 字段相同，主轴为垂直方向。

### 8.3 Grid

```json
{
  "type": "grid",
  "layout": {
    "columns": [
      {"mode": "weight", "value": 1},
      {"mode": "weight", "value": 1},
      {"mode": "fixed", "value": 96}
    ],
    "column_gap": 8,
    "row_gap": 6
  },
  "children": []
}
```

Grid 按声明列轨道自动逐行放置。首版不支持任意重叠单元格或复杂 CSS Grid 语法；子项可声明 `column_span`，范围不能超过列数。

### 8.4 Flow

Flow 按顺序排列内容，空间不足时自动换行。支持：

- `direction`：`horizontal` 或 `vertical`；
- `gap`；
- `line_gap`；
- `main_align`；
- `cross_align`。

### 8.5 Overlay

Overlay 的子项占用同一内容区域，并通过锚点放置：

- `top_left`、`top`、`top_right`；
- `left`、`center`、`right`；
- `bottom_left`、`bottom`、`bottom_right`。

只有 Overlay 子项允许 `offset_x`、`offset_y`。偏移必须有界，不能将必需交互控件完全移出可用视口。

### 8.6 Scroll

Scroll 只允许一个 `child`：

```json
{
  "type": "scroll",
  "direction": "vertical",
  "show_scrollbar": true,
  "child": {}
}
```

支持 `vertical`、`horizontal`，首版不支持双轴同时滚动。滚动区域必须裁剪内容，支持鼠标滚轮、拖动滚动条、键盘和焦点自动入视口。

### 8.7 Card

Card 是带语义样式和内边距的单子节点容器。它不拥有独立布局算法，内部子节点仍由 Row、Column、Grid 等表达。

### 8.8 不建设的重复布局

- `split` 由 Row/Column 加 `weight` 表达；
- 垂直堆叠使用 Column；
- 层叠使用 Overlay；
- 2B 不单独实现含义重叠的 Split 或 Stack 引擎。

未来如需简写，只能在编译阶段降低为上述核心布局，不能建设第二套运行时算法。

## 9. 展示与交互组件

### 9.1 Text

```json
{
  "type": "text",
  "text": {"text": "等待主持人"},
  "wrap": true,
  "max_lines": 2,
  "overflow": "ellipsis",
  "text_align": "left"
}
```

`overflow` 支持 `clip`、`ellipsis`。文本可以是静态原版文本组件，也可以使用第 11 节的动态文本模板。

### 9.2 Image

```json
{
  "type": "image",
  "asset": "pixel_tzz:textures/gui/tutorial/map.png",
  "fit": "contain",
  "required": false,
  "fallback": "pixel-tzz-pro:textures/gui/missing_image.png",
  "alt": {"text": "地图示意图"}
}
```

`fit` 支持 `contain`、`cover`、`stretch`。`alt` 对非纯装饰图片必填。

### 9.3 Button

```json
{
  "type": "button",
  "id": "continue",
  "label": {"text": "继续"},
  "style": "primary_button",
  "enabled_when": {"exists": {"bind": "ui.route.value"}},
  "disabled_reason": {"text": "请先选择路线"},
  "action": {
    "type": "flow",
    "name": "continue"
  }
}
```

Button 复用当前已通过实机验收的基础控件：

- 主要操作使用品牌金；
- 普通操作使用信息青；
- 禁用状态低亮且不播放交互动效；
- 导航/返回按钮使用两侧箭头淡入并向外展开的悬停反馈，按钮本体和命中区域不得移动；
- 默认主题的按钮悬停保持静音，只有按下、成功、错误等确定事件播放声音；
- 危险操作使用危险色，但真实提交必须进入服务端二次确认。

每个 Button 必须提供页面内唯一的稳定 `id`，用作焦点恢复、动画状态和动作请求来源；缺失时页面候选编译失败。

禁用按钮必须提供可朗读的 `disabled_reason`。

### 9.4 Divider

Divider 支持 `horizontal`、`vertical`、厚度和命名样式。

### 9.5 Spacer

Spacer 只接受 `layout`，不渲染内容，也不进入讲述人顺序。

### 9.6 Progress

```json
{
  "type": "progress",
  "value": {"bind": "flow.completed"},
  "min": {"literal": 0},
  "max": {"bind": "flow.total"},
  "label": {
    "translate": "pixel_tzz.ui.progress",
    "args": [
      {"bind": "flow.completed"},
      {"bind": "flow.total"}
    ]
  }
}
```

Progress 的显示插值不能改变权威数值；分母为零时显示明确空状态，不执行除零。

### 9.7 PlayerHead

```json
{
  "type": "player_head",
  "uuid": {"bind": "item.uuid"},
  "name": {"bind": "item.name"},
  "online": {"bind": "item.online"},
  "show_status": true
}
```

玩家头像通过 UUID 获取皮肤，名称用于讲述人和失败回退。皮肤不可用时使用原版默认皮肤。

### 9.8 Repeat

```json
{
  "type": "repeat",
  "items": {"bind": "session.players"},
  "item_key": {"bind": "item.uuid"},
  "max_items": 64,
  "estimated_item_height": 48,
  "responsive": {
    "standard": {
      "type": "grid",
      "layout": {
        "columns": [
          {"mode": "weight", "value": 1},
          {"mode": "weight", "value": 1}
        ],
        "column_gap": 6,
        "row_gap": 0
      }
    }
  },
  "template": {
    "type": "card",
    "children": []
  }
}
```

Repeat：

- 对集合中的每项渲染同一模板；
- 使用稳定 `item_key` 保留焦点和动画状态；
- 位于 Scroll 中且超过阈值时必须虚拟化；
- 默认按单列 Column 排列；可在 `responsive` 中把 Repeat 切换为 `column` 或 `grid`，但不能切换为其他容器类型；
- Grid 模式按完整网格行虚拟化，前后占位跨越全部列，避免双列列表滚动时出现列错位；
- 不因单项变化重建整棵页面树；
- 运行时项数超过 `max_items` 时拒绝新增可视项并显示有界诊断。

`estimated_item_height` 表示虚拟化的行步长。单列时通常等于单项高度加垂直间距；Grid 时表示一整行的高度加行间距，而不是单张卡片在一列中的独立高度。同一行内的模板应保持一致高度，首版不支持瀑布流或不等高网格。

Repeat 模板当前不允许包含 FieldInput。需要输入控件的页面必须把 FieldInput 放在 Repeat 之外，后续如开放该组合需先定义稳定输入状态键和焦点恢复语义。

主持人玩家列表、队伍列表、存活者列表和任务名单均由 Repeat 组合，不建设写死的 `player_list`。

## 10. FieldInput

字段输入统一引用 2A 已注册字段：

```json
{
  "type": "field_input",
  "id": "route",
  "field": "pixel_tzz:route_preference",
  "presentation": "choice_cards",
  "show_label": true,
  "show_description": true
}
```

可用表现：

| 字段类型 | 允许的 `presentation` |
|---|---|
| `boolean` | `auto`、`toggle`、`choice_buttons` |
| `integer` | `auto`、`number`、`stepper`、`slider` |
| `string` | `auto`、`text` |
| `identifier` | `auto`、`identifier_text` |
| `single_choice` | `auto`、`choice_cards`、`radio_list`、`dropdown` |
| `multi_choice` | `auto`、`choice_cards`、`checkbox_list` |

FieldInput 不重复声明字段的范围、长度、选项、必填、适用身份/阶段或编辑权限。`presentation` 与字段类型不兼容时，页面候选失败。

输入先写入当前页面实例的 `ui` 临时状态；2B 预览器不向服务端提交。后续执行器接入时，服务端必须按当前字段定义重新验证。

FieldInput 的标签和提示文本可以使用主题字体。底层 EditBox 中玩家已经输入的正文仍使用原版字体，避免原版光标、选区和裁剪逻辑使用不同字宽后错位。

## 11. 文本模板、值绑定与条件

### 11.1 静态文本

静态文本继续使用原版文本组件 JSON。

### 11.2 动态文本模板

绑定值：

```json
{"bind": "viewer.name"}
```

带回退：

```json
{
  "bind": "viewer.team_name",
  "fallback": {"text": "未分队"}
}
```

翻译模板：

```json
{
  "translate": "pixel_tzz.page.progress",
  "fallback": {
    "concat": [
      {"bind": "flow.completed"},
      {"text": " / "},
      {"bind": "flow.total"}
    ]
  },
  "args": [
    {"bind": "flow.completed"},
    {"bind": "flow.total"}
  ]
}
```

拼接：

```json
{
  "concat": [
    {"text": "当前阶段："},
    {"bind": "session.phase_name"}
  ]
}
```

动态文本只接受上述结构，不解析 `${...}`、JavaScript、命令或任意模板语言。

页面和主题引用的翻译键会加入自动资产清单。客户端缺少翻译键时优先使用显式 `fallback`；没有回退的用户可见翻译键视为必需资产，不能直接把原始翻译键展示给玩家。

### 11.3 Binding Context

首版绑定根：

| 根 | 内容 |
|---|---|
| `viewer` | 当前玩家 UUID、名称、在线、管理员资格、主持人、身份、队伍和生存状态 |
| `session` | 游戏、阶段、revision、generation、连接状态及页面明确订阅的集合 |
| `flow` | 当前流程实例、流程 ID、版本、节点、完成数、总数及临时字段 |
| `fields` | 已注册的玩家持久字段值 |
| `item` | Repeat 当前项的受检字段 |
| `ui` | 当前页面实例尚未提交的输入和局部选择 |

固定属性使用点路径，例如 `viewer.name`。命名空间字段使用：

```text
fields/pixel_tzz:route_preference
flow/fields/pixel_tzz:temporary_choice
ui/route/value
```

绑定路径必须在页面编译时能对应到已知类型。页面不能遍历未声明对象或读取任意键。

### 11.4 条件表达式

```json
{
  "all": [
    {
      "eq": [
        {"bind": "viewer.role"},
        {"literal": "pixel_tzz:runner"}
      ]
    },
    {"exists": {"bind": "ui/route/value"}}
  ]
}
```

白名单运算：

- `eq`、`not_eq`；
- `all`、`any`、`not`；
- `exists`；
- `greater`、`greater_or_equal`、`less`、`less_or_equal`。

比较双方必须类型兼容；数值比较只接受数值。表达式不能产生副作用。

## 12. 主题与样式

### 12.1 主题根格式

```json
{
  "format_version": 1,
  "tokens": {
    "colors": {
      "brand": "#F4C95D",
      "info": "#64D8E8",
      "text": "#F2F2F2",
      "secondary_text": "#A7ADB7",
      "success": "#65D68A",
      "warning": "#F6B94A",
      "danger": "#E94F64"
    },
    "spacing": {
      "small": 4,
      "medium": 8,
      "large": 16
    },
    "fonts": {
      "body": {
        "asset": "minecraft:default",
        "required": false,
        "fallback": "minecraft:default"
      }
    }
  },
  "styles": {},
  "motions": {},
  "sound_cues": {}
}
```

### 12.2 解析顺序

```text
模组默认主题
→ 数据包主题令牌
→ 组件默认命名样式
→ 节点命名样式
→ 节点 style_overrides
```

不实现 CSS 选择器、路径匹配、任意继承链或 `!important`。主题引用不存在、令牌循环或类型不匹配会使候选失败。

### 12.3 状态样式

交互组件的命名样式可以定义：

- `normal`；
- `hover`；
- `focused`；
- `pressed`；
- `disabled`；
- `success`、`warning`、`danger`。

颜色不是唯一状态信息；禁用原因、图标或文字仍需传达含义。

## 13. 响应式

响应档位根据页面实际可用 GUI 宽度判断，而不是物理分辨率：

- `compact`：宽度小于等于 `compact_max`；
- `standard`：两个阈值之间；
- `wide`：宽度大于等于 `wide_min`。

默认阈值：

```json
{
  "compact_max": 479,
  "wide_min": 721
}
```

节点示例：

```json
{
  "type": "row",
  "responsive": {
    "compact": {
      "type": "column",
      "layout": {"gap": 6}
    }
  }
}
```

响应覆盖可以修改：

- Row、Column、Grid、Flow 之间的容器类型；
- layout；
- style 和 style_overrides；
- 非关键装饰的可见性。

响应覆盖不能修改：

- 节点 ID；
- 绑定路径；
- 动作类型或动作 ID；
- 字段 ID；
- 权限及服务端条件；
- 子节点集合和顺序。

最多三个固定档位，阈值必须处于安全范围且严格递增。

## 14. 动画、事件与音效

### 14.1 事件

首批 UI 事件：

- `show`、`hide`；
- `hover_enter`、`hover_leave`；
- `focus`、`blur`；
- `press`；
- `value_change`；
- `success`、`error`。

节点只引用主题中存在的命名动画和音效：

```json
{
  "events": {
    "press": {
      "motion": "button_press",
      "sound": "button_press"
    }
  }
}
```

默认控制台按钮的悬停过渡由复用控件处理，不需要页面重复注册位移动画或悬停音。`hover_enter`、`hover_leave` 和 `button_hover` 仍是可选框架能力，仅用于确有必要的特殊任务页面。

### 14.2 动画格式

```json
{
  "duration_ms": 180,
  "delay_ms": 0,
  "easing": "ease_out_cubic",
  "tracks": [
    {
      "property": "opacity",
      "keyframes": [
        {"at": 0.0, "value": 0.0},
        {"at": 1.0, "value": 1.0}
      ]
    },
    {
      "property": "translate_y",
      "keyframes": [
        {"at": 0.0, "value": 6.0},
        {"at": 1.0, "value": 0.0}
      ]
    }
  ],
  "reduced_motion": "final"
}
```

允许属性：

- `opacity`；
- `translate_x`、`translate_y`；
- `scale`；
- `color`；
- `clip_progress`；
- `progress_value`。

`clip_progress` 当前固定从节点左侧向右侧揭示。`scale` 是围绕节点的几何缩放，只影响最终绘制，不触发布局重排、文本换行或字号重新测量。

允许缓动：

- `linear`；
- `ease_in_cubic`；
- `ease_out_cubic`；
- `ease_in_out_cubic`；
- `ease_out_quart`。

关键帧的 `at` 必须从 `0.0` 严格递增到 `1.0`。2B 不允许无界循环动画；不确定 Progress 使用模组内置、受性能限制的效果。

动画只改变表现。服务端状态到达后立即成为逻辑真值，动画不能延迟按钮权限、倒计时或错误显示。

### 14.3 减少动态效果

`reduced_motion`：

- `final`：直接显示最终状态；
- `fade`：只保留短淡入；
- `keep`：仅允许不含位移、缩放和循环的关键必要反馈。

讲述人只朗读最终稳定文本，不朗读动画帧。

### 14.4 系统级页面导航转场

ESC 入口、控制台、页面目录等模组自有 Screen 使用内置的层级导航转场。系统转场与数据包节点的 `show` / `hide` Motion 分工如下：

- `root`：从原版 ESC 进入或退出模组控制台，使用轻微缩放、垂直位移和遮罩；
- `push`：进入下一级，当前 Screen 向左退出，目标 Screen 从右进入；
- `pop`：返回上一级，方向与 `push` 严格相反；
- `replace`：同级替换，仅使用短垂直位移和遮罩，不表达层级变化；
- 数据包页面仍由自身注册的 `show` / `hide` Motion 控制内容，不重复叠加系统进入动画。

转场期间输入必须锁定，视觉结束后才执行 Screen 替换。返回后复用原 Screen 实例，并保留目录滚动等局部状态。系统转场不额外播放声音，按钮和目标页面仍各自拥有既定音效，避免“按钮点击 + 页面打开 + 转场”三重叠加。

系统转场强度读取 Minecraft 原版“屏幕效果强度”：设为 `0` 时取消位移和缩放，只保留 `80 ms` 的短淡入淡出。2B 暂不向数据包开放系统 Screen 的持续时间覆盖；后续业务页面路由可引用 `root`、`push`、`pop`、`replace` 语义，但不能以任意动画改变导航方向或延迟权威操作。

### 14.5 音效格式

```json
{
  "sound": "pixel_tzz:ui.panel_open",
  "delay_ms": 80,
  "volume": 0.7,
  "pitch": 1.0,
  "required": false
}
```

规则：

- 音量范围 `0.0..1.0`；
- 音高范围 `0.5..2.0`；
- 延迟必须非负且有界；
- 高速悬停和状态抖动按声音 ID 与节点限流；
- UI/运镜音量和字符音分别可配置；
- 减少动态效果不自动静音；
- 缺少可选声音时静默跳过并记录一次诊断；
- 缺少声音不能改变动作结果。

音效的具体默认音量、限流窗口和并发混音由模组实现选择最佳值，实机验收只判断节奏、清晰度和是否扰人。

## 15. 动作协议

### 15.1 动作类型

#### Local

只改变页面局部表现：

```json
{
  "type": "local",
  "name": "back"
}
```

允许的本地动作由模组白名单定义，例如返回、页签、展开和收起。Local 不能修改权威状态。

#### Flow

表达继续、提交或选择当前流程节点的意图：

```json
{
  "type": "flow",
  "name": "submit"
}
```

2B 预览器只显示请求内容，不执行。

#### Registered

引用 2A 面板操作：

```json
{
  "type": "registered",
  "action": "pixel_tzz:assign_hunter"
}
```

页面、动作和游戏必须匹配。

当前 2B 预览器对 Local、Flow 和 Registered 三类动作使用同一条只读边界：点击后只截获并显示请求信封，不向服务端发送，也不改变客户端或服务端的权威状态。

### 15.2 请求信封

后续真实动作请求至少携带：

- 页面实例 ID；
- definition generation；
- 页面 ID；
- 来源节点 ID；
- 服务端状态 revision；
- 动作类型及 ID；
- 有类型的提交参数；
- 客户端请求序号。

服务端重新验证：

- 页面实例仍有效；
- generation 和 revision 未过期；
- 操作者、权限、阶段和目标有效；
- 来源节点在权威页面中存在；
- 节点当前可见、可用且绑定相同动作；
- 字段值符合最新冻结定义；
- 请求未重复、未超频。

### 15.3 高风险确认

高风险操作：

1. 首次请求不执行操作；
2. 服务端返回确认页内容和一次性令牌；
3. 令牌绑定操作者、动作、目标、generation、revision 和过期时间；
4. 用户确认后提交令牌；
5. 服务端再次完整验证；
6. 成功或拒绝进入审计。

令牌只能使用一次，不能由数据包生成，也不能在客户端续期。

## 16. 页面生命周期与强制页面

强制级别由服务端页面实例决定：

- `normal`：Esc 返回父页面或关闭；
- `modal`：Esc 返回页面上一层，不丢失输入；
- `forced_flow`：不能绕过流程返回游戏 HUD。

`forced_flow`：

1. Esc、聊天、背包和其他普通界面入口不能替换或关闭当前强制页面；
2. 客户端发现强制页面被其他普通界面替换时，下一客户端 tick 恢复同一页面实例；
3. 退出或掉线不清除服务端流程进度；
4. 重连后恢复当前节点及其服务端冻结页面；
5. 普通成功响应不能解除页面，只有匹配当前流程实例与页面实例的服务端完成或取消信号可以解除；
6. 必需资源或渲染失败时改为模组内置安全错误页，保留重试、查看有界诊断和断开连接入口；
7. 错误恢复不能把玩家标记为完成，也不能偷偷切换到最新数据包定义。

2B 预览器只能模拟这些状态，不创建真实强制流程。

## 17. 资产发现、预检与诊断

### 17.1 自动资产清单

服务端编译页面和主题时自动收集：

- Image 纹理；
- 字体；
- 图标；
- 文本翻译键；
- 声音事件；
- 明确声明的回退资源。

页面作者不另行维护重复清单。

### 17.2 客户端预检

客户端收到页面后，在显示前检查当前资源管理器。报告包含：

- 页面 ID；
- generation；
- 页面 SHA-256；
- 资产类型和 ID；
- 引用节点 JSON Pointer；
- 是否必需；
- 是否已使用回退；
- 标准诊断代码。

报告数量、字符串长度和总包大小有界。

### 17.3 可选资产

- 字体回退 Minecraft 默认字体；
- 皮肤回退原版默认皮肤；
- 图片使用声明的占位图；
- 翻译键使用声明的 fallback；
- 声音跳过；
- 同一缺失资产在同一 generation 中只提示一次。

### 17.4 必需资产

缺少必需资产时：

- 不显示残缺的业务页面；
- 显示模组内置、无需数据包资源的安全错误页；
- 向服务端报告该玩家及缺失资产；
- 主持人可查询具体诊断；
- 不能把流程标记为完成；
- 允许重试资源检查、打开辅助功能或断开连接。

强制流程开始前，服务端应先请求目标参与者预检必需页面。2B 只完成协议、预览及模拟，不启动真实流程。

## 18. 管理员页面预览器

2B 的实际验收入口位于当前管理员控制台，不依赖活动游戏选择或真实流程执行。

预览器必须：

- 列出当前健康 generation 中按游戏分组的页面；
- 选择页面并使用生产编译器和生产渲染器打开；
- 提供普通玩家、管理员、主持人、猎人、逃走者、旁观者等模拟上下文；
- 模拟在线、准备、存活、捕获、流程进度和字段值；
- 切换 Compact、Standard、Wide 视口；
- 切换减少动态效果、高对比度及默认字体回退；
- 显示节点 ID、布局边界、测量尺寸、裁剪区域和溢出；
- 显示绑定值、表达式结果和资源诊断；
- 模拟 0、1、32、64 人集合；
- 截获按钮动作并显示请求信封，不向服务端执行；
- 支持重新加载到最新健康 generation；
- 在错误页面中保持退出和恢复入口。

预览器不能拥有独立的第二套布局或表达式求值器。

## 19. 辅助功能

- 所有交互组件进入确定的键盘焦点顺序；
- Overlay 不得破坏焦点顺序；
- Scroll 自动将键盘焦点滚入视口；
- 图片按语义要求 `alt`；
- 禁用按钮提供 `disabled_reason`；
- 颜色不是唯一信息；
- 高对比度通过主题令牌覆盖实现；
- 讲述人朗读页面标题、稳定文字、组件角色、值和禁用原因；
- 讲述人不朗读逐帧动画；
- 减少动态效果不隐藏最终状态；
- Compact、Standard、Wide 均必须可完成相同操作。

## 20. 安全与性能上限

首版硬上限：

| 项目 | 上限 |
|---|---:|
| 单页面源文件字符数 | `262144` |
| 单主题源文件字符数 | `262144` |
| 页面节点数 | `512` |
| 页面树深度 | `32` |
| 单容器直接子节点 | `128` |
| 页面内节点 ID 长度 | `64` |
| 条件表达式节点数 | `64` |
| 条件表达式深度 | `16` |
| 单主题命名样式 | `128` |
| 单主题动画 | `128` |
| 单动画轨道 | `8` |
| 单轨道关键帧 | `16` |
| 单动画时长 | `2000 ms` |
| 单页面音效引用 | `64` |
| Repeat 单实例项数 | `256` |
| 单次资产诊断明细 | `50` |
| 客户端页面缓存 | `64` 页或 `8 MiB`，先到者为准 |

实现要求：

- 页面加载时预解析并预编译；
- 布局只在视口、内容或绑定依赖变化时重新计算；
- 不在每 Tick 全量求值整棵页面；
- Repeat 长列表必须虚拟化；
- 32 人为常规目标，64 人为压力验收；
- 资产报告和动作请求限频；
- 诊断明细截断时仍保留准确总数；
- 性能降级时优先显示正确最终状态和可操作错误页。

## 21. 实现模块边界

实现时保持以下单一职责，不建设第二套运行时：

| 模块 | 责任 |
|---|---|
| 服务端 UI definition/compiler | 严格解析、类型检查、引用、限制、资产收集、不可变定义 |
| 服务端 page session | 页面实例、generation、revision、订阅和动作信封 |
| 客户端 page cache | 按 generation/hash 缓存及失效 |
| 客户端 binding runtime | 只读取有类型上下文并增量求值 |
| 客户端 layout engine | 测量、布局、裁剪、响应式和溢出诊断 |
| 客户端 widgets | 输入、焦点、讲述人和复用按钮 |
| 客户端 motion/audio | 时间线、减少动态效果、声音限流 |
| 客户端 resource preflight | 资产检查、回退和报告 |
| 客户端 preview | 组合生产模块并提供模拟上下文 |

布局、绑定、动作校验和资源诊断必须各自只有一个生产实现，预览器直接复用。

2B 不引入脚本引擎、浏览器 UI、CSS 解析器或新的第三方依赖。

## 22. 兼容与演进

- `format_version` 只在页面 Schema 出现不兼容变化时增加；
- 新增可选字段不得改变旧文档语义；
- 未知键始终拒绝，不能静默忽略；
- 客户端不支持页面格式或组件能力时使用内置错误页并报告服务端；
- 页面内容变化由 definition generation 和 SHA-256 标识；
- 活动流程页面冻结 generation；
- 未来确需 Split、Stack、循环动画或新组件时，应新增显式能力并补充上限，不能放宽现有节点为任意脚本。

## 23. 2B 交付停止线

完成 2B 时可以：

- 从数据包注册并严格验证页面和主题；
- 在管理员预览器中真实渲染样例页面；
- 检查响应式、长列表、动画、音效、键盘和资源缺失；
- 展示按钮将发送的受控动作请求。

完成 2B 时仍不可以：

- 让按钮真正改变游戏状态；
- 启动或完成真实流程；
- 设置猎人、准备或批准开局；
- 创建正式 BossBar；
- 用预览器模拟结果替代服务端权威状态。

核心实现达到停止线后，仍必须先由用户进游戏验收视觉、声音、键盘操作、资源异常手感及 32/64 人性能。实机验收通过前不能把 2B 标记为最终通过，也不能规划或进入执行层。
