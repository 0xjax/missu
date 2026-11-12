# Missu

Android client for missav.ai, built with Expo SDK 58, React Native, TypeScript and Expo Router.

The source site serves server-rendered HTML instead of a JSON API, so the app fetches pages directly and parses them on device. Parsing contracts live in `docs/api/openapi.json`; `docs/api/README.md` records the source constraints and how to regenerate the spec from HAR captures.

## Requirements

- Bun, used for package management and scripts
- Android device running Expo Go for daily development; iOS and web are out of scope

## Development

```bash
bun install
bun run start
```

Scan the QR code with Expo Go. Code changes reach the device through Metro Fast Refresh — do not restart Metro, rescan or force-stop the app to apply them. For a standalone APK that runs without Metro, see "构建独立 APK" in `docs/android-debugging.md`, which also covers wireless ADB, screenshots and log capture.

## Verification

```bash
bun check                              # tsc --noEmit
bun run lint                           # oxlint src scripts
bunx expo export --platform android    # production JS bundle
```

## Layout

```
src/api/         fetch + HTML parsing (missav.ts, parse.ts, with-timeout.ts)
src/app/         Expo Router file routes
src/components/  shared UI, including the video player and preview overlay
src/constants/   theme tokens, channel groups, language list
src/hooks/       theme and layout hooks
src/storage/     key-value persistence (history, search history, session-adjacent state)
src/stores/      React context stores (auth, language, preferences, preview, challenge)
src/utils/       formatting and haptics helpers
scripts/         har-to-openapi.js, used to regenerate the API draft
docs/            API contracts, Android debugging notes, design context
```

## Implementation notes

- Android only. Design and acceptance target a physical device.
- Requests go through `fetch` against the source site; Cloudflare challenges are surfaced through a WebView handshake, and `cf_clearance` is shared with the native cookie jar so the original request can retry.
- Media playback uses `expo-video` over HLS; cards carry a short preview clip whose URL is derived from the thumbnail, so previews add no requests.
- State is persisted with `expo-sqlite/kv-store`; the AsyncStorage layout from earlier builds is migrated once on startup.
