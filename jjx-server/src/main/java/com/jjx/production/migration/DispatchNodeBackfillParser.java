package com.jjx.production.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Legacy production_dispatch.operators(JSON数组) → Node 责任链 解析器（纯函数，可单测）
 * <p>
 * 迁移规则（P1-A 定稿）：
 * - operators 为空/null → 空链（不创建 Node）
 * - 单个 operator → 单节点链（最终 ACTIVE）
 * - 多个 operator → 按 legacy JSON 数组稳定顺序构造链；前 N-1 个 DELEGATED，最后一个 ACTIVE
 * - 旧 level 字段不再作为新模型层级依据；同 level 多人按 JSON 当前顺序构造兼容链并标记歧义
 * - 非法 JSON → 抛 BackfillParseException（由执行器捕获跳过该 dispatch，不中断整体）
 */
public class DispatchNodeBackfillParser {

    /** 迁移标记（写入 Node.remark） */
    public static final String MARKER_LEGACY_BACKFILL = "LEGACY_BACKFILL";
    /** 同 level 多人歧义标记 */
    public static final String MARKER_AMBIGUOUS_ORDER = "LEGACY_AMBIGUOUS_ORDER";
    /** 组织快照为迁移时重建（非真实历史）标记 */
    public static final String MARKER_ORG_RECONSTRUCTED = "ORG_RECONSTRUCTED";

    private static final ObjectMapper OM = new ObjectMapper();

    private DispatchNodeBackfillParser() {
    }

    /**
     * 责任链草稿节点（执行器补 org 快照/assignedAt/assignedBy 后落库）
     */
    public static class NodeDraft {
        public final Long assigneeId;
        public final String assigneeName;
        /** 该节点处于同 level 多人歧义段（仅信息用途，remark 统一标记） */
        public final boolean ambiguous;

        public NodeDraft(Long assigneeId, String assigneeName, boolean ambiguous) {
            this.assigneeId = assigneeId;
            this.assigneeName = assigneeName;
            this.ambiguous = ambiguous;
        }
    }

    /**
     * 解析 legacy operators JSON → 责任链草稿
     *
     * @param operatorsJson operators 列原始值（可为 null/空）
     * @return 按责任顺序排列的节点草稿；空/无执行人 → 空列表
     * @throws BackfillParseException operators 非法 JSON
     */
    public static List<NodeDraft> parseChain(String operatorsJson) {
        if (operatorsJson == null || operatorsJson.isBlank()) {
            return new ArrayList<>();
        }
        JsonNode arr;
        try {
            arr = OM.readTree(operatorsJson);
        } catch (Exception e) {
            throw new BackfillParseException("operators 非法 JSON: " + e.getMessage(), e);
        }
        if (arr == null) {
            return new ArrayList<>();
        }
        if (!arr.isArray()) {
            // 合法 JSON 但非数组：格式异常，保守报错（静默返回空会丢失"需要人工处理"的信号）
            throw new BackfillParseException("operators 不是 JSON 数组: " + operatorsJson);
        }
        if (arr.isEmpty()) {
            return new ArrayList<>();
        }
        List<NodeDraft> drafts = new ArrayList<>();
        // 同 level 多人歧义检测：统计各 level 出现次数
        java.util.Map<Integer, Integer> levelCount = new java.util.HashMap<>();
        for (JsonNode n : arr) {
            int lv = n.path("level").asInt(1);
            levelCount.merge(lv, 1, Integer::sum);
        }
        // 按数组稳定顺序生成链（旧 level 不作为层级依据）
        for (JsonNode n : arr) {
            Long userId = n.path("userId").asLong(0);
            if (userId == null || userId == 0L) {
                // 无 userId 的非法项：保留名字但不产生有效节点（执行器会跳过），记为歧义
                continue;
            }
            String userName = n.path("userName").asText("");
            int lv = n.path("level").asInt(1);
            boolean ambiguous = levelCount.getOrDefault(lv, 0) > 1;
            drafts.add(new NodeDraft(userId, userName, ambiguous));
        }
        return drafts;
    }

    /**
     * 解析异常（执行器捕获后跳过该 dispatch 并记录）
     */
    public static class BackfillParseException extends RuntimeException {
        public BackfillParseException(String message, Throwable cause) {
            super(message, cause);
        }

        public BackfillParseException(String message) {
            super(message);
        }
    }
}
