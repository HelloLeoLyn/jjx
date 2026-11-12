package com.jjx.product.service;

import com.jjx.common.exception.BusinessException;
import com.jjx.product.domain.vo.BomEditSessionVO;
import com.jjx.product.service.impl.BomEditSessionServiceImpl;
import com.jjx.system.domain.entity.SysOperLog;
import com.jjx.system.mapper.SysOperLogMapper;
import com.jjx.system.utils.SecurityUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BOM 编辑占用会话（dev-20261111-018 修复卡 F1/F2/F3）。
 *
 * <p>用内存替身覆盖 NX/续租/释放/Lua compare/账号归属/强制解锁审计语义；
 * <b>不</b>代表真实 Redis 并发已验证（真实 Redis 故障与并发列在报告“未验”）。
 */
public class BomEditSessionServiceTest {

    /** 内存版租约存储：只实现被覆盖的 4 个访问点，不需要真实 Redis；审计写入也落内存。 */
    public static class FakeRedis extends BomEditSessionServiceImpl {
        public final Map<String, String> store = new HashMap<>();
        public boolean broken = false;
        /** 模拟“读取后被并发替换”：下一次强制解锁删除时先把值换掉。 */
        public boolean replaceBeforeForceDelete = false;
        public String replacedValue = null;

        public FakeRedis(SysOperLogMapper operLogMapper) {
            super(null, operLogMapper);
        }

        @Override
        protected Boolean setIfAbsent(String key, String value, long ttlSeconds) {
            if (broken) throw new IllegalStateException("redis down");
            if (store.containsKey(key)) return false;
            store.put(key, value);
            return true;
        }

        @Override
        protected String getValue(String key) {
            if (broken) throw new IllegalStateException("redis down");
            return store.get(key);
        }

        @Override
        protected Long eval(String script, String key, Object... args) {
            if (broken) throw new IllegalStateException("redis down");
            String raw = store.get(key);
            if (raw == null) return 0L;
            if (FORCE_RELEASE_SCRIPT.equals(script)) {
                if (replaceBeforeForceDelete) {
                    replaceBeforeForceDelete = false;
                    store.put(key, replacedValue);
                    return 0L;
                }
                if (!raw.equals(String.valueOf(args[0]))) return 0L;
                store.remove(key);
                return 1L;
            }
            String token = String.valueOf(args[0]);
            if (!raw.contains("\"token\":\"" + token + "\"")) return 0L;
            if (RELEASE_SCRIPT.equals(script)) {
                store.remove(key);
                return 1L;
            }
            store.put(key, String.valueOf(args[2]));
            return 1L;
        }

        @Override
        protected Long ttlMillis(String key) {
            return store.containsKey(key) ? TTL_SECONDS * 1000L : -2L;
        }

        public void expire(String key) {
            store.remove(key);
        }
    }

    private final List<SysOperLog> audits = new ArrayList<>();
    private SysOperLogMapper auditMapper;
    private FakeRedis redis;
    private MockedStatic<SecurityUtils> security;

    @BeforeEach
    void setUp() {
        audits.clear();
        auditMapper = auditMapper(audits, 1);
        redis = new FakeRedis(auditMapper);
        security = Mockito.mockStatic(SecurityUtils.class);
        security.when(SecurityUtils::getUserId).thenReturn(7L);
        security.when(SecurityUtils::getDisplayName).thenReturn("张三");
        security.when(SecurityUtils::getUsername).thenReturn("zhangsan");
    }

    @AfterEach
    void tearDown() {
        security.close();
    }

    private static SysOperLogMapper auditMapper(List<SysOperLog> sink, int affected) {
        SysOperLogMapper mapper = Mockito.mock(SysOperLogMapper.class);
        Mockito.when(mapper.insert(Mockito.any(SysOperLog.class))).thenAnswer(invocation -> {
            if (affected > 0) sink.add(invocation.getArgument(0));
            return affected;
        });
        return mapper;
    }

    private void loginAs(Long userId, String displayName, String username) {
        security.when(SecurityUtils::getUserId).thenReturn(userId);
        security.when(SecurityUtils::getDisplayName).thenReturn(displayName);
        security.when(SecurityUtils::getUsername).thenReturn(username);
    }

