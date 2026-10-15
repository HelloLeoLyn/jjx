# 3246 附件证据引用收口：代码完成，迁移待执行

任务码：dev-20261014-039。实施：Codex，2026-10-14 至 2026-10-15。

## 已确认范围

用户确认历史业务数据已经清理，不做历史附件回填。样品完成时固定本单已绑定的图纸引用；其他单据的图纸必须先绑定到样品单。后续新增图纸不改变已有轮次快照。

## 实现

1）订单驳回与通过统一校验引用归属、重复、数量及有效性；旧附件参数非空时拒绝。审核流水保存引用 ID，零行写入视为失败。
2）报价 review/status/confirm/reject/modify/send/submitReview 保存本次动作的引用 ID 快照；流水写入异常传播并回滚业务状态。
3）样品轮次只采本单有效图纸引用，不再按 traceId 抓取其他来源。明确图纸类型纳入；工作台上传的未细分图纸按 ENGINEERING_DRAWING 元数据识别。BOM 等明确非图纸类型不纳入。
4）样品工作台上传改用公共附件引用组件，历史轮次按快照展示下载链接；报价与订单追溯按动作快照展示附件。
5）三处旧字段正则保护退役。review_flow、sales_quotation_flow、sales_sample_round 的引用快照共同保护附件，防止历史证据被解除或删除。旧列保留。

改动：17 个业务源文件；另 6 个测试文件、迁移 284、本报告及索引。没有改动已有的 clean-test-data-log.txt。

## 验证

1）后端编译通过。六个测试类合计 39 项全部通过：报价七类动作、旧字段拒绝、跨单据/失效/重复引用拒绝、样品图纸范围、轮次隔离、删除保护、零行写入失败；报价和样品证据失败回滚使用独立 H2 数据库和 Spring 事务代理验证。

```bash
cd jjx-server
mvn -Dtest=QuotationAttachmentEvidenceTest,SampleRoundEvidenceTest,OrderRejectEvidenceTest,AttachmentEvidenceServiceTest,AttachmentAccessServiceTest,ReviewFlowServiceImplTest test
```

2）`npx vue-tsc --noEmit --composite false` 通过。标准 `vue-tsc --noEmit` 报 TS6307：`src/components/product/productFilePreview.ts` 的项目文件归集错误，涉及文件不在本任务修改范围；未修改 tsconfig 绕过。
3）完整 `npm run validate` 的 status-enums/model-baseline/result-case/docs/collation/migration-collation/notnull/stock/lot/iqc-lineage/quality-ledger/doc-no 均通过，最后标准 vue-tsc 因上述 TS6307 退出非零。库存五项全 0。Windows 下临时使用 Git Bash 和本机已有 Python，不改仓库脚本或基线。
4）未进行浏览器或运行服务验收，未启动、停止、重启或重载服务。

## 数据库与上线阻塞

开工读取目标库：三张历史表行数均为 0。账本最大号 281，目录已有 282、283，因此本任务取 284。

`284_flow_attachment_ref_snapshots.sql` 为报价流水和样品轮次各增加 JSON 引用列，可重复执行；无回填、无删表/删列。

本机 Windows 执行唯一入口 `scripts/db-migrate.sh ... --yes --task dev-20261014-039 --backup ...`，在 SQL 执行前返回 `缺少flock`。**迁移未执行、未记账，不能宣称运行态已修复。** 迁移预览还暴露 Windows Python 输出反斜杠路径与 Bash 文件名匹配不兼容。未绕过入口直连执行 DDL。

应在具备 bash、python3、mysql、flock 的目标库执行环境中，确认用户手工备份存在且排除 hr_employee，然后执行：

```bash
bash scripts/db-migrate.sh 284_flow_attachment_ref_snapshots.sql --yes \
  --task dev-20261014-039 --backup <用户手工全库备份.sql>
```

回查两列与账本成功后，按脚本规则同名归档。随后由用户更新运行服务并验收：订单驳回附件、报价每次动作附件、样品两轮不同图纸、历史证据不可解除。迁移 282/283 不属于本任务，未执行。

## 任务记录

已按规范登记接单时间与 Codex 操作员。提交 hash、推送结果与待审核状态写入 sys_task；代码交付不代表上述数据库及运行态遗留已完成。
