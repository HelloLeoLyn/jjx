package com.jjx.system.converter;

import com.jjx.system.domain.entity.SysUser;
import com.jjx.system.domain.vo.LoginUser;
import com.jjx.system.domain.vo.SysUserVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface SysUserConverter {
    @Mapping(target = "tokenName", ignore = true)
    @Mapping(target = "token", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "roles", ignore = true)
    @Mapping(target = "realName", source = "nickName")
    @Mapping(target = "permissions", ignore = true)
    @Mapping(target = "needChangePassword", ignore = true)
    @Mapping(target = "loginTime", ignore = true)
    @Mapping(target = "isLogin", ignore = true)
    LoginUser toVO(SysUser user);

    @Mapping(target = "roleIds", ignore = true)
    SysUserVO toUserVO(SysUser user);

    List<SysUserVO> toVOList(List<SysUser> list);
}
