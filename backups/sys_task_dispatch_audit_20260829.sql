-- MySQL dump 10.13  Distrib 8.4.10, for Linux (x86_64)
--
-- Host: 127.0.0.1    Database: jjx_erp_db
-- ------------------------------------------------------
-- Server version	8.4.10-0ubuntu0.26.04.1

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Dumping data for table `sys_task`
--
-- WHERE:  task_code IN ('dev-1785568977000','dev-178730365451690916305','dev-178730365451690916306','dev-178730365451690916303','dev-178730365451690916307','dev-178730365451690916304','dev-1787306227388')

LOCK TABLES `sys_task` WRITE;
/*!40000 ALTER TABLE `sys_task` DISABLE KEYS */;
INSERT INTO `sys_task` VALUES (447,'dev-1785568977000','general','dev','设计任务（作废）','工程管理菜单\"设计任务\"评估结论：不独立做，复用看板即可（sys_task+看板模板已覆盖任务管理，设计任务本质是任务，独立页面会导致两套任务系统分裂）。菜单91指向空壳页，处理：隐藏菜单或后续在看板加design模板。',NULL,NULL,NULL,NULL,7,4,'low',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'admin','2026-08-01 15:22:57',NULL,'2026-08-01 15:22:57',NULL),(1088,'dev-178730365451690916303','general','dev','TT-UI-03 Task Tree 权限树清理','问题：角色权限中仍存在旧 production:dispatch:* / production:assignment:*。\n原因：旧 Dispatch/Assignment 模型删除后，对应权限点与角色关系未同步清除。\n修改范围：sys_menu 删除旧 dispatch/assignment 权限及 sys_role_menu 关系；派工管理权限统一为 production:task:view/dispatch/assign/recall/return/admin。\n验收标准：全库无 production:dispatch:* / production:assignment:* 残留；派工管理权限树=菜单+5按钮；角色授权符合业务语义（admin 全量、生产管理员 view+dispatch+assign+recall+return、车间主任/班组长 view+assign+recall+return、操作工 view）。',NULL,NULL,NULL,NULL,NULL,4,'P0',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'admin','2026-08-21 17:14:14',NULL,'2026-08-21 22:07:30',NULL),(1089,'dev-178730365451690916304','general','dev','TT-UI-07 分配任务按钮权限投影','问题：无人员任务节点时，能看页面的用户即可操作，应仅 production:task:dispatch 可首次分配；已有人员 TaskNode 时未严格按“本人节点 + production:task:assign + 有可分配数量”显示。\n原因：分配按钮显隐未完全消费后端权限与 TaskNode 身份投影，存在“能看页面就能操作”的风险。\n修改范围：派工管理列表 canDispatch 逻辑与后端 Execution VO 投影（myAssignableNodeId/可分配数量）核对收口。\n验收标准：无人员节点仅 production:task:dispatch 用户显示“分配任务”；已有节点仅本人 + task:assign + availableToAssign>0 显示；普通查看者不可操作。',NULL,NULL,NULL,NULL,NULL,4,'P0',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'admin','2026-08-21 17:14:14',NULL,'2026-08-21 22:07:31',NULL),(1090,'dev-178730365451690916305','general','dev','TT-UI-04 派工管理主列表收口','问题：派工管理主列表信息超出最终设计，包含与最终设计无关的列/信息。\n原因：列表沿用开发过程字段，未按最终列定义收口。\n修改范围：派工管理主列表列收口为 工单号/工序/任务链/任务数量/已完成/待完成/状态/操作。\n验收标准：主列表仅显示上述 8 列；列数据与后端投影一致；移除无关列后布局正常。',NULL,NULL,NULL,NULL,NULL,4,'P1',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'admin','2026-08-21 17:14:14',NULL,'2026-08-21 22:07:33',NULL),(1091,'dev-178730365451690916306','general','dev','TT-UI-05 任务链交互收口','问题：当前操作栏还有独立“任务链”按钮，与“任务链列可点击”重复。\n原因：早期实现同时保留列点击与操作栏按钮，未收敛交互入口。\n修改范围：移除操作栏独立“任务链”按钮；任务链列本身可点击打开 Task Tree Drawer；未分配显示“未分配”。\n验收标准：操作栏不再有独立“任务链”按钮；点击任务链列打开任务树 Drawer；未分配行显示“未分配”；无重复入口。',NULL,NULL,NULL,NULL,NULL,4,'P1',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'admin','2026-08-21 17:14:14',NULL,'2026-08-21 22:07:34',NULL),(1092,'dev-178730365451690916307','general','dev','TT-UI-06 开发文案清理','问题：正式页面显示“Execution → TaskNode → WorkReport”等开发术语。\n原因：P3 实现时把内部模型名直接暴露在页面副标题/说明中。\n修改范围：派工管理/任务树 Drawer/分配弹窗/报工 Drawer 等页面中的内部模型术语清理为业务文案（如“任务树/报工/已完成”）。\n验收标准：正式页面不再出现 Execution/TaskNode/WorkReport 等内部术语；业务用户可直接理解。',NULL,NULL,NULL,NULL,NULL,4,'P1',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'admin','2026-08-21 17:14:14',NULL,'2026-08-21 22:07:36',NULL),(1096,'dev-1787306227388','general','dev','TT-UI-09 分配任务人员选择器部门名称错误','问题：\n派工管理 → 分配任务 → 添加人员 → 选择执行人，\n部门树当前显示：\n部门6、部门9、部门12、部门7、部门8\n等数据库 ID，而不是实际部门名称。\n\n目标：\n人员选择器必须展示真实组织部门名称，不允许使用“部门{id}”作为正常业务展示。\n\n要求：\n\n1. 查清 /production/task-node/candidates 返回的数据结构。\n2. 查清当前“部门{id}”文案产生位置。\n3. 候选人数据必须提供真实：\n   deptId\n   deptName\n   userId\n   userName\n   以及构建组织树所需的父子部门信息。\n4. 前端 OperatorPicker 使用真实 deptName。\n5. 不允许通过前端硬编码 deptId → 部门名称。\n6. 部门名称缺失属于数据异常：\n   不得静默显示“部门{id}”冒充正常名称。\n7. 保持当前候选人员权限范围规则不变，本卡只修部门名称/组织树展示。\n8. 验收：\n   页面不再出现“部门6/部门7/部门8/部门9/部门12”；\n   应显示数据库 sys_dept 中对应真实部门名称；\n   人员仍位于正确部门节点下；\n   搜索姓名/部门仍正常。\n\n只登记任务。\n不要修改代码。\n不要顺带处理其他卡。\n不要 commit/push。\n完成后停止。','production',NULL,NULL,'未分配',NULL,4,'normal',NULL,NULL,NULL,NULL,NULL,'2026-08-21',NULL,NULL,'2026-08-21 17:57:07',NULL,'2026-08-21 22:07:24',NULL);
/*!40000 ALTER TABLE `sys_task` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed
