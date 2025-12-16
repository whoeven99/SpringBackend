create table UserSubscriptions
(
    subscription_id int identity
        primary key,
    shop_name       nvarchar(255)                      not null
        constraint UserSubscriptions_pk
            unique
        constraint UserSubscriptions_pk2
            unique,
    plan_id         int                                not null,
    status          int,
    start_date      datetime2(0),
    end_date        datetime2(0),
    create_at       datetime2(0) default sysdatetime() not null,
    update_at       datetime2(0) default sysdatetime() not null,
    fee_type        int
)
go

