package com.watchpost.collector.schedule;

import com.watchpost.common.model.MonitorTarget;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * 采集调度器 (PRD: 周期 ≥15s, 单机 ≥500 实例, 任务隔离)。
 *
 * 模型: 平台线程 ScheduledExecutorService 仅负责「周期触发」(轻量, 不阻塞);
 *      每次触发把实际采集提交到「虚拟线程」执行 (大量阻塞式 I/O 成本极低);
 *      采集前经协议舱壁 tryAcquire, 未获许可则跳过本次 (背压, 不堆积)。
 *
 * 任务隔离: 单实例采集异常/超时仅影响自身虚拟线程, 不影响调度线程与其他实例。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CollectScheduler {

    private final TargetProvider targetProvider;
    private final CollectPipeline pipeline;
    private final ProtocolBulkhead bulkhead;
    private final CollectorProperties props;

    /** 周期触发器 (平台线程, 仅派发)。 */
    private ScheduledExecutorService scheduler;
    /** 采集执行器 (虚拟线程, 承载阻塞 I/O)。 */
    private java.util.concurrent.ExecutorService vthreadExecutor;

    /** monitorId -> 周期任务句柄 (用于 refresh/cancel)。 */
    private final Map<Long, ScheduledFuture<?>> tasks = new ConcurrentHashMap<>();
    /** monitorId -> 目标快照 (refresh 时比对)。 */
    private final Map<Long, MonitorTarget> targets = new ConcurrentHashMap<>();

    private volatile boolean running = false;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (!props.isEnabled()) {
            log.info("collector scheduler disabled by config (collector.enabled=false)");
            return;
        }
        start();
        refresh();
    }

    public synchronized void start() {
        if (running) return;
        ThreadFactory tf = Thread.ofPlatform().name("collect-sched-", 0).daemon(true).factory();
        this.scheduler = Executors.newScheduledThreadPool(props.getSchedulerThreads(), tf);
        this.vthreadExecutor = Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name(props.getVirtualThreadPrefix(), 0).factory());
        this.running = true;
        log.info("collect scheduler started (schedulerThreads={}, virtualThreads=on)", props.getSchedulerThreads());
    }

    /** 重新加载目标并重建调度 (监控增删改后调用)。 */
    public synchronized void refresh() {
        if (!running) return;
        List<MonitorTarget> loaded = targetProvider.loadEnabled();
        // 取消已移除/停用的任务
        for (Long id : tasks.keySet()) {
            boolean stillThere = loaded.stream().anyMatch(t -> t.getMonitorId() == id);
            if (!stillThere) cancel(id);
        }
        // 新增/更新任务
        for (MonitorTarget t : loaded) {
            targets.put(t.getMonitorId(), t);
            ScheduledFuture<?> existing = tasks.get(t.getMonitorId());
            if (existing != null) {
                existing.cancel(false);   // 周期或配置变更, 重建
            }
            schedule(t);
        }
        log.info("collect scheduler refreshed: {} active tasks", tasks.size());
    }

    /** 调度单个目标 (新增/编辑后立即生效)。 */
    public void schedule(MonitorTarget target) {
        if (!running) return;
        targets.put(target.getMonitorId(), target);
        int interval = Math.max(target.getIntervalSec(), 15);   // PRD: 周期 ≥15s
        // 初始延迟错峰: 按 monitorId 散列, 避免所有任务同刻触发
        long initialDelay = (target.getMonitorId() % interval) + 1;
        ScheduledFuture<?> f = scheduler.scheduleAtFixedRate(
                () -> dispatch(target), initialDelay, interval, TimeUnit.SECONDS);
        tasks.put(target.getMonitorId(), f);
        log.debug("scheduled monitorId={} interval={}s initialDelay={}s", target.getMonitorId(), interval, initialDelay);
    }

    /** 取消单个目标调度 (删除/停用时)。 */
    public void cancel(long monitorId) {
        ScheduledFuture<?> f = tasks.remove(monitorId);
        if (f != null) f.cancel(false);
        targets.remove(monitorId);
        log.debug("cancelled monitorId={}", monitorId);
    }

    /** 立即采集一次 (详情页手动刷新/预检后)。 */
    public void collectNow(long monitorId) {
        MonitorTarget t = targets.get(monitorId);
        if (t != null) dispatch(t);
    }

    /**
     * 派发一次采集: 虚拟线程执行 + 协议舱壁隔离。
     * 此方法在调度线程上调用, 必须立即返回 (不阻塞), 实际工作在虚拟线程内完成。
     */
    private void dispatch(MonitorTarget target) {
        if (!running) return;
        try {
            vthreadExecutor.submit(() -> {
                // 舱壁许可在虚拟线程内申请, 调度线程不被阻塞
                if (bulkhead.tryAcquire(target.getProtocol(), target.getTimeoutMs())) {
                    try {
                        pipeline.run(target);
                    } catch (Exception e) {
                        log.error("collect task error monitorId={}", target.getMonitorId(), e);
                    } finally {
                        bulkhead.release(target.getProtocol());
                    }
                } else {
                    log.warn("bulkhead full, skip this cycle: monitorId={} protocol={}",
                            target.getMonitorId(), target.getProtocol());
                }
            });
        } catch (Exception e) {
            log.error("dispatch submit failed monitorId={}", target.getMonitorId(), e);
        }
    }

    public int activeTaskCount() {
        return tasks.size();
    }

    public boolean isRunning() {
        return running;
    }

    @PreDestroy
    public synchronized void stop() {
        running = false;
        if (scheduler != null) scheduler.shutdownNow();
        if (vthreadExecutor != null) vthreadExecutor.shutdownNow();
        tasks.clear();
        targets.clear();
        log.info("collect scheduler stopped");
    }
}
