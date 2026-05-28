package moe.cuteyuki.kanadebot.utils

import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.GradientPaint
import java.awt.Graphics2D
import java.awt.geom.Ellipse2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.imageio.ImageIO

/**
 * 历史上的今天渲染器（Material 3 深色风格）。
 */
object HistoryRenderer {

    data class Event(
        val year: Int,
        val title: String,
        val description: String?,
        val category: String?,
        val importance: Int,
    )

    private const val WIDTH = 880
    private const val PADDING = 32

    /** 根据重要程度返回强调色（用于 timeline dot / 左边条）。 */
    private fun importanceColor(importance: Int): Color = when {
        importance >= 9 -> DesignSystem.GOLD
        importance >= 7 -> DesignSystem.ORANGE
        importance >= 5 -> DesignSystem.ACCENT_CYAN
        else -> DesignSystem.MUTED
    }

    fun render(date: String, events: List<Event>): String {
        // 预计算每个事件高度
        val image0 = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
        val g0 = image0.createGraphics()
        DesignSystem.applyHints(g0)

        val cardW = WIDTH - PADDING * 2
        val textAreaW = cardW - 100 - PADDING // 留出 timeline 区域

        val titleFont = DesignSystem.uiFont(Font.BOLD, 18f)
        val descFont = DesignSystem.uiFont(Font.PLAIN, 15f)

        val eventHeights = events.map { event ->
            var h = 0
            h += DesignSystem.measureLines(g0, event.title, titleFont, textAreaW - 12, maxLines = 2) * 28
            if (!event.category.isNullOrBlank()) h += 28
            if (!event.description.isNullOrBlank()) {
                h += DesignSystem.measureLines(g0, event.description.trim(), descFont, textAreaW, maxLines = 3) * 22
            }
            h + 40 // padding
        }
        g0.dispose()

        val headerHeight = 184
        val eventsBlock = eventHeights.sum() + (events.size - 1).coerceAtLeast(0) * 4
        val cardTopPad = 24
        val cardH = cardTopPad + eventsBlock - 4
        val height = headerHeight + cardH + PADDING + 8

        val image = BufferedImage(WIDTH, height, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        try {
            DesignSystem.applyHints(g)

            g.paint = GradientPaint(0f, 0f, DesignSystem.BG_TOP, 0f, height.toFloat(), DesignSystem.BG_BOTTOM)
            g.fillRect(0, 0, WIDTH, height)

            // 装饰
            val orig = g.composite
            g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.20f)
            g.color = DesignSystem.PRIMARY
            g.fillOval(WIDTH - 240, -180, 360, 360)
            g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.10f)
            g.color = DesignSystem.ACCENT_CYAN
            g.fillOval(-120, headerHeight - 220, 280, 280)
            g.composite = orig

            // Header
            g.color = DesignSystem.ON_SURFACE
            g.font = DesignSystem.uiFont(Font.BOLD, 38f)
            g.drawString("历史上的今天", PADDING, 74)

            g.color = DesignSystem.ON_SURFACE_VARIANT
            g.font = DesignSystem.uiFont(Font.PLAIN, 18f)
            g.drawString("$date", PADDING, 108)

            // 重要性图例
            drawLegendChip(g, PADDING, 132, "重大", DesignSystem.GOLD)
            drawLegendChip(g, PADDING + 80, 132, "重要", DesignSystem.ORANGE)
            drawLegendChip(g, PADDING + 160, 132, "一般", DesignSystem.ACCENT_CYAN)

            // 主卡片 + 阴影
            val cardX = PADDING
            val cardY = headerHeight
            DesignSystem.drawCard(g, cardX.toFloat(), cardY.toFloat(), cardW.toFloat(), cardH.toFloat(), 28f)
            DesignSystem.drawLeftBar(g, cardX, cardY, cardH, DesignSystem.PRIMARY)

            // Timeline dots
            var y = cardY + 24
            for ((index, event) in events.withIndex()) {
                y = drawEvent(g, cardX, y, cardW, event, index, events.size, textAreaW)
            }

            drawFooter(g, height)
        } finally {
            g.dispose()
        }

