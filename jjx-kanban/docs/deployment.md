# 部署手册

## 1. 环境要求

### 1.1 前端

| 项目 | 要求 |
|------|------|
| Node.js | ≥ 18.x |
| npm / pnpm | npm ≥ 9.x / pnpm ≥ 8.x |
| 浏览器 | Chromium ≥ 90 / Edge ≥ 90 / Firefox ≥ 90 |

### 1.2 后端（待实施）

| 项目 | 要求 |
|------|------|
| JDK | ≥ 17 |
| Maven | ≥ 3.8 |
| MySQL | ≥ 8.0 |

---

## 2. 开发环境

### 2.1 前端启动

```bash
cd jjx-kanban

# 安装依赖
npm install

# 启动开发服务器（Mock 数据模式）
npm run dev

# 启动后访问 http://localhost:5173
```

### 2.2 构建生产包

```bash
npm run build

# 产物输出到 dist/ 目录
# dist/ 可直接部署到 Nginx
```

### 2.3 后端启动（待实施）

```bash
cd jjx-server

# 初始化数据库
mysql -u root -p < docs/sql/init.sql

# 启动服务
mvn spring-boot:run
```

---

## 3. 生产部署

### 3.1 前端 Nginx 部署

```nginx
# /etc/nginx/sites-available/kanban.jjx.com

server {
    listen 80;
    server_name kanban.jjx.com;

    # 前端静态文件
    root /var/www/jjx-kanban/dist;
    index index.html;

    # SPA 路由重定向
    location / {
        try_files $uri $uri/ /index.html;
    }

    # API 反向代理
    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # 静态资源缓存
    location /assets/ {
        expires 1y;
        add_header Cache-Control "public, immutable";
    }

    # Gzip 压缩
    gzip on;
    gzip_types text/plain application/json text/css application/javascript;
    gzip_min_length 1024;
}
```

### 3.2 后端部署

```yaml
# docker-compose.yml

version: '3.8'

services:
  kanban-server:
    image: jjx/kanban-server:latest
    ports:
      - "8080:8080"
    environment:
      - SPRING_DATASOURCE_URL=jdbc:mysql://db:3306/jjx_kanban?useSSL=false&serverTimezone=Asia/Shanghai
      - SPRING_DATASOURCE_USERNAME=root
      - SPRING_DATASOURCE_PASSWORD=${DB_PASSWORD}
      - ERP_API_BASE=http://erp-server:8081
      - JWT_SECRET=${JWT_SECRET}
    depends_on:
      - db
    restart: unless-stopped

  db:
    image: mysql:8.0
    ports:
      - "3307:3306"
    environment:
      - MYSQL_DATABASE=jjx_kanban
      - MYSQL_ROOT_PASSWORD=${DB_PASSWORD}
    volumes:
      - mysql_data:/var/lib/mysql
      - ./sql/init.sql:/docker-entrypoint-initdb.d/init.sql
    restart: unless-stopped

volumes:
  mysql_data:
```

### 3.3 Docker 构建

```dockerfile
# Dockerfile (前端)

FROM node:20-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
```

```dockerfile
# Dockerfile (后端)

FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

---

## 4. 环境变量

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `VITE_API_BASE` | (空) | API 基础路径，空则使用 Mock |
| `SPRING_DATASOURCE_URL` | 必填 | 数据库连接串 |
| `ERP_API_BASE` | 必填 | ERP 服务地址 |
| `JWT_SECRET` | 必填 | JWT 加密密钥 |
| `BROADCAST_ENABLED` | true | 是否启用语音播报 |

---

## 5. 数据库初始化

```sql
-- 创建数据库
CREATE DATABASE IF NOT EXISTS jjx_kanban
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

-- 建表语句见 data-model.md
```

---

## 6. 监控与运维

### 6.1 健康检查

```
GET /api/v1/health
→ { "status": "UP", "db": true, "erpSync": true }
```

### 6.2 日志

```
# 后端日志
/var/log/kanban-server/app.log

# Nginx 访问日志
/var/log/nginx/kanban.access.log
```

### 6.3 常见问题

| 问题 | 排查方向 |
|------|---------|
| 前端页面白屏 | 检查 API 是否可通，确认 `VITE_API_BASE` 正确 |
| 拖拽不生效 | 检查浏览器控制台是否有 JS 错误 |
| 语音播报无声 | 检查浏览器是否允许自动播放，确认系统语音包 |
| 数据不更新 | 检查 ERP 同步是否正常工作 |
