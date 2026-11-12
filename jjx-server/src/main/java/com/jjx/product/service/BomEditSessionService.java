package com.jjx.product.service;

import com.jjx.product.domain.vo.BomEditSessionVO;

/**
 * BOM 编辑占用会话（dev-20261111-018）。
 *
 * <p>职责边界：
 * 1）租约只表示「谁正在编辑」，key = {@code bom:edit:lease:v1:{bomId}}，TTL 90 秒、心跳 30 秒；
 * 2）租约<b>不</b>替代权限，也不替代服务端内容指纹（contentRevision）的权威校验；
 * 3）Redis 异常一律 fail-closed：获取编辑权失败、保存被拒绝，不出现「提示失效但仍能保存」。
 */
public interface BomEditSessionService {

    /** 查询当前占用情况；Redis 不可用时返回 leaseAvailable=false，不抛异常。 */
    BomEditSessionVO describe(Long bomId, String clientId);

    /** 获取编辑权（SET NX）。已被占用时返回占用者信息，绝不静默接管。 */
    BomEditSessionVO acquire(Long bomId, String clientId);

    /** 续租（compare-token 的 Lua 原子续期）。token 失效/被替换抛 409，不后台自动接管。 */
    BomEditSessionVO renew(Long bomId, String clientId, String token);

    /** 释放（compare-token 的 Lua 原子删除）。token 不匹配返回 false，旧窗口不能释放新窗口的租约。 */
    boolean release(Long bomId, String token);

    /**
     * 保存前的编辑 token 校验：无 token、已过期、被替换、属于其他账号、Redis 不可用
     * 一律抛 BusinessException（fail-closed）。
     */
    void verifySaveToken(Long bomId, String token);

    /**
     * 管理员强制解锁：原子解除当前 bomId 的编辑占用，并写 sys_oper_log 持久审计
     * （bomId / 操作人 / 操作时间 / 原占用者）。无占用不算错误；审计写入失败则拒绝解锁。
     * 权限码校验由控制器 {@code @SaCheckPermission(engineering:bom:edit:force-unlock)} 把关。
     */
    BomEditSessionVO forceUnlock(Long bomId);
}
