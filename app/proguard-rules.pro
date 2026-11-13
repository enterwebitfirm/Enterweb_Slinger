# === FILE: app/proguard-rules.pro ===
# ProGuard & R8 Optimization Rules for Enterweb Slinger Production Release

# Preserve line numbers and source files for crash debugging
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep Room SQLite Entities, DAOs, and Database
-keep class androidx.room.RoomDatabase
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep class * extends androidx.room.migration.Migration
-dontwarn androidx.room.paging.**

# Keep Moshi JSON Serializers & Models
-keep class com.squareup.moshi.** { *; }
-keepattributes *JavascriptInterface*
-keepclassmembers class * {
    @com.squareup.moshi.Json *;
    @com.squareup.moshi.JsonClass *;
}
-dontwarn com.squareup.moshi.**

# Keep Kotlinx Serialization
-keepattributes *Annotation*,InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
    @kotlinx.serialization.Serializable <fields>;
}

# Keep OkHttp & Retrofit Networking
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# Keep SSHJ & BouncyCastle (SFTP / SSH Automation)
-keep class net.schmizz.sshj.** { *; }
-keep class com.hierynomus.sshj.** { *; }
-keep class org.bouncycastle.** { *; }
-dontwarn net.schmizz.sshj.**
-dontwarn com.hierynomus.sshj.**
-dontwarn org.bouncycastle.**

# Keep Apache Commons Net (FTP / FTPS / Telnet / TFTP)
-keep class org.apache.commons.net.** { *; }
-dontwarn org.apache.commons.net.**

# Keep SNMP4J & ASN.1 / BER Wire Protocols
-keep class org.snmp4j.** { *; }
-dontwarn org.snmp4j.**

# Keep Firebase AI Logic & App Check
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# Keep Coroutines Internal Names
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# Keep ViewModels and Service Locator
-keep class com.enterwebslinger.network.core.di.AppContainer { *; }
-keep class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# Keep Entities and Domain Models
-keep class com.enterwebslinger.network.data.local.db.entities.** { *; }
-keep class com.enterwebslinger.network.domain.model.** { *; }
