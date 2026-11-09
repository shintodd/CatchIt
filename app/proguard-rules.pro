# R8 / ProGuard rules for CatchIt.
#
# The app hand-rolls two wire protocols, and those are exactly what shrinking
# breaks: the GTFS-RT protobuf decoder and the Socket.IO AVL client both walk
# byte arrays by hand, so nothing reflective is stripped, but the keep rules
# below stop R8 from removing the small helpers they rely on when it decides
# a class is only reachable from one call site.

# --- Kotlin metadata and coroutines ---
-keepattributes *Annotation*, InnerClasses, Signature, RuntimeVisible*Annotations, AnnotationDefault, EnclosingMethod, MethodParameters

# kotlinx.coroutines: the internals are reached reflectively by the debug agent.
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-dontwarn kotlinx.coroutines.**

# --- org.json is part of the platform, but the shaded copy in some builds is not ---
-dontwarn org.json.**

# --- Firebase ---
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**

# --- WorkManager: workers are constructed reflectively from their class name ---
-keep class * extends androidx.work.ListenableWorker { *; }
-keep class my.feeder.app.data.DepartureAlarmWorker { *; }
-keep class my.feeder.app.data.ServiceAlertsWorker { *; }

# --- Notification receivers are resolved from the manifest by name ---
-keep class my.feeder.app.fcm.NotificationActionReceiver { *; }
-keep class my.feeder.app.fcm.FeederMessagingService { *; }

# --- The app's own data layer ---
# GtfsRealtimeDecoder reads protobuf wire fields by index; keep the constants.
-keep class my.feeder.app.data.GtfsRealtimeDecoder { *; }
-keep class my.feeder.app.data.GtfsRealtimeDecoder$* { *; }
-keep class my.feeder.app.data.PrasaranaKioskClient { *; }
-keep class my.feeder.app.data.GtfsRepository { *; }
-keep class my.feeder.app.data.ServiceAlertsRepository { *; }
-keep class my.feeder.app.data.DepartureAlarmStore { *; }
-keep class my.feeder.app.data.DepartureAlarmEngine { *; }
-keep class my.feeder.app.data.DepartureAlarmNotifier { *; }
-keep class my.feeder.app.data.ServiceAlertsNotifier { *; }

# --- Compose ---
-dontwarn androidx.compose.**
