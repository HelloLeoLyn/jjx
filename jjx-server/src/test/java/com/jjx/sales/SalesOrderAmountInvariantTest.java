package com.jjx.sales;

import com.jjx.common.exception.BusinessException;
import com.jjx.sales.domain.converter.SalesOrderCalculator;
import com.jjx.sales.domain.converter.SalesOrderConverter;
import com.jjx.sales.domain.dto.*;
import com.jjx.sales.domain.entity.SalesOrder;
import com.jjx.sales.domain.vo.SalesOrderProductVO;
import com.jjx.sales.enums.ProdStatusEnum;
import com.jjx.sales.enums.SalesOrderStatusEnum;
import com.jjx.sales.enums.SalesPaymentStatusEnum;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SalesOrderAmountInvariantTest {
    private final SalesOrderConverter converter = Mappers.getMapper(SalesOrderConverter.class);
    private BigDecimal money(String value) { return new BigDecimal(value); }
    private SalesOrderProductDTO item() {
        SalesOrderProductDTO item = new SalesOrderProductDTO();
        item.setProductId(26L); item.setProductCode("JST003MEOO");
        item.setQuantity(2); item.setUnitPrice(money("325")); item.setAmount(money("650"));
        return item;
    }
    private SalesOrder existing() {
        SalesOrder order = new SalesOrder();
        order.setTotalAmount(money("770")); order.setTotalAmountWithTax(money("770"));
        order.setFinalAmount(money("770")); order.setTaxRate(money("13")); order.setTaxAmount(money("88.58"));
        order.setDiscountRate(BigDecimal.ZERO); order.setDiscountAmount(BigDecimal.ZERO);
        order.setTotalQuantity(2); order.setProducedQuantity(2); order.setShippedQuantity(1);
        order.setOrderStatus(SalesOrderStatusEnum.IN_PRODUCTION.getValue());
        order.setProdStatus(ProdStatusEnum.COMPLETED.getValue());
        order.setPaymentStatus(SalesPaymentStatusEnum.PARTIAL_PAID.getValue());
        order.setPaidAmount(money("100")); order.setUnpaidAmount(money("670"));
        return order;
    }
    private SalesOrderEditDTO edit() {
        SalesOrderEditDTO dto = new SalesOrderEditDTO();
        dto.setOrderId(2L); dto.setTotalAmount(money("770")); dto.setTaxRate(money("13"));
        dto.setDiscountRate(BigDecimal.ZERO); dto.setDiscountAmount(BigDecimal.ZERO); dto.setTotalQuantity(2);
        dto.setItems(List.of(item()));
        return dto;
    }
    private List<SalesOrderProductVO> oldItems() {
        SalesOrderProductVO item = new SalesOrderProductVO();
        item.setProductId(26L); item.setProductCode("JST003MEOO"); item.setQuantity(2);
        item.setUnitPrice(money("325.00")); item.setAmount(money("650.00"));
        return List.of(item);
    }
    @Test void confirmedBreakdownUsesItemsAndDeductsDiscountOnlyOnce() {
        SalesOrderEditDTO dto = edit(); dto.setShippingFee(money("35.50")); dto.setDiscountAmount(money("10"));
        SalesOrder patch = converter.toEntity(dto);
        SalesOrderCalculator.prepareEditAmounts(patch, existing(), dto, oldItems());
        assertEquals(money("84.50"), patch.getTaxAmount());
        assertEquals(money("770.00"), patch.getTotalAmount());
        assertEquals(money("760.00"), patch.getFinalAmount());
        assertEquals(money("660.00"), patch.getUnpaidAmount());
        assertNull(patch.getPaidAmount());
    }
    @Test void legacyNonMoneyEditDoesNotRewriteAmountsOrOperationalFacts() {
        SalesOrderEditDTO dto = edit(); dto.setDeliveryAddress("补齐收货地址");
        SalesOrder patch = converter.toEntity(dto);
        SalesOrderCalculator.prepareEditAmounts(patch, existing(), dto, oldItems());
        assertNull(patch.getTotalAmount()); assertNull(patch.getTaxAmount());
        assertNull(patch.getFinalAmount()); assertNull(patch.getPaidAmount()); assertNull(patch.getUnpaidAmount());
        assertNull(patch.getOrderStatus()); assertNull(patch.getProdStatus()); assertNull(patch.getPaymentStatus());
        assertNull(patch.getProducedQuantity()); assertNull(patch.getShippedQuantity());
        assertEquals("补齐收货地址", patch.getDeliveryAddress());
    }
    @Test void legacyPriceChangesRequireExplicitBreakdown() {
        SalesOrderEditDTO dto = edit(); dto.getItems().getFirst().setUnitPrice(money("400"));
        assertThrows(BusinessException.class, () -> SalesOrderCalculator.prepareEditAmounts(
                converter.toEntity(dto), existing(), dto, oldItems()));
    }
    @Test void knownBreakdownCannotBeOverwrittenByClientTotalsOrLineAmounts() {
        SalesOrderEditDTO dto = edit(); dto.setShippingFee(BigDecimal.ZERO);
        dto.setTotalAmount(money("1")); dto.getItems().getFirst().setAmount(money("1"));
        SalesOrder patch = converter.toEntity(dto);
        SalesOrderCalculator.prepareEditAmounts(patch, existing(), dto, oldItems());
        assertEquals(money("734.50"), patch.getFinalAmount());
        assertEquals(money("650.00"), dto.getItems().getFirst().getAmount());
    }
    @Test void quotationTaxAndDiscountCarryIntoNewOrderWithoutDoubleTax() {
        SalesOrderAddDTO dto = new SalesOrderAddDTO();
        dto.setTotalAmount(money("650")); dto.setTaxRate(money("7"));
        dto.setTaxAmount(money("45.50")); dto.setDiscountAmount(money("5.50"));
        SalesOrder order = converter.toEntity(dto); order.setShippingFee(BigDecimal.ZERO);
        SalesOrderCalculator.fillFromItems(order, List.of(item()));
        assertEquals(money("695.50"), order.getTotalAmount());
        assertEquals(money("690.00"), order.getFinalAmount());
        assertEquals(money("45.50"), order.getTaxAmount());
    }

    @Test void excessiveDiscountIsRejected() {
        SalesOrderEditDTO dto = edit(); dto.setShippingFee(BigDecimal.ZERO); dto.setDiscountAmount(money("800"));
        assertThrows(BusinessException.class, () -> SalesOrderCalculator.prepareEditAmounts(
                converter.toEntity(dto), existing(), dto, oldItems()));
    }
    @Test void productionStatusTracksCompletedPartialAndReversedQuantities() {
        assertEquals(ProdStatusEnum.COMPLETED, ProdStatusEnum.fromProducedQuantity(2, 2));
        assertEquals(ProdStatusEnum.PARTIAL_PRODUCING, ProdStatusEnum.fromProducedQuantity(1, 2));
        assertEquals(ProdStatusEnum.NONE, ProdStatusEnum.fromProducedQuantity(0, 2));
    }
}
