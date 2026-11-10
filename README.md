# 望哨 WATCHPOST · 无 Agent 实时监控系统

> PULL 模式统一监控平台 · 服务器 / HTTP API / MySQL·PostgreSQL·Oracle 全量覆盖 · 逐指标可配阈值 · 越限收敛告警 · 邮件通知
>
> 版本 v1.0（M1 骨架 + 前端控制台 + 可插拔实现）· Java 21 + Spring Boot 3.5 + React 18 · 全新自研（架构借鉴 Apache HertzBeat）· 参考 PRD v1.0

---

## 这是什么

望哨是一套**无 Agent（PULL 模式）**的实时监控系统：中心侧按周期主动拉取被监控目标的指标，被监控端**零安装、零升级**。每个采集字段可独立配置阈值，越限后经告警引擎收敛（连续 N 次 / 抑制 / 静默 / 恢复），按策略发送邮件。

**v1.0 范围**：只做「指标采集 → 阈值判定 → 告警收敛 → 邮件通知」闭环。不做日志聚合、链路追踪、Agent、AI 异常检测、移动端、IM 渠道（预留 Webhook）。

**v1.0 不监控 Windows 主机**：协议层为 SSH · SNMP · IPMI · HTTP(S) · JDBC 五种（已移除 WinRM）。

---

## 交付物清单

本仓库包含四份配置制品 + 可编译运行的 M1 骨架工程 + React 前端控制台 + 四项可插拔实现：

| 制品 | 位置 | 说明 |
|---|---|---|
| **① 数据库 DDL** | `watchpost-warehouse/src/main/resources/db/migration/` | `V1__init_schema.sql`（19 张表 + 枚举 + 分区 + 触发器）、`V2__seed_data.sql`（角色 + 出厂阈值规则 + 种子） |
| **② 采集模板 YAML** | `watchpost-collector/src/main/resources/templates/` | 五协议模板 `linux/http/mysql/postgresql/oracle.yaml` + 规范文档 `docs/templates/采集模板与解析器规范.md` |
| **③ REST API 契约** | `docs/api/openapi.yaml` | OpenAPI 3.0.3，33 个端点，22 个 schema，Bearer JWT 鉴权 |
| **④ M1 骨架工程** | 本仓库（Maven 多模块） | 6 模块，130+ Java 文件，可编译打包为可执行 jar |
| **⑤ React 前端控制台** | `web-app/` | React 18 + TS + Vite + AntD 5 + ECharts，9 路由 + 独立登录页，构建通过 |
| **⑥ 可插拔实现** | warehouse / collector / alerter | IoTDB TSDB · Redis Streams 队列 · SSH 密钥认证 · Webhook 通知 |

配套文档（上游）：《技术栈选型说明 v1.0》《系统架构设计 v1.0》。

---

## 技术栈

| 层 | 选型 |
|---|---|
| 语言/运行时 | Java 21 LTS（虚拟线程） |
| 框架 | Spring Boot 3.5.16 / Spring Framework 6.2 |
| 安全 | Spring Security 6 + JWT（jjwt）+ BCrypt + RBAC |
| 采集协议 | MINA SSHD 2.20 · OkHttp 4.12 · SNMP4J · ipmitool · HikariCP + JDBC（ojdbc8/Connector-J/pgjdbc） |
| 规则引擎 | Apache Commons JEXL3 |
| 存储 | PostgreSQL 16（元数据）· Redis 7（实时值/收敛/限流）· 可插拔 TSDB（默认 JPA 落盘，生产 IoTDB） |
| 邮件 | Spring Mail + Thymeleaf |
| 调度 | 时间轮触发 + 虚拟线程执行 + 协议舱壁 |
| API 文档 | springdoc-openapi（Swagger UI） |
| 构建 | Maven 多模块 |
| 部署 | Docker Compose（单机）→ Helm/K8s（集群预留） |

---

## 模块结构

```
watchpost/
├── watchpost-common      # 领域模型 · DTO · SPI 接口 · 加密/脱敏 · 枚举 · 异常
├── watchpost-warehouse   # JPA 实体/仓库 · 两级存储(Redis实时 + TSDB历史) · 队列 · Flyway DDL
│                         #   可插拔: JpaTimeSeriesStore(默认) / IotdbTimeSeriesStore · InMemoryMetricQueue(默认) / RedisStreamMetricQueue
├── watchpost-collector   # 五协议采集器 · 周期调度 · 虚拟线程 · 协议舱壁 · 模板加载/解析
│                         #   SSH 支持口令 + 私钥(PEM/passphrase)认证
├── watchpost-alerter     # 阈值规则引擎(JEXL) · 告警收敛状态机 · 通知路由 · 邮件投递/重试/限流
│                         #   可插拔: EmailNotifier(默认) / WebhookNotifier(预留)
├── watchpost-manager     # REST API · Security+JWT · RBAC · 审计(AOP) · WebSocket · 数据初始化
├── watchpost-startup     # v1 单体启动装配(manager+collector+alerter 同进程) · application.yml · IoTDB 打包 profile
├── web-app/              # React 18 + TS + Vite + AntD 5 + ECharts 前端控制台 (9 路由 + 独立登录页)
├── docs/                 # OpenAPI 契约 · 模板规范
└── deploy/               # Dockerfile(后端) · Dockerfile.frontend(前端) · nginx.conf · docker-compose.yml · .env.example
```

