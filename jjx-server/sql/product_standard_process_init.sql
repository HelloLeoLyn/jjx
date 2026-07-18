-- ============================================================
-- 产品标准工序初始化数据
-- 适用行业：薄膜开关/铭板/面板制造
-- 工序类型：PRINTING(印刷) CUTTING(模切) LAMINATING(贴合) TESTING(测试) PACKAGING(包装)
-- 工序类别：PREPARATION(准备) MAIN(主要) FINISHING(后处理) QUALITY(质量)
-- ============================================================

-- 清空现有数据（谨慎使用）
-- TRUNCATE TABLE product_standard_process;

-- ==================== 1. 准备类工序 (PREPARATION) ====================

INSERT INTO product_standard_process (process_code, process_name, process_type, process_category, standard_labor_hours, standard_machine_hours, process_param_template, skill_requirement, equipment_type, quality_standard, description, is_enabled, display_order, create_by, create_time, update_by, update_time) VALUES
('PREP-001', '来料检验', 'TESTING', 'PREPARATION', 0.50, 0.00,
 '{"items":[{"name":"材料规格","type":"text","required":true},{"name":"材料厚度","type":"number","unit":"mm","required":true},{"name":"颜色确认","type":"text","required":true},{"name":"数量核对","type":"number","unit":"张","required":true}]}',
 '熟悉材料检验标准，能使用测量工具', '卡尺、厚度规、色卡',
 '来料合格率≥99%，规格偏差在允许范围内',
 '对入库原材料进行规格、颜色、厚度等检验，确保符合工艺要求', 1, 1, 'system', NOW(), 'system', NOW());

INSERT INTO product_standard_process (process_code, process_name, process_type, process_category, standard_labor_hours, standard_machine_hours, process_param_template, skill_requirement, equipment_type, quality_standard, description, is_enabled, display_order, create_by, create_time, update_by, update_time) VALUES
('PREP-002', '菲林准备', 'PRINTING', 'PREPARATION', 0.30, 0.00,
 '{"items":[{"name":"菲林编号","type":"text","required":true},{"name":"菲林版本","type":"text","required":true},{"name":"菲林类型","type":"select","options":["面板菲林","线路菲林","间隔菲林"],"required":true},{"name":"检查结果","type":"select","options":["合格","不合格"],"required":true}]}',
 '能识别菲林类型和版本，具备基本检查能力', '菲林检查台、放大镜',
 '菲林无划伤、无折痕、对位标记清晰',
 '根据工单准备对应菲林，检查菲林完整性和清晰度', 1, 2, 'system', NOW(), 'system', NOW());

INSERT INTO product_standard_process (process_code, process_name, process_type, process_category, standard_labor_hours, standard_machine_hours, process_param_template, skill_requirement, equipment_type, quality_standard, description, is_enabled, display_order, create_by, create_time, update_by, update_time) VALUES
('PREP-003', '油墨调配', 'PRINTING', 'PREPARATION', 0.50, 0.00,
 '{"items":[{"name":"油墨型号","type":"text","required":true},{"name":"颜色代码","type":"text","required":true},{"name":"调配比例","type":"text","required":true},{"name":"粘度值","type":"number","unit":"Pa·s","required":true},{"name":"调配量","type":"number","unit":"kg","required":true}]}',
 '熟悉油墨特性，具备配色能力，3年以上调墨经验', '调墨机、粘度计、色差仪',
 '色差ΔE≤1.5，粘度偏差±5%',
 '按工艺要求调配所需颜色油墨，记录调配比例和粘度', 1, 3, 'system', NOW(), 'system', NOW());

INSERT INTO product_standard_process (process_code, process_name, process_type, process_category, standard_labor_hours, standard_machine_hours, process_param_template, skill_requirement, equipment_type, quality_standard, description, is_enabled, display_order, create_by, create_time, update_by, update_time) VALUES
('PREP-004', '底材预处理', 'LAMINATING', 'PREPARATION', 0.30, 0.50,
 '{"items":[{"name":"底材类型","type":"text","required":true},{"name":"底材厚度","type":"number","unit":"mm","required":true},{"name":"清洁方式","type":"select","options":["静电除尘","酒精擦拭","等离子处理"],"required":true},{"name":"处理温度","type":"number","unit":"℃"},{"name":"处理时间","type":"number","unit":"min"}]}',
 '了解底材特性，能操作预处理设备', '静电除尘器、等离子处理机',
 '底材表面清洁度达标，无油污、灰尘',
 '对底材进行清洁和表面处理，提高印刷附着力', 1, 4, 'system', NOW(), 'system', NOW());

