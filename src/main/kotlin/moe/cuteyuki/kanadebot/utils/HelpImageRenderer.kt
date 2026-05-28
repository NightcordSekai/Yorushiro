package moe.cuteyuki.kanadebot.utils

import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.GradientPaint
import java.awt.Graphics2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.imageio.ImageIO

/**
 * 命令帮助卡片渲染器（Material 3 深色风格）。
 */
object HelpImageRenderer {

    data class Entry(
        val name: String,
        val aliases: List<String>,
        val usage: String?,
        val description: String?,
        val restricted: Boolean = false,
    )

    private const val WIDTH = 880
    private const val PADDING = 32
    private const val HEADER_HEIGHT = 210
    private const val ENTRY_GAP = 10

    fun render(entries: List<Entry>): String {
        // 预测高度
        val image0 = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
        val g0 = image0.createGraphics()
        DesignSystem.applyHints(g0)
        val heights = entries.map { measureEntryHeight(g0, it) }
        g0.dispose()

        val contentHeight = heights.sum() + (entries.size - 1).coerceAtLeast(0) * ENTRY_GAP
        val height = HEADER_HEIGHT + contentHeight + PADDING + 48

        val image = BufferedImage(WIDTH, height, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        try {
            DesignSystem.applyHints(g)

            g.paint = GradientPaint(0f, 0f, DesignSystem.BG_TOP, 0f, height.toFloat(), DesignSystem.BG_BOTTOM)
            g.fillRect(0, 0, WIDTH, height)

            drawHeader(g, entries.size)

            var y = HEADER_HEIGHT
            entries.forEachIndexed { idx, entry ->
                val h = heights[idx]
                drawEntry(g, PADDING, y, WIDTH - PADDING * 2, h, entry, idx + 1)
                y += h + ENTRY_GAP
            }

            drawFooter(g, height)
        } finally {
            g.dispose()
        }

        val baos = ByteArrayOutputStream()
        ImageIO.write(image, "png", baos)
        return Base64.getEncoder().encodeToString(baos.toByteArray())
    }

    private fun drawHeader(g: Graphics2D, count: Int) {
        val orig = g.composite
        // 大装饰圆斑
        g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.20f)
        g.color = DesignSystem.PRIMARY
        g.fillOval(WIDTH - 220, -180, 370, 370)
        g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.10f)
        g.color = DesignSystem.ACCENT_CYAN
        g.fillOval(-100, 10, 250, 250)
        // 小点缀
        g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.15f)
        g.color = DesignSystem.GOLD
        g.fillOval(WIDTH - 80, HEADER_HEIGHT - 50, 40, 40)
        g.composite = orig

        // 标题
        g.color = DesignSystem.ON_SURFACE
        g.font = DesignSystem.uiFont(Font.BOLD, 40f)
        g.drawString("KanadeBot 命令帮助", PADDING, 74)

        g.color = DesignSystem.ON_SURFACE_VARIANT
        g.font = DesignSystem.uiFont(Font.PLAIN, 17f)
        g.drawString("共 $count 条命令 · 两种触发方式", PADDING, 104)

        // 触发方式
        drawTriggerChip(g, PADDING, 124, ". 前缀触发", DesignSystem.PRIMARY)
        drawTriggerChip(g, PADDING + 150, 124, "@ 机器人", DesignSystem.ACCENT_CYAN)
        g.color = DesignSystem.MUTED
        g.font = DesignSystem.uiFont(Font.PLAIN, 14f)
        g.drawString("例： .jrrp  或  @KanadeBot jrrp", PADDING, 168)

        // Admin 图例
        drawAdminBadge(g, PADDING, 186, scale = 0.85f)
        g.color = DesignSystem.MUTED
        g.font = DesignSystem.uiFont(Font.PLAIN, 13f)
        g.drawString("仅群管理/机器人管理员可用", PADDING + 40, 180)
    }

    private fun drawTriggerChip(g: Graphics2D, x: Int, y: Int, text: String, accent: Color) {
        g.font = DesignSystem.uiFont(Font.BOLD, 14f)
        val fm = g.fontMetrics
        val w = fm.stringWidth(text) + 28
        val h = 30
        val bg = DesignSystem.blend(accent, DesignSystem.SURFACE, 0.78f)
        g.color = bg
        g.fill(RoundRectangle2D.Float(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), h.toFloat(), h.toFloat()))
        g.color = accent
        g.fill(RoundRectangle2D.Float(x.toFloat() + 8, y.toFloat() + 9, 8f, 12f, 4f, 4f))
        g.color = DesignSystem.ON_SURFACE
        g.drawString(text, x + 24, y + h / 2 + fm.ascent / 2 - 2)
    }

    private fun measureEntryHeight(g: Graphics2D, entry: Entry): Int {
        val descLines = DesignSystem.wrap(g, entry.description ?: "", DesignSystem.uiFont(Font.PLAIN, 16f), WIDTH - PADDING * 2 - 80)
        val base = 62
        val descBlock = if (descLines.isEmpty()) 0 else descLines.size * 22 + 8
        val aliasBlock = if (entry.aliases.isNotEmpty()) 28 else 0
        return (base + descBlock + aliasBlock).coerceAtLeast(80)
    }

    private fun drawEntry(g: Graphics2D, x: Int, y: Int, w: Int, h: Int, entry: Entry, num: Int) {
        // 卡片 + 阴影
        DesignSystem.drawCard(g, x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), 24f)

        // 左侧序号
        val numX = x + 16
        val numW = 32
        val isAdmin = entry.restricted
        val accentColor = if (isAdmin) DesignSystem.GOLD else DesignSystem.PRIMARY

        g.color = DesignSystem.blend(accentColor, DesignSystem.SURFACE, 0.80f)
        g.fill(RoundRectangle2D.Float(numX.toFloat(), (y + 20).toFloat(), numW.toFloat(), (h - 40).toFloat(), 16f, 16f))
        g.color = accentColor
        g.font = DesignSystem.uiFont(Font.BOLD, 15f)
        val numStr = num.toString()
        val numFm = g.fontMetrics
        g.drawString(numStr, numX + (numW - numFm.stringWidth(numStr)) / 2, y + h / 2 + numFm.ascent / 2 - 3)

        // 左侧小色条
        g.color = accentColor
        g.fill(RoundRectangle2D.Float((x + 56).toFloat(), (y + 18).toFloat(), 3f, (h - 36).toFloat(), 2f, 2f))

        val left = x + 72
        var cursorY = y + 32

        // 命令名
        g.color = DesignSystem.ON_SURFACE
        g.font = DesignSystem.uiFont(Font.BOLD, 21f)
        val nameText = ".${entry.name}"
        g.drawString(nameText, left, cursorY)
        val nameWidth = g.fontMetrics.stringWidth(nameText)
        var afterNameX = left + nameWidth + 12

        // Admin 徽章
        if (isAdmin) {
            afterNameX = drawAdminBadge(g, afterNameX, cursorY - 17, scale = 0.85f) + 8
        }

        // 用法 chip
        val usage = entry.usage
        if (!usage.isNullOrBlank() && usage != entry.name) {
            drawCodeChip(g, afterNameX, cursorY - 17, ".$usage")
        }

        cursorY += 24

        // 描述
        val descLines = DesignSystem.wrap(g, entry.description ?: "", DesignSystem.uiFont(Font.PLAIN, 16f), WIDTH - PADDING * 2 - 80)
        if (descLines.isNotEmpty()) {
            g.color = DesignSystem.ON_SURFACE_VARIANT
            g.font = DesignSystem.uiFont(Font.PLAIN, 16f)
            for (line in descLines) {
                g.drawString(line, left, cursorY)
                cursorY += 22
            }
            cursorY += 4
        }

        // 别名
        if (entry.aliases.isNotEmpty()) {
            g.color = DesignSystem.MUTED
            g.font = DesignSystem.uiFont(Font.PLAIN, 12f)
            g.drawString("别名", left, cursorY + 16)
            var ax = left + 30
            for (alias in entry.aliases) {
                ax = drawAliasChip(g, ax, cursorY, alias) + 6
            }
        }
    }

    private fun drawAdminBadge(g: Graphics2D, x: Int, y: Int, scale: Float = 1f): Int {
        val text = "ADMIN"
        val fontSize = 12f * scale
        g.font = DesignSystem.uiFont(Font.BOLD, fontSize)
        val fm = g.fontMetrics
        val w = fm.stringWidth(text) + (18 * scale).toInt()
        val h = (22 * scale).toInt()
        g.paint = GradientPaint(x.toFloat(), y.toFloat(), DesignSystem.GOLD, x.toFloat(), (y + h).toFloat(), DesignSystem.ORANGE)
        g.fill(RoundRectangle2D.Float(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), h.toFloat(), h.toFloat()))
        g.color = DesignSystem.GOLD.darker()
        g.stroke = BasicStroke(1f)
        g.draw(RoundRectangle2D.Float(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), h.toFloat(), h.toFloat()))
        g.color = Color(0x2A, 0x1E, 0x00)
        g.drawString(text, x + (9 * scale).toInt(), y + h / 2 + fm.ascent / 2 - 2)
        return x + w
    }

    private fun drawCodeChip(g: Graphics2D, x: Int, y: Int, text: String) {
        g.font = DesignSystem.uiFont(Font.PLAIN, 14f)
        val fm = g.fontMetrics
        val w = fm.stringWidth(text) + 16
        val h = 24
        g.color = DesignSystem.SURFACE_HIGH
        g.fill(RoundRectangle2D.Float(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), 12f, 12f))
        g.color = DesignSystem.ACCENT_CYAN
        g.drawString(text, x + 8, y + h / 2 + fm.ascent / 2 - 2)
    }

    private fun drawAliasChip(g: Graphics2D, x: Int, y: Int, text: String): Int {
        g.font = DesignSystem.uiFont(Font.PLAIN, 13f)
        val fm = g.fontMetrics
        val w = fm.stringWidth(text) + 16
        val h = 24
        g.color = DesignSystem.SURFACE_HIGH
        g.fill(RoundRectangle2D.Float(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), h.toFloat(), h.toFloat()))
        g.color = DesignSystem.OUTLINE
        g.draw(RoundRectangle2D.Float(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), h.toFloat(), h.toFloat()))
        g.color = DesignSystem.ON_SURFACE_VARIANT
        g.drawString(text, x + 8, y + h / 2 + fm.ascent / 2 - 2)
        return x + w
    }

    private fun drawFooter(g: Graphics2D, height: Int) {
        g.color = DesignSystem.MUTED
        g.font = DesignSystem.uiFont(Font.PLAIN, 13f)
        val text = "Generated by KanadeBot · 命令变化时自动重绘"
        val fm = g.fontMetrics
        g.drawString(text, (WIDTH - fm.stringWidth(text)) / 2, height - 18)
    }
}
