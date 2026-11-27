package com.jjx.engineering.printing.screen;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.jjx.common.exception.BusinessException;
import java.math.BigDecimal;

public record ScreenCommands(String requestId, Integer expectedRevision, String frameNo, String frameType,
        String location, Integer mesh, BigDecimal tensionNcm, String material, Boolean qualified,
        Long frameId, Long stretchId, Long filmId, Long versionId, String plateNo, String position,
        String inspection, String reason, String remark) {
    @JsonAnySetter
    public void rejectUnknown(String name, Object value) {
        throw new IllegalArgumentException("未知命令字段 " + name);
    }

    static void require(boolean valid, String message) {
        if (!valid) throw new BusinessException(422, message);
    }

    static void required(String value, int max, String field) {
        require(value != null && !value.isBlank() && value.length() <= max, field + "为空或超长");
    }

    void validate() {
        required(requestId, 64, "requestId");
        require(expectedRevision != null && expectedRevision >= 0, "需要非负 expectedRevision");
        require(location == null || location.length() <= 200, "位置超长");
        require(frameType == null || frameType.length() <= 30, "型号超长");
        require(material == null || material.length() <= 200, "材料超长");
        require(reason == null || reason.length() <= 500, "原因超长");
        require(remark == null || remark.length() <= 500, "备注不能超过500字");
    }
}
