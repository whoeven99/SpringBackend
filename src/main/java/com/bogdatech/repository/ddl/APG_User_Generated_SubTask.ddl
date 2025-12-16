create table APG_User_Generated_SubTask
(
    subtask_id  uniqueidentifier default newid() not null
        primary key,
    user_id     bigint                           not null,
    status      int                              not null,
    payload     nvarchar(max)                    not null,
    create_time datetime         default sysdatetime(),
    update_time datetime         default sysdatetime()
)
go