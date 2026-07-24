package com.jjx.sales.service;

import com.jjx.common.core.page.PageResult;
import com.jjx.sales.domain.entity.SalesInvoice;

public interface SalesInvoiceService {
    PageResult<SalesInvoice> page(int pageNum, int pageSize);
    SalesInvoice getById(Long id);
    Long create(SalesInvoice invoice);
    boolean update(SalesInvoice invoice);
    boolean delete(Long id);
}
