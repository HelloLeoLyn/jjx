-- 06_quotation_item_code_structure.sql
-- DEV-1108：报价单明细增加产品编码结构参数（面板结构/线路结构/流水号）
-- 说明：报价管理-样品报价（quotationType=2）用编码生成器生成产品编码
--       （格式：客户简称+流水号+面板结构2位+线路结构2位），此前结构参数未落库，
--       导致修改报价单时无法回显面板结构/线路结构。
--       circuit_type 列此前已存在，本次补齐其余 4 列。
ALTER TABLE sales_quotation_item
    ADD COLUMN serial_no varchar(10) NULL COMMENT '编码流水号（3位）' AFTER circuit_type,
    ADD COLUMN panel_type varchar(50) NULL COMMENT '面板结构类型（M有面板有线路/S仅有线路/P仅有面板）' AFTER serial_no,
    ADD COLUMN panel_feature varchar(50) NULL COMMENT '面板特征（E凹凸/W窗口/H窗口凹凸/O无）' AFTER panel_type,
    ADD COLUMN circuit_feature varchar(50) NULL COMMENT '线路特征（O无/L发光二极体/C连接器/H连接器发光）' AFTER panel_feature;
