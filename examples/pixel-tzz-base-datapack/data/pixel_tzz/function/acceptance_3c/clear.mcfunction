# 只清除 acceptance_3c 的临时 Score、Storage 与计划函数；不会取消活动 Countdown 或重置游戏。
schedule clear pixel_tzz:acceptance_3c/hud/clock_tick
scoreboard objectives remove pixel_tzz_3c
data remove storage pixel_tzz:acceptance_3c
tellraw @s [{"text":"『V3C 验收』","color":"aqua"},{"text":"临时 Score、Storage 与计划函数已清理；权威游戏状态未改动。","color":"gray"}]
