# 同一函数内创建并按注册分组暂停，避免人工输入速度影响 group 控制验收。
function pixel_tzz:acceptance_3b/control/start
pixel_tzz_pro message control group acceptance_capture pause
