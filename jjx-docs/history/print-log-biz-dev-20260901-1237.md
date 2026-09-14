# 打印日志加业务单据维度：biz_type+biz_id（dev-20260901-1237）

- 任务来源：sys_task dev-20260901-1237（P1）
- 依据：jjx-docs/analysis/print-system-analysis-20260831.md（P2 打印日志只记模板级，无法回答"某张送货单被打印过几次、谁打的"）

## 根因

quality_template_print_log 只有 template_id/record_no，无业务单据标识。打印留痕无法追溯到具体单据。

## 改动

### 后端（4 处）

1. **migration 35**（jjx-docs/sql/migrations/35_print_log_biz.sql，幂等 information_schema 判断缺列才 ALTER）：
   ```sql
   ALTER TABLE quality_template_print_log
     ADD COLUMN biz_type VARCHAR(50) NULL COMMENT '业务类型（如 sales_delivery）' AFTER record_no,
     ADD COLUMN biz_id BIGINT NULL COMMENT '业务单据ID' AFTER biz_type;
   ```
2. `QualityTemplatePrintLog` 实体加 `bizType` / `bizId` 字段。
3. `QualityTemplateRegistryServiceImpl.recordPrint(Long id, String bizType, Long bizId)`：签名加两参数，插入时写 biz_type/biz_id。
4. `QualityTemplateRegistryController.printLog`（POST /production/quality-template/{id}/print-log）加 `@RequestParam(required = false) String bizType` / `@RequestParam(required = false) Long bizId`，透传给 service。

### 前端（2 处）

1. `jjx-web/src/api/production/qualityTemplate.ts` 的 `createQualityTemplatePrintLog` 加可选参数 `(id: number, bizType?: string, bizId?: number)`，拼到 params。
2. 调用点传业务维度：
   - `jjx-web/src/views/sales/delivery/print.vue`：调 `createQualityTemplatePrintLog(26, 'sales_delivery', deliveryId)`（送货单打印留痕带单据）
   - `jjx-web/src/views/production/quality-print/print.vue`：通用空白模板打印，不传 biz 维度（保持原样）

### 不做

- 打印日志查询/管理界面（后续）
- 批量打印留痕、打印次数统计接口（后续）

## 风险

- 工作区有用户 WIP（测试文件 D/M、jjx-docs/analysis 下 20260831 两个文档），只改本 spec 列出的文件
- 不要 git commit
- 沙箱连不上 MySQL：migration 只写文件不执行（我在外面执行）
- 前端 vue-tsc 报错若来自用户 WIP 文件过滤掉，保证自己文件零错误

## 验证

- mvn -o clean test-compile
- vue-tsc --noEmit
- npm run check:status-enums
- migration 35 应用两遍（幂等，我在外面做）
