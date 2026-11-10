package com.watchpost.alerter.notify;

import com.watchpost.common.spi.Notifier;
import com.watchpost.warehouse.entity.DeliveryLogEntity;
import com.watchpost.warehouse.repository.DeliveryLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 邮件投递重试调度器 (PRD 05: 失败重试 1/5/15 分钟共 3 次, 记录退信原因)。
 * 轮询 wp_delivery_log 中 status=2(失败) 且 next_retry_at 到期的记录, 重投。
 * 重试间隔: 第1次+1min, 第2次+5min, 第3次+15min; 超过3次标记退信(status=3)。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryRetryScheduler {

    private final DeliveryLogRepository deliveryRepo;
    private final List<Notifier> notifiers;

    private static final int[] RETRY_DELAYS_MIN = {1, 5, 15};
    private static final int MAX_RETRY = 3;

    /** 每分钟扫描一次待重试投递。 */
    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void retryPending() {
        List<DeliveryLogEntity> pending = deliveryRepo.findByStatusAndNextRetryAtBefore(2, OffsetDateTime.now());
        if (pending.isEmpty()) return;
        Notifier email = notifiers.stream().filter(n -> "EMAIL".equals(n.channel())).findFirst().orElse(null);
        if (email == null) return;
        for (DeliveryLogEntity d : pending) {
            try {
                Notifier.NotifyResult r = email.send(List.of(d.getRecipient()), d.getSubject(),
                        "", Map.of("retry", true));
                if (r.success()) {
                    d.setStatus(1);
                    d.setSentAt(OffsetDateTime.now());
                    d.setErrorReason(null);
                } else {
                    advanceRetry(d, r.error());
                }
            } catch (Exception e) {
                advanceRetry(d, e.getMessage());
            }
            deliveryRepo.save(d);
        }
        log.debug("delivery retry processed {} records", pending.size());
    }

    private void advanceRetry(DeliveryLogEntity d, String error) {
        int next = d.getRetryNo() + 1;
        d.setRetryNo(next);
        d.setErrorReason(error);
        if (next >= MAX_RETRY) {
            d.setStatus(3);   // 退信
            d.setNextRetryAt(null);
            log.warn("delivery exhausted retries, mark bounced: recipient={} subject={}", d.getRecipient(), d.getSubject());
        } else {
            d.setStatus(2);
            d.setNextRetryAt(OffsetDateTime.now().plusMinutes(RETRY_DELAYS_MIN[next]));
        }
    }
}
