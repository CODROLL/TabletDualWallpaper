# TabletDualWallpaper：Android 平板横竖屏壁纸工具

一款面向 Android 平板的低功耗横竖屏壁纸工具。支持横屏竖屏自动切换、横竖屏独立构图、桌面锁屏不同图片、MatePad 与 HarmonyOS 兼容模式。应用会读取当前显示设备的物理分辨率，为桌面和锁屏分别保存 landscape / portrait 构图。

适合搜索“横竖屏壁纸”“平板横屏竖屏切换壁纸”“Android tablet orientation wallpaper”“landscape portrait wallpaper”的用户。

## 主要功能

- 自动适配 16:10、4:3、3:2、16:9 和非标准平板比例
- 桌面与锁屏可选择不同图片，并分别保存横竖屏构图
- 单画布固定取景框：直接拖动图片构图，横屏框宽大于高、竖屏框高大于宽
- Apple HIG 参考紫色主题、44dp 最小点击区域和动态浅色/深色外观
- 编辑器左上角高区分度横竖屏选择器，右上角可直接更改图片并撤销
- 数值构图使用偶数像素输入与 ±2/±10 像素微调
- 取景区支持焦点缩放、弹性边界、中心/三分线吸附和双击切换
- 编辑画面与动态壁纸、静态锁屏共享同一渲染器和坐标算法
- 最多 50 步撤销/重做，点击“完成”后才保存正式配置
- 支持原图中心坐标、缩放百分比和 ±1/±10 像素微调
- “应用锁屏”直接生成静态锁屏，规避部分 Android/HarmonyOS 厂商系统的动态锁屏限制
- 首页支持将桌面图片及构图复制到锁屏，或反向复制
- 单指拖动、双指缩放、居中、填满与完整显示
- 全屏构图编辑和实时像素位置显示
- 使用 Storage Access Framework 选图，无需存储权限
- 自动处理 EXIF 方向并采样大图，降低 OOM 风险
- 基于 `WallpaperService` 和 Canvas 的按需单帧渲染，无持续 60 FPS 循环
- DataStore 持久保存图片 URI、构图、背景和视差设置
- 编辑器与壁纸服务共享同一个 `TransformCalculator`
- 旧版单图片和偏移参数会自动迁移到新的中心坐标模型

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

构建产物位于 `app/build/outputs/apk/debug/app-debug.apk`。当前版本为 1.4.1（versionCode 7）。

## 搜索关键词

横竖屏壁纸、平板横竖屏、横屏竖屏自动切换、横竖屏独立壁纸、Android 平板壁纸、MatePad 壁纸、HarmonyOS 动态壁纸、锁屏壁纸、landscape wallpaper、portrait wallpaper、orientation wallpaper、Android tablet wallpaper、dual orientation wallpaper、WallpaperService、Jetpack Compose wallpaper。

## HarmonyOS 说明

HarmonyOS 4.2 没有面向普通三方 ArkTS 应用的公开动态壁纸提供者 API。本项目不伪造鸿蒙接口，而通过设备 Android 兼容层调用标准 `android.service.wallpaper.WallpaperService`。不同固件/地区版本可能隐藏动态壁纸入口，需在目标真机验证。

## dev：锁屏横竖自动切换

华为部分固件不会把第三方 `WallpaperService` 绑定到锁屏，因此 `dev` 分支不再注册实验性动态锁屏组件。应用保留“应用当前方向静态锁屏”，并新增用户主动开启的前台自动切换服务：服务通过 `OrientationEventListener` 直接读取设备方向传感器的 0–359°角度，方向稳定后按横屏或竖屏配置重新生成并设置静态锁屏。斜放和平放会进入死区，不触发重复写入。

测试入口：应用壁纸 → 应用锁屏 → 开启自动跟随旋转。运行期间会显示常驻通知，可在应用或通知中停止。该兼容方案不使用隐藏 API，但旋转后存在重新生成和设置图片的延迟，华为省电策略也可能终止服务；建议允许应用自启动并关闭电池优化。
