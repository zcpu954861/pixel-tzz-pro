# 预期：Game 当前引用 opening/pause；完成准备后由主持人二次确认批准，才创建 10 秒权威 Countdown。
# 本函数绝不直接发送 HUD 包、跳过准备或绕过批准。
data modify storage pixel_tzz:acceptance_3c countdown.selected_definition set value "pixel_tzz:opening/pause"
data modify storage pixel_tzz:acceptance_3c countdown.expected set value "host_approval"
tellraw @s [{"text":"『Countdown 夹具』","color":"aqua"},{"text":"默认资源为“正式开局倒计时 · 掉线暂停”；请完成准备并从主持人控制台批准开局。","color":"white"}]
