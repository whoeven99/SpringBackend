create table PC_Subscriptions
(
    id                int identity
        primary key,
    plan_id           int           default 0  not null,
    plan_name         nvarchar(255)            not null,
    description       nvarchar(255) default '' not null,
    price             float                    not null,
    every_month_token int           default 0  not null,
    is_deleted        bit           default 0,
    created_at        datetime      default getdate(),
    updated_at        datetime      default getdate()
)
go

create index IX_PC_Subscriptions_PlanId_PlanName
    on PC_Subscriptions (plan_id, plan_name)
go