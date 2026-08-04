# 超长注册内容只压测 V3B 的换行、最大宽度、最大行数和省略，不属于正式玩法。
data modify storage pixel_tzz:acceptance_3b message set value "右侧边缘与底部边缘都不得溢出；窄窗口和高 GUI 缩放下应按注册的 max_width、max_lines 与 ellipsis 收束"
pixel_tzz_pro message play pixel_tzz:acceptance/field_capture to @s with storage pixel_tzz:acceptance_3b layout_call
