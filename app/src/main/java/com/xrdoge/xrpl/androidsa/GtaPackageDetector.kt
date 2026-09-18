package com.xrdoge.xrpl.androidsa

import android.content.Context
import android.content.Intent
import android.net.Uri

private const val RuntimePreferencesName = "androidsa_runtime"

data class GtaRuntimeStatus(
    val packageName: String,
    val versionName: String,
    val installed: Boolean,
    val launchable: Boolean,
    val state: String,
    val summary: String,
)

object GtaPackageDetector {
    const val PACKAGE_OVERRIDE_KEY = "androidsa.gta.runtime.package_override"
    const val OFFICIAL_PACKAGE = "com.rockstargames.gtasager"
    const val OFFICIAL_VERSION = "2.11.277"

    val defaultPackages: List<String> = listOf(
        OFFICIAL_PACKAGE,
        "com.rockstargames.gtasasa",
        "com.rockstargames.gtasa",
        "com.rockstargames.gtasa.de",
    )

    private fun runtimeState(installed: Boolean, launchable: Boolean): String = when {
        !installed -> "missing-host"
        !launchable -> "blocked"
        else -> "runtime-ready"
    }

    private fun matchesOfficialRuntimeVersion(versionName: String?): Boolean {
        val normalized = versionName.orEmpty().lowercase()
        return normalized.contains(OFFICIAL_VERSION) || normalized.contains("211277")
    }

    fun configuredPackages(context: Context): List<String> {
        val prefs = context.getSharedPreferences(RuntimePreferencesName, Context.MODE_PRIVATE)
        val overridePackages = prefs.getString(PACKAGE_OVERRIDE_KEY, null)
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.distinct()
        return if (!overridePackages.isNullOrEmpty()) overridePackages else defaultPackages
    }

    fun detect(context: Context): GtaRuntimeStatus {
        val packageCandidates = configuredPackages(context)
        for (packageName in packageCandidates) {
            try {
                val packageManager = context.packageManager
                val packageInfo = packageManager.getPackageInfo(packageName, 0)
                val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
                val versionName = packageInfo.versionName ?: "unknown"
                val officialRuntime = matchesOfficialRuntimeVersion(versionName)
                val state = runtimeState(installed = true, launchable = launchIntent != null)
                return GtaRuntimeStatus(
                    packageName = packageName,
                    versionName = versionName,
                    installed = true,
                    launchable = launchIntent != null,
                    state = state,
                    summary = if (launchIntent != null) {
                        val runtimeDescriptor = if (officialRuntime) "official" else "compatible"
                        "GTA SA Mobile $runtimeDescriptor runtime detected and launchable on this device (version $versionName)."
                    } else {
                        "GTA SA Mobile runtime is installed but blocked from launch on this device (version $versionName)."
                    },
                )
            } catch (_: Exception) {
                // Fall through to the next approved package candidate.
            }
        }

        val fallbackPackage = packageCandidates.firstOrNull() ?: defaultPackages.first()
        return GtaRuntimeStatus(
            packageName = fallbackPackage,
            versionName = "missing",
            installed = false,
            launchable = false,
            state = runtimeState(installed = false, launchable = false),
            summary = "GTA SA Mobile runtime is not installed on this device. Install the official app from the Play Store, then start the local runtime from AndroidSA.",
        )
    }

    fun launch(context: Context): Boolean {
        val runtimeStatus = detect(context)
        if (!runtimeStatus.installed) {
            val storeIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${runtimeStatus.packageName}"))
            return try {
                context.startActivity(storeIntent)
                false
            } catch (_: Exception) {
                false
            }
        }

        val launchIntent = context.packageManager.getLaunchIntentForPackage(runtimeStatus.packageName) ?: return false
        return try {
            context.startActivity(launchIntent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
