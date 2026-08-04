# 3B-32 player attachment：5、9、13 秒分别跨到末地、下界、主世界。
execute in minecraft:overworld run tp PlayerC 2 80 0
effect give PlayerC minecraft:slow_falling 30 0 true
pixel_tzz_pro message play pixel_tzz:acceptance/policy_matrix to PlayerC with storage pixel_tzz:acceptance_3b call
schedule function pixel_tzz:acceptance_3b/lifecycle/dimension_move_player_c 100t replace
schedule function pixel_tzz:acceptance_3b/lifecycle/dimension_move_player_c_nether 180t replace
schedule function pixel_tzz:acceptance_3b/lifecycle/dimension_move_player_c_overworld 260t replace
