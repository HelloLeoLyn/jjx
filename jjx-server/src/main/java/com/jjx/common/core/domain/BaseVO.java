package com.jjx.common.core.domain;

import lombok.Data;

import java.time.LocalDateTime;
@Data
public class BaseVO {
    /** 创建者 */
    private String createBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新者 */
    private String updateBy;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
