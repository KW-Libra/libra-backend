create table portfolio_snapshots (
    id bigint not null auto_increment,
    source varchar(64) not null,
    generated_at datetime(6) not null,
    snapshot_payload longtext not null,
    created_at datetime(6) not null,
    primary key (id)
);

create index ix_portfolio_snapshots_created_at on portfolio_snapshots (created_at);
create index ix_portfolio_snapshots_source on portfolio_snapshots (source);
