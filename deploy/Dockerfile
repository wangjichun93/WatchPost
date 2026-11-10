# =====================================================================
# 望哨 WATCHPOST · 应用镜像 (多阶段构建)
# 阶段1: Maven 构建 (JDK 21) -> 阶段2: 精简运行时 (JRE 21)
# 构建上下文为工程根目录: docker build -f deploy/Dockerfile -t watchpost:1.0.0 .
# =====================================================================

# ---- 构建阶段 ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
# 先复制 POM 利用依赖缓存层
COPY pom.xml .
COPY watchpost-common/pom.xml watchpost-common/
COPY watchpost-warehouse/pom.xml watchpost-warehouse/
COPY watchpost-collector/pom.xml watchpost-collector/
COPY watchpost-alerter/pom.xml watchpost-alerter/
COPY watchpost-manager/pom.xml watchpost-manager/
COPY watchpost-startup/pom.xml watchpost-startup/
RUN mvn -q -B dependency:go-offline -DskipTests || true
# 复制源码并打包
COPY watchpost-common/src watchpost-common/src
COPY watchpost-warehouse/src watchpost-warehouse/src
COPY watchpost-collector/src watchpost-collector/src
COPY watchpost-alerter/src watchpost-alerter/src
COPY watchpost-manager/src watchpost-manager/src
COPY watchpost-startup/src watchpost-startup/src
RUN mvn -q -B clean package -DskipTests

# ---- 运行时阶段 ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
# 带外采集兜底: ipmitool (IPMI 降级路径); net-snmp 工具 (SNMP 兜底)
RUN apk add --no-cache ipmitool net-snmp-tools wget tzdata \
    && cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
    && echo "Asia/Shanghai" > /etc/timezone
# 非 root 运行 (安全基线)
RUN addgroup -S watchpost && adduser -S watchpost -G watchpost
COPY --from=build /build/watchpost-startup/target/watchpost.jar /app/watchpost.jar
RUN chown -R watchpost:watchpost /app
USER watchpost
EXPOSE 1157
# 虚拟线程友好: 容器感知内存, 限制载体线程
ENV JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseContainerSupport"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/watchpost.jar"]
HEALTHCHECK --interval=15s --timeout=5s --retries=10 --start-period=40s \
    CMD wget -qO- http://localhost:1157/actuator/health || exit 1
