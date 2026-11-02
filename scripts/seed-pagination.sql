-- 로컬 화면 확인 전용. Flyway 마이그레이션에 포함하지 않는다.
BEGIN;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM brew_market.users) THEN
        RAISE EXCEPTION '먼저 구글 로그인으로 회원을 한 명 생성해 주세요.';
    END IF;
END $$;

-- 같은 스크립트를 다시 실행해도 테스트 글이 중복 생성되지 않게 한다.
INSERT INTO brew_market.posts (seller_id, title, description, price, status, created_at, version)
SELECT
    (SELECT id FROM brew_market.users ORDER BY id LIMIT 1),
    '[더보기 확인] 물건 ' || n,
    '로컬 더보기 확인용 데이터입니다. 실제 판매글이 아닙니다.',
    n * 1000,
    'FOR_SALE',
    -- 같은 시각의 글도 만들어 번호 기준 정렬을 함께 확인한다.
    CURRENT_TIMESTAMP - ((n / 3) * INTERVAL '1 minute'),
    0
FROM generate_series(1, 45) AS n
WHERE NOT EXISTS (
    SELECT 1 FROM brew_market.posts
    WHERE title = '[더보기 확인] 물건 ' || n
      AND description = '로컬 더보기 확인용 데이터입니다. 실제 판매글이 아닙니다.'
);

COMMIT;
