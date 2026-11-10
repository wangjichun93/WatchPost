package com.watchpost.collector.protocol.ipmi;

import com.watchpost.common.spi.IpmiClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * IPMI 客户端默认实现: ipmitool 子进程封装。
 * 选型说明: Verax IPMI 库年久失修, 故以 ipmitool 封装为兜底 (抽象 IpmiClient 便于替换为 Verax/其他)。
 * 带外采集为降级路径 (PRD: 无系统账号时降级 SNMP+IPMI), 非默认。
 * 依赖部署主机安装 ipmitool; 未安装时 probe 返回 false, 不影响其他采集。
 */
@Slf4j
@Component
public class IpmiToolClient implements IpmiClient {

    @Override
    public Map<String, String> readSensors(String host, Map<String, String> credential, long timeoutMs) {
        Map<String, String> out = new HashMap<>();
        // sdr elist: 传感器读数 (温度/电压/风扇)
        String sdr = run(host, credential, timeoutMs, "sdr", "elist");
        if (sdr != null) {
            for (String line : sdr.split("\n")) {
                // 格式: SensorName | value | unit | status
                String[] parts = line.split("\\|");
                if (parts.length >= 2) {
                    out.put(parts[0].trim(), parts[1].trim());
                }
            }
        }
        // chassis power status
        String power = run(host, credential, timeoutMs, "chassis", "power", "status");
        if (power != null) {
            out.put("power_status", power.contains("on") ? "on" : "off");
        }
        return out;
    }

    @Override
    public boolean probe(String host, Map<String, String> credential, long timeoutMs) {
        String out = run(host, credential, timeoutMs, "mc", "info");
        return out != null && out.toLowerCase().contains("device id");
    }

    /** 执行 ipmitool 命令 (lanplus, 只读权限 OPERATOR)。 */
    private String run(String host, Map<String, String> cred, long timeoutMs, String... args) {
        String user = cred.getOrDefault("username", "admin");
        String pass = cred.getOrDefault("password", cred.getOrDefault("secret", ""));
        String cipher = cred.getOrDefault("cipherSuite", "3");
        try {
            java.util.List<String> cmd = new java.util.ArrayList<>();
            cmd.add("ipmitool");
            cmd.add("-I"); cmd.add("lanplus");
            cmd.add("-H"); cmd.add(host);
            cmd.add("-U"); cmd.add(user);
            cmd.add("-P"); cmd.add(pass);
            cmd.add("-C"); cmd.add(cipher);
            cmd.add("-L"); cmd.add("OPERATOR");   // 只读巡检权限
            cmd.add("-N"); cmd.add(String.valueOf(Math.max(1, timeoutMs / 1000)));
            cmd.addAll(java.util.Arrays.asList(args));

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line).append("\n");
            }
            if (!p.waitFor(timeoutMs + 1000, TimeUnit.MILLISECONDS)) {
                p.destroyForcibly();
                return null;
            }
            return p.exitValue() == 0 ? sb.toString() : null;
        } catch (Exception e) {
            log.debug("ipmitool exec failed host={}: {}", host, e.getMessage());
            return null;
        }
    }
}
