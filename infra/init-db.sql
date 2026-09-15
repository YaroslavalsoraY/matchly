-- Создание роли и базы данных для Matchly. Выполнять от суперпользователя postgres:
--   Linux:   psql -U postgres -f infra/init-db.sql
--   Windows: "C:\Program Files\PostgreSQL\16\bin\psql.exe" -U postgres -f infra\init-db.sql
-- Скрипт идемпотентен: повторный запуск ничего не ломает.
SELECT 'CREATE ROLE matchly LOGIN PASSWORD ''matchly'''
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'matchly')\gexec

SELECT 'CREATE DATABASE matchly_db OWNER matchly ENCODING ''UTF8'' TEMPLATE template0'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'matchly_db')\gexec
