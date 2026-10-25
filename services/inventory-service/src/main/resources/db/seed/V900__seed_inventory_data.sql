-- Demo stock, dev profile only. SKUs match catalog-service's seed so the two line up.
--
-- LAP-0002 is deliberately down to 1, and MON-0001 to 0: the first makes the
-- overselling race reproducible by hand, the second exercises the out-of-stock path.

INSERT INTO stock_items (sku, quantity_on_hand, quantity_reserved, reorder_level, updated_at, version) VALUES
    ('LAP-0001', 25, 0,  5, CURRENT_TIMESTAMP, 0),
    ('LAP-0002',  1, 0,  5, CURRENT_TIMESTAMP, 0),
    ('HPH-0001', 60, 0, 10, CURRENT_TIMESTAMP, 0),
    ('HPH-0002', 14, 0, 10, CURRENT_TIMESTAMP, 0),
    ('KBD-0001', 40, 0, 10, CURRENT_TIMESTAMP, 0),
    ('KBD-0002',  8, 0, 10, CURRENT_TIMESTAMP, 0),
    ('MON-0001',  0, 0,  5, CURRENT_TIMESTAMP, 0),
    ('MON-0002', 12, 0,  5, CURRENT_TIMESTAMP, 0);

INSERT INTO stock_ledger (sku, change, reason, reference, occurred_at)
SELECT sku, quantity_on_hand, 'RECEIPT', NULL, CURRENT_TIMESTAMP
  FROM stock_items WHERE quantity_on_hand > 0;
