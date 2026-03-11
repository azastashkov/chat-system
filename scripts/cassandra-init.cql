CREATE KEYSPACE IF NOT EXISTS chat_system
WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};

USE chat_system;

CREATE TABLE IF NOT EXISTS users (
    user_id UUID PRIMARY KEY,
    username TEXT,
    password_hash TEXT,
    display_name TEXT,
    created_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS users_by_username (
    username TEXT PRIMARY KEY,
    user_id UUID,
    password_hash TEXT,
    display_name TEXT,
    created_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS channels (
    channel_id UUID PRIMARY KEY,
    channel_type TEXT,
    name TEXT,
    created_by UUID,
    created_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS channel_members (
    channel_id UUID,
    user_id UUID,
    role TEXT,
    joined_at TIMESTAMP,
    PRIMARY KEY (channel_id, user_id)
);

CREATE TABLE IF NOT EXISTS user_channels (
    user_id UUID,
    channel_id UUID,
    channel_type TEXT,
    channel_name TEXT,
    joined_at TIMESTAMP,
    PRIMARY KEY (user_id, joined_at)
) WITH CLUSTERING ORDER BY (joined_at DESC);

CREATE TABLE IF NOT EXISTS messages (
    channel_id UUID,
    message_id TIMEUUID,
    sender_id UUID,
    sender_name TEXT,
    content TEXT,
    message_type TEXT,
    created_at TIMESTAMP,
    PRIMARY KEY (channel_id, message_id)
) WITH CLUSTERING ORDER BY (message_id DESC);

CREATE TABLE IF NOT EXISTS notifications (
    user_id UUID,
    notification_id TIMEUUID,
    type TEXT,
    payload TEXT,
    is_read BOOLEAN,
    created_at TIMESTAMP,
    PRIMARY KEY (user_id, notification_id)
) WITH CLUSTERING ORDER BY (notification_id DESC);
