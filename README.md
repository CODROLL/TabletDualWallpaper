# TabletDualWallpaper

一款面向 Android 平板的低功耗图片动态壁纸应用，主打横屏、竖屏两套独立构图。适配华为 MatePad 等经常在两种方向间切换的平板设备。

## 主要功能

- 同一图片分别保存横屏 16:10 与竖屏 10:16 构图
- 桌面与锁屏分别保存两套横竖屏构图
- 支持系统动态锁屏，并为不开放动态锁屏的系统提供静态锁屏回退
- 单指拖动、双指缩放、居中、填满与完整显示
- 全屏构图编辑和实时像素位置显示
- 使用 Storage Access Framework 选图，无需存储权限
- 自动处理 EXIF 方向并采样大图，降低 OOM 风险
- 基于 `WallpaperService` 和 Canvas 的按需单帧渲染，无持续 60 FPS 循环
- DataStore 持久保存图片 URI、构图、背景和视差设置
- 编辑器与壁纸服务共享同一个 `TransformCalculator`

## 兼容范围

- Kotlin、Jetpack Compose、Material 3
- minSdk 26
- targetSdk 35
- Android 平板及提供 Android APK 兼容能力的 HarmonyOS 设备
- 不需要 root、系统签名权限或常驻 ADB 进程

## 构建

需要 JDK 17 与 Android SDK 35：

```powershell
./gradlew.bat testDebugUnitTest assembleDebug
```

构建产物位于 `app/build/outputs/apk/debug/app-debug.apk`。

## HarmonyOS 说明

HarmonyOS 4.2 没有面向普通三方 ArkTS 应用的公开动态壁纸提供者 API。本项目不伪造鸿蒙接口，而通过设备 Android 兼容层调用标准 `android.service.wallpaper.WallpaperService`。不同固件/地区版本可能隐藏动态壁纸入口，需在目标真机验证。
