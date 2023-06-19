# there's usage of GSON's @SerializedName
-keepattributes *Annotation*

-keepclassmembers,allowobfuscation class * {
  @com.google.gson.annotations.SerializedName <fields>;
}

-keepclassmembers enum com.wultra.android.digitalonboarding.api.*.** { *; }