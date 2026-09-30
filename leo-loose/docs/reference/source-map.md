# 来源与适用边界

状态：第一版来源说明；任务 dev-20260930-036；最后复核 2026-09-30。

本资料基于 JJX 仓库的只读代码审阅与现行协作约束提炼。以下路径为来源仓库定位信息，不是新项目依赖；因此以文本记录，不建立指向目录外的链接。未进行生产性能验证或全面安全认证。

## 有实现依据的模式

| 模式 | 来源路径 | 可提炼部分 | 采用前注意 |
|---|---|---|---|
| 分层与数据转换 | `jjx-server/src/main/java/com/jjx/product/domain/`、`product/service/` | DTO、VO、Converter 与持久化区分 | 各模块组织并不完全一致 |
| 统一响应与分页 | `jjx-server/src/main/java/com/jjx/common/core/result/Result.java`、`common/core/page/PageQuery.java`、`PageResult.java` | 公共契约、单页上限 | 补齐 HTTP、错误与客户端一致性；本文符号错误码为新项目建议 |
| 命名枚举 | `jjx-web/src/enums/base.ts` | 集中定义、标签和选择项复用 | UI 库依赖可以隔离；canDo 的成员检测语义需澄清 |
| 状态增量门禁 | `jjx-web/scripts/check-status-magic-values.mjs` | 检查器与只减基线 | 按新项目语法和目录适配，不复制历史债务 |
| 操作列 | `jjx-web/src/components/common-ui/TableActionColumn/types.ts`、`index.vue` | 声明动作、权限、禁用原因与加载态 | 前端判断不能代替服务端授权 |
| 前端复用 | `jjx-web/src/components/page-skeleton/`、`composables/useTable.ts` | 布局组合与交互抽取 | useTable 需补旧响应竞争等边界；选定唯一组件入口 |
| 集中写入口 | `jjx-server/src/main/java/com/jjx/inventory/service/impl/InventoryStockMutationServiceImpl.java` | 将约束、锁和修改集中封装 | 仅提炼技术模式，不迁移业务规则与表结构 |
| 并发检查 | `jjx-server/src/main/java/com/jjx/production/mapper/ProductionTaskMapper.java` | 行锁、条件更新和影响行数校验 | 锁粒度与事务范围由新项目重新设计 |
| 权限接入 | `jjx-server/src/main/java/com/jjx/framework/config/SaTokenConfig.java`、`jjx-web/src/composables/usePermission.ts` | 集中认证与统一权限访问入口 | 来源仍有权限覆盖不足和数据权限空实现 |
| 日志横切能力 | `jjx-server/src/main/java/com/jjx/system/annotation/Log.java`、`system/aspect/OperLogAspect.java` | 声明式审计、结构化上下文 | 日志服务跨模块耦合与投递可靠性不能照搬 |
| 有界日志执行器 | `jjx-server/src/main/java/com/jjx/system/config/AsyncConfig.java` | 指定线程池、队列与饱和策略 | 调用线程执行会影响延迟，不等于持久化保证 |
| 工程治理 | `AGENTS.md`、`jjx-docs/standards/CONVENTIONS.md`、`scripts/check-docs.mjs` | 授权范围、定向提交、文档索引与检查 | 数据库任务码、自动推送、备份位置等为项目专属规则 |

## 本资料补全的建议，不能声称来源已实现

- 模块依赖的自动强制与公共层完全不依赖具体模块。
- 统一 OpenAPI 真源、生成客户端与契约兼容检查。
- 全局一致的 HTTP 错误语义与安全错误响应。
- 完整请求追踪、异步上下文传播与请求结束清理。
- 持久化事件、受控重试和可恢复外部副作用。
- 每个接口的功能与对象授权，以及多实例配置传播。
- 完整后端 CI、隔离数据库验证与必需检查的严格失败语义。

## 任务教训层的来源

`docs/lessons/` 的初始条目来自 JJX `sys_task` 中已有任务描述，保留任务码和证据状态。任务教训不是代码现状的自动证明：任务状态、描述和提交证据仍需在来源项目中复核；本资料包只提炼失败机制和可迁移原则，不复制业务数据。

上述建议旨在使新项目拥有完整设计，不是对来源项目实现状态的描述。它们也不构成对来源系统的整改或政策修改。

## 不携带的内容

本目录不包含行业流程、业务枚举取值清单、真实数据、数据库备份、账号、口令、服务器地址、部署凭据和生产配置。来源技术栈只作为实例，不锁定新项目版本。

目录独立复制后，阅读与模板使用仍可完成。若需要移植源代码，应另行审查依赖、授权条件、测试与适用边界，不能把本资料等同于源代码分发授权。
