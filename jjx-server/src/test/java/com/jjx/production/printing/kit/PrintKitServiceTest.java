package com.jjx.production.printing.kit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjx.common.exception.BusinessException;
import com.jjx.engineering.printing.screen.ScreenService;
import com.jjx.inventory.service.InventoryAlertService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PrintKitServiceTest {
    final JdbcTemplate db=mock(JdbcTemplate.class);
    final ScreenService screen=mock(ScreenService.class);
    final InventoryAlertService materials=mock(InventoryAlertService.class);
    final PrintKitService service=new PrintKitService(db,new ObjectMapper(),screen,materials,null);
    {
        when(db.queryForList(contains("FROM production_order"),eq(1L)))
                .thenReturn(List.of(Map.of("orderId",1L,"orderNo","MO1","productId",2L,"specVersionId",3L)));
        when(db.queryForList(contains("FROM engineering_resource_product_rel r"),eq(2L)))
                .thenReturn(List.of(Map.of("die_no","D1","status","MAINTENANCE")));
    }

    void snapshot(String raw) {
        when(db.queryForList(contains("FROM product_work_spec_item"),eq(3L)))
                .thenReturn(List.of(Map.of("snapshot_json",raw)));
    }

    @Test void requiredPlateGapBlocksButDieOnlyWarnsAndChecksFrozenRequirements() {
        snapshot("""
            {"printingItems":[{"routingItemId":10,"artworkVersionId":5,"artworkNo":"A1","requirements":{"meshMin":120}}]}
            """);
        when(screen.countUsablePlate(5,"{\"meshMin\":120}")).thenReturn(0);
        assertEquals(false,service.orderKit(1).get("ok"));
        when(screen.countUsablePlate(5,"{\"meshMin\":120}")).thenReturn(1);
        var result=service.orderKit(1);
        assertEquals(true,result.get("ok"));
        assertEquals(true,result.get("verified"));
        assertFalse(((List<?>)result.get("warnings")).isEmpty());
        verify(screen,times(2)).countUsablePlate(5,"{\"meshMin\":120}");
        verify(screen,never()).countUsablePlate(anyLong());
    }

    @Test void absentOrInvalidSnapshotCannotPassOrCloseShortages() {
        for(String raw:new String[]{"{}","not-json","{\"printingItems\":[{}]}",
                "{\"printingItems\":[{\"routingItemId\":10,\"artworkVersionId\":5}]}"}) {
            snapshot(raw);
            var result=service.orderKit(1);
            assertEquals(false,result.get("ok"),raw);
            assertEquals(false,result.get("verified"),raw);
            assertThrows(BusinessException.class,()->service.recheck(1));
        }
        verify(db,never()).update(anyString(),any(Object[].class));
    }

    @Test void explicitEmptyPrintingListIsVerifiedNonPrintingAndNoProductLinksIsUnknown() {
        snapshot("{\"printingItems\":[]}");
        assertEquals(true,service.orderKit(1).get("ok"));
        when(db.queryForList(contains("SELECT a.artwork_id"),eq(2L))).thenReturn(List.of());
        assertEquals(false,service.productPrecheck(2).get("ok"));
        assertEquals(false,service.productPrecheck(2).get("verified"));
    }
}
