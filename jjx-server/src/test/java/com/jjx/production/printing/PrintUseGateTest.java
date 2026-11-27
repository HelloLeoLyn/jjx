package com.jjx.production.printing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjx.common.exception.BusinessException;
import com.jjx.engineering.printing.artwork.ArtworkService;
import com.jjx.engineering.printing.screen.ScreenService;
import com.jjx.system.utils.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PrintUseGateTest {
    final JdbcTemplate db=mock(JdbcTemplate.class);
    final ArtworkService artwork=mock(ArtworkService.class);
    final ScreenService screen=mock(ScreenService.class);
    final PrintUseService service=new PrintUseService(db,new ObjectMapper(),artwork,screen);
    final Map<String,Object> identity=Map.of("execution_id",1L,"routing_item_id",10L,
            "major_category","PRINT","work_spec_version_id",3L);
    {
        when(db.queryForList(contains("FROM production_operation_execution"),eq(1L)))
                .thenReturn(List.of(identity));
        snapshot("""
            {"printingItems":[{"routingItemId":10,"artworkVersionId":5,"defaultPlateId":19,"requirements":{"meshMin":120}}]}
            """);
        when(db.queryForObject("select last_insert_id()",Long.class)).thenReturn(55L);
    }
    void snapshot(String raw) {
        when(db.queryForList(contains("FROM product_work_spec_item"),eq(3L)))
                .thenReturn(List.of(Map.of("snapshot_json",raw)));
    }
    PrintUseCommands command(Long version,Long spec,Long route) {
        return new PrintUseCommands("use-test",1L,20L,version,spec,route,"同版更换");
    }
    Map<String,Object> start(PrintUseCommands command) {
        try(var user=mockStatic(SecurityUtils.class)) {
            user.when(SecurityUtils::getUsername).thenReturn("print-use-test");
            return service.start(command);
        }
    }
    void assertNoBusinessWrites() {
        assertTrue(mockingDetails(db).getInvocations().stream().noneMatch(i->i.getMethod().getName().equals("update")));
    }

    @Test void sameVersionBackupUsesFrozenRequirementsAndDoesNotChangeSpecVersion() {
        var result=start(command(null,null,null)); // 不能通过省略客户端版本绕过工单要求。
        assertEquals(20L,result.get("plateId"));
        assertEquals(5L,result.get("versionId"));
        verify(artwork).requireUsableVersion(5);
        verify(screen).requireUsablePlate(5,20,"{\"meshMin\":120}");
        assertTrue(mockingDetails(db).getInvocations().stream()
                .filter(i->i.getMethod().getName().equals("update"))
                .allMatch(i->i.getArgument(0,String.class).contains("print_use_segment")));
    }

    @Test void missingSnapshotAndWrongIdentityCannotStart() {
        assertThrows(BusinessException.class,()->start(command(6L,3L,10L)));
        assertThrows(BusinessException.class,()->start(command(5L,9L,10L)));
        assertThrows(BusinessException.class,()->start(command(5L,3L,11L)));
        snapshot("{}");
        assertThrows(BusinessException.class,()->start(command(null,null,null)));
        assertThrows(BusinessException.class,()->service.requirePlan(null,10L));
        verifyNoInteractions(artwork,screen);
        assertNoBusinessWrites();
    }

    @Test void unsuitablePlateCannotClosePreviousUseSegment() {
        doThrow(new BusinessException(422,"参数不符合")).when(screen)
                .requireUsablePlate(5,20,"{\"meshMin\":120}");
        assertThrows(BusinessException.class,()->start(command(5L,3L,10L)));
        assertNoBusinessWrites();
    }

    @Test void identityChangedWhileLockingCannotWriteUseSegment() {
        when(db.queryForList(contains("FOR UPDATE"),eq(1L))).thenReturn(List.of(Map.of(
                "execution_id",1L,"routing_item_id",11L,"major_category","PRINT","work_spec_version_id",3L)));
        assertThrows(BusinessException.class,()->start(command(5L,3L,10L)));
        assertNoBusinessWrites();
    }
}
