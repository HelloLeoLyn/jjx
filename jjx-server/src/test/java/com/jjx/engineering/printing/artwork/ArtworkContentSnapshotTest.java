package com.jjx.engineering.printing.artwork;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ArtworkContentSnapshotTest {
    final JdbcTemplate db=mock(JdbcTemplate.class);
    final ObjectMapper json=new ObjectMapper();
    final String fileHash="a".repeat(64);

    Map<String,Object> version(long id,String spec) {
        return new HashMap<>(Map.of("version_id",id,"drawing_file_id",id,"technical_spec",spec));
    }
    void file(String sha,String path) {
        var row=new HashMap<String,Object>();row.put("sha256",sha);row.put("file_path",path);
        when(db.queryForList(contains("FROM sys_attachment"),any(Object.class))).thenReturn(List.of(row));
    }
    @Test void sameContentAcrossApprovedIdentitiesAndPhysicalMetadataHasSameDigest() {
        file(fileHash,null);
        var v1=version(1,"  线宽0.1mm\r\n目数120  ");
        var v2=version(2,"线宽0.1mm\n目数120");v2.put("filmNo","FL-99");v2.put("location","另一库位");v2.put("approved_time","别的日期");
        var a=ArtworkContentSnapshot.capture(db,json,"/missing",v1);
        var b=ArtworkContentSnapshot.capture(db,json,"/missing",v2);
        assertEquals(a.path("contentDigest"),b.path("contentDigest"));
        assertEquals("VERIFIED",a.path("verification").asText());
        assertEquals("film-content-v1",a.path("algorithm").asText());
        assertNotEquals(a.path("contentDigest"),ArtworkContentSnapshot.capture(db,json,"/missing",version(1,"线宽0.2mm")).path("contentDigest"));
        file("b".repeat(64),null);
        assertNotEquals(a.path("contentDigest"),ArtworkContentSnapshot.capture(db,json,"/missing",v1).path("contentDigest"));
    }
    @Test void missingFileHashIsCalculatedFromBytesOrExplicitlyUnverified(@TempDir Path base) throws Exception {
        Files.writeString(base.resolve("drawing.txt"),"相同文件字节");
        file(null,"drawing.txt");
        var actual=ArtworkContentSnapshot.capture(db,json,base.toString(),version(1,""));
        assertEquals("VERIFIED",actual.path("verification").asText());
        assertEquals(com.jjx.common.utils.Sha256Utils.ofFile(base.resolve("drawing.txt")),actual.path("drawingSha256").asText());
        file(null,"../../outside");
        var unknown=ArtworkContentSnapshot.capture(db,json,base.toString(),version(1,""));
        assertEquals("UNVERIFIED",unknown.path("verification").asText());
        assertTrue(unknown.path("contentDigest").isNull());
    }
    @Test void historicalContentUsesReleaseFactWithoutReadingCurrentFile() {
        when(db.queryForList(contains("FROM engineering_resource_maintenance"),eq(1L)))
                .thenReturn(List.of(Map.of("result_snapshot","{\"controlledContent\":{\"contentDigest\":\"frozen\",\"algorithm\":\"v1\",\"verification\":\"VERIFIED\"}}")));
        var old=ArtworkContentSnapshot.released(db,json,1);
        assertEquals("frozen",old.path("contentDigest").asText());
        old.put("contentDigest","local change");
        assertEquals("frozen",ArtworkContentSnapshot.released(db,json,1).path("contentDigest").asText());
        assertEquals("UNVERIFIED",ArtworkContentSnapshot.released(db,json,2).path("verification").asText());
        verify(db,never()).queryForList(contains("FROM sys_attachment"),any(Object.class));
    }
}
