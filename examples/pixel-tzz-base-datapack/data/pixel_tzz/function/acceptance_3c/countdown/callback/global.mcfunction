# 无表现副作用的全局回调审计；成功账本项在 Reload/重连/重试后不得重复追加。
execute unless data storage pixel_tzz:acceptance_3c countdown.callbacks run data modify storage pixel_tzz:acceptance_3c countdown.callbacks set value []
$data modify storage pixel_tzz:acceptance_3c countdown.callbacks append value {scope:"global",game_instance_id:"$(game_instance_id)",countdown_id:"$(countdown_id)",countdown_instance_id:"$(countdown_instance_id)",slot:"$(callback_slot)",callback_id:"$(callback_id)",reason:"$(reason)",total_ticks:$(total_ticks),remaining_ticks:$(remaining_ticks)}
