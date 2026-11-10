package com.watchpost.alerter.notify;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 告警邮件链接构造器 (PRD 05: 查看详情 /alerts/A-2317, 静默1h /silence/A-2317)。
 * base-url 来自配置 (watchpost.mail.base-url), 指向 Web 控制台。
 */
@Slf4j
@Component
public class AlertLinkBuilder {

    @Value("${watchpost.mail.base-url:http://localhost:1157}")
    private String baseUrl;

    public String detailUrl(String alertNo) {
        return baseUrl + "/alerts/" + alertNo;
    }

    public String silenceUrl(String alertNo) {
        return baseUrl + "/silence/" + alertNo;
    }
}
