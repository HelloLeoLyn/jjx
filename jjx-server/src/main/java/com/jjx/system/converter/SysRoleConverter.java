package com.jjx.system.converter;

import com.jjx.system.domain.dto.SysRoleDTO;
import com.jjx.system.domain.entity.SysRole;
import com.jjx.system.domain.vo.SysRoleVO;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import java.util.List;

/**
 * 角色对象转换器
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface SysRoleConverter {

    SysRole toEntity(SysRoleDTO dto);

    SysRoleDTO toDTO(SysRole entity);

    SysRoleVO toVO(SysRole entity);

    List<SysRoleVO> toVOList(List<SysRole> entities);
}
