create table kis_credentials (
    user_id varchar(36) not null primary key,
    environment varchar(16) not null,
    app_key_ciphertext text not null,
    app_secret_ciphertext text not null,
    account_no_ciphertext text not null,
    product_code varchar(8) not null,
    user_agent varchar(255) null,
    created_at timestamp not null,
    updated_at timestamp not null,
    constraint fk_kis_credentials_user
        foreign key (user_id) references users (id)
        on delete cascade
);

create index ix_kis_credentials_environment on kis_credentials (environment);
