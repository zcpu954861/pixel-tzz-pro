# 未运行 acceptance setup 时默认成功；只有显式 callback_fault 才返回失败。
execute unless data storage pixel_tzz:acceptance_3c countdown.fault.command run return 1
return run function pixel_tzz:acceptance_3c/countdown/callback/fault_command with storage pixel_tzz:acceptance_3c countdown.fault
