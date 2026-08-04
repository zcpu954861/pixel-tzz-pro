# 3B-32 FINISH 排空 player attachment：长提示投影后，2 秒时自动把 C 送入末地。
execute in minecraft:overworld run tp PlayerC 2 80 0
effect give PlayerC minecraft:slow_falling 30 0 true
data modify storage pixel_tzz:acceptance_3b drain_call set value {player_name:"PlayerC",message:"FINISH 排空玩家绑定验证：跨维度后必须继续完整播放，不得取消、补最终帧或重新开始"}
pixel_tzz_pro message play pixel_tzz:lifecycle/player_notice to PlayerC with storage pixel_tzz:acceptance_3b drain_call
schedule function pixel_tzz:acceptance_3b/lifecycle/dimension_move_player_c 40t replace
