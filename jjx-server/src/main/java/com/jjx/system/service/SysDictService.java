package com.jjx.system.service;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jjx.common.exception.BusinessException;
import com.jjx.system.domain.converter.SysDictConverter;
import com.jjx.system.domain.converter.SysDictItemConverter;
import com.jjx.system.domain.dto.SysDictDTO;
import com.jjx.system.domain.dto.SysDictItemDTO;
import com.jjx.system.domain.entity.SysDict;
import com.jjx.system.domain.entity.SysDictItem;
import com.jjx.system.domain.vo.SysDictItemVO;
import com.jjx.system.domain.vo.SysDictVO;
import com.jjx.system.mapper.SysDictItemMapper;
import com.jjx.system.mapper.SysDictMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 字典管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysDictService {

    private final SysDictMapper dictMapper;
    private final SysDictItemMapper dictItemMapper;
    private final SysDictConverter dictConverter;
    private final SysDictItemConverter dictItemConverter;
    private final RedisTemplate<String, Object> redisTemplate;

    // ==================== 字典类型管理 ====================

    /**
     * 分页查询字典类型列表
     */
    public Page<SysDictVO> selectDictList(SysDictDTO dto, int pageNum, int pageSize) {
        LambdaQueryWrapper<SysDict> wrapper = Wrappers.lambdaQuery();
        if (StringUtils.isNotBlank(dto.getDictCode())) {
            wrapper.like(SysDict::getDictCode, dto.getDictCode());
        }
        if (StringUtils.isNotBlank(dto.getDictName())) {
            wrapper.like(SysDict::getDictName, dto.getDictName());
        }
        if (dto.getIsActive() != null) {
            wrapper.eq(SysDict::getIsActive, dto.getIsActive());
        }
        wrapper.orderByAsc(SysDict::getSortOrder);

        Page<SysDict> page = new Page<>(pageNum, pageSize);
        Page<SysDict> result = dictMapper.selectPage(page, wrapper);

        Page<SysDictVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(dictConverter.toVOList(result.getRecords()));
        return voPage;
    }

    /**
     * 查询所有字典类型列表
     */
    public List<SysDictVO> selectAllDictList() {
        LambdaQueryWrapper<SysDict> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(SysDict::getIsActive, 1);
        wrapper.orderByAsc(SysDict::getSortOrder);
        List<SysDict> list = dictMapper.selectList(wrapper);
        return dictConverter.toVOList(list);
    }

    /**
     * 按分组查询字典列表
     */
    public List<SysDictVO> selectDictListByGroup(String dictGroup) {
        LambdaQueryWrapper<SysDict> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(SysDict::getDictGroup, dictGroup);
        wrapper.eq(SysDict::getIsActive, 1);
        wrapper.orderByAsc(SysDict::getSortOrder);
        List<SysDict> list = dictMapper.selectList(wrapper);
        return dictConverter.toVOList(list);
    }

    /**
     * 根据ID查询字典类型
     */
    public SysDictVO selectDictById(Long dictId) {
        SysDict dict = dictMapper.selectById(dictId);
        if (dict == null) {
            throw new BusinessException("字典类型不存在");
        }
        SysDictVO vo = dictConverter.toVO(dict);
        // 查询字典项
        List<SysDictItem> items = selectItemsByDictCode(dict.getDictCode());
        vo.setItems(dictItemConverter.toVOList(items));
        return vo;
    }

    /**
     * 新增字典类型
     */
    @Transactional(rollbackFor = Exception.class)
    public int insertDict(SysDictDTO dto) {
        // 检查编码唯一性
        checkDictCodeUnique(dto.getDictCode(), null);

        SysDict entity = dictConverter.toEntity(dto);
        entity.setTenantId(1L);
        return dictMapper.insert(entity);
    }

    /**
     * 修改字典类型
     */
    @Transactional(rollbackFor = Exception.class)
    public int updateDict(SysDictDTO dto) {
        if (dto.getDictId() == null) {
            throw new BusinessException("字典ID不能为空");
        }

        SysDict existing = dictMapper.selectById(dto.getDictId());
        if (existing == null) {
            throw new BusinessException("字典类型不存在");
        }

        // 检查编码唯一性（排除自身）
        if (StringUtils.isNotBlank(dto.getDictCode()) && !dto.getDictCode().equals(existing.getDictCode())) {
            checkDictCodeUnique(dto.getDictCode(), dto.getDictId());
        }

        SysDict entity = dictConverter.toEntity(dto);
        entity.setDictId(dto.getDictId());
        return dictMapper.updateById(entity);
    }

    /**
     * 批量删除字典类型（级联删除字典项）
     */
    @Transactional(rollbackFor = Exception.class)
    public int deleteDictByIds(Long[] dictIds) {
        if (dictIds == null || dictIds.length == 0) {
            throw new BusinessException("请选择要删除的字典类型");
        }

        List<Long> ids = Arrays.asList(dictIds);
        // 查询所有要删除的字典编码
        List<SysDict> dicts = dictMapper.selectBatchIds(ids);
        for (SysDict dict : dicts) {
            // 级联删除字典项并清除缓存
            clearDictCache(dict.getDictCode());
            LambdaQueryWrapper<SysDictItem> itemWrapper = Wrappers.lambdaQuery();
            itemWrapper.eq(SysDictItem::getDictCode, dict.getDictCode());
            dictItemMapper.delete(itemWrapper);
        }

        return dictMapper.deleteBatchIds(ids);
    }

    /**
     * 启用/禁用字典类型
     */
    @Transactional(rollbackFor = Exception.class)
    public int changeDictStatus(Long dictId, Integer isActive) {
        SysDict dict = dictMapper.selectById(dictId);
        if (dict == null) {
            throw new BusinessException("字典类型不存在");
        }
        dict.setIsActive(isActive);
        return dictMapper.updateById(dict);
    }

    // ==================== 字典项管理 ====================

    /**
     * 根据字典编码查询字典项列表
     */
    public List<SysDictItem> selectItemsByDictCode(String dictCode) {
        LambdaQueryWrapper<SysDictItem> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(SysDictItem::getDictCode, dictCode);
        wrapper.orderByAsc(SysDictItem::getSortOrder);
        return dictItemMapper.selectList(wrapper);
    }

    /**
     * 根据字典编码查询启用的字典项列表（供前端下拉框使用，带Redis缓存）
     */
    @SuppressWarnings("unchecked")
    public List<SysDictItemVO> selectActiveItemsByDictCode(String dictCode) {
        // 1. 先从缓存获取
        String cacheKey = getDictCacheKey(dictCode);
        List<SysDictItemVO> cached = (List<SysDictItemVO>) redisTemplate.opsForValue().get(cacheKey);
        if (CollUtil.isNotEmpty(cached)) {
            log.debug("命中字典缓存: {}", dictCode);
            return cached;
        }

        // 2. 缓存未命中，查数据库
        LambdaQueryWrapper<SysDictItem> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(SysDictItem::getDictCode, dictCode);
        wrapper.eq(SysDictItem::getIsActive, 1);
        wrapper.orderByAsc(SysDictItem::getSortOrder);
        List<SysDictItem> items = dictItemMapper.selectList(wrapper);
        List<SysDictItemVO> voList = dictItemConverter.toVOList(items);

        // 3. 写入缓存（过期时间1小时）
        redisTemplate.opsForValue().set(cacheKey, voList, 1, TimeUnit.HOURS);
        log.debug("写入字典缓存: {}", dictCode);

        return voList;
    }

    /**
     * 根据字典编码和键获取字典名称
     */
    public String getDictItemValue(String dictCode, String itemKey) {
        LambdaQueryWrapper<SysDictItem> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(SysDictItem::getDictCode, dictCode);
        wrapper.eq(SysDictItem::getItemKey, itemKey);
        wrapper.eq(SysDictItem::getIsActive, 1);
        SysDictItem item = dictItemMapper.selectOne(wrapper);
        return item != null ? item.getItemValue() : itemKey;
    }

    /**
     * 新增字典项
     */
    @Transactional(rollbackFor = Exception.class)
    public int insertDictItem(SysDictItemDTO dto) {
        // 检查字典类型是否存在
        checkDictExists(dto.getDictCode());
        // 检查键唯一性
        checkItemKeyUnique(dto.getDictCode(), dto.getItemKey(), null);

        SysDictItem entity = dictItemConverter.toEntity(dto);
        entity.setTenantId(1L);
        int result = dictItemMapper.insert(entity);
        // 清除缓存
        clearDictCache(dto.getDictCode());
        return result;
    }

    /**
     * 修改字典项
     */
    @Transactional(rollbackFor = Exception.class)
    public int updateDictItem(SysDictItemDTO dto) {
        if (dto.getItemId() == null) {
            throw new BusinessException("字典项ID不能为空");
        }

        SysDictItem existing = dictItemMapper.selectById(dto.getItemId());
        if (existing == null) {
            throw new BusinessException("字典项不存在");
        }

        // 检查键唯一性（排除自身）
        if (StringUtils.isNotBlank(dto.getItemKey()) && !dto.getItemKey().equals(existing.getItemKey())) {
            checkItemKeyUnique(dto.getDictCode(), dto.getItemKey(), dto.getItemId());
        }

        SysDictItem entity = dictItemConverter.toEntity(dto);
        entity.setItemId(dto.getItemId());
        int result = dictItemMapper.updateById(entity);
        // 清除缓存
        clearDictCache(dto.getDictCode());
        return result;
    }

    /**
     * 批量删除字典项
     */
    @Transactional(rollbackFor = Exception.class)
    public int deleteDictItemByIds(Long[] itemIds) {
        if (itemIds == null || itemIds.length == 0) {
            throw new BusinessException("请选择要删除的字典项");
        }

        // 先查询要删除的字典项，获取 dictCode 用于清除缓存
        List<SysDictItem> items = dictItemMapper.selectBatchIds(Arrays.asList(itemIds));
        int result = dictItemMapper.deleteBatchIds(Arrays.asList(itemIds));

        // 清除缓存
        items.stream().map(SysDictItem::getDictCode).distinct().forEach(this::clearDictCache);

        return result;
    }

    /**
     * 启用/禁用字典项
     */
    @Transactional(rollbackFor = Exception.class)
    public int changeDictItemStatus(Long itemId, Integer isActive) {
        SysDictItem item = dictItemMapper.selectById(itemId);
        if (item == null) {
            throw new BusinessException("字典项不存在");
        }
        item.setIsActive(isActive);
        int result = dictItemMapper.updateById(item);
        // 清除缓存
        clearDictCache(item.getDictCode());
        return result;
    }

    // ==================== 内部方法 ====================

    /**
     * 检查字典编码唯一性
     */
    private void checkDictCodeUnique(String dictCode, Long excludeDictId) {
        LambdaQueryWrapper<SysDict> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(SysDict::getDictCode, dictCode);
        if (excludeDictId != null) {
            wrapper.ne(SysDict::getDictId, excludeDictId);
        }
        long count = dictMapper.selectCount(wrapper);
        if (count > 0) {
            throw new BusinessException("字典编码已存在：" + dictCode);
        }
    }

    /**
     * 检查字典类型是否存在
     */
    private void checkDictExists(String dictCode) {
        LambdaQueryWrapper<SysDict> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(SysDict::getDictCode, dictCode);
        long count = dictMapper.selectCount(wrapper);
        if (count == 0) {
            throw new BusinessException("字典类型不存在：" + dictCode);
        }
    }

    /**
     * 检查字典项键唯一性
     */
    private void checkItemKeyUnique(String dictCode, String itemKey, Long excludeItemId) {
        LambdaQueryWrapper<SysDictItem> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(SysDictItem::getDictCode, dictCode);
        wrapper.eq(SysDictItem::getItemKey, itemKey);
        if (excludeItemId != null) {
            wrapper.ne(SysDictItem::getItemId, excludeItemId);
        }
        long count = dictItemMapper.selectCount(wrapper);
        if (count > 0) {
            throw new BusinessException("字典项键已存在：" + itemKey);
        }
    }

    // ==================== 缓存管理 ====================

    private static final String DICT_CACHE_PREFIX = "system:dict:";

    private String getDictCacheKey(String dictCode) {
        return DICT_CACHE_PREFIX + dictCode;
    }

    private void clearDictCache(String dictCode) {
        redisTemplate.delete(getDictCacheKey(dictCode));
        log.info("清除字典缓存: {}", dictCode);
    }

    // ==================== 原有方法（保留兼容） ====================

    /**
     * 根据类别获取字典列表（兼容旧接口）
     */
    public List<SysDict> getByCategory(String category) {
        LambdaQueryWrapper<SysDict> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysDict::getDictCode, category)
                .eq(SysDict::getIsActive, 1)
                .orderByAsc(SysDict::getSortOrder);
        return dictMapper.selectList(wrapper);
    }

    /**
     * 根据编码获取字典名称（兼容旧接口）
     */
    public String getDictName(String category, String dictCode) {
        return getDictItemValue(category, dictCode);
    }

    /**
     * 获取所有付款条件（用于前端下拉框）
     */
    public List<SysDictItemVO> getPaymentTerms() {
        return selectActiveItemsByDictCode("payment_term");
    }

    /**
     * 计算应付日期
     */
    public static LocalDate calculateDueDate(String paymentTermCode, LocalDate baseDate) {
        switch (paymentTermCode) {
            case "COD":
            case "UPON_RECEIPT":
            case "UPON_ACCEPTANCE":
                return baseDate;
            case "NET_15":
                return baseDate.plusDays(15);
            case "NET_30":
                return baseDate.plusDays(30);
            case "NET_45":
                return baseDate.plusDays(45);
            case "NET_60":
                return baseDate.plusDays(60);
            case "NET_90":
                return baseDate.plusDays(90);
            case "MONTHLY_30":
                return baseDate.plusMonths(1).withDayOfMonth(30);
            case "MONTHLY_60":
                return baseDate.plusMonths(2).withDayOfMonth(30);
            case "INVOICE_15":
                return baseDate.plusDays(15);
            case "INVOICE_30":
                return baseDate.plusDays(30);
            case "INVOICE_45":
                return baseDate.plusDays(45);
            case "INVOICE_60":
                return baseDate.plusDays(60);
            default:
                return baseDate.plusDays(30);
        }
    }
}
