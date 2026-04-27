package com.libra.api.integration.agent;

public class AgentGatewayException extends RuntimeException {

    public AgentGatewayException(String message, Throwable cause) {
        super(message, cause);
    }

    public AgentGatewayException(String message) {
        super(message);
    }
}