    @Test
    void firstAcquireWinsAndSecondWindowIsReadOnly() {
        BomEditSessionVO first = redis.acquire(1001L, "tab-A");
        assertTrue(first.isAcquired());
        assertTrue(first.isLeaseAvailable());
        assertNotNull(first.getToken());
        assertEquals("张三", first.getOwnerDisplayName());
        assertNotNull(first.getExpiresAt());

        BomEditSessionVO second = redis.acquire(1001L, "tab-B");
        assertFalse(second.isAcquired(), "其他窗口不得同时取得编辑权");
        assertTrue(second.isOccupied());
        assertNull(second.getToken(), "不得返回他人 token");
        assertEquals("张三", second.getOwnerDisplayName());
        assertTrue(second.isOwnerIsSameUser(), "同账号信息可回显，但 token 绝不下发");
        assertTrue(second.getMessage().contains("另一个窗口"), second.getMessage());
    }

    @Test
    void sameAccountSecondWindowIsAlsoReadOnlyAndNeedsNoTakeover() {
        BomEditSessionVO first = redis.acquire(1001L, "tab-A");
        BomEditSessionVO second = redis.acquire(1001L, "tab-B");
        assertFalse(second.isAcquired(), "同账号另一窗口不得默认接管");
        assertTrue(second.isOccupied());
        assertNull(second.getToken());
        // 原窗口继续有效，未被静默替换
        assertEquals(first.getToken(), redis.describe(1001L, "tab-A").getToken());
    }

    @Test
    void sameClientRefreshesItsOwnLease() {
        BomEditSessionVO first = redis.acquire(1001L, "tab-A");
        BomEditSessionVO again = redis.acquire(1001L, "tab-A");
        assertTrue(again.isAcquired());
        assertEquals(first.getToken(), again.getToken(), "同一标签页刷新可继续持有自己的租约");
    }

    // ==================== F1 账号归属：clientId 不是权限凭证 ====================

    @Test
    void sameClientDifferentUserCannotAcquireOrSeeToken() {
        BomEditSessionVO mine = redis.acquire(1001L, "tab-A");
        String stored = redis.store.get(BomEditSessionServiceImpl.key(1001L));

        loginAs(8L, "李四", "lisi");
        BomEditSessionVO stolen = redis.acquire(1001L, "tab-A");
        assertFalse(stolen.isAcquired(), "同 clientId 不同账号不得取得编辑权");
        assertNull(stolen.getToken(), "同 clientId 不同账号不得拿到 token");
        assertFalse(stolen.isOwnerIsSameClient(), "clientId 相同但账号不同，不能标成本窗口");
        assertFalse(stolen.isOwnerIsSameUser());
        assertTrue(stolen.getMessage().contains("只读"), stolen.getMessage());
        assertEquals(stored, redis.store.get(BomEditSessionServiceImpl.key(1001L)),
                "被拒绝的申请不得改写原租约");

        BomEditSessionVO described = redis.describe(1001L, "tab-A");
        assertNull(described.getToken(), "describe 也不能把他人 token 交给同 clientId 的另一账号");
        assertFalse(described.isAcquired());
        assertFalse(described.isOwnerIsSameClient());
        assertTrue(described.isOccupied());

        loginAs(7L, "张三", "zhangsan");
        assertEquals(mine.getToken(), redis.describe(1001L, "tab-A").getToken(),
                "原租约保持不变，原账号仍可继续编辑");
    }

