# 妙极画图

一个自用的 Android 图像生成客户端，调用妙极 MAGE 与开源模型云端接口。

## 功能

- 大尺寸提示词输入区，支持文生图和参考图生图。
- 从相册上传 PNG、JPG、WEBP 参考图，应用内自动压缩。
- 妙极 API 模型、Cloudflare 开源模型、Pollinations 开源模型统一选择。
- 真实摄影、电影感、动漫、商业海报、国风水墨、3D 黏土提示词预设。
- 图片生成完成后只在应用内预览，必须手动点击“保存到手机”。
- “登录 / 充值”直达妙极官网登录/注册页面。
- GitHub Releases 检查更新。

![主界面](docs/screenshot-main.png)

## 开源模型说明

开源不等于免费。它表示模型的代码和权重公开，通常可以下载、修改或自行部署；实际生成仍需要 GPU，云端算力可能消耗免费额度或按量计费。

- `FLUX.1 Schnell`、`Stable Diffusion XL`、`DreamShaper 8`：通过 Cloudflare Workers AI 运行。
- `FLUX Kontext`、`FLUX.2 Klein`、`Qwen Image 2.1 Turbo`：通过 Pollinations 运行。
- 手机只是控制界面，不运行视觉大模型。

## 构建

需要 Android SDK、JDK 17 和 PowerShell。

```powershell
powershell -ExecutionPolicy Bypass -File .\android\build.ps1
```

构建脚本输出签名 APK 到 `android\build\`。

## 安全

- API Key 只保存在 Android 应用私有目录。
- 仓库和 APK 不包含妙极、Cloudflare 或 Pollinations 密钥。
- 支付跳转到妙极官网，应用不收集支付信息。

## 许可

MIT
