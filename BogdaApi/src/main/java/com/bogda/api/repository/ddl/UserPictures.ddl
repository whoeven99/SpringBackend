create table UserPictures
(
    id                     int identity
        primary key,
    shop_name              nvarchar(255)      not null,
    image_id               nvarchar(255)      not null,
    image_before_url       nvarchar(1024)     not null,
    image_after_url        nvarchar(1024),
    alt_before_translation nvarchar(200),
    alt_after_translation  nvarchar(200),
    language_code          nvarchar(10)       not null,
    is_delete              bit      default 0 not null,
    created_at             datetime default getdate(),
    updated_at             datetime default getdate()
)
go

