package com.jjx.product.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjx.common.enums.YesNoEnum;
import com.jjx.common.exception.BusinessException;
import com.jjx.product.domain.vo.BomEditSessionVO;
import com.jjx.product.service.BomEditSessionService;
import com.jjx.system.annotation.BusinessType;
import com.jjx.system.domain.entity.SysOperLog;
import com.jjx.system.mapper.SysOperLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * BOM 编辑占用会话实现（dev-20261111-018）。
 *
 * <p>Redis 单实例、无新依赖：只用 StringRedisTemplate + Lua compare token。
 * 所有 Redis 调用集中在 {@link #setIfAbsent}/{@link #getValue}/{@link #eval}/{@link #ttlMillis}，
 * 单元测试可子类化替身，不需要真实 Redis。
 *
 * <p>归属规则（dev-20261111-018 修复卡）：租约凭证 = 登录 userId + clientId + token 三者同时匹配。
 * clientId 只是标签页标识，不是权限凭证；登录身份缺失一律 fail-closed。
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

    /**
     * 管理员强制解锁：按「读取到的原始值」原子删除；读取后占用已被替换/释放则返回 0，
     * 不会误删紧接着取得编辑权的其他会话。
     */
    public static final String FORCE_RELEASE_SCRIPT = String.join("\n",
            "local v = redis.call('GET', KEYS[1])",
            "if not v then return 0 end",
            "if v ~= ARGV[1] then return 0 end",
            "return redis.call('DEL', KEYS[1])");

    /** 专用权限码：只有它（而不是 engineering:bom:edit）可以执行管理员强制解锁。 */
    public static final String FORCE_UNLOCK_PERMISSION = "engineering:bom:edit:force-unlock";
    /** 强制解锁审计写入的模块/操作标识（sys_oper_log.module / oper_url）。 */
    public static final String FORCE_UNLOCK_MODULE = "BOM编辑占用";
    public static final String FORCE_UNLOCK_OPER_URL = FORCE_UNLOCK_PERMISSION;

    public static final String MSG_UNAVAILABLE = "EDIT_LEASE_UNAVAILABLE：占用服务暂不可用，请稍后重试";
    public static final String MSG_REQUIRED = "BOM_EDIT_LEASE_REQUIRED：缺少编辑占用凭证，请关闭后重新打开并获取编辑权";
    public static final String MSG_EXPIRED = "BOM_EDIT_LEASE_EXPIRED：编辑占用已过期，请重新申请编辑权后再保存";
    public static final String MSG_HELD = "BOM_EDIT_LEASE_HELD：该BOM已被其它会话占用，请重新申请编辑权后再保存";
    public static final String MSG_LOST = "BOM_EDIT_LEASE_LOST：编辑权已失效，请重新申请编辑权后再保存";
    public static final String MSG_FORBIDDEN = "BOM_EDIT_LEASE_FORBIDDEN：该编辑占用属于其他账号，不能续租、释放或保存，请重新申请编辑权";
    public static final String MSG_LOGIN_REQUIRED = "BOM_EDIT_LOGIN_REQUIRED：未取得登录身份，不能获取/续租/释放编辑占用或保存";
    public static final String MSG_AUDIT_FAILED = "BOM_EDIT_FORCE_UNLOCK_AUDIT_FAILED：强制解锁审计写入失败，本次未解除占用，请稍后重试并核查";

    private static final ObjectMapper JSON = new ObjectMapper();

    private final StringRedisTemplate redisTemplate;
    private final SysOperLogMapper operLogMapper;

    public BomEditSessionServiceImpl(StringRedisTemplate redisTemplate, SysOperLogMapper operLogMapper) {
        this.redisTemplate = redisTemplate;
        this.operLogMapper = operLogMapper;
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
        if (userId == null) {
            // 身份缺失不得把 null 当“本人”，只读且不返回 token。
            return identityRequired();
        }
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
        Long userId = requireUserId();
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
                return available(false, clientId, userId, null, ttl, "该BOM已被其他会话占用");
            }
            if (ownedBy(holder, userId, clientId)) {
                // 同一账号同一标签页刷新后重入：账号 + 标签页都匹配才继续持有自己的租约。
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
        Long userId = requireUserId();
        String key = key(bomId);
        // 先读取并校验「存储 userId + clientId + token」，再 compare-token 原子续租：
        // 只凭 token 或只凭 clientId 都不能续租他人的租约。
        Lease stored;
        try {
            String raw = getValue(key);
            stored = parse(raw);
        } catch (RuntimeException ex) {
            log.error("续租BOM编辑占用失败(Redis): {}", ex.getMessage());
            throw new BusinessException(503, MSG_UNAVAILABLE);
        }
        if (stored == null || !token.equals(stored.token)) {
            throw new BusinessException(409, MSG_LOST);
        }
        if (!ownedBy(stored, userId, clientId)) {
            throw new BusinessException(409, MSG_FORBIDDEN);
        }
        long now = System.currentTimeMillis();
        Lease lease = new Lease(token, userId, stored.displayName, trim(clientId), now, now);
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
        Long userId = requireUserId();
        String key = key(bomId);
        try {
            String raw = getValue(key);
            Lease stored = parse(raw);
            // 账号归属校验：他人（或同账号另一 clientId 之外的会话）不能释放别人的租约。
            if (stored == null || !token.equals(stored.token) || stored.userId == null
                    || !stored.userId.equals(userId)) {
                return false;
            }
            Long result = eval(RELEASE_SCRIPT, key, token, String.valueOf(TTL_SECONDS), "0");
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
        Long userId = requireUserId();
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
        // token 属于其他账号：拒绝保存（同 clientId 换账号后残留的旧 token 不能继续保存）。
        if (lease.userId == null || !lease.userId.equals(userId)) {
            throw new BusinessException(409, MSG_FORBIDDEN);
        }
    }

    /**
     * 管理员强制解锁（权限由控制器 @SaCheckPermission(engineering:bom:edit:force-unlock) 把关）。
     *
     * <p>顺序：取登录身份 → 读取当前租约 → 写持久审计 → 按原始值原子删除。
     * 审计写入失败时直接拒绝（fail-closed），不会出现「解锁成功但无痕」。
     */
    @Override
    public BomEditSessionVO forceUnlock(Long bomId) {
        Long userId = requireUserId();
        String operator = currentUsername();
        String displayName = currentDisplayName();
        String raw;
        try {
            raw = getValue(key(bomId));
        } catch (RuntimeException ex) {
            log.error("强制解锁读取占用失败(Redis): {}", ex.getMessage());
            throw new BusinessException(503, MSG_UNAVAILABLE);
        }
        if (raw == null || raw.isBlank()) {
            // 无占用不是错误：返回“当前无占用”，不抛 403。
            return available(true, null, userId, null, null, "当前无占用（bomId=" + bomId + "），无需解锁");
        }
        Lease lease = parse(raw);
        long now = System.currentTimeMillis();
        writeUnlockAudit(bomId, userId, operator, displayName, lease, now, "强制解锁请求");
        Long deleted;
        try {
            deleted = eval(FORCE_RELEASE_SCRIPT, key(bomId), raw);
        } catch (RuntimeException ex) {
            log.error("强制解锁删除占用失败(Redis): {}", ex.getMessage());
            throw new BusinessException(503, MSG_UNAVAILABLE);
        }
        if (deleted == null || deleted == 0L) {
            // 并发：读取后占用已被释放或替换，不能误删新会话；补一条未生效留痕。
            writeUnlockAudit(bomId, userId, operator, displayName, lease, System.currentTimeMillis(),
                    "强制解锁未生效：占用在读取后被释放或替换");
            return available(true, null, userId, null, null,
                    "强制解锁未生效：占用已被其他会话释放或替换（bomId=" + bomId + "），请重新查询");
        }
        String owner = lease == null ? "未知" : (lease.displayName == null || lease.displayName.isBlank()
                ? "userId=" + lease.userId : lease.displayName + "（userId=" + lease.userId + "）");
        String acquired = lease == null || lease.acquiredAt == null ? "未知"
                : java.time.Instant.ofEpochMilli(lease.acquiredAt)
                .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime().toString();
        log.info("强制解锁BOM编辑占用 bomId={} 操作人={}({}) 原占用者={}", bomId, operator, userId, owner);
        return available(true, null, userId, lease, null,
                "已强制解除 bomId=" + bomId + " 的编辑占用（原占用者：" + owner + "，占用自 " + acquired + "）");
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

    /** 同一账号 + 同一标签页 + token 由调用方另行比对，才是租约持有者。 */
    private static boolean ownedBy(Lease lease, Long userId, String clientId) {
        return lease != null && userId != null && userId.equals(lease.userId)
                && clientId != null && clientId.trim().equals(lease.clientId);
    }

    private BomEditSessionVO granted(Lease lease, Long ttlMillis) {
        BomEditSessionVO vo = available(true, lease.clientId, lease.userId, lease, ttlMillis,
                "已获取编辑权");
        vo.setAcquired(true);
        vo.setToken(lease.token);
        return vo;
    }

    private BomEditSessionVO fromLease(Lease lease, Long ttlMillis, String clientId, Long userId) {
        BomEditSessionVO vo = available(false, clientId, userId, lease, ttlMillis, null);
        // 只有「存储 userId == 当前登录 userId」且 clientId 一致，才把 token 当本人的凭证返回。
        boolean sameUser = lease.userId != null && lease.userId.equals(userId);
        boolean sameClient = sameUser && clientId != null && clientId.trim().equals(lease.clientId);
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

    /** 登录身份缺失：只读、不返回 token。 */
    private BomEditSessionVO identityRequired() {
        BomEditSessionVO vo = new BomEditSessionVO();
        vo.setLeaseAvailable(false);
        vo.setAcquired(false);
        vo.setOccupied(true);
        vo.setMessage(MSG_LOGIN_REQUIRED);
        return vo;
    }

    /**
     * 强制解锁持久审计（sys_oper_log）：bomId、操作人/操作时间、原占用者/占用时间。
     * 写入失败抛 503，调用方因此不会执行删除，避免「解锁无痕」。
     */
    private void writeUnlockAudit(Long bomId, Long userId, String operator, String displayName,
                                  Lease lease, long atMillis, String outcome) {
        if (operLogMapper == null) {
            log.error("强制解锁审计组件缺失，拒绝解锁 bomId={} operator={}", bomId, operator);
            throw new BusinessException(503, MSG_AUDIT_FAILED);
        }
        String ownerName = lease == null || lease.displayName == null || lease.displayName.isBlank()
                ? "未知" : lease.displayName;
        Object ownerId = lease == null ? null : lease.userId;
        Object acquiredAt = lease == null || lease.acquiredAt == null ? null : lease.acquiredAt;
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("bomId", bomId);
        detail.put("operator", operator);
        detail.put("operatorUserId", userId);
        detail.put("ownerDisplayName", ownerName);
        detail.put("ownerUserId", ownerId);
        detail.put("ownerAcquiredAt", acquiredAt);
        detail.put("unlockAt", atMillis);
        detail.put("outcome", outcome);

        SysOperLog operLog = new SysOperLog();
        operLog.setModule(FORCE_UNLOCK_MODULE);
        operLog.setBusinessType(BusinessType.UPDATE.getCode());
        operLog.setOperUrl(FORCE_UNLOCK_OPER_URL);
        operLog.setAction("强制解锁BOM编辑占用");
        operLog.setBizType("engineering_bom");
        operLog.setBizId(String.valueOf(bomId));
        operLog.setOperParam("bomId=" + bomId + "；操作人=" + operator + "(" + userId + ")；原占用者="
                + ownerName + "(" + ownerId + ")；" + outcome);
        operLog.setDetail(toJson(detail));
        operLog.setStatus(YesNoEnum.YES.getCode());
        operLog.setUsername(operator);
        operLog.setRealName(displayName);
        operLog.setUserId(userId);
        operLog.setCreateTime(LocalDateTime.now());
        int inserted;
        try {
            inserted = operLogMapper.insert(operLog);
        } catch (RuntimeException ex) {
            log.error("强制解锁审计写入异常 bomId={} operator={}: {}", bomId, operator, ex.getMessage());
            throw new BusinessException(503, MSG_AUDIT_FAILED);
        }
        if (inserted != 1) {
            log.error("强制解锁审计写入失败 bomId={} operator={} inserted={}", bomId, operator, inserted);
            throw new BusinessException(503, MSG_AUDIT_FAILED);
        }
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    /** 当前登录 userId；缺失返回 null（仅 describe 用于降级为只读）。 */
    private static Long currentUserId() {
        try {
            return com.jjx.system.utils.SecurityUtils.getUserId();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /** 变更/校验类操作必须拿到登录身份，否则 fail-closed（不写租约、不放行保存）。 */
    private static Long requireUserId() {
        Long userId = currentUserId();
        if (userId == null) {
            throw new BusinessException(401, MSG_LOGIN_REQUIRED);
        }
        return userId;
    }

    private static String currentDisplayName() {
        try {
            return com.jjx.system.utils.SecurityUtils.getDisplayName();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static String currentUsername() {
        try {
            String username = com.jjx.system.utils.SecurityUtils.getUsername();
            return username == null || username.isBlank() ? currentDisplayName() : username;
        } catch (RuntimeException ex) {
            return currentDisplayName();
        }
    }

    public static String toJson(Object value) {
        try {
            return JSON.writeValueAsString(value);
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
