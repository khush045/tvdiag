package dev.tvnexus.modules.devoptions

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.UserManager
import android.provider.Settings
import dev.tvnexus.core.CommandTemplate
import dev.tvnexus.core.DeviceProfile
import dev.tvnexus.core.Finding
import dev.tvnexus.core.Severity

/** One value read from the device, keeping "not set" and "not readable" distinct from real values. */
data class Reading(val key: String, val scope: String, val value: Int?, val note: String) {
    fun show(): String = when {
        value != null -> value.toString()
        else -> "<$note>"
    }
}

data class DevOptionsReport(
    val readings: List<Reading>,
    val findings: List<Finding>,
    /** key -> displayed value; stored between runs so a repair can be verified by comparison. */
    val snapshot: Map<String, String>
)

/**
 * READ-ONLY. Everything here is observable by an ordinary, unprivileged app.
 * Nothing in this class writes a setting, changes a restriction or touches another package.
 *
 * Epistemic rule used throughout: a [Finding] says "Observed" only for what this app read
 * directly. Statements about what AOSP Settings or the OEM Settings do with those values are
 * labelled "Hypothesis" because they were NOT verified against this device's firmware source.
 */
object DevOptionsDiagnostics {

    private val SETUP_PACKAGES = listOf(
        "com.android.tv.settings",
        "com.android.settings",
        "com.google.android.tvsettings",
        "com.android.provision",
        "com.google.android.setupwizard",
        "com.google.android.tungsten.setupwraith",
        "com.android.managedprovisioning"
    )

    private fun readGlobal(ctx: Context, key: String): Reading = try {
        Reading(key, "global", Settings.Global.getInt(ctx.contentResolver, key), "")
    } catch (e: Settings.SettingNotFoundException) {
        Reading(key, "global", null, "not set")
    } catch (e: SecurityException) {
        Reading(key, "global", null, "not readable by this app")
    }

    private fun readSecure(ctx: Context, key: String): Reading = try {
        Reading(key, "secure", Settings.Secure.getInt(ctx.contentResolver, key), "")
    } catch (e: Settings.SettingNotFoundException) {
        Reading(key, "secure", null, "not set")
    } catch (e: SecurityException) {
        Reading(key, "secure", null, "not readable by this app")
    }

    /** Undocumented/hidden API, so reflection and "unknown" on failure. Never used as a guarantee. */
    private fun reflectBoolean(target: Any, method: String): Boolean? = try {
        target.javaClass.getMethod(method).invoke(target) as? Boolean
    } catch (e: Exception) {
        null
    }

    private fun reflectInt(target: Any, method: String): Int? = try {
        target.javaClass.getMethod(method).invoke(target) as? Int
    } catch (e: Exception) {
        null
    }

