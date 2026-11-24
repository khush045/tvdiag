package dev.tvnexus.modules.recovery

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import dev.tvnexus.core.Capability
import dev.tvnexus.core.CommandTemplate
import dev.tvnexus.core.DeviceProfile
import dev.tvnexus.core.ModuleHost
import dev.tvnexus.core.Shell
import dev.tvnexus.core.Support
import dev.tvnexus.core.ToolModule
import dev.tvnexus.ui.Ui

class RecoveryCenterModule : ToolModule {
    override val id = "recovery"
    override val title = "Recovery Center"
    override val summary = "Boot/recovery information and capability-gated reboot"

    private val rebootPermission = "android.permission.REBOOT"

    private fun canReboot(ctx: Context) =
        ctx.checkSelfPermission(rebootPermission) == PackageManager.PERMISSION_GRANTED

    // Read-only properties. A null means "unset or hidden from this app", never "false".
    private val props = listOf(
        "ro.boot.slot_suffix" to "A/B slot suffix (present on A/B devices)",
        "ro.build.ab_update" to "A/B update flag",
        "ro.boot.verifiedbootstate" to "Verified boot state (green/yellow/orange/red)",
        "ro.boot.flash.locked" to "Bootloader lock flag (1 = locked)",
        "ro.boot.veritymode" to "dm-verity mode",
        "ro.oem_unlock_supported" to "OEM unlocking supported",
        "ro.boot.dynamic_partitions" to "Dynamic partitions",
        "ro.build.system_root_image" to "System-as-root",
        "ro.product.first_api_level" to "First API level shipped",
        "ro.bootmode" to "Current boot mode"
    )

    override fun capabilities(ctx: Context, profile: DeviceProfile): List<Capability> {
        val reboot = canReboot(ctx)
        val updateUi = ctx.packageManager.resolveActivity(Intent("android.settings.SYSTEM_UPDATE_SETTINGS"), 0) != null
        val need = "Needs the REBOOT permission (system/privileged apps only). Not granted to this app."
        return listOf(
            Capability("Boot / recovery information", Support.PARTIAL,
                "Build info and read-only system properties. Some properties are hidden from apps by SELinux."),
            Capability("Normal reboot", if (reboot) Support.SUPPORTED else Support.NEEDS_SYSTEM,
                if (reboot) "REBOOT permission is granted." else "$need Use 'adb reboot' instead."),
            Capability("Reboot to recovery", if (reboot) Support.PARTIAL else Support.NEEDS_SYSTEM,
                (if (reboot) "Permission granted, but whether this firmware honours the reason is untested. " else "$need ") +
                    "Alternative: 'adb reboot recovery'."),
            Capability("Reboot to bootloader", if (reboot) Support.PARTIAL else Support.NEEDS_SYSTEM,
                (if (reboot) "Permission granted, but firmware behaviour is untested. " else "$need ") +
                    "Alternative: 'adb reboot bootloader'."),
            Capability("Open system update screen", if (updateUi) Support.SUPPORTED else Support.UNSUPPORTED,
                if (updateUi) "An activity resolves android.settings.SYSTEM_UPDATE_SETTINGS." else "No activity resolves it."),
            Capability("Read recovery partition", Support.NEEDS_ROOT, "Raw partitions are not accessible to apps."),
            Capability("Wipe data / factory reset", Support.UNSUPPORTED, "Deliberately not implemented in this app.")
        )
    }

