# 在 storage_note 自己的字段光标出现前写入最新 Data 值。
data modify storage pixel_tzz:acceptance_3b message set value "最新 Storage 数据（per_field 锁定）"
data modify storage pixel_tzz:acceptance_3b state.step set value "storage_mutated"
