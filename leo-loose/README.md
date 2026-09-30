# leo-loose：面向 AI 编程的多项目工程起步模板

状态：第二版；任务 dev-20260930-038；最后复核 2026-09-30。

适用：主要由 AI 编程代理实施、由人确定目标和关键约束的多种软件项目。语言、框架、界面、存储和部署按项目选择。本目录提供工程方法、工作流程和文档检查，不包含可启动的应用代码。

## 两个入口

- 人：本页 → [新项目启动指南](docs/how-to/start-a-project.md)。
- AI：[AGENTS.md](AGENTS.md) → 本次任务流程 → 必需的项目事实和标准。

```text
leo-loose/
├── README.md             人的入口与完整索引
├── AGENTS.md             AI 的导航与本目录约束
├── docs/
│   ├── architecture/     可选架构模式
│   ├── standards/        通用与按需采用的工程规则
│   ├── decisions/        本资料包已采用的设计决策
│   ├── how-to/           操作指南
│   └── reference/        项目选型、来源与规范映射
├── workflows/            启动、实施、诊断、审查、交接
├── templates/            填写模板，不冒充项目事实
└── checks/               独立文档检查器
```

## 带到新项目

1. 整体复制到新项目，例如 docs/engineering，保持内部结构。
2. 填写[项目约定](templates/project-profile.md)，确定项目类型、技术栈、目录、实际命令和授权范围。
3. 用[项目 AGENTS 模板](templates/project-agents.md)建立新项目根入口，替换示例路径；已有入口应合并必要导航，不直接覆盖。
4. 项目事实、ADR、任务与交接记录放入新项目自己的文档或已有任务系统。资料包保持可复用，不混入客户事实与运行数据。
5. 用一个符合项目类型的最小贯穿功能验证结构与命令，再扩展。

资料包 AGENTS.md 只约束其所在子树，不自动约束新项目整体。代理未自动识别入口时，需在配置或任务中明确加载项目根 AGENTS.md。

## 完整索引

| 分类 | 文档 |
|---|---|
| 架构 | [项目类型矩阵](docs/reference/project-shapes.md)、[模块与分层示例](docs/architecture/modular-monolith.md) |
| 标准 | [API 契约](docs/standards/api-contracts.md)、[前端组件](docs/standards/frontend-components.md)、[枚举与配置](docs/standards/enums-and-config.md) |
| 标准 | [事务与副作用](docs/standards/transactions-and-effects.md)、[权限与可观测性](docs/standards/security-and-observability.md)、[验证与协作](docs/standards/engineering-quality.md) |
| 指南 | [新项目启动](docs/how-to/start-a-project.md) |
| AI 流程 | [启动](workflows/bootstrap.md)、[实施](workflows/implement.md)、[诊断](workflows/diagnose.md)、[审查](workflows/review.md)、[交接](workflows/handoff.md) |
| 项目模板 | [项目约定](templates/project-profile.md)、[模块设计](templates/module-design.md)、[项目 AGENTS](templates/project-agents.md) |
| 记录模板 | [架构决策](templates/architecture-decision.md)、[任务](templates/task.md)、[交接](templates/handoff.md) |
| 决策 | [AI 入口与多项目适配](docs/decisions/0001-ai-oriented-kit.md) |
| 来源 | [实践来源与局限](docs/reference/source-map.md)、[公开规范映射](docs/reference/public-practices.md) |
| 检查 | [用法与边界](checks/README.md)、[检查程序](checks/check-docs.py) |

## 采用与维护

实践提炼表示来源已有相应机制，不表示全项目完全统一或全面验证。建议基线表示新项目需要选择采用并实现。模板与可选方案不是项目事实。

规则只在明确采用范围内生效；当前用户指令与所在仓库上层约束优先。不默认授权建表、运行服务、部署或发布。

CLI 不必采用 HTTP 分页，纯库不必引入用户系统或数据库。实际项目入口中的命令必须可核验；模板的待填写项可以保留。

一个主题维护一个权威位置，工作流引用标准；ADR 保留理由，Git 保留历史正文。Markdown 使用 UTF-8，检查器兼容有无 BOM。
