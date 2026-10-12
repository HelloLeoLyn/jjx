# 公共附件 V1-A 交付及人工验收清单

本次交付为待人工验收的代码与SQL，未部署、未执行迁移/回填、未启停服务、未物理删除、未Git commit/push。数据库仅执行SELECT/SHOW/只读WITH查询，没有登记或更新任务。仓库未找到已评审设计全文，按用户本次明确的V1-A规则实施。

1）修改文件清单

共27个文件，范围如下；工作区原有文档删除、components.d.ts、其他会话的备份与日志不属于本任务。

| 操作 | 文件 |
|---|---|
| 新增 | `jjx-server/src/main/java/com/jjx/system/controller/SysAttachmentRefController.java` |
| 新增 | `jjx-server/src/main/java/com/jjx/system/domain/dto/AttachmentRefDTO.java` |
| 新增 | `jjx-server/src/main/java/com/jjx/system/domain/dto/AttachmentRefMetadataDTO.java` |
| 新增 | `jjx-server/src/main/java/com/jjx/system/domain/entity/SysAttachmentRef.java` |
| 新增 | `jjx-server/src/main/java/com/jjx/system/enums/AttachmentUploadState.java` |
| 新增 | `jjx-server/src/main/java/com/jjx/system/mapper/SysAttachmentRefMapper.java` |
| 新增 | `jjx-server/src/main/java/com/jjx/system/service/AttachmentAccessService.java` |
| 新增 | `jjx-server/src/main/java/com/jjx/system/service/AttachmentDocumentTypeService.java` |
| 新增 | `jjx-server/src/main/java/com/jjx/system/service/AttachmentDownloadService.java` |
| 新增 | `jjx-server/src/main/java/com/jjx/system/service/AttachmentLifecycleService.java` |
| 新增 | `jjx-server/src/main/java/com/jjx/system/service/AttachmentRefService.java` |
| 新增 | `jjx-server/src/main/java/com/jjx/system/service/AttachmentSourcePolicy.java` |
| 修改 | `jjx-server/src/main/java/com/jjx/system/controller/SysAttachmentController.java` |
| 修改 | `jjx-server/src/main/java/com/jjx/system/domain/entity/SysAttachment.java` |
| 修改 | `jjx-server/src/main/java/com/jjx/system/mapper/SysAttachmentMapper.java` |
| 修改 | `jjx-server/src/main/java/com/jjx/system/service/FileBackupService.java` |
| 修改 | `jjx-server/src/main/java/com/jjx/system/service/SysDictService.java` |
| 修改 | `jjx-server/src/main/java/com/jjx/system/service/impl/SysAttachmentServiceImpl.java` |
| 修改 | `jjx-server/src/test/java/com/jjx/system/service/impl/SysAttachmentHashDetectionTest.java` |
| 新增 | `jjx-docs/sql/migrations/271_attachment_references.sql` |
| 新增 | `jjx-docs/sql/migrations/272_attachment_reference_backfill.sql` |
| 修改 | `scripts/clean-archive-ocr-data.sh` |
| 修改 | `scripts/db-clean-test-data.sh` |
| 修改 | `jjx-docs/sql/00_clean_test_data.sql` |
| 修改 | `jjx-docs/guides/scripts-commands-20260914.md` |
| 新增 | `jjx-docs/history/common-attachment-v1a-20261012.md` |
| 修改 | `jjx-docs/history/INDEX.md` |

2）数据库迁移及执行状态

`271_attachment_references.sql`：新增引用表、外键RESTRICT、来源/产品索引；用product NULL→0和活动标记生成列建立活动引用唯一约束，软删历史可重复保留。业务来源是多态关系，不能使用单一外键；由来源策略校验实际对象。sys_attachment新增upload_state、expires_at、upload_key、upload_owner；后两列支持按用户隔离的上传重试。历史行默认LEGACY，原ID/路径/hash/biz_type/biz_id保留。预检同名结构冲突，按列/索引存在性重试；增加禁止DELETE附件记录的触发器。新增统一一级/二级字典及通用资料初始项。

