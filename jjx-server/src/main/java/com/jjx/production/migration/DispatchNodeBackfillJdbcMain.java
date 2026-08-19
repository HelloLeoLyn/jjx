package com.jjx.production.migration;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * P1-E 正式 backfill 执行器（独立 JDBC，不依赖 Spring 上下文）
 * <p>
 * 用途：8080 服务未运行、@SpringBootTest 上下文不可用（knife4j 配置缺失）时，
 * 以最小 JDBC 方式执行与 DispatchNodeBackfill 相同的迁移规则：
 * - 空 operators → 不建节点
 * - 1 个 operator → 1 个 ACTIVE node
 * - 多个 operator → 前 N-1 DELEGATED、最后 1 ACTIVE，parentNodeId 顺序串联
 * - remark 标记 LEGACY_BACKFILL（+ 歧义 LEGACY_AMBIGUOUS_ORDER；org 重建 ORG_RECONSTRUCTED）
 * - 幂等：已有节点跳过
 * <p>
 * 用法：java -cp <classpath> com.jjx.production.migration.DispatchNodeBackfillJdbcMain dry-run
 *       java -cp <classpath> com.jjx.production.migration.DispatchNodeBackfillJdbcMain run
 */
public class DispatchNodeBackfillJdbcMain {

    private static final String URL = "jdbc:mysql://localhost:3306/jjx_erp_db"
            + "?useUnicode=true&characterEncoding=utf8&zeroDateTimeBehavior=convertToNull"
            + "&useSSL=false&serverTimezone=GMT%2B8&allowPublicKeyRetrieval=true";
    private static final String USER = "root";
    private static final String PASSWORD = "123456";

