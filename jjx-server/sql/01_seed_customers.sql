-- =====================================================
-- 演示客户数据
-- =====================================================

INSERT INTO sales_customer (customer_code, customer_name, customer_short_name, customer_type, customer_level, customer_source, country, province, city, address, contact_person, contact_phone, contact_email, customer_status, payment_method, credit_limit, remark, sales_manager_id, sales_manager_name, create_time) VALUES
('CUST-202607-JST', '捷顺通电子科技有限公司', 'JST', 1, 2, 1, '中国', '广东省', '深圳市', '深圳市宝安区西乡街道航城工业区A栋', '王经理', '13800138001', 'wang@jst-tech.com', 1, 1, 500000.00, '长期合作客户，主要采购薄膜开关', 1, '张伟', NOW()),
('CUST-202607-JTT', '金泰通电子有限公司', 'JTT', 1, 2, 1, '中国', '广东省', '东莞市', '东莞市长安镇乌沙社区兴发路168号', '李小姐', '13900139002', 'li@jtt-electronic.com', 1, 1, 300000.00, '重点客户，每月稳定订单', 1, '张伟', NOW()),
('CUST-202607-LEE', '李记精密电子科技', 'Lee', 1, 3, 2, '中国', '江苏省', '苏州市', '苏州工业园区星湖街328号创意产业园', '陈工', '13700137003', 'chen@lee-precision.com', 1, 2, 200000.00, '技术型客户，对精度要求高', 1, '李强', NOW()),
('CUST-202607-DLT', '德力通电子实业有限公司', 'DLT', 1, 2, 1, '中国', '浙江省', '杭州市', '杭州市余杭区良渚街道勾运路58号', '赵总', '13600136004', 'zhao@dlt-industry.com', 1, 1, 400000.00, '新开发客户，订单增长较快', 2, '王芳', NOW()),
('CUST-202607-HY', '华谊智控科技有限公司', 'HY', 1, 1, 1, '中国', '上海市', '上海市', '上海市松江区新桥镇新格路258号', '周经理', '13500135005', 'zhou@hy-zk.com', 1, 1, 600000.00, '大客户，需定期维护', 1, '张伟', NOW()),
('CUST-202607-LIT', '立通达光电有限公司', 'LiT', 1, 2, 2, '中国', '广东省', '深圳市', '深圳市龙华区观澜街道环观南路71号', '吴工', '13400134006', 'wu@lit-opto.com', 1, 2, 250000.00, '光电领域客户，对透光率有要求', 2, '王芳', NOW());
