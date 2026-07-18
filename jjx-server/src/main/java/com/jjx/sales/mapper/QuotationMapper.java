package com.jjx.sales.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.sales.domain.entity.SalesQuotation;
import org.apache.ibatis.annotations.Mapper;

/**
 * 销售报价单Mapper接口
 * 提供销售报价单的数据访问操作
 */
@Mapper
public interface QuotationMapper extends BaseMapper<SalesQuotation> {

    /**
     * 更新报价单状态
     *
     * @param quotationId 报价单ID
     * @param status 状态
     * @return 更新结果
     */
    int updateQuotationStatus(Long quotationId, String status);
}
