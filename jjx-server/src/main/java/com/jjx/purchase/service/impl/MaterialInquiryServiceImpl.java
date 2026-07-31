package com.jjx.purchase.service.impl;

import com.jjx.purchase.domain.enums.MaterialInquiryStatus;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jjx.common.core.page.PageResult;
import com.jjx.common.utils.DateUtils;
import com.jjx.purchase.domain.dto.MaterialInquiryDTO;
import com.jjx.purchase.domain.dto.MaterialInquiryQueryDTO;
import com.jjx.purchase.domain.entity.MaterialInquiry;
import com.jjx.purchase.domain.vo.MaterialInquiryVO;
import com.jjx.purchase.mapper.MaterialInquiryMapper;
import com.jjx.purchase.service.IMaterialInquiryService;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.executor.BatchResult;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 材料询价服务实现类
 *
 * @author JJX ERP系统
 * @date 2026-04-02
 */
@Service
public class MaterialInquiryServiceImpl extends ServiceImpl<MaterialInquiryMapper, MaterialInquiry> implements IMaterialInquiryService {

    @Override
    public PageResult<MaterialInquiryVO> selectMaterialInquiryList(MaterialInquiryQueryDTO queryDTO) {
        LambdaQueryWrapper<MaterialInquiry> queryWrapper = new LambdaQueryWrapper<>();

        // 构建查询条件
        if (StringUtils.isNotEmpty(queryDTO.getMaterialCode())) {
            queryWrapper.like(MaterialInquiry::getMaterialCode, queryDTO.getMaterialCode());
        }
        if (StringUtils.isNotEmpty(queryDTO.getMaterialName())) {
            queryWrapper.like(MaterialInquiry::getMaterialName, queryDTO.getMaterialName());
        }
        if (StringUtils.isNotEmpty(queryDTO.getSupplierName())) {
            queryWrapper.like(MaterialInquiry::getSupplierName, queryDTO.getSupplierName());
        }
        if (queryDTO.getInquiryStatus() != null) {
            queryWrapper.eq(MaterialInquiry::getInquiryStatus, queryDTO.getInquiryStatus());
        }
        if (StringUtils.isNotEmpty(queryDTO.getInquiryPerson())) {
            queryWrapper.like(MaterialInquiry::getInquiryPerson, queryDTO.getInquiryPerson());
        }
        if (ObjectUtils.isNotEmpty(queryDTO.getInquiryDateStart())) {
            queryWrapper.ge(MaterialInquiry::getInquiryDate, queryDTO.getInquiryDateStart());
        }
        if (ObjectUtils.isNotEmpty(queryDTO.getInquiryDateEnd())) {
            queryWrapper.le(MaterialInquiry::getInquiryDate, queryDTO.getInquiryDateEnd());
        }

        // 排序
        if (StringUtils.isNotEmpty(queryDTO.getOrderByColumn())) {
            String orderDirection = "desc".equalsIgnoreCase(queryDTO.getOrderDirection()) ? "DESC" : "ASC";
            queryWrapper.orderBy(true, "ASC".equals(orderDirection),
                "inquiryDate".equals(queryDTO.getOrderByColumn()) ? MaterialInquiry::getInquiryDate : MaterialInquiry::getCreateTime);
        } else {
            queryWrapper.orderByDesc(MaterialInquiry::getInquiryDate);
        }

        // 分页查询
        IPage<MaterialInquiry> page = new Page<>(queryDTO.getPageNum(), queryDTO.getPageSize());
        IPage<MaterialInquiry> result = baseMapper.selectPage(page, queryWrapper);

        // 转换为VO
        List<MaterialInquiryVO> voList = result.getRecords().stream()
            .map(MaterialInquiryServiceImpl::convertToVO)
            .collect(Collectors.toList());

        return new PageResult<>(voList, result.getTotal());
    }

