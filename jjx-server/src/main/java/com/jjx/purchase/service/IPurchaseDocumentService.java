package com.jjx.purchase.service;

import com.jjx.purchase.domain.dto.PurchaseDocumentDTO;
import com.jjx.purchase.domain.entity.PurchaseDocument;

import java.util.List;
import java.util.Map;

/**
 * 采购票据Service接口
 */
public interface IPurchaseDocumentService {

    /**
     * 查询票据列表
     */
    List<PurchaseDocument> selectDocumentList(PurchaseDocumentDTO dto);

    /**
     * 查询票据详情
     */
    PurchaseDocument selectDocumentById(Long documentId);

    /**
     * 新增票据
     */
    int insertDocument(PurchaseDocumentDTO dto);

    /**
     * 修改票据
     */
    int updateDocument(PurchaseDocumentDTO dto);

    /**
     * 删除票据
     */
    int deleteDocumentById(Long documentId);

    /**
     * 批量删除票据
     */
    int deleteDocumentByIds(Long[] documentIds);

    /**
     * 核验票据
     */
    int verifyDocument(Long documentId, String verifierName, String verificationDate, String verificationRemark);

    /**
     * 检查票据编号是否唯一
     */
    boolean checkDocumentNoUnique(String documentNo);

    /**
     * 生成票据编号
     */
    String generateDocumentNo(String documentType);

    /**
     * 根据订单ID查询票据列表
     */
    List<PurchaseDocument> selectByOrderId(Long orderId);

    /**
     * 根据供应商ID查询票据列表
     */
    List<PurchaseDocument> selectBySupplierId(Long supplierId);

    /**
     * 查询待核验的票据列表
     */
    List<PurchaseDocument> selectPendingVerification();

    /**
     * 查询已核验的票据列表
     */
    List<PurchaseDocument> selectVerified();

    /**
     * 查询今日票据
     */
    List<PurchaseDocument> selectToday();

    /**
     * 查询本周票据
     */
    List<PurchaseDocument> selectWeek();

    /**
     * 查询本月票据
     */
    List<PurchaseDocument> selectMonth();

    /**
     * 获取票据统计信息
     */
    Map<String, Object> getDocumentStatistics();

    /**
     * 导出票据列表
     */
    String exportDocumentList(PurchaseDocumentDTO dto);

    /**
     * 上传票据文件（临时保存，不插入数据库）
     *
     * @param orderId 订单ID
     * @param file    文件
     * @return 文件信息（不包含数据库ID）
     */
    Map<String, Object> uploadTempFile(Long orderId, org.springframework.web.multipart.MultipartFile file);

    /**
     * 查询订单的磁盘票据文件列表
     *
     * @param orderId 订单ID
     * @return 文件列表
     */
    List<Map<String, Object>> selectDiskFilesByOrderId(Long orderId);

    /**
     * 批量确认票据（将临时文件插入数据库）
     *
     * @param orderId    订单ID
     * @param supplierId 供应商ID
     * @param files      文件信息列表 [{fileName, fileUrl, fileSize}]
     * @return 创建的票据数量
     */
    int batchConfirmDocuments(Long orderId, Long supplierId, List<Map<String, Object>> files);

    /**
     * 删除临时文件
     *
     * @param fileUrl 文件URL
     */
    void deleteTempFile(String fileUrl);
}
