# 自动错开三种字段取值：10 -> 25 分，初始 -> 最新 Storage。
function pixel_tzz:acceptance_3b/capture/prepare
tag @s add pixel_tzz_acceptance_3b_capture
scoreboard players set @s pixel_tzz_demo 10
data modify storage pixel_tzz:acceptance_3b message set value "初始 Storage 数据（不应闪作最终值）"
data modify storage pixel_tzz:acceptance_3b capture_call set value {count:3,label:'{"text":"字段正在逐项捕获，请观察锁定时机","color":"aqua"}'}
data modify storage pixel_tzz:acceptance_3b state set value {step:"capture_started",initial_score:10}
pixel_tzz_pro message play pixel_tzz:acceptance/field_capture to @s with storage pixel_tzz:acceptance_3b capture_call
schedule function pixel_tzz:acceptance_3b/capture/mutate_score 5t replace
schedule function pixel_tzz:acceptance_3b/capture/mutate_storage 15t replace
schedule function pixel_tzz:acceptance_3b/capture/cleanup 100t replace
