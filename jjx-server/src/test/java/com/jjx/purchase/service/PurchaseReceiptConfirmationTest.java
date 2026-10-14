package com.jjx.purchase.service;

import cn.dev33.satoken.stp.StpUtil;
import com.jjx.common.enums.ApproveStatusEnum;
import com.jjx.common.exception.BusinessException;
import com.jjx.event.EventPublisher;
import com.jjx.inventory.service.InventoryInboundService;
import com.jjx.purchase.domain.dto.PurchaseOrderReceiveDTO;
import com.jjx.purchase.domain.entity.PurchaseOrder;
import com.jjx.purchase.domain.entity.PurchaseOrderItem;
import com.jjx.purchase.mapper.PurchaseOrderMapper;
import com.jjx.purchase.mapper.PurchaseOrderItemMapper;
import com.jjx.purchase.service.impl.PurchaseOrderServiceImpl;
import com.jjx.system.domain.dto.AttachmentRefDTO;
import com.jjx.system.domain.entity.SysAttachment;
import com.jjx.system.enums.AttachmentUploadState;
import com.jjx.system.mapper.SysAttachmentMapper;
import com.jjx.system.service.AttachmentRefService;
import com.jjx.system.utils.SecurityUtils;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PurchaseReceiptConfirmationTest {
    JdbcTemplate jdbc;
    IPurchaseOrderService orders;
    PurchaseReceiptAttachmentService attachments;
    AttachmentRefService refs;
    MockedStatic<StpUtil> auth;
    MockedStatic<SecurityUtils> security;

    @BeforeEach void setup() {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE purchase_order(order_id BIGINT PRIMARY KEY)");
        jdbc.execute("CREATE TABLE purchase_order_item(item_id BIGINT PRIMARY KEY, received DECIMAL(12,2))");
        jdbc.execute("CREATE TABLE inventory_inbound_order(inbound_id BIGINT AUTO_INCREMENT PRIMARY KEY,inbound_no VARCHAR(40),source_type VARCHAR(30),source_id BIGINT,remark VARCHAR(500))");
        jdbc.execute("CREATE TABLE sys_attachment(id BIGINT PRIMARY KEY,upload_state VARCHAR(20),owner VARCHAR(20),mime VARCHAR(80),size BIGINT,expires TIMESTAMP)");
        jdbc.execute("CREATE TABLE sys_attachment_ref(attachment_id BIGINT,source_id BIGINT,source_type VARCHAR(30))");
        jdbc.update("INSERT INTO purchase_order VALUES(1)");
        jdbc.update("INSERT INTO purchase_order_item VALUES(10,0)");
        file(100, "7", "image/png", 100, LocalDateTime.now().plusDays(1));
        file(101, "7", "image/jpeg", 100, LocalDateTime.now().plusDays(1));
        auth = mockStatic(StpUtil.class);
        auth.when(StpUtil::getLoginIdAsString).thenReturn("7");
        security = mockStatic(SecurityUtils.class);
        security.when(SecurityUtils::getUserId).thenReturn(7L);
        security.when(SecurityUtils::getUsername).thenReturn("tester");
        SysAttachmentMapper files = mock(SysAttachmentMapper.class);
        when(files.selectActiveForUpdate(anyLong())).thenAnswer(call -> {
            var rows = jdbc.queryForList("SELECT * FROM sys_attachment WHERE id=? FOR UPDATE", call.getArgument(0, Long.class));
            if (rows.isEmpty()) return null;
            var r = rows.get(0); var f = new SysAttachment();
            f.setId(((Number) r.get("id")).longValue()); f.setUploadState(AttachmentUploadState.valueOf((String) r.get("upload_state")));
            f.setUploadOwner((String) r.get("owner")); f.setFileType((String) r.get("mime")); f.setFileSize(((Number) r.get("size")).longValue());
            f.setExpiresAt(((java.sql.Timestamp) r.get("expires")).toLocalDateTime()); return f;
        });
        refs = mock(AttachmentRefService.class);
        when(refs.create(any())).thenAnswer(call -> {
            AttachmentRefDTO r = call.getArgument(0);
            jdbc.update("INSERT INTO sys_attachment_ref VALUES(?,?,?)", r.getAttachmentId(), r.getSourceId(), r.getSourceType());
            jdbc.update("UPDATE sys_attachment SET upload_state=? WHERE id=?", AttachmentUploadState.BOUND.name(), r.getAttachmentId());
            return r.getAttachmentId();
        });
        attachments = new PurchaseReceiptAttachmentService(jdbc, files, refs);
        var orderMapper = mock(PurchaseOrderMapper.class);
        var itemMapper = mock(PurchaseOrderItemMapper.class);
        var inbound = mock(InventoryInboundService.class);
        PurchaseOrder order = new PurchaseOrder(); order.setOrderId(1L); order.setOrderNo("PO-1"); order.setApprovalStatus(ApproveStatusEnum.APPROVED.getValue());
        when(orderMapper.selectById(1L)).thenReturn(order);
        when(itemMapper.selectById(10L)).thenAnswer(c -> {
            var item = new PurchaseOrderItem(); item.setItemId(10L); item.setOrderId(1L); item.setQuantity(new BigDecimal("100"));
            item.setReceivedQuantity(jdbc.queryForObject("SELECT received FROM purchase_order_item WHERE item_id=10", BigDecimal.class)); return item;
        });
        when(itemMapper.updateById(any(PurchaseOrderItem.class))).thenAnswer(c -> jdbc.update("UPDATE purchase_order_item SET received=? WHERE item_id=10", ((PurchaseOrderItem)c.getArgument(0)).getReceivedQuantity()));
        when(itemMapper.selectList(any())).thenReturn(List.of());
        when(inbound.createInboundRecordFromPurchase(1L)).thenAnswer(c -> {
            jdbc.update("INSERT INTO inventory_inbound_order(inbound_no,source_type,source_id,remark) VALUES('IN-test','PURCHASE',1,'采购收货自动入库')");
            return jdbc.queryForObject("SELECT MAX(inbound_id) FROM inventory_inbound_order", Long.class);
        });
        var service = mock(PurchaseOrderServiceImpl.class, CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(service, "orderMapper", orderMapper);
        ReflectionTestUtils.setField(service, "orderItemMapper", itemMapper);
        ReflectionTestUtils.setField(service, "inboundService", inbound);
        ReflectionTestUtils.setField(service, "receiptAttachments", attachments);
        ReflectionTestUtils.setField(service, "eventPublisher", mock(EventPublisher.class));
        var proxy = new ProxyFactory(service);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(ds), new AnnotationTransactionAttributeSource()));
        orders = (IPurchaseOrderService) proxy.getProxy();
    }
    @AfterEach void close() { auth.close(); security.close(); }
    void file(long id, String owner, String mime, long size, LocalDateTime expiry) {
        jdbc.update("INSERT INTO sys_attachment VALUES(?,?,?,?,?,?)",id,AttachmentUploadState.TEMP.name(),owner,mime,size,expiry);
    }
    PurchaseOrderReceiveDTO request(String key, Long... ids) {
        var dto = new PurchaseOrderReceiveDTO(); dto.setOrderId(1L); dto.setReceiptRequestKey(key); dto.setAttachmentIds(List.of(ids));
        var item = new PurchaseOrderReceiveDTO.ReceiveItemDTO(); item.setItemId(10L); item.setReceivedQuantity(new BigDecimal("5")); dto.setItems(List.of(item)); return dto;
    }
    int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table,Integer.class); }

    @Test void successRetryAndSeparateReceipts() {
        var first=request("receipt-draft-0001",100L,100L); assertEquals(1,orders.batchReceiveOrderItems(first));
        var retry=request("receipt-draft-0001",100L); assertEquals(0,orders.batchReceiveOrderItems(retry));
        assertEquals(first.getInboundId(),retry.getInboundId()); assertEquals(1,count("sys_attachment_ref"));
        var second=request("receipt-draft-0002",101L); orders.batchReceiveOrderItems(second);
        assertNotEquals(first.getInboundId(),second.getInboundId()); assertEquals(2,count("inventory_inbound_order"));
        assertEquals(0,new BigDecimal("10").compareTo(jdbc.queryForObject("SELECT received FROM purchase_order_item",BigDecimal.class)));
        assertEquals(List.of(first.getInboundId(),second.getInboundId()),jdbc.queryForList("SELECT source_id FROM sys_attachment_ref ORDER BY attachment_id",Long.class));
    }
    @Test void bindingFailureRollsBackReceiptAndPreviouslyBoundFilesThenCanRetry() {
        doAnswer(c -> { if (((AttachmentRefDTO)c.getArgument(0)).getAttachmentId()==101L) throw new BusinessException("绑定失败");
            jdbc.update("INSERT INTO sys_attachment_ref VALUES(100,1,'purchase_receipt')");
            jdbc.update("UPDATE sys_attachment SET upload_state='BOUND' WHERE id=100");return 1L;
        }).when(refs).create(any());
        assertThrows(BusinessException.class,()->orders.batchReceiveOrderItems(request("receipt-draft-0001",100L,101L)));
        assertEquals(0,count("inventory_inbound_order")); assertEquals(0,count("sys_attachment_ref"));
        assertEquals(0,BigDecimal.ZERO.compareTo(jdbc.queryForObject("SELECT received FROM purchase_order_item",BigDecimal.class)));
        assertEquals(AttachmentUploadState.TEMP.name(),jdbc.queryForObject("SELECT upload_state FROM sys_attachment WHERE id=100",String.class));
        doReturn(1L).when(refs).create(any());
        assertEquals(1,orders.batchReceiveOrderItems(request("receipt-draft-0001",100L,101L)));
    }
    @Test void deniedPermissionCannotReceiveWithFiles() {
        auth.when(()->StpUtil.checkPermission("purchase:receipt:doc:add")).thenThrow(new BusinessException("无权上传"));
        assertThrows(BusinessException.class,()->orders.batchReceiveOrderItems(request("receipt-draft-0001",100L)));
        assertEquals(0,count("inventory_inbound_order"));
        assertEquals(1,orders.batchReceiveOrderItems(request("receipt-draft-0002")));
    }
    @Test void foreignExpiredOversizeNonImageAndReusedFilesAreRejected() {
        file(200,"8","image/png",100,LocalDateTime.now().plusDays(1));
        file(201,"7","image/png",100,LocalDateTime.now().minusDays(1));
        file(202,"7","application/pdf",100,LocalDateTime.now().plusDays(1));
        file(203,"7","image/png",11L*1024*1024,LocalDateTime.now().plusDays(1));
        for(long id=200;id<=203;id++){ final long bad=id; assertThrows(BusinessException.class,()->orders.batchReceiveOrderItems(request("receipt-draft-0001",bad))); }
        orders.batchReceiveOrderItems(request("receipt-draft-0001",100L));
        assertThrows(BusinessException.class,()->orders.batchReceiveOrderItems(request("receipt-draft-0002",100L)));
        assertEquals(1,count("inventory_inbound_order"));
    }
    @Test void sameKeyCannotChangeConfirmedPayloadAndNoFileRetryIsIdempotent() {
        orders.batchReceiveOrderItems(request("receipt-draft-0001"));
        assertEquals(0,orders.batchReceiveOrderItems(request("receipt-draft-0001")));
        var changed=request("receipt-draft-0001");changed.getItems().get(0).setReceivedQuantity(BigDecimal.TEN);
        assertThrows(BusinessException.class,()->orders.batchReceiveOrderItems(changed));
        assertEquals(1,count("inventory_inbound_order"));
    }
}
