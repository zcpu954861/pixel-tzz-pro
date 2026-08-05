# 预期：下一轮 Countdown complete 的 audit_complete 先成功，fault_gate 失败并停在 completing；修复后只重试失败项与尚未运行项。
data modify storage pixel_tzz:acceptance_3c countdown.fault.command set value "return fail"
data modify storage pixel_tzz:acceptance_3c countdown.callbacks set value []
tellraw @s [{"text":"『Countdown 回调故障』","color":"red"},{"text":"故障门已武装；下一轮完成时不得进入热身。随后执行 callback_fault_clear，再从主持人控制台重试。","color":"white"}]
