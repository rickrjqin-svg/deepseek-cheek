# DeepSeek Balance

一款直接连接 DeepSeek 官方 API 的 Android 余额查看器。

## 特性

- iOS 风格动态玻璃界面与弹簧触控反馈
- 展示可用状态、总余额、充值余额和赠送余额
- 本地记录余额快照，并据此展示最近七次消费趋势
- API Key 使用 Android Keystore 加密，仅保存在设备本地
- 不经过第三方服务器，不收集任何数据

## 使用

安装 APK 后，点击右上角设置图标，输入 DeepSeek API Key。应用请求：

`GET https://api.deepseek.com/user/balance`

## 构建

推送到 `main` 后，GitHub Actions 自动生成 APK。推送 `v*` 标签时自动创建 GitHub Release。
