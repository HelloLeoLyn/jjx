package com.jjx.purchase.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.purchase.domain.entity.MaterialInquiry;
import com.jjx.purchase.domain.dto.MaterialInquiryQueryDTO;
import com.jjx.purchase.domain.vo.MaterialInquiryVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 材料询价Mapper接口
 *
 * @author JJX ERP系统
 * @date 2026-04-02
 */
@Mapper
public interface MaterialInquiryMapper extends BaseMapper<MaterialInquiry> {

    /**
     * 查询材料询价列表
     *
     * @param queryDTO 查询条件
     * @return 询价列表
     */
    List<MaterialInquiryVO> selectMaterialInquiryList(MaterialInquiryQueryDTO queryDTO);

    /**
     * 查询材料询价数量
     *
     * @param queryDTO 查询条件
     * @return 询价数量
     */
    Long countMaterialInquiryList(MaterialInquiryQueryDTO queryDTO);

    /**
     * 根据ID查询材料询价详情
     *
     * @param inquiryId 询价ID
     * @return 询价详情
     */
    MaterialInquiryVO selectMaterialInquiryById(Long inquiryId);

    /**
     * 根据物料编码查询询价历史
     *
     * @param materialCode 物料编码
     * @param limit 限制条数
     * @return 询价历史列表
     */
    List<MaterialInquiryVO> selectInquiryByMaterialCode(@Param("materialCode") String materialCode,
                                                       @Param("limit") Integer limit);

    /**
     * 获取物料最新询价
     *
     * @param materialCode 物料编码
     * @return 最新询价
     */
    MaterialInquiryVO selectLatestInquiryByMaterialCode(String materialCode);

    /**
     * 获取物料平均询价价格
     *
     * @param materialCode 物料编码
     * @return 平均价格
     */
    Double selectAvgInquiryPriceByMaterialCode(String materialCode);

    /**
     * 获取物料最低询价价格
     *
     * @param materialCode 物料编码
     * @return 最低价格
     */
    Double selectMinInquiryPriceByMaterialCode(String materialCode);

    /**
     * 获取物料最高询价价格
     *
     * @param materialCode 物料编码
     * @return 最高价格
     */
    Double selectMaxInquiryPriceByMaterialCode(String materialCode);

    /**
     * 获取供应商的询价记录
     *
     * @param supplierId 供应商ID
     * @param limit 限制条数
     * @return 询价记录列表
     */
    List<MaterialInquiryVO> selectInquiryBySupplierId(@Param("supplierId") Long supplierId,
                                                     @Param("limit") Integer limit);

    /**
     * 批量更新询价状态
     *
     * @param inquiryIds 询价ID列表
     * @param status 状态
     * @param updateBy 更新人
     * @return 更新数量
     */
    int updateInquiryStatusBatch(@Param("inquiryIds") List<Long> inquiryIds,
                                @Param("status") String status,
                                @Param("updateBy") String updateBy);

    /**
     * 更新过期询价状态
     *
     * @param updateBy 更新人
     * @return 更新数量
     */
    int updateExpiredInquiryStatus(@Param("updateBy") String updateBy);

    /**
     * 检查询价是否存在
     *
     * @param materialCode 物料编码
     * @param supplierId 供应商ID
     * @param inquiryDate 询价日期
     * @return 是否存在
     */
    boolean existsInquiry(@Param("materialCode") String materialCode,
                         @Param("supplierId") Long supplierId,
                         @Param("inquiryDate") String inquiryDate);

    /**
     * 获取物料询价统计
     *
     * @param materialCode 物料编码
     * @return 统计信息
     */
    MaterialInquiryVO selectMaterialInquiryStats(String materialCode);

    /**
     * 获取供应商询价统计
     *
     * @param supplierId 供应商ID
     * @return 统计信息
     */
    MaterialInquiryVO selectSupplierInquiryStats(Long supplierId);

    /**
     * 获取价格趋势数据
     *
     * @param materialCode 物料编码
     * @param days 天数
     * @return 价格趋势列表
     */
    List<MaterialInquiryVO> selectPriceTrend(@Param("materialCode") String materialCode,
                                            @Param("days") Integer days);

    /**
     * 批量插入询价记录
     *
     * @param inquiryList 询价记录列表
     * @return 插入数量
     */
    int batchInsert(@Param("list") List<MaterialInquiry> inquiryList);

    /**
     * 逻辑删除询价记录
     *
     * @param inquiryId 询价ID
     * @param updateBy 更新人
     * @return 删除数量
     */
    int logicDeleteById(@Param("inquiryId") Long inquiryId,
                       @Param("updateBy") String updateBy);

    /**
     * 恢复逻辑删除的询价记录
     *
     * @param inquiryId 询价ID
     * @param updateBy 更新人
     * @return 恢复数量
     */
    int recoverLogicDelete(@Param("inquiryId") Long inquiryId,
                          @Param("updateBy") String updateBy);
}
