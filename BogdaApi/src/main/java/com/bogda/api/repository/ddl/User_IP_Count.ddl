create table User_IP_Count
(
    id          int identity
        primary key,
    shop_name   nvarchar(255)            not null,
    count_type  nvarchar(255) default '' not null,
    count_value int           default 0  not null,
    is_deleted  bit           default 0  not null,
    created_at  datetime      default getdate(),
    updated_at  datetime      default getdate()
)
go

create unique index UX_User_IP_Count_UniqueFields
    on User_IP_Count (shop_name, count_type, is_deleted)
go