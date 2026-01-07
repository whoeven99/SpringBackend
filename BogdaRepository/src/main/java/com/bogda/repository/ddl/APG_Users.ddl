create table APG_Users
(
    id             bigint identity
        primary key,
    shop_name      nvarchar(255) not null
        constraint APG_Users_pk
            unique,
    access_token   nvarchar(38)  not null,
    email          nvarchar(320),
    phone          nvarchar(15),
    real_address   nvarchar(255),
    ip_address     nvarchar(255),
    user_tag       nvarchar(255),
    first_name     nvarchar(255),
    last_name      nvarchar(255),
    create_at      datetime default getutcdate(),
    update_at      datetime default getutcdate(),
    uninstall_time datetime,
    login_time     datetime
)
go