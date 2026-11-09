# CatchIt promo video

`store/video/CatchIt_promo_9x16.mp4` - 27.6 s, 1080x1920 (9:16), **60 fps**, H.264 + AAC, 5.5 MB.

**The ad is the real app.** Every app screen in it is a device capture of the release
build on the emulator, placed in `ad/assets/screens/`. The HTML composition only adds
motion, captions and the end card - it never redraws the UI, so the ad cannot drift away
from what the app actually looks like.

> An earlier version rebuilt the screens as hand-written CSS from the design tokens. It
> looked nothing like the app and was thrown away. **If the app's UI changes, re-capture
> the screens - do not edit the CSS copy.**

## Scenes

| # | Time | Scene | Caption |
|---|------|-------|---------|
| 1 | 0.0 - 3.0 | the app's own splash animation, full bleed, with its own sound | (none - the logo is the splash) |
| 2 | 3.0 - 7.4 | Bus home: live bus count, station strip, search, popular routes | **Live** - Real bus positions across Klang Valley |
| 3 | 7.4 - 9.8 | search for T507 with the result | **Every route** - Find your bus in seconds |
| 4 | 9.8 - 14.2 | route detail: the live Leaflet map and the approaching bus card | **Live map** - Watch the bus move towards you |
| 5 | 14.2 - 17.6 | the stop rail with live per-stop ETAs | **Real ETAs** - Updated every 30 seconds |
| 6 | 17.6 - 21.0 | the two-stage alarm setup sheet | **Two stops** - Warn me when the bus reaches each stop |
| 7 | 21.0 - 23.8 | the armed alarm | **Armed** - Leave now, then get to your waiting spot |
| 8 | 23.8 - 27.6 | end card: app icon, wordmark, tagline, CTA | Know when your feeder bus is coming |

Scene 7 shows a genuinely armed alarm: `T108 / MRT SRI DAMANSARA SENTRAL PINTU A ->
TAMAN BUKIT MALURI (SELATAN)`, with the live bus `VAK1034`.

## Rebuilding it

### 1. Re-capture the screens

Drive the release build on the emulator, capture each screen at 1080x2400, then scale to
864x1920 (the composition frames them at x=108 on a 1080-wide canvas):

```bash
adb -s emulator-5554 exec-out screencap -p > shot.png
ffmpeg -i shot.png -vf scale=864:1920 assets/screens/01_bus.png
```

The device must be booted with `-gpu host` (see the catchit-release skill).

### 2. Render

```bash
cd ad
npx --yes hyperframes@0.9.26 check
npx --yes hyperframes@0.9.26 render -f 60 -q delivery --gpu -o ../store/video/_raw.mp4
```

`delivery` quality is for the master only - it came out at 61 MB. Re-encode for
distribution:

```bash
ffmpeg -i _raw.mp4 -c:v libx264 -profile:v high -crf 23 -preset slow \
       -pix_fmt yuv420p -g 120 -movflags +faststart -c:a aac -b:a 160k \
       ../store/video/CatchIt_promo_9x16.mp4
```

That is 5.5 MB at the same 60 fps with no visible loss, because the frames are mostly
still captures.

## Audio

`ad/assets/bed.mp3` is **real music**, not a synthesised pad. It is "Sports Highlights"
by Ahjay Stelino from MixKit (`assets.mixkit.co/music/51/51.mp3`), used under the
MixKit Free License - free for commercial use, no attribution required.

- The music is cut from **56.0 s** for **26.6 s**, the flattest window in the track
  (1.7 dB of level variation across 14 two-second windows), then `loudnorm`'d.
- The app's **own splash sound** plays under the first 3 s, because the splash video
  element carries `data-has-audio="true"`.
- The music enters at `data-start="1.0"`.

Two earlier beds were wrong and both sounded broken:

1. The splash's 3-second swell stretched 7.6x with `atempo` - silent for 1.4 s, then a
   21-second crescendo that only peaks at the end. A stretched swell is not music.
2. A hand-synthesised five-note pad - a static drone that swelled 4x toward the end and
   carried hard clicks on every scene change.

Verified on the shipped file: mean -19.8 dB, peak -3.1 dB, **3.5 dB total level range
across the whole 27.6 s**, zero silence gaps, zero decode errors.

## Verifying that the ad still looks like the app

Do not trust the eye alone. Render a frame and compare it against the screen capture it
came from - they should be nearly identical, and clearly *not* identical to a different
screen:

```bash
ffmpeg -ss 5.0 -i CatchIt_promo_9x16.mp4 -frames:v 1 frame.png
```

Measured on the shipped file: mean absolute pixel difference against the source capture
is **1.83/255 (0.72%)** above the caption band, against **55.28/255 (21.7%)** for a
different screen as a control. If the first number climbs into the tens, the app's UI has
changed and the screens need re-capturing.

## HyperFrames gotchas hit while building this

- **`.clip { inset: 0 }` sets `top: 0`.** Any absolutely positioned child needs
  `top: auto` or it spans the whole frame.
- **Never patch by matching a comment that exists in both the CSS and the script.** A
  `str.replace` put JavaScript inside the `<style>` block and the browser silently
  dropped every rule after the damage.
- **Assert on generated attributes** - two `<path d="...">` got the JS payload and the map
  drew nothing.
- **GSAP overwrite conflicts.** An ambient tween on the same element and property as a
  reveal makes the element jitter. Start every ambient tween *after* the reveal.
- **`left` is a layout property** and HyperFrames rejects it
  (`gsap_non_transform_motion`). Animate `x`/`y`/`scale`/`opacity`.
- **`window.__timelines` only exists in the HyperFrames runtime.** Guard with
  `window.__timelines = window.__timelines || {}`.
- **Time the `<video>` itself**, not its wrapper, and give it `data-has-audio="true"`
  or `muted`.

## Other formats

9:16, ready for Reels, TikTok, Shorts and the Play Store.
