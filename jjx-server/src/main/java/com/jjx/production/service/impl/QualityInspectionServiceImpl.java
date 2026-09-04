package com.jjx.production.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jjx.common.core.page.PageResult;
import com.jjx.common.exception.BusinessException;
import com.jjx.production.domain.dto.*;
import com.jjx.production.domain.entity.ProductionQualityInspection;
import com.jjx.production.domain.entity.ProductionQualityInspectionItem;
import com.jjx.production.domain.vo.QualityInspectionVO;
import com.jjx.production.domain.vo.InspectionItemVO;
import com.jjx.production.enums.QualityInspectionResultEnum;
import com.jjx.production.enums.QualityInspectionTypeEnum;
import com.jjx.production.mapper.ProductionQualityInspectionMapper;
import com.jjx.production.mapper.ProductionQualityInspectionItemMapper;
import com.jjx.production.service.QualityInspectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class QualityInspectionServiceImpl implements QualityInspectionService {

    private final ProductionQualityInspectionMapper inspectionMapper;
    private final ProductionQualityInspectionItemMapper itemMapper;
    private final com.jjx.common.utils.pdf.PdfConfigLoader pdfConfigLoader;
    private final com.jjx.production.mapper.ProductionOrderMapper productionOrderMapper;
    /** P3-B：关联一致性校验用 */
    private final com.jjx.production.mapper.ProductionWorkReportMapper workReportMapper;
    /** P3-D：展示字段（工序名）用 */
    private final com.jjx.production.mapper.ProductionOperationExecutionMapper executionMapper;

    @Override
    public PageResult<QualityInspectionVO> page(QualityInspectionQueryDTO query) {
        LambdaQueryWrapper<ProductionQualityInspection> wrapper = Wrappers.lambdaQuery();
        if (StringUtils.isNotBlank(query.getInspectionNo()))
            wrapper.like(ProductionQualityInspection::getInspectionNo, query.getInspectionNo());
        if (StringUtils.isNotBlank(query.getInspectionType()))
            wrapper.eq(ProductionQualityInspection::getInspectionType, query.getInspectionType());
        if (query.getOrderId() != null)
            wrapper.eq(ProductionQualityInspection::getOrderId, query.getOrderId());
        // P3-B：按工序/报工过滤
        if (query.getExecutionId() != null)
            wrapper.eq(ProductionQualityInspection::getExecutionId, query.getExecutionId());
        if (query.getWorkReportId() != null)
            wrapper.eq(ProductionQualityInspection::getWorkReportId, query.getWorkReportId());
        if (StringUtils.isNotBlank(query.getResult()))
            wrapper.eq(ProductionQualityInspection::getResult, query.getResult());
        wrapper.orderByDesc(ProductionQualityInspection::getCreateTime);

        Page<ProductionQualityInspection> page = new Page<>(query.getPageNum(), query.getPageSize());
        Page<ProductionQualityInspection> result = inspectionMapper.selectPage(page, wrapper);
        List<QualityInspectionVO> voList = result.getRecords().stream().map(this::toVO).collect(Collectors.toList());
        return PageResult.build(voList, result.getTotal());
    }

    @Override
    public QualityInspectionVO getById(Long id) {
        ProductionQualityInspection entity = inspectionMapper.selectById(id);
        if (entity == null) throw new BusinessException("检验单不存在");
        QualityInspectionVO vo = toVO(entity);
        // 加载检验项
        LambdaQueryWrapper<ProductionQualityInspectionItem> iw = Wrappers.lambdaQuery();
        iw.eq(ProductionQualityInspectionItem::getInspectionId, id);
        List<InspectionItemVO> items = itemMapper.selectList(iw).stream().map(this::toItemVO).collect(Collectors.toList());
        vo.setItems(items);
        return vo;
    }

    @Override
    @Transactional
    public Long create(QualityInspectionCreateDTO dto) {
        // P3-C：workReportId 非空 → 后端反查 WorkReport 校验关联一致性（不信任客户端组合 ID）
        if (dto.getWorkReportId() != null) {
            boolean linkOk = checkWorkReportLink(dto.getWorkReportId(), dto.getExecutionId(), dto.getOrderId());
            if (!linkOk) {
                throw new BusinessException("报工与工序/订单关联不一致，无法创建质检");
            }
        }
        ProductionQualityInspection entity = new ProductionQualityInspection();
        entity.setInspectionNo(generateInspectionNo());
        entity.setInspectionType(dto.getInspectionType());
        entity.setOrderId(dto.getOrderId());
        // P3-B：写入工序/报工关联（可空）
        entity.setExecutionId(dto.getExecutionId());
        entity.setWorkReportId(dto.getWorkReportId());
        entity.setMaterialId(dto.getMaterialId());
        entity.setProductId(dto.getProductId());
        entity.setInspector(dto.getInspector());
        entity.setResult(QualityInspectionResultEnum.PENDING.getCode());
        entity.setRemark(dto.getRemark());
        inspectionMapper.insert(entity);

        if (dto.getItems() != null) {
            for (InspectionItemDTO item : dto.getItems()) {
                ProductionQualityInspectionItem ei = new ProductionQualityInspectionItem();
                ei.setInspectionId(entity.getInspectionId());
                ei.setCheckItem(item.getCheckItem());
                ei.setStandard(item.getStandard());
                ei.setActualValue(item.getActualValue());
                ei.setResult(QualityInspectionResultEnum.PENDING.getCode());
                itemMapper.insert(ei);
            }
        }
        return entity.getInspectionId();
    }

    /**
     * P3-C：唯一检验单号生成（毫秒时间戳 + 3 位随机，避免秒级并发冲突）。
     */
    private String generateInspectionNo() {
        return "QCI" + LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"))
                + String.format("%03d", java.util.concurrent.ThreadLocalRandom.current().nextInt(1000));
    }

    /**
     * LEGACY（P3-B 标记）：通用覆盖式更新质检结果/数量。
     * P3-C 收口：已判定(pass/fail)不可修改质量事实；PENDING 只允许修改非判定字段。
     */
    @Override
    @Transactional
    public void update(QualityInspectionUpdateDTO dto) {
        ProductionQualityInspection entity = inspectionMapper.selectById(dto.getInspectionId());
        if (entity == null) throw new BusinessException("检验单不存在");

        // P3-C 不可变守卫：已判定(PASS/FAIL) 禁止修改 result/数量/executionId/workReportId/orderId
        if (QualityInspectionResultEnum.PASS.getCode().equals(entity.getResult())
                || QualityInspectionResultEnum.FAIL.getCode().equals(entity.getResult())) {
            throw new BusinessException("质检结果已确定，不可修改；复检请新建质检单");
        }
        // PENDING：只允许修改数量/缺陷/备注等非判定字段；result 不接受直接判定（判定走 QualityActionService.judge）
        entity.setTotalQty(dto.getTotalQty());
        entity.setPassQty(dto.getPassQty());
        entity.setFailQty(dto.getFailQty());
        entity.setDefectDesc(dto.getDefectDesc());
        inspectionMapper.updateById(entity);

        // 更新检验项
        if (dto.getItems() != null) {
            itemMapper.delete(Wrappers.lambdaQuery(ProductionQualityInspectionItem.class)
                .eq(ProductionQualityInspectionItem::getInspectionId, dto.getInspectionId()));
            for (InspectionItemDTO item : dto.getItems()) {
                ProductionQualityInspectionItem ei = new ProductionQualityInspectionItem();
                ei.setInspectionId(dto.getInspectionId());
                ei.setCheckItem(item.getCheckItem());
                ei.setStandard(item.getStandard());
                ei.setActualValue(item.getActualValue());
                ei.setResult(item.getResult());
                itemMapper.insert(ei);
            }
        }
    }

    @Override
    public void delete(Long id) {
        inspectionMapper.deleteById(id);
        itemMapper.delete(Wrappers.lambdaQuery(ProductionQualityInspectionItem.class)
            .eq(ProductionQualityInspectionItem::getInspectionId, id));
    }

    // ============ P3-B 读取能力（供 P3-C FQC/IPQC 联动） ============

    @Override
    public List<QualityInspectionVO> listByOrderId(Long orderId) {
        if (orderId == null) return java.util.Collections.emptyList();
        return inspectionMapper.selectList(Wrappers.<ProductionQualityInspection>lambdaQuery()
                        .eq(ProductionQualityInspection::getOrderId, orderId)
                        .orderByDesc(ProductionQualityInspection::getCreateTime))
                .stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public List<QualityInspectionVO> listByExecutionId(Long executionId) {
        if (executionId == null) return java.util.Collections.emptyList();
        return inspectionMapper.selectList(Wrappers.<ProductionQualityInspection>lambdaQuery()
                        .eq(ProductionQualityInspection::getExecutionId, executionId)
                        .orderByDesc(ProductionQualityInspection::getCreateTime))
                .stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public List<QualityInspectionVO> listByWorkReportId(Long workReportId) {
        if (workReportId == null) return java.util.Collections.emptyList();
        return inspectionMapper.selectList(Wrappers.<ProductionQualityInspection>lambdaQuery()
                        .eq(ProductionQualityInspection::getWorkReportId, workReportId)
                        .orderByDesc(ProductionQualityInspection::getCreateTime))
                .stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public List<QualityInspectionVO> listFqcHistory(Long executionId) {
        if (executionId == null) return java.util.Collections.emptyList();
        return inspectionMapper.selectList(Wrappers.<ProductionQualityInspection>lambdaQuery()
                        .eq(ProductionQualityInspection::getInspectionType, QualityInspectionTypeEnum.FQC.getCode())
                        .eq(ProductionQualityInspection::getExecutionId, executionId)
                        .orderByDesc(ProductionQualityInspection::getCreateTime))
                .stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public boolean hasPendingFqc(Long executionId) {
        if (executionId == null) return false;
        Long cnt = inspectionMapper.selectCount(Wrappers.<ProductionQualityInspection>lambdaQuery()
                .eq(ProductionQualityInspection::getInspectionType, QualityInspectionTypeEnum.FQC.getCode())
                .eq(ProductionQualityInspection::getExecutionId, executionId)
                .eq(ProductionQualityInspection::getResult, QualityInspectionResultEnum.PENDING.getCode()));
        return cnt != null && cnt > 0;
    }

    @Override
    public boolean hasPassFqc(Long executionId) {
        if (executionId == null) return false;
        Long cnt = inspectionMapper.selectCount(Wrappers.<ProductionQualityInspection>lambdaQuery()
                .eq(ProductionQualityInspection::getInspectionType, QualityInspectionTypeEnum.FQC.getCode())
                .eq(ProductionQualityInspection::getExecutionId, executionId)
                .eq(ProductionQualityInspection::getResult, QualityInspectionResultEnum.PASS.getCode()));
        return cnt != null && cnt > 0;
    }

    /**
     * P3-B 关联一致性校验：workReportId 非空时校验
     *   workReport.executionId == 传入 executionId
     *   workReport.orderId == 传入 orderId
     * 任一为空/查不到/不一致 → false（由调用方决定是否阻断）。
     */
    @Override
    public boolean checkWorkReportLink(Long workReportId, Long executionId, Long orderId) {
        if (workReportId == null || executionId == null || orderId == null) return false;
        com.jjx.production.domain.entity.ProductionWorkReport wr = workReportMapper.selectById(workReportId);
        if (wr == null) return false;
        return executionId.equals(wr.getExecutionId()) && orderId.equals(wr.getOrderId());
    }

    private QualityInspectionVO toVO(ProductionQualityInspection e) {
        QualityInspectionVO vo = new QualityInspectionVO();
        vo.setInspectionId(e.getInspectionId());
        vo.setInspectionNo(e.getInspectionNo());
        vo.setInspectionType(e.getInspectionType());
        vo.setInspectionTypeName(getTypeName(e.getInspectionType()));
        vo.setOrderId(e.getOrderId());
        // P3-B：工序/报工关联映射
        vo.setExecutionId(e.getExecutionId());
        vo.setWorkReportId(e.getWorkReportId());
        // P3-D：展示字段填充（orderNo/productName/processName）
        fillDisplayFields(vo);
        vo.setInspector(e.getInspector());
        vo.setInspectTime(e.getInspectTime());
        vo.setResult(e.getResult());
        vo.setResultName(getResultName(e.getResult()));
        vo.setTotalQty(e.getTotalQty());
        vo.setPassQty(e.getPassQty());
        vo.setFailQty(e.getFailQty());
        vo.setDefectDesc(e.getDefectDesc());
        vo.setRemark(e.getRemark());
        vo.setCreateTime(e.getCreateTime());
        return vo;
    }

    /**
     * P3-D：填充展示字段（orderNo/productName/processName）
     * 仅用于列表/详情展示，不影响质量事实；查不到时保持 null（前端显示 -）。
     */
    private void fillDisplayFields(QualityInspectionVO vo) {
        if (vo.getOrderId() != null) {
            try {
                com.jjx.production.domain.entity.ProductionOrder o = productionOrderMapper.selectById(vo.getOrderId());
                if (o != null) {
                    vo.setOrderNo(o.getOrderNo());
                    if (vo.getProductName() == null && o.getProductName() != null) {
                        vo.setProductName(o.getProductName());
                    }
                }
            } catch (Exception ex) {
                log.debug("填充订单展示字段失败 orderId={}: {}", vo.getOrderId(), ex.getMessage());
            }
        }
        if (vo.getExecutionId() != null) {
            try {
                com.jjx.production.domain.entity.ProductionOperationExecution ex =
                        executionMapper.selectById(vo.getExecutionId());
                if (ex != null && ex.getProcessName() != null) {
                    vo.setProcessName(ex.getProcessName());
                }
            } catch (Exception ex) {
                log.debug("填充工序展示字段失败 executionId={}: {}", vo.getExecutionId(), ex.getMessage());
            }
        }
    }

    private InspectionItemVO toItemVO(ProductionQualityInspectionItem e) {
        InspectionItemVO vo = new InspectionItemVO();
        vo.setItemId(e.getItemId());
        vo.setCheckItem(e.getCheckItem());
        vo.setStandard(e.getStandard());
        vo.setActualValue(e.getActualValue());
        vo.setResult(e.getResult());
        vo.setRemark(e.getRemark());
        return vo;
    }

    private String getTypeName(String t) {
        // P0-01：质检类型统一走枚举；未知历史值原样返回（兼容旧数据）
        return QualityInspectionTypeEnum.labelOf(t);
    }

    private String getResultName(String r) {
        // P0-01：质检结果统一走枚举；未知历史值原样返回（兼容旧数据）
        return QualityInspectionResultEnum.labelOf(r);
    }

    @Override
    public Object getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        List<ProductionQualityInspection> all = inspectionMapper.selectList(Wrappers.emptyWrapper());
        stats.put("totalCount", (long) all.size());
        long passCount = all.stream().filter(q -> QualityInspectionResultEnum.PASS.getCode().equals(q.getResult())).count();
        long failCount = all.stream().filter(q -> QualityInspectionResultEnum.FAIL.getCode().equals(q.getResult())).count();
        long pendingCount = all.stream().filter(q -> q.getResult() == null || QualityInspectionResultEnum.PENDING.getCode().equals(q.getResult())).count();
        stats.put("passCount", passCount);
        stats.put("failCount", failCount);
        stats.put("pendingCount", pendingCount);
        java.math.BigDecimal totalQty = all.stream().filter(q -> q.getTotalQty() != null)
                .map(ProductionQualityInspection::getTotalQty)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        java.math.BigDecimal passQty = all.stream().filter(q -> q.getPassQty() != null)
                .map(ProductionQualityInspection::getPassQty)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        stats.put("totalQty", totalQty);
        stats.put("passQty", passQty);
        stats.put("passRate", totalQty.compareTo(java.math.BigDecimal.ZERO) > 0
                ? Math.round(passQty.doubleValue() / totalQty.doubleValue() * 1000.0) / 10.0 : 100.0);
        return stats;
    }


    @Override
    public byte[] exportExcel(Long id) {
        QualityInspectionVO vo = getById(id);
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm");
        try (org.apache.poi.xssf.usermodel.XSSFWorkbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
             java.io.ByteArrayOutputStream os = new java.io.ByteArrayOutputStream()) {
            org.apache.poi.ss.usermodel.Sheet sheet = wb.createSheet("质检报告");
            int r = 0;
            org.apache.poi.ss.usermodel.Row title = sheet.createRow(r++);
            title.createCell(0).setCellValue("质检报告 " + (vo.getInspectionNo() == null ? "" : vo.getInspectionNo()));
            String[][] kv = {
                    {"检验类型", vo.getInspectionTypeName() == null ? (vo.getInspectionType() == null ? "" : vo.getInspectionType()) : vo.getInspectionTypeName()},
                    {"关联订单", vo.getOrderNo() == null ? "" : vo.getOrderNo()},
                    {"产品", vo.getProductName() == null ? "" : vo.getProductName()},
                    {"物料", vo.getMaterialName() == null ? "" : vo.getMaterialName()},
                    {"检验员", vo.getInspector() == null ? "" : vo.getInspector()},
                    {"检验时间", vo.getInspectTime() == null ? "" : vo.getInspectTime().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))},
                    {"检验结果", vo.getResultName() == null ? (vo.getResult() == null ? "" : vo.getResult()) : vo.getResultName()},
                    {"总数", vo.getTotalQty() == null ? "" : String.valueOf(vo.getTotalQty())},
                    {"合格数", vo.getPassQty() == null ? "" : String.valueOf(vo.getPassQty())},
                    {"不合格数", vo.getFailQty() == null ? "" : String.valueOf(vo.getFailQty())},
            };
            for (String[] pair : kv) {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(pair[0]);
                row.createCell(1).setCellValue(pair[1]);
            }
            r++;
            String[] headers = {"序号", "检验项目", "标准要求", "实测值", "结果", "备注"};
            org.apache.poi.ss.usermodel.Row hrow = sheet.createRow(r++);
            for (int i = 0; i < headers.length; i++) hrow.createCell(i).setCellValue(headers[i]);
            if (vo.getItems() != null) {
                int idx = 1;
                for (com.jjx.production.domain.vo.InspectionItemVO item : vo.getItems()) {
                    org.apache.poi.ss.usermodel.Row row = sheet.createRow(r++);
                    row.createCell(0).setCellValue(idx++);
                    row.createCell(1).setCellValue(item.getCheckItem() == null ? "" : item.getCheckItem());
                    row.createCell(2).setCellValue(item.getStandard() == null ? "" : item.getStandard());
                    row.createCell(3).setCellValue(item.getActualValue() == null ? "" : item.getActualValue());
                    row.createCell(4).setCellValue(item.getResult() == null ? "" : item.getResult());
                    row.createCell(5).setCellValue(item.getRemark() == null ? "" : item.getRemark());
                }
            }
            if (vo.getDefectDesc() != null) {
                r++;
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(r);
                row.createCell(0).setCellValue("缺陷说明");
                row.createCell(1).setCellValue(vo.getDefectDesc());
            }
            for (int i = 0; i < 6; i++) sheet.autoSizeColumn(i);
            wb.write(os);
            return os.toByteArray();
        } catch (Exception e) {
            throw new BusinessException("导出质检报告Excel失败: " + e.getMessage());
        }
    }
}
