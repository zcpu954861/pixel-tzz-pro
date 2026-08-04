# 让捕获夹具可重复执行，不继承上一轮延迟任务、实例、标签或证据位。
schedule clear pixel_tzz:acceptance_3b/capture/mutate_score
schedule clear pixel_tzz:acceptance_3b/capture/mutate_storage
schedule clear pixel_tzz:acceptance_3b/capture/cleanup
pixel_tzz_pro message control cue pixel_tzz:acceptance/field_capture cancel
tag @a remove pixel_tzz_acceptance_3b_capture
scoreboard players reset @a pixel_tzz_demo
data remove storage pixel_tzz:acceptance_3b capture_call
data remove storage pixel_tzz:acceptance_3b message
data remove storage pixel_tzz:acceptance_3b state
data remove storage pixel_tzz:acceptance_3b last_callback
