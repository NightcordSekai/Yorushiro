package moe.cuteyuki.kanadebot.utils

import com.google.zxing.*
import com.google.zxing.client.j2se.BufferedImageLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.URI
import javax.imageio.ImageIO

/**
 * QR Code detection and decoding utility.
 */
object QRCodeUtil {

    /**
     * Try to detect and decode a QR code from an image URL.
     * Returns the decoded text if successful, null otherwise.
     */
    fun decodeFromUrl(imageUrl: String): String? {
        return try {
            val url = URI(imageUrl).toURL()
            val bufferedImage = ImageIO.read(url)
            if (bufferedImage == null) {
                System.err.println("[QRCodeUtil] Failed to read image from URL: $imageUrl")
                return null
            }
            decodeBufferedImage(bufferedImage)
        } catch (e: IOException) {
            System.err.println("[QRCodeUtil] IO error reading image from URL: ${e.message}")
            null
        } catch (e: Exception) {
            System.err.println("[QRCodeUtil] Unexpected error decoding QR code: ${e.message}")
            null
        }
    }

    /**
     * Try to detect and decode a QR code from raw image bytes.
     * Returns the decoded text if successful, null otherwise.
     */
    fun decodeFromBytes(imageBytes: ByteArray): String? {
        return try {
            val bufferedImage = ImageIO.read(ByteArrayInputStream(imageBytes))
            if (bufferedImage == null) {
                System.err.println("[QRCodeUtil] Failed to read image from bytes")
                return null
            }
            decodeBufferedImage(bufferedImage)
        } catch (e: IOException) {
            System.err.println("[QRCodeUtil] IO error reading image from bytes: ${e.message}")
            null
        } catch (e: Exception) {
            System.err.println("[QRCodeUtil] Unexpected error decoding QR code: ${e.message}")
            null
        }
    }

    private fun decodeBufferedImage(bufferedImage: BufferedImage): String? {
        val source = BufferedImageLuminanceSource(bufferedImage)
        val bitmap = BinaryBitmap(HybridBinarizer(source))

        val hints = mapOf(
            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
            DecodeHintType.TRY_HARDER to true
        )

        return try {
            val result = MultiFormatReader().decode(bitmap, hints)
            result.text
        } catch (e: NotFoundException) {
            // No QR code found in the image
            null
        }
    }
}
