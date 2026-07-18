package com.jjx.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jjx.common.core.page.PageResult;
import com.jjx.common.exception.BusinessException;
import com.jjx.common.exception.BusinessExceptionEnum;
import com.jjx.product.domain.entity.ProductInstance;
import com.jjx.product.domain.query.ProductInstanceQuery;
import com.jjx.product.domain.vo.ProductInstanceVo;
import com.jjx.product.mapper.ProductInstanceMapper;
import com.jjx.product.service.IProductInstanceService;
import lombok.NonNull;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 产品实例Service实现
 */
@Service
public class ProductInstanceServiceImpl extends ServiceImpl<ProductInstanceMapper,ProductInstance> implements IProductInstanceService {

    private final ProductInstanceMapper productInstanceMapper;

    public ProductInstanceServiceImpl(ProductInstanceMapper productInstanceMapper) {
        this.productInstanceMapper = productInstanceMapper;
    }

    @Override
    public List<ProductInstanceVo> getInstanceList(ProductInstanceQuery query) {
        LambdaQueryWrapper<ProductInstance> wrapper = getProductInstanceLambdaQueryWrapper(query);
        List<ProductInstance> instances = productInstanceMapper.selectList(wrapper);
        return  instances.stream().map(ProductInstanceServiceImpl::convertToVo).toList();
    }

    private static @NonNull LambdaQueryWrapper<ProductInstance> getProductInstanceLambdaQueryWrapper(ProductInstanceQuery query) {
        LambdaQueryWrapper<ProductInstance> wrapper = new LambdaQueryWrapper<>();
        if (query.getInstanceCode() != null && !query.getInstanceCode().isEmpty()) {
            wrapper.like(ProductInstance::getInstanceCode, query.getInstanceCode());
        }
        if (query.getProductId() != null) {
            wrapper.eq(ProductInstance::getProductId, query.getProductId());
        }
        if (query.getOrderId() != null) {
            wrapper.eq(ProductInstance::getOrderId, query.getOrderId());
        }
        if (query.getCustomerId() != null) {
            wrapper.eq(ProductInstance::getCustomerId, query.getCustomerId());
        }
        if (query.getInstanceStatus() != null && !query.getInstanceStatus().isEmpty()) {
            wrapper.eq(ProductInstance::getInstanceStatus, query.getInstanceStatus());
        }
        return wrapper;
    }

    @Override
    public PageResult<ProductInstanceVo> selectInstanceList(ProductInstanceQuery query, Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<ProductInstance> wrapper = getProductInstanceLambdaQueryWrapper(query);
        Page<ProductInstance> page = new Page<>(pageNum, pageSize);
        Page<ProductInstance> list = productInstanceMapper.selectPage(page, wrapper);
        List<ProductInstanceVo> result = list.getRecords().stream().map(ProductInstanceServiceImpl::convertToVo).toList();
        return PageResult.build(result, list.getTotal());
    }

