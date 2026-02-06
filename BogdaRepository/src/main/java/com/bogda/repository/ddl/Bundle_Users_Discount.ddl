create table Bundle_Users_Discount
(
    id            int identity primary key,
    shop_name     nvarchar(255)      not null,
    discount_id   nvarchar(255)      not null,
    discount_name nvarchar(255)      not null,
    status        BIT      DEFAULT 0 NOT NULL,
    exposure_pv INT NOT NULL DEFAULT 0,           -- 曝光 pv
    add_to_cart_pv INT NOT NULL DEFAULT 0,         -- 加购 pv
    checkout_started_pv INT NOT NULL DEFAULT 0,    -- 下单 pv
    gmv FLOAT NOT NULL DEFAULT 0,          -- 订单金额
    is_deleted    BIT      DEFAULT 0 NOT NULL,
    updated_at    DATETIME DEFAULT GETUTCDATE(),
    created_at    DATETIME DEFAULT GETUTCDATE()
)
go

CREATE UNIQUE INDEX UX_Bundle_Users_Discount_shop_discount
    ON Bundle_Users_Discount (shop_name, discount_id, discount_name);

CREATE UNIQUE INDEX UX_Bundle_Users_Discount_shop_discount_name
    ON Bundle_Users_Discount (shop_name, discount_name);
go