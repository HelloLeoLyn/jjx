package com.jjx.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jjx.common.exception.BusinessException;
import com.jjx.system.domain.entity.SysConfig;
import com.jjx.system.mapper.SysConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SysConfigService {

    private final SysConfigMapper configMapper;

    public List<SysConfig> listAll() {
        return configMapper.selectList(Wrappers.lambdaQuery(SysConfig.class)
                .orderByAsc(SysConfig::getSortOrder));
    }

    public List<SysConfig> listByGroup(String group) {
        return configMapper.selectList(Wrappers.<SysConfig>lambdaQuery()
                .eq(SysConfig::getConfigGroup, group)
                .orderByAsc(SysConfig::getSortOrder));
    }

    /** 按分组返回启用配置键值对（仅 is_active=1），供前端配置模块加载 */
    public Map<String, String> listActiveMapByGroup(String group) {
        List<SysConfig> configs = configMapper.selectList(Wrappers.<SysConfig>lambdaQuery()
                .eq(SysConfig::getConfigGroup, group)
                .eq(SysConfig::getIsActive, 1)
                .orderByAsc(SysConfig::getSortOrder));
        Map<String, String> map = new LinkedHashMap<>();
        for (SysConfig c : configs) {
            map.put(c.getConfigKey(), c.getConfigValue());
        }
        return map;
    }

    public String getValue(String configKey) {
        SysConfig config = configMapper.selectOne(Wrappers.<SysConfig>lambdaQuery()
                .eq(SysConfig::getConfigKey, configKey)
                .eq(SysConfig::getIsActive, 1));
        return config != null ? config.getConfigValue() : null;
    }

    public void updateValue(Long configId, String value) {
        SysConfig config = configMapper.selectById(configId);
        if (config == null) throw new BusinessException("配置不存在");
        config.setConfigValue(value);
        configMapper.updateById(config);
    }
}
