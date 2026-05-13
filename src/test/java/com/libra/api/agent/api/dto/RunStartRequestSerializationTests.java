package com.libra.api.agent.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class RunStartRequestSerializationTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesRecordForAgentPayload() throws Exception {
        String json = objectMapper.writeValueAsString(
            new RunStartRequest("smoke", null, "user_request", null, true));

        assertThat(json).contains("\"query\"");
        assertThat(json).contains("\"trigger\"");
        assertThat(json).contains("\"approval_required\"");
    }
}
