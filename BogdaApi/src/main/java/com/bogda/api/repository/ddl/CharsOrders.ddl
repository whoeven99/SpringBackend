create table CharsOrders
(
    id               nvarchar(256) not null
        primary key,
    shop_name        nvarchar(256) not null,
    amount           float         not null,
    created_at       datetime      not null,
    name             nvarchar(256) not null,
    status           nvarchar(50)  not null,
    confirmation_url nvarchar(256) not null,
    created_date     datetime default getutcdate(),
    updated_date     datetime default getutcdate()
)
go