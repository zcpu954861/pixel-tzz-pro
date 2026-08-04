# 单函数计时器：由 PlayerB 调用；仅 V3B 播放命令模拟 Player972 发起。
scoreboard players set #external_hud_new pixel_tzz_demo 0
# 玩家手动调用始终视为“重新开始”；服务器自调度 Tick 才沿用当前计时状态。
execute if entity @s[type=minecraft:player] run scoreboard players set #external_hud_new pixel_tzz_demo 1
execute unless data storage pixel_tzz:acceptance_3b external_hud{running:1b} run scoreboard players set #external_hud_new pixel_tzz_demo 1

# Tick 0：准备并播放 V3B 演出。
execute if score #external_hud_new pixel_tzz_demo matches 1 run schedule clear pixel_tzz:acceptance_3b/external_hud/run
execute if score #external_hud_new pixel_tzz_demo matches 1 run title PlayerB clear
execute if score #external_hud_new pixel_tzz_demo matches 1 run title PlayerB actionbar {"text":""}
execute if score #external_hud_new pixel_tzz_demo matches 1 run pixel_tzz_pro message control cue pixel_tzz:acceptance/field_capture cancel
execute if score #external_hud_new pixel_tzz_demo matches 1 run data modify storage pixel_tzz:acceptance_3b call set value {count:3,label:'{"text":"外部 HUD 冲突自动验收，请观察解除后是否补帧或补响","color":"aqua"}'}
execute if score #external_hud_new pixel_tzz_demo matches 1 run data modify storage pixel_tzz:acceptance_3b message set value "外部 HUD 冲突自动验收"
execute if score #external_hud_new pixel_tzz_demo matches 1 run scoreboard players set #external_hud_timer pixel_tzz_demo -1
execute if score #external_hud_new pixel_tzz_demo matches 1 run data modify storage pixel_tzz:acceptance_3b external_hud set value {running:1b}
execute if score #external_hud_new pixel_tzz_demo matches 1 run tellraw PlayerB {"text":"『V3B 验收』已自动启动；1.2 秒后注入外部 HUD，5 秒后自动解除。","color":"aqua"}

scoreboard players add #external_hud_timer pixel_tzz_demo 1

# Tick 1：取消已跨过一个服务端 Tick，再由 Player972 建立新实例，避免撞上重复实例门禁。
execute if score #external_hud_timer pixel_tzz_demo matches 1 as Player972 run pixel_tzz_pro message play pixel_tzz:acceptance/field_capture to PlayerB with storage pixel_tzz:acceptance_3b call

# Tick 24：三条 V3B 独占 HUD 均已开始后，注入原版内容。
execute if score #external_hud_timer pixel_tzz_demo matches 24 run title PlayerB times 0 200 0
execute if score #external_hud_timer pixel_tzz_demo matches 24 run title PlayerB title {"text":"外部 Title","color":"red"}
execute if score #external_hud_timer pixel_tzz_demo matches 24 run title PlayerB subtitle {"text":"外部 Subtitle","color":"gold"}
execute if score #external_hud_timer pixel_tzz_demo matches 24 run title PlayerB actionbar {"text":"外部 ActionBar","color":"yellow"}

# Tick 60：刷新原版 ActionBar，确保冲突覆盖到 V3B 静态尾帧。
execute if score #external_hud_timer pixel_tzz_demo matches 60 run title PlayerB actionbar {"text":"外部 ActionBar","color":"yellow"}

# Tick 100：解除外部 HUD；不再续排本函数。
execute if score #external_hud_timer pixel_tzz_demo matches 100 run title PlayerB clear
execute if score #external_hud_timer pixel_tzz_demo matches 100 run title PlayerB actionbar {"text":""}
execute if score #external_hud_timer pixel_tzz_demo matches 100 run data remove storage pixel_tzz:acceptance_3b external_hud
execute if score #external_hud_timer pixel_tzz_demo matches 100 run scoreboard players reset #external_hud_timer pixel_tzz_demo

execute if data storage pixel_tzz:acceptance_3b external_hud{running:1b} run schedule function pixel_tzz:acceptance_3b/external_hud/run 1t replace
scoreboard players reset #external_hud_new pixel_tzz_demo
