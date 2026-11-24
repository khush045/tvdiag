package dev.tvnexus.core

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build

/** How a feature can be used on THIS device. Never claim more than was actually detected. */
enum class Support(val label: String) {
    SUPPORTED("Supported"),
    PARTIAL("Partially supported"),
    NEEDS_ADB("Requires ADB"),
    NEEDS_ROOT("Requires root"),
    NEEDS_SYSTEM("Requires system privileges"),
    UNSUPPORTED("Unsupported")
}

data class Capability(val name: String, val support: Support, val detail: String)

enum class Severity(val label: String) {
    OK("OK"),
    INFO("INFO"),
    WARN("WARNING"),
    BAD("PROBLEM")
}

/**
 * [confidence] is deliberately explicit so observed facts are never mixed up with guesses:
 *  - "Observed"   : read directly from the device by this app.
 *  - "Hypothesis" : plausible, NOT verified against firmware source.
 *  - "Ruled out"  : checked and found not to apply (for the current Android user, as seen by an unprivileged app).
 */
data class Finding(
    val severity: Severity,
    val confidence: String,
    val title: String,
    val evidence: String,
    val explanation: String
)

/** A command shown to the user for running from a computer. The app NEVER executes these itself. */
data class CommandTemplate(
    val command: String,
    val purpose: String,
    val modifiesDevice: Boolean,
    val note: String = ""
)

data class DeviceProfile(
    val sdk: Int,
    val release: String,
    val manufacturer: String,
    val brand: String,
    val model: String,
    val device: String,
    val board: String,
    val hardware: String,
    val fingerprint: String,
    val buildType: String,
    val isTelevisionMode: Boolean,
    val hasLeanback: Boolean
) {
    fun summary(): String =
        "Android $release (API $sdk) · $manufacturer/$brand $model\n" +
            "device=$device board=$board hw=$hardware · build type=$buildType\n" +
            "TV UI mode: ${if (isTelevisionMode) "yes" else "no"} · Leanback feature: ${if (hasLeanback) "yes" else "no"}"

    companion object {
        fun detect(ctx: Context): DeviceProfile {
            val ui = ctx.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
            val tv = ui?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
            val leanback = ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
            return DeviceProfile(
                sdk = Build.VERSION.SDK_INT,
                release = Build.VERSION.RELEASE,
                manufacturer = Build.MANUFACTURER,
                brand = Build.BRAND,
                model = Build.MODEL,
                device = Build.DEVICE,
                board = Build.BOARD,
                hardware = Build.HARDWARE,
                fingerprint = Build.FINGERPRINT,
                buildType = Build.TYPE,
                isTelevisionMode = tv,
                hasLeanback = leanback
            )
        }
    }
}
