# 预期：每秒推进一次夹具进度，进度平滑但不超前；task/game clock 仍读取服务端权威分秒并正确暂停。
schedule clear pixel_tzz:acceptance_3c/hud/clock_tick
scoreboard players set #clock_step pixel_tzz_3c 0
scoreboard players set #hud_current pixel_tzz_3c 0
scoreboard players set #hud_maximum pixel_tzz_3c 8
data modify storage pixel_tzz:acceptance_3c hud.mode set value "clock"
schedule function pixel_tzz:acceptance_3c/hud/clock_tick 1s replace
tellraw @s [{"text":"『HUD 时钟夹具』","color":"aqua"},{"text":"8 秒进度序列已启动；同时观察任务/游戏计时为分秒且暂停后不跳秒。","color":"white"}]