`272_attachment_reference_backfill.sql`：仅INSERT引用，回填productId和documentType均NULL，不覆盖原附件元数据、不更新历史硬引用。幂等跳过已记录的相同来源+空产品上下文，包括已解绑记录，不复活解绑引用。

两份SQL均未执行。只读账本最大号268，目录原最大号270；269/270尚未应用，但已有实体/查询依赖其客供资料列，因此部署前必须人工核对依赖。DDL可能部分提交，不能依赖事务回滚DDL。人工确认/备份后按db-migrate.sh逐份执行，不直接使用--all。新增触发器需要相应数据库权限。

3）新增接口清单

统一前缀 `/system/attachment/refs`。

| 方法 | 路径 | 行为 |
|---|---|---|
| POST | 前缀本身 | attachmentId建立引用／TEMP绑定／跨业务复用；相同活动引用返回原refId |
| GET | 前缀本身 | sourceType、sourceId查询当前引用 |
| PUT | /{refId} | 替换documentType、displayName、remark、sortOrder；null清空可选字段 |
| DELETE | /{refId} | 幂等解绑；原物理文件不变 |
| GET | /product/{productId} | 明确产品上下文及产品来源引用；逐来源过滤权限 |
| GET | /{refId}/download | 指定引用来源鉴权后下载 |
| GET | /{refId}/preview | 指定引用来源鉴权后预览白名单图片/PDF |
| POST | /temp | multipart file + requestKey，登录用户独占7天临时附件 |
| POST | /cleanup-preview | 管理员分页预演；afterId/limit，limit≤100；有日志审计，无实际删除 |
| GET | /by-sha256 | 指定sourceType/sourceId范围内检测相同SHA-256；不合并路径 |
| GET | /document-types | 一级/二级字典及停用类型历史标签 |

POST引用请求字段：attachmentId、sourceType、sourceId、可空productId、可空documentType/displayName/remark/sortOrder。来源单号由后端查询，不接受客户端伪造。upload requestKey为16至100位字母/数字/下划线/短横线，同用户同键同内容重试返回原附件ID，异内容拒绝。每个新附件ID使用独立随机路径。

4）旧接口兼容处理

旧附件ID与下载URL保留；下载改为检查可见活动引用或未被显式解绑的旧业务关系。未知来源不再仅凭登录放行。旧上传、列表、SHA查询及操作补权限检查，trace/customer-docs按来源过滤。

旧DELETE /{id}在可唯一确定原业务引用时解绑该引用；有多个候选上下文时要求refId，防止删除其他业务的引用。未回填的旧记录仍可进入回收站，保留物理文件。旧物理删除与过期清理入口明确拒绝；移除03:30回收站自动删除计划。

5）历史回填与对账

只读预演最新快照：总计12条，INSERTABLE=8、INVALID_SOURCE_ID=3、DELETED_ATTACHMENT=1；实际回填0条。工作期间其他会话新增附件，数量不是固定迁移预期，实际以执行时脚本输出为准。初次预演发现业务来源编码排序规则不同，已统一ASCII二进制比较并只读复验成功。

回填SQL先锁附件行、保存临时对账快照，再按明确业务存在性插入引用。输出每类数量、跳过明细、实际插入数、附件身份/路径/hash/旧业务键差异、引用缺口、规范/质量模板/菲林硬引用差异及重复活动引用。差异必须为0；产品/分类不猜测。实体存在及文件名/路径元数据有效不等于文件实际存在，物理文件缺失仍须人工清单核查。

用户人工执行前应安排维护窗口并按规则手工备份；再次执行只回填未记录上下文。SQL不删除或改写旧文件，不给已发布规范切换引用形式。

6）权限与删除保护

来源白名单：product、inquiry、quotation、sales_order、sample、purchase_order、production_order、material；复用其业务控制器现有view/edit权限。支持order/sample_order/purchase/production旧别名归一。销售订单与样品父单分开校验，不能通过伪造sourceType借用另一模块权限。指定productId必须属于该来源且具有product:view；purchase_order产品关系未确认，非空productId直接拒绝。未确认来源直接拒绝访问/回填，不凭名称猜测。

