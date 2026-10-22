package com.jjx.system.domain.vo;

import java.time.LocalDateTime;
import java.util.List;

/** 当前引用的动作摘要；不包含整条链路、请求参数或其它来源的资料。 */
public record AttachmentRefActionsVO(
        Long refId, String uploadedBy, LocalDateTime uploadedAt,
        String joinedBy, LocalDateTime joinedAt,
        List<Action> businessActions, List<Action> referenceEvents) {

    public record Action(String eventKey, String actionName, LocalDateTime actionTime, String operatorName,
                         String traceId, String groupId, List<Action> children) {
        public Action(String eventKey, String actionName, LocalDateTime actionTime, String operatorName) {
            this(eventKey, actionName, actionTime, operatorName, null, null, List.of());
        }
    }
}
