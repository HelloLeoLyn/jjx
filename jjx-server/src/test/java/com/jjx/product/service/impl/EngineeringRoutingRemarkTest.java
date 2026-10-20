package com.jjx.product.service.impl;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.jjx.product.controller.EngineeringRoutingController;
import com.jjx.product.domain.dto.RoutingRemarkQueryDTO;
import com.jjx.product.mapper.EngineeringRoutingItemMapper;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EngineeringRoutingRemarkTest {
    @Test
    void normalizesProcessSetAndRequiresExistingViewPermission() throws Exception {
        var mapper = mock(EngineeringRoutingItemMapper.class);
        var service = new EngineeringRoutingServiceImpl(null, null, mapper, null, null, null);
        var query = new RoutingRemarkQueryDTO();
        query.setProcessCategory(" PANEL ");
        query.setProcessIds(Arrays.asList(20L, 3L, 20L, null));
        query.setKeyword(" 粘 ");
        service.suggestRemarks(query);
        verify(mapper).selectRemarkSuggestions("PANEL", "3,20", "粘");
        var permission = EngineeringRoutingController.class.getMethod("remarkSuggestions", RoutingRemarkQueryDTO.class)
                .getAnnotation(SaCheckPermission.class);
        assertArrayEquals(new String[]{"engineering:routing:view"}, permission.value());
    }

    @Test
    void sharedSaveAndReadPathsKeepParentChildNotesSeparate() {
        var mapper = mock(EngineeringRoutingItemMapper.class);
        var service = spy(new EngineeringRoutingServiceImpl(null, null, mapper, null, null, null));
        var written = new ArrayList<com.jjx.engineering.domain.entity.EngineeringRoutingItem>();
        when(mapper.insert(any(com.jjx.engineering.domain.entity.EngineeringRoutingItem.class))).thenAnswer(inv -> {
            com.jjx.engineering.domain.entity.EngineeringRoutingItem item = inv.getArgument(0);
            item.setItemId((long) written.size()+1);
            written.add(item);
            return 1;
        });
        var single = new com.jjx.product.domain.dto.EngineeringRoutingItemDTO();
        single.setProcessId(3L);
        single.setRemark("单作业备注");
        single.setDescription("工序说明");
        single.setWorkInstruction("作业说明");
        var parent = new com.jjx.product.domain.dto.EngineeringRoutingItemDTO();
        parent.setRemark("组合父行备注");
        var child = new com.jjx.product.domain.dto.EngineeringRoutingItemDTO();
        child.setProcessId(20L);
        child.setRemark("子作业备注");
        child.setWorkInstruction("子作业说明");
        parent.setChildren(List.of(child));
        // 新增、修改和升版都复用 saveItems；分别模拟不同路线 ID。
        for (long id : new long[]{1L, 1L, 2L}) {
            written.clear();
            ReflectionTestUtils.invokeMethod(service, "saveItems", id, List.of(single, parent));
            assertEquals(List.of("单作业备注", "组合父行备注", "子作业备注"),
                    written.stream().map(com.jjx.engineering.domain.entity.EngineeringRoutingItem::getRemark).toList());
            assertEquals("工序说明", written.get(0).getDescription());
            assertEquals("作业说明", written.get(0).getWorkInstruction());
            assertEquals("子作业说明", written.get(2).getWorkInstruction());
            var routing = new com.jjx.engineering.domain.entity.EngineeringRouting();
            routing.setIsCurrent(0);
            doReturn(routing).when(service).getById(id);
            var rows = written.stream().map(item -> {
                var vo = new com.jjx.product.domain.vo.EngineeringRoutingItemVO();
                cn.hutool.core.bean.BeanUtil.copyProperties(item, vo);
                return vo;
            }).toList();
            when(mapper.selectVOsByRoutingId(id)).thenReturn(rows);
            var read = service.getRoutingItems(id).getItems();
            assertEquals("单作业备注", read.get(0).getRemark());
            assertEquals("组合父行备注", read.get(1).getRemark());
            assertEquals("子作业备注", read.get(1).getChildren().get(0).getRemark());
        }
    }

    private List<String> query(Connection db, String category, String key, String keyword) throws Exception {
        String sql = String.join(" ", EngineeringRoutingItemMapper.class
                .getMethod("selectRemarkSuggestions", String.class, String.class, String.class)
                .getAnnotation(Select.class).value());
        var params = new ArrayList<String>();
        var matcher = java.util.regex.Pattern.compile("#\\{(\\w+)}").matcher(sql);
        while (matcher.find()) params.add(switch (matcher.group(1)) {
            case "category" -> category;
            case "processKey" -> key;
            default -> keyword;
        });
        sql = sql.replaceAll("#\\{\\w+}", "?");
        try (var stmt = db.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) stmt.setString(i + 1, params.get(i));
            try (var rows = stmt.executeQuery()) {
                var result = new ArrayList<String>();
                while (rows.next()) result.add(rows.getString(1));
                return result;
            }
        }
    }

    @Test
    void realMapperSqlRanksSetsFrequencyRecencyFiltersAndLimits() throws Exception {
        // 隔离 H2/MySQL 模式验证实际 mapper SQL，不写真实业务库。
        try (var db = DriverManager.getConnection("jdbc:h2:mem:route_remark;MODE=MySQL;DB_CLOSE_DELAY=-1")) {
            var s = db.createStatement();
            s.execute("CREATE TABLE engineering_routing(routing_id BIGINT PRIMARY KEY,create_time TIMESTAMP,update_time TIMESTAMP)");
            s.execute("CREATE TABLE engineering_routing_item(detail_id BIGINT PRIMARY KEY,routing_id BIGINT,parent_id BIGINT,process_id BIGINT,process_category VARCHAR(64),remark VARCHAR(500),major_category VARCHAR(64) DEFAULT 'ASSEMBLY',create_time TIMESTAMP,update_time TIMESTAMP)");
            s.execute("INSERT INTO engineering_routing VALUES(1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
            s.execute("INSERT INTO engineering_routing_item(detail_id,routing_id,parent_id,process_id,process_category,remark,create_time,update_time) VALUES(1,1,NULL,NULL,'PANEL',' 组合备注 ','2020-01-01',NULL),(2,1,1,20,'PANEL','子行机密',CURRENT_TIMESTAMP,NULL),(3,1,1,3,'PANEL','子行机密',CURRENT_TIMESTAMP,NULL),(4,1,NULL,99,'PANEL','高频',CURRENT_TIMESTAMP,NULL),(5,1,NULL,99,'PANEL','高频',CURRENT_TIMESTAMP,NULL),(6,1,NULL,99,'UP_LINE','其他类别',CURRENT_TIMESTAMP,NULL),(7,999,NULL,99,'PANEL','孤儿路线',CURRENT_TIMESTAMP,NULL),(8,1,NULL,99,'PANEL','   ',CURRENT_TIMESTAMP,NULL),(9,1,NULL,99,'PANEL','近期','2025-01-01',NULL),(10,1,NULL,99,'PANEL','较旧','2024-01-01',NULL)");
            s.execute("INSERT INTO engineering_routing_item(detail_id,routing_id,process_id,process_category,remark,major_category) VALUES(11,1,99,'PANEL','印刷备注','PRINT')");
            assertEquals(List.of("组合备注", "高频", "近期", "较旧"), query(db, "PANEL", "3,20", ""));
            assertEquals(List.of("组合备注"), query(db, "PANEL", "3,20", "备注"));
            assertEquals(List.of(), query(db, "PANEL", "3,20", "%"));
            assertEquals(List.of(), query(db, "DOWN_LINE", "3,20", ""));
            assertEquals("高频", query(db, "PANEL", "", "").get(0));
            for (int i=20; i<35; i++) s.execute("INSERT INTO engineering_routing_item(detail_id,routing_id,parent_id,process_id,process_category,remark,create_time,update_time) VALUES("+i+",1,NULL,99,'PANEL','备注"+i+"',CURRENT_TIMESTAMP,NULL)");
            assertEquals(10, query(db, "PANEL", "3,20", "").size());
            // 重复组合备注只返回一次；非匹配工序高频不会压过上下文。
            s.execute("INSERT INTO engineering_routing_item(detail_id,routing_id,parent_id,process_id,process_category,remark,create_time,update_time) VALUES(40,1,NULL,99,'PANEL','组合备注',CURRENT_TIMESTAMP,NULL)");
            assertEquals("组合备注", query(db, "PANEL", "3,20", "").get(0));
        }
    }
}
