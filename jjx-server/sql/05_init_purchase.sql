-- ============================================================
-- 采购模块初始化数据
-- 执行顺序：第5个执行（依赖系统模块）
-- 包含：供应商数据
-- ============================================================

-- ==================== 1. 供应商数据 ====================
INSERT IGNORE INTO purchase_supplier (supplier_id, supplier_code, supplier_name, supplier_type, contact_person, phone, email, address, payment_terms, bank_account, tax_number, evaluation_score, quality_score, delivery_score, price_score, last_evaluation_date, status, del_flag, create_by, create_time, update_by, update_time, remark) VALUES
(1, 'SUP-2024-001', '东莞华美PET材料有限公司', 'material', '刘经理', '0769-81112222', 'liu@huamei.com', '东莞市寮步镇华南工业区金富路88号', '月结30天', '400002XXXX9200XXXX11', '91441900MA5XXXXXXA1', 92.00, 90.00, 95.00, 88.00, '2024-06-01', 0, '0', 'system', NOW(), 'system', NOW(), 'PET基材主要供应商，品质稳定'),
(2, 'SUP-2024-002', '深圳银科电子材料有限公司', 'material', '陈经理', '0755-26553333', 'chen@yinke.com', '深圳市宝安区西乡街道固戍社区固戍一路88号', '月结30天', '400002XXXX9200XXXX22', '91440300MA5XXXXXXA2', 95.00, 93.00, 96.00, 92.00, '2024-06-01', 0, '0', 'system', NOW(), 'system', NOW(), '导电银浆主要供应商，技术领先'),
(3, 'SUP-2024-003', '上海3M胶带有限公司', 'material', '王经理', '021-68886666', 'wang@3m.com.cn', '上海市闵行区田林路888号', '月结45天', '400002XXXX9200XXXX33', '91310000MA5XXXXXXA3', 96.00, 95.00, 95.00, 90.00, '2024-06-01', 0, '0', 'system', NOW(), 'system', NOW(), '3M胶带官方授权经销商'),
(4, 'SUP-2024-004', '深圳华强电子元器件有限公司', 'material', '赵经理', '0755-83665555', 'zhao@hqelec.com', '深圳市福田区华强北路华强电子世界3楼', '月结15天', '400002XXXX9200XXXX44', '91440300MA5XXXXXXA4', 88.00, 85.00, 90.00, 85.00, '2024-06-01', 0, '0', 'system', NOW(), 'system', NOW(), '电子元器件供应商，品类齐全'),
(5, 'SUP-2024-005', '广州彩印包装材料有限公司', 'material', '黄经理', '020-82228888', 'huang@caiyin.com', '广州市番禺区石碁镇市莲路88号', '月结30天', '400002XXXX9200XXXX55', '91440100MA5XXXXXXA5', 85.00, 82.00, 88.00, 80.00, '2024-06-01', 0, '0', 'system', NOW(), 'system', NOW(), '包装材料供应商'),
(6, 'SUP-2024-006', '东莞精工模具制造有限公司', 'equipment', '周经理', '0769-85339999', 'zhou@jingongmold.com', '东莞市长安镇乌沙社区振安路168号', '月结30天', '400002XXXX9200XXXX66', '91441900MA5XXXXXXA6', 90.00, 88.00, 92.00, 85.00, '2024-06-01', 0, '0', 'system', NOW(), 'system', NOW(), '模切模具供应商'),
(7, 'SUP-2024-007', '日本油墨化工（广州）有限公司', 'material', '渡边一郎', '020-83337777', 'watanabe@japank.com', '广州市黄埔区经济技术开发区东区', '月结60天', '400002XXXX9200XXXX77', '91440100MA5XXXXXXA7', 93.00, 94.00, 90.00, 88.00, '2024-06-01', 0, '0', 'system', NOW(), 'system', NOW(), '进口油墨供应商，品质优良');

-- ============================================================
-- 数据验证
-- ============================================================
-- SELECT 'purchase_supplier' AS table_name, COUNT(*) AS count FROM purchase_supplier;
