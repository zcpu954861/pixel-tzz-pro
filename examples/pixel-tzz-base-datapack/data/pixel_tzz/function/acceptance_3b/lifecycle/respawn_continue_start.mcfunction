# 3B-31 continue：播放给 PlayerB，10 Tick 后由函数自动击杀，避免人工抢时机。
pixel_tzz_pro message play pixel_tzz:acceptance/policy_matrix to PlayerB with storage pixel_tzz:acceptance_3b call
schedule function pixel_tzz:acceptance_3b/lifecycle/kill_player_b 10t replace
