package com.jjx.inventory.migration;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjx.common.enums.StatusEnum;
import com.jjx.common.exception.BusinessException;
import com.jjx.inventory.domain.*;
import com.jjx.inventory.enums.*;
import com.jjx.inventory.mapper.*;
import com.jjx.inventory.master.*;
import com.jjx.inventory.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.*;
import static com.jjx.inventory.migration.InventoryMigrationData.*;

@Service
@RequiredArgsConstructor
public class InventoryMigrationService {
    private final InventoryMigrationRepository repository;
    private final MaterialMasterService master;
    private final InventoryMaterialMapper materials;
    private final InventoryInboundOrderMapper orders;
    private final InventoryInboundItemMapper lines;
    private final InventoryStockItemMapper stocks;
    private final InventoryTransactionMapper transactions;
    private final InventoryStorageLocationMapper locations;
    private final InventoryItemService identities;
    private final InventoryStockMutationService mutation;
    private final ObjectMapper json;
    private final com.jjx.framework.common.RedisSequenceService sequences;

    @Transactional(readOnly=true)
    public Check preview(Batch batch) {
        validateBatch(batch);
        Long warehouse=repository.warehouse(batch.warehouseName(), false);
        InventoryInboundOrder imported = repository.batch(batch.batchCode());
        if (imported != null) {
            requireSamePayload(imported, batch);
            return new Check(batch.lines().size(), 0, (int)batch.lines().stream().filter(l -> l.quantity().signum()==0).count(), List.of(), true);
        }
        var errors = new ArrayList<String>();
        var existingLocations=new HashMap<String,InventoryStorageLocation>();
        for (InventoryStorageLocation location:repository.locations(warehouse)) {
            putLocation(existingLocations,location.getLocationName(),location);
            putLocation(existingLocations,location.getLocationCode(),location);
        }
        Set<Integer> sourceRows = new HashSet<>(); Set<String> keys = new HashSet<>(); int zero=0;
        for (Line line : batch.lines()) {
            try {
                validateLine(line);
                InventoryStorageLocation location=line.location()==null?null:existingLocations.get(locationKey(line.location()));
                if (location!=null && !LocationState.NORMAL.code.equals(location.getStatus())) throw new BusinessException("对应库位已停用，请先核查");
                if (!sourceRows.add(line.sourceRow())) throw new BusinessException("重复源行号");
                keys.add(identity(master.prepareMigration(line.material()).material()));
                if (line.quantity().signum()==0) zero++;
                provenance(line);
            } catch (RuntimeException e) { errors.add("源行" + (line == null ? "?" : line.sourceRow()) + "：" + e.getMessage()); }
        }
        return new Check(batch.lines().size(), keys.size(), zero, errors, false);
    }

