# 样品单接入公共附件中心与同链路只读展示

任务码：dev-20261012-029（ATT-V1B-04，P1）  
日期：2026-10-12  
状态：实现及本地验证完成，待独立运行验收；按本次指令不 commit / push。

## 1. 背景与前置证据

1）任务开始 `git status --short` 输出为空，退出码 0；HEAD 为 `e3b4d5b8`。两个目标文件没有他人在制改动。028 任务只读查询为 status=2（待审核），依赖按用户“已完成”口径推进。
2）原样品详情的 AttachmentPanel 调用本单据 list，并通过 listByTrace 合并来源文档；替换时必须保留这条读取链路。
3）API 契约核查为源码证明，未冒充真实 HTTP 取证：`src/api/system/attachment.ts` 的 listByTrace 请求 `/system/attachment/by-trace/{traceId}`；后端 SysAttachmentController 返回 `Result<List<AttachmentSourceVO>>`，并按 sources.canRead 过滤。SysAttachmentServiceImpl 的 `vo.setId(att.getId())` 证明 VO.id 为附件 ID；另带 fileName/fileSize/fileType/createTime/bizType/bizTypeName/bizId/sourceNo。返回结构与原页面消费方式相符，无需后端适配。
4）任务库旧 description 对 CustomerDocUpload“新建待上传无收尾”的担心不适用于本页：调用位于详情弹窗，`:biz-id="detailData.orderId"` 已有值；组件 confirmUpload 在 bizId 存在时直接调用 uploadRows。本次遵循最新实施卡，不改其本体、保存链路及生产/审核/派样业务。

## 2. 实施范围

1）`jjx-web/src/views/sales/sample-order/index.vue`：AttachmentPanel 替换 AttachmentCenter；sourceType=sample、sourceId=detailData.orderId、traceId=detailData.traceId、viewPerm=sales:sample:view、editPerm=sales:sample:edit、locked=false。按 orderId 设置 key，切换详情时隔离组件实例。客供按钮改“上传客供资料（需选产品）”。CustomerDocUpload 标签参数保持逐字一致；脚本除 import 替换外不变。
2）`jjx-web/src/components/attachment/AttachmentCenter.vue`：新增独立“同链路来源单据（只读）”段，通过 listByTrace 加载；保留来源类型、来源单号（缺省回退 ID）、文件名、大小、时间，只有下载和预览。以 Number(attachmentId) 与 A 段 ref.attachmentId、B 段 row.id 去重，同时去掉 trace 数组自身重复行；A 段不同 refId 仍完整保留。
3）同链路数据不进入 pending、绑定、解绑、元数据编辑链路；空 trace 或无 view 权限不发请求。切换 sourceType/sourceId/traceId/查看权限时清空旧数据并重新读取，用请求序号隔离晚到响应，卸载后不回填；失败只提示同链路错误，不清空 A/B。公开 refresh 同时刷新本单据与同链路。
4）保留旧面板对历史附件的预览能力：B 段补只读预览按钮，同链路段也提供预览，图片使用 image viewer，其他格式延用旧面板 downloadUrl 打开行为。已有 B/trace 文件时不显示“暂无附件”。
5）首次有查看权限即加载资料类型字典，确保已绑定 A 段首次打开即可用现有 024 的 docTypeLabel 展示“一级｜二级”，无需先编辑或上传。
6）旧组件不删除；useAttachmentRefs、后端、CustomerDocUpload 本体及其他业务页面无改动。公共组件所有已有 traceId 调用点也会获得只读同链路段，无 traceId 的入口不发同链路请求。

## 3. 真实验证输出

### 类型检查

命令：`cd jjx-web && npx vue-tsc --noEmit`  
输出：空（正常通过）；退出码 **0**。

### 定向执行实际组件源码（模拟 API，不是真实浏览器 E2E）

临时 Node 校验从实际 SFC 取 script setup，经 TypeScript 转译执行，使用真实 Vue 响应式与模拟附件 API；另编译模板、比较样品业务脚本/客供参数。临时文件位于 /tmp，任务结束清除。

命令：`node /tmp/verify-029.cjs`，最终退出码 **0**。

```text
PASS A/B/trace attachmentId dedup (numeric/string), duplicate trace rows, preserve distinct refIds; initial 一级｜二级 label
PASS scope switch clears old data; late old response ignored; image preview uses attachmentId
PASS trace failure/malformed response is visible and does not remove A/B; empty trace/no view permission skips request
PASS exposed refresh reloads both lists; unmount invalidates trace request
PASS template compiles; B/trace only preview/download; sample business script and CustomerDocUpload props unchanged
5 groups passed; source executed with mocked APIs, NOT browser/API E2E
```

校验脚本首次因本机 compiler-sfc 依赖布局、Node 子进程沙箱限制未完整运行；调整为 vue/compiler-sfc 入口，并用 shell 导出 HEAD 作为只读比较输入后完整通过，未因此修改业务逻辑。

### 枚举与差异检查

命令：`cd jjx-web && npm run check:status-enums`，退出码 **0**。

```text
> jjx-web@1.0.0 check:status-enums
> node scripts/check-status-magic-values.mjs

状态魔法值门禁通过（存量基线 122 处，新增 0 处）
```

`git diff --check` 退出码 **0**；仅本任务两个源文件、此报告及 INDEX 登记涉及变更。未跑全量测试、mvn 或生产打包，未启停服务。

## 4. 独立验收与边界

1）真实浏览器页面、真实文件上传/下载/解绑与客供上传未验证；本次交付为实现、类型检查、模拟 API 行为验证及静态兼容证明，不宣称浏览器验收完成。
2）人工验收：打开含 refs、历史附件及 trace 来源文档的样品单详情，核对三段按附件 ID 不重复；A 段分类显示一级｜二级；B/trace 只有下载/预览；有编辑权限仅 A 段提供分类/命名、解绑。连续打开不同样品单，核对无旧链路文件残留。
3）有编辑权限时通过通用入口上传并确认引用创建；客供入口选择产品与类型后确认上传，检查即时上传行为与原来一致。用只读账号复核操作可见性。此处为待执行验收步骤，不作为已通过证据。
4）listByTrace 沿用现有后端基于 sys_attachment.trace_id 的查询覆盖范围；本任务保留原同链路能力，不新增跨 refs 追溯或修改后端权限规则。
5）文档按当次明确指定交付到 history/（BOM + INDEX），不修改 CONVENTIONS。任务 status 回写 2，先读后追加 remark，保留旧备注；旧 description 的现场误判以本报告和追加备注纠正。

## 5. 文档门禁与任务回写

命令：`npm --prefix jjx-web run check:docs`，退出码 **0**。

```text
> jjx-web@1.0.0 check:docs
> node ../scripts/check-docs.mjs

文档门禁通过（history 241 篇 / modules 4 篇 / task-docs 1 篇；存量基线：缺 BOM 62 篇、命名不合规 53 篇）
```

任务回写仅涉及 sys_task 中 dev-20261012-029 的 status/remark；更新前后检查 CHAR_LENGTH，追加原备注，不修改业务数据。不 commit / push，提交哈希无。

回写真实输出（mysql 退出码 0）：

```text
before: dev-20261012-029 status=0 remark_length=101
proposed_length: 351
updated_rows: 1
after: dev-20261012-029 status=2 remark_length=351
```

最终 `git status --short` 只有 2 个源文件、INDEX 和本报告共 4 文件；`git diff --stat` 默认不统计尚未跟踪的新报告，因此显示 3 files changed，报告为 `??`。
