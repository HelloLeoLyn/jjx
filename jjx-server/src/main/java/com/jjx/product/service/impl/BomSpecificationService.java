package com.jjx.product.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jjx.engineering.domain.entity.EngineeringBomItem;
import com.jjx.inventory.domain.InventoryMaterial;
import com.jjx.inventory.mapper.InventoryMaterialMapper;
import com.jjx.inventory.master.MaterialMasterService;
import com.jjx.system.annotation.BusinessType;
import com.jjx.system.domain.entity.SysOperLog;
import com.jjx.system.mapper.SysOperLogMapper;
import com.jjx.system.utils.SecurityUtils;
import com.jjx.common.enums.YesNoEnum;
import com.jjx.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/** BOM 规格的属性生成与保存时对照值；对照值复用操作日志，不增加业务表列。 */
@Service
@RequiredArgsConstructor
public class BomSpecificationService {
    private static final String SNAPSHOT_URL = "bom.specification.snapshot";
    private final MaterialMasterService materialMasterService;
    private final InventoryMaterialMapper materialMapper;
    private final SysOperLogMapper logMapper;

    public record Snapshot(Long itemId, Long materialId, String specification, String automaticSpecification) { }

    private String automatic(InventoryMaterial material) {
        return material == null ? "" : text(materialMasterService.specificationPreview(material).specification());
    }

    public void enrich(Long bomId, List<EngineeringBomItem> items) {
        List<EngineeringBomItem> linked = items.stream().filter(BomSpecificationService::linked).toList();
        if (linked.isEmpty()) return;
        SysOperLog log = logMapper.selectOne(new LambdaQueryWrapper<SysOperLog>()
                .eq(SysOperLog::getBizType, "bom").eq(SysOperLog::getBizId, bomId.toString())
                .eq(SysOperLog::getOperUrl, SNAPSHOT_URL).orderByDesc(SysOperLog::getId).last("LIMIT 1"));
        Map<Long, Snapshot> snapshots = new HashMap<>();
        if (log != null && log.getDetail() != null) {
            var saved = JSONUtil.parseObj(log.getDetail()).getJSONArray("specifications");
            for (int index = 0; index < saved.size(); index++) {
                var value = saved.getJSONObject(index);
                Snapshot snapshot = new Snapshot(value.getLong("itemId"), value.getLong("materialId"),
                        value.getStr("specification"), value.getStr("automaticSpecification"));
                snapshots.put(snapshot.itemId(), snapshot);
            }
        }
        Map<Long, String> automatic = new HashMap<>();
        for (EngineeringBomItem item : linked) {
            String current = automatic.computeIfAbsent(item.getMaterialId(), id -> automatic(materialMapper.selectById(id)));
            item.setMaterialSpecification(current);
            Snapshot snapshot = snapshots.get(item.getItemId());
            // 外部改写或重新关联后不能套用旧对照值；历史无记录时仅比较当前值，不覆盖原文。
            boolean valid = snapshot != null && Objects.equals(snapshot.materialId(), item.getMaterialId())
                    && text(snapshot.specification()).equals(text(item.getSpecification()));
            item.setSpecificationBaseline(valid ? text(snapshot.automaticSpecification()) : current);
            item.setSpecificationManual(valid && !text(snapshot.specification()).equals(text(snapshot.automaticSpecification())));
        }
    }

    /** 与 BOM 保存处于同一事务；保存失败不留下对照日志，临时行号只记录实际落库后的 ID。 */
    public void record(Long bomId, List<EngineeringBomItem> items) {
        Map<Long, String> automatic = new HashMap<>();
        List<Snapshot> snapshots = items.stream().filter(BomSpecificationService::linked).map(item ->
                new Snapshot(item.getItemId(), item.getMaterialId(), text(item.getSpecification()),
                        automatic.computeIfAbsent(item.getMaterialId(), id -> automatic(materialMapper.selectById(id))))).toList();
        if (snapshots.isEmpty()) return;
        SysOperLog log = new SysOperLog();
        log.setBizType("bom");
        log.setBizId(bomId.toString());
        log.setModule("BOM管理");
        log.setBusinessType(BusinessType.UPDATE.getCode());
        log.setOperUrl(SNAPSHOT_URL);
        log.setAction("保存BOM规格对照");
        log.setDetail(JSONUtil.toJsonStr(Map.of("specifications", snapshots.stream().map(value -> Map.of(
                "itemId", value.itemId(), "materialId", value.materialId(), "specification", value.specification(),
                "automaticSpecification", value.automaticSpecification())).toList())));
        log.setStatus(YesNoEnum.YES.getCode());
        log.setUsername(SecurityUtils.getUsername());
        log.setRealName(SecurityUtils.getDisplayName());
        log.setUserId(SecurityUtils.getUserId());
        log.setCreateTime(LocalDateTime.now());
        if (logMapper.insert(log) != 1) throw new BusinessException("BOM规格对照保存失败");
    }

    private static boolean linked(EngineeringBomItem item) {
        return item.getMaterialId() != null && item.getMaterialId() > 0;
    }

    private static String text(String value) { return value == null ? "" : value.trim(); }
}