    fun run(ctx: Context, profile: DeviceProfile): DevOptionsReport {
        val readings = listOf(
            readGlobal(ctx, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED),
            readGlobal(ctx, Settings.Global.ADB_ENABLED),
            readGlobal(ctx, "adb_wifi_enabled"),
            readGlobal(ctx, Settings.Global.DEVICE_PROVISIONED),
            readSecure(ctx, "user_setup_complete"),
            readSecure(ctx, "tv_user_setup_complete")
        )
        fun r(key: String) = readings.first { it.key == key }
        val findings = mutableListOf<Finding>()

        val um = ctx.getSystemService(Context.USER_SERVICE) as UserManager
        val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

        // 1. Provisioning consistency -------------------------------------------------------
        val prov = r(Settings.Global.DEVICE_PROVISIONED)
        val setup = r("user_setup_complete")
        val tvSetup = r("tv_user_setup_complete")
        if (prov.value == 0 && setup.value == 1) {
            findings += Finding(
                Severity.WARN, "Observed (values) / Hypothesis (effect)",
                "Provisioning flags are inconsistent",
                "device_provisioned=0 but user_setup_complete=${setup.show()} tv_user_setup_complete=${tvSetup.show()}",
                "Observed: the device-wide flag says 'not provisioned' while the per-user flags say 'setup finished'. " +
                    "A completed setup normally sets both. Hypothesis (NOT verified in this firmware's source): some " +
                    "Settings code paths treat an unprovisioned device as a reason to hide or block restricted screens. " +
                    "This is the strongest lead, but it is unconfirmed, and changing the value is not guaranteed to fix it."
            )
        } else if (prov.value == 0) {
            findings += Finding(
                Severity.WARN, "Observed", "Device not provisioned",
                "device_provisioned=${prov.show()} user_setup_complete=${setup.show()}",
                "Setup Wizard has not marked the device as provisioned."
            )
        } else {
            findings += Finding(
                Severity.OK, "Observed", "Provisioning flags look consistent",
                "device_provisioned=${prov.show()} user_setup_complete=${setup.show()}",
                "Provisioning is not an obvious cause on this device."
            )
        }

        // 2. System user / which Android user am I -------------------------------------------
        val isSystem = if (Build.VERSION.SDK_INT >= 23) um.isSystemUser else null
        val isAdmin = reflectBoolean(um, "isAdminUser")
        val userCount = reflectInt(um, "getUserCount")
        val isRestricted = reflectBoolean(um, "isRestrictedProfile")
        val isManaged = if (Build.VERSION.SDK_INT >= 30) um.isManagedProfile else null
        findings += Finding(
            if (isSystem == false || isAdmin == false || isRestricted == true) Severity.BAD else Severity.INFO,
            "Observed",
            "Android user identity (not a Google account)",
            "isSystemUser=$isSystem isAdminUser(reflective)=$isAdmin restrictedProfile(reflective)=$isRestricted " +
                "managedProfile=$isManaged userCount(reflective)=$userCount",
            "Android OS users are separate from Google accounts. 'unknown/null' means the platform hid the value from " +
                "ordinary apps; it does not mean false. If this is the system user (user 0) it is the primary admin user."
        )

        // 3. User restrictions ----------------------------------------------------------------
        val dbg = um.hasUserRestriction(UserManager.DISALLOW_DEBUGGING_FEATURES)
        val active = try {
            um.userRestrictions.keySet().filter { um.userRestrictions.getBoolean(it) }.sorted()
        } catch (e: Exception) {
            emptyList()
        }
        findings += Finding(
            if (dbg) Severity.BAD else Severity.OK, "Observed",
            "DISALLOW_DEBUGGING_FEATURES = $dbg",
            "active restrictions (${active.size}): ${if (active.isEmpty()) "none" else active.joinToString(", ")}",
            if (dbg) "This restriction explicitly blocks Developer Options and is the exact cause."
            else "Not set for the current user, so this standard restriction is ruled out. (The app cannot see " +
                "restrictions applied to other users, or private OEM checks.)"
        )

        // 4. Device admins / owner ------------------------------------------------------------
        val admins: List<ComponentName> = try {
            dpm.activeAdmins ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
        val ownsDevice = dpm.isDeviceOwnerApp(ctx.packageName)
        findings += Finding(
            if (admins.isEmpty()) Severity.OK else Severity.WARN, "Observed",
            "Device administrators",
            "activeAdmins=${if (admins.isEmpty()) "none" else admins.joinToString { it.flattenToShortString() }} " +
                "thisAppIsDeviceOwner=$ownsDevice",
            "An ordinary app cannot read the real Device Owner / Profile Owner. Confirm with ADB: " +
                "'dpm list-owners' (see command templates)."
        )

        // 5. The setting that the toggle writes -----------------------------------------------
        val dev = r(Settings.Global.DEVELOPMENT_SETTINGS_ENABLED)
        findings += Finding(
            Severity.INFO, "Observed",
            "development_settings_enabled = ${dev.show()}",
            "adb_enabled=${r(Settings.Global.ADB_ENABLED).show()} adb_wifi_enabled=${r("adb_wifi_enabled").show()}",
            "This flag is already 1, so the 'tap Build 7 times' step is not what is missing. The screen is still " +
                "blocked, which points at a gate OTHER than this flag. Wireless ADB is off, and nothing here proves it " +
                "can be switched on."
        )

        // 6. What actually handles the Developer Options intent ------------------------------
        val pm = ctx.packageManager
        val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
        val ri = pm.resolveActivity(intent, 0)
        findings += Finding(
            if (ri == null) Severity.BAD else Severity.INFO, "Observed",
            "Developer Options intent handler",
            ri?.activityInfo?.let { "${it.packageName}/${it.name}" } ?: "no activity resolves ACTION_APPLICATION_DEVELOPMENT_SETTINGS",
            if (ri == null) "The firmware exposes no Developer Options screen for this intent."
            else "This is the component that decides whether to show the screen. Its source (AOSP or OEM-modified) " +
                "was not inspected here, so the exact check that blocks it is unknown."
        )

        // 7. Settings / setup-related packages ------------------------------------------------
        val pkgLines = SETUP_PACKAGES.mapNotNull { p ->
            try {
                val info = pm.getPackageInfo(p, 0)
                val enabled = pm.getApplicationEnabledSetting(p) != PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER &&
                    pm.getApplicationEnabledSetting(p) != PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                "$p v${info.versionName} ${if (enabled) "" else "(DISABLED)"}".trim()
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }
        }
        findings += Finding(
            Severity.INFO, "Observed",
            "Settings / Setup Wizard packages present",
            if (pkgLines.isEmpty()) "none of the known package names found" else pkgLines.joinToString("\n"),
            "Shows which Settings and setup components exist. A missing or disabled Setup Wizard package is relevant " +
                "to an unprovisioned device, but this app cannot run or repair it."
        )

        // Verdict -----------------------------------------------------------------------------
        val verdict: Finding = when {
            dbg -> Finding(Severity.BAD, "Observed", "Verdict: blocked by user restriction",
                "DISALLOW_DEBUGGING_FEATURES=true", "Whoever set the restriction (a device policy controller) must lift it.")
            isAdmin == false || isSystem == false || isRestricted == true ->
                Finding(Severity.BAD, "Observed", "Verdict: current user is not a full admin",
                    "isSystemUser=$isSystem isAdmin=$isAdmin restricted=$isRestricted",
                    "Developer Options need an admin user. Switch to the owner user if one exists.")
            prov.value == 0 ->
                Finding(Severity.WARN, "Hypothesis", "Verdict: most likely lead is device_provisioned=0",
                    "Standard restrictions, user type and development_settings_enabled all checked out OK.",
                    "Everything standard was ruled out; the one anomaly left is the unprovisioned flag. Not proven. " +
                        "Repair requires WRITE_SECURE_SETTINGS (ADB shell or system privileges); a normal APK cannot do it.")
            else -> Finding(Severity.INFO, "Observed", "Verdict: no cause found from an unprivileged app",
                "No standard gate detected", "The block is probably an OEM-specific check; inspect the firmware Settings app.")
        }
        findings.add(0, verdict)

        val snapshot = LinkedHashMap<String, String>()
        readings.forEach { snapshot["${it.scope}:${it.key}"] = it.show() }
        snapshot["restriction:DISALLOW_DEBUGGING_FEATURES"] = dbg.toString()
        snapshot["user:isSystemUser"] = isSystem.toString()
        snapshot["handler:developer_options_intent"] = ri?.activityInfo?.let { "${it.packageName}/${it.name}" } ?: "none"

        return DevOptionsReport(readings, findings, snapshot)
    }

    /** Verification: compare a previous snapshot with a new one. */
    fun diff(before: Map<String, String>, after: Map<String, String>): List<String> {
        val out = mutableListOf<String>()
        (before.keys + after.keys).toSortedSet().forEach { k ->
            val b = before[k] ?: "-"
            val a = after[k] ?: "-"
            if (a != b) out += "$k: $b -> $a"
        }
        return out
    }

    /**
     * Templates for a computer with ADB. This app NEVER runs them. ADB must already be authorised on the TV;
     * if adb_enabled cannot be turned on, these cannot be used, and this app offers no way around that.
     */
    val adbTemplates: List<CommandTemplate> = listOf(
        CommandTemplate("adb shell pm list users", "List Android OS users and their flags", false),
        CommandTemplate("adb shell dpm list-owners", "Show real Device Owner / Profile Owner", false),
        CommandTemplate("adb shell settings get global device_provisioned", "Read provisioning flag", false),
        CommandTemplate("adb shell settings get secure user_setup_complete", "Read per-user setup flag", false),
        CommandTemplate("adb shell settings get global development_settings_enabled", "Read dev options flag", false),
        CommandTemplate("adb shell am start -a android.settings.APPLICATION_DEVELOPMENT_SETTINGS",
            "Ask the system to open Developer Options", false),
        CommandTemplate("adb shell settings put global device_provisioned 1",
            "Mark the device provisioned. MODIFIES a protected setting.", true,
            "Experiment based on an unverified hypothesis. Note the old value first, and re-run this app's diagnosis " +
                "afterwards. Setup Wizard may rewrite it."),
        CommandTemplate("adb shell settings put global device_provisioned 0",
            "Revert to the original observed value", true, "Use if the change made things worse.")
    )

    // Stable ordering helper for reports.
    fun reportText(profile: DeviceProfile, report: DevOptionsReport): String = buildString {
        appendLine("TV NEXUS - Developer Options Doctor report")
        appendLine(profile.summary())
        appendLine("fingerprint=${profile.fingerprint}")
        appendLine()
        report.findings.forEach {
            appendLine("[${it.severity.label}] [${it.confidence}] ${it.title}")
            appendLine("  evidence: ${it.evidence.replace("\n", "\n            ")}")
            appendLine("  note: ${it.explanation}")
            appendLine()
        }
    }
}
