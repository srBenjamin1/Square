# AYN Thor mode

Square's Thor mode is an opt-in Android multi-display presentation. It keeps
the existing `PlaybackService` and Media3 session as the only audio engine:
the player activity and panel activity each connect to that same session and
never create a second playback engine.

Enable **AYN Thor dual-screen mode** under Settings > App > System and reopen
Square. The app uses `DisplayManager` and launches:

- the player activity explicitly on display 0 (the upper integrated display);
- the panel activity explicitly on the selected secondary display, which the
  confirmed Thor configuration exposes as display 4.

The player screen shows the current artwork, metadata, progress, and transport
controls. The panel screen has Lyrics, Library, Queue, and Equalizer tabs. Lyrics
are loaded through Square's existing `LyricsLibrary`, queue entries come from
the Media3 controller, and equalizer sliders update Square's existing
per-output `Equalizer` state. Spotify/librespot and YouTube Music continue to
use their existing backends.

The physical layout is firmware-dependent. Android may reject
`setLaunchDisplayId()` or expose only one display; in that case Square remains
in its normal single-screen mode. Button keycodes differ between handheld
firmware versions, so the central input mapper handles standard Android gamepad
codes but requires validation on a real Thor. Unknown and system-reserved
codes are deliberately not consumed. Touch, D-pad, L1/R1, L2/R2, Start,
Select, A/B/X/Y, and joystick motion are handled where Android reports them.

The display picker in Settings > App > System lists the IDs, names, state,
resolution, density, and `FLAG_PRESENTATION` status reported by
`DisplayManager`. Manual player/panel choices are persisted and override
automatic selection. An `OFF` state is logged and shown as diagnostic
information; Square does not attempt to wake display 0 because Android offers
no public API that can safely force the firmware's integrated panel on.

`Presentation` was evaluated for display 4. The panel is kept as a regular
activity because it needs an independent lifecycle, Compose focus, and gamepad
input while sharing the existing Media3 session. Display 4's
`FLAG_PRESENTATION` is used for automatic selection and logged; no privileged
or private display API is required.

The Library tab intentionally opens the normal Square catalogue/search surface
through `SELECT` rather than presenting a disconnected fake library. Full
catalogue rendering on the secondary display requires validating the Thor
firmware's activity focus policy and is not claimed here.
