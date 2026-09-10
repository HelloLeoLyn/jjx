package com.jjx.hr.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 人事导入用「部门文本 → sys_dept」映射。 */
@Data
@TableName("hr_dept_mapping")
public class HrDeptMapping {

    @TableId(type = IdType.AUTO)
    private Long mappingId;

    /** 导入文件中的部门文本 */
    private String sourceName;

    /** 映射到 sys_dept.dept_id */
    private Long deptId;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
