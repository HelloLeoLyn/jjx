package com.jjx.product.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjx.common.enums.ApproveStatusEnum;
import com.jjx.common.exception.BusinessException;
import com.jjx.engineering.printing.artwork.ArtworkService;
import com.jjx.engineering.printing.screen.ScreenService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PrintingSpecSnapshotTest {
    final ObjectMapper json=new ObjectMapper();
    final JdbcTemplate db=mock(JdbcTemplate.class);
    final ArtworkService artwork=mock(ArtworkService.class);
    final ScreenService screen=new ScreenService(null,json);
    final WorkSpecDocsetSnapshotService service=new WorkSpecDocsetSnapshotService(null,null,null,null,null,db,json,null);
    {
        ReflectionTestUtils.setField(service,"artworkService",artwork);
        ReflectionTestUtils.setField(service,"screenService",screen);
    }

    @Test void frozenMembersKeepOrderIdentityAndRequirementsWhenCurrentVersionChanges() throws Exception {
        var routing=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree("""
                {"routingId":7,"routingVersion":"V2","items":[
                  {"itemId":11,"majorCategory":"PRINT","artworkVersionId":3,"printRequirements":"{\\\"meshMin\\\":100}"},
                  {"itemId":12,"majorCategory":"PRINT","artworkVersionId":4,"children":[]}]}
                """);
        routing.put("approveStatus",ApproveStatusEnum.APPROVED.getValue());
        when(db.queryForMap(anyString(),eq(3L))).thenReturn(Map.of("artwork_id",1L,"artwork_no","A1","name","旧名称","version_no","V2"));
        when(db.queryForMap(anyString(),eq(4L))).thenReturn(Map.of("artwork_id",2L,"artwork_no","A2","name","第二图","version_no","V1"));
        when(db.queryForList(contains("FROM engineering_resource_maintenance"),eq(3L))).thenReturn(java.util.List.of(
                Map.of("result_snapshot","{\"controlledContent\":{\"contentDigest\":\"release-digest\",\"verification\":\"VERIFIED\"}}")));
        var frozen=service.freezePrinting(routing);
        assertEquals("release-digest",frozen.get(0).path("controlledContent").path("contentDigest").asText());
        assertEquals("UNVERIFIED",frozen.get(1).path("controlledContent").path("verification").asText());
        assertEquals(2,frozen.size());
        assertEquals(11,frozen.get(0).path("routingItemId").asInt());
        assertEquals(2,frozen.get(1).path("memberOrder").asInt());
        assertEquals(100,frozen.get(0).path("requirements").path("meshMin").asInt());
        String historical=frozen.toString();
        ((com.fasterxml.jackson.databind.node.ObjectNode)routing.path("items").get(0)).put("artworkVersionId",99);
        assertEquals(historical,frozen.toString());
        assertEquals(3,routing.path("items").get(0).path("printingSnapshot").path("artworkVersionId").asInt());
        verify(artwork).requireUsableVersion(3); verify(artwork).requireUsableVersion(4);
    }

    @Test void unapprovedAndUnknownIdentityCannotBecomeControlledSnapshot() throws Exception {
        var route=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree("{\"items\":[{\"majorCategory\":\"PRINT\",\"itemId\":1}]}");
        assertThrows(BusinessException.class,()->service.freezePrinting(route));
        route.put("approveStatus",ApproveStatusEnum.APPROVED.getValue());
        assertThrows(BusinessException.class,()->service.freezePrinting(route));
        verifyNoInteractions(db,artwork);
    }
}
