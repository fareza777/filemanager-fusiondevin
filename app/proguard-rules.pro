# Sorta release rules — Room, Coil, ads, billing.

# Room
-keep class app.sorta.files.data.db.** { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase { <init>(); }

# Coil fetchers/decoders are looked up reflectively
-keep class app.sorta.files.core.fs.**Fetcher$Factory { *; }
-keep class coil.** { *; }

# Kotlin metadata for reflection-based libs
-keep class kotlin.Metadata { *; }

# Play Billing
-keep class com.android.billingclient.** { *; }

# Ads SDK
-keep class com.google.android.gms.ads.** { *; }

# DataStore preferences serializer internals
-keepclassmembers class * implements java.io.Serializable { *; }

-dontwarn org.bouncycastle.**
-dontwarn com.google.errorprone.**
