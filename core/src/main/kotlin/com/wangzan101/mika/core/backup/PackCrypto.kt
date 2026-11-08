package com.wangzan101.mika.core.backup

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** 导出文件用口令封起来。口令不保存。错口令打不开。 */
object PackCrypto {
    private val MAGIC = byteArrayOf('M'.code.toByte(), 'K'.code.toByte(), 'E'.code.toByte(), '1'.code.toByte())
    private const val SALT = 16
    private const val IV = 12
    private const val KEY_BITS = 256
    private const val TAG_BITS = 128
    private const val ITERATIONS = 120_000

    fun seal(plain: ByteArray, password: CharArray): ByteArray {
        require(password.isNotEmpty())
        val salt = ByteArray(SALT).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV).also { SecureRandom().nextBytes(it) }
        val key = key(password, salt)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        val body = c.doFinal(plain)
        return ByteBuffer.allocate(MAGIC.size + 4 + SALT + IV + body.size).run {
            put(MAGIC)
            putInt(ITERATIONS)
            put(salt)
            put(iv)
            put(body)
            array()
        }
    }

    fun open(blob: ByteArray, password: CharArray): ByteArray? {
        if (blob.size <= MAGIC.size + 4 + SALT + IV) return null
        val buf = ByteBuffer.wrap(blob)
        val magic = ByteArray(MAGIC.size).also { buf.get(it) }
        if (!magic.contentEquals(MAGIC)) return null
        val iterations = buf.int
        if (iterations !in 10_000..1_000_000) return null
        val salt = ByteArray(SALT).also { buf.get(it) }
        val iv = ByteArray(IV).also { buf.get(it) }
        val body = ByteArray(buf.remaining()).also { buf.get(it) }
        return runCatching {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key(password, salt, iterations), GCMParameterSpec(TAG_BITS, iv))
            c.doFinal(body)
        }.getOrNull()
    }

    private fun key(password: CharArray, salt: ByteArray, iterations: Int = ITERATIONS): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        val raw = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(raw, "AES")
    }
}
