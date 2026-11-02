# 1. 指定基础镜像（Java 21 极简版）
FROM eclipse-temurin:21-jre-alpine

# 2. 设置时区为上海
RUN apk add --no-cache tzdata && \
    cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone

# 3. 设置工作目录
WORKDIR /app

# 4. 将本地打包好的 jar 包复制到容器中
COPY target/*.jar app.jar

# 5. 暴露端口
EXPOSE 8080

# 6. 启动命令（加入 JVM 生产级内存和 GC 优化参数）
ENTRYPOINT ["java", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-Duser.timezone=Asia/Shanghai", \
    "-Xms512m", "-Xmx1024m", \
    "-XX:+UseG1GC", \
    "-jar", "app.jar"]