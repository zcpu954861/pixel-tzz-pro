# 同一函数内创建并暂停，避免人工输入速度影响暂停验收。
function pixel_tzz:acceptance_3b/control/start
pixel_tzz_pro message control cue pixel_tzz:acceptance/field_capture pause
