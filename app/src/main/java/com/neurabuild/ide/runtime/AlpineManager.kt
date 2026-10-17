package com.neurabuild.ide.runtime

import android.content.Context
import android.os.Build
import android.system.Os
import java.io.File

/**
 * STEP 1 (BUILD ORDER): PRoot + Alpine skeleton.
 *
 * Runtime layout inside app private storage:
 *   filesDir/alpine/          -> extracted minirootfs (/, /bin, /etc, ...)
 *   filesDir/alpine-rootfs.ok -> marker that extraction completed
 *   filesDir/workspace/       -> user files, bind-mounted as /workspace in guest
 *   filesDir/proot-tmp/       -> PROOT_TMP_DIR (proot needs a writable tmp)
 *
 * --------------------------------------------------------------------------------
 * PRoot BINARY PACKAGING — READ THIS, IT IS THE #1 BLOCKER
 * --------------------------------------------------------------------------------
 * Termux's proot/proot-loader binaries hardcode the prefix
 *   /data/data/com.termux/files/usr
 * inside the ELF (loader path + built-in default prefix). If you copy those
 * binaries unchanged into another app (applicationId com.neurabuild.ide), proot
 * fails at startup because /data/data/com.termux does not exist / is not
 * accessible (SELinux + ownership).
 *
 * You have two options:
 *   A) Patch the binaries (RECOMMENDED, keeps our applicationId):
 *      Run tools/patch_proot_prefix.py which replaces the
 *      "/data/data/com.termux/files/usr" byte string with an equal-length
 *      "/data/data/com.neurabuild.ide/files/usr" padded string... but note our
 *      package name is LONGER, so instead we patch to a short prefix and
 *      pass -r/-b explicitly. See docs/PROOT_PACKAGING.md for the exact
 *      procedure. Short version: patch prefix to "/data/data/neurabuild.placehold"
 *      placeholder of identical length, then always invoke proot with
 *      explicit -r (rootfs) and -b (binds) so the baked-in default is
 *      never used.
 *   B) Set applicationId = "com.termux": works with unpatched binaries but
 *      you cannot publish under that ID and it collides with real Termux.
 *
 * 16KB PAGE ALIGNMENT (Android 15+):
 *   Android 15 enforces 16KB page size on arm64. The proot ELF + loader must
 *   be built/patched with 16KB-aligned LOAD segments (p_align=16384), else
 *   exec fails with "unaligned" errors. Use a proot built from Termux
 *   packages after the 16KB migration, or run
 *   `tools/align_elf_16k.py` check. See docs/PROOT_PACKAGING.md.
 *
 * Loading:
 *   Place the patched proot as app/src/main/jniLibs/arm64-v8a/libproot.so
 *   and the loader as libproot-loader.so. At runtime we copy them from
 *   nativeLibraryDir to filesDir/bin/ and chmod 0700, because exec from
 *   nativeLibraryDir may be blocked (noexec on some OEMs).
 *
 * Env (do NOT set PROOT_NO_SECCOMP — breaks getcwd under musl):
 *   PROOT_TMP_DIR=<files>/proot-tmp
 *   PROOT_LOADER=<files>/bin/libproot-loader.so
 *   HOME=/root  PATH=/usr/local/sbin:/usr/local/bin:/usr/bin:/bin:/sbin
 */
class AlpineManager(private val context: Context) {

    companion object {
        const val ROOTFS_ASSET = "alpine-minirootfs-3.22.2-aarch64.tar.gz"
        const val EXPECTED_RELEASE_PREFIX = "3.22."
        const val MIN_FREE_BYTES = 200L * 1024 * 1024
    }

    val filesDir: File get() = context.filesDir
    val alpineDir: File get() = File(filesDir, "alpine")
    val workspaceDir: File get() = File(filesDir, "workspace")
    val prootTmpDir: File get() = File(filesDir, "proot-tmp")
    val binDir: File get() = File(filesDir, "bin")
    private val markerFile: File get() = File(filesDir, "alpine-rootfs.ok")

    fun isInstalled(): Boolean =
        markerFile.exists() && File(alpineDir, "etc/alpine-release").exists()

    fun installedRelease(): String? = try {
        File(alpineDir, "etc/alpine-release").takeIf { it.exists() }?.readText()?.trim()
    } catch (_: Exception) { null }

    fun abiSupported(): Boolean =
        Build.SUPPORTED_ABIS.contains("arm64-v8a")

    fun freeBytes(): Long = filesDir.freeSpace

