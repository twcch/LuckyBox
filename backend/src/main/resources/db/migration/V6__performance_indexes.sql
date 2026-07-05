-- Indexes for hot read paths flagged by the backend audit (no schema/logic change).
-- Member order list (payment orders newest-first) and order detail (draw results per order).
CREATE INDEX IF NOT EXISTS idx_payment_orders_user ON payment_orders(user_id, id DESC);
CREATE INDEX IF NOT EXISTS idx_draw_results_order ON draw_results(draw_order_id, result_index);

-- Fairness page + consistency audit + audit summary scan draw_results by campaign in id order.
CREATE INDEX IF NOT EXISTS idx_draw_results_campaign ON draw_results(campaign_id, id);

-- Address lookups by member.
CREATE INDEX IF NOT EXISTS idx_user_addresses_user ON user_addresses(user_id);

-- Prize box (by member + status) and shipment item joins (by shipment).
CREATE INDEX IF NOT EXISTS idx_user_prizes_user_status ON user_prizes(user_id, status);
CREATE INDEX IF NOT EXISTS idx_user_prizes_shipment ON user_prizes(shipment_id);
