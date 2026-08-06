# V3C 验收准备：只创建可观测 Score/Storage，不启动任务或 Countdown 权威实例。
execute unless data storage pixel_tzz:acceptance_3c setup{scoreboard_ready:1b} run scoreboard objectives add pixel_tzz_3c dummy
data modify storage pixel_tzz:acceptance_3c setup set value {scoreboard_ready:1b,prepared:1b}
data modify storage pixel_tzz:acceptance_3c countdown set value {selected_definition:"pixel_tzz:opening/pause",fault:{command:"return 1"},callbacks:[]}
tellraw @s [{"text":"『V3C 验收』","color":"aqua"},{"text":"倒计时回调审计位已准备；/reload 不会清除现有游戏状态。","color":"white"}]
