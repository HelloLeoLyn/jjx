package com.jjx.trace.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TraceAttachmentVO {
    private Long id;
    private String fileName;
}
