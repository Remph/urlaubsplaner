# Keep rules for the release build (R8). The libraries bring their own (Room, Glance, WorkManager,
# lifecycle, kotlinx-serialization), and the manifest classes (activities, widget receivers) are
# kept by AGP. What is left is what a library reaches by name and its own rules miss (below). The
# build has no missing-class warnings (trial builds 2026-10-31 and 2026-11-16). Keep this
# file small: every line stops R8 from shrinking something. docs/release.md says what to test
# when a rule is added or the shrunk code is changed.

# A crash report from a user is a stack trace. With the line numbers kept and the file name
# replaced by a fixed word, `retrace` (Android SDK cmdline-tools) with the release's mapping.txt
# turns it back into readable code. The mapping.txt of every released APK is kept (docs/release.md).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# WorkManager makes its input merger with Class.newInstance(). Its own rule keeps the class but not
# the no-argument constructor, and R8 removes that, so every Glance worker failed with "Could not
# create Input Merger" and both widgets stayed on the loading spinner (found on an emulator,
# 2026-11-17, after an update over 0.11.1).
-keep class * extends androidx.work.InputMerger { <init>(); }
