# GenieX and SQLCipher are reached from native code through JNI: keep them whole.
-keep class com.geniex.sdk.** { *; }
-keep class net.zetetic.database.** { *; }
-dontwarn com.geniex.sdk.**

# Stack traces in crash reports stay readable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
