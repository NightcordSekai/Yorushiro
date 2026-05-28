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
 * GitHub 仓库概览卡片渲染器（Material 3 深色）。
 */
object GitHubRepoInfoRenderer {

    data class RepoInfo(
        val fullName: String,
        val description: String?,
        val language: String?,
        val stars: Int,
        val forks: Int,
        val openIssues: Int,
        val defaultBranch: String?,
        val homepage: String?,
        val ownerLogin: String,
        val ownerAvatar: BufferedImage?,
        val isPrivate: Boolean,
        val isFork: Boolean,
        val isArchived: Boolean,
        val updatedAt: String?,
        val latestCommit: LatestCommit?,
    )

    data class LatestCommit(
        val shortSha: String,
        val title: String,
        val authorName: String,
        val timestamp: String,
    )

    /** 编程语言与对应 Material 色相映射。 */
    private fun languageColor(lang: String?): Color = when (lang?.lowercase()) {
        "kotlin" -> Color(0xE2, 0x44, 0x62) // Kotlin purple
        "java" -> Color(0xB0, 0x72, 0x19)   // Java brown
        "python" -> Color(0x35, 0x72, 0xA5)  // Python blue
        "javascript", "typescript" -> Color(0xF1, 0xE0, 0x5A) // JS yellow
        "go" -> Color(0x00, 0xAD, 0xD8)     // Go cyan
        "rust" -> Color(0xDE, 0xA5, 0x84)   // Rust orange
        "swift" -> Color(0xF0, 0x51, 0x38)  // Swift red
        "c++", "cpp" -> Color(0xF3, 0x4B, 0x7D)
        "c" -> Color(0x55, 0x55, 0x55)
        "ruby" -> Color(0x70, 0x1A, 0x15)
        "php" -> Color(0x4F, 0x5E, 0x95)
        else -> DesignSystem.ACCENT_CYAN
    }

    private const val WIDTH = 880
    private const val PADDING = 32

    fun render(info: RepoInfo): String {
        // 预测高度
        val image0 = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
        val g0 = image0.createGraphics()
        DesignSystem.applyHints(g0)
        val descLines = if (!info.description.isNullOrBlank())
            DesignSystem.wrap(g0, info.description.trim(), DesignSystem.uiFont(Font.PLAIN, 17f), WIDTH - PADDING * 2 - 64, maxLines = 5)
        else emptyList()
        g0.dispose()

        val headerHeight = 144
        val descBlock = if (descLines.isEmpty()) 0 else descLines.size * 24 + 8
        val chipsBlock = 44
        val commitBlock = if (info.latestCommit != null) 90 else 0
        val height = headerHeight + descBlock + chipsBlock + commitBlock + PADDING * 2 + 8

        val image = BufferedImage(WIDTH, height, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        try {
            DesignSystem.applyHints(g)

            g.paint = GradientPaint(0f, 0f, DesignSystem.BG_TOP, 0f, height.toFloat(), DesignSystem.BG_BOTTOM)
            g.fillRect(0, 0, WIDTH, height)

            // 装饰
            val orig = g.composite
            g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.18f)
            g.color = DesignSystem.PRIMARY
            g.fillOval(WIDTH - 240, -180, 360, 360)
            g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.10f)
            g.color = DesignSystem.ACCENT_CYAN
            g.fillOval(-120, headerHeight - 200, 280, 280)
            g.composite = orig

            // Header
            g.color = DesignSystem.ON_SURFACE
            g.font = DesignSystem.uiFont(Font.BOLD, 30f)
            g.drawString(info.fullName, PADDING, 68)

            // Tags
            var tx = PADDING
            val tagY = 86
            if (info.isPrivate) { tx = drawTag(g, tx, tagY, "PRIVATE", DesignSystem.GOLD); tx += 8 }
            if (info.isFork) { tx = drawTag(g, tx, tagY, "FORK", DesignSystem.ACCENT_CYAN); tx += 8 }
            if (info.isArchived) { tx = drawTag(g, tx, tagY, "ARCHIVED", DesignSystem.MUTED); tx += 8 }

            g.color = DesignSystem.MUTED
            g.font = DesignSystem.uiFont(Font.PLAIN, 14f)
            val subParts = mutableListOf<String>()
            info.defaultBranch?.takeIf { it.isNotBlank() }?.let { subParts += it }
            info.updatedAt?.takeIf { it.isNotBlank() }?.let { subParts += "updated $it" }
            if (subParts.isNotEmpty()) {
                g.drawString(subParts.joinToString(" · "), PADDING, 124)
            }

            // 主卡片 + 阴影
            val cardX = PADDING
            val cardY = headerHeight
            val cardW = WIDTH - PADDING * 2
            val cardH = height - cardY - PADDING
            DesignSystem.drawCard(g, cardX.toFloat(), cardY.toFloat(), cardW.toFloat(), cardH.toFloat(), 28f)
            DesignSystem.drawLeftBar(g, cardX, cardY, cardH)

            val left = cardX + 36
            var y = cardY + 30

            // 语言色条
            if (!info.language.isNullOrBlank()) {
                val langCol = languageColor(info.language)
                g.color = langCol
                g.fill(RoundRectangle2D.Float(left.toFloat(), y.toFloat(), cardW - 72f, 4f, 4f, 4f))
                g.color = DesignSystem.ON_SURFACE_VARIANT
                g.font = DesignSystem.uiFont(Font.BOLD, 13f)
                g.drawString(info.language, left, y + 18)
                y += 28
            }

            // 描述
            if (descLines.isNotEmpty()) {
                g.color = DesignSystem.ON_SURFACE_VARIANT
                g.font = DesignSystem.uiFont(Font.PLAIN, 17f)
                for (line in descLines) {
                    g.drawString(line, left, y + 16)
                    y += 24
                }
                y += 6
            } else {
                g.color = DesignSystem.MUTED
                g.font = DesignSystem.uiFont(Font.PLAIN, 16f)
                g.drawString("No description provided.", left, y + 16)
                y += 30
            }

            // Chips
            val chipY = y
            var cx = left
            cx = drawChip(g, cx, chipY, "★ ${DesignSystem.formatCount(info.stars)}", DesignSystem.GOLD) + 8
            cx = drawChip(g, cx, chipY, "⑂ ${DesignSystem.formatCount(info.forks)}", DesignSystem.ACCENT_CYAN) + 8
            cx = drawChip(g, cx, chipY, "● ${DesignSystem.formatCount(info.openIssues)} issues", DesignSystem.ORANGE) + 8
            info.homepage?.takeIf { it.isNotBlank() }?.let {
                drawChip(g, cx, chipY, it.removePrefix("https://").removePrefix("http://"), DesignSystem.PRIMARY)
            }
            y += chipsBlock

            // 最新 commit
            val lc = info.latestCommit
            if (lc != null) {
                y += 4
                g.color = DesignSystem.OUTLINE
                g.stroke = BasicStroke(1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                g.drawLine(left, y, cardX + cardW - 36, y)
                y += 14

                g.color = DesignSystem.MUTED
                g.font = DesignSystem.uiFont(Font.PLAIN, 12f)
                g.drawString("LATEST COMMIT", left, y + 10)

                g.color = DesignSystem.ON_SURFACE
                g.font = DesignSystem.uiFont(Font.BOLD, 17f)
                g.drawString(DesignSystem.ellipsize(g, lc.title, cardW - 64 - 120), left, y + 30)

                // SHA chip (right)
                val rightX = cardX + cardW - 36
                drawShaChip(g, rightX, y + 16, lc.shortSha)

                g.color = DesignSystem.MUTED
                g.font = DesignSystem.uiFont(Font.PLAIN, 13f)
                g.drawString("${lc.authorName} · ${lc.timestamp}", left, y + 50)
            }
        } finally {
            g.dispose()
        }

        val baos = ByteArrayOutputStream()
        ImageIO.write(image, "png", baos)
        return Base64.getEncoder().encodeToString(baos.toByteArray())
    }

