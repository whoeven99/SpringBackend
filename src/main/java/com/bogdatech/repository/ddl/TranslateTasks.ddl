create table TranslateTasks
(
    task_id    uniqueidentifier default newid() not null
        primary key,
    status     int                              not null,
    payload    nvarchar(max),
    created_at datetime         default getutcdate(),
    updated_at datetime         default getutcdate(),
    shop_name  nvarchar(255)                    not null,
    all_tasks  int
)
go

create index IX_TranslateTasks_Status_ShopName
    on TranslateTasks (status, shop_name)
go