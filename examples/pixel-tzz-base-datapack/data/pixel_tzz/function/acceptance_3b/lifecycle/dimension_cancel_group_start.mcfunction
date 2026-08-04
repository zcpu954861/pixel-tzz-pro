# 3B-32 world+cancel 多目标：C、E 同一实例，5 秒后只移动 C。
tag PlayerC add acceptance_3b_target
tag PlayerE add acceptance_3b_target
execute in minecraft:overworld run tp PlayerC 2 80 0
execute in minecraft:overworld run tp PlayerE 4 80 0
effect give PlayerC minecraft:slow_falling 30 0 true
pixel_tzz_pro message play pixel_tzz:acceptance/policy_matrix to @a[tag=acceptance_3b_target] with storage pixel_tzz:acceptance_3b call
tag PlayerC remove acceptance_3b_target
tag PlayerE remove acceptance_3b_target
schedule function pixel_tzz:acceptance_3b/lifecycle/dimension_move_player_c 100t replace
