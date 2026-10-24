package ch.puc.blocker.domain

/** Package name from a Play Store link ("…details?id=com.zhiliaoapp.musically") or a bare package name. */
object PlayLink {
    private val id = Regex("""[?&]id=([A-Za-z][\w]*(?:\.[A-Za-z_][\w]*)+)""")
    private val bare = Regex("""[A-Za-z][\w]*(?:\.[A-Za-z_][\w]*)+""")

    fun packageName(input: String): String? {
        val text = input.trim()
        return id.find(text)?.groupValues?.get(1) ?: text.takeIf { bare.matches(it) }
    }
}
