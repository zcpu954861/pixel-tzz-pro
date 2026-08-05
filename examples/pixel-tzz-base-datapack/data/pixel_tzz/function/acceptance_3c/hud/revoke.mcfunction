# 预期：通过主持人控制台撤销 PlayerB 的猎人/参与者资格后，专属组件立即原子清除，无 stale、残影或占位暗示。
# 本函数只记录验收意图；权限必须经正式主持人操作改变。
data modify storage pixel_tzz:acceptance_3c hud.expected_transition set value {player:"PlayerB",action:"revoke_then_restore"}
tellraw @s [{"text":"『HUD 撤权夹具』","color":"red"},{"text":"请用主持人控制台撤销再恢复 PlayerB 的猎人/参与者资格；不要用原版 OP 或 tag 代替。","color":"white"}]
