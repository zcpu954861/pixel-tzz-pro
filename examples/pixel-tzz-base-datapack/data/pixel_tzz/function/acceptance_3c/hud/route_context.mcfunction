# 预期：无活动任务时，猎人命中 hunter_waiting context；逃走者回退 default context。
# 本函数不改身份或阶段；请用主持人控制台的正式身份/阶段操作验证。
data modify storage pixel_tzz:acceptance_3c hud.expected_route set value "context"
tellraw @s [{"text":"『HUD 路由夹具』","color":"aqua"},{"text":"请在无活动任务时观察：猎人显示“猎人待命”，逃走者显示默认等待布局。","color":"white"}]
