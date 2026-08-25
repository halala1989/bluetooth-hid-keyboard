# BLE HID Keyboard ProGuard Rules
-keepattributes Signature
-keepattributes *Annotation*

# 保留 BLE 相关类
-keep class android.bluetooth.** { *; }

# 保留 Kotlin 协程
-keep class kotlinx.coroutines.** { *; }
