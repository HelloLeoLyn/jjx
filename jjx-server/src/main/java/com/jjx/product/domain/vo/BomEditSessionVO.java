package com.jjx.product.domain.vo;

import lombok.Data;

/**
 * BOM 编辑占用会话视图（dev-20261111-018）。
 * 只返回占用提示所需信息；他人 token 一律不回传。
 */
@Data
public class BomEditSessionVO {

    /** 占用服务是否可用（Redis 正常）。false 时前端必须进入只读，禁止保存。 */
    private boolean leaseAvailable;

    /** 本次请求是否取得了编辑权（或本标签页已持有）。 */
    private boolean acquired;

    /** 是否已被占用（他人或同账号另一窗口）。 */
    private boolean occupied;

    /** 占用者显示名（非账号名）。 */
    private String ownerDisplayName;

    /** 占用者是否与当前登录用户同一账号。 */
    private boolean ownerIsSameUser;

    /** 占用者是否就是本标签页（clientId 一致）。 */
    private boolean ownerIsSameClient;

    /** 租约到期时间（epoch 毫秒）。 */
    private Long expiresAt;

    /** 剩余有效秒数。 */
    private Long ttlSeconds;

    /** 编辑 token：仅本标签页持有时返回；他人占用时为空。 */
    private String token;

    /** 只读/失败原因，可直接展示给用户。 */
    private String message;
}
