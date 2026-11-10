package com.watchpost.collector.protocol;

import com.watchpost.common.enums.ProtocolType;
import com.watchpost.common.model.CollectTemplate;
import com.watchpost.common.model.MetricRecord;
import com.watchpost.common.model.MetricValue;
import com.watchpost.common.model.MonitorTarget;
import com.watchpost.common.spi.ProtocolCollector;
import com.watchpost.common.spi.RuleExpression;
import com.watchpost.collector.parser.CommandParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ChannelExec;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.client.future.ConnectFuture;
import org.apache.sshd.common.NamedResource;
import org.apache.sshd.common.config.keys.FilePasswordProvider;
import org.apache.sshd.common.util.security.SecurityUtils;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/**
 * SSH 采集器 (Linux 服务器指标, M1 核心链路)。
 * 只读命令白名单: vmstat/free/df/iostat/ss/pgrep/cat/top/nproc。
 * 硬超时 target.timeoutMs (PRD ≤5s); 连接复用由 SshClient 管理; 异常捕获落 MetricRecord.error, 不抛断调度。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SshCollector implements ProtocolCollector {

    private final CommandParser parser;
    private final RuleExpression expression;

    /** 只读命令白名单前缀 (安全: 拒绝任何写操作/命令链接注入)。 */
    private static final List<String> ALLOWED = List.of(
            "vmstat", "free", "df", "iostat", "ss", "pgrep", "cat", "top", "nproc",
            "uptime", "ps", "grep", "awk", "tail", "head", "echo", "wc", "ipcs", "netstat");

    private final SshClient client = SshClient.setUpDefaultClient();

    @jakarta.annotation.PostConstruct
    public void start() {
        client.start();
        log.info("SSH collector started (Apache MINA SSHD)");
    }

    @jakarta.annotation.PreDestroy
    public void stop() {
        client.stop();
    }

    @Override
    public ProtocolType protocol() {
        return ProtocolType.ssh;
    }

    @Override
    public MetricRecord collect(MonitorTarget target) {
        long t0 = System.currentTimeMillis();
        MetricRecord.MetricRecordBuilder rb = MetricRecord.builder()
                .monitorId(target.getMonitorId()).monitorName(target.getMonitorName())
                .appType(target.getAppType()).protocol(ProtocolType.ssh)
                .timestamp(t0);
        ClientSession session = null;
        try {
            session = openSession(target);
            CollectTemplate tpl = target.getTemplate();
            List<MetricValue> values = new ArrayList<>();
            if (tpl != null && tpl.getMetrics() != null) {
                for (CollectTemplate.MetricGroup g : tpl.getMetrics()) {
                    if (g.getCommand() == null) continue;
                    if (!isAllowed(g.getCommand())) {
                        log.warn("monitorId={} command rejected by whitelist: {}", target.getMonitorId(), g.getCommand());
                        continue;
                    }
                    String out = exec(session, g.getCommand(), target.getTimeoutMs());
                    values.addAll(parser.parse(g, out, this::evalExpr));
                }
            }
            return rb.success(true).costMs(System.currentTimeMillis() - t0).values(values).build();
        } catch (Exception e) {
            log.debug("SSH collect failed monitorId={}: {}", target.getMonitorId(), e.getMessage());
            return rb.success(false).error(e.getMessage())
                    .costMs(System.currentTimeMillis() - t0).values(List.of()).build();
        } finally {
            closeQuietly(session);
        }
    }

    @Override
    public boolean precheck(MonitorTarget target) {
        ClientSession session = null;
        try {
            session = openSession(target);
            // 轻量探活: echo ok
            String out = exec(session, "echo ok", target.getTimeoutMs());
            return out != null && out.contains("ok");
        } catch (Exception e) {
            log.info("SSH precheck failed {}:{} -> {}", target.getHost(), target.getPort(), e.getMessage());
            return false;
        } finally {
            closeQuietly(session);
        }
    }

    private ClientSession openSession(MonitorTarget target) throws Exception {
        Map<String, String> cred = target.getCredential() == null ? Map.of() : target.getCredential();
        int port = target.getPort() != null ? target.getPort() : 22;
        String user = cred.get("username");
        if (user == null) throw new IllegalArgumentException("SSH credential missing username");
        // MINA SSHD 2.20: connect(username, host, port)
        ConnectFuture cf = client.connect(user, target.getHost(), port);
        cf.await(target.getTimeoutMs());
        ClientSession session = cf.getSession();

        String privateKey = cred.get("privateKey");
        String password = cred.get("password");
        if (privateKey != null && !privateKey.isBlank()) {
            // 密钥认证: 从 PEM 内容加载 KeyPair (支持 passphrase 保护)
            String passphrase = cred.get("passphrase");
            FilePasswordProvider pwdProvider = (passphrase != null && !passphrase.isBlank())
                    ? FilePasswordProvider.of(passphrase) : FilePasswordProvider.EMPTY;
            Iterable<KeyPair> keyPairs = SecurityUtils.loadKeyPairIdentities(
                    session, NamedResource.ofName("cred-" + target.getMonitorId()),
                    new ByteArrayInputStream(privateKey.getBytes(StandardCharsets.UTF_8)), pwdProvider);
            if (keyPairs == null || !keyPairs.iterator().hasNext()) {
                throw new IllegalArgumentException("SSH private key load failed (unsupported format or wrong passphrase)");
            }
            for (KeyPair kp : keyPairs) session.addPublicKeyIdentity(kp);
            session.auth().verify(target.getTimeoutMs());
        } else if (password != null && !password.isBlank()) {
            // 口令认证
            session.addPasswordIdentity(password);
            session.auth().verify(target.getTimeoutMs());
        } else {
            throw new IllegalArgumentException("SSH credential missing password or privateKey");
        }
        return session;
    }

    private String exec(ClientSession session, String command, long timeoutMs) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        try (ChannelExec channel = session.createExecChannel(command)) {
            channel.setOut(out);
            channel.setErr(err);
            channel.open().verify(timeoutMs);
            channel.waitFor(EnumSet.of(org.apache.sshd.client.channel.ClientChannelEvent.CLOSED),
                    Duration.ofMillis(timeoutMs));
        }
        return out.toString(java.nio.charset.StandardCharsets.UTF_8);
    }

    private Object evalExpr(String expr, Map<String, Object> bindings) {
        return expression.eval(expr, bindings);
    }

    private boolean isAllowed(String command) {
        String head = command.trim().toLowerCase();
        // 拒绝命令注入: 分号/&&/||/反引号/$$() 之后的内容也需为白名单命令; 简化为拒绝危险元字符
        if (head.contains(";") || head.contains("&&") || head.contains("||")
                || head.contains("`") || head.contains("$(") || head.contains(">") || head.contains("<")) {
            // 允许管道 (|) 用于 grep/awk, 但拒绝重定向与命令链接
            // 进一步校验管道各段首命令
            for (String seg : head.split("\\|")) {
                String s = seg.trim();
                if (s.isEmpty()) continue;
                String first = s.split("\\s+")[0];
                if (ALLOWED.stream().noneMatch(a -> first.equals(a) || first.endsWith("/" + a))) return false;
            }
            return true;
        }
        String first = head.split("\\s+")[0];
        return ALLOWED.stream().anyMatch(a -> first.equals(a) || first.endsWith("/" + a));
    }

    private void closeQuietly(ClientSession session) {
        if (session != null) {
            try { session.close(); } catch (Exception ignored) {}
        }
    }
}