    private fun drawTag(g: Graphics2D, x: Int, y: Int, text: String, accent: Color): Int {
        g.font = DesignSystem.uiFont(Font.BOLD, 11f)
        val fm = g.fontMetrics
        val w = fm.stringWidth(text) + 16
        val h = 20
        g.color = DesignSystem.blend(accent, DesignSystem.SURFACE, 0.75f)
        g.fill(RoundRectangle2D.Float(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), h.toFloat(), h.toFloat()))
        g.color = accent
        g.draw(RoundRectangle2D.Float(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), h.toFloat(), h.toFloat()))
        g.drawString(text, x + 8, y + h / 2 + fm.ascent / 2 - 2)
        return x + w
    }

    private fun drawChip(g: Graphics2D, x: Int, y: Int, text: String, accent: Color): Int {
        g.font = DesignSystem.uiFont(Font.BOLD, 14f)
        val fm = g.fontMetrics
        val w = fm.stringWidth(text) + 20
        val h = 30
        g.color = DesignSystem.SURFACE_HIGH
        g.fill(RoundRectangle2D.Float(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), h.toFloat(), h.toFloat()))
        // 左侧色点
        g.color = accent
        g.fillOval(x + 8, y + 11, 8, 8)
        g.color = DesignSystem.ON_SURFACE_VARIANT
        g.drawString(text, x + 22, y + h / 2 + fm.ascent / 2 - 2)
        return x + w
    }

    private fun drawShaChip(g: Graphics2D, rightX: Int, y: Int, sha: String) {
        g.font = DesignSystem.uiFont(Font.BOLD, 13f)
        val fm = g.fontMetrics
        val w = fm.stringWidth(sha) + 20
        val h = 26
        val x = rightX - w
        g.color = DesignSystem.SURFACE_HIGH
        g.fill(RoundRectangle2D.Float(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), h.toFloat(), h.toFloat()))
        g.color = DesignSystem.ACCENT_CYAN
        g.fill(RoundRectangle2D.Float(x.toFloat() + 6, y.toFloat() + 8, 3f, 10f, 2f, 2f))
        g.color = DesignSystem.ON_SURFACE_VARIANT
        g.drawString(sha, x + 14, y + h / 2 + fm.ascent / 2 - 2)
    }
}
