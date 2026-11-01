# 1. 指定基础镜像（项目用的是 Java 21，我们用官方极简镜像）
FROM eclipse-temurin:21-jre-alpine

# 2. 设置工作目录
WORKDIR /app

# 3. 将你本地打包好的 jar 包复制到容器中
# 注意：这里假设你使用 mvn clean package 后生成的文件在 target 目录下
COPY target/*.jar app.jar

# 4. 暴露端口（对应你 application.yaml 里的 8080）
EXPOSE 8080

# 5. 启动命令
# 添加了 -Djava.security.egd=file:/dev/./urandom 加快启动速度
# 指定了时区为上海
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-Duser.timezone=Asia/Shanghai", "-jar", "app.jar"]