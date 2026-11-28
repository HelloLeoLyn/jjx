package com.jjx.engineering.printing.artwork;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.jjx.common.exception.BusinessException;
import java.math.BigDecimal;
import java.util.List;

public record ArtworkCommands(String requestId, Integer expectedRevision, String artworkNo, String name,
        String purpose, String family, String setCode, List<Long> productIds, Long sampleOrderId,
        String versionNo, Long drawingFileId, String technicalSpec, String reason, Boolean disablePrevious,
        String impactToken, String filmNo, String location, String material, BigDecimal thicknessMm, String size, Integer quantity, java.time.LocalDate dueDate, String drawingDocumentType, String stylesJson) {
    public ArtworkCommands(String requestId, Integer expectedRevision, String artworkNo, String name,
        String purpose, String family, String setCode, List<Long> productIds, Long sampleOrderId,
        String versionNo, Long drawingFileId, String technicalSpec, String reason, Boolean disablePrevious,
        String impactToken, String filmNo, String location, String material, BigDecimal thicknessMm, String size, Integer quantity, java.time.LocalDate dueDate, String drawingDocumentType) {
        this(requestId,expectedRevision,artworkNo,name,purpose,family,setCode,productIds,sampleOrderId,versionNo,drawingFileId,technicalSpec,reason,disablePrevious,impactToken,filmNo,location,material,thicknessMm,size,quantity,dueDate,drawingDocumentType,null);
    }
    @JsonAnySetter
    public void unknown(String field,Object value) {
        throw new IllegalArgumentException("未知字段 " + field);
    }

    static void required(String value,int max,String label) {
        check(value!=null && !value.isBlank() && value.length()<=max,label+"必填或超长");
    }

    static void check(boolean valid,String message) {
        if (!valid) throw new BusinessException(422,message);
    }

    void validate() {
        required(requestId,64,"requestId");
        check(expectedRevision!=null && expectedRevision>=0,"需要非负 expectedRevision");
        check(reason==null || reason.length()<=500,"原因超长");
        check(drawingDocumentType==null || drawingDocumentType.length()<=100,"图纸资料类型超长");
    }
}
