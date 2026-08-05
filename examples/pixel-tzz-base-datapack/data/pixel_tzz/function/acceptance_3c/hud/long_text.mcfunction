# 预期：长中文、Emoji 与长玩家名仍受外框约束；正文最多四行并省略，不出现页面滚动。
data modify storage pixel_tzz:acceptance_3c hud.long_text set value "这是一段用于真实字体测量的超长中文公开正文：请携带验证道具前往北侧区域，与队友确认编号后再返回；Emoji 🧭⚡ 不得拆成乱码方块，PlayerWithAnIntentionallyLongName 也不能挤掉关键任务信息。第二段继续验证稳定换行与底边向上生长。"
data modify storage pixel_tzz:acceptance_3c hud.mode set value "long_text"
tellraw @s [{"text":"『HUD 长文夹具』","color":"aqua"},{"text":"长文已写入受限 Storage；观察换行、省略、底边稳定与无滚动条。","color":"white"}]
