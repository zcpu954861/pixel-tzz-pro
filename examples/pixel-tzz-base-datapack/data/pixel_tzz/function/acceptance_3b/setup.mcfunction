# V3B 验收的一次性准备；由要观察演出的主持人玩家执行。
execute unless data storage pixel_tzz:acceptance_3b setup{scoreboard_ready:1b} run scoreboard objectives add pixel_tzz_demo dummy
function pixel_tzz:acceptance_3b/clear
data modify storage pixel_tzz:acceptance_3b setup set value {scoreboard_ready:1b,prepared:1b}
data modify storage pixel_tzz:acceptance_3b task set value {id:"pixel_tzz:acceptance/main_branch"}
data modify storage pixel_tzz:acceptance_3b call set value {count:3,label:'{"text":"来自 Storage 的参数","color":"aqua"}'}
data modify storage pixel_tzz:acceptance_3b all_call set value {target:"Player972",count:3,label:'{"text":"来自 Storage 的全体参数","color":"aqua"}'}
data modify storage pixel_tzz:acceptance_3b layout_call set value {count:64,label:'{"text":"这是一段用于窄窗口换行与省略压力验收的超长 Subtitle 标签，最终正文必须留在注册边界内","color":"yellow"}'}
data modify storage pixel_tzz:acceptance_3b layout_all_call set value {target:"Player972",count:64,label:'{"text":"这是一段用于窄窗口换行与省略压力验收的超长 Subtitle 标签，最终正文必须留在注册边界内","color":"yellow"}'}
data modify storage pixel_tzz:acceptance_3b message set value "准备阶段的 Storage 数据"
data modify storage pixel_tzz:acceptance_3b state set value {step:"prepared"}
data remove storage pixel_tzz:acceptance_3b last_callback
scoreboard players set @a pixel_tzz_demo 10
tellraw @s {"text":"『V3B 验收』Storage、Score 与回调观察位已准备。","color":"aqua"}
