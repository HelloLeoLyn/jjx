package com.jjx.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jjx.common.exception.BusinessException;
import com.jjx.product.domain.entity.ConfigModel;
import com.jjx.product.domain.entity.ConfigOption;
import com.jjx.product.mapper.ConfigModelMapper;
import com.jjx.product.mapper.ConfigOptionMapper;
import com.jjx.product.service.IConfigModelService;
import com.jjx.system.utils.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class ConfigModelServiceImpl extends ServiceImpl<ConfigModelMapper, ConfigModel> implements IConfigModelService {

    private final ConfigOptionMapper optionMapper;

    public ConfigModelServiceImpl(ConfigOptionMapper optionMapper) {
        this.optionMapper = optionMapper;
    }

    @Override
    public Object listPage(Object query) {
        return list(new LambdaQueryWrapper<ConfigModel>().orderByDesc(ConfigModel::getModelId));
    }

    @Override
    public Map<String, Object> getModelDetail(Long modelId) {
        ConfigModel model = getById(modelId);
        if (model == null) {
            throw new BusinessException("配置模型不存在");
        }
        Map<String, Object> result = new HashMap<>();
        result.put("model", model);
        result.put("options", optionMapper.selectByModelId(modelId));
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createModel(ConfigModel model, List<ConfigOption> options) {
        validate(model);
        // model_code 唯一
        Long exist = count(new LambdaQueryWrapper<ConfigModel>().eq(ConfigModel::getModelCode, model.getModelCode()));
        if (exist > 0) {
            throw new BusinessException("模型编码已存在: " + model.getModelCode());
        }
        if (model.getStatus() == null) model.setStatus(1);
        if (model.getIsDefault() == null) model.setIsDefault(0);
        model.setCreateBy(SecurityUtils.getUsername());
        save(model);
        saveOptions(model.getModelId(), options);
        log.info("创建配置模型[{}] 选项{}条", model.getModelCode(), options == null ? 0 : options.size());
        return model.getModelId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateModel(ConfigModel model, List<ConfigOption> options) {
        if (model.getModelId() == null || getById(model.getModelId()) == null) {
            throw new BusinessException("配置模型不存在");
        }
        validate(model);
        // 编码唯一（排除自身）
        Long exist = count(new LambdaQueryWrapper<ConfigModel>()
                .eq(ConfigModel::getModelCode, model.getModelCode())
                .ne(ConfigModel::getModelId, model.getModelId()));
        if (exist > 0) {
            throw new BusinessException("模型编码已存在: " + model.getModelCode());
        }
        model.setUpdateBy(SecurityUtils.getUsername());
        updateById(model);
        // 选项全量替换
        optionMapper.delete(new LambdaQueryWrapper<ConfigOption>().eq(ConfigOption::getModelId, model.getModelId()));
        saveOptions(model.getModelId(), options);
        log.info("更新配置模型[{}] 选项{}条", model.getModelCode(), options == null ? 0 : options.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteModel(Long modelId) {
        if (getById(modelId) == null) {
            throw new BusinessException("配置模型不存在");
        }
        optionMapper.delete(new LambdaQueryWrapper<ConfigOption>().eq(ConfigOption::getModelId, modelId));
        removeById(modelId);
        log.info("删除配置模型 id={}", modelId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setDefault(Long modelId) {
        ConfigModel model = getById(modelId);
        if (model == null) {
            throw new BusinessException("配置模型不存在");
        }
        // 同产品下其他模型取消默认
        ConfigModel reset = new ConfigModel();
        reset.setIsDefault(0);
        update(reset, new LambdaQueryWrapper<ConfigModel>()
                .eq(ConfigModel::getProductId, model.getProductId())
                .ne(ConfigModel::getModelId, modelId));
        model.setIsDefault(1);
        updateById(model);
        log.info("设置默认配置模型 id={}", modelId);
    }

    @Override
    public void changeStatus(Long modelId, Integer status) {
        ConfigModel model = getById(modelId);
        if (model == null) {
            throw new BusinessException("配置模型不存在");
        }
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException("状态值不合法(0停用/1启用)");
        }
        model.setStatus(status);
        model.setUpdateBy(SecurityUtils.getUsername());
        updateById(model);
        log.info("配置模型[{}] 状态 → {}", model.getModelCode(), status);
    }

    private void validate(ConfigModel model) {
        if (!StringUtils.hasText(model.getModelCode())) {
            throw new BusinessException("模型编码必填");
        }
        if (!StringUtils.hasText(model.getModelName())) {
            throw new BusinessException("模型名称必填");
        }
        if (model.getProductId() == null) {
            throw new BusinessException("所属产品必填");
        }
    }

    private void saveOptions(Long modelId, List<ConfigOption> options) {
        if (options == null || options.isEmpty()) return;
        int order = 0;
        for (ConfigOption opt : options) {
            if (opt.getOptionCode() == null || opt.getOptionName() == null) {
                throw new BusinessException("选项编码和名称必填");
            }
            opt.setOptionId(null);
            opt.setModelId(modelId);
            if (opt.getSortOrder() == null) opt.setSortOrder(order++);
            optionMapper.insert(opt);
        }
    }
}