**依赖方向**：`startup → manager → {collector, alerter} → warehouse → common`（common 为最底层，无内部依赖）。

**集群演进**：`watchpost-collector` 可单独打包为采集器节点镜像水平扩展；队列由 memory 换 Redis Streams/Kafka；TSDB 换 IoTDB 集群。均通过 SPI 隔离，不改业务代码。

---

## 快速开始

### 前置要求

- JDK 21+（推荐 Temurin 21）
- Maven 3.9+
- PostgreSQL 16 + Redis 7（或用 Docker Compose 一键起）

### 方式一：Docker Compose（推荐，含前端）

```bash
cd deploy
cp .env.example .env
# 编辑 .env, 务必修改 WP_JWT_SECRET 和 WP_CRYPTO_KEY (>=32字节随机值)
docker compose up -d --build
# 前端控制台: http://localhost        (nginx 统一入口, 反代 /api 与 /ws)
# 后端 API:   http://localhost:1157/swagger-ui.html
# 默认管理员: admin / admin123 (请首登改密)
```

启用 IoTDB 时序库（可选，生产建议）：
```bash
docker compose --profile tsdb up -d --build
# 并在 .env 设 WP_STORE_TYPE=iotdb; 后端镜像需 -Piotdb 打包 (见方式二)
```

### 方式二：本地构建运行

```bash
# 1. 构建后端 (默认 JPA 时序落盘; 加 -Piotdb 打入 IoTDB 客户端)
mvn clean package -DskipTests            # 或 mvn clean package -Piotdb -DskipTests
# 产物: watchpost-startup/target/watchpost.jar (可执行 fat jar)

# 2. 构建前端
cd web-app && npm install && npm run build && cd ..
# 产物: web-app/dist (生产由 nginx 服务; 开发用 npm run dev, 见下)

# 3. 准备依赖 (本地 PostgreSQL + Redis, 或用 docker 单独起)
docker run -d --name wp-pg -e POSTGRES_DB=watchpost -e POSTGRES_USER=watchpost \
  -e POSTGRES_PASSWORD=watchpost -p 5432:5432 postgres:16-alpine
docker run -d --name wp-redis -p 6379:6379 redis:7-alpine

# 4. 运行后端 (注入密钥)
export WP_JWT_SECRET="your-random-32byte-minimum-secret-key-here"
export WP_CRYPTO_KEY="your-crypto-master-key-here"
java -jar watchpost-startup/target/watchpost.jar

# 5. 前端开发模式 (热更新, 代理 /api 与 /ws 到 localhost:1157)
cd web-app && npm run dev
#   前端 dev:    http://localhost:4200
#   Swagger UI:  http://localhost:1157/swagger-ui.html
#   健康检查:    http://localhost:1157/actuator/health
#   实时推送:    ws://localhost:1157/ws/realtime
```

首次启动自动：执行 Flyway 迁移（建表 + 种子）→ 创建默认管理员 → 同步内置采集模板 → 启动采集调度器。

---

## 配置说明

关键配置见 `watchpost-startup/src/main/resources/application.yml`，敏感项用环境变量注入：

| 环境变量 | 默认 | 说明 |
|---|---|---|
| `WP_DB_URL` / `WP_DB_USER` / `WP_DB_PASSWORD` | localhost:5432/watchpost | PostgreSQL 连接 |
| `WP_REDIS_HOST` / `WP_REDIS_PORT` | localhost:6379 | Redis 连接 |
| `WP_JWT_SECRET` | （开发默认，生产必改） | JWT 签名密钥，**≥32 字节** |
| `WP_CRYPTO_KEY` | （开发默认，生产必改） | 凭据 AES-256-GCM 主密钥 |
| `WP_ADMIN_USER` / `WP_ADMIN_PASSWORD` | admin / admin123 | 引导管理员 |
| `WP_STORE_TYPE` | jpa | 时序存储：`jpa`（单机）/ `iotdb`（生产） |
| `WP_QUEUE_TYPE` | memory | 指标队列：`memory`（单进程）/ `redis` / `kafka`（集群） |
| `WP_MAIL_BASE_URL` | http://localhost:1157 | 告警邮件链接基址 |

---

## 许可

Apache License 2.0
