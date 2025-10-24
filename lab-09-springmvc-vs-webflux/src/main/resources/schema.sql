-- Lab-09 R2DBC 数据库初始化脚本

-- 用户表
CREATE TABLE IF NOT EXISTS users (
    id       BIGINT       PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(100) NOT NULL UNIQUE,
    email    VARCHAR(100) NOT NULL UNIQUE,
    age      INTEGER,
    bio      TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建索引优化查询性能
CREATE INDEX idx_users_age ON users(age);
CREATE INDEX idx_users_email ON users(email);

-- 初始化测试数据
INSERT INTO users (username, email, age, bio) VALUES
    ('alice', 'alice@example.com', 25, '软件工程师'),
    ('bob', 'bob@example.com', 30, '产品经理'),
    ('charlie', 'charlie@example.com', 28, '设计师'),
    ('diana', 'diana@example.com', 35, '技术主管'),
    ('eve', 'eve@example.com', 22, '实习生');
