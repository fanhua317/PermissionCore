#!/bin/sh
set -eu

: "${MYSQL_ROOT_PASSWORD:?MYSQL_ROOT_PASSWORD is required}"
: "${MYSQL_APP_PASSWORD:?MYSQL_APP_PASSWORD is required}"

password_length=${#MYSQL_APP_PASSWORD}
if [ "$password_length" -lt 16 ] || [ "$password_length" -gt 128 ]; then
  echo "MYSQL_APP_PASSWORD must be 16-128 characters" >&2
  exit 1
fi

# Encode the secret as hexadecimal so it is never interpolated as an SQL
# literal. MySQL's QUOTE() creates the final literal before PREPARE/EXECUTE.
password_hex=$(printf '%s' "$MYSQL_APP_PASSWORD" | od -An -v -tx1 | tr -d '[:space:]')
umask 077
sql_file=$(mktemp)
trap 'rm -f "$sql_file"' EXIT HUP INT TERM

cat > "$sql_file" <<SQL
SET @app_password = CONVERT(0x$password_hex USING utf8mb4);
DROP USER IF EXISTS 'permacore_app'@'%';
SET @statement = CONCAT(
  "CREATE USER 'permacore_app'@'%' IDENTIFIED BY ",
  QUOTE(@app_password)
);
PREPARE app_user_statement FROM @statement;
EXECUTE app_user_statement;
DEALLOCATE PREPARE app_user_statement;

GRANT SELECT, INSERT, UPDATE, DELETE ON permacore_iam.* TO 'permacore_app'@'%';
SQL

MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql \
  --protocol=tcp \
  --host=mysql \
  --user=root \
  --database=mysql \
  --default-character-set=utf8mb4 \
  --binary-mode=1 < "$sql_file"
