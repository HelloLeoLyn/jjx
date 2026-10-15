package com.jjx.system.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class AttachmentEvidenceServiceTest {
    JdbcTemplate jdbc;
    AttachmentEvidenceService service;

    @BeforeEach void setup() {
        var ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(ds);
        service = new AttachmentEvidenceService(jdbc, new ObjectMapper());
        jdbc.execute("CREATE TABLE sys_attachment(id BIGINT PRIMARY KEY,file_name VARCHAR(100),category VARCHAR(50),deleted INT)");
        jdbc.execute("CREATE TABLE sys_attachment_ref(id BIGINT PRIMARY KEY,attachment_id BIGINT,source_type VARCHAR(50),source_id BIGINT,document_type VARCHAR(50),display_name VARCHAR(100),deleted INT)");
    }

    void file(long id, String source, long sourceId, String type, String category, int deleted) {
        jdbc.update("INSERT INTO sys_attachment VALUES (?,?,?,0)", id, "file-" + id, category);
        jdbc.update("INSERT INTO sys_attachment_ref VALUES (?,?,?,?,?,NULL,?)", id + 100, id, source, sourceId, type, deleted);
    }

    @Test void snapshotOnlyIncludesBoundDrawingsOfThisSample() {
        file(1,"sample",10,"CUSTOMER_DRAWING",null,0);
        file(2,"sample",20,"ENGINEERING_DRAWING",null,0);
        file(3,"quotation",10,"ENGINEERING_DRAWING",null,0);
        file(4,"sample",10,"BOM",null,0);
        file(5,"sample",10,"ENGINEERING_DRAWING",null,1);
        file(6,"sample",10,null,"ENGINEERING_DRAWING",0);
        assertEquals("[101,106]", service.snapshotSampleDrawings(10L));
    }

    @Test void laterUploadsCannotChangeEarlierRound() {
        file(1,"sample",10,"ENGINEERING_DRAWING",null,0);
        String first = service.snapshotSampleDrawings(10L);
        file(2,"sample",10,"ENGINEERING_DRAWING",null,0);
        assertEquals("[101,102]", service.snapshotSampleDrawings(10L));
        assertEquals(1, service.listSnapshot(first,"sample",10L).size());
        assertEquals(101L, service.listSnapshot(first,"sample",10L).get(0).get("refId"));
    }

    @Test void snapshotReadCannotExposeAnotherSourceOrDetachedRef() {
        file(1,"sample",10,"ENGINEERING_DRAWING",null,0);
        file(2,"sample",20,"ENGINEERING_DRAWING",null,0);
        file(3,"quotation",10,"ENGINEERING_DRAWING",null,0);
        file(4,"sample",10,"ENGINEERING_DRAWING",null,1);
        assertEquals(1, service.listSnapshot("[101,102,103,104]","sample",10L).size());
        assertTrue(service.listSnapshot(null,"sample",10L).isEmpty());
    }

    @Test void invalidEvidenceIsReportedInsteadOfShowingCurrentFiles() {
        assertThrows(IllegalStateException.class, () -> service.listSnapshot("bad-json","sample",10L));
    }
}
