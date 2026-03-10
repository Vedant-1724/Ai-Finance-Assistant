DO $$
DECLARE
    tables_to_truncate TEXT;
BEGIN
    SELECT string_agg(format('%I.%I', schemaname, tablename), ', ')
    INTO tables_to_truncate
    FROM pg_tables
    WHERE schemaname = 'public'
      AND tablename <> 'flyway_schema_history';

    IF tables_to_truncate IS NOT NULL THEN
        EXECUTE 'TRUNCATE TABLE ' || tables_to_truncate || ' RESTART IDENTITY CASCADE';
    END IF;
END $$;
