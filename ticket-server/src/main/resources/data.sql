-- Train 초기 데이터
INSERT INTO train (train_id, train_serial, departure_time, created_at, modified_at)
VALUES (1, 'KTX-001', '2026-05-01 10:00:00', NOW(), NOW())
ON DUPLICATE KEY UPDATE train_serial = train_serial;

-- Seat 초기 데이터 (20건: 1A~4A, 1B~4B, 1C~4C, 1D~4D, 1E~4E)
INSERT IGNORE INTO seats (train_id, seat_number, created_at, modified_at) VALUES (1, '1A', NOW(), NOW());
INSERT IGNORE INTO seats (train_id, seat_number, created_at, modified_at) VALUES (1, '2A', NOW(), NOW());
INSERT IGNORE INTO seats (train_id, seat_number, created_at, modified_at) VALUES (1, '3A', NOW(), NOW());
INSERT IGNORE INTO seats (train_id, seat_number, created_at, modified_at) VALUES (1, '4A', NOW(), NOW());
INSERT IGNORE INTO seats (train_id, seat_number, created_at, modified_at) VALUES (1, '1B', NOW(), NOW());
INSERT IGNORE INTO seats (train_id, seat_number, created_at, modified_at) VALUES (1, '2B', NOW(), NOW());
INSERT IGNORE INTO seats (train_id, seat_number, created_at, modified_at) VALUES (1, '3B', NOW(), NOW());
INSERT IGNORE INTO seats (train_id, seat_number, created_at, modified_at) VALUES (1, '4B', NOW(), NOW());
INSERT IGNORE INTO seats (train_id, seat_number, created_at, modified_at) VALUES (1, '1C', NOW(), NOW());
INSERT IGNORE INTO seats (train_id, seat_number, created_at, modified_at) VALUES (1, '2C', NOW(), NOW());
INSERT IGNORE INTO seats (train_id, seat_number, created_at, modified_at) VALUES (1, '3C', NOW(), NOW());
INSERT IGNORE INTO seats (train_id, seat_number, created_at, modified_at) VALUES (1, '4C', NOW(), NOW());
INSERT IGNORE INTO seats (train_id, seat_number, created_at, modified_at) VALUES (1, '1D', NOW(), NOW());
INSERT IGNORE INTO seats (train_id, seat_number, created_at, modified_at) VALUES (1, '2D', NOW(), NOW());
INSERT IGNORE INTO seats (train_id, seat_number, created_at, modified_at) VALUES (1, '3D', NOW(), NOW());
INSERT IGNORE INTO seats (train_id, seat_number, created_at, modified_at) VALUES (1, '4D', NOW(), NOW());
INSERT IGNORE INTO seats (train_id, seat_number, created_at, modified_at) VALUES (1, '1E', NOW(), NOW());
INSERT IGNORE INTO seats (train_id, seat_number, created_at, modified_at) VALUES (1, '2E', NOW(), NOW());
INSERT IGNORE INTO seats (train_id, seat_number, created_at, modified_at) VALUES (1, '3E', NOW(), NOW());
INSERT IGNORE INTO seats (train_id, seat_number, created_at, modified_at) VALUES (1, '4E', NOW(), NOW());
