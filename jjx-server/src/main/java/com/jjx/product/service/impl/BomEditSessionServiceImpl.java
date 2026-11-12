package com.jjx.product.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjx.common.exception.BusinessException;
import com.jjx.product.domain.vo.BomEditSessionVO;
import com.jjx.product.service.BomEditSessionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * BOM 编辑占用会话实现（dev-20261111-018）。
 *
 * <p>Redis 单实例、无新依赖：只用 StringRedisTemplate + Lua compare token。
 * 所有 Redis 调用集中在 {@link #setIfAbsent}/{@link #getValue}/{@link #eval}/{@link #ttlMillis}，
 * 单元测试可子类化替身，不需要真实 Redis。
 */
@Slf4j
@Service
public class BomEditSessionServiceImpl implements BomEditSessionService {

    /** 单 BOM 单版本只允许一个编辑会话；key 只按 bomId，跨入口天然共用同一把锁。 */
    public static final String KEY_PREFIX = "bom:edit:lease:v1:";
    /** 租约 TTL（秒），前端心跳 30 秒。 */
    public static final long TTL_SECONDS = 90L;

    /**
     * 续租：compare token 后原子改写到新值并重置 TTL。
     * token 不匹配或已过期返回 0，旧窗口不能给新窗口的租约续期。
     */
    public static final String RENEW_SCRIPT = String.join("\n",
            "local v = redis.call('GET', KEYS[1])",
            "if not v then return 0 end",
            "local marker = '\"token\":\"' .. ARGV[1] .. '\"'",
            "if string.find(v, marker, 1, true) == nil then return 0 end",
            "redis.call('SET', KEYS[1], ARGV[3], 'EX', ARGV[2])",
            "return 1");

    /** 释放：compare token 后原子删除；token 不匹配返回 0。 */
    public static final String RELEASE_SCRIPT = String.join("\n",
            "local v = redis.call('GET', KEYS[1])",
            "if not v then return 0 end",
            "local marker = '\"token\":\"' .. ARGV[1] .. '\"'",
            "if string.find(v, marker, 1, true) == nil then return 0 end",
            "return redis.call('DEL', KEYS[1])");

    /** 专用权限码：只有它（而不是 engineering:bom:edit）可以尝试管理员强制解锁。 */
    public static final String FORCE_UNLOCK_PERMISSION = "engineering:bom:edit:force-unlock";

    public static final String MSG_UNAVAILABLE = "EDIT_LEASE_UNAVAILABLE：占用服务暂不可用，请稍后重试";
    public static final String MSG_REQUIRED = "BOM_EDIT_LEASE_REQUIRED：缺少编辑占用凭证，请关闭后重新打开并获取编辑权";
    public static final String MSG_EXPIRED = "BOM_EDIT_LEASE_EXPIRED：编辑占用已过期，请重新申请编辑权后再保存";
    public static final String MSG_HELD = "BOM_EDIT_LEASE_HELD：该BOM已被其它会话占用，请重新申请编辑权后再保存";
    public static final String MSG_LOST = "BOM_EDIT_LEASE_LOST：编辑权已失效，请重新申请编辑权后再保存";

    private static final ObjectMapper JSON = new ObjectMapper();

    private final StringRedisTemplate redisTemplate;

    public BomEditSessionServiceImpl(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** 租约内容：value 至少含 token、userId、displayName、clientId、acquiredAt、lastSeenAt。 */
    public static class Lease {
        public String token;
        public Long userId;
        public String displayName;
        public String clientId;
        public Long acquiredAt;
        public Long lastSeenAt;

        public Lease() {
        }

        public Lease(String token, Long userId, String displayName, String clientId, Long acquiredAt, Long lastSeenAt) {
            this.token = token;
            this.userId = userId;
            this.displayName = displayName;
            this.clientId = clientId;
            this.acquiredAt = acquiredAt;
            this.lastSeenAt = lastSeenAt;
        }
    }

    public static String key(Long bomId) {
        return KEY_PREFIX + bomId;
    }

    // ==================== 对外契约 ====================

    @Override
    public BomEditSessionVO describe(Long bomId, String clientId) {
        Long userId = currentUserId();
        try {
            String raw = getValue(key(bomId));
            if (raw == null) {
                return available(true, clientId, userId, null, null, "当前没有会话在编辑该BOM");
            }
            Lease lease = parse(raw);
            if (lease == null) {
                // 无法解析的旧值按“已占用”处理，宁可只读也不并发写。
                return available(false, clientId, userId, null, null, "该BOM已被其他会话占用");
            }
            Long ttl = ttlMillis(key(bomId));
            return fromLease(lease, ttl, clientId, userId);
        } catch (RuntimeException ex) {
            log.warn("查询BOM编辑占用失败，按不可用处理: {}", ex.getMessage());
            return unavailable();
        }
    }

    @Override
    public BomEditSessionVO acquire(Long bomId, String clientId) {
        if (clientId == null || clientId.isBlank()) {
            throw new BusinessException(400, "BOM_EDIT_CLIENT_REQUIRED：缺少标签页标识（clientId），无法获取编辑权");
        }
        String key = key(bomId);
        Long userId = currentUserId();
        String displayName = currentDisplayName();
        long now = System.currentTimeMillis();
        String token = UUID.randomUUID().toString();
        Lease lease = new Lease(token, userId, displayName, clientId.trim(), now, now);
        Boolean ok;
        try {
            ok = setIfAbsent(key, toJson(lease), TTL_SECONDS);
        } catch (RuntimeException ex) {
            log.error("获取BOM编辑占用失败(Redis): {}", ex.getMessage());
            throw new BusinessException(503, MSG_UNAVAILABLE);
        }
        if (Boolean.TRUE.equals(ok)) {
            return granted(lease, TTL_SECONDS * 1000L);
        }
        // 已占用：返回占用者信息（不返回他人 token），绝不静默接管。
        try {
            String raw = getValue(key);
            Long ttl = ttlMillis(key);
            Lease holder = raw == null ? null : parse(raw);
            if (holder == null) {
                BomEditSessionVO vo = available(false, clientId, userId, null, ttl,
                        "该BOM已被其他会话占用");
                return vo;
            }
            if (clientId != null && clientId.trim().equals(holder.clientId)) {
                // 同一标签页刷新后重入：同一 clientId 可继续持有自己的租约。
                return granted(holder, ttl);
            }
            return fromLease(holder, ttl, clientId, userId);
        } catch (RuntimeException ex) {
            log.error("读取BOM编辑占用者失败(Redis): {}", ex.getMessage());
            throw new BusinessException(503, MSG_UNAVAILABLE);
        }
    }

    @Override
    public BomEditSessionVO renew(Long bomId, String clientId, String token) {
        if (token == null || token.isBlank()) {
            throw new BusinessException(409, MSG_REQUIRED);
        }
        String key = key(bomId);
        long now = System.currentTimeMillis();
        Lease lease = new Lease(token, currentUserId(), currentDisplayName(), trim(clientId), now, now);
        Long result;
        try {
            result = eval(RENEW_SCRIPT, key, token, String.valueOf(TTL_SECONDS), toJson(lease));
        } catch (RuntimeException ex) {
            log.error("续租BOM编辑占用失败(Redis): {}", ex.getMessage());
            throw new BusinessException(503, MSG_UNAVAILABLE);
        }
        if (result == null || result == 0L) {
            throw new BusinessException(409, MSG_LOST);
        }
        return granted(lease, TTL_SECONDS * 1000L);
    }

    @Override
    public boolean release(Long bomId, String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            Long result = eval(RELEASE_SCRIPT, key(bomId), token, String.valueOf(TTL_SECONDS), "0");
            return result != null && result > 0L;
        } catch (RuntimeException ex) {
            // 释放失败不影响用户；租约到期后自然释放，不做后台接管。
            log.warn("释放BOM编辑占用失败(Redis): {}", ex.getMessage());
            return false;
        }
    }

    @Override
    public void verifySaveToken(Long bomId, String token) {
        if (token == null || token.isBlank()) {
            throw new BusinessException(409, MSG_REQUIRED);
        }
        String raw;
        try {
            raw = getValue(key(bomId));
        } catch (RuntimeException ex) {
            // fail-closed：Redis 不可用时拒绝保存，不放行。
            log.error("校验BOM编辑占用失败(Redis)，拒绝保存: {}", ex.getMessage());
            throw new BusinessException(503, MSG_UNAVAILABLE);
        }
        if (raw == null) {
            throw new BusinessException(409, MSG_EXPIRED);
        }
        Lease lease = parse(raw);
        if (lease == null || !token.equals(lease.token)) {
            throw new BusinessException(409, MSG_HELD);
        }
    }

    @Override
    public void forceUnlock(Long bomId) {
        // 当前库中只有 engineering:bom:view/add/edit/delete/approve/reject，
        // 专用权限码 engineering:bom:edit:force-unlock 尚未配置角色/菜单，因此一律拒绝。
        throw new BusinessException(403,
                "BOM_EDIT_FORCE_UNLOCK_NOT_CONFIGURED：管理员强制解锁待最小权限配置（engineering:bom:edit:force-unlock）后启用");
    }

    // ==================== Redis 访问（测试可子类化替身） ====================

    protected Boolean setIfAbsent(String key, String value, long ttlSeconds) {
        return redisTemplate.opsForValue().setIfAbsent(key, value, ttlSeconds, TimeUnit.SECONDS);
    }

    protected String getValue(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    protected Long eval(String script, String key, Object... args) {
        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>(script, Long.class);
        return redisTemplate.execute(redisScript, List.of(key), args);
    }

    protected Long ttlMillis(String key) {
        return redisTemplate.getExpire(key, TimeUnit.MILLISECONDS);
    }

    // ==================== 内部工具 ====================

    private BomEditSessionVO granted(Lease lease, Long ttlMillis) {
        BomEditSessionVO vo = available(true, lease.clientId, lease.userId, lease, ttlMillis,
                "已获取编辑权");
        vo.setAcquired(true);
        vo.setToken(lease.token);
        return vo;
    }

    private BomEditSessionVO fromLease(Lease lease, Long ttlMillis, String clientId, Long userId) {
        BomEditSessionVO vo = available(false, clientId, userId, lease, ttlMillis, null);
        boolean sameClient = clientId != null && clientId.trim().equals(lease.clientId);
        vo.setOwnerIsSameClient(sameClient);
        if (sameClient) {
            vo.setAcquired(true);
            vo.setToken(lease.token);
            vo.setMessage("本窗口已持有该BOM的编辑权");
            return vo;
        }
        vo.setMessage(vo.isOwnerIsSameUser()
                ? "该BOM正在你的另一个窗口编辑，本窗口为只读；请关闭另一个窗口后再申请编辑权"
                : "该BOM正由 " + (lease.displayName == null || lease.displayName.isBlank()
                        ? "其他用户" : lease.displayName) + " 编辑，本窗口为只读");
        return vo;
    }

    /** @param free true=当前无人占用 */
    private BomEditSessionVO available(boolean free, String clientId,
                                       Long userId, Lease lease, Long ttlMillis, String message) {
        BomEditSessionVO vo = new BomEditSessionVO();
        vo.setLeaseAvailable(true);
        vo.setOccupied(!free);
        if (lease != null) {
            vo.setOwnerDisplayName(lease.displayName);
            vo.setOwnerIsSameUser(lease.userId != null && lease.userId.equals(userId));
        }
        if (ttlMillis != null && ttlMillis > 0) {
            vo.setExpiresAt(System.currentTimeMillis() + ttlMillis);
            vo.setTtlSeconds(Math.max(0L, ttlMillis / 1000L));
        }
        vo.setMessage(message);
        return vo;
    }

    private BomEditSessionVO unavailable() {
        BomEditSessionVO vo = new BomEditSessionVO();
        vo.setLeaseAvailable(false);
        vo.setAcquired(false);
        vo.setOccupied(true);
        vo.setMessage(MSG_UNAVAILABLE);
        return vo;
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private static Long currentUserId() {
        try {
            return com.jjx.system.utils.SecurityUtils.getUserId();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static String currentDisplayName() {
        try {
            return com.jjx.system.utils.SecurityUtils.getDisplayName();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    static String toJson(Lease lease) {
        try {
            return JSON.writeValueAsString(lease);
        } catch (Exception ex) {
            throw new BusinessException("BOM编辑占用序列化失败: " + ex.getMessage());
        }
    }

    static Lease parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return JSON.readValue(raw, Lease.class);
        } catch (Exception ex) {
            return null;
        }
    }
}
