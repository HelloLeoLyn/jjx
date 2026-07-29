package com.jjx.product;

import com.jjx.product.domain.entity.Product;
import com.jjx.product.domain.entity.Product;
import com.jjx.product.domain.entity.ProductBom;
import com.jjx.product.domain.entity.ProductRouting;
import com.jjx.product.mapper.ProductMapper;
import com.jjx.product.service.impl.ProductServiceImpl;
import com.jjx.product.service.ProductCodeGenerator;
import com.jjx.product.domain.converter.ProductConverter;
import com.jjx.product.service.IProductBomService;
import com.jjx.product.service.IProductRoutingService;
import com.jjx.product.service.IProductCategoryService;
import com.jjx.product.service.IProductFilmService;
import com.jjx.engineering.service.EngineeringBaseService;
import com.jjx.notification.service.NotificationService;
import org.junit.jupiter.api.*;
import org.mockito.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 产品管理流程测试
 * 新建产品 → 校验 → 查询
 */
class ProductFlowTest {

    @Mock private ProductMapper productMapper;
    @Mock private ProductCodeGenerator productCodeGenerator;
    @Mock private ProductConverter productConverter;
    @Mock private IProductBomService bomService;
    @Mock private IProductRoutingService routingService;
    @Mock private IProductCategoryService categoryService;
    @Mock private IProductFilmService filmService;
    @Mock private EngineeringBaseService engineeringBaseService;
    @Mock private NotificationService notificationService;

    private ProductServiceImpl productService;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        productService = new ProductServiceImpl(
            productMapper, productCodeGenerator, productConverter,
            bomService, routingService, categoryService, filmService,
            notificationService
        );
    }

    @Test
    @DisplayName("1. 编码唯一性校验 - 通过")
    void testCheckCodeUnique_pass() {
        when(productMapper.selectCount(any())).thenReturn(0L);
        boolean unique = productService.checkProductCodeUnique("P001", null);
        assert unique : "编码应唯一";
        System.out.println("  ✅ 产品编码唯一性校验通过");
    }

    @Test
    @DisplayName("2. 名称唯一性校验 - 通过")
    void testCheckNameUnique_pass() {
        when(productMapper.selectCount(any())).thenReturn(0L);
        boolean unique = productService.checkProductNameUnique("测试产品", null);
        assert unique : "名称应唯一";
        System.out.println("  ✅ 产品名称唯一性校验通过");
    }

    @Test
    @DisplayName("3. 发布产品")
    void testReleaseProduct() {
        // 准备：有BOM和路线的已审批产品
        Product product = new Product();
        product.setProductId(1L);
        product.setCurrentBomId(100L);
        product.setCurrentRouteId(200L);
        product.setProductStatus(4); // APPROVED

        ProductBom bom = new ProductBom();
        bom.setBomId(100L);
        bom.setApproveStatus(3); // APPROVED

        ProductRouting routing = new ProductRouting();
        routing.setRoutingId(200L);
        routing.setApproveStatus(3); // APPROVED

        when(productMapper.selectById(1L)).thenReturn(product);
        when(bomService.getById(100L)).thenReturn(bom);
        when(routingService.getById(200L)).thenReturn(routing);
        when(productMapper.updateById(any(Product.class))).thenReturn(1);

        boolean result = productService.releaseProduct(1L);
        assert result : "产品发布失败";
        System.out.println("  ✅ 产品发布成功");
    }

    @Test
    @DisplayName("4. 停用产品")
    void testObsoleteProduct() {
        when(productMapper.selectById(1L)).thenReturn(new Product());
        when(productMapper.updateById(any(Product.class))).thenReturn(1);

        boolean result = productService.obsoleteProduct(1L);
        assert result : "产品停用失败";
        System.out.println("  ✅ 产品停用成功");
    }
}
