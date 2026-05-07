package com.libra.api.portfolio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "portfolio_definitions")
public class PortfolioDefinitionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Lob
    @Column(name = "definition_payload", nullable = false)
    private String definitionPayload;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected PortfolioDefinitionEntity() {
    }

    public PortfolioDefinitionEntity(String userId, String definitionPayload, LocalDateTime createdAt) {
        this.userId = userId;
        this.definitionPayload = definitionPayload;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getDefinitionPayload() {
        return definitionPayload;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
