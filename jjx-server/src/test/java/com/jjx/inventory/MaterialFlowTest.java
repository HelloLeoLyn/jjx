package com.jjx.inventory;

import com.jjx.inventory.domain.InventoryMaterial;
import com.jjx.inventory.dto.query.MaterialQueryDTO;
import com.jjx.inventory.mapper.InventoryMaterialMapper;
import com.jjx.inventory.service.impl.InventoryMaterialServiceImpl;
import com.jjx.inventory.converter.MaterialConverter;
import com.jjx.framework.common.RedisSequenceService;
import com.jjx.purchase.service.IPurchaseSupplierService;
import org.junit.jupiter.api.*;
import org.mockito.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 库存物料管理流程测试
 * 新建物料 → 查询 → 校验
 */
class MaterialFlowTest {

    @Mock private InventoryMaterialMapper materialMapper;
    @Mock private RedisSequenceService redisSequenceService;
    @Mock private MaterialConverter materialConverter;
    @Mock private IPurchaseSupplierService purchaseSupplierService;

    private InventoryMaterialServiceImpl materialService;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        materialService = new InventoryMaterialServiceImpl(
            materialMapper, redisSequenceService, materialConverter, purchaseSupplierService
        );
    }

    @Test
    @DisplayName("1. 新建物料 - 成功")
    void testCreate() {
        InventoryMaterial m = new InventoryMaterial();
        m.setMaterialName("薄膜开关面板PET");
        m.setMaterialType("raw");
        m.setSpecification("0.125mm*500mm");
        m.setUnit("平方米");

        when(materialMapper.insert(any(InventoryMaterial.class))).thenReturn(1);

        boolean result = materialService.create(m);
        assert result : "物料创建失败";
        System.out.println("  ✅ 新建物料: " + m.getMaterialName());
    }

    @Test
    @DisplayName("2. 查询物料列表")
    void testList() {
        when(materialMapper.selectList(any())).thenReturn(java.util.List.of());
        MaterialQueryDTO query = new MaterialQueryDTO();
        var list = materialService.selectList(query);
        assert list != null : "查询失败";
        System.out.println("  ✅ 物料列表查询成功");
    }

    @Test
    @DisplayName("3. 根据编码查询")
    void testGetByCode() {
        InventoryMaterial mock = new InventoryMaterial();
        mock.setMaterialCode("MAT001");
        mock.setMaterialName("测试物料");

        when(materialMapper.selectByCode(anyString())).thenReturn(mock);
        var result = materialService.getByCode("MAT001");
        assert result != null : "❌ 未找到物料";
        System.out.println("  ✅ 按编码查询成功: " + result.getMaterialName());
    }
}
