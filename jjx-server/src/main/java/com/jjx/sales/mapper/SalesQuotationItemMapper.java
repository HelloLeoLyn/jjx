package com.jjx.sales.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.sales.domain.entity.SalesQuotationItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * 报价单明细 Mapper
 */
@Mapper
public interface SalesQuotationItemMapper extends BaseMapper<SalesQuotationItem> {
}
