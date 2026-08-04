# 使用长内容留出足够时间，随后可分别调用 pause/resume/complete/cancel。
data modify storage pixel_tzz:acceptance_3b message set value "控制面验收正在播放；暂停时文字与字符音都应冻结，继续后从同一进度恢复"
pixel_tzz_pro message play pixel_tzz:acceptance/field_capture to @s with storage pixel_tzz:acceptance_3b layout_call
