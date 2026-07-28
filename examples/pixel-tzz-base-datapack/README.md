# Pixel TZZ Pro 2A/2B 示例与 2C 验收夹具

此数据包同时承担两种职责：

- 2A/2B 的定义、主题、布局和组件预览；
- 2C 服务端权威流程的极短验收夹具。

它不是正式的全员逃走中玩法数据包，不包含正式教程、任务、准备、开局或地图内容，也不应复制到未来正式游玩世界中。

## 2B 预览页面

以下六个页面只用于开发预览器，不再被可执行流程引用：

- `pixel_tzz:tutorial/welcome`：组件展示；
- `pixel_tzz:tutorial/buttons`：四种按钮语义与禁用状态展示；
- `pixel_tzz:tutorial/rules`：响应式布局；
- `pixel_tzz:tutorial/acknowledge`：FieldInput；
- `pixel_tzz:hunter/briefing`：Compact 单列、Standard/Wide 双列的 64 人响应式玩家档案 Repeat/Scroll；
- `pixel_tzz:hunter/acknowledge`：可选图片缺失后的原版资产回退。

这些页面需要配合相邻的 `pixel-tzz-base-resourcepack` 进行完整客户端验收。

## 2C 极短流程

两个可执行夹具都只有“说明 → 最终确认 → 完成”三步：

- `pixel_tzz:general_tutorial`（版本 `2`）
  - 页面：`pixel_tzz:fixture/general/briefing` → `pixel_tzz:fixture/general/confirm`
  - 来源操作：`pixel_tzz:start_general_initialization`
  - `completion_policy: if_incomplete`，当前版本已完成者不重复打开；
- `pixel_tzz:hunter_initialization`（版本 `2`）
  - 页面：`pixel_tzz:fixture/hunter/briefing` → `pixel_tzz:fixture/hunter/confirm`
  - 来源操作：`pixel_tzz:assign_hunter` 或 `pixel_tzz:reinitialize_hunter`
  - `completion_policy: always`；前者只处理逃走者并在流程完成后提交猎人身份，后者只让现有猎人重新完成说明。

旁观者无需先完成通用初始化即可由主持人设置。恢复参与时按既有记录分流：

- `pixel_tzz:restore_runner`：已完成当前通用初始化版本，立即恢复为逃走者；
- `pixel_tzz:initialize_runner`：尚未完成当前版本，先运行通用初始化，完成后再原子提交逃走者身份。

确认页的流程动作固定为 `confirm`；说明页使用 `continue`。四个夹具页面不依赖新增图片或字体资产，保持当前 2B 主题、中文主标题和弱化英文辅助信息。

两条流程引用的六个 `mcfunction` 是有意无副作用的成功回调：它们只用于验证 `on_start`、`on_player_complete` 与 `on_all_complete` 的调用和恢复语义，不向聊天栏刷验收文本。

基础数据包当前共包含 `38` 个文件和 `30` 项 Pixel TZZ 定义，其中有 `10` 个页面。`foundationSelfCheck` 会同时检查数量、流程引用、预览/执行边界、身份操作分流、完成策略和两个执行快照。
