create table Translates
(
    id             int identity
        primary key,
    source         nvarchar(100)                      not null,
    shop_name      nvarchar(255)                      not null,
    access_token   nvarchar(50)                       not null,
    status         tinyint      default 0             not null,
    create_at      datetime2(0) default sysdatetime() not null,
    update_at      datetime2(0) default sysdatetime() not null,
    target         nvarchar(10)                       not null,
    resource_type  nvarchar(255),
    auto_translate bit
)
go

create index idx_translates_shop_name
    on Translates (shop_name)
go

create unique index UQ_Translates_ShopName_Source_Target
    on Translates (shop_name, source, target)
go