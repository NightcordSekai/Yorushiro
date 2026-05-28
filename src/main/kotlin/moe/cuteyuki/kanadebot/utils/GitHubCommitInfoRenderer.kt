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
 * GitHub commit 信息卡片渲染器（Material 3 深色）。
 */
object GitHubCommitInfoRenderer {

    data class Commit(
        val repo: String,
        val shortSha: String,
        val title: String,
        val body: String?,
        val authorName: String,
        val authorAvatar: BufferedImage?,
        val timestamp: String,
        val filesChanged: Int = -1,
        val additions: Int = -1,
        val deletions: Int = -1,
        val url: String? = null,
    )

    private const val WIDTH = 880
    private const val PADDING = 32

    fun render(commit: Commit): String {
        // 预测高度
        val image0 = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
        val g0 = image0.createGraphics()
        DesignSystem.applyHints(g0)
        val titleLines = DesignSystem.wrap(g0, commit.title, DesignSystem.uiFont(Font.BOLD, 24f), WIDTH - PADDING * 2 - 64)
        val bodyLines = if (!commit.body.isNullOrBlank()) {
            DesignSystem.wrap(g0, commit.body.trim(), DesignSystem.uiFont(Font.PLAIN, 16f), WIDTH - PADDING * 2 - 64, maxLines = 8)
        } else emptyList()
        g0.dispose()

        val headerHeight = 124
        val titleBlock = titleLines.size * 32
        val bodyBlock = if (bodyLines.isEmpty()) 0 else 8 + bodyLines.size * 22
        val statsBlock = if (commit.additions >= 0 || commit.deletions >= 0) 52 else 0
        val authorBlock = 72 + statsBlock
        val height = headerHeight + titleBlock + bodyBlock + authorBlock + PADDING + 28

        val image = BufferedImage(WIDTH, height, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        try {
            DesignSystem.applyHints(g)

            g.paint = GradientPaint(0f, 0f, DesignSystem.BG_TOP, 0f, height.toFloat(), DesignSystem.BG_BOTTOM)
            g.fillRect(0, 0, WIDTH, height)

            // 装饰
            val orig = g.composite
            g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.15f)
            g.color = DesignSystem.PRIMARY
            g.fillOval(WIDTH - 240, -180, 360, 360)
            g.composite = orig

            // Header
            g.color = DesignSystem.ACCENT_CYAN
            g.font = DesignSystem.uiFont(Font.BOLD, 13f)
            g.drawString("NEW COMMIT", PADDING, 50)

            g.color = DesignSystem.ON_SURFACE
            g.font = DesignSystem.uiFont(Font.BOLD, 30f)
            g.drawString(commit.repo, PADDING, 84)

            g.color = DesignSystem.MUTED
            g.font = DesignSystem.uiFont(Font.PLAIN, 14f)
            g.drawString(commit.timestamp, PADDING, 108)

            // 主卡片 + 阴影
            val cardX = PADDING
            val cardY = headerHeight
            val cardW = WIDTH - PADDING * 2
            val cardH = height - cardY - PADDING
            DesignSystem.drawCard(g, cardX.toFloat(), cardY.toFloat(), cardW.toFloat(), cardH.toFloat(), 28f)
            DesignSystem.drawLeftBar(g, cardX, cardY, cardH)

            val left = cardX + 36
            var y = cardY + 36

            // 标题
            g.color = DesignSystem.ON_SURFACE
            g.font = DesignSystem.uiFont(Font.BOLD, 24f)
            for (line in titleLines) {
                g.drawString(line, left, y + 22)
                y += 32
            }

            // Body
            if (bodyLines.isNotEmpty()) {
                y += 4
                g.color = DesignSystem.ON_SURFACE_VARIANT
                g.font = DesignSystem.uiFont(Font.PLAIN, 16f)
                for (line in bodyLines) {
                    g.drawString(line, left, y + 16)
                    y += 22
                }
            }

            y += 12

            // 分隔线
            g.color = DesignSystem.OUTLINE
            g.stroke = BasicStroke(1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            g.drawLine(left, y, cardX + cardW - 36, y)
            y += 16

            // Author + SHA
            g.color = DesignSystem.ON_SURFACE
            g.font = DesignSystem.uiFont(Font.BOLD, 17f)
            g.drawString(commit.authorName, left, y + 14)

            // SHA chip (right)
            drawShaChip(g, cardX + cardW - 36, y, commit.shortSha)

            // Diff stats bar
            if (commit.additions >= 0 || commit.deletions >= 0 || commit.filesChanged >= 0) {
                y += 26
                drawDiffBar(g, left, y, cardW - 72, commit)
            }
        } finally {
            g.dispose()
        }

        val baos = ByteArrayOutputStream()
        ImageIO.write(image, "png", baos)
        return Base64.getEncoder().encodeToString(baos.toByteArray())
    }

    private fun drawShaChip(g: Graphics2D, rightX: Int, y: Int, sha: String) {
        val text = sha
        g.font = DesignSystem.uiFont(Font.BOLD, 13f)
        val fm = g.fontMetrics
        val w = fm.stringWidth(text) + 20
        val h = 26
        val x = rightX - w
        g.color = DesignSystem.SURFACE_HIGH
        g.fill(RoundRectangle2D.Float(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), h.toFloat(), h.toFloat()))
        // 小 hash 图标色块
        g.color = DesignSystem.ACCENT_CYAN
        g.fill(RoundRectangle2D.Float(x.toFloat() + 6, y.toFloat() + 8, 3f, 10f, 2f, 2f))
        g.color = DesignSystem.ON_SURFACE_VARIANT
        g.drawString(text, x + 14, y + h / 2 + fm.ascent / 2 - 2)
    }

