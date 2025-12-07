-- 테스트 유저 데이터 삽입
-- 이메일: ss@naver.com, 이름: 홍길동, 비밀번호: 111111 (암호화됨)
INSERT INTO user (user_name, email, password, created_at, updated_at)
VALUES ('홍길동', 'ss@naver.com', '$2a$12$mwXEIXTtFesoB6vXFUObsOZt8FF3i7PuQtPtRhuu586Jc0.8m4eEW', NOW(), NOW());

-- 포인트 데이터 삽입 (초기 잔액 0원)
INSERT INTO point (user_id, balance, created_at, updated_at)
VALUES (1, 0, NOW(), NOW());

-- 콘서트 데이터 삽입
INSERT INTO concert (title, description, created_at, updated_at)
VALUES ('블랙핑크', '블랙핑크 콘서트!!!!!!!!!!!!!', NOW(), NOW());

-- 콘서트 스케줄 4개 삽입 (오늘로부터 1주, 2주, 3주, 4주 후, 각각 다른 시간)
INSERT INTO concert_schedule (concert_id, concert_date, created_at, updated_at)
VALUES
    (1, DATE_ADD(NOW(), INTERVAL 7 DAY) + INTERVAL 19 HOUR, NOW(), NOW()),   -- 7일 후 19:00
    (1, DATE_ADD(NOW(), INTERVAL 14 DAY) + INTERVAL 18 HOUR, NOW(), NOW()),  -- 14일 후 18:00
    (1, DATE_ADD(NOW(), INTERVAL 21 DAY) + INTERVAL 20 HOUR, NOW(), NOW()),  -- 21일 후 20:00
    (1, DATE_ADD(NOW(), INTERVAL 28 DAY) + INTERVAL 19 HOUR + INTERVAL 30 MINUTE, NOW(), NOW()); -- 28일 후 19:30

-- 좌석 100개 삽입 (스케줄 ID 1)
-- VIP석 (1-30번): 150,000원
INSERT INTO seat (concert_schedule_id, seat_number, seat_status, price, created_at, updated_at)
VALUES
    (1, 1, 'AVAILABLE', 150000, NOW(), NOW()),
    (1, 2, 'AVAILABLE', 150000, NOW(), NOW()),
    (1, 3, 'AVAILABLE', 150000, NOW(), NOW()),
    (1, 4, 'AVAILABLE', 150000, NOW(), NOW()),
    (1, 5, 'AVAILABLE', 150000, NOW(), NOW()),
    (1, 6, 'AVAILABLE', 150000, NOW(), NOW()),
    (1, 7, 'AVAILABLE', 150000, NOW(), NOW()),
    (1, 8, 'AVAILABLE', 150000, NOW(), NOW()),
    (1, 9, 'AVAILABLE', 150000, NOW(), NOW()),
    (1, 10, 'AVAILABLE', 150000, NOW(), NOW()),
    (1, 11, 'AVAILABLE', 150000, NOW(), NOW()),
    (1, 12, 'AVAILABLE', 150000, NOW(), NOW()),
    (1, 13, 'AVAILABLE', 150000, NOW(), NOW()),
    (1, 14, 'AVAILABLE', 150000, NOW(), NOW()),
    (1, 15, 'AVAILABLE', 150000, NOW(), NOW()),
    (1, 16, 'AVAILABLE', 150000, NOW(), NOW()),
    (1, 17, 'AVAILABLE', 150000, NOW(), NOW()),
    (1, 18, 'AVAILABLE', 150000, NOW(), NOW()),
    (1, 19, 'AVAILABLE', 150000, NOW(), NOW()),
    (1, 20, 'AVAILABLE', 150000, NOW(), NOW()),
    (1, 21, 'AVAILABLE', 150000, NOW(), NOW()),
    (1, 22, 'AVAILABLE', 150000, NOW(), NOW()),
    (1, 23, 'AVAILABLE', 150000, NOW(), NOW()),
    (1, 24, 'AVAILABLE', 150000, NOW(), NOW()),
    (1, 25, 'AVAILABLE', 150000, NOW(), NOW()),
    (1, 26, 'AVAILABLE', 150000, NOW(), NOW()),
    (1, 27, 'AVAILABLE', 150000, NOW(), NOW()),
    (1, 28, 'AVAILABLE', 150000, NOW(), NOW()),
    (1, 29, 'AVAILABLE', 150000, NOW(), NOW()),
    (1, 30, 'AVAILABLE', 150000, NOW(), NOW()),
