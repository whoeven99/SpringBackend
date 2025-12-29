create table APG_Chars_Order
(
    id               nvarchar(256) not null
        primary key,
    user_id          bigint        not null,
    amount           float         not null,
    created_at       datetime      not null,
    name             nvarchar(256) not null,
    status           nvarchar(50)  not null,
    confirmation_url nvarchar(256) not null,
    create_time      datetime default sysdatetime(),
    update_time      datetime default sysdatetime()
)
go