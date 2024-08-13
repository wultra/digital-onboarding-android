# there's usage of GSON's @SerializedName
-keepattributes *Annotation*

-keepclassmembers,allowobfuscation class * {
  @com.google.gson.annotations.SerializedName <fields>;
}

-keep,allowobfuscation class com.wultra.android.digitalonboarding.networking.model.**