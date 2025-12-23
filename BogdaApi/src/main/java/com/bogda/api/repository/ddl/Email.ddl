create table Email
(
    id         int identity
        primary key,
    shop_name  nvarchar(255),
    from_send  nvarchar(320),
    to_send    nvarchar(320),
    subject    nvarchar(255),
    flag       tinyint,
    createTime datetime default getdate(),
    updateTime datetime default getdate()
)
go
