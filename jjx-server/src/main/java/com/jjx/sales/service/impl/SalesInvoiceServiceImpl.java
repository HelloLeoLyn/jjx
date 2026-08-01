package com.jjx.sales.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jjx.common.core.page.PageResult;
import com.jjx.sales.domain.entity.SalesInvoice;
import com.jjx.sales.mapper.SalesInvoiceMapper;
import com.jjx.sales.service.SalesInvoiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.jjx.system.annotation.Event;

@Service
@RequiredArgsConstructor
public class SalesInvoiceServiceImpl extends ServiceImpl<SalesInvoiceMapper, SalesInvoice> implements SalesInvoiceService {
    private final SalesInvoiceMapper invoiceMapper;

    @Override public PageResult<SalesInvoice> page(int pageNum, int pageSize) {
        Page<SalesInvoice> p = invoiceMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<SalesInvoice>().orderByDesc(SalesInvoice::getCreateTime));
        return PageResult.build(p.getRecords(), p.getTotal());
    }
    @Override public SalesInvoice getById(Long id) { return invoiceMapper.selectById(id); }
    @Override public Long create(SalesInvoice invoice) { invoiceMapper.insert(invoice); return invoice.getInvoiceId(); }
    @Override public boolean update(SalesInvoice invoice) { return invoiceMapper.updateById(invoice) > 0; }
    @Event(value = "sales.invoice.updated", bizId = "#invoice", bizType = "'sales'")
    @Override public boolean delete(Long id) { return invoiceMapper.deleteById(id) > 0; }
    @Event(value = "sales.invoice.deleted", bizId = "#id", bizType = "'sales'")
}
