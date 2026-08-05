# 预期：活动任务使用 3/8 分段进度、2 个公开节点；长文组件消失且 gap 同步收起。
scoreboard players set #hud_current pixel_tzz_3c 3
scoreboard players set #hud_maximum pixel_tzz_3c 8
scoreboard players set #hud_events pixel_tzz_3c 2
scoreboard players reset #clock_step pixel_tzz_3c
data remove storage pixel_tzz:acceptance_3c hud.long_text
data modify storage pixel_tzz:acceptance_3c hud.mode set value "default"
tellraw @s [{"text":"『HUD 默认夹具』","color":"aqua"},{"text":"观察右下角单一信息坞：任务标题、正文、3/8 进度、分秒计时与次要字段。","color":"white"}]
