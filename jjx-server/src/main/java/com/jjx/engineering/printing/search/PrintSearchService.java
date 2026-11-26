package com.jjx.engineering.printing.search;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jjx.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** 印刷资源统一搜索服务（只读，dev-20261125-011）。 */
@Service
@RequiredArgsConstructor
public class PrintSearchService {

    private final PrintSearchRepository repository;

    public IPage<PrintSearchRow> search(PrintSearchOption option) {
        if (option == null || !StringUtils.hasText(option.getQ())) {
            return new Page<>(safe(option == null ? null : option.getPageNum(), 1),
                    safe(option == null ? null : option.getPageSize(), 10));
        }
        if ("PRODUCTION".equalsIgnoreCase(option.getScene())
                && option.getSpecVersionId() == null && option.getRoutingItemId() == null
                && option.getExecutionId() == null) {
            throw new BusinessException("生产场景搜索需携带所属上下文");
        }
        List<String> tokens = tokenize(option.getQ());
        if (tokens.isEmpty()) {
            return new Page<>(safe(option.getPageNum(), 1), safe(option.getPageSize(), 10));
        }
        Page<PrintSearchRow> page = new Page<>(safe(option.getPageNum(), 1), safe(option.getPageSize(), 10));
        IPage<PrintSearchRow> result = repository.search(page, tokens, option.getQ().trim(), option.getProductId());
        List<PrintSearchRow> rows = result.getRecords();
        for (PrintSearchRow row : rows) {
            annotate(row, tokens, option.getQ().trim());
        }
        return result;
    }

    private void annotate(PrintSearchRow row, List<String> tokens, String q) {
        String token = tokens.get(0);
        row.setMatchedType(typeOf(row, token));
        row.setMatchedCode(codeOf(row, token));
        row.setDisplayName(buildName(row));
        List<String> reasons = new ArrayList<>();
        if (row.getVersionId() != null
                && !"PLATED".equalsIgnoreCase(Objects.toString(row.getVersionStatus(), ""))) {
            reasons.add("VERSION_NOT_READY");
        }
        if (row.getVersionId() == null && row.getFrameId() != null) {
            String frameStatus = Objects.toString(row.getFrameStatus(), "").toUpperCase();
            if ("SCRAPPED".equals(frameStatus) || "DISABLED".equals(frameStatus)
                    || "MAINTENANCE".equals(frameStatus) || "REPAIR".equals(frameStatus)) {
                reasons.add("FRAME_DISABLED");
            }
        }
        if ("DESTROYED".equalsIgnoreCase(Objects.toString(row.getFilmStatus(), ""))) {
            reasons.add("FILM_DESTROYED");
        }
        if (row.getFrameNo() != null && !"ACTIVE".equalsIgnoreCase(Objects.toString(row.getFrameStatus(), ""))) {
            reasons.add("FRAME_DISABLED");
        }
        if (Boolean.FALSE.equals(row.getQualified())) {
            reasons.add("STRETCH_UNQUALIFIED");
        }
        row.setReasonCodes(reasons);
        row.setAvailability(reasons.isEmpty());
    }

    private String buildName(PrintSearchRow row) {
        if (row.getArtworkNo() == null && row.getVersionNo() == null && row.getFrameNo() != null) {
            String mesh = row.getMesh() == null ? "" : " / " + row.getMesh() + "目";
            return "网框 " + row.getFrameNo() + mesh;
        }
        String code = Objects.toString(row.getArtworkNo(), "");
        String name = Objects.toString(row.getName(), "");
        String version = Objects.toString(row.getVersionNo(), "");
        StringBuilder sb = new StringBuilder();
        if (!code.isEmpty()) sb.append(code);
        if (!name.isEmpty()) sb.append(sb.length() > 0 ? " " : "").append(name);
        if (!version.isEmpty()) sb.append(sb.length() > 0 ? " / " : "").append(version);
        return sb.toString();
    }

    private String typeOf(PrintSearchRow row, String token) {
        if (contains(row.getArtworkNo(), token)) return "ARTWORK";
        if (contains(row.getFilmNo(), token)) return "FILM";
        if (contains(row.getPlateNo(), token)) return "PLATE";
        if (contains(row.getFrameNo(), token)) return "FRAME";
        if (contains(row.getProductCode(), token) || contains(row.getProductName(), token)) return "PRODUCT";
        return "VERSION";
    }

    private String codeOf(PrintSearchRow row, String token) {
        if (contains(row.getArtworkNo(), token)) return row.getArtworkNo();
        if (contains(row.getFilmNo(), token)) return row.getFilmNo();
        if (contains(row.getPlateNo(), token)) return row.getPlateNo();
        if (contains(row.getFrameNo(), token)) return row.getFrameNo();
        if (contains(row.getProductCode(), token)) return row.getProductCode();
        return row.getVersionNo();
    }

    private static boolean contains(String value, String token) {
        return value != null && value.toLowerCase().contains(token.toLowerCase());
    }

    private static List<String> tokenize(String q) {
        return Arrays.stream(q.trim().split("\\s+"))
                .filter(StringUtils::hasText)
                .limit(8)
                .toList();
    }

    private static long safe(Integer value, long fallback) {
        return value == null || value < 1 ? fallback : value;
    }
}
