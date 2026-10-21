package com.jjx.product.service;

import com.jjx.product.domain.entity.ProductStandardProcess;
import com.jjx.product.domain.vo.StandardProcessCommonVO;
import com.jjx.product.mapper.StandardProcessUsageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StandardProcessUsageService {
    private final StandardProcessUsageMapper usageMapper;
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    /** 近 90 个自然日（含今天）；重算整个窗口以修正 BOM 后续编辑/删除，事务内替换。 */
    @Transactional(rollbackFor = Exception.class)
    public void aggregateUsage() {
        LocalDate today = LocalDate.now(ZONE);
        LocalDate start = today.minusDays(89);
        LocalDate end = today.plusDays(1);
        usageMapper.clearPeriod(start, end);
        usageMapper.aggregatePeriod(start, end, ProductStandardProcess.Status.REJECTED.value);
    }

    public List<StandardProcessCommonVO> getCommon(int limit) {
        LocalDate today = LocalDate.now(ZONE);
        return usageMapper.selectCommon(today.minusDays(89), today.plusDays(1),
                ProductStandardProcess.Status.REJECTED.value, Math.max(1, Math.min(limit, 12)));
    }
}
