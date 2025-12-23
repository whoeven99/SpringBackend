create table UserIPSwitch
(
    id           bigint identity
        primary key,
    shop_name    nvarchar(255),
    switch_id    int,
    created_date datetime default getutcdate(),
    updated_date datetime default getutcdate()
)
go

