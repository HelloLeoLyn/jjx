package com.jjx.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.jjx.system.domain.entity.ReviewFlow;
import com.jjx.system.mapper.ReviewFlowMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReviewFlowServiceImplTest {

    static {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "review-flow-test"), ReviewFlow.class);
    }

    @Test
    void submitStartsAtOneAndAdvancesFromMaximumRound() {
        MapperStub stub = new MapperStub();
        stub.selected.add(null);
        stub.selected.add(flow(8L, 3, "REJECT"));
        ReviewFlowServiceImpl service = service(stub);

        ReviewFlow first = service.record("sales_order", 10L, "SUBMIT", "提交审核",
                1, 2, null, null);
        ReviewFlow next = service.record("sales_order", 10L, "SUBMIT", "重新提交审核",
                5, 2, null, null);

        assertEquals(1, first.getRoundNo());
        assertEquals(4, next.getRoundNo());
    }

    @Test
    void approvalUsesLatestSubmitRoundAndRecordsSnapshot() {
        MapperStub stub = new MapperStub();
        stub.selected.add(flow(12L, 4, "REJECT"));
        stub.selected.add(flow(9L, 3, "SUBMIT"));
        ReviewFlowServiceImpl service = service(stub);

        ReviewFlow recorded = service.record("purchase_order", 22L, "APPROVE", "审批通过",
                2, 3, "同意", "11,12");

        assertEquals(3, recorded.getRoundNo());
        assertEquals(1001L, recorded.getOperatorId());
        assertEquals("测试审核员", recorded.getOperatorName());
        assertEquals("同意", recorded.getComment());
        assertEquals("11,12", recorded.getAttachmentIds());
        assertEquals(List.of(recorded), stub.inserted);
    }

    @Test
    void listOrdersByRoundAndFlowIdAscending() {
        MapperStub stub = new MapperStub();
        List<ReviewFlow> rows = List.of(flow(1L, 1, "SUBMIT"), flow(2L, 1, "APPROVE"));
        stub.listResult = rows;
        ReviewFlowServiceImpl service = service(stub);

        assertEquals(rows, service.listByBiz("engineering_bom", 33L));
        String sql = stub.listWrapper.getSqlSegment().toLowerCase();
        assertTrue(sql.matches("(?s).*order by.*round_no.*asc.*flow_id.*asc.*"), sql);
    }

    private static ReviewFlowServiceImpl service(MapperStub stub) {
        return new ReviewFlowServiceImpl(stub.mapper()) {
            @Override
            protected Long currentUserId() {
                return 1001L;
            }

            @Override
            protected String currentUsername() {
                return "测试审核员";
            }
        };
    }

    private static ReviewFlow flow(Long id, int round, String action) {
        ReviewFlow flow = new ReviewFlow();
        flow.setFlowId(id);
        flow.setRoundNo(round);
        flow.setActionCode(action);
        return flow;
    }

    private static class MapperStub {
        private final Deque<ReviewFlow> selected = new LinkedList<>();
        private final List<ReviewFlow> inserted = new ArrayList<>();
        private List<ReviewFlow> listResult = List.of();
        private Wrapper<ReviewFlow> listWrapper;

        private ReviewFlowMapper mapper() {
            return (ReviewFlowMapper) Proxy.newProxyInstance(
                    ReviewFlowMapper.class.getClassLoader(), new Class<?>[]{ReviewFlowMapper.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "selectOne" -> selected.pollFirst();
                        case "insert" -> {
                            inserted.add((ReviewFlow) args[0]);
                            yield 1;
                        }
                        case "selectList" -> {
                            listWrapper = (Wrapper<ReviewFlow>) args[0];
                            yield listResult;
                        }
                        case "toString" -> "ReviewFlowMapperStub";
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
        }
    }
}
