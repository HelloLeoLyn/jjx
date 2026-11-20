# dev-20261120-007 完成记录

任务：BOM全版本流水汇集及版本审核操作留痕修复
状态：代码完成，待独立验收（用户）
日期：2026-11-20（Codex 起草 WIP，dahuang 17:53 接手续做并提交）

## 1）交付内容

1) 后端新增 `com.jjx.trace.bom`：
   - `BomTraceVersion`：版本族投影（产出类型+产出ID，兼容旧 product_id）
   - `BomTraceMapper`：版本族查询、按产出对象的日志快照、删除版本锚点、整套请求根ID关联
   - `BomTraceReader`：按版本族汇集流水；历史整套请求按严格根ID关联并标注“历史请求记录”；缺创建日志的版本档案只读补全并标注来源；已删除版本按快照保留归属
   - `BomTraceRecorder`：复制/切换/整套审批等操作的原子留痕（与业务事务一致）
2) `TraceServiceImpl`：`getEventsByBiz("bom", …)` 走新的全版本读取器；其它 bizType 路径不变
3) `TraceController`：新增 `GET /trace/bom-context`（bomId → 版本族上下文，权限 engineering:bom:view）
4) `UnifiedTraceEventVO`：新增 bomCode / bomVersion / evidenceSource / relatedBomIds
5) 前端 `TraceTimeline/index.vue` + `TraceEvidenceEnum.ts`：展示版本、来源（操作日志/历史请求/版本档案补全）
6) 测试：新增 BomTraceReaderTest、BomTraceMysqlTest；补/改 BomGroupPersistenceTest、BomReviewPackageTest、BomReviewPackageTransactionTest、VersionMaintenanceServiceTest、ProductEngineeringTraceTest、traceTimelineAttachments.spec.ts

## 2）验证结果

1) 后端相关定向测试（JVM 768M）：`BomTraceReaderTest,BomTraceMysqlTest,VersionMaintenanceServiceTest,BomReviewPackageTest,BomReviewPackageTransactionTest,BomGroupPersistenceTest,ProductEngineeringTraceTest` → **TEST_EXIT=0**
2) `mvn -o compile` → **EXIT=0**
3) 前端定向 Vitest（cgroup 1G）：`traceTimelineAttachments.spec.ts` → **8/8 通过**
4) `npx vue-tsc --noEmit` → **EXIT=0**

## 3）接手时的修正

1) `ProductEngineeringTraceTest` 的 H2 表结构缺 `oper_group_id`（实体自 dev-20261022-002 起已映射）→ 补列
2) 该测试仍按旧口径期望“排除工程来源”，与现行 `dev-20261022-003` 的“trace 全显示”不一致 → 对齐为全显示并按时间+id 分页断言

## 4）边界与未验证

1) 历史日志不补写、不改；历史整套请求只按严格根ID关联，不推断全部节点成功
2) 未新增表/字段/迁移；未改 BOM 审批/复制/当前版本规则；未启停服务
3) **真实浏览器 / 运行态 HTTP 未验证**（服务生命周期未授权）
4) 真实 MySQL 只读核对按卡面由独立验收执行（本提交未执行库写入）
