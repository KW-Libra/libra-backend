package com.libra.api.agent.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class RunStartRequestSerializationTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesRecordForAgentPayload() throws Exception {
        RunStartRequest req = objectMapper.readValue(
            """
            {
              "query": "smoke",
              "trigger": "user_request",
              "depth": "shallow",
              "deadline_seconds": 180,
              "approval_required": true,
              "knowledge_base": { "events": [] }
            }
            """,
            RunStartRequest.class);

        String json = objectMapper.writeValueAsString(req);

        assertThat(json).contains("\"query\"");
        assertThat(json).contains("\"trigger\":\"pull\"");
        assertThat(json).contains("\"depth\":\"shallow\"");
        assertThat(json).contains("\"deadline_seconds\":180");
        assertThat(json).contains("\"approval_required\"");
        assertThat(json).contains("\"enable_human_interrupts\":true");
        assertThat(json).contains("\"knowledge_base\"");
    }

    @Test
    void defaultsApprovalRequiredToFalseWhenMissing() throws Exception {
        RunStartRequest req = objectMapper.readValue(
            "{\"query\":\"smoke\"}",
            RunStartRequest.class);

        assertThat(req.approval_required()).isFalse();
        assertThat(req.enable_human_interrupts()).isFalse();
        assertThat(req.trigger()).isEqualTo("pull");
        assertThat(req.depth()).isEqualTo("medium");
    }
}
