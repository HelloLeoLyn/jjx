package com.jjx.product.domain.dto;

import lombok.Data;

/**
 * BOM 编辑占用会话请求（dev-20261111-018）。
 */
@Data
public class BomEditSessionRequest {

    /** 浏览器标签页 UUID（sessionStorage 生成）；不是 userId，也不是 token。 */
    private String clientId;

    /** 续租/释放时持有的编辑 token。 */
    private String token;

    /**
     * 管理员强制接管开关。当前实现一律拒绝：
     * 仅持有专用权限码 engineering:bom:edit:force-unlock 的账号才能进入，
     * 且未配置角色前仍然拒绝，不使用 engineering:bom:edit 冒充管理员解锁。
     */
    private Boolean takeover;
}
