package com.jjx.sales.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jjx.common.core.page.PageResult;
import com.jjx.common.exception.BusinessException;
import com.jjx.framework.common.RedisSequenceService;
import com.jjx.sales.domain.entity.SalesInquiry;
import com.jjx.sales.domain.entity.SalesQuotation;
import com.jjx.sales.domain.vo.InquiryToQuotationVO;
import com.jjx.sales.enums.InquiryStatus;
import com.jjx.sales.enums.QuotationStatus;
import com.jjx.sales.mapper.SalesInquiryMapper;
import com.jjx.sales.mapper.QuotationMapper;
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
    private final RedisSequenceService redisSequenceService;
    private final ProductMapper productMapper;

    /**
     * 分页查询询价单列表
     */
    @Override
    public PageResult<SalesInquiry> selectInquiryPage(SalesInquiry inquiry, Integer pageNum, Integer pageSize) {
        Page<SalesInquiry> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<SalesInquiry> wrapper = buildQueryWrapper(inquiry);
        Page<SalesInquiry> result = inquiryMapper.selectPage(page, wrapper);
        fillProductName(result.getRecords());
        return PageResult.build(result.getRecords(), result.getTotal());
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

        // 只查未删除
        wrapper.eq(SalesInquiry::getDeleted, 0);
        // 按创建时间倒序
        wrapper.orderByDesc(SalesInquiry::getCreateTime);

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
        return inquiryMapper.deleteBatchIds(Arrays.asList(inquiryIds));
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
