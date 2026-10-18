-- 기존 회원은 모두 BABY로 시작한다. 등급 재계산은 배치가 맡는다.
ALTER TABLE member ADD COLUMN grade VARCHAR(20) NOT NULL DEFAULT 'BABY';
