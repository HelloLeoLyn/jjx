package com.jjx.product.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 工艺路线验证结果VO
 */
@Data
@Builder
public class EngineeringRoutingValidationVO {
    
    /**
     * 是否有效
     */
    private boolean valid;
    
    /**
     * 验证状态：SUCCESS/WARNING/ERROR
     */
    private String status;
    
    /**
     * 错误列表
     */
    @Builder.Default
    private List<ValidationError> errors = new ArrayList<>();
    
    /**
     * 警告列表
     */
    @Builder.Default
    private List<ValidationWarning> warnings = new ArrayList<>();
    
    /**
     * 建议列表
     */
    @Builder.Default
    private List<String> suggestions = new ArrayList<>();
    
    /**
     * 验证时间
     */
    private String validateTime;
    
    /**
     * 验证耗时(ms)
     */
    private long duration;
    
    /**
     * 验证错误详情
     */
    @Data
    @Builder
    public static class ValidationError {
        private String code;
        private String message;
        private String field;
        private String suggestion;
    }
    
    /**
     * 验证警告详情
     */
    @Data
    @Builder
    public static class ValidationWarning {
        private String code;
        private String message;
        private String field;
        private String suggestion;
    }
}