        val baos = ByteArrayOutputStream()
        ImageIO.write(image, "png", baos)
        return Base64.getEncoder().encodeToString(baos.toByteArray())
    }

    private fun drawLegendChip(g: Graphics2D, x: Int, y: Int, text: String, color: Color) {
        g.font = DesignSystem.uiFont(Font.PLAIN, 12f)
        val fm = g.fontMetrics
        val w = fm.stringWidth(text) + 28
        val h = 22
        g.color = DesignSystem.SURFACE_HIGH
        g.fill(RoundRectangle2D.Float(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), h.toFloat(), h.toFloat()))
        g.color = color
        g.fillOval(x + 8, y + 7, 8, 8)
        g.color = DesignSystem.ON_SURFACE_VARIANT
        g.drawString(text, x + 22, y + h / 2 + fm.ascent / 2 - 2)
    }

    private fun drawEvent(
        g: Graphics2D,
        cardX: Int,
        y: Int,
        cardW: Int,
        event: Event,
        index: Int,
        total: Int,
        textAreaW: Int,
    ): Int {
        val left = cardX + 40
        val timelineX = cardX + 32
        val dotColor = importanceColor(event.importance)
        var cursor = y

        // Timeline dot
        g.color = dotColor
        g.fill(Ellipse2D.Float(timelineX.toFloat() - 5, cursor.toFloat() + 4, 10f, 10f))
        // 发光
        val orig = g.composite
        g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.15f)
        g.fill(Ellipse2D.Float(timelineX.toFloat() - 8, cursor.toFloat() + 1, 16f, 16f))
        g.composite = orig

        // 年份 + 重要性指示条
        val yearStr = "${event.year}"
        g.color = dotColor
        g.font = DesignSystem.uiFont(Font.BOLD, 15f)
        val yearFm = g.fontMetrics
        val yearW = yearFm.stringWidth(yearStr)
        // 年份背景 pill
        g.color = DesignSystem.blend(dotColor, DesignSystem.SURFACE, 0.75f)
        g.fill(RoundRectangle2D.Float(left.toFloat(), cursor.toFloat(), (yearW + 18).toFloat(), 24f, 12f, 12f))
        g.color = dotColor
        g.drawString(yearStr, left + 9, cursor + 17)

        // 重要性星级
        if (event.importance >= 7) {
            val stars = if (event.importance >= 9) "★★★" else "★★"
            g.font = DesignSystem.uiFont(Font.PLAIN, 11f)
            val starW = g.fontMetrics.stringWidth(stars)
            g.drawString(stars, left + yearW + 22, cursor + 17)
        }

        // 标题
        g.color = DesignSystem.ON_SURFACE
        g.font = DesignSystem.uiFont(Font.BOLD, 18f)
        val titleLines = DesignSystem.wrap(g, event.title, DesignSystem.uiFont(Font.BOLD, 18f), textAreaW - 12, maxLines = 2)
        for ((li, line) in titleLines.withIndex()) {
            g.drawString(line, left, cursor + 40 + li * 28)
        }
        cursor += 40 + titleLines.size * 28

        // 分类标签
        if (!event.category.isNullOrBlank()) {
            g.font = DesignSystem.uiFont(Font.BOLD, 11f)
            val fm = g.fontMetrics
            val w = fm.stringWidth(event.category) + 18
            val h = 22
            g.color = DesignSystem.blend(dotColor, DesignSystem.SURFACE, 0.70f)
            g.fill(RoundRectangle2D.Float(left.toFloat(), cursor.toFloat(), w.toFloat(), h.toFloat(), h.toFloat(), h.toFloat()))
            g.color = dotColor
            g.drawString(event.category, left + 9, cursor + h / 2 + fm.ascent / 2 - 2)
            cursor += 28
        }

        // 描述
        if (!event.description.isNullOrBlank()) {
            val descLines = DesignSystem.wrap(g, event.description.trim(), DesignSystem.uiFont(Font.PLAIN, 15f), textAreaW, maxLines = 3)
            g.color = DesignSystem.ON_SURFACE_VARIANT
            g.font = DesignSystem.uiFont(Font.PLAIN, 15f)
            for (line in descLines) {
                g.drawString(line, left, cursor + 18)
                cursor += 22
            }
        }

        cursor += 4

        // 连接线（最后一条不画）
        if (index < total - 1) {
            g.color = DesignSystem.OUTLINE
            g.stroke = BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            g.drawLine(timelineX, y + 16, timelineX, cursor + 8)
        }

        return cursor
    }

    private fun drawFooter(g: Graphics2D, height: Int) {
        g.color = DesignSystem.MUTED
        g.font = DesignSystem.uiFont(Font.PLAIN, 13f)
        val text = "Generated by KanadeBot · 历史数据来自 uapis.cn"
        val fm = g.fontMetrics
        g.drawString(text, (WIDTH - fm.stringWidth(text)) / 2, height - 20)
    }
}
