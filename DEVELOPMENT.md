# 本地开发与测试指南

本文档说明如何在本地启动后端、初始化测试库以及运行自动化测试。

> **安全基线**：`src/test/resources/application.yaml` 不包含任何明文密码。运行测试前必须通过环境变量注入测试库密码与 MinIO 凭据。

---

## 1. 环境要求

| 依赖 | 版本要求 |
|---|---|
| JDK | 21 |
| Maven | 3.9+（推荐使用仓库自带的 `./mvnw`） |
| Docker | 24+（用于启动 MySQL / Redis / MinIO） |
| Git | 2.30+ |

---

## 2. 启动本地中间件

使用开发环境 Compose 启动 MySQL、Redis、MinIO：

```bash
docker compose up -d mysql redis minio
3. 初始化测试库
自动化测试会连接独立的 lab_management_test 库，严禁连接主库。首次运行测试前执行：

Windows (PowerShell)
powershell
# 用 .env 中的 root 密码登录
$env:MYSQL_ROOT_PASSWORD = "你的本地 MySQL root 密码"

docker exec -i labsystem-mysql mysql -uroot -p"$env:MYSQL_ROOT_PASSWORD" -e "
  DROP DATABASE IF EXISTS lab_management_test;
  CREATE DATABASE lab_management_test;
"
docker cp src/main/resources/db/migration/V1__init.sql labsystem-mysql:/tmp/V1.sql
docker exec -i labsystem-mysql mysql -uroot -p"$env:MYSQL_ROOT_PASSWORD" lab_management_test -e "source /tmp/V1.sql"
docker cp run_test_data.sql labsystem-mysql:/tmp/run_test_data.sql
docker exec -i labsystem-mysql mysql -uroot -p"$env:MYSQL_ROOT_PASSWORD" lab_management_test -e "source /tmp/run_test_data.sql"
Linux / macOS
bash
docker exec -i labsystem-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "
  DROP DATABASE IF EXISTS lab_management_test;
  CREATE DATABASE lab_management_test;
"
docker cp src/main/resources/db/migration/V1__init.sql labsystem-mysql:/tmp/V1.sql
docker exec -i labsystem-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" lab_management_test -e "source /tmp/V1.sql"
docker cp run_test_data.sql labsystem-mysql:/tmp/run_test_data.sql
docker exec -i labsystem-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" lab_management_test -e "source /tmp/run_test_data.sql"
4. 运行自动化测试
测试配置 src/test/resources/application.yaml 已移除明文密码，运行前必须设置以下环境变量：

环境变量	说明
DB_PASSWORD	lab_management_test 库的密码
MINIO_ACCESS_KEY	MinIO 访问用户名（开发环境默认为 labminio）
MINIO_SECRET_KEY	MinIO 访问密钥
Windows (PowerShell)
powershell
$env:DB_PASSWORD = "你的测试库密码"
$env:MINIO_ACCESS_KEY = "labminio"
$env:MINIO_SECRET_KEY = "你的本地 MinIO 密钥"
.\mvnw.cmd test
Linux / macOS
bash
export DB_PASSWORD="你的测试库密码"
export MINIO_ACCESS_KEY="labminio"
export MINIO_SECRET_KEY="你的本地 MinIO 密钥"
./mvnw test
IntelliJ IDEA
Run/Debug Configurations → 选中 LabsystemApplicationTests / SecurityHardeningTest → Environment variables 填入上述三项。

如果未设置环境变量，测试会因数据源密码为空而启动失败，这是预期行为，用于防止意外把明文密码提交到代码仓库。

5. 安全测试说明
SecurityHardeningTest 覆盖了越权、并发、异常脱敏、文件下载对象级权限等安全回归点。

运行前请确认：

当前 Spring Profile 为 test（或默认 dev）。

app.security.test-login-enabled=true（由 src/test/resources/application.yaml 提供）。

数据库 URL 必须包含 _test 后缀，测试类会在启动时强制校验，若连接主库会直接终止。

6. 常用命令
命令	说明
./mvnw spring-boot:run	本地启动后端（需设置 DB_PASSWORD 等环境变量）
./mvnw clean package -DskipTests	打包（跳过测试）
./mvnw test	运行全部测试
./mvnw test -Dtest=SecurityHardeningTest	只跑安全回归测试
docker compose up -d	启动本地全套中间件
docker compose down	停止本地中间件（保留数据卷）
---

## 7. 监控与告警

### 7.1 指标采集

后端通过 Micrometer 暴露 Prometheus 指标：

```
GET /actuator/prometheus
```

`prometheus.yml` 中以 `labsystem-backend` 为 job 名抓取该端点。生产环境由
`docker-compose.prod.yml` 启动 Prometheus / Alertmanager / Grafana 三个容器。

### 7.2 告警规则

规则定义在 `alerts.yml`，共 12 条，分为四组：

| 分组 | 规则 |
|---|---|
| 可用性 | `BackendDown`、`BackendNoTraffic` |
| 错误率 | `HighServerErrorRate`（5xx > 5%）、`ServerErrorBurst`、`HighClientErrorRate` |
| 延迟 | `HighP95Latency`（P95 > 1s） |
| 资源 | `JvmHeapUsageHigh`、`ProcessCpuHigh`、`HikariPoolSaturated`、`HikariConnectionsPending`、`TomcatThreadsNearLimit` |
| 监控自身 | `PrometheusTargetMissing` |

> P95 延迟依赖 `http.server.requests` 的直方图数据，已在 `application.yaml` 中通过
> `management.metrics.distribution.percentiles-histogram` 开启，请勿移除。

本地校验规则语法（需要 promtool）：

```bash
promtool check rules alerts.yml
```

### 7.3 配置告警渠道

`alertmanager.yml` 默认只有一个 webhook 占位接收器。按需替换：

- **钉钉**：需要 `prometheus-webhook-dingtalk` 之类的适配器转换 payload，
  然后把 webhook url 指向适配器。
- **企业微信**：同样需要适配器转成机器人接受的 markdown 格式。
- **邮件**：取消 `email_configs` 的注释，并填写 SMTP 信息。

渠道未配置时，告警仍然可以在 Prometheus 的 `/alerts` 页面看到，只是不会外发。

### 7.4 验证

```bash
# 启动监控组件
docker compose -f docker-compose.prod.yml up -d prometheus alertmanager grafana

# Prometheus 目标是否 UP
open http://<host>:9090/targets

# 当前告警
open http://<host>:9090/alerts

# Alertmanager 收到的告警
open http://<host>:9093
```

> 9090 / 9093 / 3000 默认直接暴露在宿主机上，生产环境建议用 Nginx 反向代理并加访问控制。
