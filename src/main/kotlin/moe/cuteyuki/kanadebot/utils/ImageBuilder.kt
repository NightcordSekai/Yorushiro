package moe.cuteyuki.kanadebot.utils

import moe.cuteyuki.kanadebot.mainetwork.beans.MusicLevel.*
import moe.cuteyuki.kanadebot.mainetwork.beans.UserRatingData
import moe.cuteyuki.kanadebot.managers.ResourceManager
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.geom.RoundRectangle2D
import javax.imageio.ImageIO
import kotlin.Array
import kotlin.Int

object ImageBuilder {
    fun drawRatingCard(g2d: Graphics2D, data: UserRatingData, x: Int, y: Int){
        g2d.color = Color(237, 234, 255)
        g2d.fillRoundRect(x, y, 300, 100, 60, 60)

        //https://assets2.lxns.net/maimai/jacket/
        val radius = 40
        val oldClip = g2d.clip
        val round = RoundRectangle2D.Float(
            x.toFloat()+10,
            y.toFloat()+10,
            80f,
            80f,
            radius.toFloat(),
            radius.toFloat()
        )

        g2d.clip = round

        g2d.drawImage(
            ResourceManager.coverImage(data.musicId.toString()),
            x+10,
            y+10,
            80,
            80,
            null
        )

        g2d.clip = oldClip

        //draw music name & ach
        g2d.color = Color(61, 61, 61)
        g2d.font = Font("MiSans-Regular", Font.PLAIN, 16)
        if(data.musicName!!.length > 10){
            data.musicName = data.musicName!!.substring(0,10)+"..."
        }
        g2d.drawString(data.musicName, x + 100, y + 36)
        g2d.font = Font("MiSans-Bold", Font.PLAIN, 26)
        g2d.drawString(data.formatRatingSimple(data.achievement.toLong()), x+ 100, y + 65)

        //draw dx
        if(data.musicId > 10000)
            g2d.drawImage(ImageIO.read(object {}::javaClass.get().getResourceAsStream("/DX.png")), x+230, y+20, 60, 21, null)

        //draw shits
        val ds: Array<String> = MusicDataProvider.findDS(data.musicId.toString())
        val levelStr: String = when (data.level) {
            Basic -> ds[0]
            Advanced -> ds[1]
            Expert -> ds[2]
            Master -> ds[3]
            ReMaster -> ds[4]
            null -> ds[0]
        }
        val result: RatingCalculator.RaResult = RatingCalculator.computeRaWithRate(
            levelStr.toDouble(),
            data.achievement / 10000.0
        )!!
        g2d.drawImage(ImageIO.read(object {}::javaClass.get().getResourceAsStream(
            "/UI_TTR_Rank_${result.rate}.png"
        )), x+95, y+67, 55, 28, null)

        //draw RA
        when (data.level){
            Basic -> g2d.color = Color(98,140,123)
            Advanced -> g2d.color = Color(181,131,0)
            Expert -> g2d.color = Color(211, 122, 122)
            Master -> g2d.color = Color(103,80,164)
            ReMaster -> g2d.color = Color(208, 200, 255)
            null -> g2d.color = Color(0, 255, 2)
        }
       // g2d.color = Color(208, 200, 255)
        g2d.fillRoundRect(x+225, y+70, 68, 26, 30, 90)
        g2d.color = Color(61, 61, 61)
        g2d.font = Font("MiSans-Regular", Font.PLAIN, 12)
        g2d.drawString("RA ${result.ra}", x+237, y+87)
    }
}