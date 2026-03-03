-- transactions extra columns
ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS recurrence_interval VARCHAR(50),
    ADD COLUMN IF NOT EXISTS recurrence_end_date DATE;

-- categories GST column
ALTER TABLE categories
    ADD COLUMN IF NOT EXISTS gst_rate DECIMAL(5,2);

-- recurring_transactions
CREATE TABLE IF NOT EXISTS recurring_transactions (
                                                      id                    BIGSERIAL PRIMARY KEY,
                                                      company_id            BIGINT NOT NULL REFERENCES companies(id),
                                                      parent_transaction_id BIGINT REFERENCES transactions(id),
                                                      category_id           BIGINT REFERENCES categories(id),
                                                      account               VARCHAR(100),
                                                      amount                DECIMAL(15,2) NOT NULL,
                                                      type                  VARCHAR(10) NOT NULL,
                                                      description           VARCHAR(500),
                                                      recurrence_interval   VARCHAR(50) NOT NULL,
                                                      next_due_date         DATE,
                                                      recurrence_end_date   DATE,
                                                      is_active             BOOLEAN DEFAULT TRUE,
                                                      created_at            TIMESTAMP DEFAULT NOW(),
                                                      updated_at            TIMESTAMP DEFAULT NOW()
);

-- tax_rates
CREATE TABLE IF NOT EXISTS tax_rates (
                                         id          BIGSERIAL PRIMARY KEY,
                                         company_id  BIGINT REFERENCES companies(id),
                                         name        VARCHAR(100) NOT NULL,
                                         gst_rate    DECIMAL(5,2) NOT NULL,
                                         is_default  BOOLEAN DEFAULT FALSE,
                                         is_active   BOOLEAN DEFAULT TRUE
);

ALTER TABLE categories ADD COLUMN IF NOT EXISTS gst_rate DECIMAL(5,2);

ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS parent_transaction_id BIGINT REFERENCES transactions(id),
    ADD COLUMN IF NOT EXISTS account VARCHAR(100);