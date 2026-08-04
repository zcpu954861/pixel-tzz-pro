# 只暂停 policy_matrix；显式提示主持人现在可以安全保存退出。
pixel_tzz_pro message control cue pixel_tzz:acceptance/policy_matrix pause
tellraw Player972 {"text":"『V3B 重启验收』演出已自动暂停，现在可以保存并退出。","color":"aqua"}
