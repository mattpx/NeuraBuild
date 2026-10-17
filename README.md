# NeuraBuild — lightweight AI-assisted coding IDE

AI chat is the main interface. Alpine + PRoot runtime, short-lived sessions, no root.

## Status
Build order step 1 first: PRoot + Alpine skeleton (`AlpineManager` + `ProotRunner`).
Do not start UI polish before `cat /etc/alpine-release` returns `3.22.x` on-device.

## Prereqs to bundle (not in git)
1. `app/src/main/assets/alpine-minirootfs-3.22.2-aarch64.tar.gz`
   - https://dl-cdn.alpinelinux.org/alpine/v3.22/releases/aarch64/
2. `app/src/main/jniLibs/arm64-v8a/libproot.so` (+ loader if separate)
   - See `docs/PROOT_PACKAGING.md` — patch Termux prefix, verify 16KB alignment.

## Build (low-RAM server)
```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/arm64-v8a/debug/*.apk
```

## Structure
- `runtime/` — AlpineManager, ProotRunner, TarExtractor
- `ai/` — OpenAiClient, AgentTools, AgentLoop, Policy
- `security/` — SecurePrefs (EncryptedSharedPreferences)
- `data/` — Room chat history
- `ui/` — Chat, Terminal, Files, Editor, Preview, Settings, Setup
- `preview/` — NanoHTTPD reverse proxy :8080 -> :3000
- `core/` — App (onTrimMemory), MemoryPressure
