# 3B-31 多目标：C/E 共用实例，10 Tick 后只击杀 C。
tag @a remove acceptance_3b_target
tag PlayerC add acceptance_3b_target
tag PlayerE add acceptance_3b_target
pixel_tzz_pro message play pixel_tzz:acceptance/policy_matrix to @a[tag=acceptance_3b_target] with storage pixel_tzz:acceptance_3b call
schedule function pixel_tzz:acceptance_3b/lifecycle/kill_player_c 10t replace
