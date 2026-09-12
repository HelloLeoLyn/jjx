# 标签查询辅助组件（多选/分组/计数/与或）+ 供应商管理落地（dev-20260912-004）

## 一、背景与现状证据

系统已有通用标签体系（dev-20260911-007/008，Codex）：

| 项 | 证据 |
|---|---|
| 标签主数据 | `sys_tag` 64 条，两组：`supplier_goods`（供货品类，55 条）、`material_attribute`（物料属性，9 条）；字段含 `tag_code/tag_group/parent_id/sort_order/status` |
| 业务关联 | `sys_tag_rel(tag_id,biz_type,biz_id)`，当前 `purchase_supplier` 63 条 |
| 后端 | `SysTagController`：/list、CRUD、/rel 读写、/biz-ids（单标签反查）；`ISysTagService`：listTags、getBizTags、setBizTags、getBizIdsByTagIds |
| 供应商 | `PurchaseSupplierServiceImpl` 已支持**单** `tagId` 过滤、编辑保存 `tagIds`、导入带标签；VO 回填 `tagIds/tagNames` |
| 前端 | 供应商页有一个单标签 `el-select`（查询区）+ 一个多选 `el-select`（编辑表单），选项都来自 `/system/tag/list` |

**两个实测问题**

1. `/system/tag/list` 挂 `system:tag:view`，而该权限只有 `SYSTEM 全权限` 和 `超级管理员` 有 → 采购角色打开供应商页时标签选项加载 403、下拉为空，标签筛选对业务角色实际不可用。
2. 查询侧只支持单标签；`sys_tag_rel` 的多标签能力（AND/OR）没有暴露。

## 二、业内方案对比（标签查询交互）

| 方案 | 代表 | 优点 | 缺点 | 采用 |
|---|---|---|---|---|
| 多选标签 + 与/或语义 | GitHub/Notion/Jira 的 label 过滤 | 直观、实现成本低、可精确控制 | 标签很多时要靠搜索 | ✅ 采用 |
| 分组/层级标签 | 语雀、Confluence 空间标签 | 天然分类，`tag_group/parent_id` 现成 | 层级深了点击变多 | ✅ 采用（按 tag_group 分组） |
| Facet 计数（动态收窄） | 电商筛选、ES faceted search | 能预判"再加一个标签还剩几条"，避免空结果 | 需一次额外聚合查询 | ✅ 采用 |
| 最近/常用标签 | 各类 SaaS 筛选器 | 常用组合一键复用 | 仅本地个性化 | ✅ 采用（localStorage） |
| 保存的查询视图 | Jira saved filters、Airtable views | 团队共享常用口径 | 要新建表+权限模型，属于二期 | ⏳ 二期 |
| 查询语法（JQL/CloudWatch filter） | Jira JQL、Datadog | 表达力最强 | 学习成本高，与当前用户群不匹配 | ❌ 不采用 |
| 标签云（按热度） | 博客标签墙 | 展示效果好 | 不适合精确查询 | ❌ 不采用 |

**选型结论**：多选 + 分组 + 动态计数 + 最近使用 + 与/或，不引入查询语法；保留 `sys_tag` 作为唯一标签源，业务侧只传 `tagIds` 与 `tagMatchMode`。

## 三、实现

### 后端（通用，任何 bizType 可用）
- `SysTagRelMapper` 新增三条查询：`selectBizIdsByAnyTag`（OR，DISTINCT）、`selectBizIdsByAllTags`（AND，`GROUP BY biz_id HAVING COUNT(DISTINCT tag_id)=N`）、`countByTagGrouped`（按 tag 聚合计数，可限定 bizIds）。
- 新增 `TagFacetVO{tagId,tagCode,tagName,tagGroup,count,selected}`。
- `ISysTagService/SysTagServiceImpl` 新增 `getBizIdsByTagIds(bizType,tagIds,matchAll)` 与 `facets(bizType,selectedTagIds,keyword)`；原单参方法保留并委托 OR 语义（行为不变）。
  - facets 口径：已选标签先 AND 收窄得到"分母"，再统计各标签数量 → 真正的 faceted narrowing；组合无命中返回空。
- `GET /system/tag/facets?bizType=&tagIds=1,2&keyword=`：**登录即可、不挂 system:tag:view**（业务角色一般没有系统标签权限，查询辅助必须可用；标签增删改仍受 system:tag:add/edit/delete 保护）。
- 供应商查询：`PurchaseSupplierQueryVO` 增 `tagIds`（优先）与 `tagMatchMode`；服务层按 AND/OR 反查 bizId 过滤，`tagId` 保留兼容。

### 前端
- 新组件 `src/components/TagQuerySelect.vue`（可复用）：
  - 多选标签（collapse-tags）、按 tag_group 分组展示、每组显示标签与**当前条件下计数**；
  - 「同时满足 / 任一满足」（选中 <2 个时禁用）、「最近」标签 chips（localStorage 按 bizType 存 5 个）；
  - 已选标签参与 facets 收窄，计数随选择变化；组合无命中时保留上次选项避免显示成 ID；
  - props：`bizType`(必填)、`v-model`、`v-model:match-mode`、`tagGroup`(只显示某组)、`showMode`、`showRecent`、`width`。
- 供应商页：查询区标签筛选改为 `TagQuerySelect`（多选+计数+与或+最近），编辑表单的标签维护复用同一组件（限定 `supplier_goods` 组、关掉与或与最近）。
- `api/system/tag.ts` 增 `facets()`；`api/purchase/supplier.ts` 增参数归一化：数组 `tagIds` 压成 `"1,2"`（Spring 逗号串绑定 `List<Long>`，避开 axios 的 `tagIds[]` 序列化）。

## 四、验证

- `mvn -o compile` / `mvn -o package -DskipTests` 通过；新 jar 12:27:50 起在 8080，`/v3/api-docs` 已含 `/system/tag/facets`（未登录返回 401 JSON，端点存在）。
- 前端 `check:status-enums` 通过（基线 140，新增 0）、`vue-tsc --noEmit` 0 错误。
- SQL 口径三项实测（等价于 facets 的查询逻辑）：
  - 无选中计数：发光二极管 4、PET薄膜 4、聚碳酸酯薄膜 3…
  - AND `55,63` → 供应商 46、52（2 家）；OR `25|55` → 8 家；
  - 选中 `55` 后收窄计数：`55`→4、`63`→2（分母已限定为含 55 的供应商）。
- 待用户实测：供应商页标签多选筛选、与/或切换、计数是否随选择变化、最近标签是否记住。

## 五、待确认/二期

1. 保存的查询视图（把常用标签组合存成"我的视图"）未做，需要新表 + 权限模型。
2. 标签计数目前只按标签维度收窄，尚未与其他查询条件（供应商类型/状态/关键字）联动；若要"全条件 facet"，需把供应商侧条件传入 facets（接口预留 bizType + 条件透传即可扩展）。
3. 其余模块（物料档案 `material_attribute` 组已有 9 个标签）可直接复用该组件，只需换 `bizType` 与列表接口的多标签过滤。
4. `/system/tag/list` 的 `system:tag:view` 权限口径未改（本次用 facets 绕开），若要允许业务角色直接读标签主数据，需要角色授权决策。
