# 3B-29：专用 snapshot 受众实例面向 C/E，并在创建后同一 Tick 立即暂停。
# 不复用 live_strict policy_matrix；后者会在 E 离线时按 on_loss=cancel 正确撤销 E。
data modify storage pixel_tzz:acceptance_3b restart_finalize_result set value {play:0,pause:0}
tag @a remove acceptance_3b_target
execute if entity PlayerC if entity PlayerE run tag PlayerC add acceptance_3b_target
execute if entity PlayerC if entity PlayerE run tag PlayerE add acceptance_3b_target
execute if entity PlayerC if entity PlayerE store result storage pixel_tzz:acceptance_3b restart_finalize_result.play int 1 run pixel_tzz_pro message play pixel_tzz:acceptance/restart_finalize to @a[tag=acceptance_3b_target]
tag PlayerC remove acceptance_3b_target
tag PlayerE remove acceptance_3b_target
execute if data storage pixel_tzz:acceptance_3b restart_finalize_result{play:2} store result storage pixel_tzz:acceptance_3b restart_finalize_result.pause int 1 run pixel_tzz_pro message control cue pixel_tzz:acceptance/restart_finalize pause
execute if data storage pixel_tzz:acceptance_3b restart_finalize_result{play:2,pause:1} run tellraw Player972 {"text":"『V3B 重启验收』已真实创建 C/E 实例并自动暂停，现在可以让 E 退出后保存世界。","color":"aqua"}
execute unless data storage pixel_tzz:acceptance_3b restart_finalize_result{play:2} run tellraw Player972 {"text":"『V3B 重启验收』创建失败，未获得 C/E 两名目标；请确认 PlayerC 与 PlayerE 均在线。","color":"red"}
execute if data storage pixel_tzz:acceptance_3b restart_finalize_result{play:2} unless data storage pixel_tzz:acceptance_3b restart_finalize_result{pause:1} run tellraw Player972 {"text":"『V3B 重启验收』实例已创建但暂停失败；请勿保存退出。","color":"red"}
