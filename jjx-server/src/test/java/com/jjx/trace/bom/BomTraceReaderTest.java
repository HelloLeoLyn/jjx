package com.jjx.trace.bom;
import com.jjx.system.domain.entity.SysOperLog;
import com.jjx.trace.domain.vo.UnifiedTraceEventVO;
import org.junit.jupiter.api.*;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class BomTraceReaderTest {
    BomTraceMapper mapper;BomTraceReader reader;List<SysOperLog> logs;List<BomTraceVersion> versions;
    LocalDateTime time=LocalDateTime.of(2026,11,20,10,0);
    BomTraceVersion version(long id,String value,Long parent) {
        var v=new BomTraceVersion();v.setBomId(id);v.setVersion(value);v.setBomCode("TEST-BOM");v.setOutputType("PRODUCT");v.setOutputId(1L);
        v.setParentBomId(parent);v.setCreateTime(time.plusMinutes(id));v.setCreatorName("测试人");return v;
    }
    SysOperLog log(long id,String bomId,String action) {
        var log=new SysOperLog();log.setId(id);log.setBizType("bom");log.setBizId(bomId);log.setAction(action);log.setStatus(1);log.setCreateTime(time.plusMinutes(id));return log;
    }
    @BeforeEach void setup() {
        mapper=mock(BomTraceMapper.class);reader=new BomTraceReader(mapper);
        versions=new ArrayList<>(List.of(version(1,"V1.0",null),version(4,"V1.1",1L),version(5,"V1.2",4L)));
        logs=new ArrayList<>(List.of(log(1,"1","新增BOM"),log(6,"4","修改BOM")));
        when(mapper.anchor(anyLong())).thenAnswer(c->versions.stream().filter(v->v.getBomId().equals(c.getArgument(0))).findFirst().orElse(null));
        when(mapper.versions("PRODUCT",1L)).thenAnswer(c->versions);when(mapper.snapshots("PRODUCT",1L)).thenReturn(List.of());
        when(mapper.logs(anyList())).thenAnswer(c->logs);
    }
    UnifiedTraceEventVO convert(SysOperLog log) {
        var e=new UnifiedTraceEventVO();e.setEventId("oper-"+log.getId());e.setBizId(log.getBizId());e.setBizType(log.getBizType());
        e.setAction(log.getAction());e.setTime(log.getCreateTime());e.setResult(log.getStatus());e.setDetail(log.getDetail());e.setGroupId(log.getOperGroupId());return e;
    }
    @Test void allVersionEntryIsStableAndArchiveEvidenceDoesNotInventOperationSuccess() {
        var first=reader.events(1L,null,1,100,this::convert);var current=reader.events(4L,null,1,100,this::convert);
        assertEquals(first.getRecords().stream().map(UnifiedTraceEventVO::getEventId).toList(),current.getRecords().stream().map(UnifiedTraceEventVO::getEventId).toList());
        var archive=first.getRecords().stream().filter(e->"bom-archive-5".equals(e.getEventId())).findFirst().orElseThrow();
        assertEquals(BomTraceReader.ARCHIVE,archive.getEvidenceSource());assertNull(archive.getResult());assertTrue(archive.getDetail().contains("原操作日志未留存"));
        var subset=reader.events(1L,BomTraceReader.VERSION_SCOPE,1,100,this::convert).getRecords();
        assertTrue(subset.stream().anyMatch(e->"bom-archive-4".equals(e.getEventId())));
        assertFalse(subset.stream().anyMatch(e->"bom-archive-5".equals(e.getEventId())));
        assertFalse(subset.stream().anyMatch(e->"oper-6".equals(e.getEventId())));
    }
    @Test void legacyRequestsAreMatchedByExactRootAndNeverTurnIntoApprovalOutcome() {
        var request=log(7,null,"整套审核通过");request.setOperParam("{\"request\":{\"products\":[{\"rootBomId\":4},{\"rootBomId\":\"4junk\"}]}}");logs.add(request);
        String before=request.getOperParam();var events=reader.events(4L,null,1,100,this::convert).getRecords();
        var e=events.stream().filter(v->"oper-7".equals(v.getEventId())).findFirst().orElseThrow();
        assertEquals(BomTraceReader.LEGACY,e.getEvidenceSource());assertEquals("4",e.getBizId());assertTrue(e.getAction().contains("历史请求记录"));
        assertTrue(e.getDetail().contains("未保存逐项业务结果"));assertNull(request.getBizId());assertEquals(before,request.getOperParam());
    }
    @Test void newNodeLogSuppressesItsBatchEnvelopeAndDeletionKeepsOldVersionLogs() {
        var node=log(8,"4",BomTraceRecorder.APPROVE);node.setOperGroupId("group1");
        node.setDetail("{\"bomObject\":{\"bomId\":4,\"bomCode\":\"TEST-BOM\",\"version\":\"V1.1\",\"outputType\":\"PRODUCT\",\"outputId\":1}}");
        var envelope=log(9,null,BomTraceRecorder.APPROVE);envelope.setOperGroupId("group1");envelope.setOperParam("{\"request\":{\"products\":[{\"rootBomId\":4}]}}");
        logs.add(node);logs.add(envelope);versions.removeIf(v->v.getBomId().equals(4L));
        when(mapper.snapshots("PRODUCT",1L)).thenReturn(List.of(node));when(mapper.deletedAnchor("4")).thenReturn(node);
        assertTrue(reader.context(1L).versions().stream().anyMatch(v->v.getBomId().equals(4L)&&v.isDeleted()));
        var events=reader.events(4L,BomTraceReader.VERSION_SCOPE,1,100,this::convert).getRecords();
        assertTrue(events.stream().anyMatch(e->"oper-6".equals(e.getEventId())));assertTrue(events.stream().anyMatch(e->"oper-8".equals(e.getEventId())));
        assertFalse(events.stream().anyMatch(e->"oper-9".equals(e.getEventId())));
    }
    @Test void paginationFollowsFilteringAndUnrelatedLogsCannotLeak() {
        logs.add(log(10,"99","其它BOM"));
        var all=reader.events(4L,null,1,100,this::convert);var a=reader.events(4L,null,1,2,this::convert);var b=reader.events(4L,null,2,2,this::convert);
        assertEquals(all.getTotal(),a.getTotal());assertEquals(4,all.getTotal());
        assertTrue(Collections.disjoint(a.getRecords().stream().map(UnifiedTraceEventVO::getEventId).toList(),b.getRecords().stream().map(UnifiedTraceEventVO::getEventId).toList()));
        assertFalse(all.getRecords().stream().anyMatch(e->"oper-10".equals(e.getEventId())));
    }
}
