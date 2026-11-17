# Keep rules for the release build (R8). The libraries bring their own (Room, Glance, WorkManager,
# lifecycle, kotlinx-serialization), and the manifest classes (activities, widget receivers) are
# kept by AGP. What is left is what the app itself reaches by name; checked in the trial builds
# of 2026-10-31 and 2026-11-16: nothing, and the build has no missing-class warnings. Keep this
# file small: every line stops R8 from shrinking something. docs/release.md says what to test
# when a rule is added or the shrunk code is changed.

# A crash report from a user is a stack trace. With the line numbers kept and the file name
# replaced by a fixed word, `retrace` (Android SDK cmdline-tools) with the release's mapping.txt
# turns it back into readable code. The mapping.txt of every released APK is kept (docs/release.md).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# WorkManager creates its InputMerger by reflection (Class.newInstance) and R8 drops the
# no-argument constructor otherwise: Glance's workers then fail and widgets stay on the spinner.
-keep class * extends androidx.work.InputMerger { <init>(); }
