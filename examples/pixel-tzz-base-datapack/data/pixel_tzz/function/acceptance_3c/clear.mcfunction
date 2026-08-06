# 只清除 acceptance_3c 的临时 Score 与 Storage；不会取消活动 Countdown 或重置游戏。
scoreboard objectives remove pixel_tzz_3c
data remove storage pixel_tzz:acceptance_3c setup
data remove storage pixel_tzz:acceptance_3c countdown
tellraw @s [{"text":"『V3C 验收』","color":"aqua"},{"text":"临时 Score 与 Storage 已清理；权威游戏状态未改动。","color":"gray"}]
