create table APG_Email
(
    id          bigint identity
        primary key,
    user_id     bigint,
    from_send   nvarchar(320),
    to_send     nvarchar(320),
    subject     nvarchar(255),
    flag        bit,
    create_time datetime default sysdatetime(),
    update_time datetime default sysdatetime()
)
go