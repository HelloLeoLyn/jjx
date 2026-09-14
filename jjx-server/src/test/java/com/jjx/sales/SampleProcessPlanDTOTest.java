package com.jjx.sales;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjx.sales.dto.save.SampleProcessPlanDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SampleProcessPlanDTOTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void itemAcceptsWorkInstructionAndOperationRemark() throws Exception {
        String json = """
                {
                  "items": [{
                    "processName": "下线冲型",
                    "processNote": "线路外形",
                    "remark": "一车二模"
                  }]
                }
                """;

        SampleProcessPlanDTO dto = objectMapper.readValue(json, SampleProcessPlanDTO.class);

        assertEquals("线路外形", dto.getItems().getFirst().getProcessNote());
        assertEquals("一车二模", dto.getItems().getFirst().getRemark());
    }
}
