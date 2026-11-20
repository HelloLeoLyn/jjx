package com.jjx.trace.bom;
import cn.hutool.json.JSONUtil;
import com.jjx.common.enums.YesNoEnum;
import com.jjx.common.filter.CorrelationIdFilter;
import com.jjx.engineering.domain.entity.EngineeringBom;
import com.jjx.product.enums.ManufacturingOutputType;
import com.jjx.product.enums.ProductEnums;
import com.jjx.system.annotation.BusinessType;
import com.jjx.system.domain.entity.SysOperLog;
import com.jjx.system.mapper.SysOperLogMapper;
import com.jjx.system.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;
/** 同步加入业务事务；日志失败必须使版本操作回滚。 */
@Service
@RequiredArgsConstructor
public class BomTraceRecorder {
    public static final String COPY="复制新版本", CURRENT="切换当前版本", DELETE="删除BOM版本";
    public static final String SUBMIT="整套送审", APPROVE="整套审核通过", REJECT="整套审核驳回";
    private final SysOperLogMapper logs;
    @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public void record(EngineeringBom bom,String action,Integer status,EngineeringBom from,List<String> changes) {
        var snapshot=new LinkedHashMap<String,Object>();
        snapshot.put("bomId",bom.getBomId());snapshot.put("bomCode",bom.getBomCode());snapshot.put("version",bom.getBomVersion());
        snapshot.put("outputType",ManufacturingOutputType.normalize(bom.getOutputType()).name());
        snapshot.put("outputId",bom.getOutputId()!=null?bom.getOutputId():bom.getProductId());
        if(from!=null) {snapshot.put("fromBomId",from.getBomId());snapshot.put("fromVersion",from.getBomVersion());}
        var detail=new LinkedHashMap<String,Object>();detail.put("bomObject",snapshot);detail.put("changes",changes);detail.put("attachments",List.of());
        var log=new SysOperLog();log.setBizType("bom");log.setBizId(bom.getBomId().toString());log.setModule("产品BOM管理");
        log.setAction(action);log.setOperUrl("bom.version.operation");log.setOperGroupId(MDC.get(CorrelationIdFilter.MDC_KEY));
        log.setBusinessType((COPY.equals(action)?BusinessType.INSERT:DELETE.equals(action)?BusinessType.DELETE:
            APPROVE.equals(action)||REJECT.equals(action)?BusinessType.APPROVE:BusinessType.UPDATE).getCode());
        var state=ProductEnums.BomStatus.fromValue(status);log.setBizStatus(state==null?null:state.getLabel());
        log.setStatus(YesNoEnum.YES.getCode());log.setCreateTime(LocalDateTime.now());
        log.setUsername(SecurityUtils.getUsername());log.setRealName(SecurityUtils.getDisplayName());log.setUserId(SecurityUtils.getUserId());
        log.setDetail(JSONUtil.toJsonStr(detail));log.setOperParam(String.join("；",changes));
        if(logs.insert(log)!=1)throw new IllegalStateException("BOM版本流水未保存，业务操作已回滚");
    }
}
