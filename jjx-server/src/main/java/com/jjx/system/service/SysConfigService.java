package com.jjx.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jjx.common.exception.BusinessException;
import com.jjx.system.domain.entity.SysConfig;
import com.jjx.system.mapper.SysConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

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
