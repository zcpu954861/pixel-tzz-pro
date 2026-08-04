# 供 setup/reset 复用的无提示清理；保留 objective 与 setup.scoreboard_ready。
schedule clear pixel_tzz:acceptance_3b/capture/mutate_score
schedule clear pixel_tzz:acceptance_3b/capture/mutate_storage
schedule clear pixel_tzz:acceptance_3b/capture/cleanup
schedule clear pixel_tzz:acceptance_3b/external_hud/run
title PlayerB clear
title PlayerB actionbar {"text":""}
scoreboard players reset #external_hud_new pixel_tzz_demo
scoreboard players reset #external_hud_timer pixel_tzz_demo
data remove storage pixel_tzz:acceptance_3b external_hud
data remove storage pixel_tzz:acceptance_3b restart_finalize_result
pixel_tzz_pro message control cue pixel_tzz:acceptance/field_capture cancel
pixel_tzz_pro message control cue pixel_tzz:acceptance/policy_matrix cancel
pixel_tzz_pro message control cue pixel_tzz:acceptance/restart_finalize cancel
pixel_tzz_pro message control cue pixel_tzz:acceptance/history_replay cancel
pixel_tzz_pro message control cue pixel_tzz:acceptance/registered_entity cancel
pixel_tzz_pro message control cue pixel_tzz:acceptance/verified_player_speaker cancel
tag @a remove pixel_tzz_acceptance_3b_capture
tag @a remove acceptance_3b_target
scoreboard players reset @a pixel_tzz_demo
data remove storage pixel_tzz:acceptance_3b task
data remove storage pixel_tzz:acceptance_3b call
data remove storage pixel_tzz:acceptance_3b all_call
data remove storage pixel_tzz:acceptance_3b layout_call
data remove storage pixel_tzz:acceptance_3b layout_all_call
data remove storage pixel_tzz:acceptance_3b drain_call
data remove storage pixel_tzz:acceptance_3b capture_call
data remove storage pixel_tzz:acceptance_3b bad_call
data remove storage pixel_tzz:acceptance_3b message
data remove storage pixel_tzz:acceptance_3b state
data remove storage pixel_tzz:acceptance_3b last_callback
data modify storage pixel_tzz:acceptance_3b setup.prepared set value 0b