-- R석 (31-70번): 100,000원
    (1, 31, 'AVAILABLE', 100000, NOW(), NOW()),
    (1, 32, 'AVAILABLE', 100000, NOW(), NOW()),
    (1, 33, 'AVAILABLE', 100000, NOW(), NOW()),
    (1, 34, 'AVAILABLE', 100000, NOW(), NOW()),
    (1, 35, 'AVAILABLE', 100000, NOW(), NOW()),
    (1, 36, 'AVAILABLE', 100000, NOW(), NOW()),
    (1, 37, 'AVAILABLE', 100000, NOW(), NOW()),
    (1, 38, 'AVAILABLE', 100000, NOW(), NOW()),
    (1, 39, 'AVAILABLE', 100000, NOW(), NOW()),
    (1, 40, 'AVAILABLE', 100000, NOW(), NOW()),
    (1, 41, 'AVAILABLE', 100000, NOW(), NOW()),
    (1, 42, 'AVAILABLE', 100000, NOW(), NOW()),
    (1, 43, 'AVAILABLE', 100000, NOW(), NOW()),
    (1, 44, 'AVAILABLE', 100000, NOW(), NOW()),
    (1, 45, 'AVAILABLE', 100000, NOW(), NOW()),
    (1, 46, 'AVAILABLE', 100000, NOW(), NOW()),
    (1, 47, 'AVAILABLE', 100000, NOW(), NOW()),
    (1, 48, 'AVAILABLE', 100000, NOW(), NOW()),
    (1, 49, 'AVAILABLE', 100000, NOW(), NOW()),
    (1, 50, 'AVAILABLE', 100000, NOW(), NOW()),
    (1, 51, 'AVAILABLE', 100000, NOW(), NOW()),
    (1, 52, 'AVAILABLE', 100000, NOW(), NOW()),
    (1, 53, 'AVAILABLE', 100000, NOW(), NOW()),
    (1, 54, 'AVAILABLE', 100000, NOW(), NOW()),
    (1, 55, 'AVAILABLE', 100000, NOW(), NOW()),
    (1, 56, 'AVAILABLE', 100000, NOW(), NOW()),
    (1, 57, 'AVAILABLE', 100000, NOW(), NOW()),
    (1, 58, 'AVAILABLE', 100000, NOW(), NOW()),
    (1, 59, 'AVAILABLE', 100000, NOW(), NOW()),
    (1, 60, 'AVAILABLE', 100000, NOW(), NOW()),
    (1, 61, 'AVAILABLE', 100000, NOW(), NOW()),
    (1, 62, 'AVAILABLE', 100000, NOW(), NOW()),
    (1, 63, 'AVAILABLE', 100000, NOW(), NOW()),
    (1, 64, 'AVAILABLE', 100000, NOW(), NOW()),
    (1, 65, 'AVAILABLE', 100000, NOW(), NOW()),
    (1, 66, 'AVAILABLE', 100000, NOW(), NOW()),
    (1, 67, 'AVAILABLE', 100000, NOW(), NOW()),
    (1, 68, 'AVAILABLE', 100000, NOW(), NOW()),
    (1, 69, 'AVAILABLE', 100000, NOW(), NOW()),
    (1, 70, 'AVAILABLE', 100000, NOW(), NOW()),
-- S석 (71-100번): 70,000원
    (1, 71, 'AVAILABLE', 70000, NOW(), NOW()),
    (1, 72, 'AVAILABLE', 70000, NOW(), NOW()),
    (1, 73, 'AVAILABLE', 70000, NOW(), NOW()),
    (1, 74, 'AVAILABLE', 70000, NOW(), NOW()),
    (1, 75, 'AVAILABLE', 70000, NOW(), NOW()),
    (1, 76, 'AVAILABLE', 70000, NOW(), NOW()),
    (1, 77, 'AVAILABLE', 70000, NOW(), NOW()),
    (1, 78, 'AVAILABLE', 70000, NOW(), NOW()),
    (1, 79, 'AVAILABLE', 70000, NOW(), NOW()),
    (1, 80, 'AVAILABLE', 70000, NOW(), NOW()),
    (1, 81, 'AVAILABLE', 70000, NOW(), NOW()),
    (1, 82, 'AVAILABLE', 70000, NOW(), NOW()),
    (1, 83, 'AVAILABLE', 70000, NOW(), NOW()),
    (1, 84, 'AVAILABLE', 70000, NOW(), NOW()),
    (1, 85, 'AVAILABLE', 70000, NOW(), NOW()),
    (1, 86, 'AVAILABLE', 70000, NOW(), NOW()),
    (1, 87, 'AVAILABLE', 70000, NOW(), NOW()),
    (1, 88, 'AVAILABLE', 70000, NOW(), NOW()),
    (1, 89, 'AVAILABLE', 70000, NOW(), NOW()),
    (1, 90, 'AVAILABLE', 70000, NOW(), NOW()),
    (1, 91, 'AVAILABLE', 70000, NOW(), NOW()),
    (1, 92, 'AVAILABLE', 70000, NOW(), NOW()),
    (1, 93, 'AVAILABLE', 70000, NOW(), NOW()),
    (1, 94, 'AVAILABLE', 70000, NOW(), NOW()),
    (1, 95, 'AVAILABLE', 70000, NOW(), NOW()),
    (1, 96, 'AVAILABLE', 70000, NOW(), NOW()),
    (1, 97, 'AVAILABLE', 70000, NOW(), NOW()),
    (1, 98, 'AVAILABLE', 70000, NOW(), NOW()),
    (1, 99, 'AVAILABLE', 70000, NOW(), NOW()),
    (1, 100, 'AVAILABLE', 70000, NOW(), NOW());

-- 좌석 100개 삽입 (스케줄 ID 2)
INSERT INTO seat (concert_schedule_id, seat_number, seat_status, price, created_at, updated_at)
SELECT 2, seat_number, seat_status, price, NOW(), NOW()
FROM seat WHERE concert_schedule_id = 1;

-- 좌석 100개 삽입 (스케줄 ID 3)
INSERT INTO seat (concert_schedule_id, seat_number, seat_status, price, created_at, updated_at)
SELECT 3, seat_number, seat_status, price, NOW(), NOW()
FROM seat WHERE concert_schedule_id = 1;

-- 좌석 100개 삽입 (스케줄 ID 4)
INSERT INTO seat (concert_schedule_id, seat_number, seat_status, price, created_at, updated_at)
SELECT 4, seat_number, seat_status, price, NOW(), NOW()
FROM seat WHERE concert_schedule_id = 1;
