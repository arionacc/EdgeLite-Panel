# Changelog

All notable changes to EdgeLite Panel are listed here, newest first.

Versions 1.0.4 and 1.2.6 were never published. Their changes are included in 1.0.5 and 1.2.7.

## 1.3.1

### Added
- App icons now appear one after another when the panel opens, each fading in while sliding a short distance from the panel edge.
- The same effect plays when you swipe to another page: the items of the incoming page appear one by one from top to bottom. It starts when you lift your finger and runs at the same pace whether you swipe fast or slowly.
- The app list and the shortcut list now have the system overscroll effect when you scroll past the end: a stretch on Android 12 and newer, a glow on older versions. Short lists that fit without scrolling stay still.

### Changed
- The page dots now morph: the active dot stretches into a small capsule while the dot you leave shrinks back into a circle. The change follows your finger while you swipe.
- The page dots are slightly larger than before.
- The panel preview in settings shows the new dots, with one dot per page.

## 1.3.0

### Changed
- Page swipes are now softer and slower: the page glides into place in about half a second without bouncing, and bounces slightly when you pull past the first or last page.
- Page contents now trail slightly behind the page while it moves and appear from behind its edge, which gives the swipe more depth.
- Taps are only ignored while the page is still clearly moving. Once it is almost in place, taps and a new swipe work again.

## 1.2.9

### Changed
- The Volume and Brightness sliders are now a single wide bar instead of two thin bars side by side. Use the switch to change between Volume and Brightness.
- Changing mode now has a transition: the old bar slides out and fades while the new one slides in, and the percentage and name fade with it.
- Swiping between panel pages is softer and smoother, and pages are cached while they move so fading stays fluid even with many icons.
- The inactive bar is now hidden instead of dimmed.

## 1.2.8

### Changed
- Swiping between panel pages now feels like a launcher home screen: the page follows your finger without jumping, pulls back like rubber at the first and last page, and glides into place with a spring that continues the speed of your swipe.
- The page animation no longer depends on the system animation scale.

### Fixed
- The page slide animation could start from the wrong side, which made it look like there was no animation at all.

## 1.2.7

### Added
- The slider page now shows the value of the selected slider as a percentage, with its name (Volume or Brightness) below it.
- Page changes now fade and shrink the page slightly while it slides.

### Changed
- The panel no longer shrinks on the slider page. The sliders stretch to the full panel height, so the panel keeps the same size on every page.
- Only the slider chosen with the Volume/Brightness switch is active. The other one is dimmed and ignores touches until you switch to it. Touching a slider no longer selects it.
- The name labels under each slider were replaced by the shared percentage and name shown below both.
- The page slide animation is slower.

### Fixed
- Swiping across the sliders can no longer change the inactive slider by accident, for example brightness while you are on Volume.

## 1.2.0 to 1.2.5

These versions were released one by one. They are summarized together here.

### Added
- Volume and Brightness now have their own page in the panel. Swipe sideways to reach it, then drag the vertical sliders. Arrow buttons (which can be held) and a switch select which slider you control.
- Flashlight, using the rear camera without any camera permission.
- Auto-rotate toggle and a Vibrate mode toggle that switches between normal and vibrate.
- Media control, sent as media buttons.
- Screen record opens the recorder app you choose, with a new picker in settings. If none fits, the system quick settings panel opens instead.
- The "Modify system settings" permission, used for brightness and auto-rotate. It is only requested when you actually use the slider.
- Page dots follow the number of pages.

### Changed
- Volume and Brightness sliders moved out of the quick access list into a separate third page, drawn as tall vertical bars side by side.
- On the slider page the panel shrinks to fit the sliders instead of stretching to the full panel height. It returns to the normal height on the other pages.
- The page dots moved to the bottom of the panel.
- APKs are now signed with a fixed key, so a new version can be installed over the old one without losing your settings.

### Fixed
- Swiping between pages no longer changes volume or brightness by accident. A slider now only reacts when you drag along it, and sideways swipes just change the page. A short tap on a slider still sets the value.
- Taps made while the page is still sliding are now ignored, so they no longer hit icons or sliders by mistake.
- Sideways swipes that are slightly diagonal are now recognized more easily.

## 1.1.1

### Changed
- Quick access moved from a strip above the app list to its own second page. Swipe sideways to reach it. The panel size does not change.
- Quick access items use the same icon size and label style as apps.

### Added
- Page dots in the panel and in the preview.

## 1.1.0

### Added
- Quick access: system action buttons at the top of the panel (Notifications, Quick settings, Recents, Home, Back, Screenshot, Lock screen, Power menu). They use accessibility global actions and need no new permission.
- A new "Quick access" category in settings to choose the buttons. They also appear in the panel preview and in settings export and import.

## 1.0.7

### Changed
- Packaging changes in preparation for F-Droid. No change in app behavior.

## 1.0.6

### Changed
- Auto window shape for apps that lock their orientation now follows a direction rule: a box in the same direction is used as is, and a box in the other direction becomes a square the size of the shorter side.
- Orientation detection now covers all activities of an app.

## 1.0.5

This release also includes everything from the unpublished 1.0.4.

### Added
- Window size is now adjusted per app and per screen. A new "App fit" setting offers Auto or Exact, and percentages are calculated from the usable screen area.
- The window preview uses the same calculation, with a dp readout and a dashed outline for portrait-only apps.
- Per-app window shape: tap an app icon in settings to choose Auto, Portrait only, Landscape only, or Exact size. This helps apps that lock their orientation in code, such as TikTok Lite.

### Changed
- The note for the App fit setting is clearer.

## 1.0.3

### Changed
- The license changed from MIT to GPL-3.0-only. Version 1.0.2 and earlier remain under MIT; 1.0.3 and later are GPL-3.0-only.
- The About dialog has a "View license" button.

### Added
- A compatibility warning for devices other than One UI.

## 1.0.2

### Added
- Panel preview in settings.

### Changed
- More spacing between settings, so rows and sections are easier to read.

## 1.0.1

### Fixed
- The handle and panel are now hidden while the screen is locked.
- The About dialog now shows the version of the installed app.

## 1.0.0

First release.

### Added
- Edge panel that runs as an accessibility service, with a handle on the screen edge and a panel of pinned apps.
- Window mode (apps open as floating windows) and Full mode.
- Separate settings for portrait and landscape: panel side, handle size and position, window size, and panel size.
- Settings export and import.
- English and Indonesian.
- About dialog.
