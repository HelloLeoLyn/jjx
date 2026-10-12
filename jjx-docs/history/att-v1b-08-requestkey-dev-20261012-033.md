# 内容指纹requestKey修复（ATT-V1B-08）

任务码：dev-20261012-033；日期：2026-10-12；执行：Codex；验收：另一Hermes。沿用任务包不commit/push、不重启服务、不改后端及refs语义。

## 已核实根因

原buildRequestKey仅把draftKey、file.name、file.size、file.lastModified拼接后做旧哈希。同名、同大小、同mtime且不同内容会得到同一键。后端AttachmentLifecycleService.upload校验键为16～100位[A-Za-z0-9_-]，按owner与requestKey定位既有附件：同内容返回原id，异内容抛“同一requestKey不能上传不同文件”。后端保护正确，本任务只改前端。

## 范围与实现

仅修改`/home/administrator/jjx/jjx-web/src/composables/useAttachmentRefs.ts`的buildRequestKey及uploadTemp调用顺序。

1）先调用既有sha256Of(file)，文件摘要计算成功时取得64位十六进制指纹。
2）buildRequestKey改为async，使用Web Crypto计算SHA-256(`${draftKey}|${sha256}`)，输出64位十六进制，符合服务端键格式。
3）uploadTemp先算sha再await生成键，再调用原TEMP上传接口；队列、refs绑定、历史读取、权限与生命周期未改。
4）旧PendingEntry已有requestKey未回填或重写，已有失败绑定仍按原attachmentId重试。新算法作用于今后上传，不宣称跨算法部署前后的重新上传必然复用旧id。

目标文件当前为他人在制新文件，修改前副本用于限定差异；本轮不提交整个文件，不把其他任务内容作为本任务改动。

## 回退策略与已知限制

无crypto.subtle、文件指纹计算失败、或指纹键digest失败时，保留原名/大小/mtime算法，不阻断上传；该分支**未含内容指纹**。

回退仍可能对同形异内容生成相同键，此时服务端继续安全拒绝第二份内容，不覆盖旧文件。本任务不承诺无Web Crypto环境已解决该碰撞；若需这类环境也消除碰撞，应另行确认纯JS摘要方案，不能在本卡中扩展依赖。代码注释与本报告均明确记录此限制。

## 局部验证：真实输出与边界

`cd /home/administrator/jjx/jjx-web && npx vue-tsc --noEmit`：退出码0，无错误输出。

用TypeScript转译实际useAttachmentRefs源文件，在临时Node VM中运行uploadTemp；输入两份File均为same.txt、大小4、mtime=1234，内容分别AAAA和BBBB。使用Node Web Crypto验证实际摘要；上传接口模拟后端“同键同内容返回原id、异内容拒绝”的契约。另注入无crypto.subtle及第二次digest失败。

真实输出：

```text
模拟后端幂等契约：同名/大小/mtime异内容→不同key及模拟id；同文件重试→同模拟id；SHA256(draftKey|sha256)核对通过
无crypto.subtle：上传编排成功，旧算法逐字一致；回退未含内容指纹
指纹键digest失败：回退旧算法，不阻断上传
```

上述ID是模拟返回值，不是真实数据库attachmentId；没有实际HTTP上传或SQL业务写入，不能称为浏览器E2E/真实SQL验收通过。测试仅临时局部核查，没有新增测试类或全量测试。

`git diff --check`针对本轮文件及报告检查；报告带UTF-8 BOM，INDEX只追加本任务一条。`npm run check:docs`退出码0：文档门禁通过（history 238篇 / modules 4篇 / task-docs 1篇）。

## 另一Hermes验收步骤

在同一登录用户、同一单据/草稿上下文，用同名同大小同mtime的不同内容文件上传两次，记录Network中的requestKey与返回idA/idB；重复第一份记录idRetry。应idA≠idB且idRetry=idA，前两键不同且重试键相同。不要只按文件名查询或把其他会话附件计入结果。

记录实际返回ID后只读取证（替换占位符，不要直接执行占位SQL）：

```sql
SELECT id,file_name,file_size,sha256,upload_key,upload_state,expires_at
FROM sys_attachment WHERE id IN (<idA>,<idB>,<idRetry>);
```

期望只命中两份不同内容附件，sha256不同；重试返回第一份原id。服务端upload_key是owner与requestKey再哈希，不应要求其直接等于浏览器requestKey。

在无crypto.subtle环境验证上传仍成功；确认旧算法回退，明确其未含内容指纹。浏览器不能安全注入时写BLOCKED，不伪造结果；不改后端、不重启服务。

## 交接状态

实现及局部模拟已完成；真实浏览器两份附件ID、重复上传及SQL证据待另一Hermes。status=2代表待审核，不代表真实上传验收已通过。先读后追加033原remark，保留原记录。
