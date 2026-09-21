-- ============================================================================
-- 161_product_templates_use_bizno.sql
-- 任务码：dev-20260921-013（产品模块批）
--
-- 目的：产品域事件模板统一业务单号/编码 {bizNo}
--   ① 产品实例 4 条：{bizId} → {bizNo}（实例编码 instance_code）
--   ② 其余 10 条标题原本没有标识，补上【{bizNo}】：
--      产品 submitted/approved → 产品编码；产品分类 4 条 → 分类编码；
--      实例 batch_created（用「N 个实例」）、实例 status_updated → 实例编码；
--      标准工序 deleted/status_updated → 工序编码
--
-- 前置（同任务代码侧，需重启后端生效）：ProductInstanceServiceImpl 6 个动作、ProductServiceImpl 2 个、
--   ProductCategoryServiceImpl 4 个、ProductStandardProcessServiceImpl 2 个改手写 payload
--   （publishInstanceEvent / publishProductEvent / publishCategoryEvent / publishProcessEvent）。
--
-- 幂等：显式 SET 以原标题守卫 + REPLACE 以 LIKE 守卫。
-- ============================================================================

UPDATE sys_event_config
   SET title = REPLACE(title, '{bizId}', '{bizNo}'),
       content = REPLACE(content, '{bizId}', '{bizNo}')
 WHERE event_code IN ('product.instance.created', 'product.instance.production_started',
                      'product.instance.production_completed', 'product.instance.delivered')
   AND (title LIKE '%{bizId}%' OR content LIKE '%{bizId}%');

UPDATE sys_event_config SET title='产品【{bizNo}】已提交审核' WHERE event_code='product.submitted' AND title='产品已提交审核';
UPDATE sys_event_config SET title='产品【{bizNo}】审核通过'   WHERE event_code='product.approved'  AND title='产品审核通过';

UPDATE sys_event_config SET title='产品分类【{bizNo}】已新增'     WHERE event_code='product.category.created'          AND title='产品分类已新增';
UPDATE sys_event_config SET title='产品分类【{bizNo}】已修改'     WHERE event_code='product.category.updated'          AND title='产品分类已修改';
UPDATE sys_event_config SET title='产品分类【{bizNo}】已删除'     WHERE event_code='product.category.deleted'          AND title='产品分类已删除';
UPDATE sys_event_config SET title='产品分类【{bizNo}】已级联删除' WHERE event_code='product.category.deleted_children' AND title='产品分类已级联删除';

UPDATE sys_event_config SET title='产品实例批量创建完成：{bizNo}'   WHERE event_code='product.instance.batch_created'    AND title='产品实例批量创建完成';
UPDATE sys_event_config SET title='产品实例【{bizNo}】状态已变更'   WHERE event_code='product.instance.status_updated'  AND title='产品实例状态已变更';

UPDATE sys_event_config SET title='标准工序【{bizNo}】已删除'       WHERE event_code='product.standard_process.deleted'         AND title='标准工序已删除';
UPDATE sys_event_config SET title='标准工序【{bizNo}】启停状态已变更' WHERE event_code='product.standard_process.status_updated' AND title='标准工序启停状态已变更';
