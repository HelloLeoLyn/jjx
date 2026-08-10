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

    @Override public PageResult<SalesReceipt> page(int pageNum, int pageSize) {
        Page<SalesReceipt> p = receiptMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<SalesReceipt>().orderByDesc(SalesReceipt::getCreateTime).orderByDesc(SalesReceipt::getReceiptId));
        return PageResult.of(p, p.getRecords());
    }
    @Override public SalesReceipt getById(Long id) { return receiptMapper.selectById(id); }
    @Override public Long create(SalesReceipt receipt) { receiptMapper.insert(receipt); return receipt.getReceiptId(); }
}
