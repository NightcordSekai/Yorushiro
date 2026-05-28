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
import java.time.LocalDate
import java.util.Base64
import javax.imageio.ImageIO

/**
 * 今日人品排行榜渲染器（Material 3 风格 / 深色配色）。
 */
object JrrpBoardRenderer {

    data class Row(
        val rank: Int,
        val userId: Long,
        val displayName: String,
        val value: Int,
        val avatar: BufferedImage? = null,
    )

    private const val WIDTH = 880
    private const val PADDING = 32

    fun render(rows: List<Row>): String {
        val headerHeight = 180
        val rowHeight = 80
        val rowGap = 10
        val footerHeight = 52
        val emptyHeight = if (rows.isEmpty()) 140 else 0

        val height = headerHeight +
            (if (rows.isEmpty()) emptyHeight else rows.size * rowHeight + (rows.size - 1).coerceAtLeast(0) * rowGap) +
            footerHeight + PADDING

        val image = BufferedImage(WIDTH, height, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        try {
            DesignSystem.applyHints(g)

            g.paint = GradientPaint(0f, 0f, DesignSystem.BG_TOP, 0f, height.toFloat(), DesignSystem.BG_BOTTOM)
            g.fillRect(0, 0, WIDTH, height)

            drawHeader(g, rows)

            var y = headerHeight
            if (rows.isEmpty()) {
                drawEmptyState(g, y, emptyHeight)
                y += emptyHeight
            } else {
                for (row in rows) {
                    drawRow(g, PADDING, y, WIDTH - PADDING * 2, rowHeight, row)
                    y += rowHeight + rowGap
                }
            }

            drawFooter(g, height)
        } finally {
            g.dispose()
        }

        val baos = ByteArrayOutputStream()
        ImageIO.write(image, "png", baos)
        return Base64.getEncoder().encodeToString(baos.toByteArray())
    }

    private fun drawHeader(g: Graphics2D, rows: List<Row>) {
        // 装饰圆斑
        val orig = g.composite
        g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.20f)
        g.color = DesignSystem.PRIMARY
        g.fillOval(WIDTH - 220, -160, 340, 340)
        g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.12f)
        g.color = DesignSystem.ACCENT_CYAN
        g.fillOval(-100, 20, 240, 240)
        g.composite = orig

        // 标题
        g.color = DesignSystem.ON_SURFACE
        g.font = DesignSystem.uiFont(Font.BOLD, 40f)
        g.drawString("今日人品排行榜", PADDING, 78)

        // 副标题 + stats chips
        g.color = DesignSystem.ON_SURFACE_VARIANT
        g.font = DesignSystem.uiFont(Font.PLAIN, 17f)
        val date = LocalDate.now().toString()
        g.drawString("$date · 共 ${rows.size} 人参与", PADDING, 108)

