-- ============================================================
-- 销售模块初始化数据
-- 执行顺序：第4个执行（依赖系统模块）
-- 包含：客户数据
-- ============================================================

-- ==================== 1. 客户数据 ====================
INSERT IGNORE INTO sales_customer (customer_id, customer_code, customer_name, customer_short_name, customer_type, customer_level, industry_category, customer_source, country, province, city, address, postal_code, contact_person, contact_phone, contact_email, fax, website, unified_social_credit_code, taxpayer_id, bank_name, bank_account, payment_method, payment_terms, credit_limit, used_credit_limit, customer_status, cooperation_start_date, cooperation_end_date, sales_manager_id, sales_manager_name, remark, customer_score, annual_purchase_amount, main_product_demand, special_requirements, is_vip, customer_tags, attachments, deleted, create_by, create_time, update_by, update_time) VALUES
(1, 'CUS-2024-001', '深圳华强电子科技有限公司', '华强电子', 1, 1, '工业控制', 2, '中国', '广东省', '深圳市', '深圳市南山区科技园南区R2-B栋8楼', '518057', '李明', '13912345678', 'liming@hqelec.com', '0755-12345678', 'www.hqelec.com', '91440300MA5XXXXXX1', '91440300MA5XXXXXX1', '中国工商银行深圳科技园支行', '400002XXXX9200XXXXXX', 3, '月结30天', 500000.00, 0.00, 2, '2024-01-15 00:00:00', NULL, 2, '销售员张三', '优质客户，长期合作', 5, 1200000.00, '薄膜开关、铭板', '需要提供ROHS报告', 1, '["VIP","工业控制","长期合作"]', NULL, 0, 'system', NOW(), 'system', NOW()),

(2, 'CUS-2024-002', '杭州迈瑞医疗设备有限公司', '迈瑞医疗', 1, 1, '医疗器械', 3, '中国', '浙江省', '杭州市', '杭州市滨江区滨康路567号', '310052', '王芳', '13923456789', 'wangfang@mindray.com', '0571-87654321', 'www.mindray.com', '91330100MA5XXXXXX2', '91330100MA5XXXXXX2', '中国建设银行杭州滨江支行', '400002XXXX9200XXXXXX', 3, '月结60天', 800000.00, 0.00, 2, '2024-03-01 00:00:00', NULL, 2, '销售员张三', '医疗行业重点客户', 5, 2000000.00, '背光薄膜开关、触摸薄膜开关', '医疗级认证要求，需提供生物相容性报告', 1, '["VIP","医疗器械","高要求"]', NULL, 0, 'system', NOW(), 'system', NOW()),

(3, 'CUS-2024-003', '上海智能家居科技有限公司', '智能家居', 1, 2, '智能家居', 1, '中国', '上海市', '上海市', '上海市浦东新区张江高科技园区碧波路888号', '201203', '张伟', '13934567890', 'zhangwei@smarthome.com', '021-55556666', 'www.smarthome.com', '91310000MA5XXXXXX3', '91310000MA5XXXXXX3', '中国银行上海张江支行', '400002XXXX9200XXXXXX', 3, '月结30天', 300000.00, 0.00, 2, '2024-05-20 00:00:00', NULL, 2, '销售员张三', '新兴行业客户', 3, 500000.00, '触摸薄膜开关、面板', '需要支持定制化设计', 0, '["智能家居","定制需求"]', NULL, 0, 'system', NOW(), 'system', NOW()),

(4, 'CUS-2024-004', '东莞精工机械设备有限公司', '精工机械', 1, 2, '工业设备', 2, '中国', '广东省', '东莞市', '东莞市长安镇乌沙社区振安中路88号', '523850', '陈强', '13945678901', 'chenqiang@jingong.com', '0769-85321234', 'www.jingong.com', '91441900MA5XXXXXX4', '91441900MA5XXXXXX4', '中国农业银行东莞长安支行', '400002XXXX9200XXXXXX', 2, '货到付款', 200000.00, 0.00, 2, '2024-06-10 00:00:00', NULL, 2, '销售员张三', '稳定合作客户', 3, 300000.00, '薄膜开关、铭板', '交期要求严格', 0, '["工业设备","交期敏感"]', NULL, 0, 'system', NOW(), 'system', NOW()),

(5, 'CUS-2024-005', 'Techtronix International Ltd.', 'Techtronix', 2, 1, '电子制造', 1, '美国', 'California', 'San Jose', '2001 Gateway Place, Suite 100, San Jose, CA 95110', '95110', 'John Smith', '+1-408-555-0100', 'john.smith@techtronix.com', '+1-408-555-0199', 'www.techtronix.com', NULL, NULL, 'Bank of America', 'XXXX-XXXX-XXXX-XXXX', 4, '月结60天', 1000000.00, 0.00, 2, '2024-02-01 00:00:00', NULL, 2, '销售员张三', '海外重点客户', 5, 3000000.00, '薄膜开关、铭板、面板', '需要UL认证，出口包装要求', 1, '["VIP","海外","电子制造"]', NULL, 0, 'system', NOW(), 'system', NOW());

-- ============================================================
-- 数据验证
-- ============================================================
-- SELECT 'sales_customer' AS table_name, COUNT(*) AS count FROM sales_customer;
