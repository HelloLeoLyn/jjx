package com.jjx.engineering.printing.artwork;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record ArtworkView(long artworkId, String artworkNo, String name, String purpose, String family,
                          String setCode, Long sampleOrderId, Long defaultVersionId, int revision,
                          List<Long> productIds, List<Version> versions, List<Film> films, String productsText) {
    public record Version(long versionId, String versionNo, String status, Long drawingFileId,
                          String technicalSpec, String designerId, String approvedBy, LocalDateTime approvedTime,
                          String releasedBy, LocalDateTime releasedTime, int revision, String contentDigest, String contentAlgorithm, String drawingSha256, String drawingFileName, String drawingFileType) {}
    public record Film(long filmId, long versionId, String filmNo, String location, String status,
                       String material, BigDecimal thicknessMm, String size, LocalDateTime outputTime,
                       LocalDateTime destroyTime, int revision, Long filmRequestId) {}
}