    @Test
    void renewWithOtherUserRejected() {
        BomEditSessionVO mine = redis.acquire(1001L, "tab-A");
        String stored = redis.store.get(BomEditSessionServiceImpl.key(1001L));

        loginAs(8L, "李四", "lisi");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> redis.renew(1001L, "tab-A", mine.getToken()));
        assertEquals(Integer.valueOf(409), ex.getCode());
        assertTrue(ex.getMessage().contains("BOM_EDIT_LEASE_FORBIDDEN"), ex.getMessage());
        assertEquals(stored, redis.store.get(BomEditSessionServiceImpl.key(1001L)),
                "他人不得续租，租约内容（含 lastSeenAt）不得被改写");
    }

    @Test
    void releaseWithOtherUserRejected() {
        BomEditSessionVO mine = redis.acquire(1001L, "tab-A");
        String stored = redis.store.get(BomEditSessionServiceImpl.key(1001L));

        loginAs(8L, "李四", "lisi");
        assertFalse(redis.release(1001L, mine.getToken()), "他人不得释放别人的租约");
        assertEquals(stored, redis.store.get(BomEditSessionServiceImpl.key(1001L)));

        loginAs(7L, "张三", "zhangsan");
        assertTrue(redis.release(1001L, mine.getToken()), "本人仍可正常释放");
    }

    @Test
    void saveWithOtherUserRejected() {
        BomEditSessionVO mine = redis.acquire(1001L, "tab-A");

        loginAs(8L, "李四", "lisi");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> redis.verifySaveToken(1001L, mine.getToken()));
        assertEquals(Integer.valueOf(409), ex.getCode());
        assertTrue(ex.getMessage().contains("BOM_EDIT_LEASE_FORBIDDEN"), ex.getMessage());
    }

    // ==================== F2 登录身份缺失 fail-closed ====================

    @Test
    void acquireWithoutIdentityWritesNothing() {
        security.when(SecurityUtils::getUserId).thenThrow(new IllegalStateException("no login"));
        BusinessException ex = assertThrows(BusinessException.class, () -> redis.acquire(1001L, "tab-A"));
        assertEquals(Integer.valueOf(401), ex.getCode());
        assertTrue(ex.getMessage().contains("BOM_EDIT_LOGIN_REQUIRED"), ex.getMessage());
        assertTrue(redis.store.isEmpty(), "身份缺失不得写入租约");

        security.when(SecurityUtils::getUserId).thenReturn(null);
        assertEquals(Integer.valueOf(401), assertThrows(BusinessException.class,
                () -> redis.acquire(1001L, "tab-A")).getCode());
        assertTrue(redis.store.isEmpty());
    }

    @Test
    void renewAndReleaseWithoutIdentityAreRejected() {
        BomEditSessionVO mine = redis.acquire(1001L, "tab-A");
        String stored = redis.store.get(BomEditSessionServiceImpl.key(1001L));
        security.when(SecurityUtils::getUserId).thenReturn(null);

        assertEquals(Integer.valueOf(401), assertThrows(BusinessException.class,
                () -> redis.renew(1001L, "tab-A", mine.getToken())).getCode());
        assertEquals(Integer.valueOf(401), assertThrows(BusinessException.class,
                () -> redis.release(1001L, mine.getToken())).getCode());
        assertEquals(stored, redis.store.get(BomEditSessionServiceImpl.key(1001L)),
                "身份缺失不得续租/释放");
    }

    @Test
    void saveWithoutIdentityIsRejectedAndNothingDeleted() {
        BomEditSessionVO mine = redis.acquire(1001L, "tab-A");
        security.when(SecurityUtils::getUserId).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> redis.verifySaveToken(1001L, mine.getToken()));
        assertEquals(Integer.valueOf(401), ex.getCode());
        assertTrue(redis.store.containsKey(BomEditSessionServiceImpl.key(1001L)));
    }

    @Test
    void forceUnlockWithoutIdentityIsRejectedWithoutAudit() {
        redis.acquire(1001L, "tab-A");
        security.when(SecurityUtils::getUserId).thenReturn(null);

        assertEquals(Integer.valueOf(401), assertThrows(BusinessException.class,
                () -> redis.forceUnlock(1001L)).getCode());
        assertTrue(audits.isEmpty(), "身份缺失不得写审计");
        assertTrue(redis.store.containsKey(BomEditSessionServiceImpl.key(1001L)));
    }

    @Test
    void describeWithoutIdentityIsReadOnlyAndHidesToken() {
        redis.acquire(1001L, "tab-A");
        security.when(SecurityUtils::getUserId).thenReturn(null);

        BomEditSessionVO vo = redis.describe(1001L, "tab-A");
        assertFalse(vo.isLeaseAvailable(), "身份缺失按不可用/只读处理");
        assertFalse(vo.isAcquired());
        assertNull(vo.getToken());
        assertFalse(vo.isOwnerIsSameUser());
    }

    // ==================== 续租/释放/保存基础语义 ====================

    @Test
    void renewRequiresMatchingToken() {
        BomEditSessionVO lease = redis.acquire(1001L, "tab-A");
        BomEditSessionVO renewed = redis.renew(1001L, "tab-A", lease.getToken());
        assertTrue(renewed.isAcquired());
        assertEquals(lease.getToken(), renewed.getToken());

        BusinessException wrong = assertThrows(BusinessException.class,
                () -> redis.renew(1001L, "tab-B", "not-my-token"));
        assertEquals(Integer.valueOf(409), wrong.getCode());
        assertTrue(wrong.getMessage().contains("BOM_EDIT_LEASE_LOST"));
    }

    @Test
    void releaseIsCompareTokenAndMismatchKeepsLease() {
        BomEditSessionVO lease = redis.acquire(1001L, "tab-A");
        assertFalse(redis.release(1001L, "other-token"), "旧窗口不能释放新窗口的租约");
        assertNotNull(redis.describe(1001L, "tab-A").getToken(), "不匹配的释放不能删除租约");

        assertTrue(redis.release(1001L, lease.getToken()));
        assertNull(redis.describe(1001L, "tab-A").getToken());
        assertFalse(redis.describe(1001L, "tab-A").isOccupied(), "释放后可被其他窗口获取");
    }

    @Test
    void expiredLeaseAllowsAnotherWindowAndRejectsOldToken() {
        BomEditSessionVO lease = redis.acquire(1001L, "tab-A");
        redis.expire(BomEditSessionServiceImpl.key(1001L));

        BusinessException expired = assertThrows(BusinessException.class,
                () -> redis.verifySaveToken(1001L, lease.getToken()));
        assertEquals(Integer.valueOf(409), expired.getCode());
        assertTrue(expired.getMessage().contains("BOM_EDIT_LEASE_EXPIRED"));
        // 期望：旧窗口保存被拒，但页面草稿保留由前端负责（本测试只覆盖服务端拒绝）

        BomEditSessionVO next = redis.acquire(1001L, "tab-B");
        assertTrue(next.isAcquired(), "TTL 到期后租约释放，其他窗口可获取");
    }

    @Test
    void replacedTokenCannotSave() {
        BomEditSessionVO lease = redis.acquire(1001L, "tab-A");
        redis.expire(BomEditSessionServiceImpl.key(1001L));
        redis.acquire(1001L, "tab-B");

        BusinessException held = assertThrows(BusinessException.class,
                () -> redis.verifySaveToken(1001L, lease.getToken()));
        assertEquals(Integer.valueOf(409), held.getCode());
        assertTrue(held.getMessage().contains("BOM_EDIT_LEASE_HELD"));
    }

    @Test
    void missingTokenIsRejected() {
        redis.acquire(1001L, "tab-A");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> redis.verifySaveToken(1001L, "  "));
        assertEquals(Integer.valueOf(409), ex.getCode());
        assertTrue(ex.getMessage().contains("BOM_EDIT_LEASE_REQUIRED"));
    }

    @Test
    void validTokenPasses() {
        BomEditSessionVO lease = redis.acquire(1001L, "tab-A");
        redis.verifySaveToken(1001L, lease.getToken());
    }

    @Test
    void redisFailureIsFailClosedEverywhere() {
        BomEditSessionVO lease = redis.acquire(1001L, "tab-A");
        redis.broken = true;

        BusinessException save = assertThrows(BusinessException.class,
                () -> redis.verifySaveToken(1001L, lease.getToken()));
        assertEquals(Integer.valueOf(503), save.getCode(), "Redis 异常必须拒绝保存，不能 fail-open");
        assertTrue(save.getMessage().contains("EDIT_LEASE_UNAVAILABLE"));

        BusinessException acquire = assertThrows(BusinessException.class,
                () -> redis.acquire(1001L, "tab-B"));
        assertEquals(Integer.valueOf(503), acquire.getCode());

        BusinessException renew = assertThrows(BusinessException.class,
                () -> redis.renew(1001L, "tab-A", lease.getToken()));
        assertEquals(Integer.valueOf(503), renew.getCode());

        BusinessException unlock = assertThrows(BusinessException.class, () -> redis.forceUnlock(1001L));
        assertEquals(Integer.valueOf(503), unlock.getCode(), "Redis 异常不得假装解锁");
        assertTrue(audits.isEmpty(), "读取都失败时不得写审计");

        BomEditSessionVO described = redis.describe(1001L, "tab-A");
        assertFalse(described.isLeaseAvailable(), "占用服务不可用时前端必须只读");
        assertTrue(described.getMessage().contains("EDIT_LEASE_UNAVAILABLE"));
    }

    // ==================== F3 真实强制解锁 + 持久审计 ====================

    @Test
    void forceUnlockReallyRemovesLeaseAndWritesAudit() {
        BomEditSessionVO mine = redis.acquire(1001L, "tab-A");
        BomEditSessionVO unlocked = redis.forceUnlock(1001L);

        assertTrue(unlocked.isLeaseAvailable());
        assertFalse(unlocked.isOccupied(), "解锁后不再被占用");
        assertEquals("张三", unlocked.getOwnerDisplayName(), "返回被解除的占用者");
        assertNull(unlocked.getToken(), "解锁接口不回传 token");
        assertFalse(redis.store.containsKey(BomEditSessionServiceImpl.key(1001L)), "租约必须真的被删除");

        assertEquals(1, audits.size(), "必须写一条 sys_oper_log 审计");
        SysOperLog operLog = audits.get(0);
        assertEquals("1001", operLog.getBizId());
        assertEquals(BomEditSessionServiceImpl.FORCE_UNLOCK_PERMISSION, operLog.getOperUrl());
        assertEquals("engineering_bom", operLog.getBizType());
        assertEquals(Long.valueOf(7L), operLog.getUserId(), "审计记录操作人");
        assertTrue(operLog.getOperParam().contains("bomId=1001"), operLog.getOperParam());
        assertTrue(operLog.getOperParam().contains("张三"), operLog.getOperParam());
        assertTrue(operLog.getDetail().contains("\"ownerUserId\":7"), operLog.getDetail());
        assertNotNull(operLog.getCreateTime());

        // 解锁后普通 acquire 可重新取得，且旧 token 失效
        BomEditSessionVO next = redis.acquire(1001L, "tab-B");
        assertTrue(next.isAcquired());
        assertTrue(!next.getToken().equals(mine.getToken()));
    }

    @Test
    void forceUnlockWithoutLeaseIsNotAnErrorAndWritesNoAudit() {
        BomEditSessionVO unlocked = redis.forceUnlock(1001L);
        assertTrue(unlocked.isLeaseAvailable());
        assertFalse(unlocked.isOccupied());
        assertTrue(unlocked.getMessage().contains("当前无占用"), unlocked.getMessage());
        assertTrue(audits.isEmpty());
    }

    @Test
    void forceUnlockAuditFailureKeepsLease() {
        redis = new FakeRedis(auditMapper(audits, 0));
        redis.acquire(1001L, "tab-A");

        BusinessException ex = assertThrows(BusinessException.class, () -> redis.forceUnlock(1001L));
        assertEquals(Integer.valueOf(503), ex.getCode());
        assertTrue(ex.getMessage().contains("BOM_EDIT_FORCE_UNLOCK_AUDIT_FAILED"), ex.getMessage());
        assertTrue(redis.store.containsKey(BomEditSessionServiceImpl.key(1001L)),
                "审计写不进去时不得解锁，避免无痕成功");
    }

    @Test
    void forceUnlockDoesNotDeleteReplacedLease() {
        redis.acquire(1001L, "tab-A");
        redis.replacedValue = BomEditSessionServiceImpl.toJson(
                new BomEditSessionServiceImpl.Lease("token-B", 9L, "李四", "tab-B", 1L, 1L));
        redis.replaceBeforeForceDelete = true;

        BomEditSessionVO unlocked = redis.forceUnlock(1001L);
        assertTrue(unlocked.getMessage().contains("未生效"), unlocked.getMessage());
        assertEquals(redis.replacedValue, redis.store.get(BomEditSessionServiceImpl.key(1001L)),
                "读取后已被替换的占有没有被误删");
        assertEquals(2, audits.size(), "前一条请求 + 后一条未生效留痕");
        assertTrue(audits.get(1).getOperParam().contains("未生效"), audits.get(1).getOperParam());
    }

    @Test
    void clientIdIsRequired() {
        BusinessException ex = assertThrows(BusinessException.class, () -> redis.acquire(1001L, " "));
        assertEquals(Integer.valueOf(400), ex.getCode());
        assertTrue(ex.getMessage().contains("clientId"));
    }

    @Test
    void describeWithoutLeaseReportsAvailableAndFree() {
        BomEditSessionVO described = redis.describe(1001L, "tab-A");
        assertTrue(described.isLeaseAvailable());
        assertFalse(described.isOccupied());
        assertFalse(described.isAcquired());
    }
}
