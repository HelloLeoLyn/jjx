package com.jjx.sales.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jjx.common.core.page.PageResult;
import com.jjx.common.exception.BusinessException;
import com.jjx.sales.domain.converter.CustomerConverter;
import com.jjx.sales.domain.dto.CustomerAddDTO;
import com.jjx.sales.domain.dto.CustomerEditDTO;
import com.jjx.sales.domain.dto.CustomerImportDTO;
import com.jjx.sales.domain.dto.CustomerQueryDTO;
import com.jjx.sales.domain.entity.SalesCustomer;
import com.jjx.sales.domain.vo.CustomerVO;
import com.jjx.sales.mapper.CustomerMapper;
import com.jjx.framework.common.RedisSequenceService;
import com.jjx.sales.service.ICustomerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import com.jjx.system.annotation.Event;

/**
 * 客户服务实现类
 * 提供客户管理的具体业务逻辑实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerServiceImpl implements ICustomerService {

    private final CustomerMapper customerMapper;
    private final CustomerConverter customerConverter;
    private final RedisSequenceService redisSequenceService;

    @Override
    public List<CustomerVO> list(CustomerQueryDTO customer) {
        log.info("查询客户列表，查询条件：{}", customer);

        LambdaQueryWrapper<SalesCustomer> queryWrapper = buildQueryWrapper(customer);

        // 按创建时间倒序排列
        queryWrapper.orderByDesc(SalesCustomer::getCreateTime).orderByDesc(SalesCustomer::getCustomerId);

        List<SalesCustomer> salesCustomers = customerMapper.selectList(queryWrapper);
        return customerConverter.toVOList(salesCustomers);
    }


    @Override
    public PageResult<CustomerVO> page(CustomerQueryDTO queryDTO) {
        LambdaQueryWrapper<SalesCustomer> queryWrapper = buildQueryWrapper(queryDTO);
        Page<SalesCustomer> page = new Page<>(queryDTO.getPageNum(), queryDTO.getPageSize());
        Page<SalesCustomer> result = customerMapper.selectPage(page, queryWrapper);
        List<CustomerVO> voList = customerConverter.toVOList(result.getRecords());
        return PageResult.of(result, voList);
    }

    /**
     * 根据 CustomerQueryDTO 构建查询条件
     */
    @NonNull
    private LambdaQueryWrapper<SalesCustomer> buildQueryWrapper(CustomerQueryDTO queryDTO) {
        LambdaQueryWrapper<SalesCustomer> queryWrapper = Wrappers.lambdaQuery();

        if (StringUtils.isNotBlank(queryDTO.getCustomerCode())) {
            queryWrapper.like(SalesCustomer::getCustomerCode, queryDTO.getCustomerCode());
        }
        if (StringUtils.isNotBlank(queryDTO.getCustomerName())) {
            queryWrapper.like(SalesCustomer::getCustomerName, queryDTO.getCustomerName());
        }
        if (queryDTO.getCustomerType() != null) {
            queryWrapper.eq(SalesCustomer::getCustomerType, queryDTO.getCustomerType());
        }
        if (queryDTO.getCustomerLevel() != null) {
            queryWrapper.eq(SalesCustomer::getCustomerLevel, queryDTO.getCustomerLevel());
        }
        if (queryDTO.getCustomerStatus() != null) {
            queryWrapper.eq(SalesCustomer::getCustomerStatus, queryDTO.getCustomerStatus());
        }
        if (StringUtils.isNotBlank(queryDTO.getContactPerson())) {
            queryWrapper.like(SalesCustomer::getContactPerson, queryDTO.getContactPerson());
        }
        if (StringUtils.isNotBlank(queryDTO.getContactPhone())) {
            queryWrapper.like(SalesCustomer::getContactPhone, queryDTO.getContactPhone());
        }
        if (queryDTO.getSalesManagerId() != null) {
            queryWrapper.eq(SalesCustomer::getSalesManagerId, queryDTO.getSalesManagerId());
        }

        queryWrapper.orderByDesc(SalesCustomer::getCreateTime).orderByDesc(SalesCustomer::getCustomerId);
        return queryWrapper;
    }

    @Override
    public CustomerVO selectCustomerById(Long customerId) {
        log.info("根据ID查询客户信息，客户ID：{}", customerId);

        if (customerId == null) {
            throw new BusinessException("客户ID不能为空");
        }

        SalesCustomer customer = customerMapper.selectById(customerId);
        if (customer == null) {
            throw new BusinessException("客户不存在，客户ID：" + customerId);
        }

        return customerConverter.toVO(customer);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @Event(value = "sales.customer.created", bizId = "#dto", bizType = "'sales'")
    public int insertCustomer(CustomerAddDTO dto) {
        log.info("新增客户，DTO：{}", dto);

        // DTO转实体
        SalesCustomer customer = customerConverter.toEntity(dto);

        // 生成客户编码（在保存时生成，避免并发冲突）
        String customerCode = generateCustomerCode();
        customer.setCustomerCode(customerCode);

        // 设置默认值
        if (customer.getCustomerStatus() == null) {
            customer.setCustomerStatus(1); // 默认状态：潜在客户
        }
        if (customer.getCustomerLevel() == null) {
            customer.setCustomerLevel(3); // 默认等级：C级
        }
        if (customer.getCreditLimit() == null) {
            customer.setCreditLimit(0.0);
        }
        if (customer.getUsedCreditLimit() == null) {
            customer.setUsedCreditLimit(0.0);
        }
        if (customer.getVip() == null) {
            customer.setVip(false);
        }
        if (customer.getCustomerScore() == null) {
            customer.setCustomerScore(3); // 默认评分：3分
        }

        // 设置创建时间
        customer.setCreateTime(LocalDateTime.now());

        return customerMapper.insert(customer);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @Event(value = "sales.customer.updated", bizId = "#dto", bizType = "'sales'")
    public int updateCustomer(CustomerEditDTO dto) {
        log.info("修改客户，DTO：{}", dto);

        if (dto.getCustomerId() == null) {
            throw new BusinessException("客户ID不能为空");
        }

        // 检查客户是否存在
        SalesCustomer existingCustomer = customerMapper.selectById(dto.getCustomerId());
        if (existingCustomer == null) {
            throw new BusinessException("客户不存在，客户ID：" + dto.getCustomerId());
        }

        // DTO转实体
        SalesCustomer customer = customerConverter.toEntity(dto);
        customer.setCustomerId(dto.getCustomerId());

        // 保留原有客户编码
        customer.setCustomerCode(existingCustomer.getCustomerCode());

        // 检查客户名称唯一性（排除自身）
        if (StringUtils.isNotBlank(customer.getCustomerName()) &&
            !customer.getCustomerName().equals(existingCustomer.getCustomerName())) {
            if (!checkCustomerNameUnique(customer)) {
                throw new BusinessException("客户名称已存在：" + customer.getCustomerName());
            }
        }

        // 设置更新时间
        customer.setUpdateTime(LocalDateTime.now());

        return customerMapper.updateById(customer);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @Event(value = "sales.customer.deleted", bizId = "#customerIds", bizType = "'sales'")
    public int deleteCustomerByIds(Long[] customerIds) {
        log.info("批量删除客户，客户ID数组：{}", Arrays.toString(customerIds));

        if (customerIds == null || customerIds.length == 0) {
            throw new BusinessException("请选择要删除的客户");
        }

        // 检查客户是否存在且状态允许删除
        for (Long customerId : customerIds) {
            CustomerVO customer = selectCustomerById(customerId);
            if (customer == null) {
                throw new BusinessException("客户不存在，客户ID：" + customerId);
            }

            // 检查客户状态，正式客户不能删除
            if (customer.getCustomerStatus() != null && customer.getCustomerStatus() == 2) {
                throw new BusinessException("正式客户不能删除，客户ID：" + customerId);
            }
        }

        return customerMapper.deleteBatchIds(Arrays.asList(customerIds));
    }

    @Override
    public List<SalesCustomer> selectCustomerDropdown() {
        log.info("查询客户下拉列表");

        LambdaQueryWrapper<SalesCustomer> queryWrapper = Wrappers.lambdaQuery();
        queryWrapper.select(
            SalesCustomer::getCustomerId,
            SalesCustomer::getCustomerCode,
            SalesCustomer::getCustomerName,
            SalesCustomer::getCustomerShortName
        );
        queryWrapper.eq(SalesCustomer::getCustomerStatus, 2); // 只显示正式客户
        queryWrapper.orderByAsc(SalesCustomer::getCustomerCode);

        return customerMapper.selectList(queryWrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @Event(value = "sales.customer.status_updated", bizId = "#customerId", bizType = "'sales'")
    public int changeCustomerStatus(Long customerId, Integer status) {
        log.info("变更客户状态，客户ID：{}，状态：{}", customerId, status);

        if (customerId == null) {
            throw new BusinessException("客户ID不能为空");
        }
        if (status == null || status < 1 || status > 4) {
            throw new BusinessException("客户状态值无效");
        }

        // 检查客户是否存在
        SalesCustomer customer = customerMapper.selectById(customerId);
        if (customer == null) {
            throw new BusinessException("客户不存在，客户ID：" + customerId);
        }

        // 状态变更逻辑
        if (status == 2 && customer.getCustomerStatus() != 2) { // 转为正式客户
            // 检查必要信息是否完整
            if (StringUtils.isBlank(customer.getCustomerCode()) ||
                StringUtils.isBlank(customer.getCustomerName()) ||
                StringUtils.isBlank(customer.getContactPerson()) ||
                StringUtils.isBlank(customer.getContactPhone())) {
                throw new BusinessException("转为正式客户需要完善客户编码、名称、联系人和电话信息");
            }

            // 设置合作开始日期
            customer.setCooperationStartDate(LocalDateTime.now());
        }

        customer.setCustomerStatus(status);
        customer.setUpdateTime(LocalDateTime.now());

        return customerMapper.updateById(customer);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @Event(value = "sales.customer.approved", bizId = "#customerIds", bizType = "'sales'")
    public int approveCustomers(Long[] customerIds) {
        log.info("批量审核客户，客户ID数组：{}", Arrays.toString(customerIds));

        if (customerIds == null || customerIds.length == 0) {
            throw new BusinessException("请选择要审核的客户");
        }

        int successCount = 0;
        List<String> errorMessages = new ArrayList<>();

        for (Long customerId : customerIds) {
            try {
                // 将客户状态改为正式客户
                int result = changeCustomerStatus(customerId, 2);
                if (result > 0) {
                    successCount++;
                }
            } catch (Exception e) {
                errorMessages.add("客户ID " + customerId + " 审核失败：" + e.getMessage());
                log.error("审核客户失败，客户ID：{}", customerId, e);
            }
        }

        if (successCount == 0 && !errorMessages.isEmpty()) {
            throw new BusinessException("客户审核失败：" + String.join("；", errorMessages));
        }

        return successCount;
    }

    @Override
    public Object getCustomerStatistics() {
        log.info("获取客户统计信息");

        Map<String, Object> statistics = new HashMap<>();

        // 统计客户总数
        Long totalCount = customerMapper.selectCount(Wrappers.emptyWrapper());
        statistics.put("totalCount", totalCount);

        // 按状态统计
        LambdaQueryWrapper<SalesCustomer> queryWrapper = Wrappers.lambdaQuery();

        // 潜在客户数量
        queryWrapper.eq(SalesCustomer::getCustomerStatus, 1);
        Long potentialCount = customerMapper.selectCount(queryWrapper);
        statistics.put("potentialCount", potentialCount);

        // 正式客户数量
        queryWrapper.clear();
        queryWrapper.eq(SalesCustomer::getCustomerStatus, 2);
        Long formalCount = customerMapper.selectCount(queryWrapper);
        statistics.put("formalCount", formalCount);

        // 暂停合作客户数量
        queryWrapper.clear();
        queryWrapper.eq(SalesCustomer::getCustomerStatus, 3);
        Long suspendedCount = customerMapper.selectCount(queryWrapper);
        statistics.put("suspendedCount", suspendedCount);

        // 终止合作客户数量
        queryWrapper.clear();
        queryWrapper.eq(SalesCustomer::getCustomerStatus, 4);
        Long terminatedCount = customerMapper.selectCount(queryWrapper);
        statistics.put("terminatedCount", terminatedCount);

        // 按等级统计
        queryWrapper.clear();
        queryWrapper.eq(SalesCustomer::getCustomerLevel, 1);
        Long levelACount = customerMapper.selectCount(queryWrapper);
        statistics.put("levelACount", levelACount);

        queryWrapper.clear();
        queryWrapper.eq(SalesCustomer::getCustomerLevel, 2);
        Long levelBCount = customerMapper.selectCount(queryWrapper);
        statistics.put("levelBCount", levelBCount);

        queryWrapper.clear();
        queryWrapper.eq(SalesCustomer::getCustomerLevel, 3);
        Long levelCCount = customerMapper.selectCount(queryWrapper);
        statistics.put("levelCCount", levelCCount);

        // VIP客户数量
        queryWrapper.clear();
        queryWrapper.eq(SalesCustomer::getVip, true);
        Long vipCount = customerMapper.selectCount(queryWrapper);
        statistics.put("vipCount", vipCount);

        // 按类型统计
        queryWrapper.clear();
        queryWrapper.eq(SalesCustomer::getCustomerType, 1);
        Long terminalCount = customerMapper.selectCount(queryWrapper);
        statistics.put("terminalCount", terminalCount);

        queryWrapper.clear();
        queryWrapper.eq(SalesCustomer::getCustomerType, 2);
        Long agentCount = customerMapper.selectCount(queryWrapper);
        statistics.put("agentCount", agentCount);

        queryWrapper.clear();
        queryWrapper.eq(SalesCustomer::getCustomerType, 3);
        Long distributorCount = customerMapper.selectCount(queryWrapper);
        statistics.put("distributorCount", distributorCount);

        return statistics;
    }

    @Override
    public boolean checkCustomerCodeUnique(SalesCustomer customer) {
        if (StringUtils.isBlank(customer.getCustomerCode())) {
            return true;
        }
        CustomerVO customerVO = selectCustomerByCode(customer.getCustomerCode());
        return customerVO != null;
    }

    @Override
    public boolean checkCustomerNameUnique(SalesCustomer customer) {
        if (StringUtils.isBlank(customer.getCustomerName())) {
            return true;
        }
        LambdaQueryWrapper<SalesCustomer> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SalesCustomer::getCustomerName,customer.getCustomerName());
        Long count = customerMapper.selectCount(queryWrapper);
        return count == 0;
    }

    @Override
    public CustomerVO selectCustomerByCode(String customerCode) {
        log.info("根据客户编码获取客户信息，客户编码：{}", customerCode);

        if (StringUtils.isBlank(customerCode)) {
            throw new BusinessException("客户编码不能为空");
        }
        LambdaQueryWrapper<SalesCustomer> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SalesCustomer::getCustomerCode,customerCode);
        SalesCustomer salesCustomer = customerMapper.selectOne(queryWrapper);
        return customerConverter.toVO(salesCustomer);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateCustomerCreditLimit(Long customerId, Double creditLimit) {
        log.info("更新客户信用额度，客户ID：{}，信用额度：{}", customerId, creditLimit);

        if (customerId == null) {
            throw new BusinessException("客户ID不能为空");
        }
        if (creditLimit == null || creditLimit < 0) {
            throw new BusinessException("信用额度必须大于等于0");
        }

        // 检查客户是否存在
        SalesCustomer customer = this.customerMapper.selectById(customerId);
        if (customer == null) {
            throw new BusinessException("客户不存在，客户ID：" + customerId);
        }

        // 检查新额度是否小于已用额度
        if (customer.getUsedCreditLimit() != null && creditLimit < customer.getUsedCreditLimit()) {
            throw new BusinessException("新信用额度不能小于已用信用额度");
        }

        customer.setCreditLimit(creditLimit);
        customer.setUpdateTime(LocalDateTime.now());

        return customerMapper.updateById(customer);
    }

    @Override
    public String generateCustomerCode() {
        log.info("生成客户编码");
        // 使用统一序列号服务生成客户编码
        // 格式：CUST + 日期(yyMMdd) + 序列号(4位)
        return redisSequenceService.generateBizNumber(RedisSequenceService.BizCode.CUST);
    }

    @Override
    public List<CustomerVO> search(String keyword) {
        Page<SalesCustomer> page = new Page<>(1, 50);
        LambdaQueryWrapper<SalesCustomer> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper
                .like(SalesCustomer::getCustomerCode,keyword).or()
                .like(SalesCustomer::getCustomerName,keyword).or()
                .like(SalesCustomer::getCustomerShortName,keyword);
        Page<SalesCustomer> result = customerMapper.selectPage(page, queryWrapper);
        List<SalesCustomer> records = result.getRecords();
        return customerConverter.toVOList(records);

    }

    @Override
    public String exportCustomerList(SalesCustomer customer) {
        return "";
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String importCustomers(List<CustomerImportDTO> importList, String operName) {
        log.info("导入客户，共 {} 条，操作人：{}", importList == null ? 0 : importList.size(), operName);
        if (importList == null || importList.isEmpty()) {
            throw new BusinessException("导入数据为空");
        }

        int successCount = 0;
        int updateCount = 0;
        int failCount = 0;
        StringBuilder errorMsg = new StringBuilder();

        for (int i = 0; i < importList.size(); i++) {
            CustomerImportDTO importDTO = importList.get(i);
            try {
                // 按客户名称判重（同一批次内去重，防止重复导入）
                SalesCustomer existing = customerMapper.selectOne(
                        new LambdaQueryWrapper<SalesCustomer>()
                                .eq(SalesCustomer::getCustomerName, importDTO.getCustomerName())
                                .last("LIMIT 1"));
                if (existing != null) {
                    // 更新已有客户（保留编码，不覆盖编号字段）
                    applyImportFields(existing, importDTO);
                    customerMapper.updateById(existing);
                    updateCount++;
                } else {
                    // 新增客户
                    SalesCustomer customer = new SalesCustomer();
                    customer.setCustomerCode(generateCustomerCode());
                    customer.setCustomerName(importDTO.getCustomerName());
                    applyImportFields(customer, importDTO);
                    // 默认值
                    if (customer.getCustomerStatus() == null) customer.setCustomerStatus(1);
                    if (customer.getCustomerLevel() == null) customer.setCustomerLevel(3);
                    if (customer.getCreditLimit() == null) customer.setCreditLimit(0.0);
                    if (customer.getUsedCreditLimit() == null) customer.setUsedCreditLimit(0.0);
                    if (customer.getVip() == null) customer.setVip(false);
                    if (customer.getCustomerScore() == null) customer.setCustomerScore(3);
                    customer.setCreateBy(operName);
                    customer.setCreateTime(LocalDateTime.now());
                    customerMapper.insert(customer);
                    successCount++;
                }
            } catch (Exception e) {
                failCount++;
                errorMsg.append("第").append(i + 2).append("行(").append(importDTO.getCustomerName())
                        .append("): ").append(e.getMessage()).append("\n");
            }
        }

        StringBuilder result = new StringBuilder();
        result.append("导入完成：新增 ").append(successCount)
                .append(" 条，更新 ").append(updateCount)
                .append(" 条，失败 ").append(failCount).append(" 条");
        if (failCount > 0) {
            result.append("\n失败明细：\n").append(errorMsg);
        }
        return result.toString();
    }

    /**
     * 将导入DTO字段应用到客户实体（DEV-662）
     */
    private void applyImportFields(SalesCustomer customer, CustomerImportDTO dto) {
        customer.setCustomerShortName(dto.getCustomerShortName());
        customer.setCustomerType(dto.getCustomerType());
        customer.setCustomerLevel(dto.getCustomerLevel());
        customer.setIndustryCategory(dto.getIndustryCategory());
        customer.setCustomerSource(dto.getCustomerSource());
        customer.setCountry(dto.getCountry());
        customer.setProvince(dto.getProvince());
        customer.setCity(dto.getCity());
        customer.setAddress(dto.getAddress());
        customer.setContactPerson(dto.getContactPerson());
        customer.setContactPhone(dto.getContactPhone());
        customer.setContactEmail(dto.getContactEmail());
        customer.setUnifiedSocialCreditCode(dto.getUnifiedSocialCreditCode());
        customer.setTaxpayerId(dto.getTaxpayerId());
        customer.setBankName(dto.getBankName());
        customer.setBankAccount(dto.getBankAccount());
        customer.setPaymentMethod(dto.getPaymentMethod());
        customer.setCreditLimit(dto.getCreditLimit());
        customer.setRemark(dto.getRemark());
    }
}
