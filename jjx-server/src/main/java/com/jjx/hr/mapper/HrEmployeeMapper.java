package com.jjx.hr.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.hr.domain.entity.HrEmployee;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface HrEmployeeMapper extends BaseMapper<HrEmployee> {

    /**
     * 取工号流水最大值（含已逻辑删除行，避免号码重复使用）。
     */
    @Select("SELECT MAX(CAST(SUBSTRING(emp_no, LENGTH(#{prefix}) + 1) AS UNSIGNED)) "
            + "FROM hr_employee "
            + "WHERE emp_no LIKE CONCAT(#{prefix}, '%') "
            + "AND emp_no REGEXP CONCAT('^', #{prefix}, '[0-9]+$')")
    Long selectMaxEmpNoSeq(@Param("prefix") String prefix);
}
