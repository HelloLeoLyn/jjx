package com.jjx.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.notification.domain.entity.Notification;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {
}
