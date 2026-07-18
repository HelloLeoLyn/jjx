package com.jjx.system.domain.converter;

import com.jjx.system.domain.dto.SysDictItemDTO;
import com.jjx.system.domain.entity.SysDictItem;
import com.jjx.system.domain.vo.SysDictItemVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface SysDictItemConverter {

    SysDictItemVO toVO(SysDictItem entity);

    List<SysDictItemVO> toVOList(List<SysDictItem> list);

    @Mapping(target = "itemId", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    SysDictItem toEntity(SysDictItemDTO dto);
}