    @Transactional(rollbackFor=Exception.class)
    public Imported importBatch(Batch batch, String operator, String displayName, Long operatorId) {
        validateBatch(batch);
        // 同仓库初始迁移串行；重复提交在锁内二次检查，不能重复加数量。
        Long warehouse = repository.warehouse(batch.warehouseName(), true);
        InventoryInboundOrder previous = repository.batch(batch.batchCode());
        if (previous != null) {
            requireSamePayload(previous, batch);
            return new Imported(previous.getInboundId(), batch.batchCode(), batch.lines().size(), true);
        }
        Check check=preview(batch);
        if (!check.errors().isEmpty()) throw new BusinessException(String.join("；", check.errors().subList(0,Math.min(check.errors().size(),10))));
        InventoryInboundOrder order=new InventoryInboundOrder();
        order.setInboundNo(batch.batchCode()); order.setInboundType(InboundTypeEnum.MIGRATION.getCode());
        order.setSourceType(SOURCE); order.setSourceNo(batch.batchCode()); order.setTraceId(batch.sourceSha256());
        order.setWarehouseId(warehouse); order.setInboundDate(batch.businessDate());
        order.setOrderStatus(InventoryOrderStatusEnum.COMPLETED.getValue());
        order.setApproveStatus(com.jjx.common.enums.ApproveStatusEnum.APPROVED.getValue());
        order.setApproverId(operatorId); order.setApproverName(displayName); order.setApproveTime(LocalDateTime.now());
        order.setTotalQuantity(BigDecimal.ZERO); order.setTotalAmount(null);
        order.setCreateBy(operator); order.setCreateTime(LocalDateTime.now()); order.setUpdateTime(LocalDateTime.now());
        order.setRemark(payloadHash(batch)); orders.insert(order);
        // 同事务内初始化未知金额；不能让DB默认0把缺失成本伪装成真实零成本。
        orders.update(null,new UpdateWrapper<InventoryInboundOrder>().eq("inbound_id",order.getInboundId()).set("total_amount",null).set("total_quantity",null));
        var known = new HashMap<String,InventoryMaterial>();
        for (InventoryMaterial material : materials.selectList(new LambdaQueryWrapper<>())) known.put(identity(material),material);
        var locationMap = new HashMap<String,InventoryStorageLocation>();
        for (InventoryStorageLocation location : repository.locations(warehouse)) {
            putLocation(locationMap,location.getLocationName(),location);
            putLocation(locationMap,location.getLocationCode(),location);
        }
        for (Line input : batch.lines()) {
            InventoryMaterial prepared=master.prepareMigration(input.material()).material();
            String key=identity(prepared);
            InventoryMaterial material=known.get(key);
            if (material == null) {
                MaterialMasterDTO creating=new MaterialMasterDTO();
                org.springframework.beans.BeanUtils.copyProperties(input.material(),creating);
                String prefix=com.jjx.inventory.util.MaterialClassification.codePrefixOf(materials.selectCategory(creating.getCategoryId()));
                creating.setMaterialCode(sequences.generateBusinessNumberByTypeWithPrefix("material",prefix,"",6,repository.materialCodeFloor()));
                material=master.saveMigration(creating,operator,repository.nextMaterialId()); known.put(key,material);
            }
            if (input.originalRemark()!=null && !input.originalRemark().isBlank()) {
                InventoryMaterial locked=materials.selectOne(new LambdaQueryWrapper<InventoryMaterial>()
                    .eq(InventoryMaterial::getMaterialId,material.getMaterialId()).last("FOR UPDATE"));
                String sourceRemark=appendSourceRemark(locked.getRemark(),batch.batchCode(),input.sourceRow(),input.originalRemark());
                if (!Objects.equals(sourceRemark,locked.getRemark())) {
                    materials.update(null,new UpdateWrapper<InventoryMaterial>().eq("material_id",material.getMaterialId())
                        .set("remark",sourceRemark).set("update_by",operator).set("update_time",LocalDateTime.now()));
                }
                material.setRemark(sourceRemark);
            }
            if (input.originalUnit()!=null && !Objects.equals(material.getUnit(),input.originalUnit())) throw new BusinessException("源行"+input.sourceRow()+"：原单位与库存单位不一致，须先明确换算");
            InventoryItem existingIdentity=identities.getBySource(InventoryItemTypeEnum.MATERIAL,material.getMaterialId());
            if (existingIdentity!=null && !Objects.equals(existingIdentity.getItemCode(),material.getMaterialCode())) throw new BusinessException("统一库存身份与新档案编码不一致，禁止错误关联");
            InventoryItem inventoryItem=identities.ensure(InventoryItemTypeEnum.MATERIAL,material.getMaterialId(),material.getMaterialCode(),material.getMaterialName(),material.getSpecification(),material.getUnit());
            Long location=location(warehouse,input.location(),locationMap,batch.batchCode(),operator);
            InventoryInboundItem item=new InventoryInboundItem();
            item.setInboundId(order.getInboundId()); item.setInventoryItemId(inventoryItem.getInventoryItemId());
            item.setMaterialId(material.getMaterialId()); item.setMaterialCode(material.getMaterialCode());
            item.setMaterialName(material.getMaterialName()); item.setSpecification(material.getSpecification());
            item.setUnit(material.getUnit()); item.setQuantity(input.quantity()); item.setAcceptedQuantity(input.quantity());
            item.setPostedQuantity(input.quantity()); item.setLocationId(location); item.setSortOrder(input.sourceRow());
            item.setBatchNo(batch.batchCode()+"-"+input.sourceRow()); item.setRemark(provenance(input));
            // 原生产/到期日期、单位成本和金额均未知；不要写默认日期或0价格。
            item.setUnitPrice(null); item.setAmount(null); item.setUpdateTime(LocalDateTime.now()); lines.insert(item);
            InventoryStockItem stock=new InventoryStockItem(); stock.setInventoryItemId(inventoryItem.getInventoryItemId());
            stock.setMaterialId(material.getMaterialId()); stock.setMaterialCode(material.getMaterialCode());
            stock.setMaterialName(material.getMaterialName()); stock.setWarehouseId(warehouse); stock.setLocationId(location);
            stock.setBatchNo(item.getBatchNo()); stock.setSourceInboundItemId(item.getItemId());
            stock.setCreateTime(LocalDateTime.now()); stock.setUpdateTime(LocalDateTime.now());
            stock.setQuantity(BigDecimal.ZERO); stock.setReservedQuantity(BigDecimal.ZERO);
            stock.setStatus(StockItemStatusEnum.ACTIVE.getValue());
            if (input.quantity().signum()==0) mutation.initializeZero(stock);
            else {
                InventoryTransaction tx=new InventoryTransaction(); tx.setTransactionType(TransactionTypeEnum.ADJUST.getCode());
                tx.setSourceType(SOURCE); tx.setSourceId(order.getInboundId()); tx.setSourceNo(batch.batchCode());
                tx.setSourceItemId(item.getItemId()); tx.setOperatorId(operatorId); tx.setOperatorName(displayName); tx.setCreateBy(operator);
                tx.setRemark("期初盘点业务日期"+batch.businessDate()+"；源行"+input.sourceRow());
                stock=mutation.applyDelta(stock,input.quantity(),tx);
            }
            // 明细插入后一次性记录实际库存行；不修改已存在的原始凭证。
            item.setStockItemId(stock.getItemId()); lines.updateById(item);
        }
        return new Imported(order.getInboundId(),batch.batchCode(),batch.lines().size(),false);
    }

