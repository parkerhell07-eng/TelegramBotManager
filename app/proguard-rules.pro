# Add project specific ProGuard rules here.
# Gson generic types are used with data classes that are fully named,
# so default rules are sufficient for this app.
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.telegrambot.manager.data.model.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
