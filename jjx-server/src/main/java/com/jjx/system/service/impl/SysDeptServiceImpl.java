package com.jjx.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jjx.system.converter.SysDeptConverter;
import com.jjx.system.domain.dto.SysDeptDTO;
import com.jjx.system.domain.entity.SysDept;
import com.jjx.system.mapper.SysDeptMapper;
import com.jjx.system.service.ISysDeptService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 部门管理 服务实现
 */
@Service
@RequiredArgsConstructor
public class SysDeptServiceImpl extends ServiceImpl<SysDeptMapper, SysDept> implements ISysDeptService {

    private final SysDeptMapper deptMapper;
    private final SysDeptConverter deptConverter;

    @Override
    public List<SysDept> selectDeptList(SysDept dept) {
        LambdaQueryWrapper<SysDept> queryWrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(dept.getDeptName())) {
            queryWrapper.like(SysDept::getDeptName, dept.getDeptName());
        }
        if (StringUtils.isNotBlank(dept.getStatus())) {
            queryWrapper.eq(SysDept::getStatus, dept.getStatus());
        }
        queryWrapper.orderByAsc(SysDept::getParentId, SysDept::getOrderNum);
        return deptMapper.selectList(queryWrapper);
    }


    @Override
    public SysDept selectDeptById(Long deptId) {
        return deptMapper.selectById(deptId);
    }

    @Override
    public int selectNormalChildrenDeptById(Long deptId) {
        LambdaQueryWrapper<SysDept> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysDept::getStatus, "0");
        queryWrapper.eq(SysDept::getParentId, deptId);
        return deptMapper.selectCount(queryWrapper).intValue();
    }

    @Override
    public boolean hasChildByDeptId(Long deptId) {
        LambdaQueryWrapper<SysDept> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysDept::getParentId, deptId);
        return deptMapper.selectCount(queryWrapper) > 0;
    }

    @Override
    public boolean checkDeptExistUser(Long deptId) {
        // 这里需要检查部门下是否有用户，暂时返回false
        return false;
    }

    @Override
    public boolean checkDeptNameUnique(SysDept dept) {
        Long deptId = dept.getId() == null ? -1L : dept.getId();
        LambdaQueryWrapper<SysDept> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysDept::getDeptName, dept.getDeptName());
        queryWrapper.eq(SysDept::getParentId, dept.getParentId());
        SysDept info = deptMapper.selectOne(queryWrapper);
        if (info != null && !info.getId().equals(deptId)) {
            return false;
        }
        return true;
    }

    @Override
    public void checkDeptDataScope(Long deptId) {
        // 数据权限检查，暂时不实现
    }

    @Override
    public boolean insertDept(SysDeptDTO dto) {
        SysDept dept = deptConverter.toEntity(dto);
        dept.setAncestors("0");
        if (dept.getParentId() != null && dept.getParentId() != 0L) {
            SysDept parentDept = deptMapper.selectById(dept.getParentId());
            if (parentDept != null) {
                dept.setAncestors(parentDept.getAncestors() + "," + parentDept.getId());
            }
        }
        return save(dept);
    }

    @Override
    public boolean updateDept(SysDeptDTO dto) {
        SysDept dept = deptConverter.toEntity(dto);
        // 更新部门信息
        return updateById(dept);
    }

    @Override
    public boolean deleteDeptById(Long deptId) {
        // 检查是否有子部门
        if (hasChildByDeptId(deptId)) {
            // 有子部门，不能删除
            return false;
        }
        // 检查部门下是否有用户
        if (checkDeptExistUser(deptId)) {
            // 有用户，不能删除
            return false;
        }
        return removeById(deptId);
    }
}
