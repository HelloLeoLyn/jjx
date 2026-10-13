# 发布快照 P3：预览打印统一读取与完整性拦截

> 任务码：dev-20261013-011。父任务：dev-20261013-008。依赖：dev-20261013-010（P2，DOCSET_JSON 快照）。
> 交付前端统一读取与完整性拦截；真实接口/浏览器/实际打印未验证。

## 1. 目标

抽屉、预览、新窗口打印与版本面板统一读取**完整快照（DOCSET_JSON）**；新版**不再**请求当前产品/BOM/路线/
图标/附件清单补空；必需项失败不得输出看似完整的文档；旧版（无快照）显式标注「未固化」，不回退工作态。

## 2. 改动

1）`views/engineering/product-spec/components/docset.ts`
   - `loadDocsetForVersion`：优先按条目 `DOCSET_JSON` 走 `buildDocsetFromSnapshot`（**全程不触网**）；
     无 `DOCSET_JSON` 时走 `buildDocsetFromLegacyRefs`（仅按引用 id 读已批准 BOM/路线，非当前态），
     并显式告警「该版本未固化完整快照（旧版发布），工程图/客供等可能缺项」。
   - `buildDocsetFromSnapshot`：由快照装配 product/bom/routing/workSpec/files；快照损坏即抛错（不打印）。
   - 完整性：`snapshotWarnings` 区分「未固化正文」与「清单计数≠实际文件数」；空 BOM/路线属**原本为空**，不算缺项。
   - 快照文件 → `DocFile`（`published=true`），不再回查当前附件/图标。
2）`versionDocset.ts`：新增纯逻辑 `snapshotFileToDocFile`、`snapshotWarnings`（零依赖，供 node --test 回归）。
3）`components/product/ProductSpecDrawer.vue`：版本只读页优先解析 `DOCSET_JSON`（正文/图纸/路线项取自快照，
   不再单独调用 `SPEC_JSON`/路线/图纸接口）。
4）`ProductWorkSpecVersionPanel.vue`：条目类型标签补 `DOCSET_JSON`/`DOCSET_FILE`。
5）`api/product/workSpec.ts`：`getVersion` 已返回条目（含 `DOCSET_JSON`），无需改动契约。

字段契约见 `dev-20261013-010-completion.md` §2（`schemaVersion`/`confirmation.approved` 口径/`files`/`icons`）。

## 3. 行为与不变式

- 新窗口打印复用同一 `versionId` 与选择集合（`ProductSpecPreview` → `print.vue` 已传 `versionId`）。
- 新版文档内容全部来自快照，工作态变化不影响旧版；旧版不冒充完整快照、不回退工作态。
- 保留工作态读取（无 `versionId` 时走 `loadDocset`）、纸张布局与权限不变。

## 4. 验证

- `node --test`（前端定向回归，11 过）：快照文件映射、完整性告警（缺正文/计数不一致/空 BOM 不算缺项）、旧版引用路径回归。
- `npx vue-tsc --noEmit` → 0 error；`npm run check:status-enums` 新增 0；`npm run check:docs` 通过。

## 5. 未验证 / 遗留

- 真实接口、浏览器、新窗口实际打印未验证（遵循「仅代码与模拟测试」边界）。
- 既有 V1/V2（P2 前发布）无 `DOCSET_JSON`，走旧版回退并标注未固化；新发布版本方具备完整快照。历史核证在 P4。
- 打包图标的字节一致性由 P2 固化（`icons[].sha256`），P3 显示仍按冻结名/URL 渲染。
