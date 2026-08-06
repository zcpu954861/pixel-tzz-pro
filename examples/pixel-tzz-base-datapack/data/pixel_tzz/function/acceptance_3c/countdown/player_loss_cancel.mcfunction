# 下一轮替换片段：games/main.json -> opening_countdown.definition = pixel_tzz:opening/cancel，然后 /reload。
# 预期：约 6 秒时必需玩家掉线正式取消，回到等待批准并保留准备/开局前字段。
data modify storage pixel_tzz:acceptance_3c countdown.selected_definition set value "pixel_tzz:opening/cancel"
tellraw @s [{"text":"『掉线策略 · 取消』","color":"red"},{"text":"下一轮请让 Game 引用 pixel_tzz:opening/cancel，/reload 后重新批准；约 6 秒退出 PlayerC。","color":"white"}]
