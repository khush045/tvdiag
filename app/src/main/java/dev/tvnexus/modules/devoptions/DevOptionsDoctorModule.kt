package dev.tvnexus.modules.devoptions

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.View
import android.widget.LinearLayout
import dev.tvnexus.core.Capability
import dev.tvnexus.core.DeviceProfile
import dev.tvnexus.core.ModuleHost
import dev.tvnexus.core.Support
import dev.tvnexus.core.ToolModule
import dev.tvnexus.ui.Ui

class DevOptionsDoctorModule : ToolModule {
    override val id = "devoptions"
    override val title = "Developer Options Doctor"
    override val summary = "Find out why Developer Options are blocked (read-only diagnosis)"

    override fun capabilities(ctx: Context, profile: DeviceProfile): List<Capability> {
        val intentOk = ctx.packageManager.resolveActivity(
            Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS), 0
        ) != null
        return listOf(
            Capability("Read provisioning / setup flags", Support.SUPPORTED, "Settings.Global / Settings.Secure are readable by apps."),
            Capability("Read user restrictions (current user)", Support.SUPPORTED, "UserManager.getUserRestrictions()."),
            Capability("Read Device Owner / Profile Owner", Support.NEEDS_ADB, "Apps only see their own ownership; use 'dpm list-owners'."),
            Capability("List other Android users", Support.NEEDS_ADB, "Needs MANAGE_USERS or 'pm list users'."),
            Capability("Open Developer Options screen", if (intentOk) Support.SUPPORTED else Support.UNSUPPORTED,
                if (intentOk) "An activity resolves the standard intent." else "No activity resolves the standard intent."),
            Capability("Change device_provisioned / user flags", Support.NEEDS_ADB, "Needs WRITE_SECURE_SETTINGS; this app never writes them.")
        )
    }

    override fun createView(host: ModuleHost): View {
        val ctx = host.context
        val col = Ui.column(ctx)
        val results = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        var last: DevOptionsReport? = null
        var previous: Map<String, String>? = null

        val prefs = ctx.getSharedPreferences("devopts", Context.MODE_PRIVATE)
        fun loadSaved(): Map<String, String> =
            prefs.getString("snapshot", null)?.lines()?.mapNotNull {
                val i = it.indexOf('=')
                if (i > 0) it.substring(0, i) to it.substring(i + 1) else null
            }?.toMap() ?: emptyMap()

        fun render(report: DevOptionsReport, diff: List<String>?) {
            results.removeAllViews()
            if (diff != null) {
                results.addView(Ui.heading(ctx, "Verification vs previous run"))
                results.addView(
                    Ui.card(ctx, Ui.text(ctx,
                        if (diff.isEmpty()) "No values changed since the previous diagnosis."
                        else diff.joinToString("\n"), 18f, mono = true))
                )
            }
            results.addView(Ui.heading(ctx, "Findings"))
            report.findings.forEach { results.addView(Ui.findingCard(ctx, it)) }
        }

        fun runDiagnosis() {
            host.background({ DevOptionsDiagnostics.run(ctx, host.profile) }) { report ->
                val old = loadSaved()
                val diff = if (old.isEmpty()) null else DevOptionsDiagnostics.diff(old, report.snapshot)
                previous = old
                last = report
                prefs.edit().putString("snapshot", report.snapshot.entries.joinToString("\n") { "${it.key}=${it.value}" }).apply()
                render(report, diff)
            }
        }

        col.addView(Ui.heading(ctx, title))
        col.addView(Ui.text(ctx,
            "Read-only. Nothing on this screen changes your device. Conclusions are labelled Observed (read from " +
                "the device) or Hypothesis (not verified in this firmware).", 18f, Ui.DIM))
        col.addView(Ui.heading(ctx, "What works on this device"))
        col.addView(Ui.capabilityCard(ctx, capabilities(ctx, host.profile)))

        col.addView(Ui.heading(ctx, "Actions"))
        col.addView(Ui.button(ctx, "Run / re-run diagnosis and compare with previous run") { runDiagnosis() })
        col.addView(Ui.button(ctx, "Open Developer Options (official system screen)") {
            try {
                ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) {
                host.toast("Could not open: ${e.javaClass.simpleName}")
            }
        })
        col.addView(Ui.button(ctx, "Open device info (About) settings") {
            try {
                ctx.startActivity(Intent(Settings.ACTION_DEVICE_INFO_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) {
                host.toast("Could not open: ${e.javaClass.simpleName}")
            }
        })
        col.addView(Ui.button(ctx, "Export report to a file") {
            val rep = last
            if (rep == null) host.toast("Run the diagnosis first")
            else host.toast(host.exportReport("devoptions-report.txt", DevOptionsDiagnostics.reportText(host.profile, rep))
                ?.let { "Saved: $it" } ?: "Export failed")
        })

        col.addView(Ui.heading(ctx, "ADB command templates (run from a computer)"))
        col.addView(Ui.text(ctx,
            "This app never runs these. They need an authorised ADB connection, which requires USB/wireless debugging to be " +
                "on. This tool offers no way around Android authentication.", 18f, Ui.DIM))
        DevOptionsDiagnostics.adbTemplates.forEach { t ->
            col.addView(Ui.card(ctx,
                Ui.text(ctx, t.command, 18f, Ui.TEXT, mono = true),
                Ui.text(ctx, (if (t.modifiesDevice) "MODIFIES DEVICE - " else "read-only - ") + t.purpose +
                    (if (t.note.isNotEmpty()) "\n${t.note}" else ""), 17f,
                    if (t.modifiesDevice) Ui.WARN else Ui.DIM)))
        }

        col.addView(results)
        runDiagnosis()
        return Ui.scroll(col)
    }
}
