# JJX Kanban — 制造车间多模板看板系统

薄膜开关制造企业生产任务可视化管理系统。支持生产工单、办公室任务、紧急任务多模板管理，多维度视图切换，适合车间大屏、班组管理、办公室协同等多种场景。

## 技术栈

- **前端**: Vue 3 + TypeScript + Element Plus + Pinia + Vuedraggable
- **后端**: Spring Boot（待接入）
- **构建**: Vite 8

## 核心特性

| 特性 | 说明 |
|------|------|
| 多模板看板 | 生产工单 / 办公室任务 / 紧急任务，独立配置 |
| 多视图切换 | 工序视图、紧急度视图、交期视图、状态视图、负责人视图、部门视图 |
| 拖拽交互 | 卡片跨列拖拽，Vuedraggable 驱动 |
| 卡片操作 | 新建卡片、查看详情、编辑备注 |
| 筛选体系 | 关键字搜索、负责人筛选、优先级筛选 |
| 语音播报 | 异常级 TTS 播报（逾期/阻塞/积压阈值），定时+事件触发 |
| 角色权限 | 操作工/班组长/质检员/计划员/管理员 分级权限（待开发） |
| 大屏模式 | 车间电视全屏展示，自动刷新（待开发） |
| 扫码接入 | 工单二维码扫码跳转卡片详情（待开发） |

## 快速开始

```bash
# 安装依赖
npm install

# 启动开发服务器（Mock 数据）
npm run dev

# 构建生产包
npm run build
```

## 项目结构

```
jjx-kanban/
├── src/
│   ├── types/           # TypeScript 类型定义
│   ├── config/          # 看板模板/视图配置
│   ├── mock/            # Mock 数据 & API
│   ├── stores/          # Pinia 状态管理
│   ├── components/      # 通用组件
│   ├── views/           # 页面
│   ├── api/             # 后端 API 接入层（待接）
│   ├── utils/           # 工具函数
│   └── router/          # 路由
├── docs/                # 项目文档
└── index.html
```

## 文档索引

- [项目文档索引](docs/README.md)
- [系统架构](docs/architecture.md)
- [数据模型](docs/data-model.md)
- [API 接口规范](docs/api-spec.md)
- [部署手册](docs/deployment.md)
