# 下一轮替换片段：games/main.json -> opening_countdown.definition = pixel_tzz:opening/pause，然后 /reload。
# 预期：约 6 秒时必需玩家掉线会冻结剩余时间，原 UUID 回来协调完成后继续；活动旧实例不受 Reload 新定义影响。
data modify storage pixel_tzz:acceptance_3c countdown.selected_definition set value "pixel_tzz:opening/pause"
tellraw @s [{"text":"『掉线策略 · 暂停』","color":"yellow"},{"text":"下一轮请让 Game 引用 pixel_tzz:opening/pause，/reload 后重新批准；约 6 秒退出 PlayerC。","color":"white"}]
