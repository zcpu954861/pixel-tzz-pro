# 仅清理 V3B 验收痕迹；保留共享 objective 本身，避免破坏其他数据包。
function pixel_tzz:acceptance_3b/clear
tellraw @s {"text":"『V3B 验收』临时实例、标签、分数与 Storage 已清理。","color":"gray"}
