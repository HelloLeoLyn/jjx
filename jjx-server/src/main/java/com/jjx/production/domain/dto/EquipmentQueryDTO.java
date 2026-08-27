package com.jjx.production.domain.dto;
import com.jjx.common.core.page.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;
@Data
@EqualsAndHashCode(callSuper = true)
public class EquipmentQueryDTO extends PageQuery {
    private String equipmentNo;
    private String equipmentName;
    private String equipmentType;
    private Integer status;
}
