# Update 4.4 (v1.3.4): Echoes, night mode, new icons

- **Echoes tab**: Moods, Diary and Heatmap merged into one tab with a switcher. Notifications for moods, diary and trips still open the right section.
- **Night mode**: `nightFactor()` in `ui/theme/Theme.kt` reads the phone's clock (dusk 19-21h, full night 21-05h, dawn 5-7h) and dims the palette slightly. The Today sky gets a moon and extra stars. Nothing else changes.
- **Icons**: new `ic_tab_*` set (home, echoes, calendar, play, profile). `ic_tab_moods/diary/heatmap` are removed.
- No backend change, no migration, no Gradle sync needed.
