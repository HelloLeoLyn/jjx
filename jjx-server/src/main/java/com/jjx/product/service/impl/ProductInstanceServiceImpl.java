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
import com.jjx.system.annotation.Event;

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
        if (query.getInstanceStatus() != null) {
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
    @Event(value = "product.instance.created", bizId = "#instance", bizType = "'product'")
    @Transactional(rollbackFor = Exception.class)
    public boolean createInstance(ProductInstance instance) {
        // 检查实例编码是否唯一
        if (!checkInstanceCodeUnique(instance.getInstanceCode(), null)) {
            throw new BusinessException(BusinessExceptionEnum.DB_DUPLICATE_KEY);
        }

        // 设置默认状态
        if (instance.getInstanceStatus() == null) {
            instance.setInstanceStatus(1);
        }

        return productInstanceMapper.insert(instance) > 0;
    }

    @Override
    @Event(value = "product.instance.batch_created", bizId = "#instances", bizType = "'product'")
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
    @Event(value = "product.instance.status_updated", bizId = "#instanceId", bizType = "'product'")
    @Transactional(rollbackFor = Exception.class)
    public boolean updateInstanceStatus(Long instanceId, Integer status) {
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
    @Event(value = "product.instance.production_started", bizId = "#instanceId", bizType = "'product'")
    @Transactional(rollbackFor = Exception.class)
    public boolean startProduction(Long instanceId) {
        ProductInstance instance = productInstanceMapper.selectById(instanceId);
        if (instance == null) {
            return false;
        }

        // 检查是否可以开始生产
        if (!Integer.valueOf(1).equals(instance.getInstanceStatus()) && !Integer.valueOf(2).equals(instance.getInstanceStatus())) {
            throw new BusinessException(BusinessExceptionEnum.OPERATION_NOT_ALLOWED);
        }

        instance.setInstanceStatus(3);
//        instance.setProductionStartTime(new java.util.Date());
//        return productInstanceMapper.update(instance) > 0;
        return false;
    }

    @Override
    @Event(value = "product.instance.production_completed", bizId = "#instanceId", bizType = "'product'")
    @Transactional(rollbackFor = Exception.class)
    public boolean completeProduction(Long instanceId) {
        ProductInstance instance = productInstanceMapper.selectById(instanceId);
        if (instance == null) {
            return false;
        }

        // 检查是否可以完成生产
        if (!Integer.valueOf(3).equals(instance.getInstanceStatus())) {
            throw new BusinessException(BusinessExceptionEnum.OPERATION_NOT_ALLOWED);
        }

        instance.setInstanceStatus(5);
//        instance.setProductionEndTime(new java.util.Date());
//        return productInstanceMapper.update(instance) > 0;
        return false;
    }

    @Override
    @Event(value = "product.instance.delivered", bizId = "#instanceId", bizType = "'product'")
    @Transactional(rollbackFor = Exception.class)
    public boolean deliverInstance(Long instanceId) {
        ProductInstance instance = productInstanceMapper.selectById(instanceId);
        if (instance == null) {
            return false;
        }

        // 检查是否可以交付
        if (!Integer.valueOf(5).equals(instance.getInstanceStatus())) {
            throw new BusinessException(BusinessExceptionEnum.OPERATION_NOT_ALLOWED);
        }

        instance.setInstanceStatus(9);
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
    private static boolean isValidStatusTransition(Integer currentStatus, Integer newStatus) {
        // 定义合法的状态转换
        switch (currentStatus) {
            case 1: // created
                return Integer.valueOf(2).equals(newStatus) || Integer.valueOf(3).equals(newStatus) || Integer.valueOf(17).equals(newStatus);
            case 2: // planned
                return Integer.valueOf(3).equals(newStatus) || Integer.valueOf(17).equals(newStatus);
            case 3: // in_production
                return Integer.valueOf(5).equals(newStatus) || Integer.valueOf(17).equals(newStatus);
            case 5: // completed
                return Integer.valueOf(9).equals(newStatus);
            case 9: // delivered
                return false; // 已交付状态不能再转换
            case 17: // cancelled
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
