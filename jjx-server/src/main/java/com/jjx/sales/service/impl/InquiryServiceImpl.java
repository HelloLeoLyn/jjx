package com.jjx.sales.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jjx.common.core.page.PageResult;
import com.jjx.common.exception.BusinessException;
import com.jjx.framework.common.RedisSequenceService;
import com.jjx.sales.domain.entity.SalesInquiry;
import com.jjx.sales.domain.entity.SalesQuotation;
import com.jjx.sales.domain.entity.SalesQuotationItem;
import com.jjx.sales.domain.vo.InquiryToQuotationVO;
import com.jjx.sales.enums.InquiryStatus;
import com.jjx.sales.enums.QuotationStatus;
import com.jjx.sales.mapper.SalesInquiryMapper;
import com.jjx.sales.mapper.QuotationMapper;
import com.jjx.sales.mapper.SalesQuotationItemMapper;
import com.jjx.product.mapper.ProductMapper;
import com.jjx.product.domain.entity.Product;
import com.jjx.sales.service.IInquiryService;
import com.jjx.system.annotation.Event;
import com.jjx.system.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 销售询价单服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InquiryServiceImpl implements IInquiryService {

    private final SalesInquiryMapper inquiryMapper;
    private final QuotationMapper quotationMapper;
    private final SalesQuotationItemMapper quotationItemMapper;
    private final RedisSequenceService redisSequenceService;
    private final ProductMapper productMapper;
    private final com.jjx.product.service.IProductService productService;
    private final com.jjx.product.service.ProductCodeService productCodeService;

    /**
     * 分页查询询价单列表
     */
    @Override
    public PageResult<SalesInquiry> selectInquiryPage(SalesInquiry inquiry, Integer pageNum, Integer pageSize) {
        Page<SalesInquiry> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<SalesInquiry> wrapper = buildQueryWrapper(inquiry);
        Page<SalesInquiry> result = inquiryMapper.selectPage(page, wrapper);
        fillProductName(result.getRecords());
        return PageResult.of(result, result.getRecords());
    }

    /**
     * 查询询价单列表
     */
    @Override
    public List<SalesInquiry> selectInquiryList(SalesInquiry inquiry) {
        LambdaQueryWrapper<SalesInquiry> wrapper = buildQueryWrapper(inquiry);
        List<SalesInquiry> list = inquiryMapper.selectList(wrapper);
        fillProductName(list);
        return list;
    }

    /**
     * 构建查询条件
     */
    private LambdaQueryWrapper<SalesInquiry> buildQueryWrapper(SalesInquiry inquiry) {
        LambdaQueryWrapper<SalesInquiry> wrapper = Wrappers.lambdaQuery();

        if (inquiry.getInquiryNo() != null && !inquiry.getInquiryNo().isEmpty()) {
            wrapper.like(SalesInquiry::getInquiryNo, inquiry.getInquiryNo());
        }
        if (inquiry.getCustomerName() != null && !inquiry.getCustomerName().isEmpty()) {
            wrapper.like(SalesInquiry::getCustomerName, inquiry.getCustomerName());
        }
        if (inquiry.getInquiryStatus() != null) {
            wrapper.eq(SalesInquiry::getInquiryStatus, inquiry.getInquiryStatus());
        }
        if (inquiry.getSalesPersonId() != null) {
            wrapper.eq(SalesInquiry::getSalesPersonId, inquiry.getSalesPersonId());
        }
        if (inquiry.getStartDate() != null) {
            wrapper.ge(SalesInquiry::getInquiryDate, inquiry.getStartDate());
        }
        if (inquiry.getEndDate() != null) {
            wrapper.le(SalesInquiry::getInquiryDate, inquiry.getEndDate());
        }

        // 只查未删除
        wrapper.eq(SalesInquiry::getDeleted, 0);
        // 按创建时间倒序
        wrapper.orderByDesc(SalesInquiry::getCreateTime).orderByDesc(SalesInquiry::getInquiryId);

        return wrapper;
    }

    /**
     * 根据ID查询询价单
     */
    @Override
    public SalesInquiry selectInquiryById(Long inquiryId) {
        SalesInquiry inquiry = inquiryMapper.selectById(inquiryId);
        if (inquiry == null || inquiry.getDeleted() == 1) {
            throw new BusinessException("询价单不存在或已被删除");
        }
        fillProductName(List.of(inquiry));
        return inquiry;
    }

    /**
     * 填充关联产品名称
     */
    private void fillProductName(List<SalesInquiry> list) {
        if (list == null || list.isEmpty()) return;
        for (SalesInquiry inquiry : list) {
            if (inquiry.getProductId() != null) {
                Product product = productMapper.selectById(inquiry.getProductId());
                if (product != null) {
                    inquiry.setProductName(product.getProductName());
                }
            }
        }
    }

    /**
     * 新增询价单
     */
    @Override
    public String nextProductSerial(String customerShort) {
        // 2026-08-12：流水号逻辑统一走 ProductCodeService（兼容简称1-3位）
        return productCodeService.nextSerial(customerShort);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertInquiry(SalesInquiry inquiry) {
        // 自动生成询价单号
        // 自动生成询价单号和链路追踪ID
        String inquiryNo = redisSequenceService.generateBusinessNumber("INQ", "询价单号");
        inquiry.setInquiryNo(inquiryNo);
        inquiry.setTraceId(java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16));

        // 设置默认值
        if (inquiry.getInquiryStatus() == null) {
            inquiry.setInquiryStatus(InquiryStatus.DRAFT.getCode());
        }
        if (inquiry.getInquiryType() == null) {
            inquiry.setInquiryType(1); // 默认标准品
        }
        if (inquiry.getInquiryDate() == null) {
            inquiry.setInquiryDate(LocalDate.now());
        }
        if (inquiry.getHasDrawing() == null) {
            inquiry.setHasDrawing(0);
        }

        // 自动设置销售负责人
        if (inquiry.getSalesPersonId() == null) {
            try {
                inquiry.setSalesPersonId(SecurityUtils.getUserId());
                inquiry.setSalesPersonName(SecurityUtils.getUsername());
            } catch (Exception e) {
                log.warn("无法获取当前用户信息: {}", e.getMessage());
            }
        }

        // 样品询价：编码前置建档草稿产品（2026-08-08，DEV-750 关联）
        if (java.lang.Integer.valueOf(2).equals(inquiry.getInquiryType())
                && org.apache.commons.lang3.StringUtils.isNotBlank(inquiry.getProductCode())
                && inquiry.getProductId() == null) {
            Long pid = productService.ensureDraftProduct(
                    inquiry.getProductCode(), inquiry.getProductName(), "PCS", "inquiry");
            inquiry.setProductId(pid);
        }

        return inquiryMapper.insert(inquiry);
    }

    /**
     * 修改询价单
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateInquiry(SalesInquiry inquiry) {
        SalesInquiry existing = inquiryMapper.selectById(inquiry.getInquiryId());
        if (existing == null || existing.getDeleted() == 1) {
            throw new BusinessException("询价单不存在或已被删除");
        }

        // 已转换的询价单不能修改
        if (InquiryStatus.CONVERTED.getCode().equals(existing.getInquiryStatus())) {
            throw new BusinessException("已转换的询价单不能修改");
        }

        // 样品询价：修改时若编码变化且无产品关联，重新建档（2026-08-08）
        if (java.lang.Integer.valueOf(2).equals(inquiry.getInquiryType())
                && StringUtils.hasText(inquiry.getProductCode())
                && inquiry.getProductId() == null) {
            Long pid = productService.ensureDraftProduct(
                    inquiry.getProductCode(), inquiry.getProductName(), "PCS", "inquiry");
            inquiry.setProductId(pid);
        }

        return inquiryMapper.updateById(inquiry);
    }

    /**
     * 删除询价单
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteInquiryById(Long inquiryId) {
        SalesInquiry inquiry = inquiryMapper.selectById(inquiryId);
        if (inquiry == null || inquiry.getDeleted() == 1) {
            throw new BusinessException("询价单不存在或已被删除");
        }
        return inquiryMapper.deleteById(inquiryId);
    }

    /**
     * 批量删除询价单
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteInquiryByIds(Long[] inquiryIds) {
        if (inquiryIds == null || inquiryIds.length == 0) {
            return 0;
        }
        // 已转报价的询价单禁止删除（保护报价单来源记录）
        List<SalesInquiry> list = inquiryMapper.selectBatchIds(Arrays.asList(inquiryIds));
        for (SalesInquiry inquiry : list) {
            if (inquiry != null && InquiryStatus.CONVERTED.getCode().equals(inquiry.getInquiryStatus())) {
                throw new BusinessException("询价单[" + inquiry.getInquiryNo() + "]已转报价，不能删除");
            }
        }
        int rows = inquiryMapper.deleteBatchIds(Arrays.asList(inquiryIds));
        // 作废联动：清理询价建档的草稿产品（2026-08-08）
        for (SalesInquiry inquiry : list) {
            if (inquiry != null && inquiry.getProductId() != null) {
                try {
                    productService.cleanupDraftProduct(inquiry.getProductId(), "inquiry");
                } catch (Exception e) {
                    log.warn("清理询价草稿产品失败: productId={}, err={}", inquiry.getProductId(), e.getMessage());
                }
            }
        }
        return rows;
    }

    /**
     * 检查询价单号是否唯一
     */
    @Override
    public boolean checkInquiryNoUnique(String inquiryNo) {
        if (inquiryNo == null || inquiryNo.isEmpty()) {
            return true;
        }
        LambdaQueryWrapper<SalesInquiry> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(SalesInquiry::getInquiryNo, inquiryNo);
        wrapper.eq(SalesInquiry::getDeleted, 0);
        return inquiryMapper.selectCount(wrapper) == 0;
    }

    /**
     * 发送询价（草稿/待处理 → 已发送）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int sendInquiry(Long inquiryId) {
        SalesInquiry inquiry = inquiryMapper.selectById(inquiryId);
        if (inquiry == null || inquiry.getDeleted() == 1) {
            throw new BusinessException("询价单不存在或已被删除");
        }
        Integer status = inquiry.getInquiryStatus();
        if (!InquiryStatus.DRAFT.getCode().equals(status) && !InquiryStatus.PENDING.getCode().equals(status)) {
            throw new BusinessException("只有草稿或待处理的询价单可以发送");
        }
        SalesInquiry update = new SalesInquiry();
        update.setInquiryId(inquiryId);
        update.setInquiryStatus(InquiryStatus.SENT.getCode());
        return inquiryMapper.updateById(update);
    }

    /**
     * 客户确认询价（已发送 → 已确认）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int acceptInquiry(Long inquiryId) {
        SalesInquiry inquiry = inquiryMapper.selectById(inquiryId);
        if (inquiry == null || inquiry.getDeleted() == 1) {
            throw new BusinessException("询价单不存在或已被删除");
        }
        if (!InquiryStatus.SENT.getCode().equals(inquiry.getInquiryStatus())) {
            throw new BusinessException("只有已发送的询价单可以确认");
        }
        SalesInquiry update = new SalesInquiry();
        update.setInquiryId(inquiryId);
        update.setInquiryStatus(InquiryStatus.ACCEPTED.getCode());
        return inquiryMapper.updateById(update);
    }

    /**
     * 客户拒绝询价（已发送 → 已拒绝）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int rejectInquiry(Long inquiryId) {
        SalesInquiry inquiry = inquiryMapper.selectById(inquiryId);
        if (inquiry == null || inquiry.getDeleted() == 1) {
            throw new BusinessException("询价单不存在或已被删除");
        }
        if (!InquiryStatus.SENT.getCode().equals(inquiry.getInquiryStatus())) {
            throw new BusinessException("只有已发送的询价单可以拒绝");
        }
        SalesInquiry update = new SalesInquiry();
        update.setInquiryId(inquiryId);
        update.setInquiryStatus(InquiryStatus.REJECTED.getCode());
        return inquiryMapper.updateById(update);
    }

    /**
     * 询价转报价
     * 创建报价单并返回报价单ID
     */
    @Override
    @Event(value = "inquiry.converted", bizId = "#result.inquiryNo", bizType = "'inquiry'")
    @Transactional(rollbackFor = Exception.class)
    public InquiryToQuotationVO convertToQuotation(Long inquiryId) {
        SalesInquiry inquiry = selectInquiryById(inquiryId);

        if (InquiryStatus.CONVERTED.getCode().equals(inquiry.getInquiryStatus())) {
            throw new BusinessException("该询价单已转换，不能重复转换");
        }

        // 创建报价单
        SalesQuotation quotation = new SalesQuotation();
        quotation.setQuotationNo(redisSequenceService.generateBusinessNumber("QT", "报价单号"));
        quotation.setQuotationType(inquiry.getInquiryType() != null ? inquiry.getInquiryType() : 1);
        quotation.setCustomerId(inquiry.getCustomerId());
        quotation.setCustomerName(inquiry.getCustomerName());
        quotation.setContactPerson(inquiry.getContactPerson());
        quotation.setContactPhone(inquiry.getContactPhone());
        quotation.setQuotationDate(LocalDate.now());
        quotation.setValidUntil(LocalDate.now().plusDays(30));
        quotation.setQuotationStatus(QuotationStatus.DRAFT.getCode());
        quotation.setCurrency("CNY");
        quotation.setExchangeRate(java.math.BigDecimal.ONE);
        quotation.setSalesPersonId(inquiry.getSalesPersonId());
        quotation.setSalesPersonName(inquiry.getSalesPersonName());
        quotation.setTraceId(inquiry.getTraceId());
        quotation.setRemark("由询价单[" + inquiry.getInquiryNo() + "]自动创建");

        quotationMapper.insert(quotation);

        // 自动生成报价明细：从询价单带出产品/技术要求（DEV-588）
        createQuotationItemFromInquiry(inquiry, quotation.getQuotationId());

        // 更新询价单状态
        inquiry.setInquiryStatus(InquiryStatus.CONVERTED.getCode());
        inquiry.setConvertedQuotationId(quotation.getQuotationId());
        inquiry.setConvertTime(LocalDateTime.now());
        inquiryMapper.updateById(inquiry);

        log.info("询价单[{}]成功转换为报价单[{}]", inquiry.getInquiryNo(), quotation.getQuotationNo());

        InquiryToQuotationVO vo = new InquiryToQuotationVO();
        vo.setQuotationId(quotation.getQuotationId());
        vo.setInquiryNo(inquiry.getInquiryNo());
        vo.setTraceId(inquiry.getTraceId());
        return vo;
    }

    /**
     * 从询价单生成报价明细（DEV-588）
     * 标准品：映射产品ID/编码/名称、按键数量、连接器类型，数量取预估数量；
     * 未选产品/样品：用产品描述作为产品名称兜底；
     * 长文本（尺寸/材料/线路/特殊要求）拼接进自定义要求；
     * 单价/金额默认0，待销售定价后修改。
     */
    private void createQuotationItemFromInquiry(SalesInquiry inquiry, Long quotationId) {
        SalesQuotationItem item = new SalesQuotationItem();
        item.setQuotationId(quotationId);

        // 产品信息：标准品关联产品库；样品用询价生成的编码/名称（2026-08-08 编码前置链路）
        if (inquiry.getProductId() != null) {
            Product product = productMapper.selectById(inquiry.getProductId());
            if (product != null) {
                item.setProductId(product.getProductId());
                item.setProductCode(product.getProductCode());
                item.setProductName(product.getProductName());
            }
        }
        // 样品/未选产品：优先用询价单的编码生成器结果（productCode/productName），否则描述兜底
        if (!StringUtils.hasText(item.getProductCode()) && StringUtils.hasText(inquiry.getProductCode())) {
            item.setProductCode(inquiry.getProductCode());
        }
        if (!StringUtils.hasText(item.getProductName())) {
            if (StringUtils.hasText(inquiry.getProductName())) {
                item.setProductName(inquiry.getProductName());
            } else {
                item.setProductName(truncate(inquiry.getProductDescription(), 200));
            }
        }

        // 技术参数结构化映射
        item.setKeyCount(inquiry.getKeyCount());
        if (StringUtils.hasText(inquiry.getConnectorRequirements())) {
            item.setConnectorType(truncate(inquiry.getConnectorRequirements(), 50));
        }

        // 数量与金额（DEV-937 2026-08-12：询价单有预估单价则继承，金额=单价×数量；否则单价待销售定价，默认0）
        item.setQuantity(inquiry.getExpectedQuantity() != null ? inquiry.getExpectedQuantity() : 1);
        item.setUnit("PCS");
        java.math.BigDecimal unitPrice = inquiry.getUnitPrice() != null ? inquiry.getUnitPrice() : java.math.BigDecimal.ZERO;
        item.setUnitPrice(unitPrice);
        item.setAmount(unitPrice.multiply(java.math.BigDecimal.valueOf(item.getQuantity())));
        item.setItemOrder(1);

        // 长文本要求拼接进自定义要求（500字符内）
        StringBuilder req = new StringBuilder();
        appendReq(req, "尺寸要求", inquiry.getSizeDescription());
        appendReq(req, "材料要求", inquiry.getMaterialRequirements());
        appendReq(req, "线路要求", inquiry.getCircuitRequirements());
        appendReq(req, "特殊要求", inquiry.getSpecialRequirements());
        if (req.length() > 0) {
            item.setCustomRequirements(truncate(req.toString(), 500));
        }

        quotationItemMapper.insert(item);
    }

    /**
     * 拼接要求字段：字段名：内容；多个字段以分号分隔
     */
    private void appendReq(StringBuilder sb, String label, String value) {
        if (StringUtils.hasText(value)) {
            if (sb.length() > 0) {
                sb.append("；");
            }
            sb.append(label).append("：").append(value.trim());
        }
    }

    /**
     * 截断字符串到指定长度
     */
    private String truncate(String value, int maxLen) {
        if (value == null) {
            return null;
        }
        String v = value.trim();
        return v.length() > maxLen ? v.substring(0, maxLen) : v;
    }

    /**
     * 导出询价单列表（DEV-591）
     */
    @Override
    public byte[] exportInquiryList(SalesInquiry inquiry) {
        List<SalesInquiry> list = selectInquiryList(inquiry);
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("询价单列表");
            String[] headers = {"询价单号", "客户名称", "联系人", "联系电话", "类型", "关联产品", "产品描述", "预估数量", "按键数量", "询价日期", "状态", "销售负责人", "创建时间"};
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }
            int r = 1;
            for (SalesInquiry q : list) {
                Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(q.getInquiryNo() == null ? "" : q.getInquiryNo());
                row.createCell(1).setCellValue(q.getCustomerName() == null ? "" : q.getCustomerName());
                row.createCell(2).setCellValue(q.getContactPerson() == null ? "" : q.getContactPerson());
                row.createCell(3).setCellValue(q.getContactPhone() == null ? "" : q.getContactPhone());
                row.createCell(4).setCellValue(q.getInquiryType() != null && q.getInquiryType() == 2 ? "样品" : "标准");
                row.createCell(5).setCellValue(q.getProductName() == null ? "" : q.getProductName());
                row.createCell(6).setCellValue(q.getProductDescription() == null ? "" : q.getProductDescription());
                row.createCell(7).setCellValue(q.getExpectedQuantity() == null ? 0 : q.getExpectedQuantity().doubleValue());
                row.createCell(8).setCellValue(q.getKeyCount() == null ? 0 : q.getKeyCount().doubleValue());
                row.createCell(9).setCellValue(q.getInquiryDate() == null ? "" : q.getInquiryDate().toString());
                row.createCell(10).setCellValue(inquiryStatusLabel(q.getInquiryStatus()));
                row.createCell(11).setCellValue(q.getSalesPersonName() == null ? "" : q.getSalesPersonName());
                row.createCell(12).setCellValue(q.getCreateTime() == null ? "" : q.getCreateTime().toString().replace('T', ' '));
            }
            for (int i = 0; i < headers.length; i++) {
                sheet.setColumnWidth(i, 16 * 256);
            }
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new BusinessException("导出失败: " + e.getMessage());
        }
    }

    /**
     * 询价单状态中文标签（导出用）
     */
    private String inquiryStatusLabel(Integer status) {
        if (status == null) return "";
        return switch (status) {
            case 0 -> "草稿";
            case 1 -> "待处理";
            case 2 -> "已发送";
            case 3 -> "已转报价";
            case 4 -> "已确认";
            case 5 -> "已拒绝";
            case 6 -> "已过期";
            default -> String.valueOf(status);
        };
    }

    /**
     * 获取询价单状态选项
     */
    @Override
    public List<Object> getStatusOptions() {
        List<Map<String, Object>> options = new ArrayList<>();

        // 💡 询价单是纯登记入口，不涉及复杂流转
        //    只有 草稿(0) 和 已转报价(3) 两个状态有实际逻辑
        //    pending/sent/accepted/rejected 为预留状态，暂不实现
        Object[][] statuses = {
            {InquiryStatus.DRAFT.getCode(), "草稿"},          // ✅ 新建时默认
            {InquiryStatus.PENDING.getCode(), "待处理"},      // 💤 预留
            {InquiryStatus.SENT.getCode(), "已发送"},         // 💤 预留
            {InquiryStatus.ACCEPTED.getCode(), "已确认"},     // 💤 预留
            {InquiryStatus.REJECTED.getCode(), "已拒绝"},     // 💤 预留
            {InquiryStatus.CONVERTED.getCode(), "已转报价"}   // ✅ 转报价时自动设置
        };

        for (Object[] s : statuses) {
            Map<String, Object> item = new HashMap<>();
            item.put("value", s[0]);
            item.put("label", s[1]);
            options.add(item);
        }

        return Collections.unmodifiableList(options);
    }
}
