package com.jjx.purchase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjx.purchase.domain.dto.PurchaseOrderItemDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * dev-20261112-003：采购订单明细 DTO 必须能接收前端回传的只读列（供应商品名），
 * 且未来多送字段不得让整个保存请求 400。
 *
 * 用默认 ObjectMapper（未知字段默认报错）验证：前一个用例证明字段已补齐，
 * 后一个用例证明 @JsonIgnoreProperties(ignoreUnknown=true) 生效。
 */
class PurchaseOrderItemDtoDeserializationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void acceptsSupplierMaterialNameFromFrontendPayload() throws Exception {
        String json = "{"
                + "\"itemId\":11,\"orderId\":2,\"materialId\":109,\"materialCode\":\"RM000105\","
                + "\"materialName\":\"PC-800 茶色 单面加硬\",\"materialSpec\":\"450*225mm\",\"unit\":\"PCS\","
                + "\"quantity\":2,\"unitPrice\":3.5,\"supplierMaterialName\":\"PC-800 茶色 单面加硬(供方)\""
                + "}";
        PurchaseOrderItemDTO dto = mapper.readValue(json, PurchaseOrderItemDTO.class);
        assertNotNull(dto);
        assertEquals("PC-800 茶色 单面加硬(供方)", dto.getSupplierMaterialName());
        assertEquals(Long.valueOf(109L), dto.getMaterialId());
        assertEquals("PC-800 茶色 单面加硬", dto.getMaterialName());
    }

    @Test
    void ignoresUnknownReadonlyColumns() throws Exception {
        String json = "{\"materialName\":\"离型纸\",\"unit\":\"PCS\",\"someFutureReadonlyField\":\"x\"}";
        PurchaseOrderItemDTO dto = mapper.readValue(json, PurchaseOrderItemDTO.class);
        assertEquals("离型纸", dto.getMaterialName());
    }
}
