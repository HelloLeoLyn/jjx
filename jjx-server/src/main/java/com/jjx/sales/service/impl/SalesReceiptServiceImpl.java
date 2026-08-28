package com.jjx.sales.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jjx.common.core.page.PageResult;
import com.jjx.sales.domain.entity.SalesReceipt;
import com.jjx.sales.mapper.SalesReceiptMapper;
import com.jjx.sales.service.SalesReceiptService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SalesReceiptServiceImpl extends ServiceImpl<SalesReceiptMapper, SalesReceipt> implements SalesReceiptService {
    private final SalesReceiptMapper receiptMapper;

    @Override public PageResult<SalesReceipt> page(int pageNum, int pageSize, String receiptNo, String customerName,
                                                   java.time.LocalDate startDate, java.time.LocalDate endDate, Integer status) {
        LambdaQueryWrapper<SalesReceipt> query = new LambdaQueryWrapper<SalesReceipt>()
                .like(receiptNo != null && !receiptNo.isBlank(), SalesReceipt::getReceiptNo, receiptNo)
                .like(customerName != null && !customerName.isBlank(), SalesReceipt::getCustomerName, customerName)
                .ge(startDate != null, SalesReceipt::getReceiptDate, startDate)
                .le(endDate != null, SalesReceipt::getReceiptDate, endDate)
                .eq(status != null, SalesReceipt::getStatus, status)
                .orderByDesc(SalesReceipt::getCreateTime).orderByDesc(SalesReceipt::getReceiptId);
        Page<SalesReceipt> p = receiptMapper.selectPage(new Page<>(pageNum, pageSize),
                query);
        return PageResult.of(p, p.getRecords());
    }
    @Override public SalesReceipt getById(Long id) { return receiptMapper.selectById(id); }
    @Override public Long create(SalesReceipt receipt) {
        // DEV-934修复：actual_amount NOT NULL 无默认值，前端未传时兜底 = receiptAmount
        if (receipt.getActualAmount() == null) {
            receipt.setActualAmount(receipt.getReceiptAmount());
        }
        receiptMapper.insert(receipt); return receipt.getReceiptId();
    }
}
