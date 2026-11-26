package com.jjx.product.service;

import com.jjx.engineering.domain.entity.EngineeringRoutingItem;
import com.jjx.engineering.printing.artwork.ArtworkService;
import com.jjx.engineering.printing.screen.ScreenService;
import com.jjx.product.domain.dto.EngineeringRoutingItemDTO;
import com.jjx.product.mapper.EngineeringRoutingItemMapper;
import com.jjx.product.service.impl.EngineeringRoutingServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class EngineeringRoutingPrintBindingTest {
    @Test void saveKeepsOptionalPlateAndIndependentRequirementsOnParentAndChild() {
        var mapper = mock(EngineeringRoutingItemMapper.class);
        var service = new EngineeringRoutingServiceImpl(null,null,mapper,null,null,null);
        var artwork = mock(ArtworkService.class);
        var screen = mock(ScreenService.class);
        ReflectionTestUtils.setField(service,"artworkService",artwork);
        ReflectionTestUtils.setField(service,"screenService",screen);
        var written = new ArrayList<EngineeringRoutingItem>();
        when(mapper.insert(any(EngineeringRoutingItem.class))).thenAnswer(inv -> {
            EngineeringRoutingItem row=inv.getArgument(0); row.setItemId((long)written.size()+1); written.add(row); return 1;
        });
        var parent = new EngineeringRoutingItemDTO(); parent.setArtworkVersionId(3L);
        parent.setDefaultPlateId(4L); parent.setPrintRequirements("{\"meshMin\":100}");
        var child = new EngineeringRoutingItemDTO(); child.setArtworkVersionId(5L);
        child.setPrintRequirements("{\"position\":\"下线\"}"); parent.setChildren(List.of(child));
        ReflectionTestUtils.invokeMethod(service,"saveItems",10L,List.of(parent));
        assertEquals(4L,written.get(0).getDefaultPlateId());
        assertEquals(parent.getPrintRequirements(),written.get(0).getPrintRequirements());
        assertNull(written.get(1).getDefaultPlateId());
        assertEquals(child.getPrintRequirements(),written.get(1).getPrintRequirements());
        assertEquals(written.get(0).getItemId(),written.get(1).getParentId());
        verify(screen).requireUsablePlate(3,4,parent.getPrintRequirements());
    }

    @Test void plateWithoutVersionFailsBeforeAnyInsert() {
        var mapper=mock(EngineeringRoutingItemMapper.class);
        var service=new EngineeringRoutingServiceImpl(null,null,mapper,null,null,null);
        var row=new EngineeringRoutingItemDTO(); row.setDefaultPlateId(4L);
        assertThrows(com.jjx.common.exception.BusinessException.class,
                () -> ReflectionTestUtils.invokeMethod(service,"saveItems",10L,List.of(row)));
        verifyNoInteractions(mapper);
    }

    @Test void blankRequirementsBecomeSqlNullAndVersionLocksAreSorted() {
        var service=new EngineeringRoutingServiceImpl(null,null,null,null,null,null);
        var artwork=mock(ArtworkService.class);
        ReflectionTestUtils.setField(service,"artworkService",artwork);
        var a=new EngineeringRoutingItemDTO(); a.setArtworkVersionId(9L); a.setPrintRequirements("  ");
        var b=new EngineeringRoutingItemDTO(); b.setArtworkVersionId(3L);
        ReflectionTestUtils.invokeMethod(service,"lockArtworkVersions",List.of(a,b));
        var calls=inOrder(artwork);
        calls.verify(artwork).requireUsableVersion(3);
        calls.verify(artwork).requireUsableVersion(9);
        EngineeringRoutingItem row=ReflectionTestUtils.invokeMethod(service,"buildItem",a,10L);
        assertNull(row.getPrintRequirements());
    }
}
