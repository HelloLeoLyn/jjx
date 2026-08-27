package com.jjx.sales.service;

import com.jjx.common.core.page.PageResult;
import com.jjx.sales.domain.entity.SalesReceipt;

public interface SalesReceiptService {
    PageResult<SalesReceipt> page(int pageNum, int pageSize);
    SalesReceipt getById(Long id);
    Long create(SalesReceipt receipt);
}
