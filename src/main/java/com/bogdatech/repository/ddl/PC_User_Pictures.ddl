create table PC_User_Pictures
(
    id                     int identity
        primary key,
    shop_name              nvarchar(255)      not null,
    image_id               nvarchar(255)      not null,
    image_before_url       nvarchar(1024)     not null,
    image_after_url        nvarchar(1024),
    alt_before_translation nvarchar(1000),
    alt_after_translation  nvarchar(1000),
    language_code          nvarchar(10)       not null,
    is_deleted             int      default 0 not null,
    create_at              datetime default getdate(),
    update_at              datetime default getdate(),
    product_id             nvarchar(255),
    media_id               nvarchar(255)
)
go

create index IX_PC_User_Pictures_MainIndex
    on PC_User_Pictures (shop_name, product_id, image_id, image_before_url, alt_before_translation, language_code,
                         is_deleted)
go