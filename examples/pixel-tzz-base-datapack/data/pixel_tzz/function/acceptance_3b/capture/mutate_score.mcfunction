# 在 score_now 的第一个字段光标出现前写入新值。
scoreboard players set @a[tag=pixel_tzz_acceptance_3b_capture] pixel_tzz_demo 25
data modify storage pixel_tzz:acceptance_3b state.step set value "score_mutated"
data modify storage pixel_tzz:acceptance_3b state.latest_score set value 25
