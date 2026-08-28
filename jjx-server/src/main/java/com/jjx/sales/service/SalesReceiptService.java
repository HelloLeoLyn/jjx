package com.jjx.sales.service;

import com.jjx.common.core.page.PageResult;
import com.jjx.sales.domain.entity.SalesReceipt;

public interface SalesReceiptService {
    PageResult<SalesReceipt> page(int pageNum, int pageSize, String receiptNo, String customerName,
                                  java.time.LocalDate startDate, java.time.LocalDate endDate, Integer status);
    SalesReceipt getById(Long id);
    Long create(SalesReceipt receipt);
}
