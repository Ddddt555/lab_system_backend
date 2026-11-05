# 实验教学管理系统 (Lab Teaching Management System) - 后端

基于 Spring Boot 3 + Vue 3 的前后端分离架构。

> 💡 **致谢说明**：本项目使用了 **DeepSeek AI** 进行了优化与改进。

## 🌟 核心特性 (Features)

### 1. 架构与工程化
- **前后端分离**：后端提供 RESTful API，前端独立打包部署。
- **容器化部署**：全栈 Docker 化，提供 `docker-compose.prod.yml` 一键拉起全套环境。
- **CI/CD 自动化**：集成 GitHub Actions，自动执行测试、代码规范检查、构建镜像并推送至 Docker Hub。
- **多环境隔离**：严格的 `dev` 与 `prod` 配置隔离，敏感信息全量环境变量注入。

### 2. 安全与稳定性 (Security & Stability)
- **认证与授权**：双 Token 机制（Access + Refresh），无感刷新；基于 `@RequireRole` 的切面级权限控制。
- **接口防爆破**：登录失败锁定策略、图形验证码、Redisson 分布式限流。
- **文件安全**：严格的后缀白名单 + 文件头魔数校验 + ZIP 炸弹防护；MinIO 对象级权限代理访问。
- **数据一致性**：分布式锁防超卖、乐观锁兜底、Flyway 严格把控数据库版本。

### 3. 业务闭环
涵盖学生、教师、管理员三端：选课中心、场次预约、候补队列（Redis Pub/Sub 实时通知）、考勤签到（WebSocket 实时推送）、报告批阅与成绩核算。

---

## 🛠 技术栈 (Tech Stack)

- **后端核心**：Java 21, Spring Boot 3.3.5, MyBatis-Plus
- **数据库与中间件**：MySQL 8.0, Redis 7 (Redisson), MinIO
- **认证与安全**：Spring Security Crypto (BCrypt), JJWT
- **监控与日志**：Spring Boot Actuator, Prometheus, Micrometer, TraceId 全链路追踪
- **部署与运维**：Docker, Docker Compose, Nginx, GitHub Actions

---

## 🚀 快速启动 (Quick Start)

### 前置要求
- 安装 Docker 和 Docker Compose
- 确保所需端口未被占用（默认：80, 8080, 3306, 6379, 9000）

### 启动步骤

**1. 克隆仓库**

    git clone https://github.com/Ddddt555/lab_system_backend.git
    cd lab_system_backend

**2. 配置环境变量**

复制 `.env.example` 为 `.env`（或直接在生产环境注入变量），并填入真实的密码和密钥。

**3. 一键启动生产环境**

    docker compose -f docker-compose.prod.yml up -d

**4. 访问系统**

- 前端页面：`http://localhost` （或配置的域名）
- 后端 API 文档 (仅 dev 环境)：`http://localhost:8080/doc.html`
- 健康检查：`http://localhost:8080/actuator/health`

---

## 📂 目录结构说明

    ├── .github/workflows    # CI/CD 自动化流水线配置
    ├── db/init              # MySQL 首次启动自动化初始化脚本
    ├── scripts/             # 备份与恢复脚本 (sh / ps1)
    ├── src/main/java        # 核心业务代码
    │   ├── aspect           # AOP 切面 (日志、限流、鉴权)
    │   ├── config           # 各项配置 (安全、拦截器、WebSocket)
    │   ├── controller       # RESTful 接口层
    │   ├── service          # 业务逻辑层
    │   └── mapper           # 数据访问层
    ├── src/main/resources   # 配置文件及 Flyway 迁移脚本
    └── docker-compose.yml   # 生产环境容器编排

---

## 🔒 安全审计备忘

本项目已落实以下安全基线：
- 消除历史 Git 记录中的明文密码及敏感备份数据。
- 测试环境严禁连接生产库，使用环境变量注入凭据。
- 引入 `.dockerignore` 防止敏感文件被打入镜像。
- 镜像构建集成 Trivy 漏洞扫描（持续跟踪依赖升级）。