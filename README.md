# EdgeLite Panel

An alternative edge panel for Samsung One UI phones that do not have the built-in Edge Panel feature.

EdgeLite puts a thin handle on the edge of your screen. Swipe it inward (or tap it) and a panel of your favorite apps slides out. Tap an icon to open that app as a floating window or in fullscreen.

- Package: `com.edgelite.panel`
- Version: 1.0.0
- Minimum Android: 8.0 (API 26), built against Android 14 (API 34)
- Languages: English and Indonesian (follows the phone language)
- License: MIT
- Source code: https://github.com/arionacc/EdgeLite-Panel

## Table of contents

1. [Features](#features)
2. [How it works](#how-it-works)
3. [Requirements](#requirements)
4. [Installation](#installation)
5. [First time setup](#first-time-setup)
6. [Using EdgeLite](#using-edgelite)
7. [Settings reference](#settings-reference)
8. [Language support](#language-support)
9. [Updating without losing settings](#updating-without-losing-settings)
10. [Permissions and privacy](#permissions-and-privacy)
11. [Troubleshooting](#troubleshooting)
12. [Known limitations](#known-limitations)
13. [Building from source](#building-from-source)
14. [Project structure](#project-structure)
15. [Disclaimer](#disclaimer)
16. [License](#license)

## Features

**Handle and panel**

- A thin touch strip (24 dp wide) on the left or right edge of the screen with a light pill shaped indicator.
- Swipe inward by more than 16 dp, or simply tap, to open the panel. A short vibration confirms the gesture.
- The panel is a dark card that slides in from the edge with an animation and a dimmed scrim behind it. On Android 12 and above the background is also blurred.
- The panel shows a scrollable list of your chosen apps and a settings button at the bottom.
- The panel has a fixed size. It does not grow longer as you add apps, the list scrolls instead.

**Opening apps**

- Floating window mode: apps open as a resizable window in the center of the screen.
- Fullscreen mode: apps open normally.
- The open mode is chosen globally. The default is Window.
- Window width and height are set separately, as a percentage of the screen, with a live preview that shows the proportions and the size in pixels.

**Per orientation settings**

Portrait and landscape have their own settings for panel side, handle height and position, panel height and width, and floating window size. For example, you can keep the panel on the right in portrait and on the left in landscape.

**Customization**

- Panel height, panel width, icon size, show or hide app names, panel opacity, and corner radius.
- Handle height and handle position from the top.

**System integration**

- Quick Settings tile ("Edge Panel") to turn the panel on or off from the notification shade.
- No notification and no foreground service. EdgeLite runs as an accessibility service, so it does not appear in Samsung's "Check background activity" list.
- Autostart: Android turns the accessibility service back on by itself after a reboot, so the panel returns without opening the app.
- The handle reserves a system gesture exclusion area (Android 10 and above) so it conflicts less with the edge back gesture.

**Backup**

- Export all settings and your panel app list to a JSON file and import it later, without needing any storage permission (it uses the system file picker).

**Other**

- Dark, minimal interface with capsule shaped controls and no extra clutter.
- In app Tutorial with setup steps and fixes for common problems.
- Fully available in English and Indonesian.

## How it works

Android normally only lets a regular app draw over other apps through the "display over other apps" permission, and in practice a foreground service with a visible notification is needed to keep it alive. Samsung's own features avoid this because they are part of the system.

EdgeLite uses an accessibility service as a container for its overlay windows (`TYPE_ACCESSIBILITY_OVERLAY`). This gives a native feeling similar to Samsung's One Hand Operation+:

- no persistent notification
- no foreground service
- no "display over other apps" permission

The service is declared with `canRetrieveWindowContent="false"` and listens to no accessibility events. It only draws the handle and the panel. It does not read your screen and does not collect data.

When you tap an app, EdgeLite launches it before closing the panel (so the visible overlay still allows launching from the background on Android 10 and above). In Window mode it sets launch bounds and requests the freeform windowing mode. Freeform windows must be allowed by the system, see [First time setup](#first-time-setup).

## Requirements

- An Android phone running Android 8.0 or newer. Developed and tested on Samsung One UI.
- To use floating windows: the freeform window options in Developer options (see below). Other phones may work for fullscreen mode, but floating windows depend on the system.
- To build the APK yourself: a GitHub account (the project builds with GitHub Actions) or a local Android toolchain (JDK 17 and Gradle 8.7).

## Installation

EdgeLite is distributed as an APK file. It is not on the Play Store.

**Option 1: Download a release**

1. Open the Releases page of the repository.
2. Download `app-debug.apk` from the latest release.
3. Open the file on your phone and allow installing from your browser or file manager when asked.

**Option 2: Build it with GitHub Actions**

1. Fork or push this project to your own GitHub repository (branch `main`).
2. Open the **Actions** tab. The **Build APK** workflow runs automatically.
3. Download `EdgeLite-debug-apk` from the Artifacts section of the finished run.
4. To publish the APK in Releases, push a tag such as `v1.0.0`. The workflow attaches the APK automatically.

The result is a debug APK, signed automatically with a debug key, so it can be installed right away. A debug key is generated per build, so installing a newer build means uninstalling the old one first. See [Updating without losing settings](#updating-without-losing-settings).

## First time setup

1. **Turn on the accessibility service (required).**
   Open EdgeLite and tap **Enable panel**. Accessibility settings open. Choose **Installed apps**, tap **EdgeLite Panel**, and turn it on. Samsung shows a standard warning for all accessibility services. EdgeLite does not read your screen.

   If the switch is greyed out on Android 13 or newer, open Settings, Apps, EdgeLite Panel, tap the three dots at the top right, and choose **Allow restricted settings**. Then try again.

2. **Choose your apps.**
   Tap **Choose apps**, tick the apps you want in the panel, and tap **Save**.

3. **Ignore battery optimization (optional, recommended).**
   Tap the **Ignore battery optimization** row until its status reads Active. This helps One UI avoid shutting the service down.

4. **Enable freeform windows (for Window mode).**
   Open the three dot menu in EdgeLite and choose **Developer options**. Turn on **Enable freeform windows** and **Force activities to be resizable**. Restart the phone if windows do not change right away.
   If Developer options is not visible, open Settings, About phone, Software information, and tap **Build number** 7 times.

Menu names can differ slightly between One UI versions.

## Using EdgeLite

- **Open the panel:** swipe the handle inward or tap it.
- **Open an app:** tap its icon in the panel. It opens as a floating window or in fullscreen depending on your open mode.
- **Close the panel:** tap outside it on the dimmed area.
- **Open settings from the panel:** tap the settings button at the bottom of the panel.
- **Turn the panel on or off:** use the **Enable panel** and **Disable** buttons in the app, or the Quick Settings tile. Disable only hides the handle, the accessibility service itself stays enabled in Android settings.
- **Fully stop the service:** switch off EdgeLite Panel in Settings, Accessibility, Installed apps.

Tip: add the tile to Quick Settings by editing your Quick Settings buttons and dragging "Edge Panel" into the active area.

## Settings reference

Settings are grouped into collapsible categories. Changes from sliders are applied when you lift your finger and are visible after the panel is reopened.

### General

| Setting | Description | Default |
|---|---|---|
| Default open mode | Window (floating) or Fullscreen | Window |
| Autostart | Keep the panel active after a restart | On |

### Panel appearance

| Setting | Description | Range | Default |
|---|---|---|---|
| App names | Show or hide names below icons | Show | Show |
| Icon size | Size of app icons in the panel | 32 to 64 dp | 48 dp |
| Panel opacity | Opacity of the panel background | 50 to 100 % | 95 % |
| Corner radius | Roundness of the panel corners | 0 to 40 dp | 28 dp |

### Per orientation (Portrait and Landscape)

Choose Portrait or Landscape at the top of the section, then edit that orientation. The orientation shown first follows your current screen orientation.

| Setting | Description | Range | Portrait default | Landscape default |
|---|---|---|---|---|
| Screen side | Left or Right | Left, Right | Right | Right |
| Handle height | Height of the touch handle | 60 to 240 dp | 120 dp | 120 dp |
| Handle position from top | Vertical position of the handle | 10 to 90 % | 40 % | 40 % |
| Panel height | Panel height as a share of the screen | 30 to 95 % | 65 % | 85 % |
| Panel width | Width of the panel | 64 to 140 dp | 88 dp | 88 dp |
| Window width | Floating window width | 30 to 100 % | 75 % | 45 % |
| Window height | Floating window height | 30 to 100 % | 75 % | 80 % |

### Backup

Export and import your settings. See [Updating without losing settings](#updating-without-losing-settings).

## Language support

The app interface is available in **English** and **Indonesian**.

- By default EdgeLite follows your phone's system language. If your phone is set to Indonesian you get Indonesian, any other language falls back to English.
- On Android 13 and newer you can choose a language for EdgeLite only: open Settings, Apps, EdgeLite Panel, Language (called "App language" on some versions), then pick English or Bahasa Indonesia.
- The Quick Settings tile, accessibility service description, tutorial, dialogs, and messages are all translated.
- This README is in English.

Translations live in `app/src/main/res/values/strings.xml` (English, default) and `app/src/main/res/values-in/strings.xml` (Indonesian). To add another language, copy the English file into a new `values-xx` folder, translate the text, and add the language code to `app/src/main/res/xml/locales_config.xml`.

## Updating without losing settings

Debug builds from GitHub Actions are signed with a different key each time. Android will not install an update over an app signed with a different key, so updating means uninstalling the old version first. That removes your settings, so back them up:

1. In EdgeLite, open the **Export and import** category and tap **Export**. Save the JSON file somewhere safe.
2. Uninstall the old version and install the new one.
3. Open the new version, tap **Import**, and choose the saved file.
4. Turn the EdgeLite Panel accessibility service on again (uninstalling turned it off).

Notes:

- The file contains all settings and your panel app list.
- Panel apps that are not installed on the phone are skipped, and EdgeLite tells you how many.
- Import only accepts known settings and clamps numbers to the valid slider ranges, so a damaged or edited file cannot put the app into an invalid state.
- A file that is not an EdgeLite export is rejected.

To avoid this for future updates, sign releases with your own keystore stored in GitHub Secrets.

## Permissions and privacy

| Permission or capability | Why it is needed |
|---|---|
| Accessibility service (`BIND_ACCESSIBILITY_SERVICE`) | Draws the handle and panel as overlay windows. It does not read screen content and listens to no events. |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Lets you ask the system to avoid stopping the service. Optional. |
| App list visibility (`<queries>` for launcher apps) | Needed on Android 11 and above so EdgeLite can list the apps you can pin. |

What EdgeLite does not do:

- It does not read the contents of your screen.
- It does not collect, store, or send personal data. There is no network permission and no analytics.
- It does not request storage permission. Export and import use the system file picker.
- It does not show notifications and does not run a foreground service.

Settings are stored locally in a private `SharedPreferences` file named `edgelite`.

## Troubleshooting

**The accessibility service switch is greyed out and cannot be turned on**

Android 13 and newer restricts apps installed from an APK file. Open Settings, Apps, EdgeLite Panel, tap the three dots at the top right, choose **Allow restricted settings**, and try again. If the menu is missing, tap the service switch once, go back, and check again.

**The Enable panel button does not show the handle**

Check that the EdgeLite Panel service is on in Settings, Accessibility, Installed apps. The panel cannot be displayed without it. Also make sure the label on the main screen reads PANEL (ACTIVE).

**The panel does not start after a restart**

- The EdgeLite Panel service must still be on in Accessibility.
- Autostart in the General category must be on.
- The panel must have been active (label PANEL (ACTIVE)) before the restart. If you turned it off with Disable or the tile, it stays off on purpose.
- The panel appears after the phone has finished booting and has been unlocked.

**The accessibility service turns itself off**

- Do not use **Force stop** in App info. Android turns the service off when you do.
- Turn on **Ignore battery optimization** in EdgeLite.
- On Samsung, open Settings, Battery, Background usage limits. Remove EdgeLite from **Sleeping apps** and add it to **Never sleeping apps**.

**Apps open fullscreen instead of in a window**

- Check that Default open mode (General category) is set to Window.
- Make sure both **Enable freeform windows** and **Force activities to be resizable** are on in Developer options, then restart the phone.
- Some apps do not support resizing and ignore the requested window size. This is a limit of those apps, not a bug in EdgeLite.
- If the system rejects the window request, EdgeLite falls back to opening the app normally.

**A toast says window mode needs freeform enabled**

Freeform windows are not enabled on the system. Turn on the two freeform options in Developer options (see First time setup).

**The handle conflicts with the back gesture**

Move the handle up or down in the Side and handle category, or move it to the other side. You can also reduce the back gesture sensitivity in Samsung navigation settings when available.

**Settings do not change right away**

Sliders apply their value when you lift your finger, and the change shows after the panel is reopened. If it still looks the same, tap Disable and then Enable panel.

**An app does not appear in the panel**

Open **Choose apps** and make sure the app is ticked. Apps that were uninstalled from the phone are skipped automatically.

**The language is wrong**

EdgeLite follows the system language. On Android 13 and newer, set a language for EdgeLite only in Settings, Apps, EdgeLite Panel, Language. On older versions, change the phone language.

**Import says the file is not an EdgeLite export**

Choose a file created by the Export button in EdgeLite (by default `EdgeLite-settings.json`). Files larger than about 200 KB or files from other apps are rejected.

**The build fails on GitHub Actions**

- If you uploaded a new zip over an existing repository, old files are not deleted. Delete the whole `app/` folder in the repository first, then upload the new `app/` folder, otherwise leftover resources can reference strings that no longer exist.
- Hidden folders such as `.github` may be skipped by file managers. If the workflow is missing, create `.github/workflows/build.yml` using the GitHub web editor.
- Open the failed run, download the logs, and search for lines starting with `e: ` or `error:` to find the cause.

## Known limitations

- Window mode relies on hidden Android APIs (reflection) and on the freeform setting of One UI. It may behave differently across One UI versions and can stop working after a system update.
- Apps that declare `resizeableActivity=false` can ignore the requested window size.
- There is no preview of the panel appearance in settings (only the floating window has a preview).
- Panel apps cannot be reordered by drag and there are no folders or shortcuts inside the panel yet.
- The APK is a debug build, so updates require uninstalling first (use Export and import).
- Split screen is intentionally not included because Samsung already provides it.

## Building from source

The project uses Kotlin and programmatic Views (no XML layouts, no Compose).

| Item | Value |
|---|---|
| Language | Kotlin 1.9.24 |
| Android Gradle Plugin | 8.5.2 |
| Gradle | 8.7 |
| JDK | 17 |
| minSdk / targetSdk / compileSdk | 26 / 34 / 34 |
| Dependencies | `androidx.core:core-ktx:1.13.1`, `androidx.appcompat:appcompat:1.7.0`, `com.google.android.material:material:1.12.0` |

The repository does not include the Gradle wrapper. The GitHub workflow installs Gradle 8.7 for you. To build locally with a matching Gradle and JDK 17 installed:

```
gradle assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

The workflow in `.github/workflows/build.yml` runs on pushes to `main`, tags starting with `v`, pull requests, and manual runs. Tags starting with `v` also publish the APK to GitHub Releases.

## Project structure

```
EdgeLite/
  .github/workflows/build.yml      Build APK workflow
  LICENSE
  README.md
  build.gradle.kts
  settings.gradle.kts
  gradle.properties
  app/
    build.gradle.kts
    src/main/AndroidManifest.xml
    src/main/java/com/edgelite/panel/
      MainActivity.kt              Settings screen (all UI built in code)
      EdgeAccessibilityService.kt  Accessibility service: handle and panel overlay
      AppLauncher.kt               Opens apps as a window or fullscreen
      Prefs.kt                     Settings storage, enums, export and import
      WindowPreview.kt             Floating window preview in settings
      EdgeTileService.kt           Quick Settings tile
      AppRepo.kt                   List of launchable apps
      Ui.kt                        Color palette
    src/main/res/
      values/strings.xml           English strings (default)
      values-in/strings.xml        Indonesian strings
      xml/accessibility_service_config.xml
      xml/locales_config.xml       Languages offered in Android per app language
      values/colors.xml, themes.xml
      drawable/, mipmap-anydpi-v26/
```

## Disclaimer

EdgeLite is an independent project and is not affiliated with, endorsed by, or sponsored by Samsung Electronics. Samsung, One UI, and Edge Panel are trademarks of their respective owners, mentioned here only to describe compatibility.

## License

MIT. Created by Arion. See the [LICENSE](LICENSE) file.

Source code: https://github.com/arionacc/EdgeLite-Panel
