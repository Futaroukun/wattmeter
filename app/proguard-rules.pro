-keepattributes *Annotation*
-dontwarn java.lang.invoke.**
-dontwarn javax.annotation.**

-keep class com.antigravity.wattmeter.data.model.** { *; }
-keep class androidx.room.** { *; }
-dontwarn com.android.internal.os.PowerProfile
