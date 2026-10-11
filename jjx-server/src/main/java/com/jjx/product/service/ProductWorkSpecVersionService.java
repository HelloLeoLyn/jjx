package com.jjx.product.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jjx.common.exception.BusinessException;
import com.jjx.product.domain.entity.Product;
import com.jjx.product.domain.entity.ProductWorkSpecItem;
import com.jjx.product.domain.entity.ProductWorkSpecVersion;
import com.jjx.product.enums.WorkSpecResourceType;
import com.jjx.product.mapper.ProductMapper;
import com.jjx.product.mapper.ProductWorkSpecItemMapper;
import com.jjx.product.mapper.ProductWorkSpecMapper;
import com.jjx.product.mapper.ProductWorkSpecVersionMapper;
import com.jjx.system.domain.entity.SysAttachment;
import com.jjx.system.mapper.SysAttachmentMapper;
import com.jjx.system.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 产品作业规范发布版本服务（dev-20261011-008）
 * 发布 = 把当前工作态资料固化为不可变版本：规范正文存快照，BOM/路线/图纸记引用（+哈希）。
 */
@Service
@RequiredArgsConstructor
public class ProductWorkSpecVersionService {

    private final ProductWorkSpecMapper workSpecMapper;
    private final ProductMapper productMapper;
    private final SysAttachmentMapper attachmentMapper;
    private final ProductWorkSpecVersionMapper versionMapper;
    private final ProductWorkSpecItemMapper itemMapper;
    private final ObjectMapper json;

    /** 发布新版本（产品内串行，避免并发产生重复版本号/混合快照）。 */
    @Transactional(rollbackFor = Exception.class)
    public ProductWorkSpecVersion publish(Long productId, String changeSummary) {
        if (workSpecMapper.lock(productId) == null) {
            throw new BusinessException("产品不存在: " + productId);
        }
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BusinessException("产品不存在: " + productId);
        }
        String raw = workSpecMapper.read(productId);
        if (raw == null || raw.isBlank()) {
            raw = "{}";
        }

        int seq = Math.toIntExact(versionMapper.selectCount(
                Wrappers.<ProductWorkSpecVersion>lambdaQuery()
                        .eq(ProductWorkSpecVersion::getProductId, productId))) + 1;
        String versionNo = "V" + seq + ".0";

        List<ProductWorkSpecItem> items = new ArrayList<>();
        // 规范正文：存完整快照
        items.add(newItem(WorkSpecResourceType.SPEC_JSON.getCode(), null, null, null, raw, sha256(raw), 0));
        // BOM：引用当前已批准版本（记来源 + 版本号）
        if (product.getCurrentBomId() != null) {
            ObjectNode ref = json.createObjectNode();
            ref.put("bomId", product.getCurrentBomId());
            if (product.getCurrentBomVersion() != null) ref.put("bomVersion", product.getCurrentBomVersion());
            items.add(newItem(WorkSpecResourceType.BOM.getCode(), product.getCurrentBomId(), null, null,
                    ref.toString(), null, 1));
        }
        // 工艺路线：引用当前已批准版本
        if (product.getCurrentRouteId() != null) {
            ObjectNode ref = json.createObjectNode();
            ref.put("routingId", product.getCurrentRouteId());
            if (product.getCurrentRoutingVersion() != null) ref.put("routingVersion", product.getCurrentRoutingVersion());
            items.add(newItem(WorkSpecResourceType.ROUTING.getCode(), product.getCurrentRouteId(), null, null,
                    ref.toString(), null, 2));
        }
        // 结构图/工程图：引用附件 + 文件哈希（受控附件不可删）
        Long structureFileId = structureFileId(raw);
        if (structureFileId != null) {
            SysAttachment att = attachmentMapper.selectById(structureFileId);
            ObjectNode ref = json.createObjectNode();
            ref.put("attachmentId", structureFileId);
            if (att != null) {
                if (att.getFileName() != null) ref.put("fileName", att.getFileName());
                if (att.getVersion() != null) ref.put("version", att.getVersion());
            }
            items.add(newItem(WorkSpecResourceType.DRAWING.getCode(), structureFileId, null, null,
                    ref.toString(), att != null ? att.getSha256() : null, 3));
        }

        String manifest = items.stream()
                .sorted(Comparator.comparing(ProductWorkSpecItem::getResourceType))
                .map(i -> i.getResourceType() + "|" + (i.getSourceId() == null ? "" : i.getSourceId())
                        + "|" + (i.getContentHash() == null ? "" : i.getContentHash()))
                .collect(Collectors.joining("\n"));

        ProductWorkSpecVersion version = new ProductWorkSpecVersion();
        version.setProductId(productId);
        version.setVersionNo(versionNo);
        version.setStatus("PUBLISHED");
        version.setChangeSummary(changeSummary);
        version.setManifestHash(sha256(manifest));
        version.setPublishedAt(LocalDateTime.now());
        version.setPublishedBy(displayName());
        version.setDeleted(0);
        versionMapper.insert(version);

        for (ProductWorkSpecItem item : items) {
            item.setSpecVersionId(version.getId());
            itemMapper.insert(item);
        }
        return version;
    }

    /** 某产品的发布版本列表（新→旧） */
    @Transactional(readOnly = true)
    public List<ProductWorkSpecVersion> list(Long productId) {
        return versionMapper.selectByProductId(productId);
    }

    /** 发布版本详情（版本 + 条目） */
    @Transactional(readOnly = true)
    public Map<String, Object> detail(Long versionId) {
        ProductWorkSpecVersion version = versionMapper.selectById(versionId);
        if (version == null) {
            throw new BusinessException("发布版本不存在: " + versionId);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("version", version);
        result.put("items", itemMapper.selectByVersionId(versionId));
        return result;
    }

    // ==================== 内部 ====================

    private ProductWorkSpecItem newItem(String type, Long sourceId, Long sourceVersionId, String operationRef,
                                        String snapshotJson, String contentHash, int sortOrder) {
        ProductWorkSpecItem item = new ProductWorkSpecItem();
        item.setResourceType(type);
        item.setSourceId(sourceId);
        item.setSourceVersionId(sourceVersionId);
        item.setOperationRef(operationRef);
        item.setSnapshotJson(snapshotJson);
        item.setContentHash(contentHash);
        item.setSortOrder(sortOrder);
        item.setCreateBy(safeUsername());
        item.setCreateTime(LocalDateTime.now());
        return item;
    }

    /** 从规范正文 JSON 取结结构图附件 ID（可选） */
    private Long structureFileId(String raw) {
        try {
            JsonNode node = json.readTree(raw);
            JsonNode fileId = node.get("structureFileId");
            return fileId != null && fileId.isNumber() ? fileId.asLong() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String sha256(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest((text == null ? "" : text).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return null;
        }
    }

    private String safeUsername() {
        try {
            return SecurityUtils.getUsername();
        } catch (Exception e) {
            return null;
        }
    }

    private String displayName() {
        try {
            String realName = SecurityUtils.getRealName();
            return (realName != null && !realName.isBlank()) ? realName : SecurityUtils.getUsername();
        } catch (Exception e) {
            return null;
        }
    }
}