        if (rows.isNotEmpty()) {
            val avg = rows.sumOf { it.value }.toDouble() / rows.size
            val top = rows.first().value
            val maxY = 132
            drawStatChip(g, PADDING, maxY, "平均 ${"%.1f".format(avg)}", DesignSystem.ACCENT_CYAN)
            drawStatChip(g, PADDING + 130, maxY, "最高 $top", DesignSystem.GOLD)
            // 分数分布小标签
            val highCount = rows.count { it.value >= 90 }
            if (highCount > 0) {
                drawStatChip(g, PADDING + 260, maxY, "欧皇 ×$highCount", DesignSystem.SCORE_TOP)
            }
        }
    }

    private fun drawStatChip(g: Graphics2D, x: Int, y: Int, text: String, accent: Color) {
        g.font = DesignSystem.uiFont(Font.BOLD, 13f)
        val fm = g.fontMetrics
        val w = fm.stringWidth(text) + 20
        val h = 26
        g.color = DesignSystem.blend(accent, DesignSystem.SURFACE, 0.80f)
        g.fill(RoundRectangle2D.Float(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), h.toFloat(), h.toFloat()))
        // 左侧小色点
        g.color = accent
        g.fillOval(x + 8, y + 9, 8, 8)
        g.color = DesignSystem.ON_SURFACE
        g.drawString(text, x + 22, y + h / 2 + fm.ascent / 2 - 2)
    }

    private fun drawEmptyState(g: Graphics2D, y: Int, h: Int) {
        // 装饰
        val orig = g.composite
        g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.08f)
        g.color = DesignSystem.MUTED
        g.fillOval(WIDTH / 2 - 60, y + 10, 120, 120)
        g.composite = orig

        g.color = DesignSystem.MUTED
        g.font = DesignSystem.uiFont(Font.PLAIN, 18f)
        val text = "今天还没有人查过人品哦~ 发送 .jrrp 试试"
        val fm = g.fontMetrics
        g.drawString(text, (WIDTH - fm.stringWidth(text)) / 2, y + h / 2 + fm.ascent / 2 - 4)
    }

    private fun drawRow(g: Graphics2D, x: Int, y: Int, w: Int, h: Int, row: Row) {
        // 前三名 podium 光晕
        if (row.rank <= 3) {
            val glowColor = when (row.rank) {
                1 -> DesignSystem.GOLD
                2 -> DesignSystem.SILVER
                else -> DesignSystem.BRONZE
            }
            val orig = g.composite
            g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.06f)
            g.color = glowColor
            g.fill(RoundRectangle2D.Float(x.toFloat() - 3, y.toFloat() - 3, w.toFloat() + 6, h.toFloat() + 6, 30f, 30f))
            g.composite = orig
        }

        // 卡片 + 阴影
        DesignSystem.drawCard(g, x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), 28f)

        // 左侧 rank 区域
        val rankAreaX = x + 4
        val rankAreaW = 56
        g.color = if (row.rank <= 3) DesignSystem.SURFACE_HIGH else Color(0, 0, 0, 0)
        g.fill(RoundRectangle2D.Float(rankAreaX.toFloat(), (y + 10).toFloat(), rankAreaW.toFloat(), (h - 20).toFloat(), 22f, 22f))

        // Rank 数字
        val rankColor = when (row.rank) {
            1 -> DesignSystem.GOLD
            2 -> DesignSystem.SILVER
            3 -> DesignSystem.BRONZE
            else -> DesignSystem.MUTED
        }
        g.color = rankColor
        g.font = DesignSystem.uiFont(Font.BOLD, if (row.rank <= 3) 24f else 20f)
        val rankText = if (row.rank <= 3) row.rank.toString() else "#${row.rank}"
        val rankFm = g.fontMetrics
        g.drawString(rankText, rankAreaX + (rankAreaW - rankFm.stringWidth(rankText)) / 2, y + h / 2 + rankFm.ascent / 2 - 3)

        // 头像
        val avatarSize = 52
        val avatarX = x + 72
        val avatarY = y + (h - avatarSize) / 2
        drawAvatar(g, avatarX, avatarY, avatarSize, row)

        // 信息
        val textX = avatarX + avatarSize + 18
        g.color = DesignSystem.ON_SURFACE
        g.font = DesignSystem.uiFont(Font.BOLD, 20f)
        val nameMaxW = w - (textX - x) - 280
        g.drawString(DesignSystem.ellipsize(g, row.displayName, nameMaxW), textX, y + 34)
        g.color = DesignSystem.MUTED
        g.font = DesignSystem.uiFont(Font.PLAIN, 13f)
        g.drawString("QQ ${row.userId}", textX, y + 54)

        // 分数 + 进度条（右对齐）
        val score = row.value
        val scoreColor = scoreColor(score)
        val scoreText = score.toString()
        g.font = DesignSystem.uiFont(Font.BOLD, 40f)
        val scoreFm = g.fontMetrics
        val scoreW = scoreFm.stringWidth(scoreText)
        val scoreX = x + w - 24 - scoreW
        g.color = scoreColor
        g.drawString(scoreText, scoreX, y + h / 2 + scoreFm.ascent / 2 - 10)

        // 进度条
        val barW = 160
        val barH = 10
        val barX = x + w - 24 - barW
        val barY = y + h - 16
        // 轨道
        g.color = DesignSystem.SURFACE_HIGH
        g.fill(RoundRectangle2D.Float(barX.toFloat(), barY.toFloat(), barW.toFloat(), barH.toFloat(), barH.toFloat(), barH.toFloat()))
        val fillW = (barW * score / 100f).coerceAtLeast(if (score > 0) 6f else 0f)
        if (fillW > 0f) {
            // 进度条发光
            val glowOrig = g.composite
            g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.25f)
            g.color = scoreColor
            g.fill(RoundRectangle2D.Float(barX.toFloat() - 1, barY.toFloat() - 1, fillW + 2, barH.toFloat() + 2, barH.toFloat() + 2, barH.toFloat() + 2))
            g.composite = glowOrig
            // 填充
            g.paint = GradientPaint(barX.toFloat(), 0f, scoreColor, barX.toFloat() + barW, 0f, DesignSystem.blend(scoreColor, DesignSystem.PRIMARY, 0.3f))
            g.fill(RoundRectangle2D.Float(barX.toFloat(), barY.toFloat(), fillW, barH.toFloat(), barH.toFloat(), barH.toFloat()))
        }
    }

    private fun drawAvatar(g: Graphics2D, x: Int, y: Int, size: Int, row: Row) {
        val ringColor = when (row.rank) {
            1 -> DesignSystem.GOLD
            2 -> DesignSystem.SILVER
            3 -> DesignSystem.BRONZE
            else -> null
        }

        // 前三名光环
        if (ringColor != null) {
            val glowOrig = g.composite
            g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.20f)
            g.color = ringColor
            g.fill(Ellipse2D.Float(x.toFloat() - 5, y.toFloat() - 5, size + 10f, size + 10f))
            g.composite = glowOrig

            g.color = ringColor
            g.fill(Ellipse2D.Float(x.toFloat() - 2.5f, y.toFloat() - 2.5f, size + 5f, size + 5f))
        }

        val avatarShape = Ellipse2D.Float(x.toFloat(), y.toFloat(), size.toFloat(), size.toFloat())
        val oldClip = g.clip
        g.clip = avatarShape

        if (row.avatar != null) {
            g.drawImage(row.avatar, x, y, size, size, null)
        } else {
            val hue = ((row.userId xor (row.userId ushr 16)) and 0xFFFFL).toFloat() / 0xFFFF
            val base = Color.getHSBColor(hue, 0.45f, 0.78f)
            val deep = Color.getHSBColor(hue, 0.55f, 0.55f)
            g.paint = GradientPaint(x.toFloat(), y.toFloat(), base, x.toFloat(), (y + size).toFloat(), deep)
            g.fillRect(x, y, size, size)
            val initial = row.displayName.firstOrNull()?.toString()?.uppercase() ?: "?"
            g.color = Color(0xFF, 0xFF, 0xFF, 0xE6)
            g.font = DesignSystem.uiFont(Font.BOLD, size * 0.48f)
            val fm = g.fontMetrics
            g.drawString(initial, x + (size - fm.stringWidth(initial)) / 2, y + (size + fm.ascent) / 2 - 4)
        }

        g.clip = oldClip
        g.color = if (ringColor != null) ringColor else DesignSystem.OUTLINE
        g.stroke = BasicStroke(if (ringColor != null) 1.8f else 1.2f)
        g.draw(avatarShape)
    }

    private fun drawFooter(g: Graphics2D, height: Int) {
        g.color = DesignSystem.MUTED
        g.font = DesignSystem.uiFont(Font.PLAIN, 13f)
        val text = "Generated by KanadeBot · 每自然日刷新"
        val fm = g.fontMetrics
        g.drawString(text, (WIDTH - fm.stringWidth(text)) / 2, height - 20)
    }

    private fun scoreColor(value: Int): Color = when {
        value >= 90 -> DesignSystem.SCORE_TOP
        value >= 60 -> DesignSystem.SCORE_HIGH
        value >= 21 -> DesignSystem.SCORE_MID
        else -> DesignSystem.SCORE_LOW
    }
}
