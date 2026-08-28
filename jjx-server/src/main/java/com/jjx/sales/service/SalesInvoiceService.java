package com.jjx.sales.service;

import com.jjx.common.core.page.PageResult;
import com.jjx.sales.domain.entity.SalesInvoice;

public interface SalesInvoiceService {
    PageResult<SalesInvoice> page(int pageNum, int pageSize, String invoiceNo, String customerName,
                                  java.time.LocalDate startDate, java.time.LocalDate endDate, Integer status);
    SalesInvoice getById(Long id);
    Long create(SalesInvoice invoice);
    boolean update(SalesInvoice invoice);
    boolean delete(Long id);
}
