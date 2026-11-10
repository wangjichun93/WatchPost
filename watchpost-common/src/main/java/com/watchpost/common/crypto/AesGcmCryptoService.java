package com.watchpost.common.crypto;

import com.watchpost.common.exception.BizException;
import com.watchpost.common.exception.ErrorCode;
import com.watchpost.common.spi.CryptoService;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM 凭据加密实现 (PRD: 凭据 AES-256 加密, 主密钥不落库)。
 * 主密钥来源: 环境变量 WATCHPOST_CRYPTO_KEY (或 KMS 注入)。
 * 密文格式: Base64( 12B IV || ciphertext || 16B GCM tag )。
 * 密钥经 SHA-256 归一化为 32 字节, 支持任意长度的口令式主密钥。
 */
public class AesGcmCryptoService implements CryptoService {

    private static final String ALGO = "AES";
    private static final String TRANSFORM = "AES/GCM/NoPadding";
    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec keySpec;
    private final String keyId;
    private final SecureRandom random = new SecureRandom();

    public AesGcmCryptoService(String masterKey) {
        if (masterKey == null || masterKey.isBlank()) {
            throw new BizException(ErrorCode.CRYPTO_KEY_MISSING);
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(masterKey.getBytes(StandardCharsets.UTF_8));
            this.keySpec = new SecretKeySpec(digest, ALGO);
            // keyId: 主密钥指纹前 8 位 (不含密钥本身), 便于轮换追溯
            this.keyId = "sha256:" + Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(digest).substring(0, 8);
        } catch (Exception e) {
            throw new BizException(ErrorCode.CRYPTO_KEY_MISSING, e);
        }
    }

    @Override
    public String encrypt(String plaintext) {
        if (plaintext == null) return null;
        try {
            byte[] iv = new byte[IV_LEN];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, e);
        }
    }

    @Override
    public String decrypt(String cipherText) {
        if (cipherText == null || cipherText.isBlank()) return null;
        try {
            byte[] in = Base64.getDecoder().decode(cipherText);
            if (in.length <= IV_LEN) throw new IllegalArgumentException("ciphertext too short");
            byte[] iv = new byte[IV_LEN];
            System.arraycopy(in, 0, iv, 0, IV_LEN);
            Cipher cipher = Cipher.getInstance(TRANSFORM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BITS, iv));
            byte[] pt = cipher.doFinal(in, IV_LEN, in.length - IV_LEN);
            return new String(pt, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new BizException(ErrorCode.CREDENTIAL_DECRYPT_FAILED, e);
        }
    }

    @Override
    public String keyId() {
        return keyId;
    }
}
