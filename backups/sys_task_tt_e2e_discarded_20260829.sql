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
-- WHERE:  task_code IN ('dev-1787307298081','dev-1787307309181','dev-1787307485283','dev-1787326127177')

LOCK TABLES `sys_task` WRITE;
/*!40000 ALTER TABLE `sys_task` DISABLE KEYS */;
INSERT INTO `sys_task` VALUES (1097,'dev-1787307298081','general','dev','TT-E2E-01 部分分配后的剩余分配入口丢失','复现：\n1. 工单 WO-PL2608210003-01\n2. 工序：面板冲形\n3. Execution 任务数量 200\n4. 首次给“冲型车间主任”分配 120\n5. 返回派工管理列表\n\n期望：\n- 系统 Root effective = 200\n- childOccupied = 120\n- Root availableToAssign = 80\n- 派工管理该行仍显示“分配任务”\n- 再次进入分配弹窗可以继续分配剩余 80\n- 已分配的直接子节点可以在弹窗中执行“收回”\n\n实际：\n- 分配完成后操作栏只剩“任务链”\n- “分配任务”入口消失\n- 因此无法继续分配剩余数量，也无法进入弹窗收回\n','production',NULL,NULL,'未分配',NULL,4,'normal',NULL,NULL,NULL,NULL,NULL,'2026-08-21',NULL,NULL,'2026-08-21 18:14:58',NULL,'2026-08-21 22:07:25',NULL),(1098,'dev-1787307309181','general','dev','【TT-E2E-02 核实部分分配数量是否被错误变成全部数量】','同一数据：\nExecution = 面板冲形，任务数量 200\n人工输入给“冲型车间主任”分配 120。\n\n请登记独立核查卡：\n- 查询 production_task_node 实际 child.task_quantity\n- 如果数据库为 120，则属于列表展示语义问题，不修改数量模型\n- 如果数据库为 200，则属于 assign 写入数量 BUG\n\n注意：\n派工管理列表“任务数量=200”本身是 Execution 总任务数量，\n不能据此判断子节点被分配了 200。\n\n只登记任务。\n不要修代码。\n不要修改数据库。\n登记完成后停止并报告 task_id/task_code。','production',NULL,NULL,'未分配',NULL,4,'normal',NULL,NULL,NULL,NULL,NULL,'2026-08-21',NULL,NULL,'2026-08-21 18:15:09',NULL,'2026-08-21 22:07:27',NULL),(1099,'dev-1787307485283','general','dev','TT-E2E-03 真实 TaskNode 持有人未显示“分配任务”','\n复现：\n1. 工单 WO-PL2608210003-01\n2. 工序存在真实 TaskNode：冲型车间主任\n3. 使用冲型车间主任账号登录派工管理\n4. 任务链可看到“冲型车间主任”\n5. 但操作栏只有“任务链”，没有“分配任务”\n\n正确规则：\n当前用户 = TaskNode.assignee\nAND 有 production:task:assign\nAND availableToAssign > 0\n→ 必须显示“分配任务”\n\n要求核查：\n1. 当前 TaskNode 的 assignee_id 是否等于登录用户ID\n2. 该节点 task_quantity / recalled_quantity / selfReported / childOccupied / availableToAssign\n3. 当前用户是否真实拥有 production:task:assign\n4. 后端是否正确返回 myAssignableNodeId / 可操作投影\n5. 前端是否仅消费后端投影，没有额外错误过滤\n\n验收：\n- 冲型车间主任持有节点且仍有可分配数量时显示“分配任务”\n- 非本人节点不显示\n- 没有 task:assign 权限不显示\n- availableToAssign=0 不显示\n\n只登记任务，不修复。\n不要改数据库。\n不要顺带处理其他卡。\n完成后停止。','production',NULL,NULL,'未分配',NULL,4,'normal',NULL,NULL,NULL,NULL,NULL,'2026-08-21',NULL,NULL,'2026-08-21 18:18:05',NULL,'2026-08-21 22:07:28',NULL),(1101,'dev-1787326127177','general','dev','BUG：Execution Root 部分分配后，剩余容量存在但“分配任务”按钮消失。','BUG：Execution Root 部分分配后，剩余容量存在但“分配任务”按钮消失。\n\n复现：\nExecution 总量100\nRoot已分配20给主任\nrootAvailableToAssign=80\n页面显示当前剩余80\n但操作栏无“分配任务”。\n\n预期：\n全局生产管理员 + production:task:dispatch\nAND rootAvailableToAssign > 0\n→ Execution 父行必须继续显示“分配任务”。\n\n允许继续部分分配：\n100 → 分20 → 剩80 → 再分30 → 剩50……\n不要求一次性分完。\n\n不要改数量模型。\n先分析按钮当前绑定条件，再给最小修复方案。','production',NULL,NULL,'未分配',NULL,4,'normal',NULL,NULL,NULL,NULL,NULL,'2026-08-21',NULL,NULL,'2026-08-21 23:28:47',NULL,'2026-08-27 23:07:34',NULL);
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
