alter table portfolio_snapshots add column user_id varchar(36) null;
create index ix_portfolio_snapshots_user_created on portfolio_snapshots (user_id, created_at);
alter table portfolio_snapshots
    add constraint fk_portfolio_snapshots_user
    foreign key (user_id) references users (id)
    on delete cascade;

alter table decision_runs add column user_id varchar(36) null;
create index ix_decision_runs_user_created on decision_runs (user_id, created_at);
alter table decision_runs
    add constraint fk_decision_runs_user
    foreign key (user_id) references users (id)
    on delete cascade;
