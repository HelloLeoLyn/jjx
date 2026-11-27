package com.jjx.production.printing;

import com.jjx.common.exception.BusinessException;

/** 生产用版指令（P10，dev-20261125-017）。 */
public record PrintUseCommands(String requestId, Long executionId, Long plateId, Long versionId,
                               Long specVersionId, Long routingItemId, String reason) {

    static void check(boolean valid, String message) {
        if (!valid) throw new BusinessException(422, message);
    }

    void validate() {
        check(requestId != null && !requestId.isBlank() && requestId.length() <= 64, "requestId 为空或超长");
        check(executionId != null, "缺少工序执行ID");
        check(plateId != null, "缺少网版ID");
        check(reason == null || reason.length() <= 200, "原因超长");
    }
}
