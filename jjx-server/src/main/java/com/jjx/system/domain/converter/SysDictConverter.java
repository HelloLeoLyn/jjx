package com.jjx.system.domain.converter;

import com.jjx.system.domain.dto.SysDictDTO;
import com.jjx.system.domain.entity.SysDict;
import com.jjx.system.domain.vo.SysDictVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface SysDictConverter {

    @Mapping(target = "items", ignore = true)
    SysDictVO toVO(SysDict entity);

    List<SysDictVO> toVOList(List<SysDict> list);

    @Mapping(target = "dictId", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    SysDict toEntity(SysDictDTO dto);
}
