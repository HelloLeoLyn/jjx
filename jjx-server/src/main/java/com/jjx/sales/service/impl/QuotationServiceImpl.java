package com.jjx.sales.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jjx.common.exception.BusinessException;
import com.jjx.sales.domain.entity.SalesQuotation;
import com.jjx.sales.domain.entity.SalesQuotationItem;
import com.jjx.sales.domain.entity.SalesQuotationFlow;
import com.jjx.sales.domain.entity.SalesInquiry;
import com.jjx.sales.mapper.QuotationMapper;
import com.jjx.sales.mapper.QuotationFlowMapper;
import com.jjx.sales.mapper.SalesQuotationItemMapper;
import com.jjx.sales.mapper.SalesInquiryMapper;
import com.jjx.product.mapper.ProductMapper;
import com.jjx.product.domain.entity.Product;
import com.jjx.common.core.page.PageResult;
import com.jjx.system.service.ISysAttachmentService;
import com.jjx.sales.domain.converter.SalesQuotationConverter;
import com.jjx.sales.domain.dto.SalesOrderAddDTO;
import com.jjx.sales.domain.dto.SalesQuotationAddDTO;
import com.jjx.sales.service.IOrderService;
import com.jjx.sales.service.IQuotationService;
import com.jjx.sales.domain.vo.SalesQuotationEditVO;
import com.jjx.system.service.OperLogChangeRecorder;
import com.jjx.system.utils.SecurityUtils;
import com.jjx.framework.common.RedisSequenceService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import com.jjx.common.utils.pdf.PdfDocBuilder;
import com.jjx.sales.enums.QuotationStatus;
import com.jjx.system.annotation.Event;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 销售报价单服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuotationServiceImpl implements IQuotationService {

    private final QuotationMapper quotationMapper;
    private final QuotationFlowMapper quotationFlowMapper;
    private final SalesQuotationItemMapper quotationItemMapper;
    private final SalesInquiryMapper salesInquiryMapper;
    private final ProductMapper productMapper;
    private final com.jjx.product.service.ProductCustomerValidator productCustomerValidator;
    private final com.jjx.product.service.IProductService productService;
    private final IOrderService orderService;
    private final com.jjx.sales.mapper.OrderMapper orderMapper;
    private final ISysAttachmentService sysAttachmentService;
    private final RedisSequenceService redisSequenceService;
    private final com.jjx.common.utils.pdf.PdfConfigLoader pdfConfigLoader;
    private final OperLogChangeRecorder changeRecorder;
    private final SalesQuotationConverter quotationConverter;
    /**
     * 查询销售报价单列表
     */
    @Override
    public List<SalesQuotation> selectQuotationList(SalesQuotation quotation) {
        LambdaQueryWrapper<SalesQuotation> wrapper = buildQueryWrapper(quotation);
        return quotationMapper.selectList(wrapper);
    }

    @Override
    public PageResult<SalesQuotation> selectQuotationPage(SalesQuotation quotation, Integer pageNum, Integer pageSize) {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<SalesQuotation> page =
            new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNum, pageSize);
        LambdaQueryWrapper<SalesQuotation> wrapper = buildQueryWrapper(quotation);
        com.baomidou.mybatisplus.core.metadata.IPage<SalesQuotation> result = quotationMapper.selectPage(page, wrapper);
        fillSourceInquiryNo(result.getRecords());
        fillConvertedOrder(result.getRecords());
        return com.jjx.common.core.page.PageResult.of(result, result.getRecords());
    }

    /**
     * 填充转成单信息（DEV：报价单列表展示订单类型——样品单/销售订单/未转）
     */
    private void fillConvertedOrder(List<SalesQuotation> quotations) {
        if (quotations == null || quotations.isEmpty()) {
            return;
        }
        java.util.List<Long> orderIds = quotations.stream()
                .map(SalesQuotation::getConvertedOrderId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(java.util.stream.Collectors.toList());
        if (orderIds.isEmpty()) {
            return;
        }
        try {
            java.util.Map<Long, com.jjx.sales.domain.entity.SalesOrder> map = orderMapper.selectBatchIds(orderIds).stream()
                    .collect(java.util.stream.Collectors.toMap(com.jjx.sales.domain.entity.SalesOrder::getOrderId, o -> o, (a, b) -> a));
            for (SalesQuotation q : quotations) {
                if (q.getConvertedOrderId() == null) {
                    continue;
                }
                com.jjx.sales.domain.entity.SalesOrder o = map.get(q.getConvertedOrderId());
                if (o != null) {
                    q.setConvertedOrderType(o.getOrderType());
                    q.setConvertedOrderNo(o.getOrderNo());
                }
            }
        } catch (Exception e) {
            log.warn("填充转成单信息失败: {}", e.getMessage());
        }
    }

    /**
     * 填充来源询价单号（DEV-590）：按 traceId 批量关联 sales_inquiry
     */
    private void fillSourceInquiryNo(List<SalesQuotation> quotations) {
        if (quotations == null || quotations.isEmpty()) {
            return;
        }
        List<String> traceIds = quotations.stream()
            .map(SalesQuotation::getTraceId)
            .filter(t -> t != null && !t.isEmpty())
            .distinct()
            .collect(Collectors.toList());
        if (traceIds.isEmpty()) {
            return;
        }
        List<SalesInquiry> inquiries = salesInquiryMapper.selectList(
            new LambdaQueryWrapper<SalesInquiry>()
                .in(SalesInquiry::getTraceId, traceIds)
                .eq(SalesInquiry::getDeleted, 0));
        Map<String, String> traceToInquiryNo = inquiries.stream()
            .filter(i -> i.getTraceId() != null)
            .collect(Collectors.toMap(SalesInquiry::getTraceId, SalesInquiry::getInquiryNo, (a, b) -> a));
        for (SalesQuotation q : quotations) {
            if (q.getTraceId() != null) {
                q.setSourceInquiryNo(traceToInquiryNo.get(q.getTraceId()));
            }
        }
    }

    /**
     * 构建查询条件
     */
    private LambdaQueryWrapper<SalesQuotation> buildQueryWrapper(SalesQuotation quotation) {
        LambdaQueryWrapper<SalesQuotation> wrapper = Wrappers.lambdaQuery();

        // 根据报价单号查询
        if (quotation.getQuotationNo() != null && !quotation.getQuotationNo().isEmpty()) {
            wrapper.like(SalesQuotation::getQuotationNo, quotation.getQuotationNo());
        }

        // 根据客户名称查询
        if (quotation.getCustomerName() != null && !quotation.getCustomerName().isEmpty()) {
            wrapper.like(SalesQuotation::getCustomerName, quotation.getCustomerName());
        }

        // 根据报价状态查询
        if (quotation.getQuotationStatus() != null) {
            wrapper.eq(SalesQuotation::getQuotationStatus, quotation.getQuotationStatus());
        }

        // 根据销售员查询
        if (quotation.getSalesPersonId() != null) {
            wrapper.eq(SalesQuotation::getSalesPersonId, quotation.getSalesPersonId());
        }

        // 按来源询价单号过滤（DEV-590补充：traceId 关联 sales_inquiry，参数化查询防注入）
        if (quotation.getInquiryNo() != null && !quotation.getInquiryNo().isEmpty()) {
            List<SalesInquiry> matched = salesInquiryMapper.selectList(
                    new LambdaQueryWrapper<SalesInquiry>()
                            .like(SalesInquiry::getInquiryNo, quotation.getInquiryNo())
                            .eq(SalesInquiry::getDeleted, 0));
            List<String> traceIds = matched.stream()
                    .map(SalesInquiry::getTraceId)
                    .filter(t -> t != null && !t.isEmpty())
                    .distinct()
                    .collect(Collectors.toList());
            if (traceIds.isEmpty()) {
                wrapper.eq(SalesQuotation::getQuotationId, -1L);
            } else {
                wrapper.in(SalesQuotation::getTraceId, traceIds);
            }
        }

        // 未删除的数据
        wrapper.eq(SalesQuotation::getDeleted, 0);

        // 按创建时间倒序排序
        wrapper.orderByDesc(SalesQuotation::getCreateTime).orderByDesc(SalesQuotation::getQuotationId);

        return wrapper;
    }

    /**
     * 根据ID查询销售报价单
     */
    @Override
    public SalesQuotation selectQuotationById(Long quotationId) {
        SalesQuotation quotation = quotationMapper.selectById(quotationId);
        if (quotation == null || quotation.getDeleted() == 1) {
            throw new BusinessException("报价单不存在或已被删除");
        }
        fillQuotationItems(quotation);
        return quotation;
    }

    /**
     * 填充报价单明细
     */
    private void fillQuotationItems(SalesQuotation quotation) {
        if (quotation == null) return;
        List<SalesQuotationItem> items = quotationItemMapper.selectList(
                new LambdaQueryWrapper<SalesQuotationItem>()
                        .eq(SalesQuotationItem::getQuotationId, quotation.getQuotationId())
                        .orderByAsc(SalesQuotationItem::getItemOrder)
        );
        quotation.setItems(items);
    }

    @Override
    public java.util.List<com.jjx.sales.domain.entity.SalesQuotationItem> getItems(Long quotationId) {
        return quotationItemMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.jjx.sales.domain.entity.SalesQuotationItem>()
                        .eq(com.jjx.sales.domain.entity.SalesQuotationItem::getQuotationId, quotationId)
                        .orderByAsc(com.jjx.sales.domain.entity.SalesQuotationItem::getItemOrder));
    }

    /**
     * 新增销售报价单
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertQuotation(SalesQuotationAddDTO dto) {

        SalesQuotation quotation = quotationConverter.toEntity(dto);
        validateCustomerProducts(quotation);
        // 销售负责人默认当前登录用户（2026-08-08）
        if (quotation.getSalesPersonId() == null) {
            quotation.setSalesPersonId(SecurityUtils.getUserId());
        }
        if (!org.apache.commons.lang3.StringUtils.isNotBlank(quotation.getSalesPersonName())) {
            String realName = SecurityUtils.getRealName();
            quotation.setSalesPersonName(realName != null && !realName.isBlank() ? realName : SecurityUtils.getUsername());
        }
        // 自动生成报价单号（未传入时，DEV-601修复：原逻辑只校验不生成，导致新增保存报错）
        if (quotation.getQuotationNo() == null || quotation.getQuotationNo().isEmpty()) {
            quotation.setQuotationNo(redisSequenceService.generateBusinessNumber("QT", "报价单号"));
        }
        // 检查报价单号是否唯一
        if (!checkQuotationNoUnique(quotation.getQuotationNo())) {
            throw new BusinessException("报价单号已存在");
        }

        // 自动生成链路追踪ID（未传入时）
        if (quotation.getTraceId() == null || quotation.getTraceId().isEmpty()) {
            quotation.setTraceId(java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        }

        // 设置默认值
        if (quotation.getQuotationStatus() == null) {
            quotation.setQuotationStatus(QuotationStatus.DRAFT.getCode()); // 草稿状态
        }

        // valid_until 为数据库必填字段。手工新增或其他调用方未传日期时，
        // 统一按报价日期起 30 天生成，避免 MyBatis-Plus 省略 null 字段导致插入失败。
        if (quotation.getQuotationDate() == null) {
            quotation.setQuotationDate(LocalDate.now());
        }
        if (quotation.getValidUntil() == null) {
            quotation.setValidUntil(quotation.getQuotationDate().plusDays(30));
        }

        if (quotation.getCurrency() == null || quotation.getCurrency().isEmpty()) {
            quotation.setCurrency("CNY");
        }

        if (quotation.getExchangeRate() == null) {
            quotation.setExchangeRate(java.math.BigDecimal.valueOf(1.0000));
        }

        if (quotation.getSubtotalAmount() == null) {
            quotation.setSubtotalAmount(java.math.BigDecimal.ZERO);
        }

        if (quotation.getTotalAmount() == null) {
            quotation.setTotalAmount(java.math.BigDecimal.ZERO);
        }

        if (quotation.getFinalAmount() == null) {
            quotation.setFinalAmount(java.math.BigDecimal.ZERO);
        }

        if (quotation.getTaxRate() == null) {
            quotation.setTaxRate(java.math.BigDecimal.ZERO);
        }

        if (quotation.getTaxAmount() == null) {
            quotation.setTaxAmount(java.math.BigDecimal.ZERO);
        }

        if (quotation.getDiscountAmount() == null) {
            quotation.setDiscountAmount(java.math.BigDecimal.ZERO);
        }

        int rows = quotationMapper.insert(quotation);
        // 样品报价：编码前置建档草稿产品（2026-08-08）
        ensureSampleDraftProducts(quotation);
        // 保存报价单明细（自动汇总金额：税率百分数÷100 算税额，total=subtotal+tax，final=total-折扣）
        saveQuotationItems(quotation.getQuotationId(), quotation.getItems(),
                quotation.getTaxRate(), quotation.getDiscountAmount());
        return rows;
    }

    /**
     * 样品报价建档草稿产品（2026-08-08）：明细有编码无产品 → 建档/复用，回填 productId
     */
    private void validateCustomerProducts(SalesQuotation quotation) {
        if (!java.lang.Integer.valueOf(1).equals(quotation.getQuotationType())
                || quotation.getItems() == null || quotation.getItems().isEmpty()) {
            return;
        }
        for (SalesQuotationItem item : quotation.getItems()) {
            productCustomerValidator.validateSelectable(item.getProductId(), quotation.getCustomerId());
        }
    }

    private void ensureSampleDraftProducts(SalesQuotation quotation) {
        if (!java.lang.Integer.valueOf(2).equals(quotation.getQuotationType())) return;
        if (quotation.getItems() == null) return;
        for (SalesQuotationItem item : quotation.getItems()) {
            if (item.getProductId() == null && item.getProductCode() != null && !item.getProductCode().isBlank()) {
                try {
                    Long pid = productService.ensureDraftProduct(
                            item.getProductCode(), item.getProductName(), item.getUnit(), "quotation",
                            quotation.getCustomerId());
                    item.setProductId(pid);
                } catch (Exception e) {
                    log.warn("样品报价建档草稿产品失败: code={}, err={}", item.getProductCode(), e.getMessage());
                }
            }
        }
    }

    /**
     * 修改销售报价单
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SalesQuotationEditVO updateQuotation(SalesQuotation quotation) {
        // 检查报价单是否存在
        SalesQuotation existingQuotation = selectQuotationById(quotation.getQuotationId());
        if (existingQuotation == null) {
            throw new BusinessException("报价单不存在");
        }
        validateCustomerProducts(quotation);

        // 检查报价单号是否唯一（排除自身）
        if (quotation.getQuotationNo() != null && !quotation.getQuotationNo().equals(existingQuotation.getQuotationNo())) {
            if (!checkQuotationNoUnique(quotation.getQuotationNo())) {
                throw new BusinessException("报价单号已存在");
            }
        }

        int rows = quotationMapper.updateById(quotation);
        // 样品报价：明细建档草稿产品（2026-08-08）
        ensureSampleDraftProducts(quotation);
        // DEV-1116：更新未携带明细时跳过"先删后插"，避免误删明细且表头不重算
        if (quotation.getItems() == null || quotation.getItems().isEmpty()) {
            log.info("报价单[{}]更新未携带明细，跳过明细重写", quotation.getQuotationId());
        } else {
            // 先删后插，更新报价单明细
            quotationItemMapper.delete(new LambdaQueryWrapper<SalesQuotationItem>()
                    .eq(SalesQuotationItem::getQuotationId, quotation.getQuotationId()));
            saveQuotationItems(quotation.getQuotationId(), quotation.getItems(),
                    quotation.getTaxRate(), quotation.getDiscountAmount());
        }

        SalesQuotation updatedQuotation = selectQuotationById(quotation.getQuotationId());
        List<String> changes = buildQuotationChanges(existingQuotation, updatedQuotation);
        SalesQuotationEditVO result = new SalesQuotationEditVO();
        result.setRows(rows);
        result.setDetailMessage(changeRecorder.toDetailJson(changes));
        result.setBizStatus(updatedQuotation.getQuotationStatus());
        result.setTraceId(updatedQuotation.getTraceId());
        return result;
    }

    List<String> buildQuotationChanges(SalesQuotation oldQuotation, SalesQuotation newQuotation) {
        List<String> changes = new ArrayList<>();
        changeRecorder.diff(changes, "报价类型", oldQuotation.getQuotationType(), newQuotation.getQuotationType());
        changeRecorder.diff(changes, "客户", oldQuotation.getCustomerName(), newQuotation.getCustomerName());
        changeRecorder.diff(changes, "联系人", oldQuotation.getContactPerson(), newQuotation.getContactPerson());
        changeRecorder.diff(changes, "联系电话", oldQuotation.getContactPhone(), newQuotation.getContactPhone());
        changeRecorder.diff(changes, "报价日期",
            changeRecorder.fmtDate(oldQuotation.getQuotationDate()),
            changeRecorder.fmtDate(newQuotation.getQuotationDate()));
        changeRecorder.diff(changes, "有效期",
            changeRecorder.fmtDate(oldQuotation.getValidUntil()),
            changeRecorder.fmtDate(newQuotation.getValidUntil()));
        changeRecorder.diff(changes, "币种", oldQuotation.getCurrency(), newQuotation.getCurrency());
        changeRecorder.diffDecimal(changes, "汇率", oldQuotation.getExchangeRate(), newQuotation.getExchangeRate());
        changeRecorder.diffDecimal(changes, "税率", oldQuotation.getTaxRate(), newQuotation.getTaxRate());
        changeRecorder.diffDecimal(changes, "折扣金额", oldQuotation.getDiscountAmount(), newQuotation.getDiscountAmount());
        changeRecorder.diff(changes, "备注", oldQuotation.getRemark(), newQuotation.getRemark());
        changeRecorder.diff(changes, "销售负责人", oldQuotation.getSalesPersonName(), newQuotation.getSalesPersonName());
        diffQuotationItems(changes, oldQuotation.getItems(), newQuotation.getItems());
        return changes;
    }

    private void diffQuotationItems(List<String> changes, List<SalesQuotationItem> oldItems,
                                    List<SalesQuotationItem> newItems) {
        Map<String, SalesQuotationItem> oldMap = indexQuotationItems(oldItems);
        Map<String, SalesQuotationItem> newMap = indexQuotationItems(newItems);
        Set<String> keys = new LinkedHashSet<>();
        keys.addAll(oldMap.keySet());
        keys.addAll(newMap.keySet());
        for (String key : keys) {
            SalesQuotationItem oldItem = oldMap.get(key);
            SalesQuotationItem newItem = newMap.get(key);
            if (oldItem == null) {
                changes.add("新增明细[" + itemLabel(newItem) + "]");
                continue;
            }
            if (newItem == null) {
                changes.add("删除明细[" + itemLabel(oldItem) + "]");
                continue;
            }
            String prefix = "明细[" + itemLabel(newItem) + "]";
            changeRecorder.diff(changes, prefix + "产品名称", oldItem.getProductName(), newItem.getProductName());
            changeRecorder.diff(changes, prefix + "数量", oldItem.getQuantity(), newItem.getQuantity());
            changeRecorder.diffDecimal(changes, prefix + "单价", oldItem.getUnitPrice(), newItem.getUnitPrice());
            changeRecorder.diff(changes, prefix + "单位", oldItem.getUnit(), newItem.getUnit());
            changeRecorder.diff(changes, prefix + "交期天数", oldItem.getDeliveryDays(), newItem.getDeliveryDays());
            changeRecorder.diff(changes, prefix + "预计交期",
                changeRecorder.fmtDate(oldItem.getEstimatedDeliveryDate()),
                changeRecorder.fmtDate(newItem.getEstimatedDeliveryDate()));
            changeRecorder.diff(changes, prefix + "自定义要求", oldItem.getCustomRequirements(), newItem.getCustomRequirements());
            changeRecorder.diff(changes, prefix + "Logo要求", oldItem.getLogoRequirement(), newItem.getLogoRequirement());
            changeRecorder.diff(changes, prefix + "认证要求", oldItem.getCertificationRequirement(), newItem.getCertificationRequirement());
            changeRecorder.diff(changes, prefix + "按键数", oldItem.getKeyCount(), newItem.getKeyCount());
            changeRecorder.diffDecimal(changes, prefix + "宽度", oldItem.getWidth(), newItem.getWidth());
            changeRecorder.diffDecimal(changes, prefix + "高度", oldItem.getHeight(), newItem.getHeight());
            changeRecorder.diffDecimal(changes, prefix + "厚度", oldItem.getThickness(), newItem.getThickness());
            changeRecorder.diff(changes, prefix + "材料", oldItem.getMaterialType(), newItem.getMaterialType());
            changeRecorder.diff(changes, prefix + "颜色", oldItem.getColor(), newItem.getColor());
            changeRecorder.diff(changes, prefix + "线路类型", oldItem.getCircuitType(), newItem.getCircuitType());
            changeRecorder.diff(changes, prefix + "连接器", oldItem.getConnectorType(), newItem.getConnectorType());
        }
    }

    private Map<String, SalesQuotationItem> indexQuotationItems(List<SalesQuotationItem> items) {
        Map<String, SalesQuotationItem> indexed = new LinkedHashMap<>();
        Map<String, Integer> occurrences = new HashMap<>();
        if (items == null) {
            return indexed;
        }
        for (SalesQuotationItem item : items) {
            String productCode = item.getProductCode() == null ? "" : item.getProductCode();
            int occurrence = occurrences.merge(productCode, 1, Integer::sum);
            indexed.put(productCode + "#" + occurrence, item);
        }
        return indexed;
    }

    private String itemLabel(SalesQuotationItem item) {
        if (item.getProductCode() != null && !item.getProductCode().isBlank()) {
            return item.getProductCode();
        }
        return item.getProductName() == null ? "未命名产品" : item.getProductName();
    }

    /**
     * 批量保存报价单明细（金额汇总参考销售订单口径：
     * subtotal=行合计；tax=subtotal×税率÷100（税率存百分数）；total=subtotal+tax（含税）；final=total-折扣）
     */
    private void saveQuotationItems(Long quotationId, List<SalesQuotationItem> items,
                                    BigDecimal taxRate, BigDecimal discountAmount) {
        if (quotationId == null || items == null || items.isEmpty()) {
            return;
        }
        for (SalesQuotationItem item : items) {
            item.setItemId(null);
            item.setQuotationId(quotationId);
            // 兑底：标准单明细有产品编码但缺 productId 时回填（前端漏传/旧数据）
            if (item.getProductId() == null && item.getProductCode() != null && !item.getProductCode().isEmpty()) {
                try {
                    Product matched = productMapper.selectOne(new LambdaQueryWrapper<Product>()
                            .eq(Product::getProductCode, item.getProductCode()).last("LIMIT 1"));
                    if (matched != null) {
                        item.setProductId(matched.getProductId());
                    }
                } catch (Exception e) {
                    log.warn("回填报价明细产品ID失败: {}", e.getMessage());
                }
            }
            if (item.getQuantity() == null) item.setQuantity(1);
            if (item.getUnitPrice() == null) item.setUnitPrice(java.math.BigDecimal.ZERO);
            // 自动计算行金额 = 数量 × 单价
            if (item.getAmount() == null) {
                item.setAmount(java.math.BigDecimal.valueOf(item.getQuantity().longValue())
                        .multiply(item.getUnitPrice()));
            }
            if (item.getUnit() == null) item.setUnit("PCS");
            if (item.getItemOrder() == null) item.setItemOrder(0);
            quotationItemMapper.insert(item);
        }
        // DEV-1116：统一走重算方法汇总表头金额（口径：subtotal=Σ行金额；tax=subtotal×税率%÷100；total=subtotal+tax；final=total-折扣≥0）
        recalcQuotationAmounts(quotationId);
    }

    /**
     * 按当前明细重算报价单表头金额（DEV-1116）
     * 提交审核/发送前兑底调用，保证明细有金额时表头金额一致，不再误报"报价金额必须大于0"
     */
    @Override
    public void recalcQuotationAmounts(Long quotationId) {
        if (quotationId == null) {
            return;
        }
        try {
            java.util.List<SalesQuotationItem> items = quotationItemMapper.selectList(
                    new LambdaQueryWrapper<SalesQuotationItem>()
                            .eq(SalesQuotationItem::getQuotationId, quotationId));
            java.math.BigDecimal subtotal = java.math.BigDecimal.ZERO;
            if (items != null) {
                for (SalesQuotationItem it : items) {
                    subtotal = subtotal.add(it.getAmount() != null ? it.getAmount() : java.math.BigDecimal.ZERO);
                }
            }
            SalesQuotation q = quotationMapper.selectById(quotationId);
            if (q == null) {
                return;
            }
            BigDecimal rate = q.getTaxRate() != null ? q.getTaxRate() : java.math.BigDecimal.ZERO;
            BigDecimal tax = subtotal.multiply(rate)
                    .divide(java.math.BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
            BigDecimal total = subtotal.add(tax); // 含税总价
            BigDecimal discount = q.getDiscountAmount() != null ? q.getDiscountAmount() : java.math.BigDecimal.ZERO;
            BigDecimal finalAmount = total.subtract(discount).max(java.math.BigDecimal.ZERO);

            SalesQuotation upd = new SalesQuotation();
            upd.setQuotationId(quotationId);
            upd.setSubtotalAmount(subtotal);
            upd.setTaxAmount(tax);
            upd.setTotalAmount(total);
            upd.setDiscountAmount(discount);
            upd.setFinalAmount(finalAmount);
            quotationMapper.updateById(upd);
        } catch (Exception e) {
            log.warn("重算报价单金额失败: {}", e.getMessage());
        }
    }

    /**
     * 删除销售报价单
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteQuotationById(Long quotationId) {
        SalesQuotation quotation = selectQuotationById(quotationId);
        if (quotation == null) {
            throw new BusinessException("报价单不存在");
        }

        // 检查报价单状态：已发送/待审核/已审核/已确认/已完成/改单中的报价单不能删除（DEV-594 补待审核/已审核）
        if (QuotationStatus.SENT.getCode().equals(quotation.getQuotationStatus())
                || QuotationStatus.PENDING_REVIEW.getCode().equals(quotation.getQuotationStatus())
                || QuotationStatus.APPROVED.getCode().equals(quotation.getQuotationStatus())
                || QuotationStatus.ACCEPTED.getCode().equals(quotation.getQuotationStatus())
                || QuotationStatus.COMPLETED.getCode().equals(quotation.getQuotationStatus())
                || QuotationStatus.MODIFYING.getCode().equals(quotation.getQuotationStatus())) {
            throw new BusinessException("已发送、待审核、已审核、已确认、已完成或改单中的报价单不能删除");
        }

        // 逻辑删除（MP @TableLogic：deleteById 自动 SET deleted=1 WHERE id AND deleted=0）
        // 注意：不能 setDeleted(1)+updateById —— MP 逻辑删除字段不参与 UPDATE SET，那样 deleted 不会变
        int rows = quotationMapper.deleteById(quotationId);

        // 连带清理：明细与流转记录（子表无 deleted 字段，物理删除）
        java.util.List<Long> deletedProductIds = new java.util.ArrayList<>();
        try {
            java.util.List<SalesQuotationItem> items = quotationItemMapper.selectList(
                    new LambdaQueryWrapper<SalesQuotationItem>().eq(SalesQuotationItem::getQuotationId, quotationId));
            for (SalesQuotationItem item : items) {
                if (item.getProductId() != null) deletedProductIds.add(item.getProductId());
            }
            quotationItemMapper.delete(new LambdaQueryWrapper<SalesQuotationItem>()
                    .eq(SalesQuotationItem::getQuotationId, quotationId));
            quotationFlowMapper.delete(new LambdaQueryWrapper<SalesQuotationFlow>()
                    .eq(SalesQuotationFlow::getQuotationId, quotationId));
            // 连带清理附件（记录+物理文件）
            sysAttachmentService.deleteAttachmentsByBiz("quotation", quotationId);
        } catch (Exception e) {
            // 子表清理失败不影响主表删除结果，记录日志
            log.warn("删除报价单子表数据失败: quotationId={}, {}", quotationId, e.getMessage());
        }
        // 作废联动：清理报价建档的草稿产品（2026-08-08）
        for (Long pid : deletedProductIds) {
            try {
                productService.cleanupDraftProduct(pid, "quotation");
            } catch (Exception e) {
                log.warn("清理报价草稿产品失败: productId={}, err={}", pid, e.getMessage());
            }
        }
        return rows;
    }

    /**
     * 批量删除销售报价单
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteQuotationByIds(Long[] quotationIds) {
        int count = 0;
        for (Long quotationId : quotationIds) {
            count += deleteQuotationById(quotationId);
        }
        return count;
    }

    /**
     * 检查报价单号是否存在
     */
    @Override
    public boolean checkQuotationNoUnique(String quotationNo) {
        LambdaQueryWrapper<SalesQuotation> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(SalesQuotation::getQuotationNo, quotationNo)
               .eq(SalesQuotation::getDeleted, 0);
        Long count = quotationMapper.selectCount(wrapper);
        return count == 0;
    }

    /**
     * 记录状态流转
     */
    private void recordFlow(SalesQuotation quotation, String actionCode, String actionName,
                            Integer fromStatus, Integer toStatus, String remark, String attachmentIds) {
        try {
            SalesQuotationFlow flow = new SalesQuotationFlow();
            flow.setQuotationId(quotation.getQuotationId());
            flow.setActionCode(actionCode);
            flow.setActionName(actionName);
            flow.setFromStatus(fromStatus);
            flow.setToStatus(toStatus);
            flow.setOperatorName("系统");
            flow.setRemark(remark);
            flow.setAttachmentIds(attachmentIds);
            flow.setCreateTime(LocalDateTime.now());
            quotationFlowMapper.insert(flow);
        } catch (Exception e) {
            log.error("记录报价单流转失败: quotationId={}, action={}, {}", quotation.getQuotationId(), actionCode, e.getMessage());
        }
    }

    /**
     * 更新报价单状态（客户确认/拒绝，兼容旧调用）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateQuotationStatus(Long quotationId, Integer status, String attachmentIds) {
        if (status != null && status == 2) {
            return confirmQuotation(quotationId, attachmentIds);
        }
        if (status != null && status == 3) {
            return rejectQuotation(quotationId, attachmentIds);
        }
        SalesQuotation quotation = selectQuotationById(quotationId);
        if (quotation == null) {
            throw new BusinessException("报价单不存在");
        }
        validateStatusTransition(quotation.getQuotationStatus(), status);
        Integer from = quotation.getQuotationStatus();
        quotation.setQuotationStatus(status);
        int rows = quotationMapper.updateById(quotation);
        recordFlow(quotation, "STATUS_CHANGE", "状态变更", from, status, null, attachmentIds);
        return rows;
    }

    /**
     * 客户确认报价
     */
    @Override
    @Event(value = "quotation.confirmed", bizId = "#quotationId", bizType = "'quotation'")
    @Transactional(rollbackFor = Exception.class)
    public int confirmQuotation(Long quotationId, String attachmentIds) {
        return changeToStatus(quotationId, QuotationStatus.ACCEPTED.getCode(), "CUSTOMER_CONFIRM", "客户确认报价", attachmentIds);
    }

    /**
     * 客户拒绝报价
     */
    @Override
    @Event(value = "quotation.rejected", bizId = "#quotationId", bizType = "'quotation'")
    @Transactional(rollbackFor = Exception.class)
    public int rejectQuotation(Long quotationId, String attachmentIds) {
        return changeToStatus(quotationId, QuotationStatus.REJECTED.getCode(), "CUSTOMER_REJECT", "客户拒绝报价", attachmentIds);
    }

    /**
     * 已完成报价单改单（回到改单状态，可重新编辑流转）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int modifyQuotation(Long quotationId, String attachmentIds) {
        SalesQuotation quotation = selectQuotationById(quotationId);
        if (quotation == null) {
            throw new BusinessException("报价单不存在");
        }
        // 只有已完成状态可以改单
        if (!QuotationStatus.COMPLETED.getCode().equals(quotation.getQuotationStatus())) {
            throw new BusinessException("只有已完成的报价单可以改单");
        }
        Integer from = quotation.getQuotationStatus();
        quotation.setQuotationStatus(QuotationStatus.MODIFYING.getCode());
        int rows = quotationMapper.updateById(quotation);
        recordFlow(quotation, "MODIFY", "改单", from, QuotationStatus.MODIFYING.getCode(), null, attachmentIds);
        return rows;
    }

    /**
     * 通用状态变更 + 流转记录
     */
    private int changeToStatus(Long quotationId, Integer targetStatus, String actionCode, String actionName, String attachmentIds) {
        SalesQuotation quotation = selectQuotationById(quotationId);
        if (quotation == null) {
            throw new BusinessException("报价单不存在");
        }
        validateStatusTransition(quotation.getQuotationStatus(), targetStatus);
        Integer from = quotation.getQuotationStatus();
        quotation.setQuotationStatus(targetStatus);
        int rows = quotationMapper.updateById(quotation);
        recordFlow(quotation, actionCode, actionName, from, targetStatus, null, attachmentIds);
        return rows;
    }

    /**
     * 发送报价单给客户
     */
    @Override
    @Event(value = "quotation.sent", bizId = "#quotationId", bizType = "'quotation'")
    @Transactional(rollbackFor = Exception.class)
    public int sendQuotation(Long quotationId, String attachmentIds) {
        SalesQuotation quotation = selectQuotationById(quotationId);
        if (quotation == null) {
            throw new BusinessException("报价单不存在");
        }

        // 只有审核通过(6)状态的报价单可以发送（规则：仅审核通过的报价单才能上传/发送报价）
        if (!QuotationStatus.APPROVED.getCode().equals(quotation.getQuotationStatus())) {
            throw new BusinessException("只有审核通过的报价单可以发送");
        }

        // DEV-1116：发送前按当前明细兑底重算表头金额后重新读取，再校验
        recalcQuotationAmounts(quotationId);
        quotation = selectQuotationById(quotationId);

        // 发送前校验报价单信息完整性（客户/日期/金额，与提交审核一致）
        validateQuotationForReview(quotation);

        // 更新发送信息
        Integer from = quotation.getQuotationStatus();
        quotation.setQuotationStatus(QuotationStatus.SENT.getCode());
        quotation.setSendTime(LocalDateTime.now());
        quotation.setSendMethod("email"); // 默认邮件发送

        int rows = quotationMapper.updateById(quotation);
        recordFlow(quotation, "SEND", "发送报价", from, QuotationStatus.SENT.getCode(), null, attachmentIds);
        return rows;
    }

    /**
     * 报价单转为订单
     */
    @Override
    @Event(value = "quotation.converted", bizId = "#quotationId", bizType = "'quotation'")
    @Transactional(rollbackFor = Exception.class)
    public Object convertToOrder(Long quotationId) {
        SalesQuotation quotation = selectQuotationById(quotationId);
        if (quotation == null) {
            throw new BusinessException("报价单不存在");
        }

        // 只有已接受的报价单可以转为订单
        if (!QuotationStatus.ACCEPTED.getCode().equals(quotation.getQuotationStatus())) {
            throw new BusinessException("只有已接受的报价单可以转为订单");
        }

        // 从报价单创建订单
        SalesOrderAddDTO orderDTO = new SalesOrderAddDTO();
        orderDTO.setOrderNo(orderService.generateOrderNo());
        orderDTO.setCustomerId(quotation.getCustomerId());
        orderDTO.setCustomerName(quotation.getCustomerName());
        orderDTO.setOrderDate(new java.util.Date());
        orderDTO.setOrderType(1);
        orderDTO.setRemark("由报价单[" + quotation.getQuotationNo() + "]转换");
        // 记录来源报价单（正向引用）
        orderDTO.setQuotationId(quotationId);
        // 透传链路追踪ID
        orderDTO.setTraceId(quotation.getTraceId());
        // 金额信息传递（报价单→订单，税率百分数÷100 换算成订单小数口径，税/折扣继承报价）
        if (quotation.getSubtotalAmount() != null) orderDTO.setTotalAmount(quotation.getSubtotalAmount());
        if (quotation.getTaxRate() != null) orderDTO.setTaxRate(quotation.getTaxRate().divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
        if (quotation.getTaxAmount() != null) orderDTO.setTaxAmount(quotation.getTaxAmount());
        if (quotation.getDiscountAmount() != null) orderDTO.setDiscountAmount(quotation.getDiscountAmount());
        // 币种/汇率透传（DEV-605：报价单选外币转订单时币种丢失，订单金额仍为 CNY 口径，币种/汇率仅记录溯源）
        if (quotation.getCurrency() != null) orderDTO.setCurrency(quotation.getCurrency());
        if (quotation.getExchangeRate() != null) orderDTO.setExchangeRate(quotation.getExchangeRate());

        // 报价单明细 → 订单明细
        List<SalesQuotationItem> quotationItems = quotationItemMapper.selectList(
                new LambdaQueryWrapper<SalesQuotationItem>().eq(SalesQuotationItem::getQuotationId, quotationId));
        if (quotationItems != null && !quotationItems.isEmpty()) {
            List<com.jjx.sales.domain.dto.SalesOrderProductDTO> items = new ArrayList<>();
            for (SalesQuotationItem qi : quotationItems) {
                com.jjx.sales.domain.dto.SalesOrderProductDTO item = new com.jjx.sales.domain.dto.SalesOrderProductDTO();
                item.setProductId(qi.getProductId());
                item.setProductCode(qi.getProductCode());
                item.setProductName(qi.getProductName());
                item.setQuantity(qi.getQuantity());
                item.setUnitPrice(qi.getUnitPrice());
                item.setUnit(qi.getUnit());
                item.setAmount(qi.getAmount());
                item.setRemark(qi.getCustomRequirements());
                items.add(item);
            }
            orderDTO.setItems(items);
        }

        Long orderId = orderService.insertOrder(orderDTO);

        // 转订单成功后：报价单状态改为已完成(9)，并回写转换结果（反向引用）
        Integer from = quotation.getQuotationStatus();
        quotation.setQuotationStatus(QuotationStatus.COMPLETED.getCode());
        quotation.setConvertedOrderId(orderId);
        quotation.setConvertTime(LocalDateTime.now());
        quotationMapper.updateById(quotation);
        recordFlow(quotation, "CONVERT_ORDER", "转订单完成", from, QuotationStatus.COMPLETED.getCode(), "转为销售订单，订单号:" + orderId, null);

        log.info("报价单{}已转为订单: orderId={}", quotationId, orderId);
        return orderId;
    }

    /**
     * 导出报价单PDF
     */
    @Override
    public byte[] exportPdf(Long quotationId) {
        SalesQuotation quotation = selectQuotationById(quotationId);
        if (quotation == null) {
            throw new BusinessException("报价单不存在");
        }
        List<SalesQuotationItem> items = quotationItemMapper.selectList(
                new LambdaQueryWrapper<SalesQuotationItem>().eq(SalesQuotationItem::getQuotationId, quotationId));
        DecimalFormat df = new DecimalFormat("#,##0.00");

        Map<String, String> info = new LinkedHashMap<>();
        info.put("报价单号", quotation.getQuotationNo());
        info.put("报价日期", quotation.getQuotationDate() == null ? "" : quotation.getQuotationDate().toString());
        info.put("客户名称", quotation.getCustomerName());
        info.put("有效期至", quotation.getValidUntil() == null ? "" : quotation.getValidUntil().toString());
        info.put("联系人", joinContact(quotation.getContactPerson(), quotation.getContactPhone()));
        info.put("币种", buildCurrency(quotation));
        info.put("来源询价", quotation.getInquiryNo() == null ? "-" : quotation.getInquiryNo());
        info.put("销售负责人", quotation.getSalesPersonName() == null ? "-" : quotation.getSalesPersonName());

        java.util.List<String[]> rows = new ArrayList<>();
        for (SalesQuotationItem item : items) {
            rows.add(new String[]{
                    String.valueOf(rows.size() + 1),
                    item.getProductCode(),
                    buildItemSpec(item),
                    item.getQuantity() == null ? "" : String.valueOf(item.getQuantity()),
                    item.getUnit(),
                    item.getUnitPrice() == null ? "" : df.format(item.getUnitPrice()),
                    item.getAmount() == null ? "" : df.format(item.getAmount()),
            });
        }

        PdfDocBuilder builder = PdfDocBuilder.create()
                .withConfig(pdfConfigLoader.load())
                .title("报  价  单")
                .info(info)
                .items(new String[]{"序号", "产品编码", "产品名称/规格", "数量", "单位", "单价", "金额"}, rows)
                .amounts(new String[][]{
                        {"小计", fmt(quotation.getSubtotalAmount(), df)},
                        {"税率(%)", quotation.getTaxRate() == null ? "" : df.format(quotation.getTaxRate())},
                        {"税额", fmt(quotation.getTaxAmount(), df)},
                        {"折扣", fmt(quotation.getDiscountAmount(), df)},
                        {"合计", fmt(quotation.getFinalAmount(), df)},
                })
                .remark(quotation.getRemark())
                .signatures("销售负责人：" + (quotation.getSalesPersonName() == null ? "" : quotation.getSalesPersonName()),
                        "客户确认：", "日期：");
        return builder.toBytes();
    }

    @Override
    public byte[] exportExcel(Long quotationId) {
        SalesQuotation quotation = selectQuotationById(quotationId);
        if (quotation == null) {
            throw new BusinessException("报价单不存在");
        }
        List<SalesQuotationItem> items = quotationItemMapper.selectList(
                new LambdaQueryWrapper<SalesQuotationItem>().eq(SalesQuotationItem::getQuotationId, quotationId));
        return buildQuotationExcel(quotation, items);
    }

    /** 报价单 PDF 变量（占位符 → 值，行内容拼装） */

    /** 报价单 Excel（单张表单） */
    private byte[] buildQuotationExcel(SalesQuotation q, List<SalesQuotationItem> items) {
        DecimalFormat df = new DecimalFormat("#,##0.00");
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("报价单");
            int r = 0;
            // 标题
            Row title = sheet.createRow(r++);
            title.createCell(0).setCellValue("报价单 " + (q.getQuotationNo() == null ? "" : q.getQuotationNo()));
            // 单据信息（两列）
            String[][] info = {
                    {"客户名称", q.getCustomerName()}, {"报价日期", String.valueOf(q.getQuotationDate() == null ? "" : q.getQuotationDate())},
                    {"联系人", joinContact(q.getContactPerson(), q.getContactPhone())}, {"有效期至", String.valueOf(q.getValidUntil() == null ? "" : q.getValidUntil())},
                    {"币种", buildCurrency(q)}, {"来源询价", q.getInquiryNo() == null ? "-" : q.getInquiryNo()},
                    {"销售负责人", q.getSalesPersonName() == null ? "-" : q.getSalesPersonName()}, {"备注", q.getRemark() == null ? "" : q.getRemark()},
            };
            for (int i = 0; i < info.length; i += 2) {
                Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(info[i][0]);
                row.createCell(1).setCellValue(safe(info[i][1]));
                row.createCell(3).setCellValue(info[i + 1][0]);
                row.createCell(4).setCellValue(safe(info[i + 1][1]));
            }
            r++;
            // 明细表头
            String[] headers = {"序号", "产品编码", "产品名称/规格", "数量", "单位", "单价", "金额"};
            Row head = sheet.createRow(r++);
            for (int i = 0; i < headers.length; i++) {
                head.createCell(i).setCellValue(headers[i]);
            }
            // 明细行
            int idx = 0;
            for (SalesQuotationItem item : items) {
                Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(++idx);
                row.createCell(1).setCellValue(safe(item.getProductCode()));
                row.createCell(2).setCellValue(buildItemSpec(item));
                row.createCell(3).setCellValue(item.getQuantity() == null ? 0 : item.getQuantity().doubleValue());
                row.createCell(4).setCellValue(safe(item.getUnit()));
                row.createCell(5).setCellValue(item.getUnitPrice() == null ? 0 : item.getUnitPrice().doubleValue());
                row.createCell(6).setCellValue(item.getAmount() == null ? 0 : item.getAmount().doubleValue());
            }
            r++;
            // 汇总
            String[][] sums = {
                    {"小计", fmt(q.getSubtotalAmount(), df)}, {"税率(%)", q.getTaxRate() == null ? "" : df.format(q.getTaxRate())},
                    {"税额", fmt(q.getTaxAmount(), df)}, {"折扣", fmt(q.getDiscountAmount(), df)},
                    {"合计", fmt(q.getFinalAmount(), df)},
            };
            for (String[] s : sums) {
                Row row = sheet.createRow(r++);
                row.createCell(5).setCellValue(s[0]);
                row.createCell(6).setCellValue(s[1]);
            }
            // 列宽
            int[] widths = {6, 14, 36, 10, 8, 14, 16};
            for (int i = 0; i < widths.length; i++) {
                sheet.setColumnWidth(i, widths[i] * 256);
            }
            wb.write(os);
            return os.toByteArray();
        } catch (Exception e) {
            throw new BusinessException("报价单Excel生成失败: " + e.getMessage());
        }
    }

    /** 明细规格描述：尺寸×厚度+材质/颜色/线路/连接器/logo/认证（非空拼接） */
    private String buildItemSpec(SalesQuotationItem item) {
        StringBuilder sb = new StringBuilder();
        if (item.getProductName() != null && !item.getProductName().isBlank()) {
            sb.append(item.getProductName());
        }
        StringBuilder spec = new StringBuilder();
        if (item.getWidth() != null && item.getHeight() != null) {
            spec.append(item.getWidth().stripTrailingZeros().toPlainString()).append("×")
                .append(item.getHeight().stripTrailingZeros().toPlainString());
            if (item.getThickness() != null) {
                spec.append("×").append(item.getThickness().stripTrailingZeros().toPlainString());
            }
        }
        appendNonEmpty(spec, item.getMaterialType());
        appendNonEmpty(spec, item.getColor());
        appendNonEmpty(spec, item.getCircuitType());
        appendNonEmpty(spec, item.getConnectorType());
        if (spec.length() > 0) {
            if (sb.length() > 0) {
                sb.append(" / ");
            }
            sb.append(spec);
        }
        if (item.getCustomRequirements() != null && !item.getCustomRequirements().isBlank()) {
            sb.append("\n备注:").append(item.getCustomRequirements());
        }
        return sb.toString();
    }

    private void appendNonEmpty(StringBuilder sb, String v) {
        if (v != null && !v.isBlank()) {
            if (sb.length() > 0) {
                sb.append(" / ");
            }
            sb.append(v);
        }
    }

    private String joinContact(String person, String phone) {
        if (person == null || person.isBlank()) {
            return phone == null ? "" : phone;
        }
        return phone == null || phone.isBlank() ? person : person + " " + phone;
    }

    private String buildCurrency(SalesQuotation q) {
        if (q.getCurrency() == null) {
            return "CNY";
        }
        if (q.getExchangeRate() != null && q.getExchangeRate().compareTo(BigDecimal.ONE) != 0) {
            return q.getCurrency() + " (汇率 " + q.getExchangeRate().stripTrailingZeros().toPlainString() + ")";
        }
        return q.getCurrency();
    }

    private String fmt(BigDecimal v, DecimalFormat df) {
        return v == null ? "" : df.format(v);
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }


    /**
     * 复制报价单
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SalesQuotation copyQuotation(Long quotationId) {
        SalesQuotation original = selectQuotationById(quotationId);
        if (original == null) {
            throw new BusinessException("报价单不存在");
        }

        // 创建副本
        SalesQuotation copy = new SalesQuotation();
        copy.setQuotationNo("COPY_" + original.getQuotationNo());
        copy.setCustomerId(original.getCustomerId());
        copy.setCustomerName(original.getCustomerName());
        copy.setContactPerson(original.getContactPerson());
        copy.setContactPhone(original.getContactPhone());
        copy.setQuotationDate(original.getQuotationDate());
        copy.setValidUntil(original.getValidUntil());
        copy.setCurrency(original.getCurrency());
        copy.setExchangeRate(original.getExchangeRate());
        copy.setQuotationStatus(QuotationStatus.DRAFT.getCode());
        copy.setSubtotalAmount(original.getSubtotalAmount());
        copy.setTaxRate(original.getTaxRate());
        copy.setTaxAmount(original.getTaxAmount());
        copy.setTotalAmount(original.getTotalAmount());
        copy.setDiscountAmount(original.getDiscountAmount());
        copy.setFinalAmount(original.getFinalAmount());
        copy.setRemark("复制自报价单：" + original.getQuotationNo() + "\n" + original.getRemark());
        copy.setSalesPersonId(original.getSalesPersonId());
        copy.setSalesPersonName(original.getSalesPersonName());

        // 保存副本
        this.quotationMapper.insert(copy);
        return copy;
    }


    /**
     * 提交审核（DEV-1116：校验前按当前明细兑底重算表头金额，避免明细有金额但表头未汇总误报）
     */
    @Override
    @Event(value = "quotation.submitted", bizId = "#quotationId", bizType = "'quotation'")
    @Transactional(rollbackFor = Exception.class)
    public int submitReview(Long quotationId, String attachmentIds) {
        SalesQuotation quotation = selectQuotationById(quotationId);
        if (quotation == null) {
            throw new BusinessException("报价单不存在");
        }

        // 草稿(0)或改单(8)状态的报价单可以提交审核（改单后重新流转）
        if (!QuotationStatus.DRAFT.getCode().equals(quotation.getQuotationStatus())
                && !QuotationStatus.MODIFYING.getCode().equals(quotation.getQuotationStatus())) {
            throw new BusinessException("只有草稿或改单状态的报价单可以提交审核");
        }

        // DEV-1116：按当前明细兑底重算表头金额后重新读取，再校验
        recalcQuotationAmounts(quotationId);
        quotation = selectQuotationById(quotationId);

        // 检查报价单信息是否完整
        validateQuotationForReview(quotation);

        // 更新状态为待审核
        Integer from = quotation.getQuotationStatus();
        quotation.setQuotationStatus(QuotationStatus.PENDING_REVIEW.getCode());
        int rows = quotationMapper.updateById(quotation);
        recordFlow(quotation, "SUBMIT_REVIEW", "提交审核", from, QuotationStatus.PENDING_REVIEW.getCode(), null, attachmentIds);
        return rows;
    }

    /**
     * 审核报价单
     */
    @Override
    @Event(value = "quotation.reviewed", bizId = "#quotationId", bizType = "'quotation'")
    @Transactional(rollbackFor = Exception.class)
    public int reviewQuotation(Long quotationId, Boolean approved, String remark, String attachmentIds) {
        SalesQuotation quotation = selectQuotationById(quotationId);
        if (quotation == null) {
            throw new BusinessException("报价单不存在");
        }

        // 只有待审核状态的报价单可以审核
        if (!QuotationStatus.PENDING_REVIEW.getCode().equals(quotation.getQuotationStatus())) {
            throw new BusinessException("只有待审核状态的报价单可以审核");
        }

        // 更新审核信息（从当前登录用户获取，不再硬编码）
        quotation.setApproverId(SecurityUtils.getUserId());
        quotation.setApproverName(SecurityUtils.getUsername());
        quotation.setApproveTime(LocalDateTime.now());
        quotation.setApproveRemark(remark);

        Integer from = quotation.getQuotationStatus();
        if (approved) {
            quotation.setQuotationStatus(QuotationStatus.APPROVED.getCode());
        } else {
            quotation.setQuotationStatus(QuotationStatus.REJECTED.getCode());
        }

        int rows = quotationMapper.updateById(quotation);
        recordFlow(quotation, approved ? "APPROVE" : "REJECT", approved ? "审核通过" : "审核驳回",
                from, quotation.getQuotationStatus(), remark, attachmentIds);
        return rows;
    }

    /**
     * 查询报价单流转记录
     */
    @Override
    public List<SalesQuotationFlow> selectFlowRecords(Long quotationId) {
        return quotationFlowMapper.selectByQuotationId(quotationId);
    }

    /**
     * 导出报价单列表
     */
    @Override
    public byte[] exportQuotationList(SalesQuotation quotation) {
        List<SalesQuotation> list = selectQuotationList(quotation);
        fillSourceInquiryNo(list);
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("报价单列表");
            String[] headers = {"报价单号", "来源询价单", "客户名称", "报价日期", "有效期至", "状态", "币种", "总金额", "销售员", "创建时间"};
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }
            int r = 1;
            for (SalesQuotation q : list) {
                Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(q.getQuotationNo() == null ? "" : q.getQuotationNo());
                row.createCell(1).setCellValue(q.getSourceInquiryNo() == null ? "" : q.getSourceInquiryNo());
                row.createCell(2).setCellValue(q.getCustomerName() == null ? "" : q.getCustomerName());
                row.createCell(3).setCellValue(q.getQuotationDate() == null ? "" : q.getQuotationDate().toString());
                row.createCell(4).setCellValue(q.getValidUntil() == null ? "" : q.getValidUntil().toString());
                row.createCell(5).setCellValue(quotationStatusLabel(q.getQuotationStatus()));
                row.createCell(6).setCellValue(q.getCurrency() == null ? "" : q.getCurrency());
                row.createCell(7).setCellValue(q.getTotalAmount() == null ? 0d : q.getTotalAmount().doubleValue());
                row.createCell(8).setCellValue(q.getSalesPersonName() == null ? "" : q.getSalesPersonName());
                row.createCell(9).setCellValue(q.getCreateTime() == null ? "" : q.getCreateTime().toString().replace('T', ' '));
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
     * 报价单状态中文标签（导出用）
     */
    private String quotationStatusLabel(Integer status) {
        if (status == null) return "";
        return switch (status) {
            case 0 -> "草稿";
            case 1 -> "已发送";
            case 2 -> "已确认";
            case 3 -> "已拒绝";
            case 4 -> "已过期";
            case 5 -> "待审核";
            case 6 -> "已审核";
            case 8 -> "改单";
            case 9 -> "已完成";
            default -> String.valueOf(status);
        };
    }

    /**
     * 获取报价单状态选项
     */
    @Override
    public List<Object> getStatusOptions() {
        List<Object> options = new ArrayList<>();
        options.add(new Object() {
            public final Integer value = QuotationStatus.DRAFT.getCode();
            public final String label = "草稿";
        });
        options.add(new Object() {
            public final Integer value = QuotationStatus.SENT.getCode();
            public final String label = "已发送";
        });
        options.add(new Object() {
            public final Integer value = QuotationStatus.ACCEPTED.getCode();
            public final String label = "已接受";
        });
        options.add(new Object() {
            public final Integer value = QuotationStatus.REJECTED.getCode();
            public final String label = "已拒绝";
        });
        options.add(new Object() {
            public final Integer value = QuotationStatus.EXPIRED.getCode();
            public final String label = "已过期";
        });
        options.add(new Object() {
            public final Integer value = QuotationStatus.PENDING_REVIEW.getCode();
            public final String label = "待审核";
        });
        options.add(new Object() {
            public final Integer value = QuotationStatus.APPROVED.getCode();
            public final String label = "已审核";
        });
        return options;
    }

    /**
     * 获取币种选项
     */
    @Override
    public List<Object> getCurrencyOptions() {
        List<Object> options = new ArrayList<>();
        options.add(new Object() {
            public final String value = "CNY";
            public final String label = "人民币";
        });
        options.add(new Object() {
            public final String value = "USD";
            public final String label = "美元";
        });
        options.add(new Object() {
            public final String value = "EUR";
            public final String label = "欧元";
        });
        options.add(new Object() {
            public final String value = "JPY";
            public final String label = "日元";
        });
        options.add(new Object() {
            public final String value = "HKD";
            public final String label = "港币";
        });
        return options;
    }

    /**
     * 获取客户历史报价
     */
    @Override
    public List<SalesQuotation> getCustomerHistory(Long customerId) {
        LambdaQueryWrapper<SalesQuotation> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(SalesQuotation::getCustomerId, customerId)
               .eq(SalesQuotation::getDeleted, 0)
               .orderByDesc(SalesQuotation::getCreateTime);
        return quotationMapper.selectList(wrapper);
    }

    /**
     * 获取报价单统计信息
     */
    @Override
    public Object getQuotationStatistics() {
        // 暂时返回空对象
        Map<String, Object> stats = new HashMap<>();
        List<SalesQuotation> all = quotationMapper.selectList(Wrappers.emptyWrapper());
        stats.put("totalCount", (long) all.size());
        stats.put("totalAmount", all.stream().filter(q -> q.getTotalAmount() != null).mapToDouble(q -> q.getTotalAmount().doubleValue()).sum());
        long draftCount = all.stream().filter(q -> QuotationStatus.DRAFT.getCode().equals(q.getQuotationStatus())).count();
        long sentCount = all.stream().filter(q -> QuotationStatus.SENT.getCode().equals(q.getQuotationStatus())).count();
        long acceptedCount = all.stream().filter(q -> QuotationStatus.ACCEPTED.getCode().equals(q.getQuotationStatus())).count();
        long rejectedCount = all.stream().filter(q -> QuotationStatus.REJECTED.getCode().equals(q.getQuotationStatus())).count();
        long expiredCount = all.stream().filter(q -> QuotationStatus.EXPIRED.getCode().equals(q.getQuotationStatus())).count();
        stats.put("draftCount", draftCount);
        stats.put("sentCount", sentCount);
        stats.put("acceptedCount", acceptedCount);
        stats.put("rejectedCount", rejectedCount);
        stats.put("expiredCount", expiredCount);
        return stats;
    }

    /**
     * 验证状态转换是否合法
     */
    private static void validateStatusTransition(Integer currentStatus, Integer newStatus) {
        // 状态转换规则：
        // draft -> pending_review, sent
        // pending_review -> approved, rejected
        // approved -> sent
        // sent -> accepted, rejected, expired
        // accepted -> completed (转订单) / modifying (改单)
        // completed -> modifying (改单)
        // modifying -> draft (编辑后重新提交)
        // rejected, expired -> draft (重新报价：原单状态流转复活)

        if (QuotationStatus.DRAFT.getCode().equals(currentStatus) && (QuotationStatus.PENDING_REVIEW.getCode().equals(newStatus) || QuotationStatus.SENT.getCode().equals(newStatus))) {
            return;
        }

        if (QuotationStatus.PENDING_REVIEW.getCode().equals(currentStatus) && (QuotationStatus.APPROVED.getCode().equals(newStatus) || QuotationStatus.REJECTED.getCode().equals(newStatus))) {
            return;
        }

        if (QuotationStatus.APPROVED.getCode().equals(currentStatus) && QuotationStatus.SENT.getCode().equals(newStatus)) {
            return;
        }

        if (QuotationStatus.SENT.getCode().equals(currentStatus) && (QuotationStatus.ACCEPTED.getCode().equals(newStatus) || QuotationStatus.REJECTED.getCode().equals(newStatus) || QuotationStatus.EXPIRED.getCode().equals(newStatus))) {
            return;
        }

        if (QuotationStatus.ACCEPTED.getCode().equals(currentStatus) && QuotationStatus.COMPLETED.getCode().equals(newStatus)) {
            return;
        }

        if (QuotationStatus.COMPLETED.getCode().equals(currentStatus) && QuotationStatus.MODIFYING.getCode().equals(newStatus)) {
            return;
        }

        if (QuotationStatus.MODIFYING.getCode().equals(currentStatus) && QuotationStatus.DRAFT.getCode().equals(newStatus)) {
            return;
        }

        // 重新报价：已拒绝/已过期 → 草稿（原单状态流转，保留单号与历史）
        if ((QuotationStatus.REJECTED.getCode().equals(currentStatus) || QuotationStatus.EXPIRED.getCode().equals(currentStatus))
                && QuotationStatus.DRAFT.getCode().equals(newStatus)) {
            return;
        }

        throw new BusinessException("状态转换不合法：从状态" + currentStatus + "转换到状态" + newStatus);
    }

    /**
     * 验证报价单信息是否完整（提交审核/发送报价共用）
     */
    private void validateQuotationForReview(SalesQuotation quotation) {
        if (quotation.getCustomerId() == null) {
            throw new BusinessException("客户信息不能为空");
        }

        if (quotation.getQuotationDate() == null) {
            throw new BusinessException("报价日期不能为空");
        }

        // DEV-1116：报错信息区分"明细已有金额但表头未汇总"（提示保存）与"明细本身无金额"（提示定价），避免误导
        if (quotation.getTotalAmount() == null || quotation.getTotalAmount().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            if (sumItemAmounts(quotation.getQuotationId()).compareTo(java.math.BigDecimal.ZERO) > 0) {
                throw new BusinessException("报价金额必须大于0：明细已有金额但表头未汇总，请先保存报价单再提交审核");
            }
            throw new BusinessException("报价金额必须大于0：请先完善明细单价并保存报价单");
        }

        if (quotation.getFinalAmount() == null || quotation.getFinalAmount().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new BusinessException("最终金额必须大于0：请检查折扣金额或重新保存报价单");
        }

        // 报价明细不能为空（没有明细的报价单无法发送/审核）
        Long itemCount = quotationItemMapper.selectCount(
                new LambdaQueryWrapper<SalesQuotationItem>()
                        .eq(SalesQuotationItem::getQuotationId, quotation.getQuotationId()));
        if (itemCount == null || itemCount == 0) {
            throw new BusinessException("报价明细不能为空，请先添加报价明细");
        }
    }

    /**
     * 报价单明细金额合计（DEV-1116：报错提示区分用）
     */
    private java.math.BigDecimal sumItemAmounts(Long quotationId) {
        try {
            java.util.List<SalesQuotationItem> items = quotationItemMapper.selectList(
                    new LambdaQueryWrapper<SalesQuotationItem>()
                            .eq(SalesQuotationItem::getQuotationId, quotationId));
            java.math.BigDecimal sum = java.math.BigDecimal.ZERO;
            if (items != null) {
                for (SalesQuotationItem it : items) {
                    sum = sum.add(it.getAmount() != null ? it.getAmount() : java.math.BigDecimal.ZERO);
                }
            }
            return sum;
        } catch (Exception e) {
            return java.math.BigDecimal.ZERO;
        }
    }
}
