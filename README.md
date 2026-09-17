# 刻钟报时 (ChimeClock)

> 安卓后台常驻报时 APP——整点响 3 下、半点响 2 下、一刻/三刻各响 1 下，类似老式挂钟。

## 功能

| 时刻 | 响数 |
|---|---|
| 整点 (xx:00) | 3 下 |
| 半点 (xx:30) | 2 下 |
| 一刻 (xx:15) | 1 下 |
| 三刻 (xx:45) | 1 下 |
| 其他 | 不触发 |

## 技术参数

- **语言**: Kotlin
- **minSdk**: 26 (Android 8.0)
- **targetSdk**: 34 (Android 14)
- **架构**: 单 Activity + ForegroundService + BroadcastReceiver
- **无数据库、无网络、零第三方依赖**

## 构建步骤

> 本机无 Android SDK，以下为 Android Studio 构建步骤：

1. **安装 Android Studio** (Hedgehog 2023.1.1 或更高版本)
2. 打开 Android Studio → `File` → `Open` → 选择本工程根目录 `TB-BS-1_安卓报时APP工程/`
3. 等待 Gradle 同步完成（首次会下载 Gradle 8.5 + Android Gradle Plugin 8.2.0）
4. 连接 Android 手机（开启 USB 调试）或启动模拟器
5. 点击 `Run` 按钮（或 `Build` → `Build APK(s)`）编译
6. Debug APK 输出路径：`app/build/outputs/apk/debug/app-debug.apk`

### 命令行构建（可选）

```bash
# 确保 ANDROID_HOME 环境变量已设置
cd TB-BS-1_安卓报时APP工程
./gradlew assembleDebug
# APK 路径: app/build/outputs/apk/debug/app-debug.apk
```

## 权限说明

| 权限 | 用途 |
|---|---|
| `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` | 精确闹钟（刻钟准时触发） |
| `POST_NOTIFICATIONS` | 前台服务通知（API 33+） |
| `RECEIVE_BOOT_COMPLETED` | 开机自启恢复 |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` | 前台服务常驻 |
| `WAKE_LOCK` | 播放期间保持唤醒 |
| `VIBRATE` | 震动（可选） |

## 首次使用

1. 安装 APP（sideload，不上架应用商店）
2. 打开 APP，点击"请求精确闹钟权限"→ 跳转系统设置开启
3. 点击"请求忽略电池优化"→ 加入白名单
4. 允许自启动（各 ROM 设置路径见下文）
5. 点击"开启报时"按钮

## 各国产 ROM 后台保活设置指引

> 国产 ROM 查杀为最大风险，必须手动设置以下三项：

### 华为 / 荣耀 (EMUI / MagicOS)

1. **自启动**: 设置 → 应用 → 应用启动管理 → 找到"刻钟报时" → 关闭"自动管理" → 手动开启"自启动"+"关联启动"+"后台活动"
2. **电池优化**: 设置 → 电池 → 更多电池设置 → 关闭"休眠时始终保持网络连接" → 返回 → 更多电池设置 → 忽略电池优化 → 找到"刻钟报时" → 允许
3. **后台保护**: 设置 → 电池 → 启动应用管理（或"耗电排行" → "锁屏清理应用" → 关闭"刻钟报时"的清理）
4. **通知权限**: 设置 → 通知 → 找到"刻钟报时" → 开启"允许通知"+"允许常驻通知"

### 小米 (MIUI / HyperOS)

1. **自启动**: 设置 → 应用设置 → 应用管理 → 找到"刻钟报时" → 自启动 → 开启
2. **省电策略**: 设置 → 省电与电池 → 应用智能省电 → 找到"刻钟报时" → 选择"无限制"
3. **锁屏清理**: 设置 → 省电与电池 → 锁屏后清理内存 → 关闭（或添加"刻钟报时"到白名单）
4. **通知权限**: 设置 → 通知与控制中心 → 通知管理 → 找到"刻钟报时" → 开启

### OPPO (ColorOS)

1. **自启动**: 设置 → 应用管理 → 自启动管理 → 找到"刻钟报时" → 开启
2. **电池优化**: 设置 → 电池 → 更多电池设置 → 关闭"智能耗电" → 应用耗电管理 → 找到"刻钟报时" → 选择"允许后台运行"
3. **后台冻结**: 设置 → 电池 → 应用快速冻结 → 找到"刻钟报时" → 关闭"自动冻结"
4. **通知权限**: 设置 → 通知与状态栏 → 通知管理 → 找到"刻钟报时" → 开启

### vivo (OriginOS / FuntouchOS)

1. **自启动**: 设置 → 应用与权限 → 权限管理 → 自启动 → 找到"刻钟报时" → 开启
2. **电池优化**: 设置 → 电池 → 后台耗电管理 → 找到"刻钟报时" → 选择"允许后台高耗电"
3. **后台清理**: i管家 → 空间清理 → 设置 → 关闭"自动清理"（或添加白名单）
4. **通知权限**: 设置 → 状态栏与通知 → 应用通知管理 → 找到"刻钟报时" → 开启

### 通用建议

- **勿清理后台**: 在最近任务列表中锁定"刻钟报时"卡片（下拉锁定/加锁图标）
- **勿强制停止**: 除非主动关闭报时，否则不要在系统设置中"强制停止"
- **Doze 模式**: Android 6+ 的 Doze 模式会影响 `setExactAndAllowWhileIdle` 的精度，已通过精确闹钟权限缓解；但部分国产 ROM 的深度睡眠仍可能延迟，建议同时设置电池优化白名单

## 执行纪律遵守

1. ✅ 闹钟一次性 + 续订：`setExactAndAllowWhileIdle`，禁用 `setRepeating`
2. ✅ 音频只走 USAGE_ALARM 通道：`AudioAttributes.USAGE_ALARM`
3. ✅ 权限兜底降级：精确闹钟被拒时降级为非精确触发，不静默失效
4. ✅ 零依赖：无网络请求、无广告、无隐私采集、无第三方统计 SDK
5. ✅ 每任务自测留痕：见 `自测留痕.md`
6. ✅ AudioFocusChange 打断即止：监听 `AUDIOFOCUS_LOSS`，终止本轮不补响
7. ✅ 国产 ROM 保活指引：见上方各 ROM 设置路径

## 工程结构

```
TB-BS-1_安卓报时APP工程/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradle/wrapper/gradle-wrapper.properties
├── README.md
├── 验收测试指引.md
├── 自测留痕.md
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/example/chimeclock/
        │   ├── MainActivity.kt        # 权限申请/引导页/开关控制/试听入口
        │   ├── ChimeService.kt         # 前台服务/常驻通知/闹钟注册
        │   ├── AlarmReceiver.kt        # 收闹钟广播/算响数/播放/续订
        │   ├── ChimePlayer.kt          # 播放封装(ToneGenerator+USAGE_ALARM)
        │   └── BootReceiver.kt         # 开机自启恢复
        └── res/
            ├── layout/activity_main.xml
            ├── values/strings.xml
            ├── values/themes.xml
            ├── values/colors.xml
            ├── drawable/ic_launcher_foreground.xml
            ├── mipmap-anydpi-v26/ic_launcher.xml
            ├── mipmap-anydpi-v26/ic_launcher_round.xml
            └── xml/backup_rules.xml
```
