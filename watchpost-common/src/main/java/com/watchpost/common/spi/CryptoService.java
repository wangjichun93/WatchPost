package com.watchpost.common.spi;

/**
 * 凭据加解密 SPI (PRD: AES-256 加密, 主密钥不落库)。
 * 默认 AES-256-GCM 实现, 主密钥来自环境变量/KMS; 可替换为 KMS 集成实现。
 */
public interface CryptoService {

    /** 加密明文凭据, 返回 Base64(iv + ciphertext + tag)。 */
    String encrypt(String plaintext);

    /** 解密密文, 失败抛 BizException (密钥不匹配/密文篡改)。 */
    String decrypt(String cipherText);

    /** 当前主密钥标识 (写入 wp_credential.kms_key_id, 便于轮换追溯)。 */
    String keyId();
}
