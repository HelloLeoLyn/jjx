package com.jjx.engineering.printing.screen;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjx.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ScreenRequirementsTest {
    @Test void rejectsInvalidBoundsAndUnknownUnits() {
        var service = new ScreenService(null, new ObjectMapper());
        for (String raw : new String[]{"[]", "null", "bad", "{\"meshMin\":0}",
                "{\"meshMin\":1.5}", "{\"tensionMin\":20,\"tensionMax\":10}",
                "{\"tensionUnit\":\"kg\"}", "{\"position\":null}"}) {
            assertThrows(BusinessException.class, () -> service.validateRequirements(raw), raw);
        }
        assertDoesNotThrow(() -> service.validateRequirements("{\"meshMin\":100,\"meshMax\":200,\"tensionMin\":10}"));
    }

    @Test void releasedSameVersionPlateMustMeetIndependentRequirements() {
        var repo = mock(ScreenRepository.class);
        var plate = new HashMap<String,Object>(Map.of("frameId", 1L, "stretchId", 2L,
                "versionId", 3L, "status", ScreenService.PlateStatus.RELEASED.name(), "position", "面板"));
        when(repo.plate(eq(4L), anyBoolean())).thenReturn(plate);
        when(repo.frame(1L,true)).thenReturn(Map.of("status",ScreenService.FrameStatus.READY.name()));
        var stretch = new HashMap<String,Object>(Map.of("mesh",120,"tension_n_cm",new BigDecimal("20"),"qualified",true));
        when(repo.one(anyString(),any(),any())).thenReturn(stretch);
        var service = new ScreenService(repo, new ObjectMapper());
        assertDoesNotThrow(() -> service.requireUsablePlate(3,4,"{\"position\":\"面板\",\"meshMin\":100,\"tensionMax\":20}"));
        assertThrows(BusinessException.class, () -> service.requireUsablePlate(5,4,"{}"));
        assertThrows(BusinessException.class, () -> service.requireUsablePlate(3,4,"{\"meshMin\":121}"));
        assertThrows(BusinessException.class, () -> service.requireUsablePlate(3,4,"{\"position\":\"下线\"}"));
        stretch.put("end_time",java.time.LocalDateTime.now());
        assertThrows(BusinessException.class, () -> service.requireUsablePlate(3,4,"{}"));
        stretch.remove("end_time");
        plate.put("status",ScreenService.PlateStatus.WASHED.name());
        assertThrows(BusinessException.class, () -> service.requireUsablePlate(3,4,"{}"));
    }
}
