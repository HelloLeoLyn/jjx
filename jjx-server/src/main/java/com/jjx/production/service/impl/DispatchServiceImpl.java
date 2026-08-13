package com.jjx.production.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jjx.common.core.page.PageResult;
import com.jjx.common.exception.BusinessException;
import com.jjx.production.domain.dto.DispatchAssignDTO;
import com.jjx.production.domain.dto.DispatchQueryDTO;
import com.jjx.production.domain.entity.ProductionDispatch;
import com.jjx.production.domain.entity.ProductionDispatchLog;
import com.jjx.production.domain.entity.ProductionOperationExecution;
import com.jjx.production.domain.vo.DispatchVO;
import com.jjx.production.enums.DispatchStatusEnum;
import com.jjx.production.mapper.ProductionDispatchLogMapper;
import com.jjx.production.mapper.ProductionDispatchMapper;
import com.jjx.production.mapper.ProductionOperationExecutionMapper;
import com.jjx.production.mapper.ProductionOrderMapper;
import com.jjx.production.service.DispatchService;
import com.jjx.system.domain.entity.SysDept;
import com.jjx.system.domain.entity.SysUser;
import com.jjx.system.mapper.SysDeptMapper;
import com.jjx.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 生产派工 Service 实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DispatchServiceImpl extends ServiceImpl<ProductionDispatchMapper, ProductionDispatch> implements DispatchService {

    private final ProductionDispatchMapper dispatchMapper;
    private final ProductionDispatchLogMapper dispatchLogMapper;
    private final ProductionOperationExecutionMapper executionMapper;
    private final ProductionOrderMapper orderMapper;
    private final SysDeptMapper sysDeptMapper;
    private final SysUserMapper sysUserMapper;
    private final JdbcTemplate jdbcTemplate;

    // ==================== 查询 ====================

    @Override
    public PageResult<DispatchVO> page(DispatchQueryDTO query) {
        // 2026-08-13：派工工作台——按工单工序展示（execution LEFT JOIN dispatch），未派工工序直接可见可指派
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        if (query != null) {
            if (StringUtils.isNotBlank(query.getOrderNo())) {
                where.append(" AND o.order_no LIKE ?");
                args.add("%" + query.getOrderNo() + "%");
            }
            if (query.getTeamId() != null) {
                where.append(" AND d.team_id = ?");
                args.add(query.getTeamId());
            }
            if (query.getStatus() != null) {
                int st = query.getStatus();
                if (st == 0) {
                    // 待派工：未生成派工单（或派工单仍为待派工态）
                    where.append(" AND (d.dispatch_id IS NULL OR d.status = 0)");
                } else {
                    where.append(" AND d.status = ?");
                    args.add(st);
                }
            }
            if (StringUtils.isNotBlank(query.getKeyword())) {
                where.append(" AND (e.process_name LIKE ? OR COALESCE(sp.process_name,'') LIKE ? OR e.major_category LIKE ?)");
                String kw = "%" + query.getKeyword() + "%";
                args.add(kw);
                args.add(kw);
                args.add(kw);
            }
        }
        String base = " FROM production_operation_execution e"
                + " LEFT JOIN production_order o ON o.order_id = e.order_id"
                + " LEFT JOIN engineering_standard_process sp ON sp.process_id = e.process_id"
                + " LEFT JOIN production_dispatch d ON d.execution_id = e.execution_id";
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) " + base + where, Long.class, args.toArray());
        int pageNum = query == null ? 1 : query.getPageNum();
        int pageSize = query == null ? 10 : query.getPageSize();
        String sql = "SELECT e.execution_id, e.order_id, o.order_no,"
                + " COALESCE(e.process_name, sp.process_name, e.major_category) AS process_name,"
                + " e.major_category, e.process_order, e.execution_status, o.planned_quantity,"
                + " d.dispatch_id, d.team_id, d.team_name, d.equipment_id, d.equipment_name, d.operators,"
                + " d.status AS dispatch_status, d.assigned_by, d.assigned_by_name, d.assign_time,"
                + " d.re_dispatch_count, d.reject_reason, d.remark, d.create_by, d.create_time"
                + base + where
                + " ORDER BY e.order_id ASC, e.process_order ASC"
                + " LIMIT ? OFFSET ?";
        args.add(pageSize);
        args.add((long) (pageNum - 1) * pageSize);
        List<DispatchVO> vos = jdbcTemplate.query(sql, (rs, i) -> {
            DispatchVO vo = new DispatchVO();
            vo.setExecutionId(rs.getLong("execution_id"));
            vo.setOrderId(rs.getLong("order_id"));
            vo.setOrderNo(rs.getString("order_no"));
            vo.setProcessName(rs.getString("process_name"));
            vo.setMajorCategory(rs.getString("major_category"));
            vo.setProcessOrder(rs.getInt("process_order"));
            vo.setExecutionStatus(rs.getInt("execution_status"));
            vo.setPlannedQuantity(rs.getBigDecimal("planned_quantity"));
            long did = rs.getLong("dispatch_id");
            if (!rs.wasNull()) {
                vo.setDispatchId(did);
                vo.setTeamId(getNullableLong(rs, "team_id"));
                vo.setTeamName(rs.getString("team_name"));
                vo.setEquipmentId(getNullableLong(rs, "equipment_id"));
                vo.setEquipmentName(rs.getString("equipment_name"));
                vo.setOperators(rs.getString("operators"));
                vo.setAssignedBy(getNullableLong(rs, "assigned_by"));
                vo.setAssignedByName(rs.getString("assigned_by_name"));
                vo.setAssignTime(rs.getTimestamp("assign_time") == null ? null : rs.getTimestamp("assign_time").toLocalDateTime());
                vo.setReDispatchCount(rs.getInt("re_dispatch_count"));
                vo.setRejectReason(rs.getString("reject_reason"));
                vo.setRemark(rs.getString("remark"));
                vo.setCreateBy(rs.getString("create_by"));
                vo.setCreateTime(rs.getTimestamp("create_time") == null ? null : rs.getTimestamp("create_time").toLocalDateTime());
                int st = rs.getInt("dispatch_status");
                vo.setDispatchStatus(st);
                vo.setStatusLabel(DispatchStatusEnum.labelOf(st));
            } else {
                vo.setDispatchStatus(0);
                vo.setStatusLabel(DispatchStatusEnum.labelOf(0));
            }
            return vo;
        }, args.toArray());
        Page<DispatchVO> p = new Page<>(pageNum, pageSize);
        p.setTotal(total == null ? 0 : total);
        return PageResult.of(p, vos);
    }

    private Long getNullableLong(java.sql.ResultSet rs, String col) throws java.sql.SQLException {
        long v = rs.getLong(col);
        return rs.wasNull() ? null : v;
    }

    @Override
    public List<DispatchVO> listPending(Long orderId) {
        if (orderId == null) return new ArrayList<>();
        List<DispatchVO> vos = jdbcTemplate.query(
                "SELECT e.execution_id, e.order_id, o.order_no,"
                        + " COALESCE(e.process_name, sp.process_name, e.major_category) AS process_name,"
                        + " e.major_category, e.process_order, e.execution_status, o.planned_quantity,"
                        + " d.status AS dispatch_status"
                        + " FROM production_operation_execution e"
                        + " LEFT JOIN production_order o ON o.order_id = e.order_id"
                        + " LEFT JOIN engineering_standard_process sp ON sp.process_id = e.process_id"
                        + " LEFT JOIN production_dispatch d ON d.execution_id = e.execution_id"
                        + " WHERE e.order_id = ? AND (d.dispatch_id IS NULL OR d.status IN (0,4))"
                        + " ORDER BY e.process_order ASC",
                (rs, i) -> {
                    DispatchVO vo = new DispatchVO();
                    vo.setExecutionId(rs.getLong("execution_id"));
                    vo.setOrderId(rs.getLong("order_id"));
                    vo.setOrderNo(rs.getString("order_no"));
                    vo.setProcessName(rs.getString("process_name"));
                    vo.setMajorCategory(rs.getString("major_category"));
                    vo.setProcessOrder(rs.getInt("process_order"));
                    vo.setExecutionStatus(rs.getInt("execution_status"));
                    vo.setPlannedQuantity(rs.getBigDecimal("planned_quantity"));
                    vo.setDispatchStatus(0);
                    vo.setStatusLabel(DispatchStatusEnum.labelOf(0));
                    return vo;
                }, orderId);
        return vos;
    }

    @Override
    public List<DispatchVO> listByOrder(Long orderId) {
        if (orderId == null) return new ArrayList<>();
        LambdaQueryWrapper<ProductionDispatch> w = Wrappers.lambdaQuery();
        w.eq(ProductionDispatch::getOrderId, orderId);
        w.orderByAsc(ProductionDispatch::getProcessOrder);
        List<DispatchVO> vos = new ArrayList<>();
        for (ProductionDispatch e : dispatchMapper.selectList(w)) vos.add(DispatchVO.fromEntity(e));
        return vos;
    }

    @Override
    public DispatchVO getById(Long id) {
        ProductionDispatch e = dispatchMapper.selectById(id);
        if (e == null) throw new BusinessException("派工单不存在");
        return DispatchVO.fromEntity(e);
    }

    @Override
    public List<ProductionDispatchLog> logs(Long dispatchId) {
        LambdaQueryWrapper<ProductionDispatchLog> w = Wrappers.lambdaQuery();
        w.eq(ProductionDispatchLog::getDispatchId, dispatchId);
        w.orderByAsc(ProductionDispatchLog::getCreateTime);
        return dispatchLogMapper.selectList(w);
    }

    // ==================== 指派/改派/批量 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DispatchVO assign(DispatchAssignDTO dto, String operatorName, Long operatorId) {
        // 改派：dispatchId 存在
        if (dto.getDispatchId() != null) {
            return reassign(dto, operatorName, operatorId);
        }
        if (dto.getExecutionId() == null) throw new BusinessException("缺少工序执行ID");
        if (dto.getOrderId() == null) throw new BusinessException("缺少工单ID");
        validateAssign(dto);

        ProductionOperationExecution exec = executionMapper.selectById(dto.getExecutionId());
        if (exec == null) throw new BusinessException("工序执行记录不存在");

        // 幂等：该工序已有派工单且非待派工/已退回 → 走改派
        ProductionDispatch exist = dispatchMapper.selectOne(Wrappers.<ProductionDispatch>lambdaQuery()
                .eq(ProductionDispatch::getExecutionId, dto.getExecutionId()).last("LIMIT 1"));
        if (exist != null) {
            dto.setDispatchId(exist.getDispatchId());
            return reassign(dto, operatorName, operatorId);
        }

        ProductionDispatch d = new ProductionDispatch();
        fillAssignFields(d, dto, operatorName, operatorId);
        d.setOrderId(dto.getOrderId());
        d.setExecutionId(dto.getExecutionId());
        d.setProcessName(processNameOf(exec.getProcessId()));
        d.setProcessOrder(exec.getProcessOrder());
        d.setOrderNo(orderNoOf(dto.getOrderId()));
        d.setStatus(DispatchStatusEnum.ASSIGNED.getCode());
        d.setReDispatchCount(0);
        d.setCreateBy(operatorName);
        dispatchMapper.insert(d);

        addLog(d.getDispatchId(), dto.getOrderId(), "ASSIGN",
                buildAssignContent(null, d, operatorName), operatorName, operatorId);
        log.info("派工成功: dispatchId={}, executionId={}, 主管={}", d.getDispatchId(), dto.getExecutionId(), operatorName);
        return DispatchVO.fromEntity(d);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchAssign(DispatchAssignDTO dto, String operatorName, Long operatorId) {
        if (dto.getOrderId() == null) throw new BusinessException("缺少工单ID");
        validateAssign(dto);

        // 该工单全部工序执行记录
        List<ProductionOperationExecution> execs = executionMapper.selectList(
                Wrappers.<ProductionOperationExecution>lambdaQuery()
                        .eq(ProductionOperationExecution::getOrderId, dto.getOrderId())
                        .orderByAsc(ProductionOperationExecution::getProcessOrder));
        if (execs.isEmpty()) throw new BusinessException("该工单没有工序，无法派工");

        int count = 0;
        for (ProductionOperationExecution exec : execs) {
            // 已有派工单且状态=已派工/执行中/已完成 → 跳过；待派工/已退回 → 改派
            ProductionDispatch exist = dispatchMapper.selectOne(Wrappers.<ProductionDispatch>lambdaQuery()
                    .eq(ProductionDispatch::getExecutionId, exec.getExecutionId()).last("LIMIT 1"));
            if (exist != null && (DispatchStatusEnum.ASSIGNED.getCode().equals(exist.getStatus())
                    || DispatchStatusEnum.EXECUTING.getCode().equals(exist.getStatus())
                    || DispatchStatusEnum.COMPLETED.getCode().equals(exist.getStatus()))) {
                continue;
            }
            DispatchAssignDTO item = new DispatchAssignDTO();
            item.setOrderId(dto.getOrderId());
            item.setExecutionId(exec.getExecutionId());
            item.setTeamId(dto.getTeamId());
            item.setEquipmentId(dto.getEquipmentId());
            item.setOperatorIds(dto.getOperatorIds());
            item.setRemark(dto.getRemark());
            item.setDispatchId(exist == null ? null : exist.getDispatchId());
            assign(item, operatorName, operatorId);
            count++;
        }
        return count;
    }

    /** 改派：已派工/执行中/已退回 → 重新指派 */
    private DispatchVO reassign(DispatchAssignDTO dto, String operatorName, Long operatorId) {
        validateAssign(dto);
        ProductionDispatch d = dispatchMapper.selectById(dto.getDispatchId());
        if (d == null) throw new BusinessException("派工单不存在");

        // 记录旧值用于流水
        ProductionDispatch old = new ProductionDispatch();
        old.setTeamName(d.getTeamName());
        old.setEquipmentName(d.getEquipmentName());
        old.setOperators(d.getOperators());

        fillAssignFields(d, dto, operatorName, operatorId);
        d.setStatus(DispatchStatusEnum.ASSIGNED.getCode());
        d.setRejectReason(null);
        d.setReDispatchCount((d.getReDispatchCount() == null ? 0 : d.getReDispatchCount()) + 1);
        d.setUpdateBy(operatorName);
        dispatchMapper.updateById(d);

        addLog(d.getDispatchId(), d.getOrderId(), "REASSIGN",
                buildAssignContent(old, d, operatorName), operatorName, operatorId);
        return DispatchVO.fromEntity(d);
    }

    private void fillAssignFields(ProductionDispatch d, DispatchAssignDTO dto, String operatorName, Long operatorId) {
        if (dto.getTeamId() != null) {
            SysDept dept = sysDeptMapper.selectById(dto.getTeamId());
            if (dept == null) throw new BusinessException("班组不存在");
            d.setTeamId(dto.getTeamId());
            d.setTeamName(dept.getDeptName());
        }
        if (dto.getEquipmentId() != null) {
            // 设备名查库
            String eqName = equipmentNameOf(dto.getEquipmentId());
            if (eqName == null) throw new BusinessException("设备不存在");
            d.setEquipmentId(dto.getEquipmentId());
            d.setEquipmentName(eqName);
        }
        d.setOperators(buildOperatorsJson(dto.getOperatorIds()));
        d.setAssignedBy(operatorId);
        d.setAssignedByName(operatorName);
        d.setAssignTime(LocalDateTime.now());
        d.setRemark(dto.getRemark() != null ? dto.getRemark() : d.getRemark());
    }

    private void validateAssign(DispatchAssignDTO dto) {
        if (dto.getTeamId() == null && dto.getEquipmentId() == null
                && (dto.getOperatorIds() == null || dto.getOperatorIds().isEmpty())) {
            throw new BusinessException("请至少指定班组/设备/执行人中的一项");
        }
    }

    // ==================== 退回/开始/完成 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long dispatchId, String reason, String operatorName, Long operatorId) {
        if (StringUtils.isBlank(reason)) throw new BusinessException("退回原因必填");
        ProductionDispatch d = dispatchMapper.selectById(dispatchId);
        if (d == null) throw new BusinessException("派工单不存在");
        if (DispatchStatusEnum.COMPLETED.getCode().equals(d.getStatus())) {
            throw new BusinessException("已完成派工单不可退回");
        }
        d.setStatus(DispatchStatusEnum.REJECTED.getCode());
        d.setRejectReason(reason.trim());
        d.setUpdateBy(operatorName);
        dispatchMapper.updateById(d);

        addLog(dispatchId, d.getOrderId(), "REJECT",
                "退回：" + reason.trim(), operatorName, operatorId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void start(Long dispatchId, String operatorName, Long operatorId) {
        ProductionDispatch d = dispatchMapper.selectById(dispatchId);
        if (d == null) throw new BusinessException("派工单不存在");
        if (!DispatchStatusEnum.ASSIGNED.getCode().equals(d.getStatus())
                && !DispatchStatusEnum.REJECTED.getCode().equals(d.getStatus())) {
            throw new BusinessException("当前状态不可开始（仅已派工/已退回可开始）");
        }
        d.setStatus(DispatchStatusEnum.EXECUTING.getCode());
        d.setUpdateBy(operatorName);
        dispatchMapper.updateById(d);
        // 联动工序执行：待执行→执行中（直接更新，不触发完整校验）
        syncExecutionStatus(d.getExecutionId(), 2);
        addLog(dispatchId, d.getOrderId(), "START", "工序开始执行", operatorName, operatorId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void complete(Long dispatchId, String operatorName, Long operatorId) {
        ProductionDispatch d = dispatchMapper.selectById(dispatchId);
        if (d == null) throw new BusinessException("派工单不存在");
        if (!DispatchStatusEnum.EXECUTING.getCode().equals(d.getStatus())
                && !DispatchStatusEnum.ASSIGNED.getCode().equals(d.getStatus())) {
            throw new BusinessException("当前状态不可完成");
        }
        d.setStatus(DispatchStatusEnum.COMPLETED.getCode());
        d.setUpdateBy(operatorName);
        dispatchMapper.updateById(d);
        syncExecutionStatus(d.getExecutionId(), 4);
        addLog(dispatchId, d.getOrderId(), "COMPLETE", "工序执行完成", operatorName, operatorId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncByExecution(Long executionId, int status) {
        if (executionId == null) return;
        ProductionDispatch d = dispatchMapper.selectOne(Wrappers.<ProductionDispatch>lambdaQuery()
                .eq(ProductionDispatch::getExecutionId, executionId).last("LIMIT 1"));
        if (d == null) return;
        // 执行模块回调：2=执行中（EXECUTING），4=已完成（COMPLETED）
        if (status == 2 && DispatchStatusEnum.ASSIGNED.getCode().equals(d.getStatus())) {
            d.setStatus(DispatchStatusEnum.EXECUTING.getCode());
            dispatchMapper.updateById(d);
        } else if (status == 4 && (DispatchStatusEnum.EXECUTING.getCode().equals(d.getStatus())
                || DispatchStatusEnum.ASSIGNED.getCode().equals(d.getStatus()))) {
            d.setStatus(DispatchStatusEnum.COMPLETED.getCode());
            dispatchMapper.updateById(d);
        }
    }

    /** 同步工序执行记录状态：2=执行中 4=已完成 */
    private void syncExecutionStatus(Long executionId, int status) {
        try {
            ProductionOperationExecution exec = executionMapper.selectById(executionId);
            if (exec == null) return;
            if (status == 2) {
                exec.setExecutionStatus(2);
                if (exec.getActualStartTime() == null) exec.setActualStartTime(LocalDateTime.now());
            } else if (status == 4) {
                exec.setExecutionStatus(4);
                if (exec.getActualEndTime() == null) exec.setActualEndTime(LocalDateTime.now());
            }
            executionMapper.updateById(exec);
        } catch (Exception e) {
            log.warn("同步工序执行状态失败: {}", e.getMessage());
        }
    }

    // ==================== 工单级责任 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateOrderTeam(Long orderId, Long teamId, Long leaderId, String operatorName) {
        com.jjx.production.domain.entity.ProductionOrder order = orderMapper.selectById(orderId);
        if (order == null) throw new BusinessException("工单不存在");
        com.jjx.production.domain.entity.ProductionOrder upd = new com.jjx.production.domain.entity.ProductionOrder();
        upd.setOrderId(orderId);
        if (teamId != null) {
            SysDept dept = sysDeptMapper.selectById(teamId);
            if (dept == null) throw new BusinessException("班组不存在");
            upd.setDispatchTeamId(teamId);
            upd.setDispatchTeamName(dept.getDeptName());
        }
        if (leaderId != null) {
            SysUser u = sysUserMapper.selectById(leaderId);
            if (u == null) throw new BusinessException("负责人不存在");
            upd.setDispatchLeaderId(leaderId);
            upd.setDispatchLeaderName(u.getNickName() != null && !u.getNickName().isBlank() ? u.getNickName() : u.getUserName());
        }
        orderMapper.updateById(upd);
        log.info("工单责任更新: orderId={}, teamId={}, leaderId={}, 操作人={}", orderId, teamId, leaderId, operatorName);
    }

    // ==================== 辅助 ====================

    private String processNameOf(Long processId) {
        if (processId == null) return null;
        try {
            List<String> names = jdbcTemplate.query(
                    "SELECT process_name FROM engineering_standard_process WHERE process_id = ?",
                    (rs, i) -> rs.getString("process_name"), processId);
            return names.isEmpty() ? null : names.get(0);
        } catch (Exception e) {
            return null;
        }
    }

    private String equipmentNameOf(Long equipmentId) {
        try {
            List<String> names = jdbcTemplate.query(
                    "SELECT equipment_name FROM production_equipment WHERE equipment_id = ?",
                    (rs, i) -> rs.getString("equipment_name"), equipmentId);
            return names.isEmpty() ? null : names.get(0);
        } catch (Exception e) {
            return null;
        }
    }

    private String orderNoOf(Long orderId) {
        try {
            List<String> nos = jdbcTemplate.query(
                    "SELECT order_no FROM production_order WHERE order_id = ?",
                    (rs, i) -> rs.getString("order_no"), orderId);
            return nos.isEmpty() ? null : nos.get(0);
        } catch (Exception e) {
            return null;
        }
    }

    /** 执行人 ID 列表 → JSON [{userId,userName}] */
    private String buildOperatorsJson(List<Long> operatorIds) {
        if (operatorIds == null || operatorIds.isEmpty()) return null;
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < operatorIds.size(); i++) {
            SysUser u = sysUserMapper.selectById(operatorIds.get(i));
            if (u == null) continue;
            String name = u.getNickName() != null && !u.getNickName().isBlank() ? u.getNickName() : u.getUserName();
            if (sb.length() > 1) sb.append(",");
            sb.append("{\"userId\":").append(u.getUserId()).append(",\"userName\":\"").append(name).append("\"}");
        }
        if (sb.length() == 1) return null;
        sb.append("]");
        return sb.toString();
    }

    /** 变更内容：指派=描述，改派=旧值→新值 */
    private String buildAssignContent(ProductionDispatch old, ProductionDispatch cur, String operatorName) {
        if (old == null || (old.getTeamName() == null && old.getEquipmentName() == null && old.getOperators() == null)) {
            return "指派：" + describe(cur) + "，主管：" + operatorName;
        }
        String oldDesc = describe(old);
        String newDesc = describe(cur);
        if (oldDesc.equals(newDesc)) return "重新指派（内容不变）";
        return "改派：" + oldDesc + " → " + newDesc;
    }

    private String describe(ProductionDispatch d) {
        List<String> parts = new ArrayList<>();
        if (d.getTeamName() != null) parts.add("班组=" + d.getTeamName());
        if (d.getEquipmentName() != null) parts.add("设备=" + d.getEquipmentName());
        if (d.getOperators() != null && !d.getOperators().isBlank()) {
            try {
                var arr = new com.fasterxml.jackson.databind.ObjectMapper().readTree(d.getOperators());
                List<String> names = new ArrayList<>();
                arr.forEach(n -> names.add(n.path("userName").asText("")));
                if (!names.isEmpty()) parts.add("执行人=" + String.join("、", names));
            } catch (Exception ignored) {}
        }
        return parts.isEmpty() ? "未指定" : String.join("，", parts);
    }

    private void addLog(Long dispatchId, Long orderId, String action, String content,
                        String operatorName, Long operatorId) {
        ProductionDispatchLog log_ = new ProductionDispatchLog();
        log_.setDispatchId(dispatchId);
        log_.setOrderId(orderId);
        log_.setAction(action);
        log_.setContent(content);
        log_.setOperatorId(operatorId);
        log_.setOperatorName(operatorName);
        log_.setCreateTime(LocalDateTime.now());
        dispatchLogMapper.insert(log_);
    }
}
