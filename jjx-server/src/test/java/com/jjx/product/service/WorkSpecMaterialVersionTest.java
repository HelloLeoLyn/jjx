package com.jjx.product.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjx.common.exception.BusinessException;
import com.jjx.product.domain.entity.ProductWorkSpecVersion;
import com.jjx.product.mapper.ProductMapper;
import com.jjx.product.mapper.ProductWorkSpecItemMapper;
import com.jjx.product.mapper.ProductWorkSpecMapper;
import com.jjx.product.mapper.ProductWorkSpecVersionMapper;
import com.jjx.system.mapper.SysAttachmentMapper;
import com.jjx.system.mapper.SysAttachmentRefMapper;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** 作业规范 PRODUCT/MATERIAL 产出身份与发布版本绑定隔离（dev-20261111-004）。 */
class WorkSpecMaterialVersionTest {

    ProductWorkSpecVersionService service;
    ProductWorkSpecVersionMapper versionMapper;

    @BeforeEach
    void setup() {
        var source = new UnpooledDataSource("org.h2.Driver",
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        var configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setEnvironment(new Environment("ws-output", new SpringManagedTransactionFactory(), source));
        configuration.addMapper(ProductWorkSpecVersionMapper.class);
        var session = new SqlSessionTemplate(new MybatisSqlSessionFactoryBuilder().build(configuration));
        var db = new JdbcTemplate(source);
        var table = TableInfoHelper.getTableInfo(ProductWorkSpecVersion.class);
        var columns = new StringBuilder(table.getKeyColumn() + " BIGINT AUTO_INCREMENT PRIMARY KEY");
        for (var field : table.getFieldList()) {
            Class<?> type = field.getPropertyType();
            String sqlType = type == String.class ? "VARCHAR(4000)"
                    : type == LocalDateTime.class ? "TIMESTAMP"
                    : type == Boolean.class ? "BOOLEAN"
                    : type == BigDecimal.class ? "DECIMAL(18,4)"
                    : type == Integer.class ? "INT" : "BIGINT";
            columns.append(',').append(field.getColumn()).append(' ').append(sqlType);
        }
        db.execute("CREATE TABLE " + table.getTableName() + " (" + columns + ")");
        versionMapper = session.getMapper(ProductWorkSpecVersionMapper.class);
        service = new ProductWorkSpecVersionService(mock(ProductWorkSpecMapper.class), mock(ProductMapper.class),
                mock(SysAttachmentMapper.class), versionMapper, mock(ProductWorkSpecItemMapper.class),
                mock(WorkSpecDocsetSnapshotService.class), mock(SysAttachmentRefMapper.class), new ObjectMapper());
    }

    private ProductWorkSpecVersion version(Long productId, String type, Long outputId, String versionNo) {
        var version = new ProductWorkSpecVersion();
        version.setProductId(productId);
        version.setOutputType(type);
        version.setOutputId(outputId);
        version.setVersionNo(versionNo);
        version.setStatus("PUBLISHED");
        version.setPublishedAt(LocalDateTime.now());
        version.setDeleted(0);
        versionMapper.insert(version);
        return version;
    }

    @Test
    void resolvesPublishedVersionByOutputIdentity() {
        var productVersion = version(1L, "PRODUCT", 1L, "V1.0");
        var materialVersion = version(null, "MATERIAL", 349L, "V1.0");

        assertEquals(productVersion.getId(), service.resolvePublishedVersion(null, "PRODUCT", 1L).getId());
        assertEquals(materialVersion.getId(), service.resolvePublishedVersion(null, "MATERIAL", 349L).getId());
        assertEquals(1, service.listByOutput("MATERIAL", 349L).size());
        assertEquals(1, service.listByOutput("PRODUCT", 1L).size());
        assertEquals(0, service.listByOutput("PRODUCT", 349L).size());
    }

    @Test
    void explicitBindingRequiresPublishedButHistoricalReadKeepsRetiredVersion() {
        var published = version(1L, "PRODUCT", 1L, "V1.0");
        var retired = version(1L, "PRODUCT", 1L, "V1.1");
        retired.setStatus("RETIRED");
        versionMapper.updateById(retired);

        // 新绑定显式指定已发布版本：通过。
        assertEquals(published.getId(),
                service.resolvePublishedVersion(published.getId(), "PRODUCT", 1L).getId());

        // 新绑定显式指定已停用版本：拒绝，不能给新工单绑定。
        var ex = assertThrows(BusinessException.class,
                () -> service.resolvePublishedVersion(retired.getId(), "PRODUCT", 1L));
        assertTrue(ex.getMessage().contains("SNAPSHOT_NOT_PUBLISHED"), ex.getMessage());

        // 历史读取按持久化 ID/版本列表保持原行为，停用版本仍在。
        assertEquals("RETIRED", versionMapper.selectById(retired.getId()).getStatus());
        assertTrue(service.listByOutput("PRODUCT", 1L).stream()
                .anyMatch(v -> retired.getId().equals(v.getId())), "停用版本仍应可供历史读取");
    }

    @Test
    void rejectsCrossTypeExplicitVersionAndMissingMaterialPublication() {
        var productVersion = version(1L, "PRODUCT", 1L, "V1.0");
        version(null, "MATERIAL", 349L, "V1.0");

        var cross = assertThrows(BusinessException.class,
                () -> service.resolvePublishedVersion(productVersion.getId(), "MATERIAL", 349L));
        assertTrue(cross.getMessage().contains("不一致"), cross.getMessage());

        var missing = assertThrows(BusinessException.class,
                () -> service.resolvePublishedVersion(null, "MATERIAL", 350L));
        assertTrue(missing.getMessage().contains("MATERIAL_WORK_SPEC_NOT_PUBLISHED"), missing.getMessage());
    }
}
