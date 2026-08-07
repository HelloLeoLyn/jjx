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

    @Override
    public PageResult<QualityInspectionVO> page(QualityInspectionQueryDTO query) {
        LambdaQueryWrapper<ProductionQualityInspection> wrapper = Wrappers.lambdaQuery();
        if (StringUtils.isNotBlank(query.getInspectionNo()))
            wrapper.like(ProductionQualityInspection::getInspectionNo, query.getInspectionNo());
        if (StringUtils.isNotBlank(query.getInspectionType()))
            wrapper.eq(ProductionQualityInspection::getInspectionType, query.getInspectionType());
        if (query.getOrderId() != null)
            wrapper.eq(ProductionQualityInspection::getOrderId, query.getOrderId());
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
        ProductionQualityInspection entity = new ProductionQualityInspection();
        entity.setInspectionNo("QCI" + LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
        entity.setInspectionType(dto.getInspectionType());
        entity.setOrderId(dto.getOrderId());
        entity.setMaterialId(dto.getMaterialId());
        entity.setProductId(dto.getProductId());
        entity.setInspector(dto.getInspector());
        entity.setResult("pending");
        entity.setRemark(dto.getRemark());
        inspectionMapper.insert(entity);

        if (dto.getItems() != null) {
            for (InspectionItemDTO item : dto.getItems()) {
                ProductionQualityInspectionItem ei = new ProductionQualityInspectionItem();
                ei.setInspectionId(entity.getInspectionId());
                ei.setCheckItem(item.getCheckItem());
                ei.setStandard(item.getStandard());
                ei.setActualValue(item.getActualValue());
                ei.setResult("pending");
                itemMapper.insert(ei);
            }
        }
        return entity.getInspectionId();
    }

    @Override
    @Transactional
    public void update(QualityInspectionUpdateDTO dto) {
        ProductionQualityInspection entity = inspectionMapper.selectById(dto.getInspectionId());
        if (entity == null) throw new BusinessException("检验单不存在");
        entity.setResult(dto.getResult());
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

    private QualityInspectionVO toVO(ProductionQualityInspection e) {
        QualityInspectionVO vo = new QualityInspectionVO();
        vo.setInspectionId(e.getInspectionId());
        vo.setInspectionNo(e.getInspectionNo());
        vo.setInspectionType(e.getInspectionType());
        vo.setInspectionTypeName(getTypeName(e.getInspectionType()));
        vo.setOrderId(e.getOrderId());
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
        if ("IQC".equals(t)) return "来料检验";
        if ("IPQC".equals(t)) return "过程检验";
        if ("OQC".equals(t)) return "成品检验";
        return t;
    }

    private String getResultName(String r) {
        if ("pass".equals(r)) return "合格";
        if ("fail".equals(r)) return "不合格";
        if ("pending".equals(r)) return "待检";
        return r;
    }

    @Override
    public Object getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        List<ProductionQualityInspection> all = inspectionMapper.selectList(Wrappers.emptyWrapper());
        stats.put("totalCount", (long) all.size());
        long passCount = all.stream().filter(q -> "pass".equals(q.getResult())).count();
        long failCount = all.stream().filter(q -> "fail".equals(q.getResult())).count();
        long pendingCount = all.stream().filter(q -> q.getResult() == null || "pending".equals(q.getResult())).count();
        stats.put("passCount", passCount);
        stats.put("failCount", failCount);
        stats.put("pendingCount", pendingCount);
        double totalQty = all.stream().filter(q -> q.getTotalQty() != null).mapToInt(q -> q.getTotalQty()).sum();
        double passQty = all.stream().filter(q -> q.getPassQty() != null).mapToInt(q -> q.getPassQty()).sum();
        stats.put("totalQty", totalQty);
        stats.put("passQty", passQty);
        stats.put("passRate", totalQty > 0 ? Math.round(passQty / totalQty * 1000.0) / 10.0 : 100.0);
        return stats;
    }

    @Override
    public byte[] exportPdf(Long id) {
        QualityInspectionVO vo = getById(id);
        java.text.DecimalFormat df = new java.text.DecimalFormat("#,##0");
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm");

        java.util.Map<String, String> info = new java.util.LinkedHashMap<>();
        info.put("检验单号", vo.getInspectionNo());
        info.put("检验类型", vo.getInspectionTypeName() == null ? (vo.getInspectionType() == null ? "-" : vo.getInspectionType()) : vo.getInspectionTypeName());
        info.put("关联订单", vo.getOrderNo() == null ? "-" : vo.getOrderNo());
        info.put("产品", vo.getProductName() == null ? "-" : vo.getProductName());
        info.put("物料", vo.getMaterialName() == null ? "-" : vo.getMaterialName());
        info.put("检验员", vo.getInspector() == null ? "-" : vo.getInspector());
        info.put("检验时间", vo.getInspectTime() == null ? "" : vo.getInspectTime().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        info.put("检验结果", vo.getResultName() == null ? (vo.getResult() == null ? "-" : vo.getResult()) : vo.getResultName());
        info.put("总数/合格/不合格",
                df.format(vo.getTotalQty() == null ? 0 : vo.getTotalQty()) + " / "
                        + df.format(vo.getPassQty() == null ? 0 : vo.getPassQty()) + " / "
                        + df.format(vo.getFailQty() == null ? 0 : vo.getFailQty()));

        java.util.List<String[]> rows = new java.util.ArrayList<>();
        if (vo.getItems() != null) {
            for (com.jjx.production.domain.vo.InspectionItemVO item : vo.getItems()) {
                rows.add(new String[]{
                        String.valueOf(rows.size() + 1),
                        item.getCheckItem() == null ? "" : item.getCheckItem(),
                        item.getStandard() == null ? "" : item.getStandard(),
                        item.getActualValue() == null ? "" : item.getActualValue(),
                        item.getResult() == null ? "" : item.getResult(),
                        item.getRemark() == null ? "" : item.getRemark(),
                });
            }
        }

        return com.jjx.common.utils.pdf.PdfDocBuilder.create()
                .withConfig(pdfConfigLoader.load())
                .title("质  检  报  告")
                .info(info)
                .items(new String[]{"序号", "检验项目", "标准要求", "实测值", "结果", "备注"}, rows)
                .amounts(new String[][]{{"合格率", (vo.getTotalQty() != null && vo.getTotalQty() > 0 && vo.getPassQty() != null)
                        ? Math.round(vo.getPassQty() * 1000.0 / vo.getTotalQty()) / 10.0 + "%" : "-"}})
                .remark(vo.getDefectDesc() == null ? vo.getRemark()
                        : (vo.getRemark() == null ? "缺陷说明：" + vo.getDefectDesc() : "缺陷说明：" + vo.getDefectDesc() + "；" + vo.getRemark()))
                .signatures("检验员：" + (vo.getInspector() == null ? "" : vo.getInspector()),
                        "客户确认：", "日期：")
                .toBytes();
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
