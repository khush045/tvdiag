package dev.tvnexus.core

import android.content.Context
import android.view.View
import dev.tvnexus.modules.devoptions.DevOptionsDoctorModule
import dev.tvnexus.modules.recovery.RecoveryCenterModule

/** Services the host (MainActivity) offers to every module. Modules never touch each other. */
interface ModuleHost {
    val context: Context
    val profile: DeviceProfile

    /** Run [work] off the UI thread, then deliver the result to [done] on the UI thread. */
    fun <T> background(work: () -> T, done: (T) -> Unit)

    /** Write a text report to the app's own external files dir. Returns the path, or null on failure. */
    fun exportReport(fileName: String, text: String): String?

    fun toast(message: String)
}

/**
 * Plugin contract. To add a tool: implement this interface, then add one line to [ModuleRegistry].
 * A module must be self-contained: it gets everything through [ModuleHost].
 */
interface ToolModule {
    val id: String
    val title: String
    val summary: String

    /** Honest per-feature support on the current device. Must be cheap and must not modify anything. */
    fun capabilities(ctx: Context, profile: DeviceProfile): List<Capability>

    fun createView(host: ModuleHost): View
}

object ModuleRegistry {
    // Only modules that actually work are listed. No placeholder entries.
    val modules: List<ToolModule> = listOf(
        DevOptionsDoctorModule(),
        RecoveryCenterModule()
    )
}
