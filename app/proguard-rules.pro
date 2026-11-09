# Proguard rules for NiftySense
-keepattributes *Annotation*
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}
-keep class com.niftysense.app.data.remote.dto.** { *; }
-keep class com.niftysense.app.data.model.** { *; }
