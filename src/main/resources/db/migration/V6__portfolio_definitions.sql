create table portfolio_definitions (
    id bigint not null auto_increment,
    user_id varchar(36) not null,
    definition_payload longtext not null,
    created_at datetime(6) not null,
    primary key (id)
);

create index ix_portfolio_definitions_user_created_at on portfolio_definitions (user_id, created_at);