    @Transactional(readOnly=true)
    public List<BatchView> batches() {
        return repository.batches().stream().map(order -> {
            List<InventoryInboundItem> items=repository.lines(order.getInboundId());
            int missing=(int)items.stream().filter(i -> repository.stocks(i,false).stream().anyMatch(s -> s.getUnitCost()==null)).count();
            return new BatchView(order.getInboundId(),order.getInboundNo(),order.getInboundDate(),String.valueOf(order.getCreateTime()),items.size(),missing);
        }).toList();
    }

    @Transactional(readOnly=true)
    public List<RowView> rows(Long batchId) {
        InventoryInboundOrder order=orders.selectById(batchId);
        if (order==null || !SOURCE.equals(order.getSourceType())) throw new BusinessException("不是库存迁移批次");
        var flowSummaries=new HashMap<Long,Map<String,Map<String,Object>>>();
        return repository.lines(batchId).stream().map(line -> {
            List<InventoryStockItem> batchStocks=repository.stocks(line,false);
            BigDecimal remaining=batchStocks.stream().map(InventoryStockItem::getQuantity).reduce(BigDecimal.ZERO,BigDecimal::add);
            BigDecimal cost=batchStocks.isEmpty()?null:batchStocks.get(0).getUnitCost();
            Long version=version(repository.flows(line));
            var summary=flowSummaries.computeIfAbsent(line.getInventoryItemId(),id -> {
                Map<String,Map<String,Object>> result=new HashMap<>();
                transactions.selectBatchFlowSummary(id).forEach(s -> result.put(String.valueOf(s.get("batchNo")),s));
                return result;
            }).get(line.getBatchNo());
            BigDecimal received=summary==null?BigDecimal.ZERO:new BigDecimal(String.valueOf(summary.get("receivedQuantity")));
            BigDecimal issued=summary==null?BigDecimal.ZERO:new BigDecimal(String.valueOf(summary.get("issuedQuantity")));
            return new RowView(line.getItemId(),line.getSortOrder(),line.getMaterialCode(),line.getMaterialName(),line.getSpecification(),line.getUnit(),line.getQuantity(),received,issued,remaining,cost,version,
                    line.getLocationId()==null?"":String.valueOf(line.getLocationId()),line.getRemark());
        }).toList();
    }

