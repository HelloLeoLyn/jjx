-- dev-20260901-1236 删除销售死表：sales_contract / sales_performance
-- 背景：两表全库零代码（无 Entity/Mapper/Service/Controller/前端引用），报表页走实时统计
-- 幂等：DROP TABLE IF EXISTS 天然幂等；模板占位清理用 WHERE 限定

DROP TABLE IF EXISTS sales_contract;
DROP TABLE IF EXISTS sales_performance;

-- 清模板 JJX-QR-048（合同记录一览表）失效的 biz_type 占位：合同表已删，模板保留为通用空白表
UPDATE quality_template_registry SET biz_type = NULL
WHERE id = 48 AND biz_type = 'sales_contract';
