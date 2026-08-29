# 产品主线追溯（工程/产品模块接入流水）设计方案 v2

**修订**：用户明确要求不改表结构（v1 的 sys_oper_log 加 product_id 列方案作废）。
v2 改为"运行时反查"，零表结构改动。

## 一、核心原则（不变，业内成熟套路）

1. 产品是唯一主线：产品页"追溯"入口，按 product_id 聚合下属实体日志，时间排序成一条线。
2. 实体不带 trace 概念：BOM/工艺路线/图纸/实例各自照常用 sys_oper_log 记操作，
   日志本身不新增任何列。
3. 版本升级 = 流水节点（copy 是"改版"日志，v2 修改是带 diff 的另一条日志）。
4. 修改类操作必须接 ChangeRecorder diff（与询价单/样品单 047 同套路）。

## 二、关键设计：不建列，查询时 JOIN 反查

sys_oper_log 保持原结构。产品追溯查询时：

1. 先在各实体表按 product_id 取出归属该产品的全部业务主键集合：
   - engineering_bom：`SELECT bom_id FROM engineering_bom WHERE product_id=?`
   - engineering_routing：`SELECT routing_id FROM engineering_routing WHERE product_id=?`
   - engineering_film：`SELECT film_id FROM engineering_film WHERE product_id=?`
   - product_instance：`SELECT instance_id FROM product_instance WHERE product_id=?`
   - product 自身：`product_id=?`
2. 再查 sys_oper_log：`WHERE biz_type IN ('bom','routing','film','product_instance','product')
   AND biz_id IN (上述集合)`，按 create_time 倒序分页。
3. 产品主数据自身的变更（bizType='product', bizId=productId）天然包含。

性能：实体表行数小（当前 BOM 2 行/路由 2 行），两个查询 + 内存合并即可，
不需要任何索引/新列。数据量大了再评估（预估数万级仍可接受，都是主键 IN 查询）。

## 三、后端改动（与 v1 相同的部分保留）

1. **埋点补齐**（核心工作量，与 v1 一致）：
   - BomController：add/edit 接 ChangeRecorder diff（edit 返回 VO 加 detailMessage，
     @Log 加 detail="#result.data.detailMessage"）；copy(改版) 补 @Log
     （"BOM 改版：v{bomVersion 旧}→v{新}"）；submit/approve/reject 保留既有 @Log
   - EngineeringRoutingController：全量补 @Log（bizType='routing'），修改类接 diff
   - EngineeringFilmController：全量补 @Log（bizType='film'），修改类接 diff
   - ProductInstanceController：全量补 @Log（bizType='product_instance'）
   - ProductController edit：接 ChangeRecorder diff（产品名称/分类/价格/状态 old→new）
2. **新接口** `GET /api/trace/product-events?productId=&pageNum=&pageSize=`：
   按第二节逻辑聚合查询，返回复用 trace 事件 VO（detail 原样带出），按时间倒序分页。
3. 各实体表已有 product_id 列（实测确认），无需任何 DDL。

## 四、前端改动

1. TraceTimeline 增加可选 prop `productId`：有 productId 时调
   `/api/trace/product-events`；无 traceId 且无 productId 时保持现状。
   组件其余逻辑（分组、变更内容渲染、附件展示）完全复用。
2. 产品列表页（views/product/list/index.vue）行操作加"追溯"按钮，
   弹 TraceTimeline 抽屉传 productId。

## 五、流水体现样例（同 v1）

```
10:02 修改BOM(v2)  ✎ 物料A 数量:2→3
09:58 BOM改版 v1→v2
09:40 审核通过 v1
09:20 提交审核 v1
09:00 创建BOM v1
```

## 六、范围边界（不变）

- 不动销售侧现有流水；不建 trace_id/聚合表；不改 sys_oper_log 结构。
- engineering_base 暂不纳入（无 product_id，0 数据）。
- 历史日志不回填（从接入后开始记录）。

## 七、实施顺序

1. 后端埋点：BomController(diff+copy) → 工艺路线 → 图纸 → 实例 → ProductController diff
2. 新接口 /api/trace/product-events（JOIN 反查聚合）
3. 前端：TraceTimeline productId 模式 + 产品列表"追溯"按钮
4. mvn -o clean test-compile + npm run validate
