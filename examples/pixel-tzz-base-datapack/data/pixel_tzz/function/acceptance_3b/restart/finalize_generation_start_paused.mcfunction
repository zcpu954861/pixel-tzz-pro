# 3B-29 generation 失效反证：只面向在线 PlayerE 创建专用 finalize 实例，并在同一 Tick 暂停。
data modify storage pixel_tzz:acceptance_3b restart_finalize_result set value {play:0,pause:0}
execute if entity PlayerE store result storage pixel_tzz:acceptance_3b restart_finalize_result.play int 1 run pixel_tzz_pro message play pixel_tzz:acceptance/restart_finalize to PlayerE
execute if data storage pixel_tzz:acceptance_3b restart_finalize_result{play:1} store result storage pixel_tzz:acceptance_3b restart_finalize_result.pause int 1 run pixel_tzz_pro message control cue pixel_tzz:acceptance/restart_finalize pause
execute if data storage pixel_tzz:acceptance_3b restart_finalize_result{play:1,pause:1} run tellraw Player972 {"text":"『V3B 代际验收』已真实创建 PlayerE 实例并自动暂停，现在可以让 E 退出后保存世界。","color":"aqua"}
execute unless data storage pixel_tzz:acceptance_3b restart_finalize_result{play:1} run tellraw Player972 {"text":"『V3B 代际验收』创建失败，没有获得 PlayerE 目标；请确认 PlayerE 在线。","color":"red"}
execute if data storage pixel_tzz:acceptance_3b restart_finalize_result{play:1} unless data storage pixel_tzz:acceptance_3b restart_finalize_result{pause:1} run tellraw Player972 {"text":"『V3B 代际验收』实例已创建但暂停失败；请勿保存退出。","color":"red"}
