package com.jjx.system.converter;

import com.jjx.system.domain.dto.SysDeptDTO;
import com.jjx.system.domain.entity.SysDept;
import com.jjx.system.domain.vo.DeptVO;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface SysDeptConverter {
    DeptVO toVO(SysDept entity);

    List<DeptVO> toVOList(List<SysDept> list);

    SysDept toEntity(SysDeptDTO dto);
}
