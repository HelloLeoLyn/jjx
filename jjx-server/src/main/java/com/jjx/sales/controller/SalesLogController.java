package com.jjx.sales.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jjx.common.core.result.Result;
import com.jjx.framework.common.controller.BaseController;
import com.jjx.system.domain.entity.SysOperLog;
import com.jjx.system.mapper.SysOperLogMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 销售订单操作流水（任务1326，2026-09-03）
 * 补真端点：sys_oper_log 按 biz_type='order' + biz_id=orderId 查询（原 /sales/logs 家族为死 API，
 * OrderDetailDrawer 操作历史一直静默失败）
 */
@Tag(name = "销售订单流水")
@RestController
@RequestMapping("/sales/logs")
@RequiredArgsConstructor
public class SalesLogController extends BaseController {

    private final SysOperLogMapper operLogMapper;

    private static final Map<Integer, String> BIZ_TYPE_LABEL = new LinkedHashMap<>();

    static {
        BIZ_TYPE_LABEL.put(1, "新增");
        BIZ_TYPE_LABEL.put(2, "修改");
        BIZ_TYPE_LABEL.put(3, "删除");
        BIZ_TYPE_LABEL.put(4, "审核");
        BIZ_TYPE_LABEL.put(5, "导出");
        BIZ_TYPE_LABEL.put(6, "导入");
        BIZ_TYPE_LABEL.put(9, "其它");
    }

    @Operation(summary = "按订单ID查操作流水")
    @SaCheckPermission("sales:order:view")
    @GetMapping("/order/{orderId}")
    public Result<List<Map<String, Object>>> byOrder(@PathVariable Long orderId) {
        List<SysOperLog> logs = operLogMapper.selectList(new LambdaQueryWrapper<SysOperLog>()
                .eq(SysOperLog::getBizType, "order")
                .eq(SysOperLog::getBizId, String.valueOf(orderId))
                .orderByDesc(SysOperLog::getId));
        List<Map<String, Object>> result = new ArrayList<>();
        for (SysOperLog l : logs) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("logId", l.getId());
            m.put("orderId", orderId);
            // 操作类型：businessType 数字 → 名称（新增/修改/删除/审核…）
            m.put("operationType", l.getBusinessType() == null ? null : String.valueOf(l.getBusinessType()));
            m.put("operationTypeName", l.getBusinessType() == null ? null : BIZ_TYPE_LABEL.getOrDefault(l.getBusinessType(), "其它"));
            // 描述优先 detail（@Log SpEL 描述/变更 JSON），其次模块+URL
            String desc = (l.getDetail() != null && !l.getDetail().isBlank()) ? l.getDetail()
                    : (l.getModule() == null ? "" : l.getModule()) + " " + (l.getOperUrl() == null ? "" : l.getOperUrl());
            m.put("operationDescription", desc.trim().isEmpty() ? null : desc.trim());
            m.put("operatorId", l.getUserId());
            m.put("operatorName", l.getRealName() != null && !l.getRealName().isBlank() ? l.getRealName() : l.getUsername());
            m.put("operationTime", l.getCreateTime());
            // status：1 成功 / 0 失败（与前端 operationResult===1 判断对齐）
            m.put("operationResult", l.getStatus() == null ? 1 : l.getStatus());
            m.put("operationResultName", (l.getStatus() == null || l.getStatus() == 1) ? "成功" : "失败");
            m.put("remark", l.getErrorMsg());
            result.add(m);
        }
        return Result.success(result);
    }

    @Operation(summary = "按订单号查操作流水")
    @SaCheckPermission("sales:order:view")
    @GetMapping("/orderNo/{orderNo}")
    public Result<List<Map<String, Object>>> byOrderNo(@PathVariable String orderNo) {
        List<SysOperLog> logs = operLogMapper.selectList(new LambdaQueryWrapper<SysOperLog>()
                .eq(SysOperLog::getBizType, "order")
                .eq(SysOperLog::getOperParam, orderNo)
                .or(o -> o.like(SysOperLog::getDetail, orderNo))
                .orderByDesc(SysOperLog::getId));
        List<Map<String, Object>> result = new ArrayList<>();
        for (SysOperLog l : logs) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("logId", l.getId());
            m.put("operationType", l.getBusinessType() == null ? null : String.valueOf(l.getBusinessType()));
            m.put("operationTypeName", l.getBusinessType() == null ? null : BIZ_TYPE_LABEL.getOrDefault(l.getBusinessType(), "其它"));
            String desc = (l.getDetail() != null && !l.getDetail().isBlank()) ? l.getDetail()
                    : (l.getModule() == null ? "" : l.getModule()) + " " + (l.getOperUrl() == null ? "" : l.getOperUrl());
            m.put("operationDescription", desc.trim().isEmpty() ? null : desc.trim());
            m.put("operatorId", l.getUserId());
            m.put("operatorName", l.getRealName() != null && !l.getRealName().isBlank() ? l.getRealName() : l.getUsername());
            m.put("operationTime", l.getCreateTime());
            m.put("operationResult", l.getStatus() == null ? 1 : l.getStatus());
            m.put("remark", l.getErrorMsg());
            result.add(m);
        }
        return Result.success(result);
    }
}
