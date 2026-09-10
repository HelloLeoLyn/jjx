package com.jjx.hr.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.jjx.common.core.page.PageResult;
import com.jjx.common.core.result.Result;
import com.jjx.common.utils.ExcelUtils;
import com.jjx.framework.common.controller.BaseController;
import com.jjx.hr.domain.dto.HrCreateUserDTO;
import com.jjx.hr.domain.dto.HrEmployeeImportDTO;
import com.jjx.hr.domain.dto.HrEmployeeQueryDTO;
import com.jjx.hr.domain.entity.HrEmployee;
import com.jjx.hr.domain.vo.HrAccountVO;
import com.jjx.hr.domain.vo.HrEmployeeVO;
import com.jjx.hr.domain.vo.HrImportResultVO;
import com.jjx.hr.domain.vo.HrRoleOptionVO;
import com.jjx.hr.service.HrEmployeeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

/**
 * 人事管理 - 员工档案控制器（P0，2026-09-10）。
 */
@Tag(name = "人事管理 - 员工档案")
@RestController
@RequestMapping("/hrs/employees")
@RequiredArgsConstructor
public class HrEmployeeController extends BaseController {

    private final HrEmployeeService employeeService;

    @Operation(summary = "员工档案分页查询")
    @SaCheckPermission("hr:employee:view")
    @GetMapping("/page")
    public Result<PageResult<HrEmployeeVO>> page(HrEmployeeQueryDTO query) {
        return Result.success(employeeService.page(query));
    }

    @Operation(summary = "预览下一个工号")
    @SaCheckPermission("hr:employee:add")
    @GetMapping("/nextEmpNo")
    public Result<String> nextEmpNo() {
        return Result.success(employeeService.nextEmpNo());
    }

    @Operation(summary = "员工档案详情")
    @SaCheckPermission("hr:employee:view")
    @GetMapping("/{empId}")
    public Result<HrEmployeeVO> detail(@PathVariable Long empId) {
        return Result.success(employeeService.detail(empId));
    }

    @Operation(summary = "新增员工")
    @SaCheckPermission("hr:employee:add")
    @PostMapping
    public Result<Long> create(@Validated @RequestBody HrEmployee entity) {
        return Result.success(employeeService.create(entity));
    }

    @Operation(summary = "修改员工")
    @SaCheckPermission("hr:employee:edit")
    @PutMapping
    public Result<Void> update(@Validated @RequestBody HrEmployee entity) {
        employeeService.update(entity);
        return Result.success();
    }

    @Operation(summary = "删除员工")
    @SaCheckPermission("hr:employee:delete")
    @DeleteMapping("/{empId}")
    public Result<Void> delete(@PathVariable Long empId) {
        employeeService.delete(empId);
        return Result.success();
    }

    @Operation(summary = "生成账号时可分配的角色下拉")
    @SaCheckPermission("hr:employee:view")
    @GetMapping("/role-options")
    public Result<List<HrRoleOptionVO>> roleOptions() {
        return Result.success(employeeService.roleOptions());
    }

    @Operation(summary = "根据员工档案生成系统账号（一键生成）")
    @SaCheckPermission("hr:employee:edit")
    @PostMapping("/{empId}/create-user")
    public Result<HrAccountVO> createUser(@PathVariable Long empId,
                                          @RequestBody(required = false) HrCreateUserDTO dto) {
        return Result.success(employeeService.createUserFromEmployee(empId, dto));
    }

    @Operation(summary = "恢复员工关联的已删除账号（逻辑删除复活）")
    @SaCheckPermission("hr:employee:edit")
    @PostMapping("/{empId}/revive-user")
    public Result<HrAccountVO> reviveUser(@PathVariable Long empId) {
        return Result.success(employeeService.reviveUser(empId));
    }

    @Operation(summary = "下载员工导入模板")
    @SaCheckPermission("hr:employee:import")
    @GetMapping("/importTemplate")
    public void importTemplate(HttpServletResponse response) {
        ExcelUtils.downloadTemplate(response, HrEmployeeImportDTO.class, "员工档案导入模板");
    }

    @Operation(summary = "导入员工档案")
    @SaCheckPermission("hr:employee:import")
    @PostMapping("/import")
    public Result<HrImportResultVO> importEmployees(MultipartFile file) throws Exception {
        List<HrEmployeeImportDTO> rows = ExcelUtils.importExcel(file, HrEmployeeImportDTO.class);
        return Result.success(employeeService.importEmployees(rows, getUsername()));
    }

    @Operation(summary = "导出员工档案")
    @SaCheckPermission("hr:employee:export")
    @GetMapping("/export")
    public void export(HrEmployeeQueryDTO query, HttpServletResponse response) {
        List<HrEmployeeVO> list = employeeService.list(query);
        List<HrEmployeeImportDTO> rows = new ArrayList<>();
        for (HrEmployeeVO vo : list) {
            HrEmployeeImportDTO d = new HrEmployeeImportDTO();
            d.setEmpNo(vo.getEmpNo());
            d.setName(vo.getName());
            d.setSex(vo.getSex() == null ? null : (vo.getSex() == 1 ? "男" : "女"));
            d.setDeptName(vo.getDeptName());
            d.setPosition(vo.getPosition());
            d.setPhone(vo.getPhone());
            d.setEmail(vo.getEmail());
            d.setHireDate(vo.getHireDate() == null ? null : vo.getHireDate().toString());
            d.setIdCardNo(vo.getIdCardNo());
            d.setIdCardAddress(vo.getIdCardAddress());
            d.setCurrentAddress(vo.getCurrentAddress());
            d.setEducation(vo.getEducation());
            d.setMajor(vo.getMajor());
            d.setResume(vo.getResume());
            d.setRemark(vo.getRemark());
            rows.add(d);
        }
        ExcelUtils.export(response, rows, HrEmployeeImportDTO.class, "员工档案");
    }
}
