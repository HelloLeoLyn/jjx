# 文档作业指导书（速查卡 + 规则）

状态：✅已实施
任务：dev-20260914-004
被取代 / 取代：无
什么情况看这篇：要新建、整理、查找文档时，先看这一页
最后复核：2026-09-14

> 一句话：**写完的文档放哪、叫什么名、头信息写什么、登记到哪、跑什么检查，全在这一页。**

## 一、三分钟速查卡

| 我要写什么 | 放哪 | 文件名 | 登记到哪 | 跑什么检查 |
|---|---|---|---|---|
| 模块**现在**是什么样 | `modules/` | `<模块>.md`（不带日期） | README 的 modules 模块表里勾状态 | `npm run check:docs` |
| 当时怎么做的（实施记录/分析/核查/测试报告） | `history/` | `<主题>-dev-YYYYMMDD-NNN.md` | **`history/INDEX.md`（必须登记）** | `npm run check:docs` |
| 为什么这么设计（流程/架构/方案） | `design/` | `<主题>-YYYYMMDD.md` | 不用登 | `npm run check:docs` |
| 怎么干一件事（手册/排障/部署） | `guides/` | `<主题>-YYYYMMDD.md` | README 入口表按需加一行 | `npm run check:docs` |
| 查事实（权限矩阵/清单/组件用法） | `reference/` | `<主题>-YYYYMMDD.md` | 不用登 | `npm run check:docs` |
| 一条决策（带理由和后果） | `decisions/` | `<主题>-YYYYMMDD.md` | 不用登 | — |
| 原始需求材料（含导入源数据） | `requirements/`、`sources/` | 原名 | — | — |
| 过期件 | `archive/YYYY/` | 原名 | 头部写"已被 X 取代" | — |

写完都要：**UTF-8 带 BOM**（手机阅读不乱码）。

## 二、命名规则（只记三条）

1. `history/` `design/` `guides/` `reference/` `decisions/` 一律带日期：`<主题>-YYYYMMDD.md`；有任务码写 `<主题>-dev-YYYYMMDD-NNN.md`。
2. `modules/` 一个模块一篇，**不带日期**（`<模块>.md`）。
3. 禁止 `final` / `new` / `copy` / `_v2` 后缀——版本交给 git。

## 三、文档头（正文第一屏固定 5 行）

```
状态：✅已实施 / ⏳待做 / 待确认 / 已废弃（带任务号）
任务：dev-YYYYMMDD-NNN
被取代 / 取代：路径（没有写"无"）
什么情况看这篇：一句话
最后复核：YYYY-MM-DD   ← modules/ 必填
```

## 四、索引（谁在什么时候加一行）

- `README.md` 入口表：**出现新场景**才加一行（不逐篇加）。
- `history/INDEX.md`：新建 history 文档**必须登记**（门禁零容忍，没登记直接红）。
- `guides/`、`reference/`：改手册时同步目录说明；reference 暂不单独建清单。
- `modules/`：进度在 README 的模块表里勾（已写/待写）。

## 五、作业流程

判类型 → 选目录 → 起名 → 写头信息 → 登记索引 → `cd jjx-web && npm run check:docs` → 提交。

**改名前先 `git grep` 全仓引用**（`sys_task.description`、脚本头注释、技能文档里的路径都要同步，否则指向失效）。

## 六、禁止

- 不给历史快照写摘要、不建 CMS、不建大表/门户；真要目录只能是"派生的目录"（真相写在文档头）。
- 根目录禁散文件、临时文件（`~$` 等）、node_modules、双份备份（用 git）。
- **判断时效只看 `README.md` 那张表，不看文件 mtime**（本仓库 mtime 被历次整理刷新过）。

## 七、常见问题快查

| 问题 | 去哪 |
|---|---|
| 某模块现在是什么样？ | `modules/<模块>.md` |
| 当时怎么做的 / 为什么？ | `history/INDEX.md` → 按文件名或主题找 |
| 流程/架构长什么样？ | `design/` |
| 某脚本怎么跑、有什么危险？ | `guides/scripts-commands-20260914.md` |
| 规矩（备份/迁移/提交/讨论边界）？ | `standards/CONVENTIONS.md` + 根 `AGENTS.md` |
| 这份文档还有效吗？ | `README.md` 入口表 + 文档头的状态行 |
