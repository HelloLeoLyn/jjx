package com.jjx.purchase.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.purchase.domain.entity.PurchaseSupplier;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 供应商Mapper接口
 * 提供供应商的数据访问操作
 */
@Mapper
public interface PurchaseSupplierMapper extends BaseMapper<PurchaseSupplier> {

    /**
     * 检查供应商编码是否存在
     *
     * @param supplierCode 供应商编码
     * @return 是否存在
     */
    @Select("SELECT COUNT(*) FROM purchase_supplier WHERE supplier_code = #{supplierCode} AND del_flag = '0'")
    int checkSupplierCodeUnique(@Param("supplierCode") String supplierCode);

    /**
     * 检查供应商名称是否存在
     *
     * @param supplierName 供应商名称
     * @return 是否存在
     */
    @Select("SELECT COUNT(*) FROM purchase_supplier WHERE supplier_name = #{supplierName} AND del_flag = '0'")
    int checkSupplierNameUnique(@Param("supplierName") String supplierName);

    /**
     * 更新供应商状态
     *
     * @param supplierId 供应商ID
     * @param status 状态（0正常 1停用）
     * @return 结果
     */
    @Update("UPDATE purchase_supplier SET status = #{status}, update_time = NOW() WHERE supplier_id = #{supplierId} AND del_flag = '0'")
    int updateSupplierStatus(@Param("supplierId") Long supplierId, @Param("status") Integer status);

    /**
     * 更新供应商评估信息
     *
     * @param supplierId 供应商ID
     * @param evaluationScore 评估总分
     * @param qualityScore 质量评分
     * @param deliveryScore 交期评分
     * @param priceScore 价格评分
     * @return 结果
     */
    @Update("UPDATE purchase_supplier SET evaluation_score = #{evaluationScore}, quality_score = #{qualityScore}, delivery_score = #{deliveryScore}, price_score = #{priceScore}, last_evaluation_date = NOW(), update_time = NOW() WHERE supplier_id = #{supplierId} AND del_flag = '0'")
    int updateSupplierEvaluation(@Param("supplierId") Long supplierId,
                                @Param("evaluationScore") Double evaluationScore,
                                @Param("qualityScore") Double qualityScore,
                                @Param("deliveryScore") Double deliveryScore,
                                @Param("priceScore") Double priceScore);

    /**
     * 根据供应商类型查询供应商列表
     *
     * @param supplierType 供应商类型
     * @return 供应商列表
     */
    @Select("SELECT * FROM purchase_supplier WHERE supplier_type = #{supplierType} AND status = 0 AND del_flag = '0' ORDER BY supplier_name")
    List<PurchaseSupplier> selectSuppliersByType(@Param("supplierType") String supplierType);

    /**
     * 查询活跃供应商列表
     *
     * @return 活跃供应商列表
     */
    @Select("SELECT * FROM purchase_supplier WHERE status = 0 AND del_flag = '0' ORDER BY supplier_name")
    List<PurchaseSupplier> selectActiveSuppliers();

    /**
     * 根据评估分数查询优质供应商
     *
     * @param minScore 最低评估分数
     * @return 优质供应商列表
     */
    @Select("SELECT * FROM purchase_supplier WHERE evaluation_score >= #{minScore} AND status = 0 AND del_flag = '0' ORDER BY evaluation_score DESC")
    List<PurchaseSupplier> selectHighQualitySuppliers(@Param("minScore") Double minScore);

    /**
     * 根据供应商编码查询供应商
     *
     * @param supplierCode 供应商编码
     * @return 供应商
     */
    @Select("SELECT * FROM purchase_supplier WHERE supplier_code = #{supplierCode} AND del_flag = '0'")
    PurchaseSupplier selectBySupplierCode(@Param("supplierCode") String supplierCode);
}
