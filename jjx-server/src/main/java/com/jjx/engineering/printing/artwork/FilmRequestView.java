package com.jjx.engineering.printing.artwork;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record FilmRequestView(long requestId,String requestNo,long versionId,int requestedQuantity,
        int receivedQuantity,int remainingQuantity,String progress,String material,BigDecimal thicknessMm,
        String size,LocalDate dueDate,String remark,int revision,String createBy,LocalDateTime createTime) {
    enum Progress {
        PENDING,PARTIAL,RECEIVED;
        static Progress from(int requested,int received) { return received==0 ? PENDING : received<requested ? PARTIAL : RECEIVED; }
    }
}
