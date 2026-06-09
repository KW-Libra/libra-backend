-- Persisted agent deliberation history so the History tab survives refresh
-- and shows real past sessions instead of in-memory/demo data.

CREATE TABLE agent_runs (
    id             uuid PRIMARY KEY,
    user_id        uuid NOT NULL REFERENCES users (id),
    thread_id      varchar(80),
    status         varchar(20) NOT NULL DEFAULT 'RUNNING',
    query          text,
    trigger        varchar(20),
    final_decision varchar(40),
    final_branch   varchar(60),
    summary        text,
    event_count    integer NOT NULL DEFAULT 0,
    trace_id       varchar(80),
    completed_at   timestamptz,
    created_at     timestamptz NOT NULL,
    updated_at     timestamptz NOT NULL
);

CREATE INDEX idx_agent_runs_user_created ON agent_runs (user_id, created_at DESC);
CREATE INDEX idx_agent_runs_thread ON agent_runs (thread_id);

CREATE TABLE agent_run_events (
    id          uuid PRIMARY KEY,
    run_id      uuid NOT NULL REFERENCES agent_runs (id) ON DELETE CASCADE,
    event_index integer NOT NULL,
    event_type  varchar(40) NOT NULL,
    event_data  text,
    created_at  timestamptz NOT NULL
);

CREATE INDEX idx_agent_run_events_run ON agent_run_events (run_id, event_index);
