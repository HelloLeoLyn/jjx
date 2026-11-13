package com.jjx.purchase.mapper;
import org.apache.ibatis.annotations.*;
/** 默认单位为供应商的物料采购配置，不建第二份物料单位档案。 */
@Mapper
public interface PurchaseMaterialDefaultMapper {
    @Select("SELECT COUNT(*) FROM purchase_supplier WHERE supplier_id=#{supplierId} AND del_flag='0'")
    int supplierExists(Long supplierId);
    @Select("SELECT material_purchase_defaults FROM purchase_supplier WHERE supplier_id=#{supplierId} AND del_flag='0'")
    String read(Long supplierId);
    @Select("SELECT material_purchase_defaults FROM purchase_supplier WHERE supplier_id=#{supplierId} AND del_flag='0' FOR UPDATE")
    String readForUpdate(Long supplierId);
    @Update("UPDATE purchase_supplier SET material_purchase_defaults=#{json}, update_by=#{operator}, update_time=NOW() WHERE supplier_id=#{supplierId} AND del_flag='0'")
    int write(@Param("supplierId") Long supplierId, @Param("json") String json, @Param("operator") String operator);
}
