package com.watchpost.common.crypto;

import com.watchpost.common.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 凭据加密测试 (PRD: 凭据 AES-256 加密)。
 * 验证加解密往返、密文非确定性 (随机 IV)、篡改检测、密钥缺失保护。
 */
class AesGcmCryptoServiceTest {

    private final AesGcmCryptoService crypto = new AesGcmCryptoService("test-master-key-for-unit-tests");

    @Test
    @DisplayName("加解密往返: 明文 -> 密文 -> 明文 一致")
    void roundTrip() {
        String secret = "P@ssw0rd!#$%^&*()_+中文口令";
        String cipher = crypto.encrypt(secret);
        assertNotNull(cipher);
        assertNotEquals(secret, cipher, "密文不应等于明文");
        assertEquals(secret, crypto.decrypt(cipher), "解密应还原明文");
    }

    @Test
    @DisplayName("密文非确定性: 同一明文两次加密结果不同 (随机 IV)")
    void nonDeterministic() {
        String secret = "same-plaintext";
        String c1 = crypto.encrypt(secret);
        String c2 = crypto.encrypt(secret);
        assertNotEquals(c1, c2, "随机 IV 应使密文不同");
        assertEquals(secret, crypto.decrypt(c1));
        assertEquals(secret, crypto.decrypt(c2));
    }

    @Test
    @DisplayName("篡改检测: 密文被改动后解密失败 (GCM 认证标签)")
    void tamperDetected() {
        String cipher = crypto.encrypt("sensitive-credential");
        // 篡改密文中间一个字符
        char[] chars = cipher.toCharArray();
        int mid = chars.length / 2;
        chars[mid] = chars[mid] == 'A' ? 'B' : 'A';
        String tampered = new String(chars);
        assertThrows(BizException.class, () -> crypto.decrypt(tampered), "篡改密文应解密失败");
    }

    @Test
    @DisplayName("密钥隔离: 不同主密钥无法互相解密")
    void keyIsolation() {
        AesGcmCryptoService other = new AesGcmCryptoService("a-completely-different-master-key");
        String cipher = crypto.encrypt("secret");
        assertThrows(BizException.class, () -> other.decrypt(cipher), "错误密钥应解密失败");
    }

    @Test
    @DisplayName("主密钥缺失保护: 空密钥构造抛异常")
    void missingKeyRejected() {
        assertThrows(BizException.class, () -> new AesGcmCryptoService(null));
        assertThrows(BizException.class, () -> new AesGcmCryptoService("  "));
    }

    @Test
    @DisplayName("keyId 为主密钥指纹, 不含密钥本身")
    void keyIdIsFingerprint() {
        String keyId = crypto.keyId();
        assertNotNull(keyId);
        assertTrue(keyId.startsWith("sha256:"), "keyId 应为 sha256 指纹前缀");
        assertFalse(keyId.contains("test-master-key"), "keyId 不应泄漏主密钥");
    }

    @Test
    @DisplayName("null/空值安全处理")
    void nullSafe() {
        assertNull(crypto.encrypt(null));
        assertNull(crypto.decrypt(null));
        assertNull(crypto.decrypt(""));
    }
}
