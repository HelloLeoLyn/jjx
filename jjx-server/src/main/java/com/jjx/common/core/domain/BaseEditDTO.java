package com.jjx.common.core.domain;

import java.util.List;

import lombok.Data;
import java.util.Map;
@Data
public class BaseEditDTO {

    /** 创建者 */
    private String createBy;

    /** 创建时间 */
    private String createTime;

    /** 更新者 */
    private String updateBy;

    /** 更新时间 */
    private String updateTime;

    /** 备注 */
    private List<Map<String, Object>> modifiedFields;

}
