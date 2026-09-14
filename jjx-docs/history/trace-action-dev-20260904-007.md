# 流水操作动作化：@Log 加 action 中文动作文案（阶段1 框架，dev-20260904-007）

状态：⏳待实施（阶段1 框架；阶段2 铺码 / 阶段3 收紧见文末计划）

## 背景与已拍板口径（2026-09-04 用户裁定）

- 问题：流水"操作"列标题 = module(去"管理"后缀) + businessType 标签（如「库存预警 - 修改」），
  同一模块多种真实动作（执行检查/处理预警/标记已读…）显示全一样，不精准。全站通用问题，不只库存预警。
- 口径（用户逐条拍板）：
  1. action 存**中文动作文案**（如"执行预警检查"），不是英文码——本仓先例：bizStatus 存中文
     label 快照、module 中文自由文本，前端直接显示零映射；事件编码是英文因为它是机器触发钥匙，
     action 无此职能（无脏数据问题，旧行不回填）。
  2. 列名/注解属性就叫 `action`，不用 action_code。
  3. 全站 316 处 @Log（45 个文件）都要有 action，根治后不允许再出现"看不出是什么操作"的行。
  4. businessType 枚举语义与列不动（筛选/类型列/审批识别 businessType===6 依赖它），只不再承担标题语义。

## 现状锚点（file:line，改动前先核对）

- 标题生成：jjx-web/src/components/TraceTimeline/index.vue buildActionTitle(:341-346)，
  行内 :46-53；点击条件 hasDetail(:256-258)= changes||isReview||attachments，与标题无关；
  审核意见/驳回判断用 actionCode 字段（:329/:335/:383，REVIEW/SUBMIT/APPROVE/REJECT），**不许动**。
- 数据源：TraceTimeline GET /api/trace/events → TraceServiceImpl.getEvents/getEventsByBiz
  (:36-63) → fromLog(:130-145) 逐字段映射 SysOperLog → UnifiedTraceEventVO。
- 日志列表页：OperLogController /logs/oper（controller/log/OperLogController.java:31-49）直接返回
  PageResult<SysOperLog> → 实体加字段即自动透传。
- @Log 注解：system/annotation/Log.java（module/businessType/bizId/bizType/traceId/bizStatus/detail）。
- aspect 落库：system/aspect/OperLogAspect.java:237-238 module/businessType 设置处。
- 实体：system/domain/entity/SysOperLog.java 纯 MP @TableName，无手写 XML，加字段自动映射。
- 全站 @Log 316 处 / 45 文件（grep "@Log(" jjx-server/src/main/java 实测，2026-09-04）。

## 阶段1 改动清单（本次执行范围）

### 数据库
1. 新建迁移 `jjx-docs/sql/migrations/57_sys_oper_log_action.sql`（幂等，information_schema 守卫，
   参照现有 25/26 号迁移的 ALTER 模式）：
   `ALTER TABLE sys_oper_log ADD COLUMN action varchar(100) NULL COMMENT '操作动作中文文案（如 执行预警检查）'`
   Codex 只写文件**不要执行**（沙箱无 DB）；由我在外面应用 + 双跑幂等验证。

### 后端 jjx-server
2. `system/annotation/Log.java`：新增属性 `String action() default "";`
   javadoc 写明：操作动作中文文案（人读快照，同 bizStatus 思路）；字面量非 SpEL；与事件编码体系无关。
3. `system/aspect/OperLogAspect.java`：在 :237-238 同块加
   `operLog.setAction(logAnnotation.action());`（字面量，不做任何求值/判断/解析）。
4. `system/domain/entity/SysOperLog.java`：加 `private String action;`（放 bizStatus/detail 附近）。
5. `trace/domain/vo/UnifiedTraceEventVO.java`：加 `private String action;`；
   `trace/service/impl/TraceServiceImpl.java` fromLog(:130-145) 加 `event.setAction(log.getAction());`。
6. 操作日志页后端无需改（OperLogController 直出实体）。

### 前端 jjx-web
7. `components/TraceTimeline/index.vue`：
   - TraceEvent 接口加 `action?: string`；
   - buildActionTitle(:341) 改为：action 非空 → 直接返回 action；否则回退现有 module+类型逻辑。
   - **其余一律不动**：hasDetail/selectEvent/loadRowContent/isRejected/actionCode 判断、附件渲染、
     变更徽标、分页、bizType+bizId 双模式。
8. `views/log/operation`（表格页）：加一列"动作"显示 row.action，无值 '-'（列配置在
   views/log/operation/index.ts 附近，Codex 先找列定义文件再改，含模板若分离）。
9. `types/system/operation-log.ts`：OperationLog 接口加 `action?: string`。

## 明确不做

- 不在后端对 action 做任何语义/分支判断（纯存储透传，映射展示全在前端）。
- 不动审核 actionCode 体系（review_flow 的 SUBMIT/APPROVE/REJECT、TraceTimeline 驳回判断）。
- 不动 businessType 枚举/列/前端 BusinessTypeEnum 的筛选与类型列语义。
- action 不进 sys_event_config/字典，不建后端枚举。
- 旧行不回填 action（无脏数据问题，用户自理）；阶段1 不强制校验（老注解没码仍能启动，标题回退）。
- 本阶段不铺 316 处注解码（阶段2）。

## 验证

Codex 侧（自己跑并报告）：
1. jjx-server `mvn -o clean test-compile`；OperLogAspect 相关既有测试类若受影响需跑（正常应无，
   注解 default "" 兼容）。
2. jjx-web `npx vue-tsc --noEmit`：只报本次相关错误；仓库其它既有报错列出不修。
3. 不 git commit；工作区其它脏文件（并行会话产物）禁止触碰。

我侧：应用 57 号迁移 + 幂等双跑验证 + 实体↔表结构核对（SHOW COLUMNS diff）；
提交（dev-20260904-007）。

人工验收（交给用户，后端需重打包重启；前端 vite 热更）：
- 任一 @Log 操作后查 sys_oper_log.action 落中文；
- TraceTimeline 该行标题显示动作文案；无 action 老行标题回退"模块 - 类型"不炸；
- 点开带附件/变更/审核意见的行：附件、变更、驳回原因显示与改造前一致；
- 操作日志页出现"动作"列。

## 阶段2 / 阶段3（后续，不在本次 Codex 范围）

- 阶段2 铺码（形态 2026-09-04 用户拍板 = 常量类）：
  新建 `com.jjx.common.constant.LogActions`，每个动作一条
  `public static final String <英文标识> = "<中文文案>";`（同文案可共用常量）。
  铺码 = 我先出全量清单（45 文件 316 处 @Log → 常量名+中文文案草案）交用户过目，
  Codex 照单建常量类并把注解改为 `action = LogActions.XXX`，每批独立提交可回退。
  以后改文案只动这一个类；历史行保留写入时快照不受影响。
- 阶段3 收紧：扩展启动期 fail-fast 校验（LogBizStatusValidator 同款 SmartInitializingSingleton）：
  bizType 非空的 @Log 未写 action → 拒绝启动，防回潮。铺码全部完成后才收紧。
