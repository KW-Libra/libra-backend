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
@Table(name = "portfolio_snapshots")
public class PortfolioSnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", length = 36)
    private String userId;

    @Column(name = "source", nullable = false, length = 64)
    private String source;

    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;

    @Lob
    @Column(name = "snapshot_payload", nullable = false)
    private String snapshotPayload;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected PortfolioSnapshotEntity() {
    }

    public PortfolioSnapshotEntity(
            String userId,
            String source,
            LocalDateTime generatedAt,
            String snapshotPayload,
            LocalDateTime createdAt
    ) {
        this.userId = userId;
        this.source = source;
        this.generatedAt = generatedAt;
        this.snapshotPayload = snapshotPayload;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getSource() {
        return source;
    }

    public LocalDateTime getGeneratedAt() {
        return generatedAt;
    }

    public String getSnapshotPayload() {
        return snapshotPayload;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
