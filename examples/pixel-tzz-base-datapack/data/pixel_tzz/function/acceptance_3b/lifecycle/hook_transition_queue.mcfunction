# 3B-36 定向回归：模拟相邻 Task 完成/开始 Hook 在同 Tick 投影到同一 Subtitle 队列。
data modify storage pixel_tzz:acceptance_3b hook_end set value {message:"『热身任务』已完成"}
data modify storage pixel_tzz:acceptance_3b hook_start set value {message:"『2D 验收 · 分支任务』已开始"}
pixel_tzz_pro message play pixel_tzz:lifecycle/notice to Player972 with storage pixel_tzz:acceptance_3b hook_end
pixel_tzz_pro message play pixel_tzz:lifecycle/notice to Player972 with storage pixel_tzz:acceptance_3b hook_start
