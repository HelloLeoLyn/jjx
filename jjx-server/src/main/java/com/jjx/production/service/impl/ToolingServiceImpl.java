package com.jjx.production.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jjx.common.core.page.PageResult;
import com.jjx.common.exception.BusinessException;
import com.jjx.framework.common.RedisSequenceService;
import com.jjx.production.domain.dto.ToolingDTO;
import com.jjx.production.domain.dto.ToolingImportDTO;
import com.jjx.production.domain.dto.ToolingQueryDTO;
import com.jjx.production.domain.entity.ProductionTooling;
import com.jjx.production.domain.vo.ToolingVO;
import com.jjx.production.enums.ToolingStatusEnum;
import com.jjx.production.enums.ToolingTypeEnum;
import com.jjx.production.mapper.ProductionToolingMapper;
import com.jjx.production.service.ToolingService;
import com.jjx.system.domain.entity.SysAttachment;
import com.jjx.system.service.ISysAttachmentService;
import com.jjx.system.service.SysConfigService;
import com.jjx.system.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 工装模具档案 Service 实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ToolingServiceImpl extends ServiceImpl<ProductionToolingMapper, ProductionTooling> implements ToolingService {

    /** 编号规则配置键（sys_config） */
    private static final String CONFIG_NO_RULE = "tooling_no_rule";
    private static final String DEFAULT_NO_RULE = "{prefix}{date}{seq:3}";
    /** 附件业务类型 */
    private static final String ATTACH_BIZ_TYPE = "tooling";

    private final ProductionToolingMapper toolingMapper;
    private final SysConfigService sysConfigService;
    private final RedisSequenceService redisSequenceService;
    private final ISysAttachmentService sysAttachmentService;

    // ==================== 查询 ====================

    @Override
    public PageResult<ToolingVO> page(ToolingQueryDTO query) {
        LambdaQueryWrapper<ProductionTooling> w = buildWrapper(query);
        w.orderByDesc(ProductionTooling::getCreateTime);
        Page<ProductionTooling> p = new Page<>(query.getPageNum(), query.getPageSize());
        toolingMapper.selectPage(p, w);
        List<ToolingVO> vos = new ArrayList<>();
        for (ProductionTooling e : p.getRecords()) {
            vos.add(withPhoto(toVO(e)));
        }
        return PageResult.of(p, vos);
    }

    @Override
    public List<ToolingVO> list(ToolingQueryDTO query) {
        LambdaQueryWrapper<ProductionTooling> w = buildWrapper(query);
        w.orderByDesc(ProductionTooling::getCreateTime);
        List<ToolingVO> vos = new ArrayList<>();
        for (ProductionTooling e : toolingMapper.selectList(w)) {
            vos.add(toVO(e));
        }
        return vos;
    }

    @Override
    public ToolingVO getById(Long id) {
        ProductionTooling e = toolingMapper.selectById(id);
        if (e == null) throw new BusinessException("工装模具不存在");
        return withPhoto(toVO(e));
    }

    @Override
    public List<ToolingVO> options(String type) {
        ToolingQueryDTO q = new ToolingQueryDTO();
        q.setType(type);
        LambdaQueryWrapper<ProductionTooling> w = buildWrapper(q);
        // 排除已报废：status != 4
        w.ne(ProductionTooling::getStatus, ToolingStatusEnum.SCRAPPED.getCode());
        w.orderByAsc(ProductionTooling::getToolingNo);
        List<ToolingVO> vos = new ArrayList<>();
        for (ProductionTooling e : toolingMapper.selectList(w)) {
            vos.add(toVO(e));
        }
        return vos;
    }

    private LambdaQueryWrapper<ProductionTooling> buildWrapper(ToolingQueryDTO q) {
        LambdaQueryWrapper<ProductionTooling> w = Wrappers.lambdaQuery();
        if (q == null) return w;
        if (StringUtils.isNotBlank(q.getType())) {
            w.eq(ProductionTooling::getToolingType, q.getType());
        }
        if (StringUtils.isNotBlank(q.getKeyword())) {
            w.and(x -> x.like(ProductionTooling::getToolingNo, q.getKeyword())
                    .or().like(ProductionTooling::getToolingName, q.getKeyword()));
        }
        if (q.getStatus() != null) {
            w.eq(ProductionTooling::getStatus, q.getStatus());
        }
        return w;
    }

    // ==================== 编号生成 ====================

    @Override
    public String genNo(String type) {
        ToolingTypeEnum t = ToolingTypeEnum.fromCode(type);
        if (t == null) throw new BusinessException("工装类型不正确（SCREEN=网框 / DIE=刀模）");
        String rule = sysConfigService.getValue(CONFIG_NO_RULE);
        if (StringUtils.isBlank(rule)) rule = DEFAULT_NO_RULE;

        String prefix = "DIE".equals(t.getCode()) ? "DM" : "WK";
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd"));
        String redisKey = "seq:tooling:" + prefix + ":" + date;
        long seq = redisSequenceService.getNextSequence(redisKey);

        // 解析模板：{prefix} {date} {seq:N}
        StringBuilder sb = new StringBuilder();
        Matcher m = Pattern.compile("\\{(prefix|date|seq:\\d+)}").matcher(rule);
        int last = 0;
        while (m.find()) {
            sb.append(rule, last, m.start());
            String token = m.group(1);
            if ("prefix".equals(token)) {
                sb.append(prefix);
            } else if ("date".equals(token)) {
                sb.append(date);
            } else {
                int width = Integer.parseInt(token.substring(4, token.length() - 1));
                sb.append(String.format("%0" + width + "d", seq));
            }
            last = m.end();
        }
        sb.append(rule.substring(last));
        String no = sb.toString();
        // 唯一性兜底：万一冲突则继续递增
        while (toolingMapper.selectCount(Wrappers.<ProductionTooling>lambdaQuery()
                .eq(ProductionTooling::getToolingNo, no)) > 0) {
            seq = redisSequenceService.getNextSequence(redisKey);
            no = render(rule, prefix, date, (int) seq);
        }
        return no;
    }

    private String render(String rule, String prefix, String date, long seq) {
        StringBuilder sb = new StringBuilder();
        Matcher m = Pattern.compile("\\{(prefix|date|seq:\\d+)}").matcher(rule);
        int last = 0;
        while (m.find()) {
            sb.append(rule, last, m.start());
            String token = m.group(1);
            if ("prefix".equals(token)) sb.append(prefix);
            else if ("date".equals(token)) sb.append(date);
            else {
                int width = Integer.parseInt(token.substring(4, token.length() - 1));
                sb.append(String.format("%0" + width + "d", seq));
            }
            last = m.end();
        }
        sb.append(rule.substring(last));
        return sb.toString();
    }

    // ==================== 增删改 ====================

    @Override
    public Long create(ToolingDTO dto) {
        validate(dto);
        checkNoUnique(dto.getToolingNo(), null);
        ProductionTooling e = toEntity(dto);
        e.setCreateBy(SecurityUtils.getUsername());
        toolingMapper.insert(e);
        return e.getToolingId();
    }

    @Override
    public void update(ToolingDTO dto) {
        if (dto.getToolingId() == null) throw new BusinessException("缺少工装ID");
        ProductionTooling exist = toolingMapper.selectById(dto.getToolingId());
        if (exist == null) throw new BusinessException("工装模具不存在");
        validate(dto);
        checkNoUnique(dto.getToolingNo(), dto.getToolingId());
        ProductionTooling e = toEntity(dto);
        e.setUpdateBy(SecurityUtils.getUsername());
        toolingMapper.updateById(e);
    }

    @Override
    public void changeStatus(Long id, Integer status) {
        if (status == null || ToolingStatusEnum.fromCode(status) == null) {
            throw new BusinessException("工装状态不正确（0在库/1使用中/2清洗保养/3维修/4报废）");
        }
        ProductionTooling e = toolingMapper.selectById(id);
        if (e == null) throw new BusinessException("工装模具不存在");
        ProductionTooling upd = new ProductionTooling();
        upd.setToolingId(id);
        upd.setStatus(status);
        upd.setUpdateBy(SecurityUtils.getUsername());
        toolingMapper.updateById(upd);
    }

    @Override
    public void delete(Long id) {
        ProductionTooling e = toolingMapper.selectById(id);
        if (e == null) throw new BusinessException("工装模具不存在");
        toolingMapper.deleteById(id);
    }

    // ==================== 导入导出 ====================

    @Override
    public String importExcel(List<ToolingImportDTO> list, String operator) {
        if (list == null || list.isEmpty()) return "导入文件为空";
        int success = 0;
        int fail = 0;
        StringBuilder errMsg = new StringBuilder();
        int row = 1; // 表头占1行，数据从第2行开始
        for (ToolingImportDTO dto : list) {
            row++;
            try {
                ToolingTypeEnum t = ToolingTypeEnum.fromLabel(dto.getToolingType());
                if (t == null) {
                    fail++;
                    errMsg.append("第").append(row).append("行：类型必须是'网框'或'刀模'；");
                    continue;
                }
                if (StringUtils.isBlank(dto.getToolingName())) {
                    fail++;
                    errMsg.append("第").append(row).append("行：名称为空；");
                    continue;
                }
                ProductionTooling e = new ProductionTooling();
                e.setToolingNo(StringUtils.isBlank(dto.getToolingNo()) ? genNo(t.getCode()) : dto.getToolingNo().trim());
                if (toolingMapper.selectCount(Wrappers.<ProductionTooling>lambdaQuery()
                        .eq(ProductionTooling::getToolingNo, e.getToolingNo())) > 0) {
                    fail++;
                    errMsg.append("第").append(row).append("行：编号已存在(").append(e.getToolingNo()).append(")；");
                    continue;
                }
                e.setToolingName(dto.getToolingName().trim());
                e.setToolingType(t.getCode());
                e.setSpec(dto.getSpec());
                e.setLifeLimit(dto.getLifeLimit());
                e.setCurrentCount(0);
                e.setStatus(ToolingStatusEnum.IN_STOCK.getCode());
                e.setLocation(dto.getLocation());
                e.setResponsible(dto.getResponsible());
                e.setCustomer(dto.getCustomer());
                e.setEnableDate(parseDate(dto.getEnableDate()));
                e.setRemark(dto.getRemark());
                e.setCreateBy(operator);
                toolingMapper.insert(e);
                success++;
            } catch (Exception ex) {
                fail++;
                errMsg.append("第").append(row).append("行：").append(ex.getMessage()).append("；");
            }
        }
        String result = "导入完成：成功 " + success + " 条，失败 " + fail + " 条";
        if (fail > 0) result += "。失败明细：" + errMsg;
        return result;
    }

    @Override
    public Class<ToolingImportDTO> importDtoClass() {
        return ToolingImportDTO.class;
    }

    @Override
    public ToolingVO toVO(ProductionTooling e) {
        return ToolingVO.fromEntity(e);
    }

    // ==================== 私有方法 ====================

    private ToolingVO withPhoto(ToolingVO vo) {
        if (vo == null) return null;
        try {
            List<SysAttachment> atts = sysAttachmentService.getAttachments(ATTACH_BIZ_TYPE, vo.getToolingId());
            if (atts != null && !atts.isEmpty() && atts.get(0).getId() != null) {
                vo.setPhotoId(atts.get(0).getId());
            }
        } catch (Exception ignored) {
            // 附件查询失败不影响主流程
        }
        return vo;
    }

    private void validate(ToolingDTO dto) {
        ToolingTypeEnum t = ToolingTypeEnum.fromCode(dto.getToolingType());
        if (t == null) throw new BusinessException("工装类型不正确（SCREEN=网框 / DIE=刀模）");
        if (dto.getStatus() == null || ToolingStatusEnum.fromCode(dto.getStatus()) == null) {
            throw new BusinessException("工装状态不正确（0在库/1使用中/2清洗保养/3维修/4报废）");
        }
        if (t == ToolingTypeEnum.DIE && dto.getLifeLimit() != null && dto.getLifeLimit() <= 0) {
            throw new BusinessException("设计寿命需大于 0");
        }
    }

    private void checkNoUnique(String no, Long excludeId) {
        LambdaQueryWrapper<ProductionTooling> w = Wrappers.<ProductionTooling>lambdaQuery()
                .eq(ProductionTooling::getToolingNo, no);
        if (excludeId != null) w.ne(ProductionTooling::getToolingId, excludeId);
        if (toolingMapper.selectCount(w) > 0) {
            throw new BusinessException("工装编号已存在：" + no);
        }
    }

    /**
     * 解析导入的启用日期字符串（yyyy-MM-dd），解析失败返回 null
     */
    private java.time.LocalDate parseDate(String s) {
        if (StringUtils.isBlank(s)) return null;
        try {
            return java.time.LocalDate.parse(s.trim(), java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        } catch (Exception e) {
            return null;
        }
    }

    private ProductionTooling toEntity(ToolingDTO dto) {
        ProductionTooling e = new ProductionTooling();
        e.setToolingId(dto.getToolingId());
        e.setToolingNo(dto.getToolingNo().trim());
        e.setToolingName(dto.getToolingName().trim());
        e.setToolingType(dto.getToolingType());
        e.setSpec(dto.getSpec());
        e.setLifeLimit(dto.getLifeLimit());
        e.setCurrentCount(dto.getCurrentCount() == null ? 0 : dto.getCurrentCount());
        e.setStatus(dto.getStatus());
        e.setLocation(dto.getLocation());
        e.setDepartment(dto.getDepartment());
        e.setResponsible(dto.getResponsible());
        e.setCustomer(dto.getCustomer());
        e.setEnableDate(dto.getEnableDate());
        e.setRemark(dto.getRemark());
        return e;
    }
}
