package com.jjx.notification.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jjx.notification.domain.dto.NotificationCreateDTO;
import com.jjx.notification.domain.dto.NotificationQueryDTO;
import com.jjx.notification.domain.vo.NotificationVO;
import java.util.List;

public interface NotificationService {
    Long createNotification(NotificationCreateDTO dto);
    Page<NotificationVO> queryPage(NotificationQueryDTO dto);
    List<NotificationVO> queryUnread(Long receiverId);
    long countUnread(Long receiverId);
    boolean markRead(Long notificationId);
    boolean markAllRead(Long receiverId);
    boolean deleteNotification(Long notificationId);
}
