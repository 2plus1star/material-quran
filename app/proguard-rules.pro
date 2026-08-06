# R8 keep rules for the release build.
#
# Almost empty by design: every dependency here (kotlinx-serialization,
# WorkManager, media3, DataStore) ships consumer rules covering its own
# reflective surface. The file must still exist — AGP 9 fails the build on a
# named-but-missing proguard file.
#
# Two things to re-verify after a dependency bump, both of which fail silently:
#
#   1. navigation3 serializes NavKeys by class name and reads them back
#      reflectively. Check the keeps survived:
#          grep INSTANCE app/build/outputs/mapping/release/seeds.txt
#
#   2. Bookmarks and LastRead are kotlinx-serialization @Serializable classes
#      persisted as JSON in DataStore. If their serializers are stripped,
#      decoding fails and — see SettingsRepository.toggleBookmark — the app must
#      refuse to write rather than clobber the stored list.

-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
