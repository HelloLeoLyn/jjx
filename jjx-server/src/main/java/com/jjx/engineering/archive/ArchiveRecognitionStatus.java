package com.jjx.engineering.archive;

import lombok.Getter;

@Getter
public enum ArchiveRecognitionStatus {
    PENDING(0), RECOGNIZING(1), REVIEW(2), GENERATED(3), FAILED(4);
    private final int value;
    ArchiveRecognitionStatus(int value) { this.value = value; }
}