    override fun createView(host: ModuleHost): View {
        val ctx = host.context
        val col = Ui.column(ctx)
        col.addView(Ui.heading(ctx, title))
        col.addView(Ui.text(ctx,
            "This app never wipes data or factory-resets, and never invents button combinations. Reboot buttons are " +
                "enabled only when the system has granted the needed permission.", 18f, Ui.DIM))

        col.addView(Ui.heading(ctx, "What works on this device"))
        col.addView(Ui.capabilityCard(ctx, capabilities(ctx, host.profile)))

        col.addView(Ui.heading(ctx, "Boot information (read-only)"))
        val info = StringBuilder()
        info.appendLine("Build.BOOTLOADER = ${Build.BOOTLOADER}")
        info.appendLine("Build.FINGERPRINT = ${Build.FINGERPRINT}")
        info.appendLine("Build.TYPE = ${Build.TYPE}  TAGS = ${Build.TAGS}")
        if (Build.VERSION.SDK_INT >= 23) info.appendLine("Security patch = ${Build.VERSION.SECURITY_PATCH}")
        info.appendLine()
        props.forEach { (k, d) -> info.appendLine("$k = ${Shell.getprop(k) ?: "<unset or not visible to apps>"}\n    $d") }
        col.addView(Ui.card(ctx, Ui.text(ctx, info.toString().trimEnd(), 17f, mono = true)))

        col.addView(Ui.heading(ctx, "Actions"))
        col.addView(Ui.button(ctx, "Open system update screen") {
            try {
                ctx.startActivity(Intent("android.settings.SYSTEM_UPDATE_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) {
                host.toast("No system update screen on this firmware")
            }
        })
        val reboot = canReboot(ctx)
        fun rebootButton(label: String, reason: String?, warning: String) {
            col.addView(Ui.button(ctx, if (reboot) label else "$label (unavailable: permission not granted)", reboot) {
                confirm(ctx, label, warning) {
                    try {
                        (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).reboot(reason)
                    } catch (e: Exception) {
                        host.toast("Reboot refused: ${e.javaClass.simpleName}")
                    }
                }
            })
        }
        rebootButton("Normal reboot", null, "The TV restarts. Unsaved work in apps may be lost.")
        rebootButton("Reboot to recovery", "recovery",
            "The TV restarts into recovery. Do NOT choose 'Wipe data/factory reset' there unless you intend to erase everything. " +
                "To leave, choose the reboot-system option; menu navigation keys vary by device.")
        rebootButton("Reboot to bootloader", "bootloader",
            "The TV restarts into bootloader/fastboot, if this firmware supports it. It may then wait for a computer. " +
                "Leaving it usually requires 'fastboot reboot' from a computer.")

        col.addView(Ui.heading(ctx, "Boot modes - what they are"))
        listOf(
            "Recovery Mode" to "A small separate system used for installing updates and wiping data. Entered by a reboot reason or a hardware/firmware-specific method.",
            "Fastboot Mode" to "A protocol served by the bootloader over USB to a computer running 'fastboot'. Not every TV exposes it.",
            "Bootloader Mode" to "The first-stage loader. On some devices it is the same as fastboot; on others (including some MediaTek designs) there are lower-level modes with different tools.",
            "Factory Mode" to "Manufacturer production-line mode. Not standardised.",
            "Service Mode" to "Manufacturer technician menu. Not standardised."
        ).forEach { (t, d) ->
            col.addView(Ui.card(ctx, Ui.text(ctx, t, 20f, Ui.ACCENT), Ui.text(ctx, d, 17f, Ui.DIM)))
        }
        col.addView(Ui.card(ctx, Ui.text(ctx,
            "No hardware key combinations are listed because none are verified for your model. Check the manufacturer's " +
                "documentation or service manual.", 17f, Ui.WARN)))

        col.addView(Ui.heading(ctx, "ADB templates (run from a computer)"))
        listOf(
            CommandTemplate("adb reboot", "Normal reboot", true),
            CommandTemplate("adb reboot recovery", "Reboot to recovery", true, "Do not select wipe/factory reset."),
            CommandTemplate("adb reboot bootloader", "Reboot to bootloader", true),
            CommandTemplate("adb shell getprop ro.boot.flash.locked", "Read bootloader lock state", false),
            CommandTemplate("fastboot getvar all", "Read bootloader variables (only in fastboot mode)", false)
        ).forEach { t ->
            col.addView(Ui.card(ctx,
                Ui.text(ctx, t.command, 18f, mono = true),
                Ui.text(ctx, (if (t.modifiesDevice) "REBOOTS DEVICE - " else "read-only - ") + t.purpose +
                    (if (t.note.isNotEmpty()) "\n${t.note}" else ""), 17f, if (t.modifiesDevice) Ui.WARN else Ui.DIM)))
        }
        return Ui.scroll(col)
    }

    /** Confirmation dialog. Cancel is the default-focused button so a stray OK press does nothing. */
    private fun confirm(ctx: Context, label: String, warning: String, onYes: () -> Unit) {
        val d = AlertDialog.Builder(ctx)
            .setTitle("Confirm: $label")
            .setMessage(warning)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Proceed") { _, _ -> onYes() }
            .create()
        d.show()
        d.getButton(AlertDialog.BUTTON_NEGATIVE)?.requestFocus()
    }
}
