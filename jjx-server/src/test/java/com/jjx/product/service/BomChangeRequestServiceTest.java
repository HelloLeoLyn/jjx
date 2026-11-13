package com.jjx.product.service;

import com.jjx.common.exception.BusinessException;
import com.jjx.engineering.domain.entity.EngineeringBom;
import com.jjx.product.domain.dto.BomChangeRequestDTO;
import com.jjx.product.domain.entity.BomChangeRequest;
import com.jjx.product.domain.entity.BomChangeRequestItem;
import com.jjx.product.domain.vo.BomCheckIssueVO;
import com.jjx.product.mapper.BomChangeRequestItemMapper;
import com.jjx.product.mapper.BomChangeRequestMapper;
import com.jjx.product.mapper.EngineeringBomMapper;
import com.jjx.system.utils.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** BOM 变更单 ECN-lite（dev-20261112-009）：打包提交/统一生效/取消不动 BOM。 */
@ExtendWith(MockitoExtension.class)
class BomChangeRequestServiceTest {

    @Mock BomChangeRequestMapper changeMapper;
    @Mock BomChangeRequestItemMapper itemMapper;
    @Mock EngineeringBomMapper bomMapper;
    @Mock IEngineeringBomService bomService;
    @InjectMocks BomChangeRequestService service;

    private BomChangeRequest change(String status) {
        BomChangeRequest c = new BomChangeRequest();
        c.setChangeId(9L);
        c.setStatus(status);
        c.setEffectiveDate(LocalDate.now());
        return c;
    }

    private BomChangeRequestItem item(long bomId) {
        BomChangeRequestItem i = new BomChangeRequestItem();
        i.setChangeId(9L);
        i.setBomId(bomId);
        return i;
    }

    @Test
    void submitIsBlockedWhenAnyBomHasIssues() {
        when(changeMapper.selectById(9L)).thenReturn(change("DRAFT"));
        when(itemMapper.selectList(any())).thenReturn(List.of(item(10L), item(11L)));
        BomCheckIssueVO issue = new BomCheckIssueVO();
        issue.setField("MODULE_QTY_INVALID");
        issue.setMaterialCode("AUX000106");
        issue.setMessage("模数必须大于0");
        when(bomService.checkBomForSubmit(10L)).thenReturn(List.of());
        when(bomService.checkBomForSubmit(11L)).thenReturn(List.of(issue));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.submit(9L));
        org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().contains("MODULE_QTY_INVALID"));
        verify(changeMapper, never()).updateById(any());
    }

    @Test
    void activateRetiresOldCurrentAndActivatesWholePackage() {
        when(changeMapper.selectById(9L)).thenReturn(change("APPROVED"));
        when(itemMapper.selectList(any())).thenReturn(List.of(item(10L)));
        EngineeringBom target = new EngineeringBom();
        target.setBomId(10L);
        target.setProductId(1L);
        target.setBomCode("B10");
        target.setApproveStatus(3);
        target.setOutputType("PRODUCT");
        when(bomMapper.selectById(10L)).thenReturn(target);
        EngineeringBom old = new EngineeringBom();
        old.setBomId(9L);
        old.setProductId(1L);
        old.setOutputType("PRODUCT");
        old.setIsCurrent(true);
        when(bomMapper.selectList(any())).thenReturn(List.of(old));

        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getRealName).thenReturn("admin");
            BomChangeRequest result = service.activate(9L, true);
            assertEquals("EFFECTIVE", result.getStatus());
        }

        verify(bomMapper).updateById(any(EngineeringBom.class));
        assertEquals(false, old.getIsCurrent());
    }

    @Test
    void cancelNeverTouchesBom() {
        when(changeMapper.selectById(9L)).thenReturn(change("PENDING"));
        BomChangeRequest result = service.cancel(9L);
        assertEquals("CANCELLED", result.getStatus());
        verify(bomMapper, never()).updateById(any());
        verify(bomMapper, never()).selectList(any());
    }

    @Test
    void activateRejectedBeforeEffectiveDateWithoutForce() {
        BomChangeRequest future = change("APPROVED");
        future.setEffectiveDate(LocalDate.now().plusDays(3));
        when(changeMapper.selectById(9L)).thenReturn(future);
        assertThrows(BusinessException.class, () -> service.activate(9L, false));
    }
}
