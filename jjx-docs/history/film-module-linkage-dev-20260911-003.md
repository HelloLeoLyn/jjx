# 菲林模块打通链路（档案可用 / 打样联动 / 网版联动）dev-20260911-003

## 一、背景与现状证据（改前实测）

| 项 | 证据 |
|---|---|
| 表 | `engineering_film` 结构完整（版本 `version/is_current/parent_film_id`、审批 `approve_status`、发布 `is_released`、设计人、菲林规格、`process_id`），但 **0 行** |
| 后端 | `/engineering/films` CRUD + 提交/审批/驳回/新版本/设当前/下发生产齐全；权限码 `engineering:film:*`（sys_menu 92 下 269~274 按钮行已存在） |
| 枚举 | `FilmTypeEnum`：面板/上层线路/间隔/下层线路/背胶 5 类，符合薄膜开关层结构 |
| 前端 | `views/product/film/index.vue`（335 行）走真接口无 mock |
| 菜单 | 工程管理(90) → 菜单名却叫「薄膜管理」，与表/接口/枚举的 film/菲林 不一致 |
| 已有引用 | 产品详情「菲林配置」表、产品文件库类别含「菲林」、样品工程工作台收「菲林图」、转量产就绪检查把菲林列为建议项 |

**三个断点（本次要解决的）**

1. **档案建不起来**：`filmCode` 后端 `@NotBlank` 但前端不传；create/update 是 `@RequestPart("dto")`（multipart）而前端传 JSON，必 400；图纸字段 `file_id/file_path` 服务层是 TODO 空实现，前端也没有上传控件；产品编码/名称不回填（总览页无法展示产品）。
2. **打样 → 菲林断链**：打样工作台上传的「菲林图」只落 `sys_attachment(bizType='sample')`，资料转移只生成 产品/BOM/工艺路线，不生成菲林 → 转量产检查里菲林永远黄灯（任务 770 登记未做）。
3. **菲林 ↔ 网版零关联**：`jjx_screen_master` 有 4502 条真实网版台账（A/B/C/F/G/H 框型），但没有产品/菲林列；菲林是制网版的上游，无法追溯。

## 二、方案（用户 2026-09-11 选定「方案 A」，口径：网版按人工指定框型生成，不随发布自动建）

### A1 菲林档案可用
- 后端：`EngineeringFilmDTO` 增 `fileId/filePath/fileName`、`filmCode` 转可选；controller create/update 改 `@RequestBody` JSON；`new-version` 的 `newVersion` 改可选；新增 `GET /engineering/films/page` 总览查询。
- 服务：编码自动生成 `FILM-{产品编码}-{类型短码}`（冲突续号）、版本自增 `v1.0→v1.1`、设计人/设计时间回填、产品编码名称回填、`listFilms` 过滤（产品/类型/审批状态/关键字）。
- 前端：产品选择器换公共组件 `ProductSelector`；不选产品=全部菲林；类型/审批状态/关键字筛选；**图纸上传**走 `/system/attachment/upload-product`（落产品文件库「菲林」类别）后回填 `fileId`，列表可下载；状态展示/分支全部改用 `src/enums/product/film.ts` 具名枚举。

### A2 打样 → 菲林联动
`SampleOrderServiceImpl.transferMaterials` 末尾新增第 ④ 步 `generateFilmDraftsFromSample`：
- 取该打样单工程附件（`bizType='sample'`），识别文件名含「菲林」或图纸类扩展名（dxf/dwg/ai/cdr/pdf/plt/eps/jpg/png）的条目；
- 按文件名猜类型（上层/下层/间隔/背胶，缺省面板菲林），生成 `engineering_film` 草稿（版本 v1.0、待补类型与尺寸、fileId 回填附件）；
- 产品已有菲林档案则跳过；任何异常只记日志，不影响资料转移主流程；
- 转移明细新增一行「菲林[自动生成N张草稿]」；转量产就绪检查的菲林提示改为指向两个补录入口（仍为建议项、不阻塞）。

### A3 菲林 → 网版联动
- 迁移 `91_film_screen_link.sql`：`jjx_screen_master` 增 `product_id/product_code/film_id` + 两个索引（可空、幂等，历史 4502 条不受影响）。
- 服务：`listByFilmId`、`createFromFilm`（网版号按框型自动续号 `A2778`，`content` = 产品编码+菲林类型+菲林名，备注留来源菲林与版本）。
- 接口：`GET /engineering/screen/by-film/{filmId}`（`engineering:screen:view`）、`POST /engineering/screen/from-film`（`engineering:screen:add`）。
- 前端：菲林页「网版」列 + 联动弹窗（查看该菲林生成的网版、按框型/目数一键生成）；网版管理页加「关联产品/菲林」列，历史台账显示"历史台账"。

## 三、改动清单

后端（jjx-server）
- `product/domain/dto/EngineeringFilmDTO.java`、`product/service/IEngineeringFilmService.java`、`product/service/impl/EngineeringFilmServiceImpl.java`、`product/controller/EngineeringFilmController.java`
- `sales/service/impl/SampleOrderServiceImpl.java`（资料转移 + 转量产检查文案）
- `engineering/domain/entity/ScreenMaster.java`、`engineering/service/IScreenMasterService.java`、`engineering/service/impl/ScreenMasterServiceImpl.java`、`engineering/controller/ScreenMasterController.java`

前端（jjx-web）
- 新增 `src/enums/product/film.ts`、`src/enums/engineering/screen.ts`
- `src/api/product/film.ts`、`src/views/product/film/index.vue`（重写）
- `src/api/engineering/screen.ts`、`src/views/engineering/screen/index.vue`（加关联列）
- `scripts/status-magic-baseline.json`（菲林页 4 条存量魔法值消除，只减不增）

数据库
- `jjx-docs/sql/migrations/90_film_menu_rename.sql`（薄膜→菲林，改菜单/按钮名，不动 perms）
- `jjx-docs/sql/migrations/91_film_screen_link.sql`
- 备份：`jjx_erp_db_backup_20260911-1448_before-90.sql`、`jjx_erp_db_backup_20260911-1451_before-91.sql`、`sys_task_register_dev-20260911-003_20260911-1443.sql`

## 四、验证

- 后端 `mvn -o compile` 通过（两次，含 A3 后）
- 前端 `npm run check:status-enums` 通过（基线 140，新增 0）；`vue-tsc --noEmit` 见提交时的实际输出
- 迁移经唯一通道 `bash scripts/db-migrate.sh` 执行，`sys_config.ops.schema.applied` 已记到 91
- 业务实测（待用户）：菲林建档+传图纸+提交审批+下发生产；打样单资料转移看明细是否出现「菲林[自动生成N张草稿]」；菲林弹窗生成网版并核对网版号续号（A 框现有最大号之后）

## 五、待确认点

1. 菲林「下发生产」目前只置状态，**不自动**生成网版（避免凭空造网版号）；若要"发布即自动生成 A 框网版"需再拍板。
2. 打样附件识别目前按文件名关键字（含「菲林」+ 图纸扩展名），后续可在工作台上传点加「类别」下拉（与销售附件分类需求 dev-20260904-010 同源）。
3. 网版页暂未加"按菲林/产品筛选"的下拉，只在菲林侧发起与查看。
