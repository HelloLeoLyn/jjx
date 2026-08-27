package com.jjx.product.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.jjx.product.domain.entity.ConfigModel;
import com.jjx.product.domain.entity.ConfigOption;

import java.util.List;
import java.util.Map;

public interface IConfigModelService extends IService<ConfigModel> {
    Object listPage(Object query);

    /**
     * 查询配置模型详情（含选项列表）
     */
    Map<String, Object> getModelDetail(Long modelId);

    /**
     * 创建配置模型（含选项）
     */
    Long createModel(ConfigModel model, List<ConfigOption> options);

    /**
     * 更新配置模型（含选项全量替换）
     */
    void updateModel(ConfigModel model, List<ConfigOption> options);

    /**
     * 删除配置模型（含选项）
     */
    void deleteModel(Long modelId);

    /**
     * 设置默认模型（同产品下互斥）
     */
    void setDefault(Long modelId);

    /**
     * 启用/停用
     */
    void changeStatus(Long modelId, Integer status);
}
