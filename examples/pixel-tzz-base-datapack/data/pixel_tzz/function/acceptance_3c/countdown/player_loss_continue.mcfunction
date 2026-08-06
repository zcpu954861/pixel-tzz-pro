# 下一轮替换片段：games/main.json -> opening_countdown.definition = pixel_tzz:opening/continue，然后 /reload。
# 预期：约 6 秒时必需玩家掉线，其余玩家继续；冻结参与者名单不删人、不换人。
data modify storage pixel_tzz:acceptance_3c countdown.selected_definition set value "pixel_tzz:opening/continue"
tellraw @s [{"text":"『掉线策略 · 继续』","color":"green"},{"text":"下一轮请让 Game 引用 pixel_tzz:opening/continue，/reload 后重新批准；约 6 秒退出 PlayerC。","color":"white"}]
