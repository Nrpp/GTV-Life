package io.github.nrpp.gtvlife.ui.theme

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import io.github.nrpp.gtvlife.R

/** Color de acento elegido desde el móvil: primario + secundario para degradados. */
enum class Accent(val key: String, val primary: Int, val secondary: Int) {
    CYAN("cyan", 0xFF00E5FF.toInt(), 0xFF7C4DFF.toInt()),
    MAGENTA("magenta", 0xFFFF2BD6.toInt(), 0xFF6A5CFF.toInt()),
    AMBAR("ambar", 0xFFFFB300.toInt(), 0xFFFF3D6E.toInt()),
    VERDE("verde", 0xFF39FF88.toInt(), 0xFF00B8FF.toInt()),
    VIOLETA("violeta", 0xFFA67CFF.toInt(), 0xFF00E5FF.toInt());

    companion object {
        fun from(key: String) = entries.firstOrNull { it.key == key } ?: CYAN
    }
}

/** Paleta y tipografías comunes de la interfaz. */
object Palette {
    @Volatile
    var accent: Accent = Accent.CYAN

    val primary get() = accent.primary
    val secondary get() = accent.secondary

    const val BG0 = 0xFF04060C.toInt()
    const val BG1 = 0xFF0A1022.toInt()
    const val TEXT = 0xFFEAF2FF.toInt()
    const val TEXT_DIM = 0xFF8C9BBA.toInt()
    const val TEXT_FAINT = 0xFF4C5978.toInt()
    const val WARN = 0xFFFFB547.toInt()
    const val OK = 0xFF3DFFA2.toInt()
    const val DANGER = 0xFFFF4D6D.toInt()

    fun alpha(color: Int, a: Float): Int =
        Color.argb((a.coerceIn(0f, 1f) * 255).toInt(), Color.red(color), Color.green(color), Color.blue(color))

    private var cache: Map<Int, Typeface> = emptyMap()

    private fun font(context: Context, res: Int): Typeface =
        cache[res] ?: context.resources.getFont(res).also { cache = cache + (res to it) }

    fun display(c: Context) = font(c, R.font.orbitron)
    fun light(c: Context) = font(c, R.font.rajdhani_light)
    fun regular(c: Context) = font(c, R.font.rajdhani_regular)
    fun medium(c: Context) = font(c, R.font.rajdhani_medium)
    fun semibold(c: Context) = font(c, R.font.rajdhani_semibold)
    fun bold(c: Context) = font(c, R.font.rajdhani_bold)
}
