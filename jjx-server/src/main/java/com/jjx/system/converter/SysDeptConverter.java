package com.jjx.system.converter;

import com.jjx.system.domain.dto.SysDeptDTO;
import com.jjx.system.domain.entity.SysDept;
import com.jjx.system.domain.vo.DeptVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface SysDeptConverter {
    DeptVO toVO(SysDept entity);

    List<DeptVO> toVOList(List<SysDept> list);

    @Mapping(target = "createBy", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "params", ignore = true)
    @Mapping(target = "updateBy", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "ancestors", ignore = true)
    @Mapping(target = "children", ignore = true)
    @Mapping(target = "delFlag", ignore = true)
    @Mapping(target = "parentName", ignore = true)
    @Mapping(target = "remark", ignore = true)
    SysDept toEntity(SysDeptDTO dto);
}
