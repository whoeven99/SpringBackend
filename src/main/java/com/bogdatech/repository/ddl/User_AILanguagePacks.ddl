create table User_AILanguagePacks
(
    shop_name     nvarchar(255) not null
        constraint User_AILanguagePacks_pk
            unique,
    pack_id       int           not null,
    create_time   datetime default getdate(),
    update_time   datetime default getdate(),
    language_pack nvarchar(255)
)
go