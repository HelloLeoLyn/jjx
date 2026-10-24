package com.jjx.inventory.master;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jjx.inventory.domain.InventoryMaterial;
import com.jjx.inventory.mapper.InventoryMaterialMapper;
import lombok.RequiredArgsConstructor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import java.io.ByteArrayOutputStream;
import java.util.*;

/** 只读迁移候选与校验，不根据名称自动认定品类或属性事实。 */
@Service
@RequiredArgsConstructor
public class MaterialMasterAuditService {
    private final InventoryMaterialMapper materials;
    private final MaterialMasterCatalog catalog;
    private final MaterialMasterService service;
    public record Problem(Long materialId, String materialName, String reason) { }
    public record Audit(int total, int pendingMigration, int newModel, Map<String, Long> categoryDistribution,
                        long customerSuppliedCount, long sampleCount, List<Problem> problems) { }

    public Audit inspect() {
        List<InventoryMaterial> all = materials.selectList(new LambdaQueryWrapper<InventoryMaterial>().orderByAsc(InventoryMaterial::getMaterialId));
        Map<String, Long> distribution = new LinkedHashMap<>();
        List<Problem> problems = new ArrayList<>();
        int pending = 0, modern = 0; long supplied = 0, samples = 0;
        for (InventoryMaterial material : all) {
            if (material.getOriginalName() == null) { pending++; continue; }
            modern++;
            if (Boolean.TRUE.equals(material.getIsCustomerSupplied())) supplied++;
            if (Boolean.TRUE.equals(material.getIsSample())) samples++;
            try {
                var config = catalog.configuration(material.getCategoryId());
                distribution.merge(config.category().pathName(), 1L, Long::sum);
                var dto = new MaterialMasterDTO(); BeanUtils.copyProperties(material, dto);
                dto.setAttributeJson(service.readAttributes(material.getAttributeJson()));
                var prepared = service.prepare(dto).material();
                if (materials.materialTypeOf(material.getCategoryId()) == null) problems.add(new Problem(material.getMaterialId(),material.getMaterialName(),"分类路径无法派生类型"));
                if (!Objects.equals(material.getSpecification(), prepared.getSpecification())) problems.add(new Problem(material.getMaterialId(),material.getMaterialName(),"规格文本与属性生成结果不一致"));
            } catch (RuntimeException e) { problems.add(new Problem(material.getMaterialId(),material.getMaterialName(),e.getMessage())); }
        }
        return new Audit(all.size(),pending,modern,distribution,supplied,samples,problems);
    }

    public byte[] migrationCandidates() {
        List<InventoryMaterial> pending = materials.selectList(new LambdaQueryWrapper<InventoryMaterial>()
                .isNull(InventoryMaterial::getOriginalName).orderByAsc(InventoryMaterial::getMaterialId));
        String[] headers={"物料ID","原名称（来源留痕）","原规格（来源留痕）","当前名称","当前规格","当前尺寸","单位","供应商","业务备注（原样保留）","待确认品类编码"};
        try (XSSFWorkbook workbook=new XSSFWorkbook()) {
            var sheet=workbook.createSheet("待确认物料"); var header=sheet.createRow(0);
            for(int i=0;i<headers.length;i++){header.createCell(i).setCellValue(headers[i]);sheet.setColumnWidth(i,25*256);}
            int index=0;
            for(InventoryMaterial material:pending){
                var row=sheet.createRow(++index);
                var source=MaterialSourceValues.from(material);
                String name=source.originalName();
                String spec=source.originalSpecification();
                String[] values={material.getMaterialId().toString(),name,spec,material.getMaterialName(),material.getSpecification(),material.getSize(),material.getUnit(),material.getSupplierName(),material.getRemark(),""};
                for(int i=0;i<values.length;i++)row.createCell(i).setCellValue(values[i]==null?"":values[i]);
            }
            sheet.createFreezePane(0,1); var out=new ByteArrayOutputStream();workbook.write(out);return out.toByteArray();
        } catch(Exception e){throw new com.jjx.common.exception.BusinessException("导出迁移候选失败："+e.getMessage());}
    }
}