-- ==================== 2. 印刷类工序 (PRINTING) ====================

INSERT INTO product_standard_process (process_code, process_name, process_type, process_category, standard_labor_hours, standard_machine_hours, process_param_template, skill_requirement, equipment_type, quality_standard, description, is_enabled, display_order, create_by, create_time, update_by, update_time) VALUES
('PRINT-001', '面板丝印', 'PRINTING', 'MAIN', 1.00, 1.00,
 '{"items":[{"name":"丝印颜色","type":"text","required":true},{"name":"网版编号","type":"text","required":true},{"name":"刮刀压力","type":"number","unit":"kg","required":true},{"name":"印刷速度","type":"number","unit":"mm/s","required":true},{"name":"油墨粘度","type":"number","unit":"Pa·s","required":true},{"name":"烘干温度","type":"number","unit":"℃","required":true},{"name":"烘干时间","type":"number","unit":"min","required":true}]}',
 '熟练操作丝印机，3年以上丝印经验', '半自动丝印机、IR烘干线',
 '套印精度±0.10mm，墨层均匀无气泡、无针孔、无刮痕',
 '面板文字、图案、底色等丝网印刷，按工艺参数控制印刷质量', 1, 10, 'system', NOW(), 'system', NOW());

INSERT INTO product_standard_process (process_code, process_name, process_type, process_category, standard_labor_hours, standard_machine_hours, process_param_template, skill_requirement, equipment_type, quality_standard, description, is_enabled, display_order, create_by, create_time, update_by, update_time) VALUES
('PRINT-002', '线路印刷', 'PRINTING', 'MAIN', 1.50, 1.50,
 '{"items":[{"name":"导电银浆型号","type":"text","required":true},{"name":"网版编号","type":"text","required":true},{"name":"刮刀压力","type":"number","unit":"kg","required":true},{"name":"印刷速度","type":"number","unit":"mm/s","required":true},{"name":"银浆粘度","type":"number","unit":"Pa·s","required":true},{"name":"线宽要求","type":"number","unit":"mm","required":true},{"name":"烘干温度","type":"number","unit":"℃","required":true},{"name":"烘干时间","type":"number","unit":"min","required":true}]}',
 '熟练操作精密丝印机，5年以上线路印刷经验', '精密丝印机、隧道烘干炉',
 '线路电阻≤10Ω/□，线宽精度±0.05mm，无短路/断路',
 '印刷导电线路，控制银浆厚度和线宽，确保导电性能', 1, 11, 'system', NOW(), 'system', NOW());

INSERT INTO product_standard_process (process_code, process_name, process_type, process_category, standard_labor_hours, standard_machine_hours, process_param_template, skill_requirement, equipment_type, quality_standard, description, is_enabled, display_order, create_by, create_time, update_by, update_time) VALUES
('PRINT-003', '绝缘印刷', 'PRINTING', 'MAIN', 0.80, 0.80,
 '{"items":[{"name":"绝缘油墨型号","type":"text","required":true},{"name":"网版编号","type":"text","required":true},{"name":"印刷厚度","type":"number","unit":"mm","required":true},{"name":"烘干温度","type":"number","unit":"℃","required":true},{"name":"烘干时间","type":"number","unit":"min","required":true},{"name":"绝缘电阻要求","type":"number","unit":"MΩ","required":true}]}',
 '熟悉绝缘材料特性，2年以上丝印经验', '丝印机、烘干箱',
 '绝缘层无针孔、无漏印，绝缘电阻≥100MΩ',
 '在线路上印刷绝缘层，保护线路并防止短路', 1, 12, 'system', NOW(), 'system', NOW());

