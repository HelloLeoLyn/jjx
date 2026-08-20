# 📚 文档中心目录规范

> 版本: v1.1 | 最后更新: 2026-08-20
> 目的: jjx-docs/ 作为系统"活资料"，可追溯可信赖

---

## 一、目录结构

```
jjx-docs/
├── flows/          # 业务流程分析（端点级，每模块一份）+ 流程图
│   ├── sales-flow-analysis.md / purchase / inventory / production / engineering
│   ├── biz-flow-full.md / production-inventory-purchase-detail.md
│   ├── order-to-production-target-flow.md
│   └── phase1~phase6-*.html      # 流程图（7阶段导航）
├── specs/          # 规范文档（新开发必须对照）
│   ├── state-machine-spec.md    # 状态机统一规范
│   ├── data-inheritance-spec.md # 数据传承规则
│   ├── dict-maintenance-spec.md # 字典维护规范
│   ├── status-visual-spec.md    # 状态可视化规范
│   ├── event-flow-analysis.md   # 事件体系分析
│   ├── dispatch-spec.md / dispatch-redesign-v2.md   # 派工规范
│   ├── scan-execution-spec.md   # 扫码执行规范
│   └── tooling-mold-spec.md     # 工装模具规范
├── analysis/       # 分析/实施/验收报告（含 Production P0-P4 全量）
│   ├── production-p0~p4-*.md    # P0-P4 各阶段设计/实施/验收/评审
│   ├── production-v1-final-review.md / production-v1-release-fix-report.md
│   ├── 通用分析: db-audit / module-redesign / kanban-optimization / eventbus 等
│   └── history/                 # 历史归档
├── reference/      # 参考文档
│   ├── 01-业务流程.md / 02-模块状态.md / 03-开发规范.md / 04-Redis方案.md
│   ├── component-usage/         # 组件/服务使用文档（2026-08-20 迁移）
│   │   ├── BomItemEditor.usage.md
│   │   ├── ProductCategorySelect.usage.md
│   │   └── README.md            # （原 services/sales/README.md）
│   └── redis-sequence-implementation-summary.md / 日志管理系统实现总结.md
├── requirements/   # 需求文档
│   └── product-stock-requirement.md / .html
├── sql/            # SQL 脚本（初始化/迁移/备份）
│   ├── product_initial_data.sql       # 产品初始脚本（2026-08-20）
│   ├── auth-rebuild / auth-users-english / production-org-data
│   ├── dispatch-module / tooling-module / inventory_stock_refactor
├── task-analysis/  # 单任务分析
├── task-dashboard/ # 可视化任务看板 + 权限审计
├── tasks/          # 任务看板数据（index.html + tasks.json）
├── test/           # 测试工作台 + 测试标准
│   ├── index.html              # 测试工作台
│   ├── 测试标准.md / 测试数据定义.md / 测试报告-*.md
├── accounts/       # 账号说明（index.html）
├── index.html      # 文档中心首页（九宫格）
└── server.js       # 文档中心服务（8899）
```

## 二、文档标注规范

每份文档头部必须标注：

```markdown
> 最后更新: YYYY-MM-DD
> 基于代码版本: <git commit 或分支>
```

**防止陈旧文档误导**：更新代码后，涉及文档要同步更新日期。

## 三、新文档放哪

| 文档类型 | 位置 |
|---|---|
| 模块业务流程分析 | flows/ |
| 设计/开发规范 | specs/ |
| 一次性分析/实施/验收报告 | analysis/ |
| 组件/服务使用说明 | reference/component-usage/ |
| 需求文档 | requirements/ |
| SQL 脚本（初始化/迁移/备份） | sql/ |
| 测试相关 | test/ |

## 四、废弃文档处理

- 与现状脱节的文档：**标注"已废弃"**，移到 analysis/history/，不删除（保留历史）
- 已解决的方案文档：标注"已实施"，归档 analysis/history/

## 五、注意事项

- `node_modules/` 为文档中心 server.js 的运行依赖，勿提交/勿修改
- 生产相关新报告（P 系列）统一命名 `production-pX-*.md` 放 analysis/，随代码提交
