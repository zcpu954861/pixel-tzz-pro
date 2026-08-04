# 3B-31 FINISH 排空：长生命周期提示投影后，2 秒时自动击杀 C。
data modify storage pixel_tzz:acceptance_3b drain_call set value {player_name:"PlayerC",message:"FINISH 排空生命周期验证：重生后必须立即取消，不得继续打字、补最终帧或重播字符音"}
pixel_tzz_pro message play pixel_tzz:lifecycle/player_notice to PlayerC with storage pixel_tzz:acceptance_3b drain_call
schedule function pixel_tzz:acceptance_3b/lifecycle/kill_player_c 40t replace
