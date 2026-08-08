# 📚 文档中心目录规范

> 版本: v1.0 | 最后更新: 2026-08-01
> 目的: docs/ 作为系统"活资料"，可追溯可信赖

---

## 一、目录结构

```
docs/
├── flows/          # 业务流程分析（端点级，每模块一份）
│   ├── sales-flow-analysis.md
│   ├── purchase-flow-analysis.md
│   ├── inventory-flow-analysis.md
│   ├── production-flow-analysis.md
│   ├── engineering-flow-analysis.md
│   └── phase*.html          # 流程图（7阶段导航）
├── specs/          # 规范文档（新开发必须对照）
│   ├── state-machine-spec.md    # 状态机统一规范
│   ├── data-inheritance-spec.md # 数据传承规则
│   ├── dict-maintenance-spec.md # 字典维护规范
│   ├── status-visual-spec.md    # 状态可视化规范
│   └── event-flow-analysis.md   # 事件体系分析
├── analysis/       # 历史分析报告（含已解决/过期的）
├── test/           # 测试工作台 + 测试标准
│   ├── index.html              # 测试工作台 v2.0
│   ├── 测试标准.md
│   ├── 测试数据定义.md
│   └── 测试报告-*.md
├── tasks/          # 任务看板
├── task-dashboard/ # 可视化任务看板
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
| 一次性分析报告 | analysis/ |
| 测试相关 | test/ |

## 四、废弃文档处理

- 与现状脱节的文档：**标注"已废弃"**，移到 analysis/，不删除（保留历史）
- 已解决的方案文档：标注"已实施"，归档 analysis/
