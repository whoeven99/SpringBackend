create table SubscriptionPlans
(
    plan_id                int identity
        primary key,
    plan_name              nvarchar(255)                      not null,
    description            nvarchar(max),
    price                  float,
    max_translations_month int,
    create_at              datetime2(0) default sysdatetime() not null,
    update_at              datetime2(0) default sysdatetime() not null,
    every_month_token      int
)
go