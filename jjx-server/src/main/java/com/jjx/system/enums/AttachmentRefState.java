package com.jjx.system.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 引用是否已落定到正式单据；区别于文件上传生命周期。 */
@Getter
@RequiredArgsConstructor
public enum AttachmentRefState {
    DRAFT("draft"),
    ACTIVE("active");

    private final String value;
}