    @Transactional(rollbackFor=Exception.class)
    public CostResult maintainCosts(Long batchId,List<Cost> requests,String operator,String displayName,Long operatorId) {
        repository.lockBatch(batchId);
        if (requests==null || requests.isEmpty() || requests.size()>2000) throw new BusinessException("请选择1～2000条成本记录");
        Map<Long,InventoryInboundItem> owned=new HashMap<>();
        repository.lines(batchId).forEach(i -> owned.put(i.getItemId(),i));
        Set<Long> seen=new HashSet<>(); int changed=0, adjustments=0; BigDecimal delta=BigDecimal.ZERO;
        for (Cost request:requests) {
            if (request==null || !seen.add(request.lineId()) || !owned.containsKey(request.lineId())) throw new BusinessException("重复记录或明细不属于当前迁移批次");
            decimal(request.unitCost(),"单位成本");
            if (request.reason()==null || request.reason().isBlank() || request.reason().length()>120) throw new BusinessException("请填写1～120字成本补录依据");
            InventoryInboundItem line=owned.get(request.lineId());
            List<InventoryStockItem> held=repository.stocks(line,true);
            if (held.isEmpty()) throw new BusinessException("迁移批次库存行缺失");
            List<InventoryTransaction> flows=repository.flows(line);
            if (!Objects.equals(version(flows),request.expectedVersion())) throw new BusinessException("成本已被其他人修改，请刷新后重试");
            Map<Long,BigDecimal> effective=new HashMap<>();
            for (InventoryTransaction flow:flows) if (isCost(flow) && flow.getSourceItemId()!=null) effective.put(flow.getSourceItemId(),flow.getUnitCost());
            int before=adjustments;
            for (InventoryTransaction original:flows) {
                if (isCost(original) || original.getQuantity().signum()==0) continue;
                BigDecimal old=effective.containsKey(original.getTransactionId())?effective.get(original.getTransactionId()):original.getUnitCost();
                if (same(old,request.unitCost())) continue;
                BigDecimal amount=costDelta(original.getQuantity(),old,request.unitCost());
                InventoryStockItem target=held.stream().filter(s -> Objects.equals(s.getItemId(),original.getStockItemId())).findFirst().orElseThrow(() -> new BusinessException("历史流水缺少准确库存行归属，不能补录成本"));
                appendCost(line,target,original.getTransactionId(),old,request,amount,operator,displayName,operatorId);
                adjustments++; delta=delta.add(amount);
            }
            boolean stockChanged=held.stream().anyMatch(s -> !same(s.getUnitCost(),request.unitCost()));
            if (stockChanged && before==adjustments) {
                // 0数量期初行没有数量流水，用来源明细记录0金额成本维护审计。
                InventoryStockItem target=held.get(0);
                appendCost(line,target,null,target.getUnitCost(),request,BigDecimal.ZERO,operator,displayName,operatorId); adjustments++;
            }
            if (stockChanged || before!=adjustments) {
                for (InventoryStockItem stock:held) stocks.update(null,new UpdateWrapper<InventoryStockItem>().eq("item_id",stock.getItemId()).set("unit_cost",request.unitCost()));
                changed++;
            }
        }
        return new CostResult(changed,adjustments,delta);
    }

    @Transactional(readOnly=true)
    public List<CostAudit> costHistory(Long batchId,Long lineId) {
        InventoryInboundOrder order=orders.selectById(batchId);
        if (order==null || !SOURCE.equals(order.getSourceType())) throw new BusinessException("不是库存迁移批次");
        InventoryInboundItem line=repository.lines(batchId).stream().filter(i -> Objects.equals(i.getItemId(),lineId)).findFirst()
                .orElseThrow(() -> new BusinessException("明细不属于当前迁移批次"));
        List<InventoryTransaction> flows=repository.flows(line);
        Map<Long,InventoryTransaction> byId=new HashMap<>(); flows.forEach(t -> byId.put(t.getTransactionId(),t));
        return flows.stream().filter(InventoryMigrationService::isCost).sorted(Comparator.comparing(InventoryTransaction::getTransactionId).reversed()).map(t -> {
            try {
                var detail=json.readTree(t.getRemark());
                BigDecimal before=detail.hasNonNull("beforeCost")?detail.path("beforeCost").decimalValue():null;
                InventoryTransaction original=byId.get(t.getSourceItemId());
                return new CostAudit(t.getTransactionId(),String.valueOf(t.getTransactionTime()),t.getOperatorName(),before,t.getUnitCost(),t.getAmount(),detail.path("reason").asText(),original==null?"零库存成本记录":original.getSourceNo());
            } catch(Exception e) { throw new BusinessException("成本维护记录无法解析，请核查原始审计记录"); }
        }).toList();
    }

