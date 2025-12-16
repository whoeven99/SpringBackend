create table UserIp
(
    id           int identity
        primary key,
    shop_name    nvarchar(255)      not null
        unique,
    times        bigint   default 0 not null,
    first_email  bit      default 0 not null,
    second_email bit      default 0 not null,
    created_at   datetime default getdate(),
    updated_at   datetime default getdate(),
    all_times    bigint   default 0 not null
)
go