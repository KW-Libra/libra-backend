create table decision_runs (
    id varchar(36) not null,
    thread_id varchar(128) not null,
    query text not null,
    model varchar(128) not null,
    trigger_type varchar(32) not null,
    decision varchar(64) not null,
    urgency varchar(32) not null,
    confidence decimal(6, 5) not null,
    consensus_score decimal(7, 5) not null,
    divergence_score decimal(6, 5) not null,
    needs_trade_evaluation boolean not null,
    follow_up_at datetime(6) null,
    feedback_checkpoint_at datetime(6) null,
    portfolio_snapshot longtext not null,
    request_payload longtext not null,
    result_payload longtext not null,
    knowledge_sources longtext not null,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    primary key (id)
);

create index ix_decision_runs_thread_id on decision_runs (thread_id);
create index ix_decision_runs_created_at on decision_runs (created_at);
create index ix_decision_runs_decision on decision_runs (decision);

create table agent_signals (
    id bigint not null auto_increment,
    decision_run_id varchar(36) not null,
    agent_id varchar(64) not null,
    opinion_id varchar(128) not null,
    turn_number integer not null,
    verdict varchar(64) not null,
    direction decimal(7, 5) not null,
    strength decimal(6, 5) not null,
    urgency varchar(32) not null,
    confidence decimal(6, 5) not null,
    signal_score decimal(7, 5) null,
    source_trust decimal(6, 5) null,
    event_type varchar(64) null,
    horizon varchar(64) null,
    focus_tickers text not null,
    evidence longtext not null,
    tools_called longtext not null,
    reasoning text not null,
    limits_acknowledged text null,
    created_at datetime(6) not null,
    primary key (id),
    constraint fk_agent_signals_decision_run
        foreign key (decision_run_id) references decision_runs (id)
        on delete cascade
);

create index ix_agent_signals_run_agent on agent_signals (decision_run_id, agent_id);
create index ix_agent_signals_signal_score on agent_signals (signal_score);

create table rebalance_plan_items (
    id bigint not null auto_increment,
    decision_run_id varchar(36) not null,
    ticker varchar(32) not null,
    weight_delta decimal(8, 6) not null,
    created_at datetime(6) not null,
    primary key (id),
    constraint uk_rebalance_plan_run_ticker unique (decision_run_id, ticker),
    constraint fk_rebalance_plan_items_decision_run
        foreign key (decision_run_id) references decision_runs (id)
        on delete cascade
);

create table user_feedback (
    id bigint not null auto_increment,
    decision_run_id varchar(36) not null,
    response varchar(32) not null,
    reason text null,
    created_at datetime(6) not null,
    primary key (id),
    constraint fk_user_feedback_decision_run
        foreign key (decision_run_id) references decision_runs (id)
        on delete cascade
);

create index ix_user_feedback_run on user_feedback (decision_run_id);

create table executions (
    id bigint not null auto_increment,
    decision_run_id varchar(36) not null,
    ticker varchar(32) not null,
    side varchar(8) not null,
    quantity decimal(20, 6) null,
    price_krw decimal(20, 4) null,
    amount_krw decimal(20, 4) null,
    executed_at datetime(6) null,
    status varchar(32) not null,
    raw_payload longtext not null,
    created_at datetime(6) not null,
    primary key (id),
    constraint fk_executions_decision_run
        foreign key (decision_run_id) references decision_runs (id)
        on delete cascade
);

create index ix_executions_run on executions (decision_run_id);

create table evaluations (
    id bigint not null auto_increment,
    decision_run_id varchar(36) not null,
    horizon varchar(16) not null,
    evaluated_at datetime(6) not null,
    direction_accuracy boolean null,
    timing_accuracy decimal(6, 5) null,
    magnitude_error decimal(12, 6) null,
    cost_efficiency decimal(12, 6) null,
    fast_track_accuracy boolean null,
    verdict varchar(64) not null,
    notes text null,
    metrics_payload longtext not null,
    created_at datetime(6) not null,
    primary key (id),
    constraint uk_evaluations_run_horizon unique (decision_run_id, horizon),
    constraint fk_evaluations_decision_run
        foreign key (decision_run_id) references decision_runs (id)
        on delete cascade
);

create index ix_evaluations_evaluated_at on evaluations (evaluated_at);