    @Override
    public MaterialInquiryVO selectMaterialInquiryById(Long inquiryId) {
        MaterialInquiry entity = baseMapper.selectById(inquiryId);
        return entity != null ? convertToVO(entity) : null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertMaterialInquiry(MaterialInquiryDTO inquiryDTO) {
        MaterialInquiry entity = new MaterialInquiry();
        BeanUtils.copyProperties(inquiryDTO, entity);
        return baseMapper.insert(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateMaterialInquiry(MaterialInquiryDTO inquiryDTO) {
        MaterialInquiry entity = new MaterialInquiry();
        BeanUtils.copyProperties(inquiryDTO, entity);
        return baseMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteMaterialInquiryByIds(Long[] inquiryIds) {
        return baseMapper.deleteBatchIds(Arrays.asList(inquiryIds));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteMaterialInquiryById(Long inquiryId) {
        return baseMapper.deleteById(inquiryId);
    }

    @Override
    public List<MaterialInquiryVO> selectInquiryByMaterialCode(String materialCode, Integer limit) {
        LambdaQueryWrapper<MaterialInquiry> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(MaterialInquiry::getMaterialCode, materialCode)
                   .orderByDesc(MaterialInquiry::getInquiryDate);

        if (limit != null && limit > 0) {
            queryWrapper.last("LIMIT " + limit);
        }

        List<MaterialInquiry> entities = baseMapper.selectList(queryWrapper);
        return entities.stream()
            .map(MaterialInquiryServiceImpl::convertToVO)
            .collect(Collectors.toList());
    }

    @Override
    public MaterialInquiryVO selectLatestInquiryByMaterialCode(String materialCode) {
        LambdaQueryWrapper<MaterialInquiry> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(MaterialInquiry::getMaterialCode, materialCode)
                   .orderByDesc(MaterialInquiry::getInquiryDate)
                   .last("LIMIT 1");

        MaterialInquiry entity = baseMapper.selectOne(queryWrapper);
        return entity != null ? convertToVO(entity) : null;
    }

    @Override
    public MaterialInquiryVO selectMaterialInquiryStats(String materialCode) {
        // 这里实现统计逻辑
        MaterialInquiryVO stats = new MaterialInquiryVO();
        stats.setMaterialCode(materialCode);

        // 查询询价记录
        List<MaterialInquiry> inquiries = baseMapper.selectList(
            new LambdaQueryWrapper<MaterialInquiry>()
                .eq(MaterialInquiry::getMaterialCode, materialCode)
                .orderByDesc(MaterialInquiry::getInquiryDate)
        );

        if (!inquiries.isEmpty()) {
            stats.setInquiryCount(inquiries.size());

            // 计算价格统计
            BigDecimal minPrice = inquiries.stream()
                .map(MaterialInquiry::getInquiryPrice)
                .filter(price -> price != null)
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

            BigDecimal maxPrice = inquiries.stream()
                .map(MaterialInquiry::getInquiryPrice)
                .filter(price -> price != null)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

            BigDecimal avgPrice = inquiries.stream()
                .map(MaterialInquiry::getInquiryPrice)
                .filter(price -> price != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(inquiries.size()), 2, BigDecimal.ROUND_HALF_UP);

            stats.setMinInquiryPrice(minPrice);
            stats.setMaxInquiryPrice(maxPrice);
            stats.setAvgInquiryPrice(avgPrice);

            // 设置最新询价
            MaterialInquiry latest = inquiries.get(0);
            stats.setInquiryDate(latest.getInquiryDate());
            stats.setInquiryPrice(latest.getInquiryPrice());
            stats.setSupplierName(latest.getSupplierName());
        }

        return stats;
    }

    @Override
    public int updateInquiryStatusBatch(List<Long> inquiryIds, String status) {
        if (inquiryIds == null || inquiryIds.isEmpty()) {
            return 0;
        }

        List<MaterialInquiry> updates = new ArrayList<>();
        for (Long inquiryId : inquiryIds) {
            MaterialInquiry entity = new MaterialInquiry();
            entity.setInquiryId(inquiryId);
            entity.setInquiryStatus(Integer.valueOf(status));
            updates.add(entity);
        }
        List<BatchResult> batchResults = baseMapper.updateById(updates);
        return batchResults.size();
    }

    @Override
    public int updateExpiredInquiryStatus() {
        LocalDate today = LocalDate.now();

        LambdaQueryWrapper<MaterialInquiry> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(MaterialInquiry::getInquiryStatus, MaterialInquiryStatus.ACTIVE.getCode())
                   .apply("DATE_ADD(inquiry_date, INTERVAL validity_days DAY) < {0}", today);

        List<MaterialInquiry> expiredInquiries = baseMapper.selectList(queryWrapper);

        if (expiredInquiries.isEmpty()) {
            return 0;
        }

        for (MaterialInquiry inquiry : expiredInquiries) {
            inquiry.setInquiryStatus(MaterialInquiryStatus.EXPIRED.getCode());
//            inquiry.setUpdateBy("system");
//            inquiry.setUpdateTime(DateUtils.getNowDate());
        }
        List<BatchResult> results = baseMapper.updateById(expiredInquiries);
        return results.size();
    }

    @Override
    public boolean existsInquiry(String materialCode, Long supplierId, String inquiryDate) {
        LambdaQueryWrapper<MaterialInquiry> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(MaterialInquiry::getMaterialCode, materialCode)
                   .eq(MaterialInquiry::getSupplierId, supplierId)
                   .eq(MaterialInquiry::getInquiryDate, inquiryDate);

        return baseMapper.selectCount(queryWrapper) > 0;
    }

    @Override
    public String importMaterialInquiry(List<MaterialInquiryDTO> inquiryList, Boolean isUpdateSupport, String operName) {
        int successCount = 0;
        int failureCount = 0;
        StringBuilder successMsg = new StringBuilder();
        StringBuilder failureMsg = new StringBuilder();

        for (MaterialInquiryDTO inquiryDTO : inquiryList) {
            try {
                // 检查是否已存在
                Date inquiryDate = inquiryDTO.getInquiryDate();
                boolean exists = existsInquiry(inquiryDTO.getMaterialCode(),
                    inquiryDTO.getSupplierId(), DateUtils.formatDate(inquiryDate));

                if (exists && Boolean.TRUE.equals(isUpdateSupport)) {
                    // 更新现有记录
                    MaterialInquiry entity = baseMapper.selectOne(
                        new LambdaQueryWrapper<MaterialInquiry>()
                            .eq(MaterialInquiry::getMaterialCode, inquiryDTO.getMaterialCode())
                            .eq(MaterialInquiry::getSupplierId, inquiryDTO.getSupplierId())
                            .eq(MaterialInquiry::getInquiryDate, inquiryDTO.getInquiryDate())
                    );

                    if (entity != null) {
                        BeanUtils.copyProperties(inquiryDTO, entity);
//                        entity.setUpdateBy(operName);
//                        entity.setUpdateTime(DateUtils.getNowDate());
                        baseMapper.updateById(entity);
                        successCount++;
                    }
                } else if (!exists) {
                    // 新增记录
                    MaterialInquiry entity = new MaterialInquiry();
                    BeanUtils.copyProperties(inquiryDTO, entity);
//                    entity.setCreateBy(operName);
//                    entity.setCreateTime(DateUtils.getNowDate());
                    baseMapper.insert(entity);
                    successCount++;
                } else {
                    failureCount++;
                    failureMsg.append("<br/>").append(inquiryDTO.getMaterialCode())
                             .append(" ").append(inquiryDTO.getSupplierName())
                             .append(" 已存在");
                }
            } catch (Exception e) {
                failureCount++;
                failureMsg.append("<br/>").append(inquiryDTO.getMaterialCode())
                         .append(" ").append(inquiryDTO.getSupplierName())
                         .append(" 导入失败：").append(e.getMessage());
            }
        }

        if (failureCount > 0) {
            failureMsg.insert(0, "很抱歉，导入失败！共 " + failureCount + " 条数据格式不正确，错误如下：");
            throw new RuntimeException(failureMsg.toString());
        } else {
            successMsg.append("恭喜您，数据已全部导入成功！共 ").append(successCount).append(" 条");
            return successMsg.toString();
        }
    }

    @Override
    public List<MaterialInquiryVO> exportMaterialInquiry(MaterialInquiryQueryDTO queryDTO) {
        LambdaQueryWrapper<MaterialInquiry> queryWrapper = new LambdaQueryWrapper<>();

        // 构建查询条件
        if (StringUtils.isNotEmpty(queryDTO.getMaterialCode())) {
            queryWrapper.like(MaterialInquiry::getMaterialCode, queryDTO.getMaterialCode());
        }
        if (StringUtils.isNotEmpty(queryDTO.getMaterialName())) {
            queryWrapper.like(MaterialInquiry::getMaterialName, queryDTO.getMaterialName());
        }
        if (StringUtils.isNotEmpty(queryDTO.getSupplierName())) {
            queryWrapper.like(MaterialInquiry::getSupplierName, queryDTO.getSupplierName());
        }
        if (queryDTO.getInquiryStatus() != null) {
            queryWrapper.eq(MaterialInquiry::getInquiryStatus, queryDTO.getInquiryStatus());
        }

        queryWrapper.orderByDesc(MaterialInquiry::getInquiryDate);

        List<MaterialInquiry> entities = baseMapper.selectList(queryWrapper);
        return entities.stream()
            .map(MaterialInquiryServiceImpl::convertToVO)
            .collect(Collectors.toList());
    }

    @Override
    public Long copyMaterialInquiry(Long inquiryId) {
        MaterialInquiry source = baseMapper.selectById(inquiryId);
        if (source == null) {
            throw new RuntimeException("源询价记录不存在");
        }

        MaterialInquiry copy = new MaterialInquiry();
        BeanUtils.copyProperties(source, copy);

        // 重置ID和创建信息
        copy.setInquiryId(null);
//        copy.setCreateBy(SecurityUtils.getUsername());
//        copy.setCreateTime(DateUtils.getNowDate());
        copy.setUpdateBy(null);
        copy.setUpdateTime(null);

        // 修改询价日期为今天
        LocalDate now = LocalDate.now();
//        copy.setInquiryDate(now);

        baseMapper.insert(copy);
        return copy.getInquiryId();
    }

    @Override
    public int batchInsertMaterialInquiry(List<MaterialInquiryDTO> inquiryDTOList) {
        List<MaterialInquiry> entities = inquiryDTOList.stream()
            .map(dto -> {
                MaterialInquiry entity = new MaterialInquiry();
                BeanUtils.copyProperties(dto, entity);
                return entity;
            })
            .collect(Collectors.toList());
        return baseMapper.batchInsert(entities);
    }

    @Override
    public List<MaterialInquiryVO> selectPriceTrend(String materialCode, Integer days) {
        LocalDate startDate = LocalDate.now().minusDays(days != null ? days : 30);

        LambdaQueryWrapper<MaterialInquiry> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(MaterialInquiry::getMaterialCode, materialCode)
                   .ge(MaterialInquiry::getInquiryDate, startDate.toString())
                   .orderByAsc(MaterialInquiry::getInquiryDate);

        List<MaterialInquiry> entities = baseMapper.selectList(queryWrapper);
        return entities.stream()
            .map(MaterialInquiryServiceImpl::convertToVO)
            .collect(Collectors.toList());
    }

    @Override
    public MaterialInquiryVO selectSupplierInquiryStats(Long supplierId) {
        MaterialInquiryVO stats = new MaterialInquiryVO();
        stats.setSupplierId(supplierId);

        // 查询供应商的询价记录
        List<MaterialInquiry> inquiries = baseMapper.selectList(
            new LambdaQueryWrapper<MaterialInquiry>()
                .eq(MaterialInquiry::getSupplierId, supplierId)
                .orderByDesc(MaterialInquiry::getInquiryDate)
        );

        if (!inquiries.isEmpty()) {
            MaterialInquiry first = inquiries.get(0);
            stats.setSupplierName(first.getSupplierName());
            stats.setSupplierCode(first.getSupplierCode());
            stats.setInquiryCount(inquiries.size());

            // 计算统计信息
            BigDecimal totalAmount = inquiries.stream()
                .map(MaterialInquiry::getTotalAmount)
                .filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            stats.setTotalAmount(totalAmount);
        }

        return stats;
    }

    @Override
    public int logicDeleteMaterialInquiry(Long inquiryId) {
        MaterialInquiry entity = new MaterialInquiry();
        entity.setInquiryId(inquiryId);
//        entity.setDelFlag("2"); // 逻辑删除标记
//        entity.setUpdateBy(SecurityUtils.getUsername());
//        entity.setUpdateTime(DateUtils.getNowDate());

        return baseMapper.updateById(entity);
    }

    @Override
    public int recoverMaterialInquiry(Long inquiryId) {
        MaterialInquiry entity = new MaterialInquiry();
        entity.setInquiryId(inquiryId);
//        entity.setDelFlag("0"); // 恢复删除
//        entity.setUpdateBy(SecurityUtils.getUsername());
//        entity.setUpdateTime(DateUtils.getNowDate());

        return baseMapper.updateById(entity);
    }

    /**
     * 将实体转换为VO
     */
    private static MaterialInquiryVO convertToVO(MaterialInquiry entity) {
        if (entity == null) {
            return null;
        }

        MaterialInquiryVO vo = new MaterialInquiryVO();
        BeanUtils.copyProperties(entity, vo);

        // 计算总金额
        if (entity.getInquiryPrice() != null && entity.getQuantity() != null) {
            vo.setTotalAmount(entity.getInquiryPrice().multiply(entity.getQuantity()));
        }

        // 设置状态标签
        vo.setInquiryStatusLabel(getInquiryStatusLabel(entity.getInquiryStatus()));

        return vo;
    }

    /**
     * 获取询价状态标签
     */
    private static String getInquiryStatusLabel(Integer status) {
        if (status == null) {
            return "未知";
        }

        switch (status) {
            case 0: return "有效";
            case 2: return "已过期";
            case 3: return "已取消";
            case 4: return "已完成";
            default: return status != null ? String.valueOf(status) : "";
        }
    }

    @Override
    public String validateMaterialInquiry(MaterialInquiryDTO inquiryDTO) {
        // 验证询价数据
        StringBuilder validationResult = new StringBuilder();

        // 验证物料编码
        if (StringUtils.isEmpty(inquiryDTO.getMaterialCode())) {
            validationResult.append("物料编码不能为空; ");
        }

        // 验证物料名称
        if (StringUtils.isEmpty(inquiryDTO.getMaterialName())) {
            validationResult.append("物料名称不能为空; ");
        }

        // 验证供应商
        if (inquiryDTO.getSupplierId() == null) {
            validationResult.append("供应商不能为空; ");
        }

        // 验证询价价格
        if (inquiryDTO.getInquiryPrice() == null || inquiryDTO.getInquiryPrice().compareTo(BigDecimal.ZERO) <= 0) {
            validationResult.append("询价价格必须大于0; ");
        }

        // 验证数量
        if (inquiryDTO.getQuantity() == null || inquiryDTO.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            validationResult.append("数量必须大于0; ");
        }

        // 验证询价日期
        if (ObjectUtils.isEmpty(inquiryDTO.getInquiryDate())) {
            validationResult.append("询价日期不能为空; ");
        }

        // 验证有效期
        if (inquiryDTO.getValidityDays() == null || inquiryDTO.getValidityDays() <= 0) {
            validationResult.append("有效期必须大于0天; ");
        }

        if (validationResult.length() > 0) {
            return validationResult.toString();
        }

        return "数据验证通过";
    }

    @Override
    public List<String> getAvailableInquiryStatus() {
        // 返回可用的询价状态列表
        return List.of("0", "2", "3", "4");
    }

    @Override
    public List<String> getCurrencyList() {
        // 返回支持的币种列表
        return List.of("CNY", "USD", "EUR", "JPY", "GBP", "HKD");
    }

    @Override
    public List<String> getInquiryPersonList() {
        // 查询所有询价人（去重）
        LambdaQueryWrapper<MaterialInquiry> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.select(MaterialInquiry::getInquiryPerson)
                   .isNotNull(MaterialInquiry::getInquiryPerson)
                   .ne(MaterialInquiry::getInquiryPerson, "")
                   .groupBy(MaterialInquiry::getInquiryPerson);

        List<MaterialInquiry> inquiries = baseMapper.selectList(queryWrapper);
        return inquiries.stream()
            .map(MaterialInquiry::getInquiryPerson)
            .collect(Collectors.toList());
    }
}
