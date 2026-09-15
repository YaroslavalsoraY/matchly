-- Настройки приложения, изменяемые администратором во время работы (ключ-значение).
CREATE TABLE app_settings (
    setting_key   VARCHAR(50)              PRIMARY KEY,
    setting_value VARCHAR(255)             NOT NULL,
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL
);

INSERT INTO app_settings (setting_key, setting_value, updated_at)
VALUES ('default_strategy', 'HYBRID', CURRENT_TIMESTAMP);