INSERT INTO product_standard_process (process_code, process_name, process_type, process_category, standard_labor_hours, standard_machine_hours, process_param_template, skill_requirement, equipment_type, quality_standard, description, is_enabled, display_order, create_by, create_time, update_by, update_time) VALUES
('PRINT-004', '碳浆印刷', 'PRINTING', 'MAIN', 0.80, 0.80,
 '{"items":[{"name":"碳浆型号","type":"text","required":true},{"name":"网版编号","type":"text","required":true},{"name":"印刷厚度","type":"number","unit":"mm","required":true},{"name":"烘干温度","type":"number","unit":"℃","required":true},{"name":"烘干时间","type":"number","unit":"min","required":true},{"name":"接触电阻","type":"number","unit":"Ω","required":true}]}',
 '熟悉碳浆印刷工艺，2年以上丝印经验', '丝印机、烘干箱',
 '碳浆厚度均匀，接触电阻≤100Ω，耐磨性达标',
 '在触点位置印刷碳浆，提高触点耐磨性和导电性', 1, 13, 'system', NOW(), 'system', NOW());

INSERT INTO product_standard_process (process_code, process_name, process_type, process_category, standard_labor_hours, standard_machine_hours, process_param_template, skill_requirement, equipment_type, quality_standard, description, is_enabled, display_order, create_by, create_time, update_by, update_time) VALUES
('PRINT-005', 'UV印刷', 'PRINTING', 'MAIN', 0.60, 0.60,
 '{"items":[{"name":"UV油墨型号","type":"text","required":true},{"name":"网版编号","type":"text","required":true},{"name":"UV能量","type":"number","unit":"mJ/cm²","required":true},{"name":"印刷速度","type":"number","unit":"mm/s","required":true},{"name":"固化时间","type":"number","unit":"s","required":true}]}',
 '熟练操作UV印刷机，了解UV固化工艺', 'UV印刷机、UV固化机',
 'UV固化完全，附着力达标，无黄变',
 '使用UV油墨进行特殊效果印刷，快速固化提高效率', 1, 14, 'system', NOW(), 'system', NOW());

-- ==================== 3. 模切类工序 (CUTTING) ====================

INSERT INTO product_standard_process (process_code, process_name, process_type, process_category, standard_labor_hours, standard_machine_hours, process_param_template, skill_requirement, equipment_type, quality_standard, description, is_enabled, display_order, create_by, create_time, update_by, update_time) VALUES
('CUT-001', '面板模切', 'CUTTING', 'MAIN', 0.80, 0.80,
 '{"items":[{"name":"模具编号","type":"text","required":true},{"name":"模切压力","type":"number","unit":"kg","required":true},{"name":"模切速度","type":"number","unit":"次/min","required":true},{"name":"材料厚度","type":"number","unit":"mm","required":true},{"name":"产品尺寸","type":"text","required":true}]}',
 '熟练操作模切机，3年以上模切经验，能独立换模', '平板模切机、自动模切机',
 '尺寸精度±0.15mm，边缘光滑无毛刺，无压痕',
 '对印刷完成的面板进行外形模切，冲出产品外形和孔位', 1, 20, 'system', NOW(), 'system', NOW());

INSERT INTO product_standard_process (process_code, process_name, process_type, process_category, standard_labor_hours, standard_machine_hours, process_param_template, skill_requirement, equipment_type, quality_standard, description, is_enabled, display_order, create_by, create_time, update_by, update_time) VALUES
('CUT-002', '间隔层模切', 'CUTTING', 'MAIN', 0.60, 0.60,
 '{"items":[{"name":"模具编号","type":"text","required":true},{"name":"材料类型","type":"select","options":["PET","PC","PVC"],"required":true},{"name":"材料厚度","type":"number","unit":"mm","required":true},{"name":"模切压力","type":"number","unit":"kg","required":true},{"name":"模切速度","type":"number","unit":"次/min","required":true}]}',
 '熟练操作模切机，2年以上模切经验', '模切机',
 '尺寸精度±0.10mm，窗口位置准确，无变形',
 '对间隔层材料进行模切，冲出按键窗口和定位孔', 1, 21, 'system', NOW(), 'system', NOW());

