# 解除验收故障，不直接重试或推进 Countdown；重试必须由正式主持人恢复入口执行。
data modify storage pixel_tzz:acceptance_3c countdown.fault.command set value "return 1"
tellraw @s [{"text":"『Countdown 回调故障』","color":"green"},{"text":"故障门已解除；请从主持人控制台重试失败回调。","color":"white"}]
