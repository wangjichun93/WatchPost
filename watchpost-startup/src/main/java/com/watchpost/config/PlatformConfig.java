package com.watchpost.config;

import com.watchpost.common.crypto.AesGcmCryptoService;
import com.watchpost.common.spi.CryptoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 平台级 Bean 装配 (startup 模块统一组装)。
 * CryptoService: AES-256-GCM 凭据加密, 主密钥来自配置 watchpost.crypto.master-key。
 * 生产环境务必用环境变量/KMS 注入主密钥, 不使用默认值。
 */
@Slf4j
@Configuration
public class PlatformConfig {

    @Bean
    public CryptoService cryptoService(@Value("${watchpost.crypto.master-key}") String masterKey) {
        if (masterKey == null || masterKey.contains("change-me")) {
            log.warn("=== 正在使用默认凭据加密主密钥, 生产环境务必通过 WP_CRYPTO_KEY 环境变量/KMS 注入 ===");
        }
        return new AesGcmCryptoService(masterKey);
    }
}
