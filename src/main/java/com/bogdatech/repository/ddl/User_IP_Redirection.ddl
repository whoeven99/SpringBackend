create table User_IP_Redirection
(
    id            int identity
        primary key,
    shop_name     nvarchar(255)            not null,
    region        nvarchar(255) default '' not null,
    language_code nvarchar(255) default '' not null,
    currency_code nvarchar(255) default '' not null,
    is_deleted    bit           default 0  not null,
    created_at    datetime      default getdate(),
    updated_at    datetime      default getdate()
)
go

create unique index UX_User_IP_Redirection_UniqueFields
    on User_IP_Redirection (shop_name, region, language_code, currency_code, is_deleted)
go