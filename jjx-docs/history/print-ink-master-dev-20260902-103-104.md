# 1304+1305 实施：打样印刷工序色号字典下拉 + 油墨物料体系化（dev-20260902-103/104）

依据：jjx-docs/analysis/sample-print-color-ink-master-data-20260902.md（2026-09-02 Leo 定方向）
本 spec 按该方向把 1304（PrintProcessPanel 改造）与 1305（油墨物料建档能力）可执行部分落地；
1305 的"甄别+挂分类"需人工确认，不在本次 Codex 范围（见"明确不做"）。

## 现状（已核实）

- 基础数据：物料分类 INK/油墨印刷 category_id=1 ✓；色号字典 engineering_color dict_id=120 ✓（items 0 条）
- sales_sample_process 全表 0 行（无历史数据）；R 物料 1535 条混有膜/胶带/油胶PET 等（噪声大，不可自动挂分类）
- PrintProcessPanel.vue（jjx-web/src/views/engineering/sample-workbench/components/PrintProcessPanel.vue）：
  色号列 :52-63 el-autocomplete(suggestFrom colorNos)；油墨列 :64-75 el-autocomplete(inkNos)；
  网框列 :76-87 suggestScreen（网版主数据，保持不动）；印刷名称列 :39-51 autocomplete printNames（保持）
- 历史联想 getProcessHistory（api/sales/sampleOrder.ts）返回 {printNames,colorNos,inkNos}
- 行结构：{printName,colorNo,inkNo,screenNo,materials,status,...}；行字段序列化/回显位于父组件
  workbench.vue（实施时 grep custom_process_params / savePlan 定位"保存 payload 构建"与"加载回显解析"两处）

## 1304 改动点（本次主任务）

### A. 前端 PrintProcessPanel.vue
1. 色号列：el-autocomplete → 字典下拉。数据源 sys_dict_item dict_code=engineering_color
   （调字典接口加载 items，显示 item_value/label，值存 item_key）；保留 clearable；
   选择时同时写 colorNoLabel（冗余显示名）；不允许下拉内新建（字典在系统字典管理页维护）。
2. 油墨列：el-autocomplete → 油墨物料选择器（过滤 category_id=1 / category_code=INK）
   + 手输兜底：
   - 选择物料：行上写 inkMaterialId（物料ID）+ inkNo（冗余物料名/编码），下拉显示
     "物料名+规格+编码"，远程搜索按名称（若 materialApi.search 不支持 category 过滤参数，
     则在后端 material 列表/search 接口补 category/categoryId 可选参数——只允许参数级小改，
     不允许新建接口）
   - 手输兜底：允许输入自由文本（未建档新油墨场景），此时 inkMaterialId 空、仅 inkNo 有值；
     UI 用"选择/手输"两态（如 select allow-create 的创建项即兜底，或附"手输"开关，实现自定，
     交互须自解释）
3. 行数据结构（custom_process_params）变更为
   {printName,colorNo,colorNoLabel,inkMaterialId,inkNo,screenNo}
   ——同步四处：
   a) 父组件保存 payload 构建 + 加载回显解析（workbench.vue 序列化/解析点，实施时定位）；
   b) PrintProcessPanel dirty 监听字段列表（:330-345 附近，把 colorNoLabel/inkMaterialId 纳入，
      漏加则"改了不提示保存"）；
   c) 空行判定 isEmptyRow（:293-302，新增字段为空才算空行）；
   d) ExecutionTimeline.vue :51 noteParts 显示（油墨优先显示 inkNo，色号可带 colorNoLabel）
4. 兼容：sales_sample_process 0 行 → 无老数据迁移负担；代码层面加载解析用
   `row.colorNo ?? row.xxx` 兜底即可，不做历史兼容层。

### B. 后端（仅在必要时的最小改动）
- material list/search 若不能按 category 过滤 → 控制器参数加 categoryId（可选），mapper/XML
  加条件；不改表结构、不新建 VO。
- 色号字典 items 由用户在系统字典管理页维护，本期 Codex 不需要写数据。

## 1305 可执行部分（建档能力，非甄别）

1. 物料编码规则 INK-xxx 落地方式（二选一，Codex 按仓库现状判断成本后选择并说明）：
   a) 后端物料编码生成器支持 INK 前缀（若物料编码生成是集中服务/规则，扩展规则映射）；
   b) 或提供一次性改码/建档 SQL 脚本模板（INSERT 油墨物料样例 + UPDATE 编码规则说明），
      输出为 jjx-docs/sql/migrations/ 下文件（不执行，标注待人工确认后执行）。
2. 初筛候选清单（供 Leo/工程部勾选，禁止自动挂分类）：Codex 查询 R 物料中含
   油墨语义名称的候选（银浆/碳浆/色墨/油墨/导电墨 等），把候选名单+判定依据输出到
   jjx-docs/analysis/ 下（追加到本 spec 同目录新文件 ink-material-candidates-20260902.md），
   明确"不确定的不要列入，宁可少不可错"。

## 明确不做（防止越界）

- ❌ 1305 的"甄别结论 + 挂 category_id=1"——需 Leo/工程部确认（任务描述明示避免误挂）
- ❌ 色号字典 items 初始化写入（空字典起步，工程部边用边加）
- ❌ 油墨领料联动、BOM 联动（后置）
- ❌ 改动网框列/印刷名称列交互、改动字典/物料管理页
- ❌ 修改以下工作区已有未提交文件：jjx-web/src/components/OperationPreviewDialog/index.vue
  （openclaw Accept 修复）、jjx-server/.../config/WebConfig.java（openclaw YAML 转换器修复）、
  jjx-server/.../SysUserDTO.java、jjx-web/src/components.d.ts、jjx-docs/assets/ 下文件
- ❌ git commit；不清理/不格式化无关文件

## 验证（Codex 自查 + 交用户手测）

1. vue-tsc：cd jjx-web && npx vue-tsc --noEmit 2>&1 | grep -E "error TS" |
   grep -v "ProcessCard|sample-workbench" —— 但注意本次改的就是 sample-workbench 下文件，
   若该过滤词命中本文件报错，需单独确认修复（报告时区分用户 WIP vs 本任务报错）
2. 后端若动 material 查询：mvn -o clean test-compile（不跑全量测试）
3. check:status-enums 不受影响（无状态字面量改动）——若跑了 npm run validate 报告结果
4. 手测清单（写进交付说明）：打样工作台印刷 tab→色号下拉可选字典值并回填 label；
   油墨可选 INK 物料或手输兜底；保存后重进回显正确（含 colorNoLabel/inkMaterialId）；
   时间线备注正常；空行/新增行行为不变

## 关联任务
- sys_task 1304 dev-20260902-103 P1、1305 dev-20260902-104 P2（本次交付后翻待审核，由用户验收）
