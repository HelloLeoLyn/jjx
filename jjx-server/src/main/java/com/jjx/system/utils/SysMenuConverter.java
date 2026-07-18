package com.jjx.system.utils;

import com.jjx.system.domain.dto.SysMenuDTO;
import com.jjx.system.domain.entity.SysMenu;
import com.jjx.system.domain.vo.SysMenuVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface SysMenuConverter {
    // 单对象转换
    @Mapping(target = "children", ignore = true)
    SysMenuVO toVO(SysMenu menu);

    // 集合转换
    List<SysMenuVO> toVOList(List<SysMenu> menus);

    @Mapping(target = "updateBy", ignore = true)
    @Mapping(target = "params", ignore = true)
    @Mapping(target = "createBy", ignore = true)
    SysMenu toEntity(SysMenuVO vo);

    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "updateBy", ignore = true)
    @Mapping(target = "query", ignore = true)
    @Mapping(target = "params", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "createBy", ignore = true)
    SysMenu toEntity(SysMenuDTO dto);
}
