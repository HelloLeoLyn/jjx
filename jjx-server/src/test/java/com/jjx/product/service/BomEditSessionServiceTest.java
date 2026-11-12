package com.jjx.product.service;

import com.jjx.common.exception.BusinessException;
import com.jjx.product.domain.vo.BomEditSessionVO;
import com.jjx.product.service.impl.BomEditSessionServiceImpl;
import com.jjx.system.utils.SecurityUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BOM 编辑占用会话（dev-20261111-018）。
 *
 * <p>用内存替身覆盖 NX/续租/释放/Lua compare 语义与 fail-closed；<b>不</b>代表真实 Redis 并发已验证。
 */
class BomEditSessionServiceTest {

    /** 内存版租约存储：只实现被覆盖的 4 个访问点，不需要真实 Redis。 */
    static class FakeRedis extends BomEditSessionServiceImpl {
        final Map<String, String> store = new HashMap<>();
        boolean broken = false;

        FakeRedis() {
            super(null);
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

        void expire(String key) {
            store.remove(key);
        }
    }

    private FakeRedis redis;
    private MockedStatic<SecurityUtils> security;

    @BeforeEach
    void setUp() {
        redis = new FakeRedis();
        security = Mockito.mockStatic(SecurityUtils.class);
        security.when(SecurityUtils::getUserId).thenReturn(7L);
        security.when(SecurityUtils::getDisplayName).thenReturn("张三");
    }

    @AfterEach
    void tearDown() {
        security.close();
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

        BomEditSessionVO described = redis.describe(1001L, "tab-A");
        assertFalse(described.isLeaseAvailable(), "占用服务不可用时前端必须只读");
        assertTrue(described.getMessage().contains("EDIT_LEASE_UNAVAILABLE"));
    }

    @Test
    void forceUnlockIsAlwaysRejectedPendingPermissionConfig() {
        BusinessException ex = assertThrows(BusinessException.class, () -> redis.forceUnlock(1001L));
        assertEquals(Integer.valueOf(403), ex.getCode());
        assertTrue(ex.getMessage().contains("engineering:bom:edit:force-unlock"),
                "管理员解锁必须走专用权限码，不能用 engineering:bom:edit 冒充");
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
