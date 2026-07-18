package com.jjx.system.controller.system;

import com.jjx.common.core.result.Result;
import com.jjx.common.exception.BusinessException;
import com.jjx.common.tree.TreeUtils;
import com.jjx.framework.common.controller.BaseController;
import com.jjx.system.annotation.Log;
import com.jjx.system.annotation.BusinessType;
import com.jjx.system.converter.SysDeptConverter;
import com.jjx.system.domain.dto.SysDeptDTO;
import com.jjx.system.domain.entity.SysDept;
import com.jjx.system.domain.vo.DeptVO;
import com.jjx.system.service.ISysDeptService;
import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 部门信息
 */
@RestController
@RequestMapping("/system/dept")
@RequiredArgsConstructor
public class SysDeptController extends BaseController {

    private final ISysDeptService deptService;
    private final SysDeptConverter deptConverter;
    /**
     * 获取部门列表
     */
    @GetMapping("/list")
    public Result<List<DeptVO>> list(SysDept dept) {
        List<SysDept> list = deptService.selectDeptList(dept);
        List<DeptVO> voList = deptConverter.toVOList(list);
        return Result.success(voList);
    }

    /**
     * 查询部门列表（排除节点）
     */
    @GetMapping("/list/exclude/{deptId}")
    public Result<List<DeptVO>> excludeChild(@PathVariable Long deptId) {
        List<SysDept> list = deptService.selectDeptList(new SysDept());
        List<DeptVO> voList = deptConverter.toVOList(list);
        return Result.success(voList);
    }

    /**
     * 根据部门编号获取详细信息
     */
    @GetMapping(value = "/{deptId}")
    public Result<SysDept> getInfo(@PathVariable Long deptId) {
        deptService.checkDeptDataScope(deptId);
        return Result.success(deptService.selectDeptById(deptId));
    }

    /**
     * 获取部门下拉树列表
     */
    @GetMapping("/treeselect")
    public Result<List<DeptVO>> treeSelect(SysDept dept) {
        List<SysDept> list = deptService.selectDeptList(dept);
        List<DeptVO> deptList;
        deptList = deptConverter.toVOList(list);
        List<DeptVO> data = TreeUtils.build(deptList);
        return Result.success(data);
    }


    /**
     * 新增部门
     */
    @PostMapping
    @Log(module = "部门管理", businessType = BusinessType.INSERT)
    @SaCheckPermission("system:dept:add")
    public Result<Void> add(@Validated @RequestBody SysDeptDTO deptDTO) {
        SysDept dept = new SysDept();
        dept.setParentId(deptDTO.getParentId());
        dept.setDeptName(deptDTO.getDeptName());
        dept.setOrderNum(deptDTO.getOrderNum());
        dept.setLeader(deptDTO.getLeader());
        dept.setPhone(deptDTO.getPhone());
        dept.setEmail(deptDTO.getEmail());
        dept.setStatus(deptDTO.getStatus());
        if (!deptService.checkDeptNameUnique(dept)) {
            throw new BusinessException("新增部门'" + dept.getDeptName() + "'失败，部门名称已存在");
        }
        return toAjax(deptService.insertDept(deptDTO));
    }

    /**
     * 修改部门
     */
    @PutMapping
    @Log(module = "部门管理", businessType = BusinessType.UPDATE)
    @SaCheckPermission("system:dept:edit")
    public Result<Void> edit(@Validated @RequestBody SysDeptDTO deptDTO) {
        SysDept dept = new SysDept();
        dept.setId(deptDTO.getId());
        dept.setParentId(deptDTO.getParentId());
        dept.setDeptName(deptDTO.getDeptName());
        dept.setOrderNum(deptDTO.getOrderNum());
        dept.setLeader(deptDTO.getLeader());
        dept.setPhone(deptDTO.getPhone());
        dept.setEmail(deptDTO.getEmail());
        dept.setStatus(deptDTO.getStatus());
        Long deptId = dept.getId();
        deptService.checkDeptDataScope(deptId);
        if (!deptService.checkDeptNameUnique(dept)) {
            throw new BusinessException("修改部门'" + dept.getDeptName() + "'失败，部门名称已存在");
        } else if (dept.getParentId().equals(deptId)) {
            throw new BusinessException("修改部门'" + dept.getDeptName() + "'失败，上级部门不能是自己");
        } else if (dept.getStatus().equals("1") && deptService.selectNormalChildrenDeptById(deptId) > 0) {
            throw new BusinessException("该部门包含未停用的子部门！");
        }
        return toAjax(deptService.updateDept(deptDTO));
    }

    /**
     * 删除部门
     */
    @DeleteMapping("/{deptId}")
    @Log(module = "部门管理", businessType = BusinessType.DELETE)
    @SaCheckPermission("system:dept:delete")
    public Result<Void> remove(@PathVariable Long deptId) {
        if (deptService.hasChildByDeptId(deptId)) {
            throw new BusinessException("存在下级部门,不允许删除");
        }
        if (deptService.checkDeptExistUser(deptId)) {
            throw new BusinessException("部门存在用户,不允许删除");
        }
        deptService.checkDeptDataScope(deptId);
        return toAjax(deptService.deleteDeptById(deptId));
    }
}
