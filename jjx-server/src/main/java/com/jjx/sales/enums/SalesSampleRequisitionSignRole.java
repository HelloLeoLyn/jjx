package com.jjx.sales.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 样品需求单签字位（dev-20261010-028）
 * 权限点由「角色管理」分配；三个位子的权限点各自独立。
 */
@Getter
@AllArgsConstructor
public enum SalesSampleRequisitionSignRole {

    SALES("SALES", "业务", "sales:sample:reqsign:sales"),
    APPROVE("APPROVE", "核准", "sales:sample:reqsign:approve"),
    DEPT("DEPT", "部门主管", "sales:sample:reqsign:dept");

    private final String code;
    private final String label;
    /** 该签字位对应的权限点 */
    private final String permission;

    public static SalesSampleRequisitionSignRole of(String code) {
        if (code != null) {
            for (SalesSampleRequisitionSignRole role : values()) {
                if (role.code.equalsIgnoreCase(code)) {
                    return role;
                }
            }
        }
        return null;
    }
}
