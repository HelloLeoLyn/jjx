# JJX ERP 质量模块实证差距分析报告

- 报告日期：2026-09-02
- 方法：代码实扫 + 数据库实查（质量功能代码散落在 production/product 包，按业务聚合分析）

---

## 1. 现状盘点（实证）

### 1.1 表与行数

| 表 | 行数 | 判定 |
|---|---|---|
| quality_template_registry | 100 | 全量（QR-001~100，2026-09-02 维护入口已迁文档管理 316） |
| quality_template_print_log | 0 | 空转（1237 已加 biz_type/biz_id，待验收） |
| production_quality_inspection(-item) | 0 / 0 | 空转（质检单主从） |
| production_quality_inspection 关联执行 | - | 见 production 报告 |

### 1.2 后端能力

- QualityInspectionController（/production/quality，11 端点）：page/detail/CRUD/judge(:61 判定)/reinspect(:68 复检)/statistics/export-pdf/export-excel
- QualityActionServiceImpl：judge/reinspect/createFqcForExecution（已接通 auto-FQC，P3-C）
- QualityTemplateRegistryController（/production/quality-template）：CRUD + print-log（1237 后带 biz 参数）
- 质检打印页：quality-print 下 fqc-report/iqc-report/daily-report/first-piece/rework-form 5 张（6 份 data 模板已联动）

### 1.3 前端与菜单

质检管理(264)+质检报告(265) 菜单 → views/production/quality（index/report）✅；质量记录模板(315) 已迁文档管理 ✅；打印中心 quality-print 静态路由（hidden，业务模块跳转进入）。移动端质检判定页 /m/quality（1150a9d 交付待验收）。

### 1.4 数据联动模板（category='data' 6 份）

进料检验报告←inbound、成品检验报告←FQC、生产日报←报工聚合、印刷/冲型首件←工序首检、返工返修单←FQC FAIL——打印页已实现（2026-08-29 打印体系盘点核销证据）。

---

## 2. 业务闭环验证

| 环节 | 判定 |
|---|---|
| 质检单创建→判定→复检 | ✅通（judge/reinspect 端点+移动端判定页） |
| 工序质检门禁（PASS/FAIL 阻断报工审批） | ✅通（P3 质检集成验收） |
| FQC FAIL→重置工序执行（REWORK） | ✅通（QualityActionServiceImpl reset 分支） |
| 质检记录打印（5 张 data 模板） | ✅通 |
| 打印留痕追溯（1237 biz 维度） | ✅通（代码已交，待验收） |
| 质量记录回传归档（扫描件挂单+保存期限提醒） | ❌缺（1186 已登记 P3：层4 后续做） |

## 3. 与行业基准对照

覆盖：来料/制程/成品检验✅ 判定/复检✅ 首件✅ 返工✅ 质量记录台账✅。
缺失：质量追溯到"检验批次+具体单据"的打印留痕已补（1237）；质量成本（报废/返工成本归集）无；SPC/趋势分析无（statistics 为计数非统计过程控制）。

## 4. 缺口与死代码清单

| 类型 | 项 | 证据 | 影响 | 建议 |
|---|---|---|---|---|
| 业务缺失 | 质量记录回传归档（1186） | 无扫描件挂单/到期提醒 | 中 | P3 后置（依赖 1237 已就绪） |
| 空转 | quality_template_print_log 0 行 | 未实际打印 | 低 | 1258 验证任务覆盖 |
| 弱 | 检验结果单行覆盖（无多轮检验历史明细） | 08-27 基线缺口 | 中 | 复核 reinspect 是否落历史 |

## 5. 优先级结论

| 优先级 | 事项 | 理由 |
|---|---|---|
| P2 | 1186 归档（扫描件回传） | 质量记录 2 年保存期限合规，纸质回传闭环 |
| P2 | 检验历史明细复核 | 复检可追溯性 |
| P3 | SPC/质量成本 | 增强 |
