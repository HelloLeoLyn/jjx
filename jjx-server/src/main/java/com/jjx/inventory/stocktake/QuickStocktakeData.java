package com.jjx.inventory.stocktake;
import com.jjx.inventory.domain.InventoryStockItem;
import java.math.BigDecimal;
public final class QuickStocktakeData {
    private QuickStocktakeData() { }
    public record Save(String requestId,Long inventoryItemId,Long stockItemId,String fingerprint,BigDecimal actualQuantity,String reason) { }
    public record Preview(BigDecimal systemQuantity,BigDecimal actualQuantity,BigDecimal diffQuantity,String unit) { }
    public record Batch(InventoryStockItem stock,String unit,String warehouseName,String locationName,String fingerprint) { }
    public record Receipt(Long stocktakeId,String stocktakeNo,Integer orderStatus,Long inventoryItemId,Long stockItemId,
        BigDecimal systemQuantity,BigDecimal actualQuantity,BigDecimal diffQuantity,String unit,String reason) { }
}
