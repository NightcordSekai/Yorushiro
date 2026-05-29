
package moe.cuteyuki.kanadebot.utils

import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.GraphicsEnvironment
import java.awt.RenderingHints
import java.awt.geom.RoundRectangle2D

/**
 * 共享设计系统：颜色 / 字体 / 渲染提示 / 通用绘制工具。
 *
 * 所有 renderer 统一引用这里的资源，避免每张卡片重复定义同一套调色板与工具函数。
 */
object DesignSystem {

    // ---- Material 3 dark palette ----

    // Surface family
    @JvmField val BG_TOP = Color(0x10, 0x14, 0x1B)
    @JvmField val BG_BOTTOM = Color(0x16, 0x1B, 0x24)
    @JvmField val SURFACE = Color(0x1E, 0x24, 0x2E)
    @JvmField val SURFACE_HIGH = Color(0x26, 0x2D, 0x39)
    @JvmField val OUTLINE = Color(0x3A, 0x42, 0x4F)

    // Text family
    @JvmField val ON_SURFACE = Color(0xE6, 0xE1, 0xE5)
    @JvmField val ON_SURFACE_VARIANT = Color(0xC9, 0xC5, 0xD0)
    @JvmField val MUTED = Color(0x90, 0x8B, 0x9A)

    // Accent family
    @JvmField val PRIMARY = Color(0xCF, 0xBC, 0xFF)
    @JvmField val PRIMARY_DEEP = Color(0x9A, 0x82, 0xDB)
    @JvmField val ACCENT_CYAN = Color(0x8B, 0xE9, 0xFD)
    @JvmField val ACCENT_PINK = Color(0xFF, 0xB1, 0xC1)
    @JvmField val GOLD = Color(0xFF, 0xD7, 0x6B)
    @JvmField val GREEN = Color(0x7E, 0xE0, 0x9D)
    @JvmField val ORANGE = Color(0xFF, 0xB7, 0x4D)
    @JvmField val RED = Color(0xFF, 0x8A, 0x80)

    // JrrpBoard score colors
    @JvmField val SILVER = Color(0xCF, 0xD8, 0xDC)
    @JvmField val BRONZE = Color(0xFF, 0xA8, 0x7A)
    @JvmField val SCORE_LOW = Color(0xFF, 0x8A, 0x80)
    @JvmField val SCORE_MID = Color(0xFF, 0xB7, 0x4D)
    @JvmField val SCORE_HIGH = Color(0x8B, 0xE9, 0xFD)
    @JvmField val SCORE_TOP = Color(0xFF, 0xD7, 0x6B)

    // ---- Typography ----

    private val uiFontFamily: String by lazy {
        val candidates = listOf(
            "PingFang SC",
            "Source Han Sans SC",
            "Noto Sans CJK SC",
            "Microsoft YaHei",
            "MiSans",
            "HarmonyOS Sans SC",
            "WenQuanYi Micro Hei"
        )
        val available = GraphicsEnvironment.getLocalGraphicsEnvironment()
            .availableFontFamilyNames.toHashSet()
        candidates.firstOrNull { it in available } ?: Font.SANS_SERIF
    }

    @JvmStatic
    fun uiFont(style: Int, size: Float): Font =
        Font(uiFontFamily, style, size.toInt()).deriveFont(size)

    private val isMacOS = System.getProperty("os.name").lowercase().contains("mac")

    /** MiSans Regular — macOS 使用 "MiSans-Regular"，其它系统使用 "MiSans Regular"。 */
    @JvmStatic
    fun miSansRegular(size: Int): Font =
        Font(if (isMacOS) "MiSans-Regular" else "MiSans Regular", Font.PLAIN, size)

    /** MiSans Bold — macOS 使用 "MiSans-Bold"，其它系统使用 "MiSans Bold"。 */
    @JvmStatic
    fun miSansBold(size: Int): Font =
        Font(if (isMacOS) "MiSans-Bold" else "MiSans Bold", Font.PLAIN, size)

    // ---- Rendering ----

