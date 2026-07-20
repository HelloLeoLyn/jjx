# JJX Kanban 文档索引

## 文档总览

| 文档 | 说明 | 状态 |
|------|------|------|
| [系统架构](architecture.md) | 整体架构设计、模块划分、技术选型决策 | ✅ 定稿 |
| [数据模型](data-model.md) | 全套数据库表设计、ER 关系、索引策略 | ✅ 定稿 |
| [API 接口规范](api-spec.md) | RESTful API 定义，含请求/响应示例 | ✅ 定稿 |
| [功能清单](features.md) | 完整功能列表 + 分阶段实施路线图 | ✅ 定稿 |
| [部署手册](deployment.md) | 开发/生产环境部署指引 | ✅ 定稿 |
| [大屏模式](big-screen.md) | 车间电视全屏展示方案 | ✅ 定稿 |
| [权限模型](permission.md) | RBAC 角色权限矩阵 | ✅ 定稿 |
| [移动端与扫码](mobile-scan.md) | PDA/手机适配、工单二维码扫码方案 | ✅ 定稿 |
| [工序耗时与报工](time-tracking.md) | 工序计时、报工表单、良率统计 | ✅ 定稿 |
| [语音播报](broadcast.md) | 异常级 TTS 播报方案 | ✅ 定稿 |

## 项目信息

| 项目 | 内容 |
|------|------|
| 项目名称 | JJX Kanban |
| 版本 | v0.1.0 (MVP) |
| 文档版本 | v1.0 |
| 最后更新 | 2026-07-18 |

## 快速索引

按角色：

- **开发者**: 先读 [架构](architecture.md) → [数据模型](data-model.md) → [API](api-spec.md)
- **部署运维**: 先读 [部署](deployment.md) → [数据模型](data-model.md)
- **产品/计划员**: 先读 [功能清单](features.md) → [大屏模式](big-screen.md) → [权限](permission.md)
- **车间管理**: 先读 [大屏模式](big-screen.md) → [播报](broadcast.md) → [耗时统计](time-tracking.md)

按实施阶段：

| 阶段 | 涉及文档 |
|------|---------|
| Phase 1 看板核心 | 架构、数据模型、API、功能清单 |
| Phase 2 大屏模式 | 大屏模式 |
| Phase 3 后端对接 | 部署、数据模型、API |
| Phase 4 权限+报工 | 权限、耗时统计 |
| Phase 5 移动+播报 | 移动端与扫码、播报 |
