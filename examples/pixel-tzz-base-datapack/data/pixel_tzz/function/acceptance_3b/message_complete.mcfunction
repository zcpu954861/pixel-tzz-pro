# V3B 验收回调：只记录服务端冻结的有限上下文，不推进正式玩法。
$data modify storage pixel_tzz:acceptance_3b last_callback set value {cue:"$(cue_id)",instance:"$(instance_id)",cycle:$(cycle),node:"$(node_id)",occurrence:$(occurrence),reason:"$(interrupt_reason)"}
$data modify storage pixel_tzz:acceptance_3b last_callback.parameters set value $(parameters)
execute store result storage pixel_tzz:acceptance_3b last_callback.game_time long 1 run time query gametime