INSERT INTO product_standard_process (process_code, process_name, process_type, process_category, standard_labor_hours, standard_machine_hours, process_param_template, skill_requirement, equipment_type, quality_standard, description, is_enabled, display_order, create_by, create_time, update_by, update_time) VALUES
('CUT-003', '背胶模切', 'CUTTING', 'MAIN', 0.40, 0.40,
 '{"items":[{"name":"模具编号","type":"text","required":true},{"name":"胶型","type":"select","options":["3M胶","TESA胶","日东胶"],"required":true},{"name":"胶厚度","type":"number","unit":"mm","required":true},{"name":"模切压力","type":"number","unit":"kg","required":true}]}',
 '熟悉各种胶带特性，1年以上模切经验', '模切机',
 '尺寸精度±0.15mm，无溢胶、无气泡',
 '对背胶材料进行模切，冲出产品背面胶层', 1, 22, 'system', NOW(), 'system', NOW());

INSERT INTO product_standard_process (process_code, process_name, process_type, process_category, standard_labor_hours, standard_machine_hours, process_param_template, skill_requirement, equipment_type, quality_standard, description, is_enabled, display_order, create_by, create_time, update_by, update_time) VALUES
('CUT-004', '线路层模切', 'CUTTING', 'MAIN', 0.70, 0.70,
 '{"items":[{"name":"模具编号","type":"text","required":true},{"name":"材料类型","type":"select","options":["PET","PC"],"required":true},{"name":"材料厚度","type":"number","unit":"mm","required":true},{"name":"模切压力","type":"number","unit":"kg","required":true},{"name":"模切速度","type":"number","unit":"次/min","required":true}]}',
 '熟练操作模切机，3年以上模切经验', '精密模切机',
 '尺寸精度±0.10mm，线路区域无损伤',
 '对印刷完成的线路层进行模切，冲出外形和定位孔', 1, 23, 'system', NOW(), 'system', NOW());

INSERT INTO product_standard_process (process_code, process_name, process_type, process_category, standard_labor_hours, standard_machine_hours, process_param_template, skill_requirement, equipment_type, quality_standard, description, is_enabled, display_order, create_by, create_time, update_by, update_time) VALUES
('CUT-005', '排废', 'CUTTING', 'FINISHING', 0.30, 0.00,
 '{"items":[{"name":"排废方式","type":"select","options":["手工排废","自动排废"],"required":true},{"name":"排废率","type":"number","unit":"%","required":true}]}',
 '细心耐心，能识别废料和成品', '排废台、气枪',
 '排废干净，不损伤产品，排废率≥99%',
 '将模切后的废料边框去除，保留成品', 1, 24, 'system', NOW(), 'system', NOW());

-- ==================== 4. 贴合类工序 (LAMINATING) ====================

INSERT INTO product_standard_process (process_code, process_name, process_type, process_category, standard_labor_hours, standard_machine_hours, process_param_template, skill_requirement, equipment_type, quality_standard, description, is_enabled, display_order, create_by, create_time, update_by, update_time) VALUES
('LAM-001', '面板与间隔层贴合', 'LAMINATING', 'MAIN', 0.60, 0.50,
 '{"items":[{"name":"贴合方式","type":"select","options":["手工贴合","半自动贴合","自动贴合"],"required":true},{"name":"对位精度","type":"number","unit":"mm","required":true},{"name":"贴合压力","type":"number","unit":"kg","required":true},{"name":"贴合温度","type":"number","unit":"℃"}]}',
 '具备精密对位能力，2年以上贴合经验', '贴合机、对位治具',
 '对位精度±0.15mm，无气泡、无偏移、无褶皱',
 '将面板层与间隔层精确对位贴合', 1, 30, 'system', NOW(), 'system', NOW());

INSERT INTO product_standard_process (process_code, process_name, process_type, process_category, standard_labor_hours, standard_machine_hours, process_param_template, skill_requirement, equipment_type, quality_standard, description, is_enabled, display_order, create_by, create_time, update_by, update_time) VALUES
('LAM-002', '线路层贴合', 'LAMINATING', 'MAIN', 0.80, 0.50,
 '{"items":[{"name":"贴合方式","type":"select","options":["手工贴合","半自动贴合"],"required":true},{"name":"对位精度","type":"number","unit":"mm","required":true},{"name":"贴合压力","type":"number","unit":"kg","required":true},{"name":"贴合温度","type":"number","unit":"℃"}]}',
 '具备精密对位能力，3年以上贴合经验', '贴合机、CCD对位系统',
 '对位精度±0.10mm，线路对位准确，无气泡',
 '将上层线路与间隔层精确对位贴合', 1, 31, 'system', NOW(), 'system', NOW());

