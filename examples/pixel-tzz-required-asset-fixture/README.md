# Pixel TZZ Pro 必需资产缺失夹具

此数据包默认不要安装。它必须与 `pixel-tzz-base-datapack` 一起使用，注册一个引用不存在必需图片的页面：

```text
pixel_tzz_fixture:required_asset_error
```

该页面本身应通过服务端 Schema 和引用校验；打开时，客户端资源预检必须阻止残缺业务页面出现，并改为显示模组内置安全错误页。它不是“无效 definition generation”夹具。
