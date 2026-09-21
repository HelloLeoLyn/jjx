package com.jjx.sales.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jjx.common.core.page.PageResult;
import com.jjx.sales.domain.entity.SalesInvoice;
import com.jjx.sales.mapper.SalesInvoiceMapper;
import com.jjx.sales.service.SalesInvoiceService;
import com.jjx.system.annotation.Event;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SalesInvoiceServiceImpl extends ServiceImpl<SalesInvoiceMapper, SalesInvoice> implements SalesInvoiceService {
    private final SalesInvoiceMapper invoiceMapper;
    /** 2026-09-21（dev-20260921-013）：发票删除改手写 payload（带 invoiceNo）。 */
    private final com.jjx.event.EventPublisher eventPublisher;

    @Override public PageResult<SalesInvoice> page(int pageNum, int pageSize, String invoiceNo, String customerName,
                                                   java.time.LocalDate startDate, java.time.LocalDate endDate, Integer status) {
        LambdaQueryWrapper<SalesInvoice> query = new LambdaQueryWrapper<SalesInvoice>()
                .like(invoiceNo != null && !invoiceNo.isBlank(), SalesInvoice::getInvoiceNo, invoiceNo)
                .like(customerName != null && !customerName.isBlank(), SalesInvoice::getCustomerName, customerName)
                .ge(startDate != null, SalesInvoice::getInvoiceDate, startDate)
                .le(endDate != null, SalesInvoice::getInvoiceDate, endDate)
                .eq(status != null, SalesInvoice::getStatus, status)
                .orderByDesc(SalesInvoice::getCreateTime).orderByDesc(SalesInvoice::getInvoiceId);
        Page<SalesInvoice> p = invoiceMapper.selectPage(new Page<>(pageNum, pageSize),
                query);
        return PageResult.of(p, p.getRecords());
    }
    @Override public SalesInvoice getById(Long id) { return invoiceMapper.selectById(id); }
    @Override public Long create(SalesInvoice invoice) { invoiceMapper.insert(invoice); return invoice.getInvoiceId(); }
    @Event(value = "sales.invoice.updated", bizId = "#invoice", bizType = "'sales'",
            params = {"bizNo=#invoice.invoiceNo"})
    @Override public boolean update(SalesInvoice invoice) { return invoiceMapper.updateById(invoice) > 0; }
        @Override
    public boolean delete(Long id) {
        SalesInvoice invoice = invoiceMapper.selectById(id);
        boolean deleted = invoiceMapper.deleteById(id) > 0;
        if (deleted) {
            java.util.Map<String, Object> payload = com.jjx.event.EventPublishSupport.payload(
                    "invoice", id, invoice == null ? null : invoice.getInvoiceNo());
            if (invoice != null) {
                payload.put("customerName", invoice.getCustomerName());
                payload.put("invoiceAmount", invoice.getInvoiceAmount());
            }
            com.jjx.event.EventPublishSupport.fireAfterCommit(eventPublisher, "sales.invoice.deleted", payload);
        }
        return deleted;
    }
}
