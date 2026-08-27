# JJX ERP 系统

> 薄膜开关制造企业资源计划系统，前后端分离架构。

## 技术栈

- **后端**: Spring Boot 3.4 + Java 21 + MyBatis-Plus + MySQL 8.4 + Redis
- **前端**: Vue 3.4 + Element Plus + Pinia + Vite + TypeScript
- **安全**: Sa-Token + JWT

## 快速开始

```bash
# 后端
cd jjx-server && java -jar target/jjx-server-1.0.0.jar

# 前端
cd jjx-web && pnpm install && pnpm dev
```

## 访问地址

| 地址 | 说明 |
|------|------|
| http://localhost:3000 | 前端页面 |
| http://localhost:8080 | 后端 API |
| http://localhost:8080/doc.html | API 文档（Knife4j） |

## 默认账号

- **admin** / 123456
- 数据库: `jjx_erp_db` (root / 123456)

## 项目结构

```
jjx/
├── jjx-server/          # 后端 Spring Boot
│   └── src/main/java/com/jjx/
│       ├── common/       # 通用工具
│       ├── framework/    # 框架配置
│       ├── system/       # 系统管理
│       ├── inventory/    # 库存管理
│       ├── product/      # 产品管理
│       ├── production/   # 生产管理
│       ├── purchase/     # 采购管理
│       └── sales/        # 销售管理
├── jjx-web/             # 前端 Vue 3
│   └── src/
│       ├── api/          # API 接口
│       ├── views/        # 页面组件
│       ├── router/       # 路由配置
│       └── store/        # 状态管理
└── docs/                 # 存档文档
```
