-- ============================================================================
-- 145_sample_ready_title_use_bizno.sql
-- 任务码：dev-20260921-002
--
-- 目的：样品制作完成通知的标题不再显示内部编号，改显示业务信息：
--         改前：样品【{bizId}】已制作完成        （bizId = 样品单主键，如「样品【2】已制作完成」，看不出是哪个单）
--         改后：样品单【{orderNo}】{productName}/{productCode} 已制作完成
--
-- 背景（实测）：sys_event_config 里 sample.ready(id=9) 的标题用的是 {bizId}，
--   而 {bizId} 来自 @Event(bizId="#orderId")，就是自增主键 → 通知里只有编号。
--   同目录 138 迁移的注释已记录该限制：「变量只能用事件 payload 里真实存在的键，
--   否则模板里的 {x} 会原样显示」；139 迁移是给采购/入库三条事件补业务单号的样板。
--
-- 代码侧同任务改动（SampleOrderServiceImpl.markSampleReady）：
--   ① @Event 补 params：orderNo=#result.orderNo、productName=#result.sampleProductName、
--      productCode=#result.sampleProductCode；
--   ② 返回值由 orderMapper.selectById(orderId) 改为 loadSampleOrder(orderId) ——
--      sampleProductName/sampleProductCode 是 @TableField(exist=false) 的回填字段
--      （来自扩展表 sales_sample_order），selectById 取不到值，否则模板占位符会原样显示。
--
-- ⚠ 前置：后端要重新打包 + 重启才生效。重启前若又有人标记样品完成，
--         通知标题会原样显示 {orderNo} / {productName} / {productCode}。
--
-- 幂等：UPDATE 直接写目标值，可重复执行。
-- ============================================================================

UPDATE sys_event_config
   SET title = '样品单【{orderNo}】{productName}/{productCode} 已制作完成'
 WHERE event_code = 'sample.ready';

-- ============================================================================
-- 执行后自检（应看到新标题）：
--   SELECT id, event_code, title, content FROM sys_event_config WHERE event_code = 'sample.ready';
-- ============================================================================
