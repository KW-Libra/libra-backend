create table users (
    id varchar(36) not null,
    email varchar(255) not null,
    password_hash varchar(100) null,
    name varchar(120) not null,
    role varchar(32) not null,
    oauth_provider varchar(32) null,
    oauth_provider_id varchar(128) null,
    last_login_at datetime(6) null,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    primary key (id),
    constraint uk_users_email unique (email),
    constraint uk_users_oauth unique (oauth_provider, oauth_provider_id)
);

create index ix_users_role on users (role);

create table refresh_tokens (
    id varchar(36) not null,
    user_id varchar(36) not null,
    token_hash varchar(128) not null,
    issued_at datetime(6) not null,
    expires_at datetime(6) not null,
    revoked_at datetime(6) null,
    rotated_to_id varchar(36) null,
    user_agent varchar(255) null,
    ip varchar(64) null,
    primary key (id),
    constraint uk_refresh_tokens_hash unique (token_hash),
    constraint fk_refresh_tokens_user
        foreign key (user_id) references users (id)
        on delete cascade
);

create index ix_refresh_tokens_user_id on refresh_tokens (user_id);
create index ix_refresh_tokens_expires_at on refresh_tokens (expires_at);
