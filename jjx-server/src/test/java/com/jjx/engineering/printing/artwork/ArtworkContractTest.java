package com.jjx.engineering.printing.artwork;

import com.jjx.common.exception.BusinessException;
import com.jjx.system.domain.entity.SysAttachment;
import com.jjx.system.service.AttachmentAccessService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.sql.DriverManager;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ArtworkContractTest {
    @Test
    @EnabledIfEnvironmentVariable(named = "JJX_P03_ISO_PASSWORD", matches = ".+")
    void readsSharedProductsAndProtectsVersionDrawing() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:mysql://127.0.0.1/jjx_erp_db_p02_iso",
                "root", System.getenv("JJX_P03_ISO_PASSWORD"))) {
            var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            assertEquals("jjx_erp_db_p02_iso", jdbc.queryForObject("SELECT DATABASE()", String.class));
            connection.setAutoCommit(false);
            try {
                long product = -Math.abs(UUID.randomUUID().getMostSignificantBits() / 2);
                jdbc.update("INSERT INTO product(product_id) VALUES (?),(?)", product, product+1);
                jdbc.update("INSERT INTO sys_attachment(file_name,file_path) VALUES ('drawing.pdf',?)",
                    "/p03-fixture/" + UUID.randomUUID());
                long attachment = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
                var repo = new ArtworkRepository(jdbc);
                var service = new ArtworkService(jdbc);
                for (int i = 0; i < 2; i++) {
                    jdbc.update("INSERT INTO print_artwork(artwork_no,name,family) VALUES (?,?,?)",
                        UUID.randomUUID().toString(), "测试", "面板");
                    long artwork = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
                    jdbc.update("INSERT INTO print_artwork_version(artwork_id,version_no,status,drawing_file_id) VALUES (?,'v2','RELEASED',?)", artwork, attachment);
                    long version = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
                    jdbc.update("UPDATE print_artwork SET default_version_id=? WHERE artwork_id=?", version, artwork);
                    jdbc.update("INSERT INTO engineering_resource_product_rel(resource_type,resource_id,product_id) VALUES ('ARTWORK',?,?),('ARTWORK',?,?)",
                        artwork, product, artwork, product+1);
                    var view = repo.detail(artwork);
                    assertEquals(2, view.productIds().size());
                    assertEquals(version, view.defaultVersionId());
                    assertEquals("v2", view.versions().get(0).versionNo());
                    service.requireUsableVersion(version);
                    jdbc.update("INSERT INTO print_artwork_version(artwork_id,version_no,status) VALUES (?,'v3','RELEASED')", artwork);
                    long next = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
                    jdbc.update("UPDATE print_artwork SET default_version_id=? WHERE artwork_id=?", next, artwork);
                    service.requireUsableVersion(version);
                    jdbc.update("UPDATE print_artwork_version SET status='DISABLED' WHERE version_id=?", version);
                    assertThrows(BusinessException.class, () -> service.requireUsableVersion(version));
                    assertEquals("DISABLED", repo.detail(artwork).versions().get(0).status());
                }
                assertEquals(2, repo.productVersions(product, true).size());
                assertEquals(4, repo.productVersions(product+1, false).size());
                var file = new SysAttachment();
                file.setId(attachment);
                var access = new AttachmentAccessService(null, null, jdbc);
                assertThrows(BusinessException.class, () -> access.checkUnlink(file));
            } finally {
                connection.rollback();
            }
        }
    }
}
