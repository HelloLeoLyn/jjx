package com.jjx.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.jjx.system.domain.entity.ReviewFlow;

import java.util.List;

public interface ReviewFlowService extends IService<ReviewFlow> {
    ReviewFlow record(String bizType, Long bizId, String action, String actionName,
                      Object fromStatus, Object toStatus, String comment, String attachmentIds);

    List<ReviewFlow> listByBiz(String bizType, Long bizId);
}