    suspend fun stagedInstall(
        withNode: Boolean,
        withPython: Boolean,
        onProgress: (String, Float) -> Unit
    ): Result<String> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            if (!abiSupported()) {
                return@withContext Result.failure(
                    IllegalStateException("Unsupported ABI: ${Build.SUPPORTED_ABIS.joinToString()}. arm64-v8a required.")
                )
            }
            if (freeBytes() < MIN_FREE_BYTES) {
                return@withContext Result.failure(
                    IllegalStateException("Need >= 200MB free storage.")
                )
            }
            onProgress("Preparing directories", 0.05f)
            alpineDir.mkdirs(); workspaceDir.mkdirs(); prootTmpDir.mkdirs(); binDir.mkdirs()

            onProgress("Installing PRoot binaries", 0.15f)
            val prootInstall = installProotBinaries()
            if (prootInstall.isFailure) return@withContext Result.failure(prootInstall.exceptionOrNull()!!)

            onProgress("Extracting Alpine rootfs", 0.25f)
            extractRootfs { frac -> onProgress("Extracting Alpine rootfs", 0.25f + frac * 0.45f) }

            onProgress("Verifying Alpine release", 0.72f)
            val release = installedRelease()
            if (release == null || !release.startsWith(EXPECTED_RELEASE_PREFIX)) {
                return@withContext Result.failure(
                    IllegalStateException("Unexpected alpine-release: $release (want ${EXPECTED_RELEASE_PREFIX}x)")
                )
            }

            // STEP 1 GATE: proot must actually launch and cat the release file.
            onProgress("Verifying PRoot launch", 0.78f)
            val probe = ProotRunner(this@AlpineManager).run("cat /etc/alpine-release", timeoutMs = 15_000)
            if (probe.exitCode != 0 || !probe.stdout.trim().startsWith(EXPECTED_RELEASE_PREFIX)) {
                return@withContext Result.failure(
                    IllegalStateException("PRoot probe failed: exit=${probe.exitCode} out=${probe.stdout} err=${probe.stderr}")
                )
            }

            if (withNode || withPython) {
                onProgress("Installing packages", 0.85f)
                val pkgs = buildList {
                    if (withNode) add("nodejs"); if (withNode) add("npm")
                    if (withPython) add("python3"); if (withPython) add("py3-pip")
                }
                val apk = ProotRunner(this@AlpineManager).run(
                    "apk add --no-cache ${pkgs.joinToString(" ")}", timeoutMs = 300_000
                )
                if (apk.exitCode != 0) {
                    return@withContext Result.failure(
                        IllegalStateException("apk add failed: ${apk.stderr.take(2000)}")
                    )
                }
            }

            // Seed workspace with a hello file + resolv.conf for DNS.
            try {
                File(alpineDir, "etc/resolv.conf").apply {
                    parentFile?.mkdirs()
                    writeText("nameserver 8.8.8.8\nnameserver 1.1.1.1\n")
                }
                File(workspaceDir, "README.md").apply {
                    if (!exists()) writeText("# Workspace\n\nFiles here appear as /workspace inside Alpine.\n")
                }
            } catch (_: Exception) { }

            markerFile.writeText("ok $release")
            onProgress("Ready", 1.0f)
            Result.success(release)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun installProotBinaries(): Result<Unit> {
        return try {
            val libDir = File(context.applicationInfo.nativeLibraryDir)
            val candidates = listOf(
                File(libDir, "libproot.so"),
                File(libDir, "libproot-loader.so")
            )
            val prootSrc = candidates[0].takeIf { it.exists() }
                ?: return Result.failure(
                    IllegalStateException("libproot.so missing. See docs/PROOT_PACKAGING.md")
                )
            val loaderSrc = candidates[1].takeIf { it.exists() }
            val prootDst = File(binDir, "proot")
            val loaderDst = File(binDir, "libproot-loader.so")
            prootSrc.copyTo(prootDst, overwrite = true)
            if (loaderSrc != null) loaderSrc.copyTo(loaderDst, overwrite = true)
            try {
                Os.chmod(prootDst.absolutePath, 448) // 0700
                if (loaderDst.exists()) Os.chmod(loaderDst.absolutePath, 448)
            } catch (_: Exception) {
                prootDst.setExecutable(true, true)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractRootfs(onFrac: (Float) -> Unit) {
        val asset = context.assets.open(ROOTFS_ASSET)
        asset.use { input ->
            // Stream through gzip + tar without loading whole file in RAM.
            java.util.zip.GZIPInputStream(input).use { gz ->
                TarExtractor.extract(gz, alpineDir, onFrac)
            }
        }
    }
}
