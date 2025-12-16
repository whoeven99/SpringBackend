create table PC_Orders
(
    id               int identity
        primary key,
    order_id         nvarchar(255) default '' not null,
    shop_name        nvarchar(255)            not null,
    amount           float                    not null,
    name             nvarchar(255) default '' not null,
    status           nvarchar(50)  default '' not null,
    confirmation_url nvarchar(256) default '' not null,
    is_deleted       bit           default 0,
    created_at       datetime      default getdate(),
    updated_at       datetime      default getdate(),
    constraint UQ_PC_Orders_ShopName_OrderId
        unique (shop_name, order_id)
)
go

create index IX_PC_Orders_OrderId_ShopName_Status_IsDeleted_CreatedAt
    on PC_Orders (order_id, shop_name, status, is_deleted, created_at)
go