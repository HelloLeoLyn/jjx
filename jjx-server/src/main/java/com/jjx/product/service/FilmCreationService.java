package com.jjx.product.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jjx.engineering.printing.artwork.ArtworkCommands;
import com.jjx.engineering.printing.artwork.ArtworkService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

/** 首次录入的事务入口；归属、版本和附件规则仍由 ArtworkService 唯一实现。 */
@Service
@RequiredArgsConstructor
public class FilmCreationService {
    private final ArtworkService artworks;
    private final ObjectMapper json;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Map<String, Object> create(ArtworkCommands command) {
        // 原命令记录完整载荷，重试时图纸、技术要求等发生变化仍由原服务拒绝重放。
        var artwork = artworks.execute("createArtwork", 0, command);
        long artworkId = ((Number) artwork.get("artworkId")).longValue();
        ObjectNode version = json.valueToTree(command);
        // 两个动作各有稳定请求号；网络重试不会额外生成档案或版本。
        version.put("requestId", UUID.nameUUIDFromBytes(
                ("film-initial-version:" + command.requestId()).getBytes(StandardCharsets.UTF_8)).toString());
        version.put("expectedRevision", 0);
        version.put("versionNo", command.versionNo() == null || command.versionNo().isBlank()
                ? "V1" : command.versionNo());
        var initial = artworks.execute("createVersion", artworkId, json.convertValue(version, ArtworkCommands.class));
        return Map.of("artworkId", artworkId, "versionId", ((Number) initial.get("versionId")).longValue());
    }
}
