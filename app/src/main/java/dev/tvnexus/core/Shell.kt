package dev.tvnexus.core

/**
 * Minimal, read-only helpers. This runs only as the app's own unprivileged user, so SELinux and
 * Android permissions still apply; a null result means "not set OR not visible to this app".
 */
object Shell {
    fun getprop(key: String): String? = try {
        val p = ProcessBuilder("getprop", key).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().use { it.readText() }.trim()
        p.waitFor()
        out.ifEmpty { null }
    } catch (e: Exception) {
        null
    }
}