INSERT INTO product_standard_process (process_code, process_name, process_type, process_category, standard_labor_hours, standard_machine_hours, process_param_template, skill_requirement, equipment_type, quality_standard, description, is_enabled, display_order, create_by, create_time, update_by, update_time) VALUES
('LAM-003', '下层线路贴合', 'LAMINATING', 'MAIN', 0.80, 0.50,
 '{"items":[{"name":"贴合方式","type":"select","options":["手工贴合","半自动贴合"],"required":true},{"name":"对位精度","type":"number","unit":"mm","required":true},{"name":"贴合压力","type":"number","unit":"kg","required":true},{"name":"贴合温度","type":"number","unit":"℃"}]}',
 '具备精密对位能力，3年以上贴合经验', '贴合机、CCD对位系统',
 '对位精度±0.10mm，线路对位准确，无气泡',
 '将下层线路与间隔层另一侧精确对位贴合', 1, 32, 'system', NOW(), 'system', NOW());

INSERT INTO product_standard_process (process_code, process_name, process_type, process_category, standard_labor_hours, standard_machine_hours, process_param_template, skill_requirement, equipment_type, quality_standard, description, is_enabled, display_order, create_by, create_time, update_by, update_time) VALUES
('LAM-004', '总成贴合', 'LAMINATING', 'MAIN', 1.00, 0.50,
 '{"items":[{"name":"贴合方式","type":"select","options":["半自动贴合","自动贴合"],"required":true},{"name":"对位精度","type":"number","unit":"mm","required":true},{"name":"贴合压力","type":"number","unit":"kg","required":true},{"name":"贴合温度","type":"number","unit":"℃"},{"name":"保压时间","type":"number","unit":"s","required":true}]}',
 '具备精密对位能力，5年以上贴合经验', '全自动贴合机、CCD对位系统',
 '总成对位精度±0.15mm，各层无偏移，无气泡',
 '将面板、间隔层、上下线路层进行总成贴合', 1, 33, 'system', NOW(), 'system', NOW());

INSERT INTO product_standard_process (process_code, process_name, process_type, process_category, standard_labor_hours, standard_machine_hours, process_param_template, skill_requirement, equipment_type, quality_standard, description, is_enabled, display_order, create_by, create_time, update_by, update_time) VALUES
('LAM-005', '背胶贴合', 'LAMINATING', 'FINISHING', 0.30, 0.30,
 '{"items":[{"name":"背胶类型","type":"select","options":["3M467","3M468","TESA","日东"],"required":true},{"name":"贴合压力","type":"number","unit":"kg","required":true},{"name":"贴合温度","type":"number","unit":"℃"}]}',
 '熟悉背胶材料特性，1年以上贴合经验', '贴合机',
 '背胶贴合平整，无气泡、无褶皱、无溢胶',
 '在产品背面贴合双面胶层，便于客户安装', 1, 34, 'system', NOW(), 'system', NOW());

-- ==================== 5. 测试类工序 (TESTING) ====================

INSERT INTO product_standard_process (process_code, process_name, process_type, process_category, standard_labor_hours, standard_machine_hours, process_param_template, skill_requirement, equipment_type, quality_standard, description, is_enabled, display_order, create_by, create_time, update_by, update_time) VALUES
('TEST-001', '电性能测试', 'TESTING', 'QUALITY', 0.50, 0.50,
 '{"items":[{"name":"测试项目","type":"select","options":["导通测试","绝缘测试","耐压测试","接触电阻"],"required":true},{"name":"测试电压","type":"number","unit":"V","required":true},{"name":"判定标准","type":"text","required":true},{"name":"测试结果","type":"select","options":["合格","不合格"],"required":true}]}',
 '熟悉电子测试原理，能操作测试设备', '万用表、绝缘电阻测试仪、耐压测试仪',
 '导通率100%，绝缘电阻≥100MΩ，接触电阻≤100Ω',
 '对成品进行电性能测试，包括导通、绝缘、耐压等', 1, 40, 'system', NOW(), 'system', NOW());

