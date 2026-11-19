package com.jjx.inventory.migration;

import com.jjx.inventory.master.MaterialMasterDTO;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 原始资料与本次导入计划分开保存；数量和日期不得由缺省值虚构。 */
public final class InventoryMigrationData {
    private InventoryMigrationData() { }
    public static final String SOURCE = "INV_MIGRATION";
    public static final String COST_SOURCE = "MIGRATION_COST";
    public enum LocationState {
        NORMAL("0"), DISABLED("1");
        public final String code;
        LocationState(String code) { this.code=code; }
    }
    public record Batch(String batchCode, String sourceSha256, LocalDate businessDate, String warehouseName,
                        List<Line> lines) { }
    public record Line(Integer sourceRow, String originalName, String originalSpecification,
                       BigDecimal originalArea, String originalUnit, String originalRemark,
                       String location, BigDecimal quantity, List<String> issues, MaterialMasterDTO material) { }
    public record Check(int rows, int materials, int zeroRows, List<String> errors, boolean alreadyImported) { }
    public record Imported(Long inboundId, String batchCode, int rows, boolean alreadyImported) { }
    public record BatchView(Long inboundId, String batchCode, LocalDate businessDate, String importedAt,
                            int rows, int missingCosts) { }
    public record RowView(Long lineId, Integer sourceRow, String materialCode, String materialName, String specification,
                          String unit, BigDecimal initialQuantity, BigDecimal receivedQuantity, BigDecimal issuedQuantity,
                          BigDecimal remainingQuantity, BigDecimal unitCost,
                          Long costVersion, String location, String issues) { }
    public record Cost(Long lineId, Long expectedVersion, BigDecimal unitCost, String reason) { }
    public record CostResult(int changedRows, int adjustmentRecords, BigDecimal amountDelta) { }
    public record CostAudit(Long recordId, String time, String operator, BigDecimal beforeCost,
                            BigDecimal afterCost, BigDecimal amountDelta, String reason, String referenceNo) { }
}
