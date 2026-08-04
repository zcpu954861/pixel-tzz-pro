# Pixel TZZ Pro 3B 资源压力覆盖包

这是叠加在 `pixel-tzz-base-datapack` 之上的一次性 3B 编译压力夹具，不是正式玩法内容。

覆盖包验证三件事：

- 高优先级的 `pixel_tzz:acceptance` 完整替换基础包同 ID 文字效果，不做字段级合并；
- 高优先级的 `pixel_tzz:acceptance/field_capture` 完整替换基础包同 ID 演出，同时保留
  只允许显式 `call_targets` 的受众隔离契约；
- 高优先级的 `pixel_tzz:acceptance/policy_matrix` 用极简策略完整替换基础包的参数、时间线、
  历史、静态降级与资源声明；
- `pixel_tzz:pressure/max_fields` 在 64 个动态字段硬上限处仍能合法编译。

自动检查只验证资源发现、严格解析、引用、来源、整资源覆盖和 generation 候选快照。虽然
3B 播放与控制命令已经接线，这个覆盖包仍只用于确定性编译压力检查，不用于客户端画面验收。

覆盖包当前共 `6` 个文件，提供 `1` 个文字效果覆盖、`2` 条文字演出覆盖，以及 `1` 条字段
上限演出。它必须始终叠加在基础包之上，不能单独作为正式游戏数据包使用。
