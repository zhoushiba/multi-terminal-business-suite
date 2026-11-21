# =====================================================================
# 多端业务中台套件 · Docker 镜像构建（多阶段）
# 本机未安装 Docker，本文件仅作为交付的部署方案；在装有 Docker 的机器上可直接构建
# =====================================================================
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# 先只拷 pom，利用镜像层缓存加速依赖下载
COPY pom.xml .
RUN mvn -B -q -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app

# 健康检查用 curl；不影响主进程
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# pom.xml 中 finalName = business-suite-backend
COPY --from=build /app/target/business-suite-backend.jar app.jar

ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -Duser.timezone=Asia/Shanghai"

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
