-- Banner / 公告 排程上下架：以時間窗（publish_at <= now < unpublish_at）控制公開可見性，免背景排程。
ALTER TABLE banners ADD COLUMN publish_at TEXT;
ALTER TABLE banners ADD COLUMN unpublish_at TEXT;
-- news 已有 published_at（上架時間），補一個自動下架時間。
ALTER TABLE news ADD COLUMN unpublish_at TEXT;
