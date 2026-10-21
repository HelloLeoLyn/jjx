package com.jjx.system.controller;

import com.jjx.common.core.result.Result;
import com.jjx.system.domain.vo.AttachmentRefActionsVO;
import com.jjx.system.service.AttachmentRefActionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/system/attachment/refs")
@RequiredArgsConstructor
public class AttachmentRefActionController {
    private final AttachmentRefActionService actions;

    /** 权限由服务按引用的实际来源校验；不接受客户端指定的来源或 trace。 */
    @GetMapping("/{refId}/actions")
    public Result<AttachmentRefActionsVO> actions(@PathVariable Long refId) {
        return Result.success(actions.detail(refId));
    }
}
