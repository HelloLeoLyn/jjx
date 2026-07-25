package com.jjx.system.converter;

import com.jjx.system.domain.dto.SysRoleDTO;
import com.jjx.system.domain.entity.SysRole;
import com.jjx.system.domain.vo.SysRoleVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import java.util.List;

/**
 * 角色对象转换器
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface SysRoleConverter {

    @Mapping(target = "createBy", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateBy", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "params", ignore = true)
    @Mapping(target = "delFlag", ignore = true)
    @Mapping(target = "flag", ignore = true)
    @Mapping(target = "menus", ignore = true)
    SysRole toEntity(SysRoleDTO dto);

    SysRoleDTO toDTO(SysRole entity);

    SysRoleVO toVO(SysRole entity);

    List<SysRoleVO> toVOList(List<SysRole> entities);
}
