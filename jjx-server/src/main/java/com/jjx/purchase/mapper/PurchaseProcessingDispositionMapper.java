package com.jjx.purchase.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.purchase.domain.entity.PurchaseProcessingDisposition;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface PurchaseProcessingDispositionMapper extends BaseMapper<PurchaseProcessingDisposition> {
    @Select("SELECT receipt_json FROM purchase_processing_disposition WHERE item_id=#{itemId} ORDER BY disposition_id")
    List<String> history(@Param("itemId") Long itemId);
}
