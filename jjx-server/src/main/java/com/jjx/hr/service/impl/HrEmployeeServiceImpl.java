package com.jjx.hr.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jjx.common.core.page.PageResult;
import com.jjx.common.exception.BusinessException;
import com.jjx.hr.domain.dto.HrCreateUserDTO;
import com.jjx.hr.domain.dto.HrEmployeeImportDTO;
import com.jjx.hr.domain.dto.HrEmployeeQueryDTO;
import com.jjx.hr.domain.entity.HrEmployee;
import com.jjx.hr.domain.vo.HrAccountVO;
import com.jjx.hr.domain.vo.HrEmployeeVO;
import com.jjx.hr.domain.vo.HrImportResultVO;
import com.jjx.hr.domain.vo.HrRoleOptionVO;
import com.jjx.hr.mapper.HrDeptMappingMapper;
import com.jjx.hr.mapper.HrEmployeeMapper;
import com.jjx.hr.service.HrEmployeeService;
import com.jjx.hr.support.IdCardCipher;
import com.jjx.system.domain.dto.SysUserDTO;
import com.jjx.system.domain.entity.SysUser;
import com.jjx.system.service.ISysUserService;
import com.jjx.system.service.SysConfigService;
import com.jjx.system.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 员工档案服务实现（人事管理 P0，2026-09-10）。
 *
 * <p>要点：①员工与账号解耦（user_id 可空、唯一）②身份证号 AES 加密落库、按权限脱敏展示
 * ③部门支持「含子部门」筛选 ④导入按 hr_dept_mapping 映射部门、行级错误不阻断整批。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HrEmployeeServiceImpl extends ServiceImpl<HrEmployeeMapper, HrEmployee>
        implements HrEmployeeService {

    /** 敏感字段查看权限 */
    public static final String PERM_SENSITIVE = "hr:employee:sensitive";

    private static final String CFG_PREFIX = "hr.emp_no.prefix";
    private static final String CFG_DIGITS = "hr.emp_no.digits";
    private static final String CFG_DEFAULT_PASSWORD = "hr.user.default_password";
    private static final String DEFAULT_PASSWORD = "123456";
    private static final String DEFAULT_PREFIX = "JJX";
    private static final int DEFAULT_DIGITS = 4;
    private static final String PHONE_REGEX = "^1[3-9]\\d{9}$";

    private static final DateTimeFormatter[] DATE_FORMATS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy/M/d"),
            DateTimeFormatter.ofPattern("yyyy.M.d"),
            DateTimeFormatter.ofPattern("yyyyMMdd")
    };

    private final HrDeptMappingMapper deptMappingMapper;
    private final SysConfigService sysConfigService;
    private final IdCardCipher idCardCipher;
    private final JdbcTemplate jdbcTemplate;
    private final ISysUserService sysUserService;

    // ==================== 查询 ====================

    @Override
    public PageResult<HrEmployeeVO> page(HrEmployeeQueryDTO query) {
        Page<HrEmployee> page = new Page<>(query.getPageNum(), query.getPageSize());
        Page<HrEmployee> result = this.page(page, buildWrapper(query));
        return PageResult.of(result, toVOList(result.getRecords()));
    }

    @Override
    public List<HrEmployeeVO> list(HrEmployeeQueryDTO query) {
        return toVOList(this.list(buildWrapper(query)));
    }

    @Override
    public HrEmployeeVO detail(Long empId) {
        HrEmployee entity = this.getById(empId);
        if (entity == null) {
            throw new BusinessException("员工不存在或已删除");
        }
        List<HrEmployeeVO> vos = toVOList(List.of(entity));
        return vos.get(0);
    }

    @Override
    public String nextEmpNo() {
        return generateEmpNo();
    }

    // ==================== 写 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(HrEmployee entity) {
        if (entity == null || !hasText(entity.getName())) {
            throw new BusinessException("姓名不能为空");
        }
        entity.setEmpId(null);
        if (!hasText(entity.getEmpNo())) {
            entity.setEmpNo(generateEmpNo());
        } else {
            entity.setEmpNo(entity.getEmpNo().trim());
        }
        normalize(entity);
        entity.setIdCardNo(encryptIdCard(entity.getIdCardNo()));
        assertUnique(entity, null);
        entity.setDelFlag("0");
        this.save(entity);
        log.info("人事：新增员工 {} {}", entity.getEmpNo(), entity.getName());
        return entity.getEmpId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(HrEmployee entity) {
        if (entity == null || entity.getEmpId() == null) {
            throw new BusinessException("缺少员工ID");
        }
        HrEmployee db = this.getById(entity.getEmpId());
        if (db == null) {
            throw new BusinessException("员工不存在或已删除");
        }
        // 脱敏值原样回传时视为“未修改”，保留库中原值
        if (!hasText(entity.getIdCardNo()) || IdCardCipher.isMasked(entity.getIdCardNo())) {
            entity.setIdCardNo(db.getIdCardNo());
        } else {
            entity.setIdCardNo(encryptIdCard(entity.getIdCardNo()));
        }
        if (!hasText(entity.getIdCardAddress()) || IdCardCipher.isMasked(entity.getIdCardAddress())) {
            entity.setIdCardAddress(db.getIdCardAddress());
        }
        if (!hasText(entity.getCurrentAddress()) || IdCardCipher.isMasked(entity.getCurrentAddress())) {
            entity.setCurrentAddress(db.getCurrentAddress());
        }
        if (!hasText(entity.getEmpNo())) {
            entity.setEmpNo(db.getEmpNo());
        } else {
            entity.setEmpNo(entity.getEmpNo().trim());
        }
        normalize(entity);
        assertUnique(entity, entity.getEmpId());
        this.updateById(entity);
        log.info("人事：更新员工 {} {}", entity.getEmpNo(), entity.getName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long empId) {
        HrEmployee db = this.getById(empId);
        if (db == null) {
            throw new BusinessException("员工不存在或已删除");
        }
        this.removeById(empId);
        log.info("人事：删除员工 {} {}", db.getEmpNo(), db.getName());
    }

    @Override
    public void assertUserAvailable(Long userId, Long excludeEmpId) {
        if (userId == null) {
            return;
        }
        LambdaQueryWrapper<HrEmployee> w = new LambdaQueryWrapper<HrEmployee>()
                .eq(HrEmployee::getUserId, userId)
                .ne(excludeEmpId != null, HrEmployee::getEmpId, excludeEmpId);
        if (this.count(w) > 0) {
            throw new BusinessException("该账号已关联其他员工（一账号仅可关联一名员工）");
        }
    }

    // ==================== 生成系统账号 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public HrAccountVO createUserFromEmployee(Long empId, HrCreateUserDTO dto) {
        HrEmployee e = this.getById(empId);
        if (e == null) {
            throw new BusinessException("员工不存在或已删除");
        }
        if (e.getUserId() != null) {
            throw new BusinessException("该员工已生成账号（user_id=" + e.getUserId() + "），请到系统管理→用户管理维护");
        }
        HrCreateUserDTO param = dto == null ? new HrCreateUserDTO() : dto;
        String userName = hasText(param.getUserName()) ? param.getUserName().trim() : e.getEmpNo();
        if (!hasText(userName)) {
            throw new BusinessException("未取到登录名（员工工号为空），请手工填写");
        }
        String password = hasText(param.getPassword()) ? param.getPassword().trim() : defaultPassword();

        List<String> warnings = new ArrayList<>();
        // 重名（如 张伟/张薇 → zhangwei）自动加序号，并在返回里提示
        if (sysUserService.selectUserByUserName(userName) != null) {
            String base = userName;
            int seq = 2;
            while (seq <= 99 && sysUserService.selectUserByUserName(base + seq) != null) {
                seq++;
            }
            if (seq > 99) {
                throw new BusinessException("登录名 " + base + " 及其序号后缀均被占用，请手工指定登录名");
            }
            userName = base + seq;
            warnings.add("登录名 " + base + " 已存在，已自动改为 " + userName + "（可手工修改）");
        }

        SysUserDTO u = new SysUserDTO();
        u.setUserName(userName);
        u.setNickName(e.getName());
        u.setDeptId(e.getDeptId());
        u.setSex(e.getSex() == null ? "0" : String.valueOf(e.getSex()));
        u.setStatus(0);
        u.setPassword(password);
        u.setRemark(hasText(param.getRemark()) ? param.getRemark()
                : ("由员工档案生成（工号 " + e.getEmpNo() + "）"));
        if (hasText(e.getPhone())) {
            if (countUserByField("phone", e.getPhone()) == 0) {
                u.setPhone(e.getPhone());
            } else {
                warnings.add("手机号 " + e.getPhone() + " 已被其他账号占用，未同步到账号");
            }
        }
        if (hasText(e.getEmail())) {
            if (countUserByField("email", e.getEmail()) == 0) {
                u.setEmail(e.getEmail());
            } else {
                warnings.add("邮箱 " + e.getEmail() + " 已被其他账号占用，未同步到账号");
            }
        }
        boolean bound = param.getRoleIds() != null && !param.getRoleIds().isEmpty();
        if (bound) {
            u.setRoleIds(param.getRoleIds());
        }
        sysUserService.insertUser(u);

        SysUser created = sysUserService.selectUserByUserName(userName);
        if (created == null) {
            throw new BusinessException("账号创建失败，请重试");
        }
        HrEmployee upd = new HrEmployee();
        upd.setEmpId(empId);
        upd.setUserId(created.getUserId());
        this.updateById(upd);

        HrAccountVO vo = new HrAccountVO();
        vo.setUserId(created.getUserId());
        vo.setUserName(userName);
        vo.setNickName(e.getName());
        vo.setDeptId(e.getDeptId());
        vo.setDeptName(deptNameIndexById().get(e.getDeptId()));
        vo.setRoleBound(bound);
        if (!bound) {
            warnings.add("未分配角色：可在弹窗中勾选，或到 系统管理→用户管理→分配角色 授权");
        }
        vo.setWarnings(warnings);
        log.info("人事：由员工档案生成账号 empNo={} userName={} userId={}", e.getEmpNo(), userName, created.getUserId());
        return vo;
    }

    @Override
    public List<HrRoleOptionVO> roleOptions() {
        List<HrRoleOptionVO> list = new ArrayList<>();
        jdbcTemplate.query("SELECT role_id, role_name, role_key FROM sys_role "
                        + "WHERE del_flag = '0' AND status = '0' ORDER BY role_sort, role_id",
                rs -> {
                    HrRoleOptionVO r = new HrRoleOptionVO();
                    r.setRoleId(rs.getLong(1));
                    r.setRoleName(rs.getString(2));
                    r.setRoleKey(rs.getString(3));
                    list.add(r);
                });
        return list;
    }

    private String defaultPassword() {
        try {
            String v = sysConfigService.getValue(CFG_DEFAULT_PASSWORD);
            if (hasText(v)) {
                return v.trim();
            }
        } catch (Exception ex) {
            log.warn("读取 {} 失败，使用内置默认密码：{}", CFG_DEFAULT_PASSWORD, ex.getMessage());
        }
        return DEFAULT_PASSWORD;
    }

    /** 统计 sys_user 中某列已存在（列名仅内部常量调用） */
    private long countUserByField(String column, String value) {
        Long c = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE " + column + " = ? AND del_flag = '0'",
                Long.class, value);
        return c == null ? 0L : c;
    }

    // ==================== 导入 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public HrImportResultVO importEmployees(List<HrEmployeeImportDTO> rows, String operName) {
        HrImportResultVO result = new HrImportResultVO();
        if (rows == null || rows.isEmpty()) {
            result.setTotal(0);
            return result;
        }
        result.setTotal(rows.size());

        Map<String, Long> deptMapping = deptMappingIndex();
        Map<String, Long> deptByName = deptNameIndex();
        Map<String, String> eduMap = educationLabelToKey();

        int rowNum = 1; // 第 1 行为表头
        for (HrEmployeeImportDTO dto : rows) {
            rowNum++;
            try {
                HrEmployee e = new HrEmployee();
                String name = trimToNull(dto.getName());
                if (name == null) {
                    throw new BusinessException("姓名为空");
                }
                e.setName(name);
                e.setSex(parseSex(dto.getSex()));

                Long deptId = resolveDept(dto.getDeptName(), deptMapping, deptByName);
                if (deptId == null) {
                    throw new BusinessException("部门未匹配：" + trimToNull(dto.getDeptName()));
                }
                e.setDeptId(deptId);
                e.setPosition(trimToNull(dto.getPosition()));

                String phone = trimToNull(dto.getPhone());
                if (phone != null) {
                    if (!phone.matches(PHONE_REGEX)) {
                        throw new BusinessException("手机号格式不正确：" + phone);
                    }
                    e.setPhone(phone);
                }
                e.setEmail(trimToNull(dto.getEmail()));
                e.setHireDate(parseDate(dto.getHireDate()));
                e.setIdCardAddress(trimToNull(dto.getIdCardAddress()));
                e.setCurrentAddress(trimToNull(dto.getCurrentAddress()));
                String edu = trimToNull(dto.getEducation());
                if (edu != null) {
                    e.setEducation(eduMap.getOrDefault(edu, edu));
                }
                e.setMajor(trimToNull(dto.getMajor()));
                e.setResume(trimToNull(dto.getResume()));
                e.setRemark(trimToNull(dto.getRemark()));
                e.setEmploymentStatus(2);
                e.setIdCardNo(trimToNull(dto.getIdCardNo()));

                String empNo = trimToNull(dto.getEmpNo());
                if (empNo != null) {
                    e.setEmpNo(empNo);
                }
                create(e);
                result.setSuccessCount(result.getSuccessCount() + 1);
            } catch (Exception ex) {
                result.setFailCount(result.getFailCount() + 1);
                String msg = ex instanceof BusinessException ? ex.getMessage() : ("入库失败：" + ex.getMessage());
                result.addError(rowNum, msg);
                log.warn("人事导入第 {} 行失败：{}", rowNum, msg);
            }
        }
        log.info("人事导入完成（操作人 {}）：共 {} 行，成功 {}，失败 {}", operName,
                result.getTotal(), result.getSuccessCount(), result.getFailCount());
        return result;
    }

    // ==================== 内部：包装/转换 ====================

    private LambdaQueryWrapper<HrEmployee> buildWrapper(HrEmployeeQueryDTO q) {
        LambdaQueryWrapper<HrEmployee> w = new LambdaQueryWrapper<>();
        if (q == null) {
            return w.orderByDesc(HrEmployee::getEmpId);
        }
        String kw = trimToNull(q.getKeyword());
        if (kw != null) {
            w.and(x -> x.like(HrEmployee::getName, kw)
                    .or().like(HrEmployee::getEmpNo, kw)
                    .or().like(HrEmployee::getPhone, kw));
        }
        if (q.getDeptId() != null) {
            w.in(HrEmployee::getDeptId, deptIdWithChildren(q.getDeptId()));
        }
        if (q.getPosition() != null && !q.getPosition().trim().isEmpty()) {
            w.eq(HrEmployee::getPosition, q.getPosition().trim());
        }
        if (q.getEmploymentStatus() != null) {
            w.eq(HrEmployee::getEmploymentStatus, q.getEmploymentStatus());
        }
        if (Boolean.TRUE.equals(q.getOnlyLinked())) {
            w.isNotNull(HrEmployee::getUserId);
        }
        return w.orderByDesc(HrEmployee::getEmpId);
    }

    private List<HrEmployeeVO> toVOList(List<HrEmployee> records) {
        List<HrEmployeeVO> list = new ArrayList<>();
        if (records == null || records.isEmpty()) {
            return list;
        }
        Map<Long, String> deptNames = deptNameIndexById();
        Map<Long, String> userNames = userNameIndex();
        boolean visible = SecurityUtils.hasPermission(PERM_SENSITIVE);
        for (HrEmployee e : records) {
            HrEmployeeVO vo = new HrEmployeeVO();
            vo.setEmpId(e.getEmpId());
            vo.setEmpNo(e.getEmpNo());
            vo.setName(e.getName());
            vo.setSex(e.getSex());
            vo.setDeptId(e.getDeptId());
            vo.setDeptName(deptNames.get(e.getDeptId()));
            vo.setPosition(e.getPosition());
            vo.setPhone(e.getPhone());
            vo.setEmail(e.getEmail());
            vo.setHireDate(e.getHireDate());
            vo.setLeaveDate(e.getLeaveDate());
            vo.setEmploymentStatus(e.getEmploymentStatus());
            vo.setEducation(e.getEducation());
            vo.setMajor(e.getMajor());
            vo.setResume(e.getResume());
            vo.setUserId(e.getUserId());
            vo.setUserName(userNames.get(e.getUserId()));
            vo.setRemark(e.getRemark());
            vo.setCreateTime(e.getCreateTime());
            vo.setUpdateTime(e.getUpdateTime());
            vo.setSensitiveVisible(visible);

            String idCard = idCardCipher.decrypt(e.getIdCardNo());
            if (visible) {
                vo.setIdCardNo(idCard);
                vo.setIdCardAddress(e.getIdCardAddress());
                vo.setCurrentAddress(e.getCurrentAddress());
            } else {
                vo.setIdCardNo(IdCardCipher.maskIdCard(idCard));
                vo.setIdCardAddress(IdCardCipher.maskAddress(e.getIdCardAddress()));
                vo.setCurrentAddress(IdCardCipher.maskAddress(e.getCurrentAddress()));
            }
            list.add(vo);
        }
        return list;
    }

    // ==================== 内部：索引 ====================

    private Map<Long, String> deptNameIndexById() {
        Map<Long, String> m = new HashMap<>();
        jdbcTemplate.query("SELECT dept_id, dept_name FROM sys_dept WHERE del_flag = '0'",
                rs -> {
                    m.put(rs.getLong(1), rs.getString(2));
                });
        return m;
    }

    private Map<String, Long> deptNameIndex() {
        Map<String, Long> m = new HashMap<>();
        jdbcTemplate.query("SELECT dept_id, dept_name FROM sys_dept WHERE del_flag = '0'",
                rs -> {
                    m.put(rs.getString(2), rs.getLong(1));
                });
        return m;
    }

    private Map<String, Long> deptMappingIndex() {
        Map<String, Long> m = new HashMap<>();
        jdbcTemplate.query("SELECT m.source_name, m.dept_id FROM hr_dept_mapping m "
                        + "JOIN sys_dept d ON d.dept_id = m.dept_id AND d.del_flag = '0'",
                rs -> {
                    m.put(rs.getString(1), rs.getLong(2));
                });
        return m;
    }

    private Map<Long, String> userNameIndex() {
        Map<Long, String> m = new HashMap<>();
        jdbcTemplate.query("SELECT user_id, user_name FROM sys_user WHERE del_flag = '0'",
                rs -> {
                    m.put(rs.getLong(1), rs.getString(2));
                });
        return m;
    }

    private Map<String, String> educationLabelToKey() {
        Map<String, String> m = new HashMap<>();
        jdbcTemplate.query("SELECT item_key, label FROM sys_dict_item "
                        + "WHERE dict_code = 'hr_education' AND deleted = 0",
                rs -> {
                    m.put(rs.getString(2), rs.getString(1));
                });
        return m;
    }

    private Set<Long> deptIdWithChildren(Long rootId) {
        Map<Long, Long> parent = new HashMap<>();
        jdbcTemplate.query("SELECT dept_id, parent_id FROM sys_dept WHERE del_flag = '0'",
                rs -> {
                    parent.put(rs.getLong(1), rs.getLong(2));
                });
        Set<Long> ids = new HashSet<>();
        ids.add(rootId);
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Map.Entry<Long, Long> e : parent.entrySet()) {
                if (!ids.contains(e.getKey()) && e.getValue() != null && ids.contains(e.getValue())) {
                    ids.add(e.getKey());
                    changed = true;
                }
            }
        }
        return ids;
    }

    // ==================== 内部：校验/生成 ====================

    private void normalize(HrEmployee e) {
        e.setName(e.getName() == null ? null : e.getName().trim());
        if (hasText(e.getPhone())) {
            String phone = e.getPhone().trim();
            if (!phone.matches(PHONE_REGEX)) {
                throw new BusinessException("手机号格式不正确：" + phone);
            }
            e.setPhone(phone);
        } else {
            e.setPhone(null);
        }
        if (e.getEmploymentStatus() == null) {
            e.setEmploymentStatus(2);
        }
        if (e.getSex() != null && (e.getSex() < 0 || e.getSex() > 2)) {
            e.setSex(null);
        }
    }

    private void assertUnique(HrEmployee e, Long excludeId) {
        if (hasText(e.getEmpNo())
                && this.count(new LambdaQueryWrapper<HrEmployee>()
                .eq(HrEmployee::getEmpNo, e.getEmpNo())
                .ne(excludeId != null, HrEmployee::getEmpId, excludeId)) > 0) {
            throw new BusinessException("工号已存在：" + e.getEmpNo());
        }
        if (hasText(e.getPhone())
                && this.count(new LambdaQueryWrapper<HrEmployee>()
                .eq(HrEmployee::getPhone, e.getPhone())
                .ne(excludeId != null, HrEmployee::getEmpId, excludeId)) > 0) {
            throw new BusinessException("手机号已被其他员工使用：" + e.getPhone());
        }
        if (e.getUserId() != null) {
            assertUserAvailable(e.getUserId(), excludeId);
        }
        if (hasText(e.getIdCardNo())
                && this.count(new LambdaQueryWrapper<HrEmployee>()
                .eq(HrEmployee::getIdCardNo, e.getIdCardNo())
                .ne(excludeId != null, HrEmployee::getEmpId, excludeId)) > 0) {
            throw new BusinessException("身份证号已存在，请勿重复录入");
        }
    }

    private String encryptIdCard(String value) {
        if (!hasText(value)) {
            return null;
        }
        String v = value.trim();
        if (IdCardCipher.isMasked(v)) {
            return null;
        }
        return idCardCipher.encrypt(v);
    }

    private String generateEmpNo() {
        String prefix = DEFAULT_PREFIX;
        int digits = DEFAULT_DIGITS;
        try {
            String p = sysConfigService.getValue(CFG_PREFIX);
            if (hasText(p)) {
                prefix = p.trim();
            }
            String d = sysConfigService.getValue(CFG_DIGITS);
            if (hasText(d)) {
                digits = Integer.parseInt(d.trim());
            }
        } catch (Exception ex) {
            log.warn("读取工号规则配置失败，使用默认值 {} / {}：{}", prefix, digits, ex.getMessage());
        }
        Long max = baseMapper.selectMaxEmpNoSeq(prefix);
        long next = (max == null ? 0L : max) + 1L;
        return prefix + String.format("%0" + digits + "d", next);
    }

    private Long resolveDept(String deptText, Map<String, Long> mapping, Map<String, Long> byName) {
        String t = trimToNull(deptText);
        if (t == null) {
            return null;
        }
        Long id = mapping.get(t);
        if (id != null) {
            return id;
        }
        id = byName.get(t);
        if (id != null) {
            return id;
        }
        // 容错：去掉“部/车间”等后缀再试
        String simplified = t.replace("车间", "").replace("部门", "").replace("部", "");
        for (Map.Entry<String, Long> e : mapping.entrySet()) {
            if (e.getKey().replace("车间", "").replace("部门", "").replace("部", "").equals(simplified)) {
                return e.getValue();
            }
        }
        for (Map.Entry<String, Long> e : byName.entrySet()) {
            if (e.getKey().replace("车间", "").replace("部门", "").replace("部", "").equals(simplified)) {
                return e.getValue();
            }
        }
        return null;
    }

    private Integer parseSex(String value) {
        String t = trimToNull(value);
        if (t == null) {
            return null;
        }
        if (t.startsWith("男") || "1".equals(t) || "M".equalsIgnoreCase(t)) {
            return 1;
        }
        if (t.startsWith("女") || "2".equals(t) || "F".equalsIgnoreCase(t)) {
            return 2;
        }
        return null;
    }

    private LocalDate parseDate(String value) {
        String t = trimToNull(value);
        if (t == null) {
            return null;
        }
        String datePart = t.length() > 10 && t.contains(" ") ? t.substring(0, t.indexOf(' ')) : t;
        for (DateTimeFormatter f : DATE_FORMATS) {
            try {
                return LocalDate.parse(datePart, f);
            } catch (Exception ignored) {
                // 试下一种格式
            }
        }
        log.warn("无法解析日期：{}", value);
        return null;
    }

    private static boolean hasText(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
