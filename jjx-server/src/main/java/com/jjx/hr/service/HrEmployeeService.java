package com.jjx.hr.service;

import com.jjx.common.core.page.PageResult;
import com.jjx.hr.domain.dto.HrCreateUserDTO;
import com.jjx.hr.domain.dto.HrEmployeeImportDTO;
import com.jjx.hr.domain.dto.HrEmployeeQueryDTO;
import com.jjx.hr.domain.entity.HrEmployee;
import com.jjx.hr.domain.vo.HrAccountVO;
import com.jjx.hr.domain.vo.HrEmployeeVO;
import com.jjx.hr.domain.vo.HrImportResultVO;
import com.jjx.hr.domain.vo.HrRoleOptionVO;

import java.util.List;

/** 员工档案服务（人事管理 P0）。 */
public interface HrEmployeeService {

    PageResult<HrEmployeeVO> page(HrEmployeeQueryDTO query);

    List<HrEmployeeVO> list(HrEmployeeQueryDTO query);

    HrEmployeeVO detail(Long empId);

    /** 预览下一个工号 */
    String nextEmpNo();

    Long create(HrEmployee entity);

    void update(HrEmployee entity);

    void delete(Long empId);

    HrImportResultVO importEmployees(List<HrEmployeeImportDTO> rows, String operName);

    /** 校验账号是否已被其他员工占用 */
    void assertUserAvailable(Long userId, Long excludeEmpId);

    /** 根据员工档案生成系统账号（一键生成），并回写 hr_employee.user_id */
    HrAccountVO createUserFromEmployee(Long empId, HrCreateUserDTO dto);

    /** 生成账号时可分配的角色选项 */
    List<HrRoleOptionVO> roleOptions();
}