    private void appendCost(InventoryInboundItem line,InventoryStockItem stock,Long target,BigDecimal old,Cost request,BigDecimal amount,String operator,String displayName,Long operatorId) {
        InventoryTransaction audit=new InventoryTransaction(); audit.setTransactionType(TransactionTypeEnum.MIGRATION_COST.getCode());
        audit.setSourceType(COST_SOURCE); audit.setSourceId(line.getInboundId()); audit.setSourceNo(line.getBatchNo()); audit.setSourceItemId(target);
        audit.setStockItemId(stock.getItemId()); audit.setInventoryItemId(stock.getInventoryItemId()); audit.setMaterialId(stock.getMaterialId());
        audit.setMaterialCode(stock.getMaterialCode()); audit.setMaterialName(stock.getMaterialName()); audit.setWarehouseId(stock.getWarehouseId());
        audit.setLocationId(stock.getLocationId()); audit.setBatchNo(stock.getBatchNo()); audit.setQuantity(BigDecimal.ZERO);
        audit.setBeforeQuantity(stock.getQuantity()); audit.setAfterQuantity(stock.getQuantity()); audit.setUnitCost(request.unitCost()); audit.setAmount(amount);
        audit.setTransactionTime(LocalDateTime.now()); audit.setOperatorId(operatorId); audit.setOperatorName(displayName); audit.setCreateBy(operator);
        var detail=new LinkedHashMap<String,Object>(); detail.put("lineId",line.getItemId()); detail.put("beforeCost",old); detail.put("afterCost",request.unitCost()); detail.put("reason",request.reason());
        audit.setRemark(write(detail)); transactions.insert(audit);
    }

