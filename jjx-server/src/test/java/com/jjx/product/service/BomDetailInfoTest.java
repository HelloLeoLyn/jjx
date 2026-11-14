package com.jjx.product.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.jjx.engineering.domain.entity.EngineeringBom;
import com.jjx.engineering.domain.entity.EngineeringBomItem;
import com.jjx.inventory.domain.InventoryMaterial;
import com.jjx.inventory.mapper.InventoryMaterialMapper;
import com.jjx.product.domain.converter.EngineeringBomConverterImpl;
import com.jjx.product.mapper.EngineeringBomItemMapper;
import com.jjx.product.mapper.EngineeringBomMapper;
import com.jjx.product.service.impl.BomSpecificationService;
import com.jjx.product.service.impl.EngineeringBomServiceImpl;
import com.jjx.system.mapper.SysOperLogMapper;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * BOM 详情信息补全（dev-20261114-002）：明细带出档案单位与下层制造方案，头部带出产出对象与版本总数。
 *
 * <p>H2 + Mockito 单测：证明服务端取值链路，不连真实业务库；真实库数据表现以只读 SQL 另行取证。
 */
class BomDetailInfoTest {

    private static final Long ITEM_MATERIAL_ID = 10L;
    private static final Long OUTPUT_MATERIAL_ID = 348L;

    @Test
    void detailExposesArchiveUnitSubBomAndOutputObject() throws Exception {
        var source = new UnpooledDataSource("org.h2.Driver",
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        var configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setEnvironment(new Environment("bom-detail", new JdbcTransactionFactory(), source));
        configuration.addMapper(EngineeringBomMapper.class);
        var table = TableInfoHelper.getTableInfo(EngineeringBom.class);
        var columns = new StringBuilder("bom_id BIGINT AUTO_INCREMENT PRIMARY KEY");
        for (var field : table.getFieldList()) {
            var type = field.getPropertyType();
            columns.append(',').append(field.getColumn()).append(' ').append(type == String.class ? "VARCHAR(500)"
                    : type == Boolean.class ? "BOOLEAN"
                    : type == LocalDateTime.class || type == java.util.Date.class ? "TIMESTAMP" : "BIGINT");
        }
        new JdbcTemplate(source).execute("CREATE TABLE engineering_bom (" + columns + ")");

        var materialMapper = mock(InventoryMaterialMapper.class);
        var itemMaterial = new InventoryMaterial();
        itemMaterial.setMaterialId(ITEM_MATERIAL_ID);
        itemMaterial.setMaterialCode("AUX000348");
        itemMaterial.setMaterialName("离型纸 RP-W195-L80");
        itemMaterial.setUnit("M2");
        when(materialMapper.selectById(ITEM_MATERIAL_ID)).thenReturn(itemMaterial);
        var outputMaterial = new InventoryMaterial();
        outputMaterial.setMaterialId(OUTPUT_MATERIAL_ID);
        outputMaterial.setMaterialCode("AUX000348");
        outputMaterial.setMaterialName("离型纸 RP-W195-L80");
        outputMaterial.setUnit("PCS");
        when(materialMapper.selectById(OUTPUT_MATERIAL_ID)).thenReturn(outputMaterial);

        var specService = new BomSpecificationService(
                mock(com.jjx.inventory.master.MaterialMasterService.class), materialMapper, mock(SysOperLogMapper.class));

        try (var session = new MybatisSqlSessionFactoryBuilder().build(configuration).openSession(true)) {
            var bomMapper = session.getMapper(EngineeringBomMapper.class);
            var subBom = new EngineeringBom();
            subBom.setBomCode("AUX000348-MFG");
            subBom.setBomName("离型纸制造方案");
            subBom.setBomVersion("V1.0");
            subBom.setOutputType("MATERIAL");
            subBom.setOutputId(OUTPUT_MATERIAL_ID);
            subBom.setApproveStatus(3);
            subBom.setIsCurrent(false);
            assertEquals(1, bomMapper.insert(subBom));

            var parent = new EngineeringBom();
            parent.setBomCode("YL1025PWOO-BOM");
            parent.setBomName("茶色视窗BOM");
            parent.setBomVersion("V1.1");
            parent.setOutputType("MATERIAL");
            parent.setOutputId(OUTPUT_MATERIAL_ID);
            parent.setApproveStatus(2);
            parent.setIsCurrent(false);
            assertEquals(1, bomMapper.insert(parent));
            var olderVersion = new EngineeringBom();
            olderVersion.setBomCode("YL1025PWOO-BOM");
            olderVersion.setBomVersion("V1.0");
            olderVersion.setOutputType("MATERIAL");
            olderVersion.setOutputId(OUTPUT_MATERIAL_ID);
            olderVersion.setApproveStatus(3);
            olderVersion.setIsCurrent(false);
            assertEquals(1, bomMapper.insert(olderVersion));

            var item = new EngineeringBomItem();
            item.setBomId(parent.getBomId());
            item.setMaterialId(ITEM_MATERIAL_ID);
            item.setMaterialCode("AUX000348");
            item.setMaterialName("离型纸 RP-W195-L80");
            item.setSpecification("195*80mm");
            item.setUnit("PCS");
            item.setQuantity(new BigDecimal("1.0000"));
            item.setAppliedQty(new BigDecimal("1.0000"));
            item.setActualIssueQty(new BigDecimal("1.0000"));
            item.setLossRate(BigDecimal.ZERO);
            item.setSourceType("make");
            item.setSubBomId(subBom.getBomId());
            item.setItemOrder(1);

            var itemMapper = mock(EngineeringBomItemMapper.class);
            when(itemMapper.selectList(any())).thenReturn(List.of(item));

            var service = new EngineeringBomServiceImpl(bomMapper, itemMapper, null,
                    new EngineeringBomConverterImpl(), null, materialMapper, null, null, null, null);
            Field specField = EngineeringBomServiceImpl.class.getDeclaredField("bomSpecificationService");
            specField.setAccessible(true);
            specField.set(service, specService);

            var detail = service.getBomDetail(parent.getBomId());

            // 头部：物料产出的制造方案没有 productId，也要说清产出对象；版本总数按 bomCode 统计
            assertEquals("AUX000348", detail.getOutputCode());
            assertEquals("离型纸 RP-W195-L80", detail.getOutputName());
            assertEquals("MATERIAL", detail.getOutputType());
            assertEquals(2, detail.getVersionTotal());
            assertNull(detail.getProductCode());

            // 明细：档案单位与下层制造方案（编码/版本/状态）随行带出
            var detailItem = detail.getItems().get(0);
            assertEquals("M2", detailItem.getArchiveUnit());
            assertEquals("AUX000348-MFG", detailItem.getSubBomCode());
            assertEquals("离型纸制造方案", detailItem.getSubBomName());
            assertEquals("V1.0", detailItem.getSubBomVersion());
            assertEquals(3, detailItem.getSubBomApproveStatus());
        }
    }
}
