create table APG_User_Generated_Task
(
    id          bigint identity
        primary key,
    user_id     bigint             not null,
    task_status int      default 0 not null,
    task_model  nvarchar(20)       not null,
    task_data   nvarchar(max),
    create_time datetime default getdate(),
    update_time datetime default getdate()
)
go