package com.jjx.hr.domain.dto;

import com.jjx.common.annotation.ExcelColumn;
import lombok.Data;

/**
 * 员工档案导入 DTO（人事管理 P0）。
 *
 * <p>导入策略：工号为空时自动生成；部门文本经 hr_dept_mapping 映射到 sys_dept；
 * 身份证号导入后立即 AES 加密存储；行级错误不阻断整批（收集后返回）。</p>
 */
@Data
public class HrEmployeeImportDTO {

    @ExcelColumn(value = "工号", order = 1, comment = "可空；空则按 JJX+4位流水自动生成")
    private String empNo;

    @ExcelColumn(value = "姓名", order = 2, required = true, comment = "必填")
    private String name;

    @ExcelColumn(value = "性别", order = 3, comment = "男/女 或 1=男,2=女")
    private String sex;

    @ExcelColumn(value = "部门", order = 4, required = true, comment = "如 工程部/业务部/仓库/资材部/品质部/制造部/冲型/印刷/组装/加工/刀模")
    private String deptName;

    @ExcelColumn(value = "岗位", order = 5, comment = "岗位名称（字典 hr_position）：主管/经理/工程师/业务/助理/组长/印刷师傅/网版师傅/作业员/冲型师傅/QC/IPQC")
    private String position;

    @ExcelColumn(value = "手机号", order = 6, comment = "11 位手机号")
    private String phone;

    @ExcelColumn(value = "邮箱", order = 7)
    private String email;

    @ExcelColumn(value = "进厂日期", order = 8, comment = "yyyy-MM-dd，如 2019-03-01")
    private String hireDate;

    @ExcelColumn(value = "身份证号", order = 9, comment = "18 位身份证号（入库即加密）")
    private String idCardNo;

    @ExcelColumn(value = "身份证地址", order = 10, comment = "身份证住址（敏感）")
    private String idCardAddress;

    @ExcelColumn(value = "现住址", order = 11)
    private String currentAddress;

    @ExcelColumn(value = "学历", order = 12, comment = "小学/初中/高中/中专/职高/大专/本科/硕士")
    private String education;

    @ExcelColumn(value = "专业", order = 13)
    private String major;

    @ExcelColumn(value = "个人履历", order = 14)
    private String resume;

    @ExcelColumn(value = "备注", order = 15)
    private String remark;
}
