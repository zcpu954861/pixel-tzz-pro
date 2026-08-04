# 3B-32 world+cancel：先在主世界创建实例，5 秒后自动将 PlayerC 送入末地。
execute in minecraft:overworld run tp PlayerC 2 80 0
effect give PlayerC minecraft:slow_falling 30 0 true
pixel_tzz_pro message play pixel_tzz:acceptance/policy_matrix to PlayerC with storage pixel_tzz:acceptance_3b call
schedule function pixel_tzz:acceptance_3b/lifecycle/dimension_move_player_c 100t replace