INSERT INTO product_standard_process (process_code, process_name, process_type, process_category, standard_labor_hours, standard_machine_hours, process_param_template, skill_requirement, equipment_type, quality_standard, description, is_enabled, display_order, create_by, create_time, update_by, update_time) VALUES
('TEST-002', '外观检验', 'TESTING', 'QUALITY', 0.30, 0.00,
 '{"items":[{"name":"检验项目","type":"select","options":["外观检查","颜色检查","印刷质量","表面处理"],"required":true},{"name":"检验标准","type":"text","required":true},{"name":"检验结果","type":"select","options":["合格","不合格","返工"],"required":true}]}',
 '具备外观检验经验，熟悉AQL抽样标准', '放大镜、目视检查台、标准光源箱',
 '外观无划伤、无脏污、无气泡、颜色符合标准',
 '对产品外观进行全面检查，包括印刷质量、颜色、表面等', 1, 41, 'system', NOW(), 'system', NOW());

INSERT INTO product_standard_process (process_code, process_name, process_type, process_category, standard_labor_hours, standard_machine_hours, process_param_template, skill_requirement, equipment_type, quality_standard, description, is_enabled, display_order, create_by, create_time, update_by, update_time) VALUES
('TEST-003', '尺寸测量', 'TESTING', 'QUALITY', 0.30, 0.30,
 '{"items":[{"name":"测量项目","type":"text","required":true},{"name":"标准值","type":"number","unit":"mm","required":true},{"name":"公差范围","type":"text","required":true},{"name":"实测值","type":"number","unit":"mm","required":true},{"name":"测量工具","type":"select","options":["卡尺","投影仪","二次元","三次元"],"required":true}]}',
 '能使用各种测量工具，熟悉图纸要求', '卡尺、投影仪、二次元测量仪',
 '关键尺寸合格率≥98%，尺寸偏差在公差范围内',
 '对产品关键尺寸进行测量，确保符合图纸要求', 1, 42, 'system', NOW(), 'system', NOW());

INSERT INTO product_standard_process (process_code, process_name, process_type, process_category, standard_labor_hours, standard_machine_hours, process_param_template, skill_requirement, equipment_type, quality_standard, description, is_enabled, display_order, create_by, create_time, update_by, update_time) VALUES
('TEST-004', '按键手感测试', 'TESTING', 'QUALITY', 0.40, 0.40,
 '{"items":[{"name":"测试项目","type":"select","options":["操作力测试","回弹测试","行程测试","寿命测试"],"required":true},{"name":"标准力值","type":"number","unit":"N","required":true},{"name":"标准行程","type":"number","unit":"mm","required":true},{"name":"测试结果","type":"select","options":["合格","不合格"],"required":true}]}',
 '熟悉按键手感测试标准', '按键手感测试仪、推拉力计',
 '操作力偏差±20%，回弹良好，行程符合要求',
 '对薄膜开关按键进行操作力、回弹、行程等手感测试', 1, 43, 'system', NOW(), 'system', NOW());

INSERT INTO product_standard_process (process_code, process_name, process_type, process_category, standard_labor_hours, standard_machine_hours, process_param_template, skill_requirement, equipment_type, quality_standard, description, is_enabled, display_order, create_by, create_time, update_by, update_time) VALUES
('TEST-005', '环境可靠性测试', 'TESTING', 'QUALITY', 2.00, 2.00,
 '{"items":[{"name":"测试项目","type":"select","options":["高温测试","低温测试","湿热测试","温度循环","盐雾测试"],"required":true},{"name":"测试温度","type":"number","unit":"℃","required":true},{"name":"测试湿度","type":"number","unit":"%RH"},{"name":"测试时间","type":"number","unit":"h","required":true},{"name":"测试结果","type":"select","options":["合格","不合格"],"required":true}]}',
 '熟悉环境测试标准和设备操作', '恒温恒湿箱、冷热冲击箱、盐雾试验箱',
 '测试后产品功能正常，外观无异常，性能指标在允许范围内',
 '对产品进行高温、低温、湿热等环境可靠性测试', 1, 44, 'system', NOW(), 'system', NOW());

