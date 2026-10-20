-- 배송 완료 시각과 구매확정 시각이다. 해당 상태로 바뀔 때만 채운다.
ALTER TABLE orders
    ADD COLUMN delivered_at TIMESTAMPTZ,
    ADD COLUMN confirmed_at TIMESTAMPTZ;

-- 자동 구매확정은 배송 완료 주문만 배송 완료 시각 순으로 훑는다.
CREATE INDEX idx_orders_delivered_at ON orders (delivered_at) WHERE status = 'DELIVERED';
-- 등급 배치는 회원별로 기간 안에 확정된 주문만 읽는다.
CREATE INDEX idx_orders_member_confirmed_at ON orders (member_id, confirmed_at) WHERE status = 'CONFIRMED';
-- 관리자 주문 목록의 상태 필터는 최신 순(id 내림차순)으로 읽는다.
CREATE INDEX idx_orders_status_id_desc ON orders (status, id DESC);
