package com.jjx.sales.service.impl;

import com.jjx.common.exception.BusinessException;
import com.jjx.engineering.domain.entity.EngineeringBom;
import com.jjx.engineering.domain.entity.EngineeringBomItem;
import com.jjx.framework.common.RedisSequenceService;
import com.jjx.inventory.domain.InventoryMaterial;
import com.jjx.inventory.mapper.InventoryMaterialMapper;
import com.jjx.product.domain.entity.Product;
import com.jjx.product.domain.entity.ProductStandardProcess;
import com.jjx.product.mapper.EngineeringBomItemMapper;
import com.jjx.product.mapper.ProductMapper;
import com.jjx.product.mapper.ProductStandardProcessMapper;
import com.jjx.product.service.IEngineeringBomService;
import com.jjx.product.service.IEngineeringRoutingService;
import com.jjx.sales.domain.entity.*;
import com.jjx.sales.domain.vo.SalesOrderProductVO;
import com.jjx.sales.dto.transfer.SampleTransferConfirmDTO;
import com.jjx.sales.enums.SampleOrderStatusEnum;
import com.jjx.sales.mapper.*;
import com.jjx.sales.service.ISalesOrderProductService;
import com.jjx.system.utils.SecurityUtils;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 真实转移服务，mapper替身；核对预览、自动/手动映射直至标准BOM insert参数，非业务库E2E。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SampleMaterialSplitTransferTest {
    @Mock OrderMapper orderMapper;
    @Mock SalesSampleProcessMapper sampleProcessMapper;
    @Mock SalesSampleBomMapper sampleBomMapper;
    @Mock InventoryMaterialMapper inventoryMaterialMapper;
    @Mock ProductStandardProcessMapper standardProcessMapper;
    @Mock RedisSequenceService redisSequenceService;
    @Mock ISalesOrderProductService orderProductService;
    @Mock ProductMapper productMapper;
    @Mock IEngineeringBomService bomService;
    @Mock EngineeringBomItemMapper bomItemMapper;
    @Mock IEngineeringRoutingService routingService;
    @InjectMocks SampleOrderServiceImpl service;

    @BeforeEach void setup() {
        var order=new SalesOrder();order.setOrderId(10L);order.setDeleted(0);order.setSampleRound(2);
        order.setSampleStatus(SampleOrderStatusEnum.CONFIRMED.getValue());
        when(orderMapper.selectById(10L)).thenReturn(order);
        var process=new SalesSampleProcess();process.setProcessId(41L);process.setRoundNo(2);process.setProcessOrder(1);
        process.setStdProcessId(7L);process.setProcessName("冲型");
        process.setMaterials("[{\"name\":\"面板\",\"materialId\":1,\"qty\":0.5,\"split\":\"50mm\",\"projectIds\":[]}]");
        when(sampleProcessMapper.selectByOrderId(10L)).thenReturn(List.of(process));
        var independent=new SalesSampleBom();independent.setBomId(2L);independent.setRoundNo(2);
        independent.setMaterialName("胶");independent.setMaterialId(1L);independent.setQuantity(BigDecimal.ONE);independent.setSplit("60mm");
        var history=new SalesSampleBom();history.setRoundNo(1);history.setSplit("99mm");
        when(sampleBomMapper.selectByOrderId(10L)).thenReturn(List.of(independent,history));
        var mat=new InventoryMaterial();mat.setMaterialId(1L);mat.setMaterialName("材料");
        when(inventoryMaterialMapper.selectById(1L)).thenReturn(mat);
        when(inventoryMaterialMapper.selectList(any())).thenReturn(List.of(mat));
        var std=new ProductStandardProcess();std.setProcessId(7L);std.setProcessName("冲型");std.setIsEnabled(1);
        when(standardProcessMapper.selectEnabledProcesses()).thenReturn(List.of(std));
        when(standardProcessMapper.selectById(7L)).thenReturn(std);
    }
    @Test void previewKeepsProcessAndStandaloneSplitsAndExcludesHistory() {
        var rows=service.previewTransfer(10L).getSampleMaterials();
        assertEquals(List.of("50mm","60mm"), rows.stream().map(row->row.getSplit()).toList());
    }
    void prepareBomWrite() {
        var prod=new SalesOrderProductVO();prod.setProductId(20L);prod.setProductCode("P20");prod.setProductName("产品");
        when(orderProductService.getListByOrderId(10L)).thenReturn(List.of(prod));
        var product=new Product();product.setProductId(20L);
        when(productMapper.selectById(20L)).thenReturn(product);
        when(bomService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of());
        when(bomService.save(any(EngineeringBom.class))).thenAnswer(call->{ ((EngineeringBom)call.getArgument(0)).setBomId(30L);return true; });
        // 本用例只检查BOM写入，路线在下一阶段停止，不伪称全链路已执行。
        when(routingService.getOne(any())).thenThrow(new IllegalStateException("BOM_CAPTURED"));
    }
    @Test void automaticTransferWritesBothSplitsIntoStandardBom() {
        prepareBomWrite();
        var dto=new SampleTransferConfirmDTO();dto.setOrderId(10L);
        try(var security=mockStatic(SecurityUtils.class)) {
            var error=assertThrows(IllegalStateException.class,()->service.confirmTransfer(dto));
            assertEquals("BOM_CAPTURED",error.getMessage());
        }
        var capture=ArgumentCaptor.forClass(EngineeringBomItem.class);
        verify(bomItemMapper,times(2)).insert(capture.capture());
        assertEquals(List.of("50mm","60mm"),capture.getAllValues().stream().map(EngineeringBomItem::getSplit).toList());
        assertEquals(0,new BigDecimal("0.5").compareTo(capture.getAllValues().getFirst().getQuantity()));
    }
    SampleTransferConfirmDTO manual(String split) {
        var dto=new SampleTransferConfirmDTO();dto.setOrderId(10L);
        var process=new SampleTransferConfirmDTO.ProcessMapping();process.setStdProcessId(7L);process.setProcessName("冲型");
        dto.setProcessMappings(List.of(process));
        var material=new SampleTransferConfirmDTO.MaterialMapping();material.setMaterialId(1L);material.setSplit(split);material.setQty(BigDecimal.ONE);
        dto.setMaterialMappings(List.of(material));return dto;
    }
    @Test void manualMappingKeepsSplitAndLegacyEmptyIsAllowed() {
        prepareBomWrite();
        for(String split : Arrays.asList("50mm",null)) {
            try(var security=mockStatic(SecurityUtils.class)) {
                assertEquals("BOM_CAPTURED",assertThrows(IllegalStateException.class,()->service.confirmTransfer(manual(split))).getMessage());
            }
        }
        var capture=ArgumentCaptor.forClass(EngineeringBomItem.class);
        verify(bomItemMapper,times(2)).insert(capture.capture());
        assertEquals("50mm",capture.getAllValues().getFirst().getSplit());
        assertNull(capture.getAllValues().getLast().getSplit());
    }
    @Test void invalidSplitIsRejectedBeforeTransferWrites() {
        for(String split : List.of("-1mm","1.5mm","text","10cm","9007199254740992mm"))
            assertThrows(BusinessException.class,()->service.confirmTransfer(manual(split)));
        verifyNoInteractions(redisSequenceService,bomItemMapper);
    }
    /** 真实业务库Mapper保存回读；外层事务强制回滚，仅使用本测试唯一order_id。 */
    @Test
    @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="JJX_TEST_MYSQL", matches="1")
    void mysqlMapperPersistsAndReadsSplitWithoutLeavingFixtures() throws Exception {
        var ds = new org.springframework.jdbc.datasource.DriverManagerDataSource();
        ds.setDriverClassName("com.mysql.cj.jdbc.Driver");
        ds.setUrl(System.getenv().getOrDefault("JJX_TEST_MYSQL_URL", "jdbc:mysql://127.0.0.1:3306/jjx_erp_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai"));
        ds.setUsername(System.getenv().getOrDefault("DB_USER", "root"));
        ds.setPassword(System.getenv().getOrDefault("DB_PASS", "123456"));
        var jdbc = new org.springframework.jdbc.core.JdbcTemplate(ds);
        long id = System.currentTimeMillis();
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sales_sample_bom WHERE order_id=?", Integer.class, id));
        var config = new com.baomidou.mybatisplus.core.MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);config.addMapper(SalesSampleBomMapper.class);
        var bean = new com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean();
        bean.setDataSource(ds);bean.setConfiguration(config);
        bean.setGlobalConfig(new com.baomidou.mybatisplus.core.config.GlobalConfig().setBanner(false));
        var mapper = new org.mybatis.spring.SqlSessionTemplate(bean.getObject()).getMapper(SalesSampleBomMapper.class);
        org.springframework.test.util.ReflectionTestUtils.setField(service,"sampleBomMapper",mapper);
        var order = new SalesOrder();order.setOrderId(id);order.setDeleted(0);order.setSampleRound(1);
        when(orderMapper.selectById(id)).thenReturn(order);
        var tx = new org.springframework.transaction.support.TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(ds));
        tx.execute(status -> {
            status.setRollbackOnly();
            var filled = new SalesSampleBom();filled.setMaterialName("跳距回读夹具");filled.setSplit("50mm");filled.setQuantity(BigDecimal.ONE);
            var blank = new SalesSampleBom();blank.setMaterialName("历史空值夹具");blank.setQuantity(BigDecimal.ONE);
            try(var security=mockStatic(SecurityUtils.class)) {
                var rows=service.saveSampleBom(id,1,List.of(filled,blank));
                assertEquals(2,rows.size());assertEquals("50mm",rows.getFirst().getSplit());assertNull(rows.getLast().getSplit());
                filled.setSplit("");
                rows=service.saveSampleBom(id,1,List.of(filled));
                assertEquals(1,rows.size());assertNull(rows.getFirst().getSplit());
            }
            return null;
        });
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM sales_sample_bom WHERE order_id=?",Integer.class,id));
    }

}
