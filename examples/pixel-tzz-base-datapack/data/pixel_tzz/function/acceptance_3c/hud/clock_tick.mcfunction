# 内部计划函数：只更新验收 Score；相同 HUD 树不得整体重播进入动画。
scoreboard players add #clock_step pixel_tzz_3c 1
scoreboard players operation #hud_current pixel_tzz_3c = #clock_step pixel_tzz_3c
execute if score #clock_step pixel_tzz_3c matches ..7 run schedule function pixel_tzz:acceptance_3c/hud/clock_tick 1s replace
