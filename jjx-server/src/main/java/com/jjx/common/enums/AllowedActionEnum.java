package com.jjx.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/**
 * 允许动作（Allowed Actions）枚举 —— dev-20260923-039「状态×动作唯一出处」的**动作字典**。
 *
 * <p>唯一出处原则（见 jjx-docs/design/quality-disposition-rework-master-dev-20260923-045.md §2.3）：
 * 动作码、中文名、所属域、所需权限点**只在本枚举定义一次**；
 * 由 {@code AllowedActionResolver} 按「对象 × 状态 × 链路有效性」算出 allowedActions，
 * 接口下发、服务端守卫、前端渲染**三处共用同一份结果**，页面不再各写状态条件。</p>
 */
@Getter
@AllArgsConstructor
public enum AllowedActionEnum {

    // ==================== 检验批（quality_lot） ====================
    LOT_INSPECT("LOT_INSPECT", "检验录入", Domain.QUALITY, "quality:lot:inspect"),
    LOT_JUDGE("LOT_JUDGE", "判定", Domain.QUALITY, "quality:lot:judge"),
    LOT_REINSPECT("LOT_REINSPECT", "复检", Domain.QUALITY, "quality:lot:judge"),
    LOT_REOPEN("LOT_REOPEN", "重开", Domain.QUALITY, "quality:lot:judge"),

    // ==================== 不良单（quality_ncr） ====================
    NCR_DISPOSE("NCR_DISPOSE", "不良处置", Domain.QUALITY, "quality:ncr:dispose"),
    NCR_VOID_SUPERSEDED("NCR_VOID_SUPERSEDED", "随批作废", Domain.QUALITY, "quality:ncr:void-superseded"),

    // ==================== 处置动作（quality_ncr_action） ====================
    NCR_REVOKE("NCR_REVOKE", "处置撤销", Domain.QUALITY, "quality:ncr:revoke"),
    NCR_REWORK_COMPLETE("NCR_REWORK_COMPLETE", "推进返工闭环", Domain.QUALITY, "quality:ncr:dispose"),
    /** 发起类动作放在质量页、权限点归库存域 —— 方案 §4.2 明确授权的唯一例外 */
    NCR_REWORK_SUPPLEMENT("NCR_REWORK_SUPPLEMENT", "返工补料", Domain.INVENTORY, "inventory:outbound:rework-supplement"),
    NCR_REWORK_RETURN("NCR_REWORK_RETURN", "返工退料", Domain.INVENTORY, "inventory:outbound:rework-return"),

    // ==================== 生产（工单 / 工序，后续切片接入） ====================
    TASK_FLOW("FLOW", "流转记录", Domain.PRODUCTION, "production:task:list"),
    TASK_ASSIGN("ASSIGN", "分配", Domain.PRODUCTION, "production:task:assign"),
    TASK_RETURN("RETURN", "退回", Domain.PRODUCTION, "production:task:return"),
    TASK_RECALL("RECALL", "收回", Domain.PRODUCTION, "production:task:recall"),
    ORDER_SUPPLEMENT("ORDER_SUPPLEMENT", "补产（补报）", Domain.PRODUCTION, "production:work-report:add"),
    ORDER_COMPLETE("ORDER_COMPLETE", "完成工单", Domain.PRODUCTION, "production:operation-execution:edit"),
    ORDER_INBOUND_RETRY("ORDER_INBOUND_RETRY", "重试完工入库", Domain.PRODUCTION, "production:operation-execution:edit"),
    REWORK_ASSIGN("REWORK_ASSIGN", "派工", Domain.PRODUCTION, "production:task:assign"),
    REWORK_START("REWORK_START", "开工", Domain.PRODUCTION, "production:operation-execution:edit"),
    REWORK_REPORT("REWORK_REPORT", "报工/补报", Domain.PRODUCTION, "production:work-report:add"),
    REWORK_APPROVE("REWORK_APPROVE", "报工审批", Domain.PRODUCTION, "production:work-report:approve");

    /** 所属域（用于「动作只放在所属域页面」与权限点归属核对） */
    public enum Domain {
        QUALITY, PRODUCTION, INVENTORY
    }

    private final String code;
    private final String label;
    private final Domain domain;
    /** 该动作需要的权限点（与 sys_menu.perms 一致） */
    private final String permission;

    public static AllowedActionEnum byCode(String code) {
        for (AllowedActionEnum value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }

    /** 动作码列表（下发给前端用） */
    public static List<String> codesOf(List<AllowedActionEnum> actions) {
        return actions == null ? List.of() : actions.stream().map(AllowedActionEnum::getCode).toList();
    }
}
