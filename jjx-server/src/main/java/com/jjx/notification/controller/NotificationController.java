package com.jjx.notification.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jjx.common.core.result.Result;
import com.jjx.notification.domain.dto.NotificationCreateDTO;
import com.jjx.notification.domain.dto.NotificationQueryDTO;
import com.jjx.notification.domain.vo.NotificationVO;
import com.jjx.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@Tag(name = "消息通知")
@RestController
@RequiredArgsConstructor
@RequestMapping("/notification")
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "创建通知")
    @PostMapping
    public Result<Long> create(@Validated @RequestBody NotificationCreateDTO dto) {
        return Result.success(notificationService.createNotification(dto));
    }

    @Operation(summary = "分页查询通知")
    @GetMapping("/page")
    public Result<Page<NotificationVO>> page(NotificationQueryDTO dto) {
        return Result.success(notificationService.queryPage(dto));
    }

    @Operation(summary = "查询未读通知")
    @GetMapping("/unread/{receiverId}")
    public Result<List<NotificationVO>> unread(@PathVariable Long receiverId) {
        return Result.success(notificationService.queryUnread(receiverId));
    }

    @Operation(summary = "未读通知数量")
    @GetMapping("/unread-count/{receiverId}")
    public Result<Long> unreadCount(@PathVariable Long receiverId) {
        return Result.success(notificationService.countUnread(receiverId));
    }

    @Operation(summary = "标记已读")
    @PutMapping("/read/{id}")
    public Result<Boolean> markRead(@PathVariable Long id) {
        return Result.success(notificationService.markRead(id));
    }

    @Operation(summary = "全部标记已读")
    @PutMapping("/read-all/{receiverId}")
    public Result<Boolean> markAllRead(@PathVariable Long receiverId) {
        return Result.success(notificationService.markAllRead(receiverId));
    }

    @Operation(summary = "删除通知")
    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable Long id) {
        return Result.success(notificationService.deleteNotification(id));
    }
}
