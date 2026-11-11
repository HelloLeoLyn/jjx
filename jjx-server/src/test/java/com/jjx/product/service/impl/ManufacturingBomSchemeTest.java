package com.jjx.product.service.impl;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjx.common.exception.BusinessException;
import com.jjx.engineering.controller.BomController;
import com.jjx.engineering.domain.entity.EngineeringBom;
import com.jjx.engineering.domain.entity.EngineeringBomItem;
import com.jjx.inventory.domain.InventoryMaterial;
import com.jjx.inventory.mapper.InventoryMaterialMapper;
import com.jjx.product.domain.converter.EngineeringBomConverterImpl;
import com.jjx.product.domain.dto.EngineeringBomDTO;
import com.jjx.product.domain.dto.EngineeringBomItemDTO;
import com.jjx.product.domain.dto.ManufacturingSchemeChoiceDTO;
import com.jjx.product.domain.entity.Product;
import com.jjx.product.domain.vo.ManufacturingSchemeVO;
import com.jjx.product.mapper.EngineeringBomItemMapper;
import com.jjx.product.mapper.EngineeringBomMapper;
import com.jjx.product.mapper.ProductMapper;
import com.jjx.product.service.IEngineeringBomService;
import com.jjx.product.service.ManufacturingBomService;
import com.jjx.system.mapper.SysOperLogMapper;
import com.jjx.system.service.OperLogChangeRecorder;
import com.jjx.system.service.ReviewFlowService;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 制造方案保存回读、产品/物料隔离、主方案同事务与草稿/审批边界（dev-20261111-004）。 */
class ManufacturingBomSchemeTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    IEngineeringBomService service;
    ManufacturingBomService manufacturing;
    ManufacturingBomService manufacturingTx;
    JdbcTemplate db;
    ProductMapper productMapper;
    EngineeringBomMapper rawBomMapper;

    @BeforeEach
    void setup() {
        var source = new UnpooledDataSource("org.h2.Driver",
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        var configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setEnvironment(new Environment("mfg-scheme", new SpringManagedTransactionFactory(), source));
        configuration.addMapper(EngineeringBomMapper.class);
        configuration.addMapper(EngineeringBomItemMapper.class);
        configuration.addMapper(ProductMapper.class);
        configuration.addMapper(SysOperLogMapper.class);
        var session = new SqlSessionTemplate(new MybatisSqlSessionFactoryBuilder().build(configuration));
        db = new JdbcTemplate(source);
        createTable(EngineeringBom.class);
        createTable(EngineeringBomItem.class);
        createTable(Product.class);
        createTable(com.jjx.system.domain.entity.SysOperLog.class);

        productMapper = session.getMapper(ProductMapper.class);
        var materials = mock(InventoryMaterialMapper.class);
        when(materials.selectById(anyLong())).thenAnswer(invocation -> {
            long id = invocation.getArgument(0);
            var material = new InventoryMaterial();
            material.setMaterialId(id);
            material.setMaterialCode("SFG" + id);
            material.setMaterialName("半成品" + id);
            material.setUnit("PCS");
            material.setStatus(1);
            return material;
        });
        var materialMaster = mock(com.jjx.inventory.master.MaterialMasterService.class);
        when(materialMaster.specificationSource(any()))
                .thenReturn(java.util.Map.of("size", "系统规格", "attributes", java.util.Map.of()));

        var reviewFlow = mock(ReviewFlowService.class);
        var implementation = new EngineeringBomServiceImpl(
                session.getMapper(EngineeringBomMapper.class),
                session.getMapper(EngineeringBomItemMapper.class),
                productMapper, new EngineeringBomConverterImpl(), null, materials,
                reviewFlow, null, mock(OperLogChangeRecorder.class), null);
        ReflectionTestUtils.setField(implementation, "bomSpecificationService",
                new BomSpecificationService(materialMaster, materials, session.getMapper(SysOperLogMapper.class)));
        ReflectionTestUtils.setField(implementation, "baseMapper", session.getMapper(EngineeringBomMapper.class));
        var proxy = new ProxyFactory(implementation);
        proxy.setInterfaces(IEngineeringBomService.class);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(source),
                new AnnotationTransactionAttributeSource()));
        service = (IEngineeringBomService) proxy.getProxy();
        rawBomMapper = session.getMapper(EngineeringBomMapper.class);
        manufacturing = new ManufacturingBomServiceImpl(service, rawBomMapper, productMapper);
        // 实际 HTTP 入口 ManufacturingBomServiceImpl.update 自带 @Transactional 外层事务，单独套代理复现真实快照边界。
        var mfgProxy = new ProxyFactory(manufacturing);
        mfgProxy.setInterfaces(ManufacturingBomService.class);
        mfgProxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(source),
                new AnnotationTransactionAttributeSource()));
        manufacturingTx = (ManufacturingBomService) mfgProxy.getProxy();
    }

    private void createTable(Class<?> entity) {
        var table = TableInfoHelper.getTableInfo(entity);
        var columns = new StringBuilder(table.getKeyColumn() + " BIGINT AUTO_INCREMENT PRIMARY KEY");
        for (var field : table.getFieldList()) {
            Class<?> type = field.getPropertyType();
            String sqlType = type == String.class ? "VARCHAR(4000)"
                    : type == LocalDateTime.class ? "TIMESTAMP"
                    : type == Boolean.class ? "BOOLEAN"
                    : type == BigDecimal.class ? "DECIMAL(18,8)" : "BIGINT";
            columns.append(',').append(field.getColumn()).append(' ').append(sqlType);
        }
        db.execute("CREATE TABLE " + table.getTableName() + " (" + columns + ")");
    }

    private Product product(String code) {
        var product = new Product();
        product.setProductCode(code);
        product.setProductName(code);
        product.setProductStatus(1);
        product.setSchemeRevision(0);
        productMapper.insert(product);
        return product;
    }

    private EngineeringBomItemDTO buyRow(long materialId) {
        var row = new EngineeringBomItemDTO();
        row.setMaterialId(materialId);
        row.setMaterialCode("RM" + materialId);
        row.setMaterialName("原料" + materialId);
        row.setUnit("PCS");
        row.setSourceType("buy");
        row.setModuleQty(new BigDecimal("6"));
        row.setBaseQty(BigDecimal.ONE);
        row.setItemOrder(1);
        return row;
    }

    private EngineeringBomItemDTO makeRow(long materialId, long subBomId) {
        var row = buyRow(materialId);
        row.setSourceType("make");
        row.setSubBomId(subBomId);
        row.setModuleQty(BigDecimal.ONE);
        row.setBaseQty(BigDecimal.ONE);
        return row;
    }

    private EngineeringBomDTO scheme(String code, String version, long materialId) {
        var dto = new EngineeringBomDTO();
        dto.setBomCode(code);
        dto.setBomName(code);
        dto.setBomVersion(version);
        dto.setOutputType("MATERIAL");
        dto.setOutputId(materialId);
        dto.setItems(List.of(buyRow(110)));
        return dto;
    }

    private long scheme(long materialId, String code) {
        return service.createBomReturnId(scheme(code, "V1.0", materialId));
    }

    private ManufacturingSchemeChoiceDTO choice(long materialId, long bomId, Boolean primary) {
        var choice = new ManufacturingSchemeChoiceDTO();
        choice.setMaterialId(materialId);
        choice.setBomId(bomId);
        choice.setIsPrimary(primary);
        return choice;
    }

    private EngineeringBomDTO parent(Long productId, String code, List<EngineeringBomItemDTO> rows,
                                     List<ManufacturingSchemeChoiceDTO> choices, Integer revision) {
        var dto = new EngineeringBomDTO();
        dto.setBomCode(code);
        dto.setBomName(code);
        dto.setBomVersion("V1.0");
        dto.setOutputType("PRODUCT");
        dto.setOutputId(productId);
        dto.setProductId(productId);
        dto.setItems(rows);
        dto.setSchemeChoices(choices);
        dto.setSchemeRevision(revision);
        return dto;
    }

    private EngineeringBomDTO parent(Long productId, long materialId, long childBomId, Integer revision) {
        return parent(productId, "TOP-" + productId + "-" + childBomId, List.of(makeRow(materialId, childBomId)),
                List.of(choice(materialId, childBomId, true)), revision);
    }

    private EngineeringBomDTO withStoredItemIds(EngineeringBomDTO dto) {
        List<Long> itemIds = db.queryForList(
                "SELECT item_id FROM engineering_bom_item WHERE bom_id=? ORDER BY item_id", Long.class, dto.getBomId());
        assertEquals(dto.getItems().size(), itemIds.size(), "行数与库中不一致");
        for (int i = 0; i < itemIds.size(); i++) {
            dto.getItems().get(i).setItemId(itemIds.get(i));
        }
        return dto;
    }

    private String savedJson(Product product) {
        return productMapper.selectById(product.getProductId()).getMaterialSchemeJson();
    }

    private Integer revision(Product product) {
        return productMapper.selectById(product.getProductId()).getSchemeRevision();
    }

    @Test
    void savesReadsSchemesAndKeepsProductChoicesIsolated() {
        var p1 = product("P1");
        var p2 = product("P2");
        long child1 = scheme(349, "SFG349-A");
        long child2 = scheme(349, "SFG349-B");

        long parent1 = service.createBomReturnId(parent(p1.getProductId(), 349, child1, 0));
        long parent2 = service.createBomReturnId(parent(p2.getProductId(), 349, child2, 0));

        assertEquals(Integer.valueOf(1), revision(p1));
        assertEquals(Integer.valueOf(1), revision(p2));
        assertTrue(savedJson(p1).contains("\"bomId\":" + child1));
        assertTrue(savedJson(p2).contains("\"bomId\":" + child2));

        var detail = service.getBomDetail(parent1);
        assertEquals("PRODUCT", detail.getOutputType());
        assertEquals(Integer.valueOf(1), detail.getSchemeRevision());
        assertEquals(Long.valueOf(child1), detail.getSchemeChoices().get(0).getBomId());
        assertEquals(Boolean.TRUE, detail.getSchemeChoices().get(0).getIsPrimary());

        var childDetail = service.getBomDetail(child1);
        assertEquals("MATERIAL", childDetail.getOutputType());
        assertEquals(Long.valueOf(349), childDetail.getOutputId());
        assertEquals("6.00000000", childDetail.getItems().get(0).getModuleQty().toPlainString());

        var options = manufacturing.options("MATERIAL", 349L, p1.getProductId());
        assertEquals(2, options.size());
        assertTrue(options.stream().filter(o -> o.getBomId() == child1).findFirst().orElseThrow().getIsPrimary());
        assertFalse(options.stream().filter(o -> o.getBomId() == child2).findFirst().orElseThrow().getIsPrimary());
        assertEquals(1, manufacturing.getSchemeChoices(parent1).size());
        assertEquals(Long.valueOf(child1), manufacturing.getSchemeChoices(parent1).get(0).getBomId());
    }

    @Test
    void primaryChoiceIsolatedPerMaterialAndPerProduct() throws Exception {
        var p1 = product("P1");
        var p2 = product("P2");
        long a1 = scheme(349, "SFG349-A1");
        long a2 = scheme(349, "SFG349-A2");
        long b1 = scheme(350, "SFG350-B1");

        long parent1 = service.createBomReturnId(parent(p1.getProductId(), "TOP-P1",
                List.of(makeRow(349, a1), makeRow(350, b1)),
                List.of(choice(349, a1, true), choice(350, b1, true)), 0));
        var root1 = JSON.readTree(savedJson(p1));
        assertEquals(a1, root1.path("349").path("bomId").asLong());
        assertTrue(root1.path("349").path("isPrimary").asBoolean());
        assertEquals(b1, root1.path("350").path("bomId").asLong());
        assertTrue(root1.path("350").path("isPrimary").asBoolean());
        assertEquals(Integer.valueOf(1), revision(p1));

        var update = parent(p1.getProductId(), "TOP-P1",
                List.of(makeRow(349, a2), makeRow(350, b1)),
                List.of(choice(349, a2, true), choice(350, b1, false)), 1);
        update.setBomId(parent1);
        var edit = service.updateBomWithDetail(withStoredItemIds(update));
        assertEquals(Integer.valueOf(2), edit.getSchemeRevision());
        var root2 = JSON.readTree(savedJson(p1));
        assertEquals(a2, root2.path("349").path("bomId").asLong());
        assertTrue(root2.path("349").path("isPrimary").asBoolean());
        assertEquals(b1, root2.path("350").path("bomId").asLong(),
                "改 349 的主方案不得清掉 350 的主方案");
        assertTrue(root2.path("350").path("isPrimary").asBoolean());
        assertEquals(Integer.valueOf(2), revision(p1));

        service.createBomReturnId(parent(p2.getProductId(), 349, a1, 0));
        var rootP1 = JSON.readTree(savedJson(p1));
        var rootP2 = JSON.readTree(savedJson(p2));
        assertEquals(a2, rootP1.path("349").path("bomId").asLong());
        assertEquals(a1, rootP2.path("349").path("bomId").asLong());
        assertEquals(Integer.valueOf(1), revision(p2));
    }

    @Test
    void nonPrimaryAdoptionDoesNotOverwritePrimaryAndPrimaryCanChangeThenCancel() throws Exception {
        var p1 = product("P1");
        long a = scheme(349, "SFG349-A");
        long b = scheme(349, "SFG349-B");
        long parentId = service.createBomReturnId(parent(p1.getProductId(), 349, a, 0));

        // 父 BOM 实际采用非主方案 B：只有行 sub_bom_id 表达实际采用，产品主方案仍是 A。
        var adoptNonPrimary = parent(p1.getProductId(), "TOP-P1", List.of(makeRow(349, b)),
                List.of(choice(349, b, false)), 1);
        adoptNonPrimary.setBomId(parentId);
        var edit = service.updateBomWithDetail(withStoredItemIds(adoptNonPrimary));
        assertEquals(Integer.valueOf(1), edit.getSchemeRevision());
        var afterAdopt = JSON.readTree(savedJson(p1));
        assertEquals(a, afterAdopt.path("349").path("bomId").asLong(), "非主采用不得覆盖原主方案");
        assertTrue(afterAdopt.path("349").path("isPrimary").asBoolean());
        assertEquals(Long.valueOf(b), service.getBomDetail(parentId).getItems().get(0).getSubBomId());

        // 主方案变更：同一物料显式设主才替换。
        var changePrimary = parent(p1.getProductId(), "TOP-P1", List.of(makeRow(349, b)),
                List.of(choice(349, b, true)), 1);
        changePrimary.setBomId(parentId);
        assertEquals(Integer.valueOf(2), service.updateBomWithDetail(withStoredItemIds(changePrimary)).getSchemeRevision());
        var afterChange = JSON.readTree(savedJson(p1));
        assertEquals(b, afterChange.path("349").path("bomId").asLong());
        assertTrue(afterChange.path("349").path("isPrimary").asBoolean());

        // 取消主方案：显式 cancelPrimary，只移除该物料的主方案。
        var cancel = parent(p1.getProductId(), "TOP-P1", List.of(makeRow(349, b)),
                List.of(cancelChoice(349)), 2);
        cancel.setBomId(parentId);
        assertEquals(Integer.valueOf(3), service.updateBomWithDetail(withStoredItemIds(cancel)).getSchemeRevision());
        assertFalse(JSON.readTree(savedJson(p1)).has("349"));
        assertTrue(manufacturing.getSchemeChoices(parentId).isEmpty());
    }

    private ManufacturingSchemeChoiceDTO cancelChoice(long materialId) {
        var choice = new ManufacturingSchemeChoiceDTO();
        choice.setMaterialId(materialId);
        choice.setCancelPrimary(true);
        return choice;
    }

    @Test
    void missingRevisionRejectedWhenJsonWouldChange() throws Exception {
        var p1 = product("P1");
        long a = scheme(349, "SFG349-A");
        long b = scheme(349, "SFG349-B");
        long parentId = service.createBomReturnId(parent(p1.getProductId(), 349, a, 0));
        String before = savedJson(p1);

        var noRevision = parent(p1.getProductId(), "TOP-P1", List.of(makeRow(349, b)),
                List.of(choice(349, b, true)), null);
        noRevision.setBomId(parentId);
        var ex = assertThrows(BusinessException.class,
                () -> service.updateBomWithDetail(withStoredItemIds(noRevision)));
        assertTrue(ex.getMessage().contains("SCHEME_REVISION_REQUIRED"), ex.getMessage());
        assertEquals(before, savedJson(p1));
    }

    @Test
    void unusedDraftChoiceDoesNotBlockOtherBomSubmit() {
        var p1 = product("P1");
        long used = scheme(349, "SFG349-USED");
        long unusedDraft = scheme(350, "SFG350-DRAFT");
        long parent1 = service.createBomReturnId(parent(p1.getProductId(), 349, used, 0));
        // 同一产品的另一张父 BOM 使用 350 草稿方案；该主方案与 parent1 无关。
        service.createBomReturnId(parent(p1.getProductId(), 350, unusedDraft, 1));
        db.update("UPDATE engineering_bom SET approve_status=3 WHERE bom_id=?", used);

        assertTrue(service.submitApprove(parent1));
        assertEquals(Integer.valueOf(2), service.getBomDetail(parent1).getApproveStatus());
    }

    @Test
    void adoptDoesNotPersistSchemeRevision() {
        var p1 = product("P1");
        long child = scheme(349, "SFG349-A");
        service.createBomReturnId(parent(p1.getProductId(), 349, child, 0));
        var before = revision(p1);

        var adopted = manufacturing.adopt(child);
        assertNotNull(adopted.getSelectionToken());
        assertEquals(before, revision(p1), "采用 token 不得持久化主方案");
        assertEquals(Integer.valueOf(1), service.getBomDetail(child).getApproveStatus());
    }

    @Test
    void legacyCallWithoutSchemeChoicesNeedsNoRevision() {
        var p1 = product("P1");
        long child = scheme(349, "SFG349-A");
        long parentId = service.createBomReturnId(parent(p1.getProductId(), "LEGACY-TOP",
                List.of(makeRow(349, child)), null, null));
        assertEquals(Integer.valueOf(0), revision(p1));

        var update = parent(p1.getProductId(), "LEGACY-TOP", List.of(makeRow(349, child)), null, null);
        update.setBomId(parentId);
        update.setRemark("旧调用只改 BOM，不传 schemeChoices/schemeRevision");
        assertNotNull(service.updateBomWithDetail(withStoredItemIds(update)));
        assertEquals(Integer.valueOf(0), revision(p1), "未实际写 JSON 时不得递增 revision");
    }

    @Test
    void draftReferenceBlocksSubmitButNotSave() {
        var p1 = product("P1");
        long child = scheme(349, "SFG349-A");
        long parentId = service.createBomReturnId(parent(p1.getProductId(), 349, child, 0));

        var ex = assertThrows(BusinessException.class, () -> service.submitApprove(parentId));
        assertTrue(ex.getMessage().contains("SUB_BOM_NOT_APPROVED"), ex.getMessage());
    }

    @Test
    void malformedSchemeJsonBlocksSubmitAndKeepsOriginalData() {
        var p1 = product("P1");
        long child = scheme(349, "SFG349-A");
        long parentId = service.createBomReturnId(parent(p1.getProductId(), "TOP-P1",
                List.of(makeRow(349, child)), null, null));
        db.update("UPDATE engineering_bom SET approve_status=3 WHERE bom_id=?", child);
        db.update("UPDATE product SET material_scheme_json='{bad' WHERE product_id=?", p1.getProductId());

        var ex = assertThrows(BusinessException.class, () -> service.submitApprove(parentId));
        assertTrue(ex.getMessage().contains("SCHEME_JSON_INVALID"), ex.getMessage());
        assertEquals("{bad", savedJson(p1), "坏 JSON 不得被静默清空");
        assertEquals(Integer.valueOf(1), db.queryForObject(
                "SELECT approve_status FROM engineering_bom WHERE bom_id=?", Integer.class, parentId));
    }

    @Test
    void adoptRejectsViewOnlyAndAllowsEditPermission() throws Exception {
        StpLogic original = StpUtil.getStpLogic();
        StpLogic logic = spy(new StpLogic("login"));
        doReturn(42L).when(logic).getLoginId();
        StpUtil.setStpLogic(logic);
        try {
            var bomService = mock(IEngineeringBomService.class);
            var mfg = mock(ManufacturingBomService.class);
            when(mfg.options(any(), any(), any())).thenReturn(List.of());
            when(mfg.adopt(1L)).thenReturn(new ManufacturingSchemeVO());
            var controller = new BomController(bomService);
            ReflectionTestUtils.setField(controller, "manufacturingBomService", mfg);
            var mvc = MockMvcBuilders.standaloneSetup(controller).addInterceptors(new SaInterceptor()).build();

            doReturn(List.of("engineering:bom:view")).when(logic).getPermissionList(any());
            mvc.perform(get("/engineering/bom/manufacturing/options")
                            .param("outputType", "MATERIAL").param("outputId", "349"))
                    .andExpect(status().isOk());
            mvc.perform(post("/engineering/bom/manufacturing/1/adopt"))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
            verify(mfg, never()).adopt(anyLong());

            doReturn(List.of("engineering:bom:edit")).when(logic).getPermissionList(any());
            mvc.perform(post("/engineering/bom/manufacturing/1/adopt")).andExpect(status().isOk());
            verify(mfg).adopt(1L);
        } finally {
            StpUtil.setStpLogic(original);
        }
    }

    @Test
    void unusedDraftPrimarySameMaterialDoesNotBlockApprovedNonPrimaryReference() throws Exception {
        var p1 = product("P1");
        long draftX = scheme(349, "SFG349-X-DRAFT"); // 新建默认草稿
        long approvedY = scheme(349, "SFG349-Y-APPROVED");
        db.update("UPDATE engineering_bom SET approve_status=3 WHERE bom_id=?", approvedY);

        long parentId = service.createBomReturnId(parent(p1.getProductId(), "TOP-P1",
                List.of(makeRow(349, approvedY)), List.of(choice(349, draftX, true)), 0));
        // 产品主方案 X 是草稿，但当前父 BOM 实际显式引用的是已批准 Y。
        assertEquals(draftX, JSON.readTree(savedJson(p1)).path("349").path("bomId").asLong());

        assertTrue(service.submitApprove(parentId), "未使用的同材料草稿主方案不得阻断审批");
        assertEquals(Integer.valueOf(2), service.getBomDetail(parentId).getApproveStatus());
    }

    @Test
    void directMaterialRowIsNotForcedIntoManufactureByUnusedProductPrimary() throws Exception {
        var p1 = product("P1");
        long draftX = scheme(349, "SFG349-X-DRAFT");
        service.createBomReturnId(parent(p1.getProductId(), 349, draftX, 0));
        assertEquals(draftX, JSON.readTree(savedJson(p1)).path("349").path("bomId").asLong());

        // 普通直接大料行（buy，无 sub_bom_id）不能因产品另有该料主方案而被强制制造。
        long direct = service.createBomReturnId(parent(p1.getProductId(), "DIRECT-TOP",
                List.of(buyRow(349)), null, null));
        assertTrue(service.submitApprove(direct));
        assertEquals(Integer.valueOf(2), service.getBomDetail(direct).getApproveStatus());
    }

    @Test
    void explicitlyReferencedDraftSchemeStillBlocksSubmit() {
        var p1 = product("P1");
        long draftX = scheme(349, "SFG349-X-DRAFT");
        long approvedY = scheme(349, "SFG349-Y-APPROVED");
        db.update("UPDATE engineering_bom SET approve_status=3 WHERE bom_id=?", approvedY);

        // 主方案是已批准 Y，但当前 BOM 行实际引用草稿 X：审批仍必须拒绝实际引用草稿。
        long parentId = service.createBomReturnId(parent(p1.getProductId(), "TOP-P1",
                List.of(makeRow(349, draftX)), List.of(choice(349, approvedY, true)), 0));
        var ex = assertThrows(BusinessException.class, () -> service.submitApprove(parentId));
        assertTrue(ex.getMessage().contains("SUB_BOM_NOT_APPROVED"), ex.getMessage());
    }

    private static final String MVC_ITEMS =
            "\"items\":[{\"materialId\":110,\"materialCode\":\"M110\",\"materialName\":\"物料110\","
                    + "\"unit\":\"PCS\",\"sourceType\":\"buy\",\"moduleQty\":1,\"baseQty\":1,\"itemOrder\":1}]";

    @Test
    void mvcCancelPrimaryWithoutBomIdPassesBeanValidation() throws Exception {
        var bomService = mock(IEngineeringBomService.class);
        when(bomService.createBomReturnId(any(EngineeringBomDTO.class))).thenReturn(9L);
        var controller = new BomController(bomService);
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();

        mvc.perform(post("/engineering/bom").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bomCode\":\"MVC-CANCEL\",\"bomName\":\"MVC-CANCEL\","
                                + "\"outputType\":\"PRODUCT\",\"outputId\":1," + MVC_ITEMS + ","
                                + "\"schemeChoices\":[{\"materialId\":349,\"cancelPrimary\":true}]}"))
                .andExpect(status().isOk());
        verify(bomService).createBomReturnId(any(EngineeringBomDTO.class));
    }

    @Test
    void mvcAdoptionWithoutBomIdIsRejectedBeforeService() throws Exception {
        var bomService = mock(IEngineeringBomService.class);
        var controller = new BomController(bomService);
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();

        mvc.perform(post("/engineering/bom").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bomCode\":\"MVC-ADOPT\",\"bomName\":\"MVC-ADOPT\","
                                + "\"outputType\":\"PRODUCT\",\"outputId\":1," + MVC_ITEMS + ","
                                + "\"schemeChoices\":[{\"materialId\":349,\"isPrimary\":true}]}"))
                .andExpect(status().isBadRequest());
        verify(bomService, never()).createBomReturnId(any(EngineeringBomDTO.class));
    }

    @Test
    void wrapperTakesGraphLockBeforeAnyRead() {
        long a = scheme(349, "ORDER-A");
        long b = scheme(350, "ORDER-B");
        long itemId = db.queryForObject(
                "SELECT item_id FROM engineering_bom_item WHERE bom_id=?", Long.class, b);

        var row = makeRow(349, a);
        row.setItemId(itemId);
        var dto = new com.jjx.product.domain.dto.ManufacturingBomSaveDTO();
        dto.setBomCode("ORDER-B-UPD");
        dto.setBomName("ORDER-B-UPD");
        dto.setOutputType("MATERIAL");
        dto.setOutputId(350L);
        dto.setItems(List.of(row));

        var spyMapper = spy(rawBomMapper);
        var impl = new ManufacturingBomServiceImpl(service, spyMapper, productMapper);
        var proxy = new ProxyFactory(impl);
        proxy.setInterfaces(ManufacturingBomService.class);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(db.getDataSource()),
                new AnnotationTransactionAttributeSource()));
        ((ManufacturingBomService) proxy.getProxy()).update(b, dto);

        var order = inOrder(spyMapper);
        order.verify(spyMapper, atLeastOnce()).lockGraphForMutation();
        order.verify(spyMapper, atLeastOnce()).selectById(b);
    }

    @Test
    void wrapperTransactionTakesGraphLockBeforeSnapshot() throws Exception {
        long a = scheme(349, "WRAP-A");
        long b = scheme(350, "WRAP-B");
        long bItemId = db.queryForObject(
                "SELECT item_id FROM engineering_bom_item WHERE bom_id=?", Long.class, b);

        var row = makeRow(349, a);
        row.setItemId(bItemId);
        var dto = new com.jjx.product.domain.dto.ManufacturingBomSaveDTO();
        dto.setBomCode("WRAP-B-UPD");
        dto.setBomName("WRAP-B-UPD");
        dto.setOutputType("MATERIAL");
        dto.setOutputId(350L);
        dto.setItems(List.of(row));

        CountDownLatch entered = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        Future<String> writer = pool.submit(() -> {
            entered.await();
            try {
                manufacturingTx.update(b, dto);
                return "OK";
            } catch (BusinessException ex) {
                return ex.getMessage();
            }
        });
        String result;
        try {
            try (var conn = db.getDataSource().getConnection()) {
                conn.setAutoCommit(false);
                try (var st = conn.createStatement()) {
                    st.executeQuery("SELECT bom_id FROM engineering_bom ORDER BY bom_id FOR UPDATE").close();
                }
                entered.countDown();
                Thread.sleep(800);
                assertFalse(writer.isDone(), "外层 update 必须在任何普通 SELECT 前等待图锁，快照不能早于锁");
                try (var st = conn.createStatement()) {
                    st.executeUpdate("UPDATE engineering_bom_item SET source_type='make', sub_bom_id=" + b
                            + " WHERE bom_id=" + a);
                }
                conn.commit();
            }
            result = writer.get(20, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        assertTrue(result.contains("BOM_CYCLE"), "外层入口锁释放后必须重读已提交图并拒绝成环: " + result);
        assertEquals(1L, db.queryForObject(
                "SELECT COUNT(*) FROM engineering_bom_item WHERE sub_bom_id IS NOT NULL", Long.class));
    }

    @Test
    void concurrentSchemeRevisionIsRejected() {
        var p1 = product("P1");
        long child = scheme(349, "SFG349-A");
        long parentId = service.createBomReturnId(parent(p1.getProductId(), 349, child, 0));
        var stale = parent(p1.getProductId(), 349, child, 0);
        stale.setBomId(parentId);
        var ex = assertThrows(BusinessException.class, () -> service.updateBomWithDetail(stale));
        assertEquals(409, ex.getCode());
        assertTrue(ex.getMessage().contains("CONCURRENT_MODIFICATION"));
    }
}
