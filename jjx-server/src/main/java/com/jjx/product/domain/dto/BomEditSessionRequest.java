package com.jjx.product.domain.dto;

import lombok.Data;

/**
 * BOM 编辑占用会话请求（dev-20261111-018）。
 */
@Data
public class BomEditSessionRequest {

    /** 编辑实例 UUID（内存生成，各窗口/弹窗独立）；不是 userId，也不是 token。 */
    private String clientId;

    /** 续租/释放时持有的编辑 token。 */
    private String token;

    /**
     * 管理员强制接管开关：
     * 仅持有专用权限码 engineering:bom:edit:force-unlock 的账号才能进入，
     * 超管通配权限覆盖该专用码；解锁持久留痕后重新申请，不使用普通 edit 冒充。
     */
    private Boolean takeover;
}
