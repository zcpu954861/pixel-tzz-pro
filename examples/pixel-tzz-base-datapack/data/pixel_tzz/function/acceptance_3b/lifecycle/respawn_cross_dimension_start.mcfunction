# 3B-31：主持人与 C 进入末地，创建 world 绑定实例后自动击杀 C。
execute in minecraft:the_end run tp Player972 0 80 0
execute in minecraft:the_end run tp PlayerC 2 80 0
pixel_tzz_pro message play pixel_tzz:acceptance/policy_matrix to PlayerC with storage pixel_tzz:acceptance_3b call
schedule function pixel_tzz:acceptance_3b/lifecycle/kill_player_c 10t replace
schedule function pixel_tzz:acceptance_3b/lifecycle/return_host 220t replace
