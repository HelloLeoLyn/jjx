package com.jjx.notification.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jjx.common.exception.BusinessException;
import com.jjx.notification.domain.dto.NotificationCreateDTO;
import com.jjx.notification.domain.dto.NotificationQueryDTO;
import com.jjx.notification.domain.entity.Notification;
import com.jjx.notification.domain.vo.NotificationVO;
import com.jjx.notification.mapper.NotificationMapper;
import com.jjx.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl extends ServiceImpl<NotificationMapper, Notification>
        implements NotificationService {

    private final NotificationMapper notificationMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createNotification(NotificationCreateDTO dto) {
        Notification notif = new Notification();
        BeanUtils.copyProperties(dto, notif);
        notif.setIsRead(0);
        notif.setStatus(0);
        notif.setSendTime(LocalDateTime.now());
        save(notif);
        // 立即标记为已发送（简化处理，后续可加MQ异步）
        notif.setStatus(1);
        updateById(notif);
        log.info("通知已创建: {} -> {} [{}]", dto.getTitle(), dto.getReceiverName(), dto.getNotificationType());
        return notif.getNotificationId();
    }

    @Override
    public Page<NotificationVO> queryPage(NotificationQueryDTO dto) {
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<Notification>()
                .eq(dto.getReceiverId() != null, Notification::getReceiverId, dto.getReceiverId())
                .eq(dto.getNotificationType() != null, Notification::getNotificationType, dto.getNotificationType())
                .eq(dto.getIsRead() != null, Notification::getIsRead, dto.getIsRead())
                .orderByDesc(Notification::getSendTime);

        Page<Notification> page = page(new Page<>(dto.getPageNum(), dto.getPageSize()), wrapper);
        Page<NotificationVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(page.getRecords().stream().map(this::toVO).collect(Collectors.toList()));
        return voPage;
    }

    @Override
    public List<NotificationVO> queryUnread(Long receiverId) {
        List<Notification> list = lambdaQuery()
                .eq(Notification::getReceiverId, receiverId)
                .eq(Notification::getIsRead, 0)
                .orderByDesc(Notification::getSendTime)
                .last("LIMIT 50")
                .list();
        return list.stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public long countUnread(Long receiverId) {
        return lambdaQuery()
                .eq(Notification::getReceiverId, receiverId)
                .eq(Notification::getIsRead, 0)
                .count();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean markRead(Long notificationId) {
        Notification notif = getById(notificationId);
        if (notif == null) throw new BusinessException("通知不存在");
        notif.setIsRead(1);
        notif.setReadTime(LocalDateTime.now());
        return updateById(notif);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean markAllRead(Long receiverId) {
        return lambdaUpdate()
                .eq(Notification::getReceiverId, receiverId)
                .eq(Notification::getIsRead, 0)
                .set(Notification::getIsRead, 1)
                .set(Notification::getReadTime, LocalDateTime.now())
                .update();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteNotification(Long notificationId) {
        return removeById(notificationId);
    }

    private NotificationVO toVO(Notification notif) {
        NotificationVO vo = new NotificationVO();
        BeanUtils.copyProperties(notif, vo);
        return vo;
    }
}
