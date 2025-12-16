create table APG_User_Plan
(
    id          bigint identity
        primary key,
    user_id     bigint not null
        unique,
    plan_id     bigint not null,
    create_time datetime default getdate(),
    update_time datetime default getdate()
)
go