    @JvmStatic
    fun applyHints(g: Graphics2D) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
    }

    // ---- Color utility ----

    @JvmStatic
    fun blend(a: Color, b: Color, t: Float): Color {
        val tt = t.coerceIn(0f, 1f)
        return Color(
            (a.red * (1 - tt) + b.red * tt).toInt().coerceIn(0, 255),
            (a.green * (1 - tt) + b.green * tt).toInt().coerceIn(0, 255),
            (a.blue * (1 - tt) + b.blue * tt).toInt().coerceIn(0, 255),
            (a.alpha * (1 - tt) + b.alpha * tt).toInt().coerceIn(0, 255),
        )
    }

    // ---- Text utilities ----

    /** 按最大像素宽度自动换行，可限制最大行数（超出行尾加 …）。 */
    @JvmStatic
    fun wrap(
        g: Graphics2D,
        text: String,
        font: Font,
        maxWidth: Int,
        maxLines: Int = Int.MAX_VALUE
    ): List<String> {
        val saved = g.font
        g.font = font
        val fm = g.fontMetrics
        var out = mutableListOf<String>()
        for (rawLine in text.lines()) {
            if (out.size >= maxLines) break
            if (rawLine.isEmpty()) {
                out += ""
                continue
            }
            var current = StringBuilder()
            for (ch in rawLine) {
                current.append(ch)
                if (fm.stringWidth(current.toString()) > maxWidth) {
                    current.deleteCharAt(current.length - 1)
                    out += current.toString()
                    if (out.size >= maxLines) break
                    current = StringBuilder()
                    current.append(ch)
                }
            }
            if (out.size < maxLines && current.isNotEmpty()) out += current.toString()
        }
        g.font = saved
        if (out.size > maxLines) {
            val truncated = out.take(maxLines).toMutableList()
            val last = truncated.last()
            if (last.length > 1)
                truncated[truncated.lastIndex] = last.substring(0, last.length - 1) + "…"
            return truncated
        }
        return out
    }

    /** 预估文字将占据的行数，与 [wrap] 保持一致的测量方式。 */
    @JvmStatic
    fun measureLines(
        g: Graphics2D,
        text: String,
        font: Font,
        maxWidth: Int,
        maxLines: Int
    ): Int {
        val saved = g.font
        g.font = font
        val fm = g.fontMetrics
        var lines = 0
        for (rawLine in text.lines()) {
            if (lines >= maxLines) break
            var current = StringBuilder()
            for (ch in rawLine) {
                current.append(ch)
                if (fm.stringWidth(current.toString()) > maxWidth) {
                    lines++
                    if (lines >= maxLines) break
                    current = StringBuilder().append(ch)
                }
            }
            if (current.isNotEmpty() && lines < maxLines) lines++
        }
        g.font = saved
        return lines.coerceAtLeast(1)
    }

    /** 文字过长时尾部加 …，保证不超出 maxWidth。 */
    @JvmStatic
    fun ellipsize(g: Graphics2D, text: String, maxWidth: Int): String {
        val fm = g.fontMetrics
        if (fm.stringWidth(text) <= maxWidth) return text
        val ellipsis = "…"
        var lo = 0
        var hi = text.length
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            val candidate = text.substring(0, mid) + ellipsis
            if (fm.stringWidth(candidate) <= maxWidth) lo = mid else hi = mid - 1
        }
        return if (lo <= 0) ellipsis else text.substring(0, lo) + ellipsis
    }

    /** 大于 1000 的数字格式化为 "1.2k" 等。 */
    @JvmStatic
    fun formatCount(n: Int): String = when {
        n >= 1000 -> "%.1fk".format(n / 1000.0)
        else -> n.toString()
    }

    // ---- Drawing primitives ----

    /** 绘制软阴影（多层半透明填充叠加，模拟 elevation）。 */
    @JvmStatic
    fun drawShadow(g: Graphics2D, x: Float, y: Float, w: Float, h: Float, radius: Float, elevation: Int = 4) {
        val orig = g.composite
        for (i in elevation downTo 1) {
            val alpha = 0.04f * i
            val offset = i * 1.5f
            g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha)
            g.color = Color(0, 0, 0)
            g.fill(RoundRectangle2D.Float(x + offset, y + offset, w, h, radius, radius))
        }
        g.composite = orig
    }

    /** 绘制带阴影+背景+描边的卡片。 */
    @JvmStatic
    fun drawCard(
        g: Graphics2D,
        x: Float, y: Float, w: Float, h: Float,
        radius: Float = 28f,
        fill: Color = SURFACE,
        stroke: Color = OUTLINE,
        elevation: Int = 4
    ) {
        drawShadow(g, x, y, w, h, radius, elevation)
        val card = RoundRectangle2D.Float(x, y, w, h, radius, radius)
        g.color = fill
        g.fill(card)
        if (stroke.alpha > 0) {
            g.color = stroke
            g.stroke = BasicStroke(1f)
            g.draw(card)
        }
    }

    /** 绘制左侧装饰色条（Material 3 卡片风格）。 */
    @JvmStatic
    fun drawLeftBar(g: Graphics2D, cardX: Int, cardY: Int, cardH: Int, color: Color = PRIMARY) {
        g.color = color
        g.fill(RoundRectangle2D.Float((cardX + 14).toFloat(), (cardY + 16).toFloat(), 4f, (cardH - 32).toFloat(), 4f, 4f))
    }
}
