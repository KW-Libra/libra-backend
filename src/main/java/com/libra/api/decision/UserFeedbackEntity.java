package com.libra.api.decision;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_feedback")
public class UserFeedbackEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "decision_run_id", nullable = false, length = 36)
    private String decisionRunId;

    @Column(name = "response", nullable = false, length = 32)
    private String response;

    @Lob
    @Column(name = "reason")
    private String reason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected UserFeedbackEntity() {
    }

    public UserFeedbackEntity(String decisionRunId, String response, String reason, LocalDateTime createdAt) {
        this.decisionRunId = decisionRunId;
        this.response = response;
        this.reason = reason;
        this.createdAt = createdAt;
    }
}
