package moe.cuteyuki.kanadebot.utils

import moe.cuteyuki.kanadebot.managers.ConfigManager
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object CipherAES {
    private const val BLOCK_SIZE = 128 // bits

    /**
     * 懒加载 AES 密钥和 IV，确保 ConfigManager 已初始化
     */
    private val AES_KEY: ByteArray by lazy {
        val keyStr = ConfigManager.getConfig().aesKey
        keyStr.toByteArray(Charsets.UTF_8)
    }

    private val AES_IV: ByteArray by lazy {
        val ivStr = ConfigManager.getConfig().aesIv
        ivStr.toByteArray(Charsets.UTF_8)
    }

    /**
     * AES CBC PKCS#7 加密
     */
    @JvmStatic
    @Throws(Exception::class)
    fun encrypt(plaintext: ByteArray): ByteArray {
        val padded = pkcs7Pad(plaintext)

        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        val keySpec = SecretKeySpec(AES_KEY, "AES")
        val ivSpec = IvParameterSpec(AES_IV)

        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
        return cipher.doFinal(padded)
    }

    /**
     * AES CBC PKCS#7 解密
     */
    @JvmStatic
    @Throws(Exception::class)
    fun decrypt(ciphertext: ByteArray): ByteArray {
        if (ciphertext.isEmpty()) {
            throw IllegalArgumentException("响应内容为空")
        }
        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        val keySpec = SecretKeySpec(AES_KEY, "AES")
        val ivSpec = IvParameterSpec(AES_IV)

        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
        val decrypted = cipher.doFinal(ciphertext)
        return pkcs7Unpad(decrypted)
    }

    /**
     * PKCS#7 padding
     */
    private fun pkcs7Pad(data: ByteArray): ByteArray {
        val blockSize = BLOCK_SIZE / 8
        val paddingLength = blockSize - (data.size % blockSize)
        val padded = data.copyOf(data.size + paddingLength)
        padded.fill(paddingLength.toByte(), data.size, padded.size)
        return padded
    }

    /**
     * 移除 PKCS#7 padding
     */
    private fun pkcs7Unpad(paddedData: ByteArray): ByteArray {
        val padChar = paddedData.last().toInt() and 0xFF
        require(padChar in 1..BLOCK_SIZE / 8) { "Invalid padding: $padChar" }
        return paddedData.copyOf(paddedData.size - padChar)
    }
}