新建/复用要求附件原来源可读+目标来源可编辑；按refId下载只检查该条引用来源，不能用另一个可见引用绕过。附件行锁串行化绑定/解绑、唯一键防重复；重试解绑不重置30天期限。TEMP按上传人校验，上传requestKey按用户隔离；同用户上传由用户行锁串行化。

受控/已下发/现行文件、所有规范版本DRAWING条目、质量模板file_id、菲林file_id、产品结构图、trace证据以及历史审批/样品/客户附件字段均拦截解绑/清理。历史文本引用使用保守数字边界匹配，可能多保护，不冒险少保护。所有LEGACY文件均标记人工复核，不进入自动清理候选，未查清的隐式JSON/路径引用因此保留。

TEMP期限7天；最后有效引用解除后30天；预演重新查活动引用和实际保护状态，不单凭upload_state。旧路径多ID共享时阻止清理，不改历史路径。V1-A没有自动物理删除实现。

两个旧原始SQL清理入口因不能证明历史硬引用安全，写模式在任何写入之前禁用：clean-archive-ocr-data.sh --yes、db-clean-test-data.sh --execute；仍允许只读预览。此项会影响人工旧清库流程，需要验收确认后设计独立安全清理方案。数据库DELETE触发器是附加防线，不能防止DBA手工TRUNCATE或删除磁盘文件；本实现不声称能约束有数据库管理权限的人。

字典复用现有system:dict权限维护。二级ext_data格式为{"parent":"general","sources":["product","inquiry"]}；引用写入校验父分类、二级类型启用状态及来源范围。停用不影响历史标签读取、未变分类的备注修改；公共字典/项禁止删除和改编码，用停用保留历史。不引入客供资料版本规则。

7）编译与最小验证

后端 `mvn -f jjx-server/pom.xml -o -Dmaven.test.skip=true compile -q`通过。只运行现有SysAttachmentHashDetectionTest，扩充3个针对性断言，无新增测试类：总6项，0失败、0错误。首次受Mockito自挂载限制失败，显式Java agent重跑通过：

```sh
mvn -f jjx-server/pom.xml -o -Dtest=SysAttachmentHashDetectionTest -DargLine=-javaagent:/home/administrator/.m2/repository/org/mockito/mockito-core/5.20.0/mockito-core-5.20.0.jar test -q
```

脚本bash -n通过；git diff --check通过；回填查询只读预演通过；静态断言引用表19列、回填不UPDATE/DELETE旧附件或硬引用通过。check:docs结果见本报告末尾。没有全量测试、前端构建、服务重启、迁移执行、HTTP联调、物理清理。6项测试不是端到端权限与并发验证。

8）尚未完成/未启用

运行环境迁移与HTTP联调由人工决定；未实际回填。没有启用自动删除。未确认来源（包括3条biz_id=0的engineering_jjx_icon）处于拒绝/保留状态；inbound/outbound/drawing等未验证来源也不开放。现有图标若依赖旧通用下载会被拒绝，必须先明确其独立授权和来源模型再开放。

上传写盘成功后事务失败会记录孤立路径供人工核查；按同键可重试，但V1-A不自动删除无数据库记录的孤立文件。大型列表当前未增加分页（清理预演有分页），后续按规模评估。

9）风险与阻塞

仓库缺少所述设计全文。269/270未执行，271/272及真实迁移重跑、NULL唯一性/外键、并发绑定解绑、双用户越权、TEMP跨用户/过期、最后解绑30天时钟均需人工环境验收。DDL只做静态审查，没有声称在数据库执行成功。现有权限以模块view/edit为粒度，未发现可复用的来源行级范围服务，若业务还有未落实的组织/人员行级授权，需要先补来源适配再开放相关来源。

既有LEGACY在解绑后仍保留旧biz_type/biz_id用于兼容，统一下载不会因已解绑旧关系放行；其他直接读取附件表的既有聚合页可能仍显示旧元数据，本轮不切换页面。未知引用不会因这个兼容安排被物理删除。

10）V1-B建议

先在人工批准的验证环境应用迁移，核对回填与双用户权限/并发/时钟边界，再确定未映射来源的授权。完成后接入公共前端组件与业务页；独立评审历史JSON/路径硬引用清单、失败上传孤立文件台账、旧清库替代流程，最后再决定是否开放真实清理。
