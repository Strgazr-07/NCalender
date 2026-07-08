# NCalendar release (R8) keep rules.
# Compose, Room and AndroidX ship their own consumer rules; these cover the
# reflective entry points R8 can't see on its own.

# --- ViewModel: reflective (Application) constructor used by the default factory
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}
-keep class com.ncalendar.app.viewmodel.CalendarViewModel { <init>(...); }

# --- Room entities / converters are instantiated & populated reflectively
-keep class com.ncalendar.app.data.EventEntity { *; }
-keep class com.ncalendar.app.data.Converters { *; }
-keepclassmembers enum com.ncalendar.app.data.** { *; }

# --- App widget & receivers are referenced from the manifest / AlarmManager
-keep class com.ncalendar.app.widget.** { *; }
-keep class com.ncalendar.app.notifications.** { *; }

# --- biweekly (ICS parsing): model classes are referenced reflectively by its
# scribe registry; its optional integrations (jsoup/xml) are not shipped.
-keep class biweekly.** { *; }
-dontwarn biweekly.**
-dontwarn com.github.mangstadt.vinnie.**
-dontwarn org.jsoup.**
-dontwarn javax.xml.**
-dontwarn javax.cache.**

# --- Keep line numbers for readable crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
