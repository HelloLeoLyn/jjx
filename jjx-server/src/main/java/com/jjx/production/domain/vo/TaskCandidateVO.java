package com.jjx.production.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 分配任务候选人员 VO（P3：仅当前用户组织范围内，不伪造全公司）
 */
@Data
@Schema(description = "分配任务候选人员VO")
public class TaskCandidateVO {

    private Long userId;
    private String userName;
    private String nickName;
    private Long deptId;
    private String deptName;
}
