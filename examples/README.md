# Pixel TZZ Pro 示例内容

本目录中的内容用于开发和验收，不是完整玩法数据包。

- `pixel-tzz-base-datapack`：健康的 2A/2B 基础定义，包含一个游戏、六个页面和一个主题。
- `pixel-tzz-base-resourcepack`：页面样例使用的最小资源包；只提供本项目翻译，纹理和声音优先复用 Minecraft 原版资产。
- `pixel-tzz-required-asset-fixture`：默认不要安装。它依赖基础数据包，用于验证“必需客户端资产缺失”时的内置安全错误页。

正常验收先启用基础数据包和基础资源包。只有执行资源异常测试时，才额外启用必需资产缺失夹具。