    @Override
    public ProductInstanceVo getInstanceDetail(Long instanceId) {
        ProductInstance instance = productInstanceMapper.selectById(instanceId);
        if (instance == null) {
            return null;
        }

        return convertToVo(instance);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean createInstance(ProductInstance instance) {
        // 检查实例编码是否唯一
        if (!checkInstanceCodeUnique(instance.getInstanceCode(), null)) {
            throw new BusinessException(BusinessExceptionEnum.DB_DUPLICATE_KEY);
        }

        // 设置默认状态
        if (instance.getInstanceStatus() == null || instance.getInstanceStatus().isEmpty()) {
            instance.setInstanceStatus("created");
        }

        return productInstanceMapper.insert(instance) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean batchCreateInstances(List<ProductInstance> instances) {
        if (instances == null || instances.isEmpty()) {
            return false;
        }

        int successCount = 0;
        for (ProductInstance instance : instances) {
            try {
                if (createInstance(instance)) {
                    successCount++;
                }
            } catch (Exception e) {
                // 记录错误但继续处理其他实例
                System.err.println("创建产品实例失败: " + e.getMessage());
            }
        }

        return successCount > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateInstanceStatus(Long instanceId, String status) {
        ProductInstance instance = productInstanceMapper.selectById(instanceId);
        if (instance == null) {
            return false;
        }

        // 验证状态转换是否合法
        if (!isValidStatusTransition(instance.getInstanceStatus(), status)) {
            throw new BusinessException(BusinessExceptionEnum.OPERATION_NOT_ALLOWED);
        }

        instance.setInstanceStatus(status);
//        return productInstanceMapper.update(instance) > 0;
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean startProduction(Long instanceId) {
        ProductInstance instance = productInstanceMapper.selectById(instanceId);
        if (instance == null) {
            return false;
        }

        // 检查是否可以开始生产
        if (!"created".equals(instance.getInstanceStatus()) && !"planned".equals(instance.getInstanceStatus())) {
            throw new BusinessException(BusinessExceptionEnum.OPERATION_NOT_ALLOWED);
        }

        instance.setInstanceStatus("in_production");
//        instance.setProductionStartTime(new java.util.Date());
//        return productInstanceMapper.update(instance) > 0;
        return false;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean completeProduction(Long instanceId) {
        ProductInstance instance = productInstanceMapper.selectById(instanceId);
        if (instance == null) {
            return false;
        }

        // 检查是否可以完成生产
        if (!"in_production".equals(instance.getInstanceStatus())) {
            throw new BusinessException(BusinessExceptionEnum.OPERATION_NOT_ALLOWED);
        }

        instance.setInstanceStatus("completed");
//        instance.setProductionEndTime(new java.util.Date());
//        return productInstanceMapper.update(instance) > 0;
        return false;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deliverInstance(Long instanceId) {
        ProductInstance instance = productInstanceMapper.selectById(instanceId);
        if (instance == null) {
            return false;
        }

        // 检查是否可以交付
        if (!"completed".equals(instance.getInstanceStatus())) {
            throw new BusinessException(BusinessExceptionEnum.OPERATION_NOT_ALLOWED);
        }

        instance.setInstanceStatus("delivered");
//        instance.setDeliveryTime(new java.util.Date());
//        productCategoryMapper.update(category) > 0
        return false;
    }

    @Override
    public List<ProductInstance> getInstancesByOrderId(Long orderId) {
//        return productInstanceMapper.selectByOrderId(orderId);
        return null;
    }

    @Override
    public List<ProductInstance> getInstancesByProductId(Long productId) {
//        return productInstanceMapper.selectByProductId(productId);
        return null;
    }

    @Override
    public List<ProductInstance> getInstancesByCustomerId(Long customerId) {
//        return productInstanceMapper.selectByCustomerId(customerId);
        return null;
    }

    @Override
    public long countInstances(ProductInstanceQuery query) {
        List<ProductInstanceVo> instances = getInstanceList(query);
        return instances.size();
    }

    /**
     * 检查实例编码是否唯一
     */
    private static boolean checkInstanceCodeUnique(String instanceCode, Long instanceId) {
//        ProductInstance instance = productInstanceMapper.selectByInstanceCode(instanceCode);
//        if (instance == null) {
//            return true;
//        }
//        if (instanceId != null && instance.getInstanceId().equals(instanceId)) {
//            return true;
//        }
        return false;
    }

    /**
     * 验证状态转换是否合法
     */
    private static boolean isValidStatusTransition(String currentStatus, String newStatus) {
        // 定义合法的状态转换
        switch (currentStatus) {
            case "created":
                return "planned".equals(newStatus) || "in_production".equals(newStatus) || "cancelled".equals(newStatus);
            case "planned":
                return "in_production".equals(newStatus) || "cancelled".equals(newStatus);
            case "in_production":
                return "completed".equals(newStatus) || "cancelled".equals(newStatus);
            case "completed":
                return "delivered".equals(newStatus);
            case "delivered":
                return false; // 已交付状态不能再转换
            case "cancelled":
                return false; // 已取消状态不能再转换
            default:
                return false;
        }
    }

    /**
     * 将ProductInstance实体转换为ProductInstanceVo
     */
    private static ProductInstanceVo convertToVo(ProductInstance instance) {
        if (instance == null) {
            return null;
        }

        ProductInstanceVo vo = new ProductInstanceVo();
        BeanUtils.copyProperties(instance, vo);

        return vo;
    }
}
