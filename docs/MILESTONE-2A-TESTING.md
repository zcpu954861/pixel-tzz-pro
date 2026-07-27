# 第二里程碑 2A 验收

状态：**自动检查、独立服务端复验与用户客户端实机验收全部通过**

验收日期：2026-07-26  
模组版本：`0.1.0`  
Minecraft：`26.2`

## 本次验收边界

本次可以验收：

- 服务端从数据包发现并注册游戏、身份、队伍、生存状态、阶段、字段、流程节点及面板操作；
- 严格 Schema、跨引用、流程图、资源数量、总字符数和 JSON 深度限制生效；
- 当前控制台区分有效、空、定义无效和原版重载失败；
- 有效非空定义成功应用后播放加载 ActionBar；
- Pixel TZZ 定义无效时不播放成功动画；
- 无效候选保留上一代不可变快照，但注册状态变为不健康并禁止发起新流程；
- `/reload` 不修改世界阶段、主持人或核心状态版本。

2A 明确不执行注册的流程或操作，不开放数据包注册的主持人按钮，不修改身份、队伍、生存状态或阶段，也不渲染数据包页面、图标和 BossBar。

## 最终验收结果

- 开发客户端成功进入独立测试快照；
- 握手、加载 ActionBar、临时音效和有效 `/reload` 行为通过；
- 控制台正确显示在线、待机、`1` 个游戏和 `15` 项定义；
- 有效重载发布新 generation，世界阶段及既有状态保持不变；
- 无效候选显示有界诊断并保留上一代快照；
- 修复资源后重新发布下一代有效定义；
- 不可用操作保持不可用，没有越过 2A 边界修改游戏状态；
- 玩家控制台精修布局、阶段轨道、状态卡、自绘按钮及返回按钮悬停反馈通过实机验收；
- 用户于 2026-07-26 确认其余测试全部通过，2A 正式收口。

当前工作树继续保持未提交、未推送状态。

## 示例位置与数量

源文件：

```text
E:\minecraftserver\fabricmod\pixel-tzz-pro\examples\pixel-tzz-base-datapack
```

目标世界：

```text
E:\minecraft\.minecraft\versions\Pixelmc-26.2\saves\全员逃走中-数据包\datapacks\Pixel-Tzz-Base-0.1.0
```

当前仓库示例应为 `23` 个文件，其中 `15` 个 Pixel TZZ 定义：

- `1` 个游戏；
- `3` 个身份；
- `2` 个生存状态；
- `3` 个阶段；
- `2` 个流程；
- `4` 个面板操作。

最终同步后，两棵目录都应为 `23` 个文件，并逐文件 SHA-256 一致。

## 自动检查

```powershell
cd E:\minecraftserver\fabricmod\pixel-tzz-pro
.\gradlew.bat foundationSelfCheck --warning-mode all
.\gradlew.bat clean check build --warning-mode all
```

预期：

```text
DEFINITION_REGISTRY_SELF_CHECK=PASS
FOUNDATION_SELF_CHECK=PASS
BUILD SUCCESSFUL
```

`foundationSelfCheck` 除基础协议与持久化检查外，还应覆盖：

- 示例完整编译为 `1 game(s), 15 total definition(s)`；
- `life_states`、`default_life_state` 及 `life_state` 交叉引用；
- 字段版本、适用身份/阶段、predicate 和迁移策略；
- Audience 的生存状态筛选；
- `change_state` 的 `life_state` 轴；
- 面板动态显示、禁用原因、完整目标筛选和 `assign_life_state`；
- BossBar 模板、颜色、样式、优先级和完成反馈；
- 诊断详情固定保留前 `100` 条，但控制台仍报告准确总数；
- 空流程节点、重复键、未知键、深度和总字符数等错误用例。

本轮自动检查已于 2026-07-26 实际通过：

```text
DEFINITION_REGISTRY_SELF_CHECK=PASS
FOUNDATION_SELF_CHECK=PASS
BUILD SUCCESSFUL
```

## 客户端实机验收步骤

开发客户端固定使用：

```text
E:\minecraftserver\fabricmod\pixel-tzz-pro\run\client
```

它不会读取 HMCL 实例的 `saves`。本工作区已经把目标世界完整复制为独立测试快照：

```text
E:\minecraftserver\fabricmod\pixel-tzz-pro\run\client\saves\全员逃走中-数据包
```

测试快照与 HMCL 原世界互不自动同步，开发客户端中的测试修改不会写回原世界。若复制时世界选择界面已经打开，需要返回主菜单后重新进入“单人游戏”，或重启开发客户端，列表才会刷新。

1. 启动开发客户端：

   ```powershell
   cd E:\minecraftserver\fabricmod\pixel-tzz-pro
   .\gradlew.bat runClient
   ```

2. 进入开发测试快照“全员逃走中-数据包”。

3. 等待握手完成，观察一次 ActionBar 加载动画。

4. 打开 ESC 后进入“全员逃走中”：

   - 连接状态为在线；
   - 世界存档桥阶段仍为待机；
   - 摘要显示 `1` 个游戏、`15` 项定义；
   - 操作按钮仍不可用，这是 2A 的预期边界。

5. 执行：

   ```mcfunction
   /reload
   ```

   - 只播放一次加载成功动画；
   - definition generation 增加；
   - 摘要仍显示 `1` 个游戏、`15` 项定义；
   - 世界阶段仍为待机；
   - 主持人和其他游戏状态没有被创建、修改或清除。

## 可选的错误界面验收

只修改目标世界中的安装副本，不修改仓库源文件。在：

```text
...\Pixel-Tzz-Base-0.1.0\data\pixel_tzz\pixel_tzz_pro\roles\runner.json
```

加入一个未知键：

```json
"intentional_invalid_test": true
```

执行 `/reload` 后应看到：

- 不播放加载成功动画；
- 控制台摘要变为红色定义错误；
- 诊断包含资源路径、`/intentional_invalid_test` 和 `UNKNOWN_KEY`；
- 日志说明上一代 generation 保留；
- 操作按钮保持不可用。

删除测试键并再次执行 `/reload` 后，应恢复 `ready` 并发布下一代定义。

## 独立服务端复验证据

2026-07-26 已在真实独立服务端完成：

1. 服务端启动至 `Done`，注册 `generation 1: 1 game(s), 15 total definition(s)`；
2. 有效 `/reload` 发布 `generation 2`；
3. 在独服安装副本故意加入未知键后，日志输出 `UNKNOWN_KEY`，并明确保留 `generation 2`；
4. 恢复原文件并逐文件校验后，有效 `/reload` 发布 `generation 3`；
5. 启动、有效重载、无效重载、恢复重载、`save-all flush` 和正常停服后，世界状态文件 SHA-256 始终为：

   ```text
   4D3685AB647C663C64BABF7CB28CAD6EC15AE23BFB4A823F9466399C14782060
   ```

6. 服务端正常写出 `Stopping server`、`Saving worlds` 和 `Thread RCON Listener stopped`；
7. 独服 Java/Gradle wrapper 已退出，`25565`、`25575` 监听均已关闭；
8. 仓库示例、独服副本和目标世界副本均为 `23` 个文件，逐路径 SHA-256 差异为 `0`。

这份证据不代替客户端中的动画、音效和控制台显示验收。
