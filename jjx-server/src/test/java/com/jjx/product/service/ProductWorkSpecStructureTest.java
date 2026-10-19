package com.jjx.product.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.jjx.common.exception.BusinessException;
import com.jjx.system.domain.entity.SysAttachment;
import com.jjx.system.domain.entity.SysAttachmentRef;
import com.jjx.system.mapper.SysAttachmentMapper;
import com.jjx.system.mapper.SysAttachmentRefMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Executes the production ownership predicate against H2; no legacy biz fields are populated. */
class ProductWorkSpecStructureTest {
    private ProductWorkSpecService service;
    private JdbcTemplate jdbc;
    private SysAttachment file;

    @BeforeEach
    void init() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE TABLE sys_attachment_ref (attachment_id BIGINT,source_type VARCHAR(32),source_id BIGINT,document_type VARCHAR(64),deleted BOOLEAN)");
        var attachments = mock(SysAttachmentMapper.class);
        var refs = mock(SysAttachmentRefMapper.class);
        service = new ProductWorkSpecService(null, attachments, refs, null, null, null, null);
        file = new SysAttachment();
        file.setId(224L); file.setFileName("产品结构图.png"); file.setFileType("image/png");
        when(attachments.selectById(224L)).thenReturn(file);
        when(refs.selectList(any())).thenAnswer(call -> {
            QueryWrapper<SysAttachmentRef> query = call.getArgument(0);
            var matcher = Pattern.compile("#\\{ew.paramNameValuePairs.([^}]+)}").matcher(query.getSqlSegment());
            var args = new ArrayList<Object>();
            StringBuilder sql = new StringBuilder();
            while (matcher.find()) {
                args.add(query.getParamNameValuePairs().get(matcher.group(1)));
                matcher.appendReplacement(sql, "?");
            }
            matcher.appendTail(sql);
            return jdbc.query("SELECT document_type FROM sys_attachment_ref WHERE " + sql, (rs, i) -> {
                var ref = new SysAttachmentRef(); ref.setDocumentType(rs.getString(1)); return ref;
            }, args.toArray());
        });
    }

    private Object validate() { return ReflectionTestUtils.invokeMethod(service, "structure", 2L, 224L); }
    private void ref(long attachment, String source, long product, String type, boolean deleted) {
        jdbc.update("INSERT INTO sys_attachment_ref VALUES (?,?,?,?,?)", attachment, source, product, type, deleted);
    }

    @Test void acceptsDocumentTypeWithNullLegacyOwnershipAndCategory() {
        ref(224, "product", 2, "STRUCTURE_DRAWING", false);
        assertSame(file, validate());
    }
    @Test void acceptsLegacyCategoryOnlyWithActiveProductReference() {
        file.setCategory("结构图"); ref(224, "product", 2, null, false);
        assertSame(file, validate());
    }
    @Test void rejectsOtherProductSourceAttachmentAndDeletedReferences() {
        file.setCategory("结构图"); file.setBizType("product"); file.setBizId(2L);
        ref(224, "product", 3, "STRUCTURE_DRAWING", false);
        ref(224, "quotation", 2, "STRUCTURE_DRAWING", false);
        ref(225, "product", 2, "STRUCTURE_DRAWING", false);
        ref(224, "product", 2, "STRUCTURE_DRAWING", true);
        assertThrows(BusinessException.class, this::validate);
    }
    @Test void rejectsWrongType() {
        ref(224, "product", 2, "ASSEMBLY_DRAWING", false);
        assertThrows(BusinessException.class, this::validate);
    }
    @Test void retainsPreviewGuard() {
        ref(224, "product", 2, "STRUCTURE_DRAWING", false);
        file.setFileName("drawing.dwg"); file.setFileType("application/octet-stream");
        assertTrue(assertThrows(BusinessException.class, this::validate).getMessage().contains("图片或PDF"));
        file.setFileName("drawing.PDF");
        assertSame(file, validate());
    }
}
