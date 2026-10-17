# PRoot packaging (the #1 blocker)

Termux `proot` binaries hardcode `/data/data/com.termux/files/usr`.
Our app id is `com.neurabuild.ide`, so unpatched binaries fail at startup.

## Option A — patch (recommended)

1. Get Termux proot 5.4+ built with 16KB page support:
   - https://github.com/termux/termux-packages (package `proot`)
   - Or extract from a Termux apk: `lib/arm64-v8a/libproot.so`
   - You need both `proot` and `loader` (often `libproot-loader.so`).
2. Check alignment:
   `python3 tools/check_elf_16k.py /path/to/libproot.so`
   All `LOAD` segments must show `align=16384`.
3. Patch the baked-in prefix:
   `python3 tools/patch_proot_prefix.py in.so out.so`
   This replaces `/data/data/com.termux/files/usr` with a same-length
   placeholder. The app always passes explicit `-r/-b`, so the baked
   default is never used.
4. Place outputs:
   - `app/src/main/jniLibs/arm64-v8a/libproot.so`
   - `app/src/main/jniLibs/arm64-v8a/libproot-loader.so` (if separate)

## Option B — use com.termux id

Set `applicationId = "com.termux"`. Works with unpatched binaries but
collides with real Termux and cannot be published. Not recommended.

## Rootfs asset

Download and place as `app/src/main/assets/alpine-minirootfs-3.22.2-aarch64.tar.gz`:
- https://dl-cdn.alpinelinux.org/alpine/v3.22/releases/aarch64/alpine-minirootfs-3.22.2-aarch64.tar.gz

Verify `cat /etc/alpine-release` returns `3.22.x` after install.

## Runtime env (set by AlpineManager/ProotRunner)

- `PROOT_TMP_DIR`, `PROOT_LOADER`, `HOME=/root`, `PATH`
- Never set `PROOT_NO_SECCOMP` (breaks getcwd under musl).
