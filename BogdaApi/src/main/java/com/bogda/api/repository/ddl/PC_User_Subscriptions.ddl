create table PC_User_Subscriptions
(
    id         int identity
        primary key,
    shop_name  nvarchar(255)      not null
        constraint UQ_PC_User_Subscriptions_ShopName
            unique,
    plan_id    int      default 0 not null,
    start_date datetime,
    end_date   datetime,
    fee_type   int      default 0 not null,
    is_deleted bit      default 0,
    created_at datetime default getdate(),
    updated_at datetime default getdate()
)
go