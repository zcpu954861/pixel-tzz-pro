# 预期：使用 opening/pause 批准后，在约 7 秒正常保存退出；离线时间不扣减，重启进入 recovery_wait，人员协调后原实例继续。
data modify storage pixel_tzz:acceptance_3c countdown.selected_definition set value "pixel_tzz:opening/pause"
data modify storage pixel_tzz:acceptance_3c countdown.expected set value "recovery_wait"
tellraw @s [{"text":"『Countdown 恢复夹具』","color":"aqua"},{"text":"请批准 pause 夹具，在约 7 秒正常保存退出；重启后先主持人、再参与者依次进入。","color":"white"}]
