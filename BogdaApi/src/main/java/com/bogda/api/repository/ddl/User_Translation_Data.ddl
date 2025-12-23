create table User_Translation_Data
(
    task_id    uniqueidentifier default newid() not null
        primary key,
    status     int                              not null,
    payload    nvarchar(max),
    shop_name  nvarchar(255)                    not null,
    updated_at datetime         default getutcdate(),
    created_at datetime         default getutcdate()
)
go

create index IX_UserTranslation_ShopName_Status_CreatedAt
    on User_Translation_Data (status asc, shop_name asc, created_at desc)
go

create index IX_User_Translation_Data_ShopName_Status
    on User_Translation_Data (shop_name, status)
go

