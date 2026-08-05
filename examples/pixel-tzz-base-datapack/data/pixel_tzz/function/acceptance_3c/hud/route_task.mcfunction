# 预期：存在 starting/running/paused/settling/interval 任务时，task tier 唯一覆盖 context/default。
# 本函数不伪造任务；请通过主持人批准或既有 2D 权威任务入口进入活动任务。
data modify storage pixel_tzz:acceptance_3c hud.expected_route set value "task"
tellraw @s [{"text":"『HUD 路由夹具』","color":"aqua"},{"text":"请通过主持人批准进入活动任务；预期只出现一个任务根布局，不能与等待布局拼接。","color":"white"}]
