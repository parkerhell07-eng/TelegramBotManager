package com.telegrambot.manager.data.cloud

import org.bouncycastle.crypto.digests.Blake2bDigest
import org.bouncycastle.crypto.engines.XSalsa20Engine
import org.bouncycastle.crypto.macs.Poly1305
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.ParametersWithIV
import org.bouncycastle.math.ec.rfc7748.X25519
import java.security.SecureRandom
import android.util.Base64

/**
 * Encrypts a secret the way GitHub Actions expects (libsodium sealed box),
 * implemented with BouncyCastle primitives.
 *
 * The algorithm below is a 1:1 mirror of scripts/test_seal.py, which was
 * verified offline against real libsodium (crypto_box_seal_open), i.e. the
 * exact decoder GitHub runs server-side:
 *
 *   1. ephemeral X25519 keypair
 *   2. shared = X25519(esk, recipient_pk)
 *   3. key    = HSalsa20(shared, in=0^16, const="expand 32-byte k")
 *   4. nonce  = BLAKE2b-24(epk || recipient_pk)
 *   5. box    = XSalsa20-Poly1305(key, nonce, msg)  →  mac(16) || ct
 *   6. sealed = epk(32) || mac || ct
 *   7. value  = base64( 0x00000000 || 0x00000007 || "libsodium" || sealed )
 */
object GitHubSecrets {

    private const val SIGMA_A = 0x61707865 // "expa"
    private const val SIGMA_B = 0x3320646E // "nd 3"
    private const val SIGMA_C = 0x79622D32 // "2-b"
    private const val SIGMA_D = 0x6B206574 // "yte k"

    /** Seals [plaintext] to the GitHub repository public key. Returns base64 for the REST API. */
    fun seal(recipientPkB64: String, plaintext: ByteArray): String {
        require(plaintext.isNotEmpty()) { "empty secret" }
        val pk = Base64.decode(recipientPkB64, Base64.NO_WRAP)
        require(pk.size == 32) { "bad repo public key" }

        // 1. ephemeral X25519 keypair
        val esk = ByteArray(32)
        SecureRandom().nextBytes(esk)
        val epk = ByteArray(32)
        X25519.generatePublicKey(esk, 0, epk, 0)

        // 2. shared secret = X25519(esk, recipient_pk)
        val shared = ByteArray(32)
        X25519.calculateAgreement(esk, 0, pk, 0, shared, 0)

        // 3. HSalsa20(shared, zeros16) → secretbox key
        val bkey = hsalsa20(shared)

        // 4. nonce = BLAKE2b-24(epk || pk)
        val digest = Blake2bDigest(192) // 24 bytes
        digest.update(epk, 0, epk.size)
        digest.update(pk, 0, pk.size)
        val nonce = ByteArray(24)
        digest.doFinal(nonce, 0)

        // 5. secretbox = XSalsa20-Poly1305: mac(16) || ct
        val xs = XSalsa20Engine()
        xs.init(true, ParametersWithIV(KeyParameter(bkey), nonce))
        val polyKey = ByteArray(32)
        xs.processBytes(ByteArray(32), 0, 32, polyKey, 0)
        val ct = ByteArray(plaintext.size)
        xs.processBytes(plaintext, 0, plaintext.size, ct, 0)
        val poly = Poly1305()
        poly.init(KeyParameter(polyKey))
        poly.update(ct, 0, ct.size)
        val mac = ByteArray(16)
        poly.doFinal(mac, 0)

        // 6. sealed = epk || mac || ct
        val sealed = ByteArray(32 + 16 + ct.size)
        epk.copyInto(sealed, 0)
        mac.copyInto(sealed, 32)
        ct.copyInto(sealed, 48)

        // 7. GitHub envelope: version(4) || keyTypeLen(4)=7 || "libsodium" || sealed
        val head = byteArrayOf(
            0, 0, 0, 0,
            0, 0, 0, 7,
            0x6C, 0x69, 0x62, 0x73, 0x6F, 0x64, 0x69, 0x75, 0x6D // "libsodium"
        )
        val env = ByteArray(head.size + sealed.size)
        head.copyInto(env, 0)
        sealed.copyInto(env, head.size)
        return Base64.encodeToString(env, Base64.NO_WRAP)
    }

    // ── HSalsa20 core (TweetNaCl-compatible) ────────────────────────────
    // Verified against crypto_box_beforenm in scripts/test_seal.py.

    private fun rotl(x: Int, n: Int): Int = (x shl n) or (x ushr (32 - n))

    private fun qr(x: IntArray, a: Int, b: Int, c: Int, d: Int) {
        x[b] = x[b] xor rotl(x[a] + x[d], 7)
        x[c] = x[c] xor rotl(x[b] + x[a], 9)
        x[d] = x[d] xor rotl(x[c] + x[b], 13)
        x[a] = x[a] xor rotl(x[d] + x[c], 18)
    }

    private fun hsalsa20(shared32: ByteArray): ByteArray {
        val k = IntArray(8) { i ->
            val o = i * 4
            (shared32[o].toInt() and 0xFF) or
                ((shared32[o + 1].toInt() and 0xFF) shl 8) or
                ((shared32[o + 2].toInt() and 0xFF) shl 16) or
                ((shared32[o + 3].toInt() and 0xFF) shl 24)
        }
        // TweetNaCl interleaved state: sigma, key[0:16], in(zeros16), sigma, key[16:32], sigma
        val x = intArrayOf(
            SIGMA_A, k[0], k[1], k[2], k[3], SIGMA_B,
            0, 0, 0, 0,
            SIGMA_C, k[4], k[5], k[6], k[7], SIGMA_D
        )
        repeat(10) {
            qr(x, 0, 4, 8, 12); qr(x, 5, 9, 13, 1); qr(x, 10, 14, 2, 6); qr(x, 15, 3, 7, 11)
            qr(x, 0, 1, 2, 3); qr(x, 5, 6, 7, 4); qr(x, 10, 11, 8, 9); qr(x, 15, 12, 13, 14)
        }
        val out = intArrayOf(x[0], x[5], x[10], x[15], x[6], x[7], x[8], x[9])
        val bytes = ByteArray(32)
        for (i in 0 until 8) {
            bytes[i * 4] = (out[i] and 0xFF).toByte()
            bytes[i * 4 + 1] = ((out[i] ushr 8) and 0xFF).toByte()
            bytes[i * 4 + 2] = ((out[i] ushr 16) and 0xFF).toByte()
            bytes[i * 4 + 3] = ((out[i] ushr 24) and 0xFF).toByte()
        }
        return bytes
    }
}
