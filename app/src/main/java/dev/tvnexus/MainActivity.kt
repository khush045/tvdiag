package dev.tvnexus

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import dev.tvnexus.core.DeviceProfile
import dev.tvnexus.core.ModuleHost
import dev.tvnexus.core.ModuleRegistry
import dev.tvnexus.core.ToolModule
import dev.tvnexus.ui.Ui
import java.io.File
import java.util.concurrent.Executors

class MainActivity : Activity(), ModuleHost {

    override val context: Context get() = this
    override lateinit var profile: DeviceProfile

    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private lateinit var content: FrameLayout
    private var openModule: ToolModule? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        profile = DeviceProfile.detect(this)
        content = FrameLayout(this).apply { setBackgroundColor(Ui.BG) }
        setContentView(content)
        showHome()
    }

    override fun onDestroy() {
        worker.shutdownNow()
        super.onDestroy()
    }

    private fun showHome() {
        openModule = null
        val col = Ui.column(this)
        col.addView(Ui.heading(this, "TV NEXUS"))
        col.addView(Ui.card(this, Ui.text(this, profile.summary(), 18f, mono = true)))
        col.addView(Ui.card(this, Ui.text(this,
            "Honesty note: this build has only ${ModuleRegistry.modules.size} modules. Each feature states whether it is " +
                "supported, needs ADB, or needs system privileges on THIS device.", 17f, Ui.DIM)))
        col.addView(Ui.heading(this, "Modules"))
        var first: View? = null
        ModuleRegistry.modules.forEach { m ->
            val b = Ui.button(this, "${m.title}  -  ${m.summary}") { open(m) }
            if (first == null) first = b
            col.addView(b)
        }
        content.removeAllViews()
        content.addView(Ui.scroll(col))
        first?.post { first?.requestFocus() }
    }

    private fun open(m: ToolModule) {
        openModule = m
        content.removeAllViews()
        val view = m.createView(this)
        content.addView(view)
        view.post { view.requestFocus() }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (openModule != null) showHome() else super.onBackPressed()
    }

    override fun <T> background(work: () -> T, done: (T) -> Unit) {
        worker.execute {
            val result = try { work() } catch (e: Throwable) { null }
            main.post { @Suppress("UNCHECKED_CAST") if (result != null) done(result as T) }
        }
    }

    override fun exportReport(fileName: String, text: String): String? = try {
        val dir = getExternalFilesDir(null) ?: filesDir
        val f = File(dir, fileName)
        f.writeText(text)
        f.absolutePath
    } catch (e: Exception) {
        null
    }

    override fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