    public static void main(String[] args) throws Exception {
        String mode = args.length > 0 ? args[0] : "dry-run";
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD)) {
            if ("run".equalsIgnoreCase(mode)) {
                backfillAll(conn);
            } else {
                dryRunAll(conn);
            }
        }
    }

    private static void dryRunAll(Connection conn) throws SQLException {
        List<long[]> dispatches = loadDispatches(conn);
        System.out.println("==== DRY-RUN ====");
        int migrated = 0, skipped = 0;
        for (long[] d : dispatches) {
            long dispatchId = d[0];
            String operators = getOperators(conn, dispatchId);
            List<DispatchNodeBackfillParser.NodeDraft> drafts =
                    DispatchNodeBackfillParser.parseChain(operators);
            boolean hasNode = hasNodes(conn, dispatchId);
            boolean ambiguous = drafts.stream().anyMatch(x -> x.ambiguous);
            StringBuilder sb = new StringBuilder("dispatchId=").append(dispatchId)
                    .append(" operators=").append(operators).append(" → 节点=").append(drafts.size()).append(" (");
            for (int i = 0; i < drafts.size(); i++) {
                if (i > 0) sb.append(" → ");
                sb.append(drafts.get(i).assigneeName);
                if (i == drafts.size() - 1) sb.append(" ACTIVE");
            }
            sb.append(")");
            if (hasNode) {
                sb.append(" [SKIP: 已有Node]");
                skipped++;
            } else if (drafts.isEmpty()) {
                sb.append(" [SKIP: 无执行人]");
                skipped++;
            } else {
                if (ambiguous) sb.append(" [LEGACY_AMBIGUOUS_ORDER]");
                migrated++;
            }
            System.out.println("  " + sb);
        }
        System.out.println("==== 汇总: scanned=" + dispatches.size()
                + ", migrated=" + migrated + ", skipped=" + skipped + ", errors=0 ====");
    }

    private static void backfillAll(Connection conn) throws SQLException {
        System.out.println("==== BACKFILL RUN ====");
        List<long[]> dispatches = loadDispatches(conn);
        int migrated = 0, skipped = 0, errors = 0;
        for (long[] d : dispatches) {
            long dispatchId = d[0];
            try {
                int created = backfillDispatch(conn, dispatchId);
                if (created < 0) skipped++;
                else if (created == 0) skipped++;
                else migrated++;
                System.out.println("  dispatchId=" + dispatchId + " → 创建节点=" + created);
            } catch (Exception e) {
                errors++;
                System.err.println("  dispatchId=" + dispatchId + " 失败: " + e.getMessage());
            }
        }
        System.out.println("==== 汇总: scanned=" + dispatches.size()
                + ", migrated=" + migrated + ", skipped=" + skipped + ", errors=" + errors + " ====");
        if (errors > 0) {
            System.out.println("RESULT=ERRORS_PRESENT");
        } else {
            System.out.println("RESULT=OK");
        }
    }

    /** 返回 -1=已有节点跳过；0=无执行人；>0=创建数 */
    private static int backfillDispatch(Connection conn, long dispatchId) throws SQLException {
        if (hasNodes(conn, dispatchId)) {
            System.out.println("  [SKIP] dispatchId=" + dispatchId + " 已有 Node");
            return -1;
        }
        String operators = getOperators(conn, dispatchId);
        List<DispatchNodeBackfillParser.NodeDraft> drafts =
                DispatchNodeBackfillParser.parseChain(operators);
        if (drafts.isEmpty()) return 0;

        boolean ambiguous = drafts.stream().anyMatch(x -> x.ambiguous);
        String baseRemark = DispatchNodeBackfillParser.MARKER_LEGACY_BACKFILL
                + (ambiguous ? "," + DispatchNodeBackfillParser.MARKER_AMBIGUOUS_ORDER : "");

        String assignedBy = getAssignedBy(conn, dispatchId);
        Timestamp assignedAt = getAssignTime(conn, dispatchId);

        long prevNodeId = 0;
        int created = 0;
        for (int i = 0; i < drafts.size(); i++) {
            DispatchNodeBackfillParser.NodeDraft draft = drafts.get(i);
            boolean last = (i == drafts.size() - 1);
            String status = last ? "ACTIVE" : "DELEGATED";
            long nodeId = insertNode(conn, dispatchId, prevNodeId == 0 ? null : prevNodeId,
                    draft.assigneeId, draft.assigneeName, status,
                    assignedBy, assignedAt, baseRemark);
            prevNodeId = nodeId;
            created++;
        }
        return created;
    }

    private static long insertNode(Connection conn, long dispatchId, Long parentNodeId,
                                   Long assigneeId, String assigneeName, String status,
                                   String assignedBy, Timestamp assignedAt, String remark) throws SQLException {
        // org 快照：按当前用户部门重建（非真实历史），标记 ORG_RECONSTRUCTED
        String[] org = orgSnapshotOf(conn, assigneeId);
        String remarkFinal = remark + (org[0] != null ? "," + DispatchNodeBackfillParser.MARKER_ORG_RECONSTRUCTED : "");
        String sql = "INSERT INTO production_dispatch_node "
                + "(dispatch_id, parent_node_id, assignee_type, assignee_id, assignee_name,"
                + " org_id, org_name, org_path, node_status, assigned_by, assigned_by_name, assigned_at, remark, create_by, create_time)"
                + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,NOW())";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, dispatchId);
            if (parentNodeId == null) ps.setNull(2, Types.BIGINT);
            else ps.setLong(2, parentNodeId);
            ps.setString(3, "USER");
            ps.setLong(4, assigneeId);
            ps.setString(5, assigneeName);
            if (org[0] == null) ps.setNull(6, Types.BIGINT);
            else ps.setLong(6, Long.parseLong(org[0]));
            ps.setString(7, org[1]);
            ps.setString(8, org[2]);
            ps.setString(9, status);
            if (assignedBy == null) ps.setNull(10, Types.BIGINT);
            else ps.setLong(10, Long.parseLong(assignedBy));
            ps.setString(11, assignedBy);
            if (assignedAt == null) ps.setNull(12, Types.TIMESTAMP);
            else ps.setTimestamp(12, assignedAt);
            ps.setString(13, remarkFinal);
            ps.setString(14, "LEGACY_BACKFILL");
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private static String[] orgSnapshotOf(Connection conn, Long userId) throws SQLException {
        // 查用户 dept_id → dept_name → 祖先链
        Long deptId = null;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT dept_id FROM sys_user WHERE user_id=? AND del_flag='0'")) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) deptId = rs.getLong(1);
            }
        }
        if (deptId == null) return new String[]{null, null, null};
        String deptName = null;
        List<String> path = new ArrayList<>();
        Long cur = deptId;
        int guard = 0;
        while (cur != null && guard++ < 20) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT dept_id, dept_name, parent_id FROM sys_dept WHERE dept_id=? AND del_flag='0'")) {
                ps.setLong(1, cur);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) break;
                    if (deptName == null) deptName = rs.getString("dept_name");
                    path.add(0, String.valueOf(rs.getLong("dept_id")));
                    long parent = rs.getLong("parent_id");
                    if (parent == 0 || rs.wasNull()) break;
                    cur = parent;
                }
            }
        }
        return new String[]{String.valueOf(deptId), deptName, String.join("/", path)};
    }

    private static List<long[]> loadDispatches(Connection conn) throws SQLException {
        List<long[]> ids = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT dispatch_id FROM production_dispatch"
                             + " WHERE operators IS NOT NULL AND operators != '' ORDER BY dispatch_id")) {
            while (rs.next()) ids.add(new long[]{rs.getLong(1)});
        }
        return ids;
    }

    private static String getOperators(Connection conn, long dispatchId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT operators FROM production_dispatch WHERE dispatch_id=?")) {
            ps.setLong(1, dispatchId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private static boolean hasNodes(Connection conn, long dispatchId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM production_dispatch_node WHERE dispatch_id=?")) {
            ps.setLong(1, dispatchId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1) > 0;
            }
        }
    }

    private static String getAssignedBy(Connection conn, long dispatchId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT assigned_by FROM production_dispatch WHERE dispatch_id=?")) {
            ps.setLong(1, dispatchId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private static Timestamp getAssignTime(Connection conn, long dispatchId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT assign_time, create_time FROM production_dispatch WHERE dispatch_id=?")) {
            ps.setLong(1, dispatchId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                Timestamp t = rs.getTimestamp(1);
                return t != null ? t : rs.getTimestamp(2);
            }
        }
    }
}
