CREATE TABLE IF NOT EXISTS ecommerce_advice (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    shop_name VARCHAR(255) NOT NULL,
    advice_type VARCHAR(50) NOT NULL,
    advice_content TEXT NOT NULL,
    target_resource_id VARCHAR(255),
    target_resource_type VARCHAR(50),
    status VARCHAR(20) DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_shop (shop_name),
    INDEX idx_status (status)
);

CREATE TABLE IF NOT EXISTS advice_feedback (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    advice_id BIGINT NOT NULL,
    shop_name VARCHAR(255) NOT NULL,
    feedback_type VARCHAR(20) NOT NULL,
    rating INT,
    notes TEXT,
    feedback_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (advice_id) REFERENCES ecommerce_advice(id),
    INDEX idx_advice (advice_id),
    INDEX idx_shop (shop_name)
);

CREATE TABLE IF NOT EXISTS advice_performance (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    advice_id BIGINT NOT NULL,
    metric_name VARCHAR(50) NOT NULL,
    metric_before DECIMAL(10, 2),
    metric_after DECIMAL(10, 2),
    observation_window_days INT,
    recorded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (advice_id) REFERENCES ecommerce_advice(id),
    INDEX idx_advice (advice_id)
);
