# 历史保留规格表基线问题完成记录

任务码：dev-20261120-005；执行Codex；独立验收用户；提交/推送状态见sys_task追加记录。

1）结果：保留inventory_material_spec_attr_def（27行）和inventory_material_spec_value（0行），按后续用户保留决定补齐2条治理例外。例外以本轮005、2026-11-20、005-plan.md为依据，明确历史保留、不启用旧接口、不恢复第二属性真源。原例外和typeSuffixAllowlist完全不变，未修改门禁脚本或钩子。
2）历史与事实：原302批准、309/358退役、018后续保留的证据及当前运行时无引用核查见005-plan。目标库两表create_time为2026-11-20 15:46:22，只报告当前元数据，不推断重建责任或原因。业务表DDL/DML均未执行，两表及现行attribute_json/模板不改；库存/流水/原凭证不改，无迁移或自动备份。仅本任务登记、领取和完成记录写sys_task。
3）受控同步：scripts/check-model-baseline.sh --write-baseline退出0。表名快照原134项，原count字段136；新增6个已核实对象（4个此前已备案表与2张本轮历史保留表），校正为当前140项（138张BASE TABLE、2个VIEW），无基线表被移除。配置临时副本已清理，不生成业务库备份、不动6个既有备份删除。
4）正反验证：原approvedNewTables条目和类型白名单逐项比较相等；新增例外精确2条且字段完整。/tmp独立基线副本移除一张保留表及其例外，真实门禁退出1并报未批准表；另一副本删除proposalLink，真实门禁退出1并报缺依据；正式文件不受反例影响，临时副本自动清理。说明仍会拒绝未备案表和无依据的例外。
5）完整验证：NODE_OPTIONS=--max-old-space-size=3072 timeout 300 npm run validate于2026-11-20 16:50退出0，包含status-enums、model-baseline、result-case、docs、collation:strict、migration-collation、notnull、stock:strict、lot:strict、iqc-lineage:strict、quality-ledger:strict、doc-no及vue-tsc。库存严格对账五项0；各项跨表一致性为0。少量同构表告警和存量文档/非空提示为现有咨询信息，不影响门禁退出0；没有放宽其规则。
6）交付与边界：仅1份治理配置与3份文档（含INDEX）提交，零业务源文件改动。正常git push须经pre-push通过，无--no-verify。未改业务逻辑，因此不另跑业务单测或前后端打包；未启停服务。前轮第4项“加载新版后的页面验收”仍待用户安排，不在本任务执行范围；第5项表数基线问题已修复并自检通过，待用户独立审核。
