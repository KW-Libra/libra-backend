package com.libra.api.scheduler.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 매일 정해진 시각에 활성 사용자의 포트폴리오에 대해 자동으로 Judge run 을 트리거하는
 * 일일 리밸런싱 스케줄러 설정.
 *
 * <p>{@code enabled} 는 기본 false 다. 운영자가 명시적으로 켜야 자동 트리거가 동작한다.
 * {@code approvalRequired} 는 기본 true 로, 스케줄러는 판단까지만 자동화하고 실제 체결은
 * 사용자 승인을 거치도록 한다(자동 매매 방지).
 */
@ConfigurationProperties(prefix = "libra.scheduler")
public record SchedulerProperties(
    boolean enabled,
    String dailyCron,
    String zone,
    String query,
    String depth,
    Boolean approvalRequired,
    Integer deadlineSeconds
) {

    public SchedulerProperties {
        if (dailyCron == null || dailyCron.isBlank()) {
            dailyCron = "0 30 8 * * MON-FRI";
        }
        if (zone == null || zone.isBlank()) {
            zone = "Asia/Seoul";
        }
        if (query == null || query.isBlank()) {
            query = "정기 포트폴리오 점검 - 목표 비중 대비 이탈, 거래비용, 최신 뉴스와 공시를 종합해 지금 리밸런싱이 필요한지 판단해줘.";
        }
        if (depth == null || depth.isBlank()) {
            depth = "medium";
        }
        if (approvalRequired == null) {
            approvalRequired = true;
        }
    }
}
