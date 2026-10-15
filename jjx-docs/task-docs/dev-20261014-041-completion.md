# 工序图标永久资产交付

任务码：dev-20261014-041（任务 3248）

1）结果

工序图标改为专用上传入口 POST /engineering/process-icons。沿用工序新增或编辑权限，后端事务内完成附件上传、字典登记及 ASSET 永久化，不再要求调用者拥有系统字典新增权限。复用 sys_attachment 和 engineering_jjx_icon 字典，不建表、不建业务附件引用。

ASSET 工序图标对登录用户共享读取，不受上传者或七天期限限制；永久资产受通用清理、释放和解绑守卫保护，禁止转为业务附件引用。工序换图、清除或字典停用均不删除原文件，停用只影响后续选取。

PNG/JPEG 以实际解码结果识别，最大 1MB、宽高不超过 2048 像素。SVG 使用静态元素及属性白名单，禁止脚本、样式表、DOCTYPE、外部资源等；仅本入口校验后的资产 SVG 可内联展示，并施加 CSP。复杂 SVG 请转换为 PNG 后上传。

2）范围

10 个源文件：新增 ProcessIconController、ProcessIconService、ProcessIconValidator、processIcon.ts；修改 AttachmentUploadState、AttachmentAccessService、AttachmentRefService、AttachmentDownloadService、JJXIcon/index.vue、AttachmentEnum.ts。另本交付文档及 INDEX。

3）验证与限制

- mvn -o -q -DskipTests compile：通过。
- npm run check:status-enums：通过，新增违规 0。
- 表数基线：通过，130 张表；现有同构表告警保留。
- 标准 npx vue-tsc --noEmit：受既有 TS6307 阻塞，涉及 productFilePreview.ts 的 composite 文件清单；不属于本任务。
- 补充 npx vue-tsc --noEmit --composite false：通过。
- 文档门禁及 git diff --check：通过。
- 用户明确要求不做回归测试，因此未编写或运行回归测试；跨用户、过期、清理及浏览器行为未做运行态验收。

开工只读核查：本机附件表 0 行、图标字典 49 项、标准工序 51 条，未发现附件图标 URL，因此无需历史数据迁移。任务登记与状态更新之外未修改业务数据。

上传失败时数据库事务回滚；磁盘文件沿用公共上传链路现有策略：回滚只记日志保留孤立文件供人工核查，不自动物理删除。未新增资产删除功能。

4）交付与部署

代码提交推送后将任务置待审核。需用户部署前后端并手工重启后端，本次未进行服务生命周期操作。原任务 027 文档所述“关闭清理即不受影响”不适用于 TEMP 访问有效期；本任务通过永久资产状态解决。

工作区原有 jjx-docs/sql/backups/clean-test-data-log.txt 修改未纳入本任务。
