// AGP 9 has Kotlin built in, so there is no separate org.jetbrains.kotlin.android plugin any more.
// Compose + serialization compiler plugins must match the Kotlin version AGP 9.4 ships with (2.4.x).
plugins {
    id("com.android.application") version "9.4.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.10" apply false
}