    public static BigDecimal costDelta(BigDecimal quantity,BigDecimal old,BigDecimal next) {
        // 原数量不变；差额采用已确定成本与已记录金额之间的差，避免逐次四舍五入累积漂移。
        BigDecimal result=quantity.multiply(next).setScale(2,RoundingMode.HALF_UP).subtract(quantity.multiply(old==null?BigDecimal.ZERO:old).setScale(2,RoundingMode.HALF_UP));
        if (result.precision()-result.scale()>10) throw new BusinessException("成本调整金额超过系统金额精度，请核对单价及单位");
        return result;
    }
    private static boolean same(BigDecimal a,BigDecimal b) { return a!=null && b!=null && a.compareTo(b)==0; }
    private static boolean isCost(InventoryTransaction tx) { return TransactionTypeEnum.MIGRATION_COST.getCode().equalsIgnoreCase(tx.getTransactionType()) && COST_SOURCE.equals(tx.getSourceType()); }
    private static Long version(List<InventoryTransaction> flows) { return flows.stream().filter(InventoryMigrationService::isCost).map(InventoryTransaction::getTransactionId).max(Long::compareTo).orElse(null); }
    private Long location(Long warehouse,String name,Map<String,InventoryStorageLocation> found,String code,String operator) {
        if (name==null || name.isBlank()) return null;
        String key=locationKey(name); InventoryStorageLocation existing=found.get(key);
        if (existing!=null) {
            if (!LocationState.NORMAL.code.equals(existing.getStatus())) throw new BusinessException("对应库位已停用："+name);
            return existing.getLocationId();
        }
        InventoryStorageLocation created=new InventoryStorageLocation(); created.setWarehouseId(warehouse);
        created.setLocationName(name.trim()); created.setLocationCode("MIG-"+hash(warehouse+"|"+key).substring(0,20));
        created.setLocationType("normal"); created.setStatus(LocationState.NORMAL.code); created.setSortOrder(0);
        created.setCreateBy(operator); created.setUpdateBy(operator); created.setCreateTime(LocalDateTime.now()); created.setUpdateTime(LocalDateTime.now());
        locations.insert(created); putLocation(found,name,created); return created.getLocationId();
    }
    private static void putLocation(Map<String,InventoryStorageLocation> map,String key,InventoryStorageLocation location) {
        if (key==null || key.isBlank()) return;
        InventoryStorageLocation before=map.putIfAbsent(locationKey(key),location);
        if (before!=null && !Objects.equals(before.getLocationId(),location.getLocationId())) throw new BusinessException("同一位置规范化后对应多个库位："+key);
    }
    public static String locationKey(String text) { return Normalizer.normalize(text,Normalizer.Form.NFKC).replaceAll("[\\s，,、；;]+","").toLowerCase(Locale.ROOT); }
    /** 来源按批次和原行分段；不丢合并物料的备注，不截断，不改已有用户说明。 */
    public static String appendSourceRemark(String current,String batchCode,Integer sourceRow,String originalRemark) {
        if (originalRemark==null || originalRemark.isBlank()) return current;
        String marker="【迁移来源 "+batchCode+" / 源行"+sourceRow+"】";
        String block=marker+"\n"+originalRemark;
        if (current!=null && current.contains(marker)) {
            int start=current.indexOf(marker);
            int end=current.indexOf("\n\n【迁移来源 ",start+marker.length());
            if (current.substring(start,end<0?current.length():end).equals(block)) return current;
            throw new BusinessException("同一迁移源行备注不一致，请核查");
        }
        String result=current==null || current.isBlank()?block:current+"\n\n"+block;
        if (result.length()>1000) throw new BusinessException("合并来源备注超过1000字，请核查，不能截断");
        return result;
    }
    private String provenance(Line line) {
        var data=new LinkedHashMap<String,Object>(); data.put("row",line.sourceRow()); data.put("name",line.originalName()); data.put("spec",line.originalSpecification());
        data.put("unit",line.originalUnit()); data.put("quantity",line.quantity()); data.put("area",line.originalArea()); data.put("issues",line.issues());
        data.put("originalRemark",line.originalRemark());
        String result=write(data); if (result.length()>500) throw new BusinessException("来源信息超过500字，请拆分核查"); return result;
    }
    private static void validateBatch(Batch batch) {
        if (batch==null || batch.sourceSha256()==null || !batch.sourceSha256().matches("[a-f0-9]{64}") || batch.businessDate()==null || batch.warehouseName()==null || batch.warehouseName().isBlank()
                || batch.batchCode()==null || !batch.batchCode().matches("MIG[0-9]{8}-[a-f0-9]{12}") || !batch.batchCode().equals("MIG"+batch.businessDate().toString().replace("-","")+"-"+batch.sourceSha256().substring(0,12))
                || batch.lines()==null || batch.lines().isEmpty() || batch.lines().size()>2000) throw new BusinessException("迁移批次、原文件指纹、业务日期或明细无效");
    }
    private static void validateLine(Line line) {
        if (line==null || line.sourceRow()==null || line.sourceRow()<1 || line.material()==null || line.material().getMaterialId()!=null) throw new BusinessException("源行与新增物料信息无效");
        decimal(line.quantity(),"原库存数量");
        if (line.originalUnit()==null ? line.quantity().signum()!=0 : !line.originalUnit().equals(line.material().getUnit())) throw new BusinessException("原单位缺失或与档案单位不一致");
        if (line.location()!=null && line.location().length()>100) throw new BusinessException("库位名称超过100字");
        if (line.originalName()==null || line.originalName().length()>200 || line.originalSpecification()!=null && line.originalSpecification().length()>500) throw new BusinessException("原名称/规格缺失或过长");
    }
    private static void decimal(BigDecimal value,String label) {
        if (value==null || value.signum()<0 || value.stripTrailingZeros().scale()>4 || value.precision()-value.scale()>8) throw new BusinessException(label+"须非负、最多4位小数和8位整数");
    }
    private void requireSamePayload(InventoryInboundOrder order,Batch batch) {
        if (!SOURCE.equals(order.getSourceType()) || !Objects.equals(order.getTraceId(),batch.sourceSha256()) || !payloadHash(batch).equals(order.getRemark())) throw new BusinessException("批次号已存在但数据内容不同，禁止重复覆盖");
    }
    private String payloadHash(Batch batch) {
        // Web与一次性命令的Jackson缺省配置可能不同；来源指纹必须采用同一规范化方式。
        try {
            ObjectMapper canonical=json.copy().setSerializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
                    .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                    .enable(com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                    .enable(com.fasterxml.jackson.databind.MapperFeature.SORT_PROPERTIES_ALPHABETICALLY);
            return hash(canonical.writeValueAsString(batch));
        } catch (Exception e) { throw new BusinessException("迁移来源指纹生成失败："+e.getMessage()); }
    }
    private String write(Object value) { try { return json.writeValueAsString(value); } catch(Exception e) { throw new BusinessException("迁移资料序列化失败："+e.getMessage()); } }
    private static String hash(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch(Exception e) { throw new IllegalStateException(e); } }
    private String identity(InventoryMaterial material) {
        // 与普通导入共用包含供应商、规格和属性的完整身份，不用型号作为唯一键。
        return master.duplicateKey(material);
    }
}