-- ==================== 6. 包装类工序 (PACKAGING) ====================

INSERT INTO product_standard_process (process_code, process_name, process_type, process_category, standard_labor_hours, standard_machine_hours, process_param_template, skill_requirement, equipment_type, quality_standard, description, is_enabled, display_order, create_by, create_time, update_by, update_time) VALUES
('PACK-001', '清洁处理', 'PACKAGING', 'FINISHING', 0.20, 0.00,
 '{"items":[{"name":"清洁方式","type":"select","options":["无尘布擦拭","静电除尘","酒精清洁"],"required":true},{"name":"清洁标准","type":"text","required":true}]}',
 '细心，具备清洁操作经验', '无尘布、静电除尘枪、酒精',
 '产品表面无灰尘、无指纹、无油污',
 '对成品进行清洁处理，去除表面灰尘和污渍', 1, 50, 'system', NOW(), 'system', NOW());

INSERT INTO product_standard_process (process_code, process_name, process_type, process_category, standard_labor_hours, standard_machine_hours, process_param_template, skill_requirement, equipment_type, quality_standard, description, is_enabled, display_order, create_by, create_time, update_by, update_time) VALUES
('PACK-002', '贴保护膜', 'PACKAGING', 'FINISHING', 0.20, 0.00,
 '{"items":[{"name":"保护膜型号","type":"text","required":true},{"name":"保护膜尺寸","type":"text","required":true},{"name":"贴合要求","type":"text","required":true}]}',
 '细心，操作手法轻柔', '保护膜、刮板',
 '保护膜贴合平整，无气泡、无偏移、无褶皱',
 '在产品表面贴保护膜，防止运输和安装过程中划伤', 1, 51, 'system', NOW(), 'system', NOW());

INSERT INTO product_standard_process (process_code, process_name, process_type, process_category, standard_labor_hours, standard_machine_hours, process_param_template, skill_requirement, equipment_type, quality_standard, description, is_enabled, display_order, create_by, create_time, update_by, update_time) VALUES
('PACK-003', '包装', 'PACKAGING', 'FINISHING', 0.20, 0.00,
 '{"items":[{"name":"包装方式","type":"select","options":["气泡袋","珍珠棉","吸塑盒","纸箱"],"required":true},{"name":"包装数量","type":"number","unit":"pcs","required":true},{"name":"包装规格","type":"text","required":true}]}',
 '熟悉包装规范', '封箱机、打包机',
 '包装牢固，产品在包装内无晃动，标识清晰',
 '按客户要求对产品进行包装，贴标签和标识', 1, 52, 'system', NOW(), 'system', NOW());

INSERT INTO product_standard_process (process_code, process_name, process_type, process_category, standard_labor_hours, standard_machine_hours, process_param_template, skill_requirement, equipment_type, quality_standard, description, is_enabled, display_order, create_by, create_time, update_by, update_time) VALUES
('PACK-004', '最终检验（OQC）', 'PACKAGING', 'QUALITY', 0.30, 0.00,
 '{"items":[{"name":"检验项目","type":"select","options":["外观检查","尺寸检查","功能测试","包装检查","标签检查"],"required":true},{"name":"抽样方案","type":"text","required":true},{"name":"检验结果","type":"select","options":["合格","不合格"],"required":true}]}',
 '熟悉出货检验标准，具备QC经验', '检验台、测量工具',
 '出货合格率≥99.5%，AQL=0.65',
 '出货前对产品进行最终检验，确保产品质量符合出货标准', 1, 53, 'system', NOW(), 'system', NOW());

-- ============================================================
-- 数据验证
-- ============================================================
-- 查询各类型工序数量
-- SELECT process_type, process_category, COUNT(*) AS count
-- FROM product_standard_process
-- GROUP BY process_type, process_category
-- ORDER BY process_type, process_category;

-- 查询所有启用的工序（按显示顺序排序）
-- SELECT process_code, process_name, process_type, process_category, display_order
-- FROM product_standard_process
-- WHERE is_enabled = 1
-- ORDER BY display_order;
