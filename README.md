# ZenAgent (Android)

A native Android client for **ZenAgent** — an on-device AI-agent chat app —
scaffolded from the Pencil design `zen/design/zenagent.lib.pen`. Built with
**Kotlin + Jetpack Compose (Material 3)**.

## What's implemented

The design's core flows are translated into Compose screens:

| Screen | File | Notes |
|---|---|---|
| Chat | `ui/chat/ChatScreen.kt` | Message bubbles, tool-call cards, sub-agent delegation, and the **approval gate** (Deny / Approve & run). Header shows live run-state; input bar switches to a red **Stop** control while running. |
| Sidebar drawer | `ui/sidebar/SidebarScreen.kt` | Profile, search, conversation list, "新建对话" CTA — opened by swiping or tapping back. |
| Settings | `ui/settings/SettingsScreen.kt` | Grouped rows (通用 / 对话 / 隐私与数据) with toggles and a destructive "清除所有对话". |

State & data:
- `data/Models.kt` — domain model (`Conversation`, `Message`, `ToolCall`, `ApprovalRequest`, `RunState`, …).
- `data/SampleData.kt` — sample content mirroring the design so the UI is populated on launch.
- `ui/theme/` — palette and type extracted from the design (`#1C1C1E` chrome, `#F5F5F5` canvas, accent blue, Inter→sans-serif).

The drawer + chat + settings are wired together in `ui/AppRoot.kt`.

## Toolchain

- Gradle **8.13** (wrapper pinned), Android Gradle Plugin **8.7.3**
- Kotlin **2.0.21** (built-in Compose compiler), Compose BOM **2024.12.01**
- `compileSdk` 35, `minSdk` 26, `targetSdk` 35

`local.properties` points at the SDK (`sdk.dir=/opt/android-sdk`); update it for your machine.

## Build & run

```bash
./gradlew :app:assembleDebug          # build debug APK
./gradlew :app:installDebug           # install on a connected device/emulator
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

## Next steps

- Replace `SampleData` with a real repository + `ViewModel`s (lifecycle-viewmodel-compose is already a dependency).
- Add the remaining run-lifecycle/failure-state screens from the design (T1–T6, OOM, can't-start, can't-resume).
- Bundle the Inter font in `res/font` and point `Type.kt` at it for exact typographic fidelity.
- Wire the local model runtime (the design references "Gemma 2B · 本地").