    /** 绘制彩色 diff 统计条（绿色 additions / 红色 deletions / 灰色 files）。 */
    private fun drawDiffBar(g: Graphics2D, x: Int, y: Int, maxW: Int, commit: Commit) {
        val barH = 10
        val total = (commit.additions.coerceAtLeast(0) + commit.deletions.coerceAtLeast(0)).coerceAtLeast(1)

        g.font = DesignSystem.uiFont(Font.BOLD, 13f)
        val fm = g.fontMetrics

        // Files count
        if (commit.filesChanged >= 0) {
            g.color = DesignSystem.ON_SURFACE_VARIANT
            val txt = "${commit.filesChanged} files"
            g.drawString(txt, x, y + fm.ascent)
        }

        // Bar bg
        val barY = y + 4
        val barX = x + 110
        val barW = (maxW - 120).coerceAtLeast(40)
        g.color = DesignSystem.SURFACE_HIGH
        g.fill(RoundRectangle2D.Float(barX.toFloat(), barY.toFloat(), barW.toFloat(), barH.toFloat(), barH.toFloat(), barH.toFloat()))

        // Additions bar
        val addW = barW * commit.additions.coerceAtLeast(0) / total
        if (addW > 0) {
            g.paint = GradientPaint(barX.toFloat(), 0f, DesignSystem.GREEN, barX.toFloat() + barW, 0f, DesignSystem.blend(DesignSystem.GREEN, DesignSystem.ACCENT_CYAN, 0.5f))
            g.fill(RoundRectangle2D.Float(barX.toFloat(), barY.toFloat(), addW.toFloat(), barH.toFloat(), barH.toFloat(), barH.toFloat()))
        }

        // Deletions bar
        val delW = barW * commit.deletions.coerceAtLeast(0) / total
        if (delW > 0) {
            val delX = barX + addW
            g.paint = GradientPaint(delX.toFloat(), 0f, DesignSystem.RED, delX.toFloat() + delW, 0f, DesignSystem.blend(DesignSystem.RED, DesignSystem.ORANGE, 0.3f))
            g.fill(RoundRectangle2D.Float(delX.toFloat(), barY.toFloat(), delW.toFloat(), barH.toFloat(), barH.toFloat(), barH.toFloat()))
        }

        // Legend
        val legendY = y + 4 + barH + 16
        if (commit.additions >= 0) {
            g.color = DesignSystem.GREEN
            g.fillOval(x, legendY - 8, 8, 8)
            g.color = DesignSystem.ON_SURFACE_VARIANT
            g.drawString("+${commit.additions} additions", x + 14, legendY)
        }
        if (commit.deletions >= 0) {
            val delX = if (commit.additions >= 0) x + 14 + fm.stringWidth("+${commit.additions} additions") + 24 else x
            g.color = DesignSystem.RED
            g.fillOval(delX, legendY - 8, 8, 8)
            g.color = DesignSystem.ON_SURFACE_VARIANT
            g.drawString("-${commit.deletions} deletions", delX + 14, legendY)
        }
    }
}
