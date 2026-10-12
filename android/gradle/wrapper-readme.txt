Android Studio (Rabbit 1 | 2026.2.1) creates the Gradle wrapper automatically when you open the project
(File > Open > select the "android" folder) and will offer Gradle 9.6.0, which AGP 9.4 requires.
To create it manually: `gradle wrapper --gradle-version 9.6.0` (run it twice).
