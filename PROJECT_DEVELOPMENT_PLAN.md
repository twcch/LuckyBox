# LuckyBox 線上一番賞網站專案開發計劃書

文件日期：2026-06-11  
目標讀者：負責實作的 AI coding agent、產品負責人、設計/營運協作者  
專案狀態：從 0 建置網站與後台  
主要市場假設：以台灣使用者為主，行動裝置優先，販售實體動漫/潮玩/卡牌類抽賞商品

> 重要提醒：本文件是產品與工程開發計劃，不是法律意見。線上抽賞涉及消費者保護、機率揭露、金流、個資、稅務、商標與 IP 授權，正式上線前必須由熟悉台灣電商與抽獎/抽賞法規的律師或法遵顧問確認。

---

## 0. AI 執行方式

後續 AI 開發時，請把本文件當作任務板使用。

- [x] 每開始一個階段前，先閱讀該階段的「完成條件」。持續規則：本輪依 Phase 10/14 未完成項與完成條件收斂。
- [x] 每完成一個項目，就把 `- [ ]` 改成 `- [x]`。持續規則：本輪已更新真實金流與 readiness 狀態。
- [x] 每次提交前，在本文件「進度紀錄」新增一筆簡短紀錄。持續規則：本輪已新增 2026-07-04 ECPay adapter 進度紀錄；若之後實際 git commit 仍需再次檢查。
- [x] 不確定產品規則時，不要自行改變抽賞/金流/機率邏輯，先在「待確認問題」新增問題。持續規則：本輪只補 ECPay 工程 adapter，正式合約/法務/物流仍留待外部決策。
- [x] 所有涉及金流、抽籤、公平性、庫存扣減、出貨的改動，必須有測試。持續規則：本輪金流改動已補 `EcpayChecksumTests`、`EcpayPaymentApiTests` 與相關 wallet 測試。
- [x] 不要把真實 API key、金流 Hash Key、會員個資、後台帳號密碼提交到 repo。持續規則：`.env.example` 僅保留 placeholder，secret policy tests 持續鎖定。
- [x] 不要使用未授權動漫圖片、品牌 Logo、角色圖、官方商品圖作為正式商用素材。持續規則：本輪無新增商用素材，賞池發布 gate 仍要求商用/授權確認。

---

## 1. 專案願景

LuckyBox 要做成一個「透明、公平、好用、可營運」的線上抽賞平台。核心不是只做抽獎動畫，而是要完整處理：商品上架、獎池設定、機率/剩餘數揭露、付款、抽籤、戰利品保管、出貨、客服、後台審計與營運活動。

### 1.1 一句話定位

- [x] LuckyBox 是面向台灣收藏玩家的線上抽賞平台，主打公開剩餘獎池、可追溯抽籤紀錄、行動端順暢體驗與清楚售後流程。MVP 已涵蓋公開剩餘/機率、fairness proof、RWD 前台與出貨/客服政策頁。

### 1.2 MVP 成功標準

- [x] 使用者可以註冊/登入。
- [x] 使用者可以瀏覽目前可抽的賞池。
- [x] 使用者可以查看每個賞池的獎項、剩餘數、機率、抽賞價格、最後賞規則與出貨說明。
- [x] 使用者可以儲值或用測試金流取得點數。
- [x] 使用者可以抽 1 抽或多抽。
- [x] 系統能正確扣點、抽籤、扣庫存、產生中獎紀錄。
- [x] 同一獎籤不會在高併發下被重複抽出。
- [x] 使用者可以在「戰利品」查看已抽中的商品。
- [x] 使用者可以申請出貨並填寫收件資訊。
- [x] 後台可以管理賞池、獎項、庫存、訂單、出貨、會員、點數與公告。MVP 範圍：獎項/庫存整合於賞池編輯頁。
- [x] 管理者可以查看每一筆點數與抽籤交易紀錄。
- [x] 上線前通過基本資安、法遵、壓力與 E2E 測試。MVP 範圍：本機已完成 security / concurrency / E2E / packaging 驗證；法遵以 `docs/launch-readiness.md` gate 管理，正式律師審閱仍屬 Milestone 5 外部簽核。

### 1.3 MVP 暫不做的事情

- [x] 不做使用者之間的自由交易市集，先避免交易糾紛與法遵複雜度。
- [x] 不做現金回饋、現金兌換、可提領點數，避免被誤判為賭博或金融服務。
- [x] 不做成人內容賞池，先降低年齡驗證與內容審核成本。
- [x] 不做原生 App，先做 RWD/PWA。
- [x] 不做多國語系，先做繁體中文。
- [x] 不做第三方賣家開店，先由平台自營。

---

## 2. 競品研究摘要

研究日期：2026-06-11  
研究方式：公開網站搜尋與頁面觀察  
核心觀察：成熟平台大多不是單純「抽一下」，而是圍繞抽賞建立了會員、儲值、戰利品、榜單、活動、透明庫存、客服、出貨與後台營運能力。

### 2.1 參考來源

- [BANDAI SPIRITS 一番くじONLINE](https://on-line.1kuji.com/)：官方線上抽賞。新手指南指出流程包含選商品、選籤箱、確認剩餘等賞、選抽數、選籤、確認中獎商品、最後賞、到府收貨。
- [台灣萬代南夢宮 一番賞 FAQ](https://www.bandainamcoth.tw/kuji-faq)：台灣官方資訊、上市查詢、店鋪查詢、瑕疵品處理、雙重中獎配送等。
- [抽籤堂](https://kujibikido.tw/)：日本官方授權的線上抽獎服務，頁面顯示銷售中/預計銷售/銷售結束商品、10 連抽、單筆 20 抽內統一運費、信用卡付款、付款後確認中獎。
- [抽抽一番賞](https://official.cc1kuji.com/)：台灣線上一番賞平台，商品類型含一番賞、卡牌、泡泡瑪特、版權雕像；強調正版、金流合作、玩家市集、消費送抽。
- [KUJiHUNTER 玩番樂](https://kujihunter.com/)：分類清楚，首頁直接顯示剩餘/總抽數與每抽價格，含代幣/金幣/銀幣/銅幣/紅利、新品快訊、命運輪盤、限時特典。
- [賞翻天 KujiFlip](https://kujiflip.tw/)：導覽含一番賞、商城、賞品盒、即時榜單、每日活動、每日任務、儲值、排序、限制級彈窗、客服資訊。
- [丸島賞](https://wonderkuji.com.tw/)：分類為官方島、一番島、卡牌島、丸預購；有最新榜單、官方/熱門一番賞、熱門卡牌、島民報喜、合作島主，並主打機率透明與公平。
- [潮流勁抽](https://fashionkuji.com/)：以 App 下載為主，商品線含 GK、卡牌、潮牌、熱門 IP，強調正版、售後、可退回。
- [良級懸賞](https://citydao.world/)：有任務、榜單、實體店面、GK 修復，並主打 VRF Hash 驗證抽獎公平性。
- [多巴胺一番賞](https://dopaminekuji.com/shop/page/3/)：有 G 幣、快速儲值、戰利品、交換區、許願牆、VIP 轉盤、抽況 LIVE、歐氣榜、剩餘進度與商品分類。
- [爽抽樂 / oneone Lite 介紹](https://universe.oneone.com.tw/posts/2474)：主打創作者/店家自製一番賞，提供免費版、訂閱方案、活動數/抽數限制、即時紀錄、獎項名單。
- [台北市一番賞相關遊戲商家指引 PDF](https://www-ws.gov.taipei/Download.ashx?icon=..pdf&n=5LiA55Wq6LOe55u46Zec6YGK5oiy5ZWG5a625oyH5byVLnBkZg%3D%3D&u=LzAwMS9VcGxvYWQvNDA5L3JlbGZpbGUvMTAxNjIvOTM2NzQzNy8xYjg5YTUzMy1jYWVhLTRmYTItODMxMS03NTVjODU2OGU0ZDEucGRm)：要求清楚標示商品名稱、獎單序號、混套/自製標示、總籤數、剩餘獎項、已抽出狀況、最後賞等。
- [法務部：公布轉蛋中獎機率保障遊戲玩家權益](https://www.moj.gov.tw/2204/2473/2492/152620/post)：指出付費機會型商品/活動應揭露中獎機率，且應以數字百分比方式記載。
- [臺北地檢署：個人資料蒐集、處理、利用之規範與刑責](https://www.tpc.moj.gov.tw/292885/976681/661783/1107652/post)：提醒個資包含姓名、生日、身分證字號、聯絡方式、財務情況等可識別自然人資料。

### 2.2 競品功能矩陣

| 類型 | 代表競品 | 觀察到的功能 | LuckyBox 可學習之處 |
| --- | --- | --- | --- |
| 官方抽賞 | 一番くじONLINE | 會員、商品列表、選籤箱、剩餘獎項、選抽數、選籤、確認中獎、最後賞、配送 | MVP 必須做到透明剩餘獎池與完整抽賞流程 |
| 官方/代理資訊 | 台灣萬代南夢宮 | 發售資訊、店鋪查詢、FAQ、瑕疵換貨、雙重中獎配送 | 商品授權、客服與瑕疵處理要清楚 |
| 授權線上抽獎 | 抽籤堂 | 銷售期間、10 連抽、單筆 20 抽內統一運費、信用卡付款、付款後開獎 | 時限型商品、批量抽、運費規則、付款後揭曉 |
| 台灣抽賞平台 | 抽抽一番賞 | 一番賞、卡牌、泡泡瑪特、GK、金流合作、市集、消費送抽 | 產品線可擴展，但 MVP 先聚焦抽賞本體 |
| 台灣抽賞平台 | KUJiHUNTER | 商品卡直接顯示剩餘/總抽與價格，多幣別、輪盤、限時特典 | 首頁資訊密度與活動入口很重要 |
| 台灣抽賞平台 | 賞翻天 | 儲值、賞品盒、即時榜單、每日任務、排序、18+ 彈窗、客服資訊 | 戰利品、榜單、任務與客服是留存關鍵 |
| 台灣抽賞平台 | 丸島賞 | 分島分類、最新榜單、熱門賞、卡牌、合作島主、透明公平主張 | 建立信任與社群感，要有榜單與公開紀錄 |
| App 型平台 | 潮流勁抽 | App 下載、GK/卡牌/潮牌/IP、售後退換主張 | PWA 可先替代 App，未來可封裝 |
| 公平性差異化 | 良級懸賞 | VRF Hash 驗證、任務、榜單、實體店、GK 修復 | 可驗證公平是差異化方向 |
| 內容/互動型 | 多巴胺一番賞 | G 幣、戰利品、交換區、許願牆、VIP 轉盤、抽況 LIVE、歐氣榜 | Live 抽況、許願牆與活動玩法有助轉換 |
| SaaS/自製賞 | 爽抽樂 | 免費建立活動、訂閱方案、抽數限制、即時紀錄、獎項名單 | 後期可做 B2B 賞池建置，但 MVP 不做 |

### 2.3 競品共同模式

- [x] 首頁需要直接呈現「現在可以抽什麼」。
- [x] 商品卡要有價格、剩餘抽數、總抽數、狀態、主圖、標籤。
- [x] 詳情頁要清楚列出獎項等級、獎品圖、原始數量、剩餘數量、機率。
- [x] 抽賞頁要有沉浸式體驗，但不能犧牲資訊透明。
- [x] 使用者付款/扣點後才抽籤。
- [x] 抽完要立即展示結果。
- [x] 抽中商品要進入戰利品/賞品盒。
- [x] 使用者可以累積戰利品再合併出貨。
- [x] 儲值、紅利、優惠券、免運券是常見轉換工具。
- [x] 即時榜單/抽況 LIVE 可以創造熱度與信任感。MVP 範圍：`/leaderboard` 顯示最近抽出紀錄與熱門賞池榜，首頁 LIVE strip 改接公開 API。
- [x] 後台營運能力決定平台能不能長期更新賞池。
- [x] 法遵與公平揭露會直接影響信任。

### 2.4 LuckyBox 建議差異化

- [x] 公開每個賞池的「目前剩餘獎項、目前機率、已抽出紀錄」。
- [x] 每次抽籤產生不可竄改的 audit log。
- [x] 完抽後公開該池的抽籤 Hash/Seed 驗證資訊。
- [x] 商品頁清楚區分「官方授權一番賞」、「自營混套賞」、「盲盒/卡牌賞」。
- [x] 所有混套/自製賞必須在商品頁醒目標示。
- [x] 不用誇大「超高機率」、「必中大賞」；改用數字與剩餘數說話。
- [x] 強調客服、出貨、瑕疵處理流程。MVP 範圍：新增 `/shipping-policy` 全站政策頁，彙整出貨、退換貨、瑕疵與客服 SLA。
- [x] 先不做市集，先把主流程做穩。

---

## 3. 法遵與信任設計

### 3.1 必須遵守的產品原則

- [x] 每次抽賞都必須得到實體商品或明確標示的等值獎項，不設「空獎」。
- [x] 不允許把點數兌換現金。
- [x] 不允許讓使用者出售點數或獎品給平台換現金。
- [x] 不允許用「投資、回本、賺錢」作為行銷文案。
- [x] 不在未授權情況下宣稱「官方」、「授權」、「正版代理」。MVP 範圍：後台賞池新增 `officialLicenseConfirmed`；`sourceType=OFFICIAL` 發布前必須確認官方授權或進貨佐證，前台公開來源/授權說明。
- [x] 未授權商品圖只可在內部測試使用，不可正式公開。MVP 範圍：後台賞池新增 `commercialUseConfirmed` 與公開 `rightsNotice`，發布前 checklist/API gate 皆要求素材可商用確認。
- [x] 若商品為混套、自製賞、二手品、預購品、海外代購品，必須醒目標示。
- [x] 商品瑕疵、延遲出貨、預購風險、退換貨限制必須在購買前揭露。
- [x] 購買頁與商品頁必須顯示中獎機率或可由剩餘數明確計算的機率。

### 3.2 一番賞資訊揭露清單

- [x] 商品名稱。
- [x] 商品品牌/IP。
- [x] 商品來源：官方、代理、平行輸入、自製、混套、預購、二手。
- [x] 每抽價格。
- [x] 總籤數。
- [x] 目前剩餘籤數。
- [x] 每個獎項等級的原始數量。
- [x] 每個獎項等級的剩餘數量。
- [x] 每個獎項目前中獎機率。
- [x] 是否有最後賞。
- [x] 最後賞取得條件。
- [x] 已抽出紀錄。MVP 範圍：賞池詳情頁新增「最近抽出紀錄」，讀取 `/api/leaderboard/campaigns/{slug}/draws`，顯示遮罩會員、獎項、結果序號與時間。
- [x] 出貨時間。
- [x] 運費規則。
- [x] 退換貨/瑕疵處理規則。
- [x] 客服聯絡方式。MVP 範圍：賞池詳情政策區新增客服聯絡入口，導向 `/contact`；`/contact` 提供客服信箱、處理時段、mail template 與問題分類。
- [x] 若限制年齡，顯示年齡門檻與驗證方式。MVP 範圍：賞池主檔新增 `ageRestricted` / `minimumAge` / `ageVerificationNote`，後台必填與發布前檢查，前台詳情顯示年齡 badge 與驗證方式。

### 3.3 個資與隱私

- [x] 僅收集必要個資。
- [x] 註冊階段只收集 email/手機/密碼或社群登入 ID。
- [x] 出貨階段才收集姓名、電話、地址。
- [x] 後台會員列表預設遮罩手機、email、地址。MVP 已於 `/admin/users` 回傳 `maskedEmail` / `maskedPhone`。
- [x] 抽況 LIVE 只顯示遮罩暱稱，例如 `Lu**`。MVP 已於 `GET /api/leaderboard` 後端輸出前遮罩 display name。
- [x] 管理員查閱完整個資要留 audit log。MVP：會員詳情預設遮罩；只有 `GET /api/admin/users/:id?reveal=true` 或後台明確點擊揭露完整個資時，才回未遮罩 email/手機/地址並寫 `ADMIN_MEMBER_DETAIL_VIEWED` audit。
- [x] 隱私權政策列出蒐集目的、資料類型、保存期間、第三方服務、使用者權利。MVP 範圍：`/privacy` 已公開說明帳號、交易、出貨、安全資料、用途、保存邏輯、第三方服務與會員權利。
- [x] 提供刪除帳號/匯出資料的客服流程。MVP 範圍：`/privacy` 已列出以會員 Email 寄送申請、驗證身分、確認未完成交易/出貨/爭議後處理。

### 3.4 付款與點數

- [x] 點數只可在平台消費，不可提領。MVP 範圍：`/terms` 已明定 Lucky Point 僅限平台內抽賞、出貨與活動折抵使用，不提供提領或兌換現金。
- [x] 點數命名避免像金融貨幣，可用 `Lucky Point` 或 `LP`。MVP 範圍：`/terms` 使用 Lucky Point（LP）並說明其非金融商品或可提領餘額。
- [x] 點數交易要有 immutable ledger。
- [x] 所有儲值都要對應金流訂單。MVP 範圍：會員端儲值先建立 `payment_orders`；Mock 以 signed webhook sandbox 入點，ECPay 以 ReturnURL callback 驗證後入點。
- [x] 金流 webhook 必須 idempotent。MVP 範圍：`payment_webhook_events` 以 `UNIQUE(provider,event_id)` 防重送，重複回呼不會重複入點。
- [x] 退款要有人工審核流程。MVP 範圍：後台付款訂單退款需管理員輸入原因，回收該訂單入帳點數並寫 audit；另新增 `admin_approval_requests` 審核佇列，可先建立退款待審單，再由超級管理員核准執行。
- [x] 贈點/紅利點要標示有效期限。MVP 範圍：錢包總覽 API 回傳 `bonusPointExpiryDays` / `bonusPointExpiryLabel`，預設紅利點自入帳日起 365 天有效，可用 `LUCKYBOX_BONUS_POINT_EXPIRY_DAYS` 調整；前台錢包頁於贈點餘額旁顯示期限政策。
- [x] 不同點數類型要分帳：現金點、贈點、補償點。MVP 範圍：wallet balance 與 ledger 以 CASH / BONUS 分帳，補償發點以 bonus point ledger reason/audit 留痕。

### 3.5 抽籤公平性

- [x] 每個賞池建立時產生完整 ticket 清單。
- [x] 每張 ticket 有唯一序號。
- [x] 抽籤時只可從未抽出的 ticket 選取。
- [x] 抽籤演算法要使用加密安全亂數。
- [x] 抽籤與扣點必須在同一個資料庫 transaction 內完成。
- [x] 多抽時要一次鎖定足夠 tickets。
- [x] 任何失敗都要 rollback。
- [x] 抽籤結果不可由前端決定。
- [x] 管理員不可手動指定某使用者抽中特定獎。
- [x] 管理員修改賞池/獎項/票券狀態要留下 audit log。
- [x] 完抽後提供 audit summary。

---

## 4. 使用者角色

### 4.1 訪客

- [x] 可以瀏覽首頁。
- [x] 可以看商品列表。
- [x] 可以看商品詳情。
- [x] 可以看公開抽況/榜單。MVP 範圍：訪客可開啟 `/leaderboard` 查看最近抽出紀錄與熱門賞池。
- [x] 不能抽賞。
- [x] 不能查看完整戰利品功能。

### 4.2 一般會員

- [x] 可以登入。
- [x] 可以儲值。
- [x] 可以抽賞。
- [x] 可以查看抽賞紀錄。MVP 範圍：`/account/orders` 顯示自己的抽賞訂單、中獎摘要與結果明細。
- [x] 可以查看戰利品。
- [x] 可以申請出貨。
- [x] 可以使用優惠券。
- [x] 可以編輯基本資料與收件地址。

### 4.3 VIP/高活躍會員

- [x] 可以看到 VIP 等級。
- [x] 可以領取 VIP 優惠券或免運券。MVP 範圍：優惠券可設定 `vip_tier`，會員列表、贈點券領取、抽賞折扣券與出貨免運券均依目前 VIP 等級 gating。
- [x] 可以參與指定活動。MVP 範圍：簽到、優惠券、許願池與促銷活動。
- [x] 可以查看累積消費與等級進度。

### 4.4 客服人員

- [x] 可以查詢會員。
- [x] 可以查詢訂單。
- [x] 可以查詢出貨。
- [x] 可以建立客服備註。
- [x] 可以處理瑕疵/補償。
- [x] 不可修改抽籤結果。
- [x] 不可直接調整點數，必須發起申請或留下原因。

### 4.5 營運管理員

- [x] 可以建立/編輯賞池。
- [x] 可以上傳商品圖。MVP：後台 Banner、賞池封面 / Banner 與獎項圖片欄位可直接選檔上傳 JPG / PNG / WebP，成功後自動填入 `/uploads/**` URL。
- [x] 可以設定獎項與 ticket。MVP：後台可建立/編輯賞池獎項並補生成可抽 ticket。
- [x] 可以上下架商品。
- [x] 可以設定活動、banner、公告。
- [x] 可以查看營運報表。
- [x] 不可直接修改已抽出 ticket。

### 4.6 超級管理員

- [x] 可以管理後台帳號與權限。MVP 範圍：超級管理員可於 `/admin/users` 調整 USER / CUSTOMER_SERVICE / OPERATOR / ADMIN 角色，保護自己與 SUPER_ADMIN。
- [x] 可以審核點數調整。MVP 範圍：新增 `/admin/approval-requests` 審核中心，點數調整可建立待審單並由超級管理員核准後執行。
- [x] 可以審核退款/補償。MVP 範圍：退款與客服補償均可建立待審單，超級管理員核准後呼叫既有退款/補償服務，駁回不執行。
- [x] 可以查看完整 audit log。
- [x] 可以處理緊急下架與系統維護。MVP 範圍：可暫停/下架賞池並透過公告、Banner 與後台控制前台可見狀態。

---

## 5. 指定技術棧

本專案技術已限定如下，後續 AI 開發不可改用未列入本節的前端框架、後端框架或資料庫主架構。若因套件或部署需要使用輔助工具，必須只作為建置、測試或開發輔助，不可改變主要技術棧。

### 5.1 前端

- [x] HTML5。
- [x] CSS3。
- [x] JavaScript。
- [x] Bootstrap 5。
- [x] VueJS，建議使用 Vue 3。
- [x] Vue Router，用於前台與後台頁面路由。
- [x] Pinia，用於會員、錢包、戰利品、購物/抽賞狀態管理。
- [x] Axios 或 Fetch API，用於呼叫 Spring 後端 REST API。
- [x] Bootstrap Icons，用於按鈕與導覽 icon。
- [x] 抽賞動畫以 Vue component + CSS animation 實作，不依賴大型動畫框架。MVP 範圍：賞池詳情頁以 Vue 狀態與 CSS keyframes 實作抽取 ticket、逐張揭曉與稀有結果動畫，未引入大型動畫框架。
- [x] 若使用 Vite，只作為 Vue 開發與打包工具；正式產品技術仍是 HTML/CSS/JavaScript/Bootstrap/VueJS。

### 5.2 後端

- [x] Java。
- [x] Spring Framework，建議使用 Spring Boot 加速專案設定。
- [x] Spring MVC，提供 REST API。
- [x] Spring Security，處理登入、權限、Session/JWT。
- [x] Spring Validation / Jakarta Bean Validation，處理 DTO 驗證。
- [x] Spring Transaction Management，確保扣點、抽籤、扣庫存同一 transaction 完成。
- [x] Spring Data JPA 或 Spring JDBC。若需要精準控制 SQLite lock/transaction，可優先使用 Spring JDBC。MVP 使用 Spring JDBC。
- [x] SQLite。
- [x] Flyway 或 Liquibase，管理 SQLite schema migration。MVP 使用 Flyway。
- [x] Java Mail Sender 或後續 SMTP provider，用於通知信。MVP 範圍：`EmailService` 以 `JavaMailSender` optional bean 支援正式 SMTP，dev/test 預設 log fallback；密碼重設信與出貨狀態 email 已接入。
- [x] 本機檔案或可設定的外部檔案目錄，用於商品圖與附件。MVP：新增 `LUCKYBOX_UPLOAD_DIR` 本機圖片儲存目錄，後台圖片上傳後回傳 `/uploads/**` URL，可用於商品圖 / Banner 圖欄位。

### 5.3 驗證與帳號

- [x] Spring Security。
- [x] Email/password。
- [x] 密碼使用 BCrypt。
- [x] 前台會員可用 Session Cookie 或 JWT，需明確選定一種。MVP 採 Session Cookie。
- [x] 後台管理者建議使用 Session Cookie + CSRF 防護。
- [x] 手機 OTP 可放 Phase 2。
- [x] 後台帳號必須啟用 2FA。MVP 範圍：管理員可自助啟用/停用 TOTP；正式上線強制策略仍屬營運政策。

### 5.4 金流

- [x] 開發期先做 Mock Payment Provider。MVP 範圍：會員端 mock 儲值完成與 `POST /api/webhooks/payment/mock` signed webhook sandbox。
- [x] MVP 串接 1 個台灣金流：綠界 ECPay 或藍新 NewebPay。MVP 範圍：完成 ECPay AioCheckOut adapter，支援 ECPay 訂單、checkout form 欄位、`CheckMacValue` SHA256 驗證、`application/x-www-form-urlencoded` ReturnURL callback、金額檢查、模擬付款預設不入點、webhook event 冪等入點；正式 merchant 帳號開通仍屬 Milestone 5。
- [x] Phase 2 再加 LINE Pay / 街口 / 信用卡分期。狀態：信用卡分期已透過 ECPay AioCheckOut `CreditInstallment` 設定完成工程支援；LINE Pay request/confirm redirect adapter 與街口 Entry/confirm/result adapter 已完成後端、前端導向與 provider-specific 測試。正式啟用仍由 merchant account/API key、callback dashboard 測試與 readiness gate 擋住。
- [x] 所有金流回呼要驗證簽章。MVP 範圍：mock webhook 以 `X-LuckyBox-Signature` 驗 HMAC-SHA256。
- [x] 金流 webhook 由 Spring Controller 接收，必須做到 idempotent。MVP 範圍：Spring Controller 接收 `/api/webhooks/payment/mock`，event id 重送回 `duplicate=true` 且不重複入點。

### 5.5 部署

- [x] 前端 Vue 打包後可由 Spring Boot static resources 服務，形成單一部署包。MVP 範圍：後端新增 Maven `single-package` profile，先執行 `frontend` build，再以 `./mvnw -Psingle-package -DskipTests package` 將 `frontend/dist` 複製到 Spring Boot static resources 並產出單一 jar。
- [x] 或前端靜態檔部署於 Nginx/Apache，後端 Spring Boot 獨立部署。MVP 文件：`docs/operations.md` 保留 split deployment 作為正式上線替代部署形態；實際主機、CDN 與反向代理設定待 Phase 14 決策。
- [x] SQLite 檔案必須放在可持久化磁碟，不可放 ephemeral container filesystem。MVP 文件：`docs/operations.md` 已列正式環境持久化要求；實際正式環境掛載仍待部署時設定。
- [x] 正式環境必須設定 SQLite 備份策略。MVP 文件：`docs/operations.md` 已列每日、遷移前與 restore drill；實際備份任務待正式環境設定。
- [x] 商品圖片上傳目錄必須設定持久化與備份。MVP 文件：`docs/operations.md` 已列 uploads 持久化與備份要求；實際 object storage/volume 待部署時設定。
- [x] Spring Boot 可打包為 `.jar`。驗證：`./mvnw package` 成功產生 repackaged Spring Boot jar。
- [x] 可使用 VPS、Docker Compose 或支援持久化磁碟的 PaaS。MVP 文件：`docs/operations.md` 已列支援部署目標與持久化磁碟要求；實際主機/PaaS 選型待 Phase 14。
- [x] Sentry 或 Spring Boot Actuator + log aggregation 監控錯誤。MVP 範圍：新增 Spring Boot Actuator，公開 `/actuator/health` 供 uptime monitor，metrics 仍受安全規則保護；正式 Sentry/log aggregation 待 Phase 14 設定。
- [x] Plausible、PostHog 或自建事件表追蹤產品事件。MVP 範圍：已建 `visitor_sessions`、`POST /api/analytics/visit`、前端 visitorId 與 Dashboard product metrics；正式第三方分析工具仍可於上線時替換或併用。

### 5.6 SQLite 特別限制

- [x] SQLite 適合 MVP 與中小流量內測，但抽賞高併發時要特別保守設計。
- [x] 啟用 WAL mode。
- [x] 設定 busy timeout。
- [x] 抽賞 transaction 必須短，避免長時間鎖表。
- [x] 抽賞 API 要使用 application-level lock 或資料庫 transaction 防止同賞池併發重複出籤。
- [x] 每次抽賞後要立刻提交 transaction。
- [x] 報表查詢不可阻塞抽賞交易，必要時使用排程產生彙總表。
- [x] 正式流量變大時，需預留遷移到企業級資料庫的資料抽象邊界，但 MVP 仍以 SQLite 實作。

---

## 6. 網站地圖

### 6.1 前台頁面

- [x] `/` 首頁。MVP 範圍：首頁直接顯示可抽賞池、Banner、LIVE strip、熱門賞池與公告入口。
- [x] `/kuji` 全部賞池列表。MVP 範圍：相容路由導向首頁賞池區 `/#campaigns`，列表搜尋/篩選/排序整合於首頁。
- [x] `/kuji/[slug]` 賞池詳情。MVP 範圍：商品圖、獎項、剩餘數、即時機率、近期抽出、FAQ、公平性與抽賞區。
- [x] `/kuji/[slug]/draw` 抽賞頁。MVP 範圍：相容路由導向 `/kuji/[slug]#draw`，抽賞互動整合於詳情頁。
- [x] `/result/[drawId]` 抽賞結果頁。MVP 範圍：相容路由導向 `/account/orders?drawId=...`，即時結果仍在抽賞完成後於詳情頁揭曉。
- [x] `/account` 會員中心。
- [x] `/account/wallet` 點數錢包。
- [x] `/account/top-up` 儲值頁。MVP 範圍：相容路由導向 `/account/wallet#top-up`，儲值方案與 mock checkout 由錢包頁處理。
- [x] `/account/prizes` 我的戰利品。
- [x] `/account/shipments` 出貨紀錄。MVP 範圍：出貨申請統計、狀態、收件地區、運費與品項列表，並可進入 `/account/shipments/:shipmentId` 查看單筆物流追蹤。
- [x] `/account/orders` 付款/抽賞訂單。MVP 範圍：最近抽賞訂單、Mock 付款訂單、統計摘要與會員中心入口。
- [x] `/account/coupons` 優惠券。MVP 範圍：顯示目前可用的啟用優惠券，折扣券可在賞池詳情抽賞區輸入代碼使用。
- [x] `/account/profile` 個人資料。MVP 範圍：會員可查看 Email、角色、狀態、VIP、點數摘要，並更新顯示名稱與手機。
- [x] `/leaderboard` 抽況 LIVE / 榜單。MVP 範圍：最近抽出紀錄、熱門賞池榜、手動重整與 30 秒短輪詢。
- [x] `/news` 公告與活動。MVP 範圍：公開公告列表。
- [x] `/news/[slug]` 公告詳情。MVP 範圍：已發布公告完整內容。
- [x] `/faq` 常見問題。MVP 範圍：分類、搜尋、帳號/點數/抽賞/戰利品/出貨/客服 FAQ、客服信箱與相關頁面導流。
- [x] `/terms` 會員服務條款。MVP 範圍：帳號資格、LP 點數、抽賞、戰利品/出貨、優惠券、禁止行為、暫停/終止與客服爭議流程。
- [x] `/privacy` 隱私權政策。MVP 範圍：資料類型、使用目的、保存期間、第三方服務、資料權利與刪除/匯出客服流程。
- [x] `/shipping-policy` 出貨與退換貨政策。MVP 範圍：出貨流程、固定運費、免運券、物流追蹤、退換貨/瑕疵規則與客服聯絡方式。

### 6.2 後台頁面

- [x] `/admin` 後台首頁。
- [x] `/admin/login` 後台登入。
- [x] `/admin/campaigns` 賞池管理。
- [x] `/admin/campaigns/new` 新增賞池。
- [x] `/admin/campaigns/[id]` 編輯賞池。
- [x] `/admin/campaigns/[id]/tickets` 票券/獎籤管理。MVP 範圍：賞池編輯頁可管理獎項/生成 ticket，ticket 子頁可查完整序號、狀態、獎項、draw id 與遮罩抽出者 email。
- [x] `/admin/prizes` 獎品管理。MVP 範圍：獨立獎品庫頁可跨賞池搜尋/篩選獎品，顯示賞池狀態、一般/最後賞、原始/剩餘數、已生成/可抽/已抽出 tickets，並導回賞池編輯與 ticket 子頁。
- [x] `/admin/orders` 訂單管理。MVP 範圍：付款訂單列表、狀態/provider/關鍵字篩選、會員遮罩 email。
- [x] `/admin/draws` 抽賞紀錄。MVP 範圍：狀態/賞池/關鍵字篩選、會員遮罩 email、獎項摘要與單筆抽賞詳情。
- [x] `/admin/wallet-ledger` 點數流水。MVP 範圍：流水列表、類型/點數種類/來源/關鍵字篩選、會員遮罩 email、入點/扣點摘要。
- [x] `/admin/users` 會員管理。MVP 範圍：搜尋、狀態/角色篩選、遮罩聯絡資訊、啟用/停權。
- [x] `/admin/shipments` 出貨管理。
- [x] `/admin/coupons` 優惠券管理。MVP 範圍：優惠券列表、搜尋、類型/狀態篩選、新增、編輯、啟用與封存。
- [x] `/admin/banners` Banner 管理。MVP 範圍：Banner 列表、搜尋、位置/狀態篩選、新增、編輯、啟用與封存。
- [x] `/admin/news` 公告管理。MVP 範圍：公告列表、搜尋、狀態篩選、新增、編輯、發布與封存。
- [x] `/admin/audit-logs` 審計紀錄。
- [x] `/admin/settings` 系統設定。MVP 範圍：獨立系統設定頁讀取 `GET /api/admin/settings`，顯示 runtime、安全、金流、Email/SMTP、促銷/VIP 等非敏感摘要；密鑰、SMTP 密碼與 provider secret 不回傳。

---

## 7. 核心資料模型

### 7.1 User

- [x] `id`
- [x] `email`
- [x] `phone`
- [x] `passwordHash`
- [x] `displayName`
- [x] `avatarUrl`
- [x] `role`
- [x] `status`
- [x] `vipLevel`
- [x] `createdAt`
- [x] `updatedAt`
- [x] `lastLoginAt`

### 7.2 UserAddress

- [x] `id`
- [x] `userId`
- [x] `recipientName`
- [x] `phone`
- [x] `postalCode`
- [x] `city`
- [x] `district`
- [x] `addressLine`
- [x] `isDefault`
- [x] `createdAt`
- [x] `updatedAt`

### 7.3 Wallet

- [x] `id`
- [x] `userId`
- [x] `cashPointBalance`
- [x] `bonusPointBalance`
- [x] `lockedBalance`
- [x] `createdAt`
- [x] `updatedAt`

### 7.4 WalletLedger

- [x] `id`
- [x] `userId`
- [x] `walletId`
- [x] `type`: `TOP_UP`, `DRAW_SPEND`, `REFUND`, `BONUS`, `ADJUSTMENT`, `SHIPMENT_FEE`
- [x] `amount`
- [x] `pointKind`: `CASH`, `BONUS`
- [x] `balanceAfter`
- [x] `referenceType`
- [x] `referenceId`
- [x] `reason`
- [x] `createdBy`
- [x] `createdAt`

### 7.5 PaymentOrder

- [x] `id`
- [x] `userId`
- [x] `provider`
- [x] `merchantTradeNo`
- [x] `amount`
- [x] `pointAmount`
- [x] `bonusPointAmount`
- [x] `status`: `PENDING`, `PAID`, `FAILED`, `CANCELED`, `REFUNDED`
- [x] `providerPayload`
- [x] `paidAt`
- [x] `createdAt`
- [x] `updatedAt`

### 7.6 KujiCampaign

- [x] `id`
- [x] `slug`
- [x] `title`
- [x] `subtitle`
- [x] `description`
- [x] `coverImageUrl`
- [x] `bannerImageUrl`
- [x] `sourceType`: `OFFICIAL`, `SELF_MADE`, `MIXED`, `BLIND_BOX`, `CARD`, `GK`, `PREORDER`
- [x] `ipName`
- [x] `brandName`
- [x] `pricePerDraw`
- [x] `totalTickets`
- [x] `remainingTickets`
- [x] `status`: `DRAFT`, `SCHEDULED`, `LIVE`, `SOLD_OUT`, `PAUSED`, `ENDED`
- [x] `salesStartAt`
- [x] `salesEndAt`
- [x] `shippingNote`
- [x] `returnPolicyNote`
- [x] `hasLastPrize`
- [x] `lastPrizeRule`
- [x] `fairnessMode`: `SERVER_RANDOM`, `HASH_COMMIT_REVEAL`
- [x] `seedHash`
- [x] `revealedSeed`
- [x] `createdAt`
- [x] `updatedAt`

### 7.7 Prize

- [x] `id`
- [x] `campaignId`
- [x] `rank`: `S`, `A`, `B`, `C`, `D`, `E`, `F`, `G`, `LAST`
- [x] `name`
- [x] `description`
- [x] `imageUrl`
- [x] `originalQuantity`
- [x] `remainingQuantity`
- [x] `sortOrder`
- [x] `isLastPrize`
- [x] `createdAt`
- [x] `updatedAt`

### 7.8 KujiTicket

- [x] `id`
- [x] `campaignId`
- [x] `prizeId`
- [x] `serialNumber`
- [x] `status`: `AVAILABLE`, `DRAWN`, `VOIDED`
- [x] `drawId`
- [x] `drawnByUserId`
- [x] `drawnAt`
- [x] `createdAt`
- [x] `updatedAt`

### 7.9 DrawOrder

- [x] `id`
- [x] `userId`
- [x] `campaignId`
- [x] `quantity`
- [x] `pointSpent`
- [x] `status`: `PENDING`, `COMPLETED`, `FAILED`, `REFUNDED`
- [x] `idempotencyKey`
- [x] `clientRequestId`
- [x] `ipAddress`
- [x] `userAgent`
- [x] `createdAt`
- [x] `completedAt`

### 7.10 DrawResult

- [x] `id`
- [x] `drawOrderId`
- [x] `ticketId`
- [x] `prizeId`
- [x] `userId`
- [x] `campaignId`
- [x] `resultIndex`
- [x] `randomProof`
- [x] `createdAt`

### 7.11 UserPrize

- [x] `id`
- [x] `userId`
- [x] `campaignId`
- [x] `prizeId`
- [x] `drawResultId`
- [x] `status`: `IN_BOX`, `SHIPMENT_REQUESTED`, `SHIPPED`, `DELIVERED`, `EXCHANGED`, `REFUNDED`, `CANCELED`
- [x] `shipmentId`
- [x] `expiresAt`
- [x] `createdAt`
- [x] `updatedAt`

### 7.12 Shipment

- [x] `id`
- [x] `userId`
- [x] `status`: `REQUESTED`, `PACKING`, `SHIPPED`, `DELIVERED`, `RETURNED`, `CANCELED`
- [x] `recipientSnapshot`
- [x] `shippingFee`
- [x] `trackingNumber`
- [x] `carrier`
- [x] `adminNote`
- [x] `requestedAt`
- [x] `shippedAt`
- [x] `deliveredAt`
- [x] `createdAt`
- [x] `updatedAt`

### 7.13 Coupon

- [x] `id`
- [x] `code`
- [x] `type`: `POINT_BONUS`, `DISCOUNT`, `FREE_SHIPPING`
- [x] `value`
- [x] `minSpend`
- [x] `usageLimit`
- [x] `usedCount`
- [x] `startsAt`
- [x] `endsAt`
- [x] `status`
- [x] `createdAt`
- [x] `updatedAt`

### 7.14 AuditLog

- [x] `id`
- [x] `actorId`
- [x] `actorRole`
- [x] `action`
- [x] `entityType`
- [x] `entityId`
- [x] `before`
- [x] `after`
- [x] `ipAddress`
- [x] `createdAt`

### 7.15 Content / Banner / News

- [x] `Banner.id`
- [x] `Banner.title`
- [x] `Banner.imageUrl`
- [x] `Banner.href`
- [x] `Banner.position`
- [x] `Banner.status`
- [x] `News.id`
- [x] `News.title`
- [x] `News.slug`
- [x] `News.content`
- [x] `News.status`
- [x] `News.publishedAt`

---

## 8. API 設計

### 8.1 Public API

- [x] `GET /api/campaigns` 取得賞池列表。
- [x] `GET /api/campaigns/:slug` 取得賞池詳情。
- [x] `GET /api/campaigns/:slug/probabilities` 取得即時機率。MVP 範圍：相容端點，回傳與賞池詳情相同的剩餘票券來源與獎項機率。
- [x] `GET /api/leaderboard` 取得抽況 LIVE 與熱門賞池榜。MVP 採合併端點；若後續流量需要，再拆 `live-draws` / `popular-campaigns`。
- [x] `GET /api/news` 取得公告列表。
- [x] `GET /api/news/:slug` 取得公告詳情。
- [x] `GET /api/banners` 取得啟用 Banner 列表。MVP 範圍：首頁 `HOME_HERO`。

### 8.2 Auth API

- [x] `POST /api/auth/register`
- [x] `POST /api/auth/login`
- [x] `POST /api/auth/logout`
- [x] `POST /api/auth/forgot-password`
- [x] `POST /api/auth/reset-password`
- [x] `GET /api/me`。MVP 範圍：相容端點，代理目前的 `GET /api/auth/me`。

### 8.3 Wallet API

- [x] `GET /api/wallet` 查詢餘額。MVP 範圍：相容端點，代理 `GET /api/account/wallet`。
- [x] `GET /api/wallet/ledger` 查詢點數流水。MVP 範圍：相容端點，代理 `GET /api/account/wallet/ledger`。
- [x] `POST /api/payments/top-up` 建立儲值訂單。MVP 範圍：相容端點，代理 `POST /api/account/payment-orders`。
- [x] `POST /api/payments/mock/complete` 開發環境完成假付款。MVP 範圍：相容端點，接受 `orderId` 並代理既有 mock complete flow。
- [x] `POST /api/webhooks/payment/:provider` 金流回呼。MVP 範圍：`POST /api/webhooks/payment/mock` signed webhook sandbox，支援 PAID / FAILED / CANCELED、重送與金額不一致處理。

### 8.4 Draw API

- [x] `POST /api/draw-orders` 建立抽賞訂單並抽籤。MVP 路徑：`POST /api/account/draw-orders`，支援折扣券套用與使用紀錄。
- [x] `GET /api/account/orders` 查詢會員付款與抽賞訂單列表。MVP 範圍：依目前登入會員過濾，回傳最近 50 筆抽賞訂單與付款訂單。
- [x] `GET /api/draw-orders/:id` 查詢抽賞訂單。MVP 範圍：相容端點，只回目前登入會員自己的抽賞訂單；目前主路徑另支援 `GET /api/account/draw-orders/:id`。
- [x] `GET /api/draw-results/:id` 查詢單次結果。MVP 範圍：相容端點，只回目前登入會員自己的單筆抽賞結果。

### 8.5 Prize Box API

- [x] `GET /api/user-prizes` 查詢我的戰利品。MVP 路徑：`GET /api/account/prizes`。
- [x] `POST /api/shipments` 申請出貨。MVP 路徑：`POST /api/account/shipments`。
- [x] `GET /api/shipments` 查詢出貨紀錄。MVP 路徑：`GET /api/account/shipments`。
- [x] `GET /api/account/notifications` 查詢站內通知。
- [x] `PATCH /api/account/notifications/:id/read` 標記通知已讀。
- [x] `GET /api/account/coupons` 查詢可用優惠券。
- [x] `POST /api/account/coupons/:id/redeem` 領取贈點券。MVP 範圍：`POINT_BONUS` 直接入會員贈點錢包並寫入流水。
- [x] `GET /api/shipments/:id` 查詢出貨詳情。MVP 路徑：`GET /api/account/shipments/{shipmentId}`，依登入會員隔離資料。

### 8.6 Profile API

- [x] `GET /api/profile`。MVP 路徑：`GET /api/account/profile`，依登入 session 回傳目前會員 profile。
- [x] `PATCH /api/profile`。MVP 路徑：`PATCH /api/account/profile`，支援更新顯示名稱與手機；既有 `PUT /api/account/profile` 保留相容。
- [x] `GET /api/addresses`。MVP 範圍：相容端點，代理 `GET /api/account/addresses`。
- [x] `POST /api/addresses`。MVP 範圍：相容端點，代理 `POST /api/account/addresses`。
- [x] `PATCH /api/addresses/:id`。MVP 範圍：相容端點，代理目前地址更新 flow；`PUT /api/addresses/:id` 亦保留。
- [x] `DELETE /api/addresses/:id`。MVP 範圍：相容端點，代理 `DELETE /api/account/addresses/:id`。

### 8.7 Admin API

- [x] `GET /api/admin/campaigns`
- [x] `POST /api/admin/campaigns`
- [x] `PATCH /api/admin/campaigns/:id`
- [x] `GET /api/admin/campaigns/:id/prizes`
- [x] `POST /api/admin/campaigns/:id/prizes`
- [x] `PATCH /api/admin/campaigns/:id/prizes/:prizeId`
- [x] `POST /api/admin/campaigns/:id/publish`
- [x] `POST /api/admin/campaigns/:id/pause`
- [x] `POST /api/admin/campaigns/:id/correction-version`
- [x] `POST /api/admin/campaigns/:id/tickets/generate`
- [x] `GET /api/admin/payment-orders`
- [x] `GET /api/admin/payment-orders/:id`。MVP 範圍：付款訂單詳情含 provider payload 與 webhook events。
- [x] `GET /api/admin/wallet-ledger`
- [x] `GET /api/admin/draw-orders`
- [x] `GET /api/admin/draw-orders/:id`。MVP 範圍：單筆抽賞詳情含冪等鍵、結果、ticket serial、random proof 與點數流水。
- [x] `GET /api/admin/users`
- [x] `PATCH /api/admin/users/:id/status`
- [x] `PATCH /api/admin/users/:id/role`
- [x] `GET /api/admin/shipments`
- [x] `PATCH /api/admin/shipments/:id`
- [x] `POST /api/admin/wallet-adjustments`
- [x] `GET /api/admin/approval-requests`
- [x] `POST /api/admin/approval-requests/wallet-adjustments`
- [x] `POST /api/admin/approval-requests/payment-refunds/:orderId`
- [x] `POST /api/admin/approval-requests/compensations/:userId`
- [x] `POST /api/admin/approval-requests/:id/approve`
- [x] `POST /api/admin/approval-requests/:id/reject`
- [x] `GET /api/admin/banners`
- [x] `POST /api/admin/banners`
- [x] `PATCH /api/admin/banners/:id`
- [x] `GET /api/admin/coupons`
- [x] `POST /api/admin/coupons`
- [x] `PATCH /api/admin/coupons/:id`
- [x] `GET /api/admin/news`
- [x] `POST /api/admin/news`
- [x] `PATCH /api/admin/news/:id`
- [x] `GET /api/admin/audit-logs`
- [x] `GET /api/admin/reports/dashboard` 取得後台 Dashboard。MVP 路徑：`GET /api/admin/dashboard`。

---

## 9. 關鍵流程規格

### 9.1 賞池建立流程

- [x] 管理員建立賞池草稿。
- [x] 填寫基本資訊：標題、slug、描述、來源類型、IP/品牌、價格、銷售時間。
- [x] 上傳封面與商品圖。MVP：賞池媒體區可上傳封面與 Banner 圖，獎項表單可上傳獎項圖，皆沿用後台圖片上傳 API 並保留手動貼 URL。
- [x] 新增獎項等級與數量。MVP：整合於 `/admin/campaigns/[id]/tickets`。
- [x] 設定最後賞。MVP：最後賞不生成普通 ticket。
- [x] 系統自動計算總籤數。MVP：依非最後賞獎項與已生成 tickets 同步。
- [x] 管理員確認總籤數與獎項數量。MVP：後台顯示獎項剩餘、已生成與可抽 tickets。
- [x] 系統產生 ticket 清單。MVP：依獎項數量補產生缺少的 `AVAILABLE` tickets。
- [x] 系統產生 seed hash 或 audit metadata。
- [x] 賞池狀態從 `DRAFT` 變 `SCHEDULED` 或 `LIVE`。MVP：後台發布命令將有可抽 ticket 的賞池切為 `LIVE`。
- [x] 發布後不可任意變更已生成 ticket 對應獎項。MVP：`LIVE` / `PAUSED` / `SOLD_OUT` / `ENDED` 賞池禁止獎項新增/修改與 ticket 補生成，阻擋動作寫入 audit。
- [x] 若必須更正，須走「停用賞池 -> 建立修正版本 -> audit log」流程。MVP 範圍：`POST /api/admin/campaigns/:id/correction-version` 會停用 LIVE/SCHEDULED 原賞池、複製主檔與獎項到 DRAFT 修正版，不複製 tickets/抽賞紀錄並寫 audit。

完成條件：

- [x] 建立一個測試賞池後，前台可看到正確剩餘數與機率。MVP：建立獎項、生成 ticket、發布後前台清單可見。
- [x] 後台可查看 ticket 清單。MVP 範圍：`GET /api/admin/campaigns/:id/tickets` 與 `/admin/campaigns/:id/tickets` 可查看完整 ticket 序號、狀態、獎項、draw id 與遮罩抽出者 email。
- [x] 發布後修改敏感欄位會被阻擋或要求特殊權限。MVP：已開抽、暫停、完抽、結束或已有抽賞紀錄的賞池，禁止直接修改 slug、價格、總籤數、狀態、開賣時間、公平性與最後賞設定，並記錄 blocked audit。

### 9.2 抽賞流程

- [x] 使用者進入賞池詳情頁。
- [x] 使用者確認價格、剩餘數、機率、獎項、最後賞、出貨規則。
- [x] 使用者選擇抽數。
- [x] 前端檢查登入狀態。
- [x] 前端檢查點數餘額。
- [x] 使用者按下抽賞。
- [x] 前端送出 `idempotencyKey`。
- [x] 後端開啟 DB transaction。
- [x] 後端重新讀取使用者 wallet 並鎖定。
- [x] 後端確認點數足夠。
- [x] 後端重新讀取 campaign 狀態與 remainingTickets。
- [x] 後端確認剩餘籤數足夠。
- [x] 後端扣點並寫入 WalletLedger。
- [x] 後端用安全亂數抽取指定數量的 `AVAILABLE` tickets。
- [x] 後端把 ticket 標記為 `DRAWN`。
- [x] 後端扣 Prize.remainingQuantity。
- [x] 後端扣 Campaign.remainingTickets。
- [x] 後端建立 DrawOrder。
- [x] 後端建立 DrawResult。
- [x] 後端建立 UserPrize。
- [x] 若抽完後 remainingTickets = 0，處理最後賞歸屬。
- [x] 後端 commit。
- [x] 前端顯示抽賞動畫。MVP 範圍：抽賞送出後顯示 ticket 抽取動畫，結果回來後逐張揭曉並可略過。
- [x] 前端顯示結果。
- [x] 前端更新錢包與戰利品。

完成條件：

- [x] 單抽成功。
- [x] 10 連抽成功。
- [x] 餘額不足不可抽。
- [x] 剩餘籤數不足不可抽。
- [x] 同一 ticket 不會重複抽出。
- [x] 重複送出同一 `idempotencyKey` 不會扣兩次點數。

### 9.3 最後賞流程

- [x] 賞池設定 `hasLastPrize = true`。
- [x] 最後一張普通 ticket 被抽出時觸發最後賞。
- [x] 系統把最後賞加入該次 DrawOrder 的結果或額外 UserPrize。
- [x] 結果頁顯示「獲得最後賞」。
- [x] 後台紀錄最後賞歸屬。
- [x] 賞池狀態改為 `SOLD_OUT`。

完成條件：

- [x] 測試剩 1 抽時抽出可正確給最後賞。
- [x] 測試多抽包含最後一抽時可正確給最後賞。
- [x] 沒有最後賞的賞池不會誤發。

### 9.4 儲值流程

- [x] 使用者選擇儲值方案。MVP 範圍：會員錢包頁提供 starter/value/collector 儲值方案，可依設定走 Mock 或 ECPay。
- [x] 系統建立 PaymentOrder。MVP 範圍：`POST /api/account/payment-orders` 建立 `PENDING` payment order、provider 與 merchant trade no；ECPay 訂單號符合 20 字元英數限制。
- [x] 前端導向金流或顯示金流表單。MVP 範圍：Mock 導向 `/payment/mock/:orderId` sandbox；ECPay 呼叫 `/api/account/payment-orders/:id/ecpay-checkout` 後以 hidden form POST 到 AioCheckOut。
- [x] 金流付款完成後呼叫 webhook。MVP 範圍：Mock 可呼叫 JSON webhook；ECPay ReturnURL 支援 form-urlencoded server-side callback。
- [x] 後端驗證 webhook 簽章。MVP 範圍：Mock 驗 `X-LuckyBox-Signature` HMAC-SHA256；ECPay 驗 `CheckMacValue` SHA256，錯誤不記錄事件、不入點。
- [x] 後端檢查 PaymentOrder 尚未入帳。MVP 範圍：只對 `PENDING` 訂單執行付款轉態與入點，已處理訂單保持冪等。
- [x] 後端更新 PaymentOrder 為 `PAID`。MVP 範圍：PAID webhook 以 provider + merchantTradeNo 條件更新 pending 訂單。
- [x] 後端增加 Wallet balance。MVP 範圍：重用 `WalletService` 入點流程，加現金點與贈點。
- [x] 後端寫入 WalletLedger。MVP 範圍：入點寫 `TOP_UP` / `TOP_UP_BONUS` ledger，首儲加贈沿用既有流水。
- [x] 使用者回到儲值完成頁。MVP 範圍：mock checkout 成功頁提供回錢包與訂單紀錄連結，回錢包時顯示付款完成狀態並刷新點數。

完成條件：

- [x] webhook 重送不會重複入點。MVP 範圍：`PaymentWebhookApiTests` 驗證 duplicate event 只入點一次。
- [x] 金流失敗不會入點。MVP 範圍：FAILED webhook 將 pending 訂單標為 FAILED，後續不可 mock 完成付款。
- [x] 金額不一致會拒絕。MVP 範圍：amount mismatch 記錄為 `AMOUNT_MISMATCH`，訂單維持 PENDING 且不入點。
- [x] 後台可查詢付款與入點紀錄。MVP 範圍：後台付款訂單列表與 wallet ledger 列表可查付款與入點流水。

### 9.5 戰利品出貨流程

- [x] 使用者進入我的戰利品。
- [x] 使用者勾選要出貨的商品。
- [x] 系統檢查商品狀態都是 `IN_BOX`。
- [x] 使用者選擇地址或新增地址。
- [x] 系統計算運費。
- [x] 使用者確認出貨。
- [x] 系統扣運費或使用免運券。
- [x] 系統建立 Shipment。
- [x] 系統把 UserPrize 狀態改為 `SHIPMENT_REQUESTED`。
- [x] 後台人員包貨。
- [x] 後台填入物流商與追蹤碼。
- [x] 系統通知使用者。
- [x] 使用者可查看出貨狀態。MVP 範圍：會員可於 `/account/shipments/:shipmentId` 查看狀態、物流商、追蹤碼與時間線。

完成條件：

- [x] 同一戰利品不可重複申請出貨。
- [x] 已出貨商品不可取消。
- [x] 後台更新追蹤碼會留下 audit log。

---

## 10. UI/UX 規格

### 10.1 視覺方向

- [x] 行動裝置優先。
- [x] 首頁不是行銷 landing page，而是直接可瀏覽/可抽賞的商店。
- [x] 色彩可活潑，但資訊區塊要清楚。
- [x] 商品卡要穩定尺寸，避免圖片載入造成 layout shift。
- [x] 抽賞按鈕要醒目，但旁邊要有價格與剩餘抽數。
- [x] 避免大量紫藍漸層或單一色系導致同質化。
- [x] 使用 icon 表示搜尋、篩選、排序、錢包、戰利品、出貨、客服。
- [x] 所有按鈕文字在手機上不可溢出。

### 10.2 首頁模組

- [x] 頂部導覽：Logo、搜尋、錢包、登入/會員、戰利品。MVP 範圍：桌機頂部導覽與手機頂部搜尋列可直接搜尋賞池，送出後導向首頁賞池區並以 URL query 保留搜尋條件；導覽仍顯示錢包、登入 / 會員、戰利品與後台入口。
- [x] 主要 Banner：活動/新品。MVP 範圍：首頁 Hero 讀取啟用中的 `HOME_HERO` Banner，無資料則使用靜態圖。
- [x] 狀態 tabs：開抽中、即將開抽、已完抽。MVP 範圍：首頁賞池區提供全部 / 開抽中 / 即將開抽 / 已完抽 tabs，切換後重新查詢公開賞池。
- [x] 商品分類：官方賞、自製賞、卡牌、盲盒、GK、預購。MVP 範圍：首頁類型篩選支援官方賞、自製賞、自營混套賞、卡牌賞、盲盒賞、GK 賞與預購賞；後台賞池來源類型同步支援 GK。
- [x] 商品列表。MVP 範圍：首頁以 RWD 商品卡列表顯示公開賞池，支援搜尋、狀態 tabs、類型篩選、排序與分頁。
- [x] 抽況 LIVE 跑馬燈。MVP 範圍：首頁 LIVE strip 串接公開 leaderboard API，顯示最近抽出紀錄與全站榜單入口。
- [x] 熱門榜單。MVP 範圍：首頁新增熱門賞池榜，顯示熱門賞池排名、狀態、抽數、玩家數、每抽 LP、售出進度與稀有剩餘提示，並導向完整榜單。
- [x] 新手流程入口。MVP 範圍：首頁新增新手任務入口，導向註冊、賞池列表與出貨政策，串起建立會員、挑選賞池、累積出貨三步。
- [x] 公告入口。MVP 範圍：主導覽、手機底部導覽與 Footer 連到 `/news`。
- [x] 客服入口。MVP 範圍：首頁新增 FAQ、出貨政策與隱私權政策支援入口，連到客服信箱與政策資訊。

### 10.3 商品卡資訊

- [x] 商品圖。MVP 範圍：公開賞池列表 API 回傳 `coverImageUrl`，首頁商品卡使用賞池主圖，未設定或載入失敗時回退預設商品圖。
- [x] 商品狀態 badge。MVP 範圍：首頁商品卡於商品圖上顯示開抽中 / 即將開抽 / 已完抽狀態。
- [x] 來源類型 badge。MVP 範圍：首頁商品卡顯示官方賞、自製賞、混套賞、卡牌賞等來源類型 badge。
- [x] 商品名稱。MVP 範圍：首頁商品卡顯示賞池名稱與副標，長字串可換行不擠壓卡片。
- [x] 每抽價格。MVP 範圍：首頁商品卡以資訊格顯示每抽 LP。
- [x] 剩餘/總抽數。MVP 範圍：首頁商品卡以資訊格顯示剩餘 / 總籤數。
- [x] 進度條。MVP 範圍：首頁商品卡顯示剩餘比例 progress bar 與百分比。
- [x] 最稀有剩餘提示，例如 `A賞剩 1`。MVP 範圍：首頁商品卡顯示後端 `rareHint`，用於提示目前最稀有仍可抽獎項或最後賞狀態。
- [x] 是否有最後賞。MVP 範圍：首頁商品卡於商品圖角落顯示最後賞 badge。
- [x] 開抽 CTA。MVP 範圍：首頁商品卡依狀態顯示「立即開抽 / 查看詳情 / 查看結果」CTA，皆導向賞池詳情頁。

### 10.4 賞池詳情頁

- [x] 商品主圖 gallery。MVP 範圍：公開賞池詳情 API 回傳 `coverImageUrl` / `bannerImageUrl`，前端顯示主圖 gallery 與縮圖切換，未設定圖片時沿用預設圖 fallback。
- [x] 商品名稱與狀態。MVP 範圍：賞池詳情頁右側摘要顯示標題、副標、描述、狀態 badge、來源 badge 與可抽狀態 pill。
- [x] 每抽價格。MVP 範圍：抽賞摘要顯示每抽 LP，並與抽數選擇器即時計算原始扣點。
- [x] 剩餘/總抽數。MVP 範圍：抽賞摘要顯示剩餘籤數 / 總籤數、剩餘比例 progress bar，並於可抽狀態提示中反映完抽或未開放狀態。
- [x] 抽數選擇器：1、3、5、10、自訂。MVP 範圍：賞池詳情頁提供 1 / 3 / 5 / 10 快捷抽數與自訂數字輸入，限制在剩餘籤數內，扣點、確認視窗與送出 API 共用正規化抽數。
- [x] 主要 CTA：立即開抽。MVP 範圍：賞池詳情頁提供主要「立即抽賞」CTA，搭配可抽狀態 callout，未登入導向登入、不可抽時停用。
- [x] 獎項列表。MVP 範圍：賞池詳情頁提供獎項資訊表，包含等級、名稱、描述、剩餘狀態、機率與剩餘比例，手機版改為單欄清單。
- [x] 每個獎項顯示原始數量、剩餘數量、目前機率。MVP 範圍：獎項列顯示剩餘 / 原始數量、依目前剩餘普通 ticket 計算之機率、最後賞條件提示與剩餘比例條。
- [x] 最後賞區塊。MVP 範圍：賞池詳情頁新增獨立最後賞區塊，顯示最後賞品項、觸發條件、普通籤剩餘、最後賞剩餘與可取得狀態。
- [x] 已抽出紀錄。MVP 範圍：賞池詳情頁顯示該賞池最近抽出紀錄，無資料時顯示空狀態，可連至全站抽況榜單。
- [x] 出貨說明。MVP 範圍：賞池詳情頁新增出貨政策卡，顯示本賞池出貨備註、合併出貨、地址 / 免運券與出貨追蹤提示，並導向完整出貨政策與戰利品盒。
- [x] 退換貨說明。MVP 範圍：賞池詳情頁新增退換貨政策卡，顯示本賞池退換貨備註、不可因個人喜好取消、瑕疵保留照片與客服查核提示，並導向完整政策與客服。
- [x] 公平性說明。MVP 範圍：賞池詳情政策區新增公平性入口，導向 `/fairness` 說明後端抽籤、ticket serial、交易與查核流程。
- [x] FAQ。MVP 範圍：賞池詳情頁新增抽賞前常見問題，涵蓋扣點、剩餘數與即時機率、最後賞、戰利品盒、合併出貨與客服查核導流。

### 10.5 抽賞頁

- [x] 抽賞前確認 modal。MVP 範圍：點擊「立即抽賞」後先開啟確認視窗，按下「確認抽賞」才送出抽賞 API。
- [x] 顯示抽數、總點數、抽後預估餘額。MVP 範圍：確認視窗顯示賞池、抽數、原始扣點、目前餘額與未計折扣的抽後預估餘額。
- [x] 顯示「抽賞後不可取消」提醒。MVP 範圍：確認視窗以警示區塊提醒抽賞送出後立即扣點並抽取 ticket，完成後不可取消或復原。
- [x] 抽賞動畫。MVP 範圍：送出確認後顯示抽取 ticket 動畫與狀態說明，等待後端回傳結果。
- [x] 可略過動畫按鈕。MVP 範圍：結果揭曉中提供「略過揭曉」按鈕，可立即顯示全部結果卡。
- [x] 結果卡片逐張揭曉。MVP 範圍：抽賞成功後依序揭曉結果卡，並顯示已揭曉張數。
- [x] 高稀有結果有特殊動畫。MVP 範圍：S / A / LAST 結果卡使用稀有樣式、標籤與揭曉動畫。
- [x] 結果後 CTA：繼續抽、查看戰利品、申請出貨、分享。MVP 範圍：揭曉完成後提供繼續抽、查看戰利品、申請出貨與複製分享文字入口。

### 10.6 會員中心

- [x] 錢包餘額。MVP 範圍：會員中心第一屏讀取 `/api/account/wallet` 顯示可用總點數、現金點與贈點，並導向完整錢包頁。
- [x] 快速儲值。MVP 範圍：會員中心顯示前兩個儲值方案，可直接建立並完成 Mock 付款訂單，成功後刷新錢包、session 與總覽資料。
- [x] 最近抽賞。MVP 範圍：會員中心讀取 `/api/account/orders` 顯示最近三筆抽賞訂單、狀態、抽數、實付 LP 與完成時間，並導向賞池或完整訂單頁。
- [x] 戰利品數量。MVP 範圍：會員中心讀取 `/api/account/prizes` 顯示戰利品總數與可申請出貨數。
- [x] 待出貨數量。MVP 範圍：會員中心以戰利品狀態與 `/api/account/shipments` 顯示待處理出貨品項與已出貨單數。
- [x] 優惠券。MVP 範圍：會員中心讀取 `/api/account/coupons` 顯示可用優惠券、贈點券與免運券數量，並導向優惠券頁。
- [x] 會員等級。MVP 範圍：會員中心會員資料卡醒目顯示目前 `vipLevel`，並保留角色、狀態與個人資料入口。
- [x] 客服入口。MVP 範圍：會員中心新增 FAQ、出貨紀錄、出貨/退換貨政策與客服信箱入口。

### 10.7 後台

- [x] 賞池列表支援搜尋、狀態篩選、排序。MVP 範圍：後台賞池列表支援 slug / 標題 / 品牌搜尋、狀態篩選與排序（最新建立、最近更新、營運狀態、標題、價格高低、剩餘數高低），後端以白名單 `sort` 參數產生排序 SQL。
- [x] 賞池編輯採分段表單。MVP 範圍：後台賞池主檔表單拆成基本資料、商品與銷售、媒體與說明、出貨與售後、公平性與最後賞五段，保留同一個儲存流程並補上 Banner 圖 URL 欄位。
- [x] 獎項設定要有即時計算總籤數。MVP 範圍：後台獎項編輯表單即時計算儲存後普通獎項總籤數，依是否最後賞排除最後賞數量，並提示與賞池總抽數相符、尚差或超出。
- [x] 危險操作要二次確認。MVP 範圍：後台賞池的發布上架、暫停下架與生成 Ticket 操作送出前先跳出確認，避免誤觸造成前台狀態或 ticket 數量變動。
- [x] 發布前要有 checklist。MVP 範圍：後台賞池發布區顯示主檔資料、公開說明、獎項數量、Ticket 生成、最後賞與可發布狀態六項 checklist；發布按鈕需 checklist 全部通過才可送出。
- [x] 儀表板顯示今日營收、抽數、活躍會員、未出貨、客服待處理。MVP 範圍：`/admin` 儀表板透過 `/api/admin/dashboard` 顯示今日營收、今日抽數、今日活躍會員、未出貨與客服待處理指標；客服待處理先統計待審願望與失敗付款。

---

## 11. 詳細開發階段

### Phase 0：專案初始化

- [x] 建立專案根目錄結構。
- [x] 建立 `frontend/` VueJS 專案。
- [x] 建立 `backend/` Java Spring 專案。
- [x] 前端安裝 Bootstrap 5。
- [x] 前端安裝 Vue Router。
- [x] 前端安裝 Pinia。
- [x] 前端設定 ESLint。
- [x] 前端設定 Prettier。
- [x] 後端設定 Maven 或 Gradle，建議 Maven。
- [x] 後端加入 Spring Web / Spring MVC。
- [x] 後端加入 Spring Security。
- [x] 後端加入 Spring Validation。
- [x] 後端加入 SQLite JDBC driver。
- [x] 後端加入 Flyway 或 Liquibase。
- [x] 後端加入 JUnit 5。
- [x] 後端加入 Mockito。
- [x] 後端加入 MockMvc 測試設定。
- [x] 設定環境變數範本 `.env.example`。
- [x] 設定 Git ignore。
- [x] 建立 README。
- [x] 建立 `docs/` 資料夾。
- [x] 建立 `docs/architecture.md`。
- [x] 建立 `docs/api.md`。
- [x] 建立 `docs/testing.md`。
- [x] 建立 `docs/operations.md`。
- [x] 設定前端單元測試工具，可用 Vitest，但測試目標必須是 Vue/JavaScript。
- [x] 設定 E2E 測試工具，可用 Playwright 或 Selenium。
- [x] 設定 CI：前端 lint/test/build、後端 test/package。

完成條件：

- [x] `cd frontend && npm run lint` 通過。
- [x] `cd frontend && npm run test` 通過。
- [x] `cd frontend && npm run build` 通過。
- [x] `cd backend && ./mvnw test` 通過。
- [x] `cd backend && ./mvnw package` 通過。
- [x] Spring 後端可在本機啟動。
- [x] 首頁可在本機打開。

### Phase 1：資料庫與基礎架構

- [x] 設定 SQLite 資料庫檔案路徑。
- [x] 啟用 SQLite WAL mode。
- [x] 設定 SQLite busy timeout。
- [x] 建立初版 SQL schema。
- [x] 建立 Flyway/Liquibase migration。
- [x] 建立 seed runner 或 Spring profile `dev` seed script。
- [x] seed 一個管理員帳號。
- [x] seed 三個測試賞池。
- [x] seed 每個賞池的 prizes。
- [x] seed 每個賞池的 tickets。
- [x] 建立 Spring service transaction boundary。
- [x] 建立 repository/DAO layer。
- [x] 建立 SQLite transaction/concurrency 測試。
- [x] 建立 server-side logger。
- [x] 建立 audit log helper。
- [x] 建立錯誤處理格式。

完成條件：

- [x] Spring 啟動時 migration 可成功執行。
- [x] Dev seed 可成功執行。
- [x] DB 中有可抽賞池。
- [x] Seed 後總籤數與獎項數量一致。

### Phase 2：設計系統與 Layout

- [x] 建立全站 layout。
- [x] 建立 Header。
- [x] 建立 Mobile bottom navigation。
- [x] 建立 Footer。
- [x] 建立 Button。
- [x] 建立 Badge。
- [x] 建立 Card。
- [x] 建立 Modal/Dialog。
- [x] 建立 Tabs。
- [x] 建立 Progress。
- [x] 建立 Toast。
- [x] 建立 Empty State。
- [x] 建立 Loading Skeleton。
- [x] 建立 Error State。
- [x] 建立 Form components。
- [x] 建立 responsive grid。
- [x] 建立圖像比例與 fallback。

完成條件：

- [x] Desktop 1440px 畫面正常。
- [x] Tablet 768px 畫面正常。
- [x] Mobile 390px 畫面正常。
- [x] 沒有明顯文字溢出。
- [x] 沒有 UI 元件互相重疊。

### Phase 3：會員系統

- [x] 建立註冊頁。
- [x] 建立登入頁。
- [x] 建立登出功能。
- [x] 建立 session。
- [x] 密碼 hash。
- [x] 表單驗證。
- [x] 錯誤訊息。
- [x] 建立忘記密碼流程。MVP 範圍：後端 `POST /api/auth/forgot-password`（產一次性 token、存 SHA-256 hash、30 分過期、永遠回 202 不洩漏 email 是否存在）與 `POST /api/auth/reset-password`（驗 token/過期/已用、BCrypt 改密、單次使用），含 `PasswordResetApiTests`。前端 `/forgot-password`、`/reset-password` 表單與登入頁「忘記密碼？」入口已完成；重設信已透過 `EmailService` 接入真實 SMTP / dev log fallback，正式供應商帳密屬上線環境設定。
- [x] 建立會員中心頁。
- [x] 建立個人資料編輯。
- [x] 建立地址 CRUD。
- [x] 後台角色判斷 middleware。
- [x] 前台登入保護 middleware。

完成條件：

- [x] 使用者可註冊。
- [x] 使用者可登入。
- [x] 使用者可登出。
- [x] 未登入不能抽賞。
- [x] 一般使用者不能進入後台。

### Phase 4：賞池列表與詳情

- [x] 建立 campaigns query。
- [x] 建立商品列表頁。
- [x] 建立商品卡。
- [x] 建立搜尋功能。
- [x] 建立分類篩選。
- [x] 建立狀態篩選。
- [x] 建立排序：最新、熱門、價格低到高、價格高到低、剩餘少到多。
- [x] 建立分頁或 infinite scroll。
- [x] 建立賞池詳情頁。
- [x] 顯示獎項列表。
- [x] 顯示即時剩餘數。
- [x] 顯示即時機率。
- [x] 顯示最後賞。
- [x] 顯示已抽出紀錄。
- [x] 顯示出貨/退換貨說明。

完成條件：

- [x] 前台能看到 seed 賞池。
- [x] 每個賞池詳情數字與 DB 一致。
- [x] 賞池售完時不可抽。
- [x] 暫停賞池不可抽。

### Phase 5：錢包與 Mock 儲值

- [x] 建立 Wallet model。
- [x] 新會員自動建立 wallet。
- [x] 建立錢包頁。
- [x] 顯示現金點/贈點。
- [x] 建立點數流水頁。
- [x] 建立儲值方案設定。
- [x] 建立 Mock PaymentOrder。
- [x] 建立 Mock 付款完成 API。
- [x] 付款完成後入點。
- [x] 寫入 WalletLedger。
- [x] 建立後台付款訂單列表。MVP 範圍：付款訂單列表、狀態/provider/關鍵字篩選、金額與點數摘要。

完成條件：

- [x] 使用者可 mock 儲值。
- [x] 儲值後餘額正確增加。
- [x] Ledger 顯示正確。
- [x] 重複完成同一 payment 不會重複入點。

### Phase 6：抽賞核心

- [x] 建立抽賞 API。
- [x] 建立抽賞 transaction。
- [x] 實作點數扣除。
- [x] 實作 ticket 隨機抽取。
- [x] 實作 prize/campaign remaining 扣減。
- [x] 實作 DrawOrder。
- [x] 實作 DrawResult。
- [x] 實作 UserPrize。
- [x] 實作 idempotency。
- [x] 實作餘額不足錯誤。
- [x] 實作剩餘不足錯誤。
- [x] 實作最後賞。
- [x] 建立抽賞頁 UI。
- [x] 建立抽賞確認 modal。MVP 範圍：已於賞池詳情頁建立二次確認視窗，確認後才送出抽賞 API。
- [x] 建立抽賞結果 UI。
- [x] 建立抽賞動畫。MVP 範圍：抽賞送出與結果揭曉皆以 Vue 狀態與 CSS animation 呈現，支援略過揭曉。
- [x] 建立抽完後前台資料刷新。

完成條件：

- [x] 單抽扣點正確。
- [x] 多抽扣點正確。
- [x] 抽中獎品進戰利品。
- [x] 剩餘數與機率即時更新。
- [x] 高併發測試不重複抽同 ticket。
- [x] 最後賞測試通過。

### Phase 7：戰利品與出貨

- [x] 建立我的戰利品頁。
- [x] 支援依狀態篩選。
- [x] 支援依賞池篩選。
- [x] 支援選取多個戰利品。
- [x] 建立出貨申請流程。
- [x] 建立地址選擇。
- [x] 建立運費計算。
- [x] 建立免運券套用介面。MVP 範圍：申請出貨時可選可用免運券並將運費折抵為 0。
- [x] 建立 Shipment。
- [x] 更新 UserPrize 狀態。
- [x] 建立出貨紀錄頁。
- [x] 後台出貨列表。
- [x] 後台更新物流狀態。
- [x] 後台填追蹤碼。
- [x] 通知使用者出貨。

完成條件：

- [x] 使用者可申請出貨。
- [x] 出貨後戰利品狀態正確。
- [x] 後台可處理出貨。
- [x] 同一商品不能重複出貨。

### Phase 8：後台 MVP

- [x] 建立後台登入。
- [x] 建立後台 layout。
- [x] 建立後台 Dashboard。
- [x] 建立賞池 CRUD。MVP 範圍：賞池主檔列表、新增、編輯。
- [x] 建立獎項 CRUD。MVP 範圍：賞池內獎項列表、新增、編輯、最後賞註記與數量保護。
- [x] 建立 ticket 生成。MVP 範圍：依非最後賞獎項補產生可抽 tickets 並同步賞池抽數。
- [x] 建立賞池發布流程。MVP 範圍：需有可抽 ticket 才可發布為 `LIVE`，並寫入 audit log。
- [x] 建立賞池暫停/下架。MVP 範圍：`LIVE` / `SCHEDULED` 可暫停為 `PAUSED` 並從前台清單下架。
- [x] 建立會員列表。MVP 範圍：搜尋、狀態/角色篩選、點數/抽賞/戰利品/出貨摘要、遮罩聯絡資訊。
- [x] 建立會員詳情。MVP 範圍：`GET /api/admin/users/:id` 回傳單一會員的錢包餘額、活動統計（抽賞/消費/儲值/戰利品/出貨）、收件地址與近期點數流水，email/手機/地址預設遮罩；`?reveal=true` 才回完整個資並寫 `ADMIN_MEMBER_DETAIL_VIEWED` audit（操作者/時間/對象）。前台 `/admin/users/:id` 詳情頁 + 列表「查看明細」入口。
- [x] 建立訂單列表。MVP 範圍：付款訂單、會員遮罩 email、金額、點數、付款狀態與付款時間；詳情可查 provider payload 與 webhook events。
- [x] 建立抽賞紀錄列表。MVP 範圍：訂單、會員、賞池、抽數、點數花費與獎項摘要；單筆詳情可查冪等鍵、抽賞結果、ticket serial、random proof 與點數流水。
- [x] 建立點數流水列表。MVP 範圍：流水類型、點數種類、來源、會員遮罩 email、異動後餘額與原因。
- [x] 建立出貨管理。
- [x] 建立公告管理。MVP 範圍：後台公告 CRUD、公開公告列表/詳情、發布/封存狀態與 audit log。
- [x] 建立 banner 管理。MVP 範圍：後台 Banner CRUD、啟用/封存、首頁主視覺公開讀取與 audit log。
- [x] 建立 audit log 頁。MVP 範圍：actor role、action、entity、keyword、limit 篩選，顯示 before/after 狀態摘要。

完成條件：

- [x] 管理員可以完整建立一個可抽賞池。
- [x] 管理員可以處理出貨。
- [x] 管理員可以查所有交易與抽賞紀錄。
- [x] 後台操作都有權限檢查。

### Phase 9：透明公平與審計

- [x] 每個賞池發布時保存 `seedHash`。MVP 範圍：HASH_COMMIT_REVEAL 模式發布時以 SecureRandom 產生 server seed，承諾 `seed_hash = SHA-256(seed)`（取代原佔位字串），seed 存於新增的 `kuji_campaigns.server_seed`（V5）。
- [x] 每次抽籤保存 random proof。MVP 範圍：每抽以 `HMAC-SHA256(server_seed, "{orderId}:{index}")` 對當下可抽籤數取餘選票，並記錄該 HMAC 為 `random_proof`。
- [x] 每次抽籤保存 ticket serial。
- [x] 完抽後可公開 `revealedSeed`。MVP 範圍：售罄時寫入 `revealed_seed = server_seed`，公開 API 可驗證 `SHA-256(revealedSeed)==seedHash` 並重算每抽 HMAC。
- [x] 建立公平性說明頁。MVP 範圍：新增 `/fairness` 公開頁，說明 ticket 清單、後端抽籤、交易一致性、最後賞、目前控制點與 HASH_COMMIT_REVEAL 可驗證 proof。
- [x] 建立完抽賞池 audit summary。MVP 範圍：admin-only `GET /api/admin/audit-summary/{slug}` 彙整獎項分布（原始/已抽/剩餘）、總抽數、獨立中獎人、訂單數、首末抽時間、最後賞是否發出與 seedHash/reveal 狀態。
- [x] 後台敏感操作寫 audit log。MVP：賞池發布/暫停、獎項/ticket 生成、會員個資查閱、付款退款、補償與本次敏感欄位/獎項 blocked attempts 皆寫 audit。
- [x] 後台可查 actor、action、before、after。
- [x] 建立異常偵測：剩餘數與 ticket 狀態不一致。MVP 範圍：`ConsistencyService` 檢查 remaining_tickets vs AVAILABLE 票數、DRAWN 票 vs draw_results、HASH 售罄未 reveal、prize remaining_quantity vs 可抽票，公開 `GET /api/admin/consistency`（admin-only）。
- [x] 建立每日一致性檢查 job。MVP 範圍：`@Scheduled`（預設每日 03:00 Asia/Taipei，可由 `luckybox.consistency.cron` 設定）執行掃描並將每筆異常寫入 audit log（`DATA_INCONSISTENCY_DETECTED`）。

完成條件：

- [x] 任一完抽賞池可輸出完整抽籤摘要。MVP 範圍：公開 `GET /api/campaigns/{slug}/fairness` 輸出 fairnessMode、seedHash、revealedSeed、每抽 serial/proof 與驗證演算法說明。
- [x] 系統可偵測資料不一致。MVP 範圍：`GET /api/admin/consistency` 與每日 job 可偵測 remaining/ticket/draw_result/prize/seed-reveal 不一致並寫入 audit log。
- [x] 管理員無法無紀錄修改敏感資料。MVP：已公開/已售賞池的敏感主檔、獎項與 ticket 修改會被 API 阻擋，並以同一交易搭配 `noRollbackFor=ApiException` 保留 blocked audit 紀錄；後台 UI 同步鎖定欄位。

### Phase 10：真實金流串接

- [x] 決定第一個金流供應商。MVP 工程預設：第一個真實台灣金流採 ECPay first；NewebPay 作為合約、費率、結算或串接條件不合時的 fallback；LINE Pay、街口與分期已列 Phase 2 並完成工程 adapter；發票自動化列後續。正式開通仍需商務合約與 provider 文件。
- [x] 建立金流設定文件。MVP 範圍：`docs/api.md` 說明 Mock webhook、ECPay checkout、ECPay ReturnURL callback、secret 設定、狀態與重送語意；`README.md` 記錄 launch readiness。
- [x] 建立 sandbox 環境。MVP 範圍：Mock provider webhook sandbox + ECPay staging action URL 設定；正式 ECPay merchant dashboard 測試需帳號開通後執行。
- [x] 建立付款表單/導頁。MVP 範圍：Mock `/payment/mock/:orderId`；ECPay checkout API 回傳 hidden form fields 供前端 POST 到 ECPay AioCheckOut；LINE Pay / 街口 checkout 回傳 redirect URL 供前端導向 provider。
- [x] 建立 webhook endpoint。MVP 範圍：`POST /api/webhooks/payment/mock`、`POST /api/webhooks/payment/ecpay`、LINE Pay confirm/cancel redirect、街口 confirm/result callback，皆 CSRF 豁免或公開 provider callback、rate limited。
- [x] 驗證金流簽章。MVP 範圍：Mock 驗 `X-LuckyBox-Signature`；ECPay 驗 `CheckMacValue` 並排除錯誤 callback；LINE Pay / 街口 outbound API request 依 provider 規則簽章。
- [x] 處理付款成功。MVP 範圍：Mock/ECPay PAID callback 更新訂單、入現金點/贈點、寫 ledger 與 audit。
- [x] 處理付款失敗。MVP 範圍：Mock FAILED / ECPay 非 `RtnCode=1` 將 pending 訂單標為 FAILED，不入點。
- [x] 處理使用者取消付款。MVP 範圍：CANCELED webhook 將 pending 訂單標為 CANCELED，不入點。
- [x] 處理 webhook 重送。MVP 範圍：`payment_webhook_events` 以 provider + event id 去重，duplicate 回應不再入點。
- [x] 處理金額不一致。MVP 範圍：金額不同時記錄 `AMOUNT_MISMATCH`，訂單狀態與點數不變。
- [x] 後台顯示 provider raw payload。MVP 範圍：`GET /api/admin/payment-orders/:id` 與 `/admin/orders` 詳情展開顯示 provider payload、webhook event raw payload、處理狀態與時間。
- [x] 建立付款 reconciliation script。MVP 範圍：`backend/scripts/reconcile-payments.sh` 對 SQLite payment_orders、payment_webhook_events 與 wallet_ledger 做本機對帳，支援 `--strict` 於異常時 exit 2；`backend/scripts/reconcile-provider-payments.py` 可匯入 provider CSV export，依 provider、merchant trade no、金額與狀態比對本機 payment_orders。

完成條件：

- [x] Sandbox 信用卡付款成功可入點。MVP 範圍：Mock checkout 由後端產生 signed webhook；ECPay callback 測試以正式 form callback shape 驗證 PAID 入點。
- [x] Webhook 重送不重複入點。MVP 範圍：後端測試涵蓋 Mock event id、ECPay TradeNo、LINE Pay transactionId 與街口 tradeNo 重送後 wallet ledger 不重複。
- [x] 金額竄改會拒絕。MVP 範圍：後端測試涵蓋 Mock / ECPay webhook amount 與訂單金額不一致時不入點。
- [x] 金流設定不會出現在前端 bundle。MVP 範圍：Mock secret 與 ECPay HashKey/HashIV 僅存在 Spring `application.properties` / 環境變數，前端只接收 ECPay checkout 必要欄位。

### Phase 11：活動與留存

- [x] 建立公告系統。MVP 範圍：公開公告列表/詳情與後台公告 CRUD；活動排程上下架已於後續完成（見 Banner/News 排程）。
- [x] 建立首頁 banner。MVP 範圍：公開 `HOME_HERO` Banner 串接首頁 Hero；排程上下架已於後續完成。
- [x] 建立優惠券。MVP 範圍：後台優惠券 CRUD、會員可用優惠券列表、折扣券於抽賞流程套用、贈點券領取入錢包，與免運券出貨折抵。
- [x] 建立首儲活動。MVP 範圍：首次儲值（第一筆 PAID 付款訂單）完成時額外贈送設定化的首儲紅利（`luckybox.promo.first-deposit-bonus`，dev 預設 100、可由環境變數覆寫；測試 profile 預設 0 不影響既有錢包測試），寫入 `FIRST_DEPOSIT_BONUS` 流水 + audit，僅發放一次；錢包總覽 `firstDepositPromo` 回傳贈點額度與是否符合資格供前端宣傳；後台點數帳本支援該型別與「首儲贈點」標籤。含 `FirstDepositPromoTests`。前端錢包頁已加首儲促銷橫幅（teal callout：「首儲再送 N LP」+ pill），僅對符合資格者顯示。
- [x] 建立滿額贈點。MVP 範圍：消費門檻紅利 —— 使用者累積抽賞消費（COMPLETED draw_orders 的 point_spent 總和）首次跨越設定門檻時，一次性贈送紅利（`luckybox.promo.spend-threshold` / `luckybox.promo.spend-threshold-bonus`，dev 預設 1000/80、可由環境變數覆寫；測試 profile 預設 0 不影響既有抽賞測試）。以「跨越前 < 門檻 ≤ 跨越後」判定首次跨越，天然冪等只發一次，寫入 `SPEND_THRESHOLD_BONUS` 流水 + audit；後台點數帳本支援該型別與「消費門檻紅利」標籤。含 `SpendThresholdPromoTests`。前端錢包頁已加滿額贈點橫幅：錢包總覽新增 `spendThresholdPromo`（門檻/紅利/已消費/剩餘/已達成），`WalletView` 顯示 amber 進度條「再消費 X LP 得 Y 紅利」、達標後轉 teal「已入帳」。
- [x] 建立免運券。MVP 範圍：`FREE_SHIPPING` 可於出貨申請折抵固定運費。
- [x] 建立抽況 LIVE。MVP 範圍：`GET /api/leaderboard` 回傳最近 `draw_results`，前台 `/leaderboard` 與首頁 LIVE strip 共用資料。
- [x] 建立歐氣榜。MVP 範圍：`GET /api/leaderboard` 新增 `luckyMembers`（`luckyLimit` 參數，1~20），彙整抽中高稀有度（賞別 S/A 或最後賞）且賞池為 LIVE/SCHEDULED/SOLD_OUT 的會員，依幸運次數→S 賞數→最後賞數排序、後端遮罩顯示名稱不洩漏個資。前台 `/leaderboard` 新增「歐氣榜」面板（金/銀/銅牌名次、S 賞/最後賞標籤、歐氣次數）與「本期歐氣王」指標卡。含 `PublicLeaderboardApiTests` 歐氣榜斷言。
- [x] 建立熱門賞池榜。MVP 範圍：以完成抽數、售出數與狀態排序公開賞池熱度。
- [x] 建立許願牆 MVP。MVP 範圍：V9 `wishes` 表；會員 `POST /api/account/wishes` 投稿（4~200 字、每日上限 5 則）與 `GET /api/account/wishes` 查看自己的願望與審核狀態；公開 `GET /api/wishes` 僅回傳 APPROVED 且作者匿名遮罩；管理員 `GET/PATCH /api/admin/wishes` 列表與審核（核准/隱藏/退回）並寫 audit。投稿是否自動上架由 `luckybox.wish.auto-approve`（dev 預設 true）控制。前台新增 `/wishes` 公開許願牆（投稿表單 + 我的願望 + 匿名牆）、導覽列與頁尾入口；後台新增 `/admin/wishes` 審核頁與側欄入口。含 `WishApiTests`、`WishModerationApiTests`。
- [x] 建立每日登入任務（簽到送點）。MVP 範圍：V8 `daily_check_ins` 表（`user_id`+`check_in_date` 唯一）；`GET /api/account/check-in` 回傳今日是否已簽到、基礎獎勵、連續簽到加碼、連續/累積天數與下一階加碼門檻，`POST /api/account/check-in` 以 Asia/Taipei 日期每日一次發放設定化紅利（`luckybox.promo.daily-check-in-bonus`，dev 預設 20；`luckybox.promo.daily-check-in-streak-bonuses`，dev 預設 `3:30,7:80,14:150,30:500`；測試 profile 預設 0 不影響既有測試），唯一鍵保證冪等、寫入 `CHECK_IN_BONUS` 流水 + audit；後台點數帳本支援該型別與「每日簽到獎勵」標籤。前台會員中心新增每日簽到卡（連續/累積天數、今日可領、加碼提示、立即簽到、已簽到狀態）。含 `CheckInApiTests`。

完成條件：

- [x] 營運可自行新增活動 banner。MVP 範圍：`/admin/banners` 新增/編輯/啟用。
- [x] 優惠券可被套用並記錄。MVP 範圍：折扣券可於抽賞流程折抵 LP，贈點券可領取入錢包，免運券可折抵出貨運費，並寫入 `coupon_usages`。
- [x] 抽況 LIVE 不洩漏完整個資。MVP 範圍：公開 API 僅回傳後端遮罩後的顯示名稱，不回傳 email/phone。

### Phase 12：法遵文件與客服

- [x] 撰寫會員服務條款。MVP 範圍：`/terms` 已公開會員帳號、點數、抽賞、戰利品、優惠券、禁止行為、終止與客服爭議流程。
- [x] 撰寫隱私權政策。MVP 範圍：`/privacy` 已完成公開草案；正式法遵審閱已列外部 launch gate。
- [x] 撰寫出貨政策。MVP 範圍：`/shipping-policy` 已列出合併出貨、固定運費、免運券折抵、出貨批次與物流追蹤。
- [x] 撰寫退換貨/瑕疵處理政策。MVP 範圍：`/shipping-policy` 已列出破損、缺件、錯品與到貨後 7 日內聯繫規則。
- [x] 撰寫抽賞公平性說明。MVP 範圍：`/fairness` 已公開抽賞流程、目前 MVP 控制點、查核資料與 seed/reveal/random proof 驗證能力。
- [x] 撰寫點數使用規則。MVP 範圍：`/terms` 已列出 LP 平台內用途、不可提領/兌現、現金點/贈點/補償與活動限制。
- [x] 撰寫未成年使用規則。MVP 範圍：`/terms` 已列出未成年人或限制行為能力人使用時需取得法定代理人同意。
- [x] 撰寫客服 SLA。MVP 範圍：`/shipping-policy` 已列出客服信箱、處理時段與 1 個工作天內初步回覆。
- [x] 建立 FAQ。MVP 範圍：`/faq` 已提供可搜尋分類題庫、客服入口、出貨/隱私/會員頁面導流。
- [x] 建立聯絡我們頁。MVP 範圍：新增 `/contact`，提供客服信箱、處理時段、mail template、問題分類、寄信前資料清單與 FAQ/政策/會員紀錄導流。
- [ ] 正式上線前交由法律顧問審閱。狀態：`docs/launch-readiness.md` 與 `docs/launch-signoff-register.md` 已列 legal counsel、review 與 feedback-applied gate；實際律師/法律顧問審閱不可由程式替代。

完成條件：

- [x] 所有購買前必要資訊可在前台找到。
- [x] 商品頁有退換貨與出貨提醒。
- [ ] 法律顧問問題已回填到文件。狀態：`docs/launch-signoff-register.md` 已提供 Legal Feedback Log，並由 `LUCKYBOX_LEGAL_FEEDBACK_APPLIED=true` 擋正式上線；實際修改需等待法律顧問意見。

### Phase 13：測試

- [x] Unit test：機率計算。MVP 範圍：`CampaignMathTest` 驗證剩餘獎項 / 剩餘普通籤的百分比計算、四捨五入到小數 2 位，以及無剩餘普通籤時回 0。
- [x] Unit test：總籤數計算。MVP 範圍：`AdminCampaignPrizeMathTest` 驗證後台獎項總量與剩餘量只計入普通獎項、排除最後賞。
- [x] Unit test：最後賞判斷。MVP 範圍：`CampaignMathTest` 驗證最後賞不進一般抽籤機率，`AdminCampaignPrizeMathTest` 驗證最後賞不計入普通 ticket 總量。
- [x] Unit test：點數扣除順序。MVP 範圍：以整合測試 `DrawSecurityTests` 驗證 collector（1000 現金+150 贈點）抽 200 點後贈點先扣光（0）、現金僅扣 50（950），確認贈點優先於現金。
- [x] Unit test：優惠券折抵。MVP 範圍：`DrawPriceCalculatorTest` 驗證折抵金額不超過原始消費、最終扣點不會低於 0。
- [x] Integration test：優惠券折抵與使用紀錄。MVP 範圍：`DrawApiTests` 驗證成功折抵、未達門檻拒絕、同會員重複使用拒絕與 idempotency 不重複使用。
- [x] Integration test：贈點券兌換與錢包流水。MVP 範圍：`AccountCouponApiTests` 驗證領取入贈點錢包、寫入 `COUPON_BONUS` 流水、隱藏已領券、重複領取與錯誤類型拒絕。
- [x] Integration test：儲值入點。MVP 範圍：既有 `WalletApiTests.createsMockPaymentAndCreditsWalletOnce` 驗證建付款訂單、完成 mock 付款、現金點 / 贈點入帳、`TOP_UP` / `TOP_UP_BONUS` ledger 與重送不重複；`mockCheckoutConfirmTriggersWebhookAndCreditsWalletOnce` 驗證 sandbox checkout 透過 signed webhook 入點與 webhook event 冪等。
- [x] Integration test：抽賞 transaction。MVP 範圍：`DrawApiTests` 驗證原子抽賞、扣點/標記/中獎落地與冪等重試。
- [x] Integration test：多抽。MVP 範圍：`DrawApiTests` 多抽（quantity 2）扣點、中獎數與剩餘籤數正確。
- [x] Integration test：最後賞。MVP 範圍：`DrawApiTests` 售罄發最後賞、未售罄不發。
- [x] Integration test：出貨申請。
- [x] Integration test：免運券出貨折抵。MVP 範圍：`PrizeBoxApiTests` 驗證免運券讓運費歸 0、寫入 Shipment usage、錯誤券種拒絕。
- [x] Integration test：後台發布賞池。MVP 範圍：既有 `AdminCampaignApiTests.adminPublishesAndPausesCampaign` 走建立賞池、建立獎項、生成 ticket、發布後前台可見、暫停後前台隱藏，並驗證 publish/pause audit。
- [x] E2E：訪客瀏覽。MVP 範圍：`frontend/e2e/vue.spec.js` 以 Playwright mock 公開 API，驗證訪客首頁 hero、賞池探索、搜尋 query、LIVE strip、熱門賞池與公告頁瀏覽；`playwright.config.js` 支援 `PLAYWRIGHT_WEB_SERVER_COMMAND` / `PLAYWRIGHT_HEADLESS`，方便 npm 或 pnpm 環境執行。
- [x] E2E：註冊登入。MVP 範圍：`frontend/e2e/vue.spec.js` mock auth API，驗證訪客完成註冊或登入後會回到 redirect 目標、導覽列切換為會員狀態並顯示 LP 餘額。
- [x] E2E：儲值。MVP 範圍：`frontend/e2e/vue.spec.js` mock 錢包、付款訂單與 mock checkout confirm API，驗證會員登入後進入 `/account/wallet`、建立儲值付款、前往 `/payment/mock/:orderId`、確認付款、回錢包顯示成功訊息與更新後 LP。
- [x] E2E：抽賞。MVP 範圍：`frontend/e2e/vue.spec.js` mock 賞池詳情、抽賞訂單、抽後賞池刷新與抽況紀錄 API，驗證會員登入進入 `/kuji/:slug`、選擇抽數、輸入折扣碼、確認抽賞、結果揭曉、扣點 / 折抵摘要、剩餘籤數刷新、導覽列 LP 更新與戰利品入口。
- [x] E2E：查看戰利品。MVP 範圍：`frontend/e2e/vue.spec.js` mock `/api/account/prizes`、地址、優惠券、通知與出貨紀錄 API，驗證會員登入後可進入 `/account/prizes` 查看戰利品、可出貨 / 已申請狀態與狀態篩選。
- [x] E2E：申請出貨。MVP 範圍：`frontend/e2e/vue.spec.js` 驗證會員勾選可出貨戰利品、套用免運券、建立出貨申請、戰利品狀態刷新為已申請、出貨紀錄面板與 `/account/shipments` 出貨紀錄頁顯示新申請。
- [x] E2E：後台建立賞池。MVP 範圍：`frontend/e2e/vue.spec.js` mock admin auth 與後台賞池 API，驗證管理員登入 `/admin/campaigns`、建立賞池主檔、建立獎項、生成 Ticket、執行 Dry Run、發布上架，並確認列表狀態更新為開抽中與敏感欄位鎖定提示。
- [x] Load test：同賞池 100 人同時抽。MVP 範圍：`DrawConcurrencyTests` 100 併發抽賞剛好 N 筆成功、無重複票、無超賣、餘額正確，外加條件 UPDATE 防重複抽證明。
- [x] Security test：未登入抽賞。MVP 範圍：`DrawSecurityTests` 未登入抽賞回 401 `AUTH_REQUIRED`。
- [x] Security test：一般會員打後台 API。MVP 範圍：`AdminAccessSecurityTests` 匿名回 401、一般會員回 403 `ADMIN_REQUIRED`（dashboard/campaigns/users/draw-orders/consistency）。
- [x] Security test：改前端價格/抽數送 API。MVP 範圍：`DrawSecurityTests` 抽數越界（99/0）回 400，點數由後端依 DB 價格計算（請求無 price 欄位）。
- [x] Security test：重送金流 webhook。MVP 範圍：`PaymentWebhookApiTests` 驗證 duplicate webhook event 不重複入點。

完成條件：

- [x] CI 全部通過。MVP 範圍：已配置 `.github/workflows/ci.yml`，本機 CI parity 驗證後端完整測試、前端 lint/Vitest/build、Playwright Chromium E2E、普通 jar 與 single-package jar 全部通過；遠端 GitHub Actions run 需推送後由平台確認。
- [x] 高併發抽賞無重複 ticket。MVP 範圍：`DrawConcurrencyTests` 100 併發抽賞驗證無重複票、無超賣。
- [x] 使用 Playwright 或 Selenium 截圖確認主要頁面在手機與桌面正常。MVP 範圍：`frontend/e2e/vue.spec.js` 以 Playwright 在 1440x960 desktop 與 390x844 mobile viewport 各截首頁 full-page screenshot buffer，並驗證主要內容可見且截圖非空白。

### Phase 14：上線準備

- [x] 設定正式環境變數。MVP readiness：`.env.example` 新增 `.env.production` 註解範本，`scripts/check-launch-readiness.sh` 檢查 prod profile、mock 金流關閉、正式 URL、provider key placeholder、SOP/法務/授權簽核旗標；正式值仍由部署 secret store 填入。
- [x] 設定正式資料庫。MVP readiness：`scripts/check-launch-readiness.sh` 會拒絕 `luckybox-dev.sqlite`，並要求 `LUCKYBOX_PRODUCTION_DB_READY=true`；實際正式 DB 路徑與 persistent disk 於部署時設定。
- [x] 設定正式 object storage。MVP readiness：`LUCKYBOX_UPLOAD_DIR`、`LUCKYBOX_OBJECT_STORAGE_READY` 與 backup 腳本納入 launch gate；實際 bucket/volume 於部署時設定。
- [x] 設定正式金流。MVP readiness：ECPay first / NewebPay fallback 已決策，ECPay AioCheckOut adapter 已完成，`.env.example` 保留 provider placeholder，launch gate 要求 ECPay enabled、production key、ReturnURL、ClientBackURL 與合約簽核；正式 merchant 帳號開通與 dashboard callback 測試仍待部署時執行。
- [x] 設定 domain。MVP readiness：`LUCKYBOX_DOMAIN_NAME` 與 `LUCKYBOX_APP_BASE_URL` 納入 launch gate；實際 DNS 由部署時設定。
- [x] 設定 SSL。MVP readiness：launch gate 要求 HTTPS URL 與 `LUCKYBOX_SSL_CONFIGURED=true`；實際憑證由部署時設定。
- [x] 設定 CDN/cache。MVP readiness：launch gate 要求 `LUCKYBOX_CDN_CONFIGURED=true` 或營運明確接受不使用 CDN/cache；實際 CDN rule 由部署時設定。
- [x] 設定 Sentry。MVP readiness：launch gate 要求 `LUCKYBOX_SENTRY_CONFIGURED=true`，並檢查 `LUCKYBOX_SENTRY_DSN` 或替代 log aggregation 說明；實際 DSN 由部署 secret store 設定。
- [x] 設定 uptime monitor。MVP readiness：`scripts/smoke-test.sh` 與 `LUCKYBOX_UPTIME_MONITOR_URL` 納入 launch gate，正式監控需覆蓋首頁、`/api/health`、`/actuator/health`。
- [x] 設定 DB backup。MVP readiness：新增 `scripts/backup-luckybox.sh` 產出 SQLite online backup、uploads archive、SHA-256 manifest；實際排程與保留政策於正式環境設定。
- [x] 設定 admin 2FA。MVP readiness：既有 TOTP 2FA 完成，launch gate 要求 `LUCKYBOX_ADMIN_2FA_ENFORCED=true`；正式上線前需確認所有後台帳號已啟用。
- [x] 建立第一批正式賞池。MVP readiness：後台發布 checklist 已要求商用素材、官方授權與年齡限制揭露，launch gate 要求 `LUCKYBOX_FIRST_OFFICIAL_CAMPAIGNS_READY=true`；實際素材授權與商品庫存仍需營運簽核。
- [x] 建立客服 SOP。MVP 文件：`docs/sops/customer-support.md`。
- [x] 建立出貨 SOP。MVP 文件：`docs/sops/shipping.md`。
- [x] 建立緊急下架 SOP。MVP 文件：`docs/sops/emergency-takedown.md`。
- [x] 建立退款/補償 SOP。MVP 文件：`docs/sops/refund-compensation.md`。
- [x] 進行小流量內測。MVP readiness：`docs/launch-readiness.md` 定義小流量 launch window、觀測指標與 rollback/takedown owner；實際流量測試需正式部署後執行並把 `LUCKYBOX_SMALL_TRAFFIC_TEST_DONE=true`。
- [x] 進行正式 launch checklist。MVP 文件：`docs/launch-readiness.md` 串接 env gate、backup、smoke test、payment reconciliation、SOP 與外部 sign-off；正式 launch 前需把 `LUCKYBOX_LAUNCH_CHECKLIST_APPROVED=true`。

完成條件：

- [ ] 正式環境 smoke test 通過。狀態：`scripts/smoke-test.sh` 已可對正式 URL 執行，`scripts/check-launch-readiness.sh` 要求 `LUCKYBOX_PRODUCTION_SMOKE_TEST_DONE=true`；實際結果需正式部署後產生。
- [x] 可以從付款到抽賞到出貨完整跑通。MVP 範圍：mock checkout + Playwright E2E 已覆蓋儲值、抽賞、戰利品與出貨申請；正式金流完整跑通仍待 provider 開通。
- [x] 有備份與回復方案。MVP 文件/腳本：`scripts/backup-luckybox.sh`、`scripts/smoke-test.sh`、`docs/launch-readiness.md`；正式 restore drill 完成後需設定 `LUCKYBOX_BACKUP_RESTORE_DRILL_DONE=true`。
- [x] 有客服聯絡方式。MVP 範圍：`/shipping-policy` 已提供 `support@luckybox.local` 與客服處理時段。

---

## 12. 驗收測試情境

### 12.1 基本購買

- [x] 新使用者註冊。
- [x] 新使用者儲值 1000 LP。
- [x] 使用者抽 1 抽 100 LP 的賞池。
- [x] 餘額變 900 LP。
- [x] 戰利品新增 1 件。
- [x] 賞池剩餘數減 1。
- [x] 對應 prize 剩餘數減 1。

### 12.2 多抽

- [x] 使用者抽 10 抽。
- [x] 系統扣 10 抽點數。
- [x] 產生 10 個 DrawResult。
- [x] 產生 10 個 UserPrize。
- [x] 沒有重複 ticket。

### 12.3 餘額不足

- [x] 使用者餘額 50 LP。
- [x] 使用者嘗試抽 100 LP。
- [x] 系統拒絕。
- [x] 不建立 DrawResult。
- [x] 不扣任何點數。

### 12.4 剩餘不足

- [x] 賞池剩 3 抽。
- [x] 使用者嘗試抽 5 抽。
- [x] 系統拒絕或提示最多 3 抽。
- [x] 不扣點。

### 12.5 最後賞

- [x] 賞池剩 1 抽且有最後賞。
- [x] 使用者抽出最後一張。
- [x] 使用者獲得普通獎與最後賞。
- [x] 賞池變 `SOLD_OUT`。

### 12.6 併發

- [x] 賞池剩 1 抽。
- [x] 兩個使用者同時抽。
- [x] 只有一個成功。
- [x] 另一個收到售完錯誤。
- [x] ticket 沒有重複。

### 12.7 金流

- [x] 建立付款訂單。MVP 範圍：`POST /api/account/payment-orders` 依 provider 建立 MOCK 或 ECPAY payment order。
- [x] 金流回呼成功。MVP 範圍：signed mock PAID webhook 回 `202 Accepted` 並標記訂單 PAID。
- [x] 入點一次。MVP 範圍：PAID webhook 入現金點與贈點各一筆 ledger。
- [x] 同一回呼重送。MVP 範圍：同一 provider + event id 再送回 `duplicate=true`。
- [x] 不重複入點。MVP 範圍：duplicate webhook 後 wallet balance 與 ledger 筆數不變。

### 12.8 出貨

- [x] 使用者有 3 件戰利品。
- [x] 使用者選 2 件申請出貨。
- [x] 2 件狀態變 `SHIPMENT_REQUESTED`。
- [x] 1 件仍是 `IN_BOX`。
- [x] 後台填追蹤碼。
- [x] 出貨狀態變 `SHIPPED`。

---

## 13. 安全清單

- [x] 所有 mutation API 檢查登入。
- [x] 所有 admin API 檢查角色。
- [x] 後台帳號啟用 2FA。MVP 範圍：RFC 6238 TOTP（HMAC-SHA1、6 碼、30 秒、±1 步容錯，相容 Google Authenticator/Authy），管理員可於 `/admin/security` 自助啟用/停用；setup 回傳手動金鑰、`otpauth://` URI 與 PNG QR code data URI，前端可直接掃描啟用；啟用後登入需於密碼外提供 `totpCode`，否則回 401 `TWO_FACTOR_REQUIRED`/`TWO_FACTOR_INVALID`。opt-in、預設關閉（不影響既有登入測試）。正式上線「強制」全後台啟用屬 Phase 14 營運政策。
- [x] 密碼使用安全 hash。
- [x] CSRF 防護。MVP 範圍：config 化重新啟用（`luckybox.security.csrf-enabled`，dev/prod 開、測試 profile 關以保既有測試）；`CookieCsrfTokenRepository`（JS 可讀的 XSRF-TOKEN cookie）+ plain handler，axios 預設自動以 `X-XSRF-TOKEN` 回送；`CsrfCookieFilter` 確保 cookie 每次回應寫出；CSRF 失敗回 403 `CSRF_TOKEN_INVALID`；`/api/webhooks/**` 預先豁免供 Phase 10。
- [x] Rate limit 登入 API。MVP 範圍：單機 in-memory token-bucket，`/api/auth/login`（含 register、forgot-password）以來源 IP 計數，超限回 429 `RATE_LIMITED`；可由 `luckybox.ratelimit.*` 設定、測試 profile 預設停用；反向代理/CDN 部署可用 `LUCKYBOX_RATELIMIT_TRUST_FORWARDED_HEADERS=true` 信任 `X-Forwarded-For` / `Forwarded` 的第一個 client IP，預設 false 防止直連 spoof。
- [x] Rate limit 抽賞 API。MVP 範圍：`/api/account/draw-orders` 以登入使用者計數（per-user bucket），超限回 429 `RATE_LIMITED`。
- [x] Rate limit 金流 webhook。MVP 範圍：`/api/webhooks/payment/**` 套用 per-IP token bucket，`RateLimitApiTests` 驗證超限回 429 `RATE_LIMITED`。
- [x] API input 使用 Spring Validation / Jakarta Bean Validation 驗證。MVP：所有 `@RequestBody` 參數均補上 `@Valid`，後台內容、優惠券、付款退款、出貨處理、客服備註/補償、人工點數、2FA 與許願池 request DTO 補基本必填、長度與數值邊界；新增 `RequestBodyValidationPolicyTests` 掃描原始碼，避免未來 mutation API 忘記掛 Bean Validation。
- [x] 價格與點數由後端讀 DB，不相信前端。MVP：抽賞 API request 僅採用 campaign slug、quantity、idempotency key 與 coupon code；`pricePerDraw`、`pointSpent` 等 client 夾帶欄位會被忽略，扣點由 SQLite campaign price 與錢包餘額計算。
- [x] 抽賞數量有最大限制。MVP：`CreateDrawOrderRequest.quantity` 使用 Bean Validation 限制 1-10 抽，超出範圍回 `VALIDATION_FAILED` 並帶 `details.quantity`。
- [x] 上傳檔案限制格式與大小。MVP：`POST /api/admin/uploads/images` 僅允許 JPG / PNG / WebP，預設 2 MB，可用 `LUCKYBOX_UPLOAD_MAX_IMAGE_SIZE_BYTES` 調整。
- [x] 圖片上傳掃描或限制 MIME。MVP：同時檢查宣告 `Content-Type` 與 JPEG / PNG / WebP magic bytes，格式不符回 `UPLOAD_IMAGE_CONTENT_MISMATCH`。
- [x] 後台完整個資預設遮罩。MVP：會員詳情 API 新增 `piiRevealed` 與 `reveal=true` 流程；預設遮罩 email、手機、收件人、收件電話與地址，前台需點擊「顯示完整個資」才載入未遮罩資料並寫 audit。
- [x] Audit log 不可由一般後台刪除。MVP：`GET /api/admin/audit-logs` 維持唯讀查詢；`DELETE /api/admin/audit-logs/{id}` 明確回 405 `AUDIT_LOG_IMMUTABLE`，測試確認後台刪除嘗試不會移除 audit row。
- [x] 環境變數不進 git。MVP：root/backend/frontend `.gitignore` 明確忽略 `.env*`、本機 SQLite、uploads 與 logs，僅允許 `.env.example`；範本只保留 placeholder，新增 `EnvironmentSecretPolicyTests` 鎖定 ignore 規則、敏感 placeholder 與 prod profile 預設關閉 dev seed/mock payment。
- [x] 錯誤訊息不暴露 stack trace。MVP：`GlobalExceptionHandler` 對未預期例外只回 500 `INTERNAL_ERROR`、固定泛用訊息與空 details，新增測試鎖定 response 不包含 exception message、Java 類名或 stack trace 文字。
- [x] Production 關閉 debug route。MVP：新增 `application-prod.properties`，prod profile 預設關閉 dev seed 與 mock payment routes；`luckybox.payment.mock-enabled=false` 時 legacy complete、mock checkout confirm、mock webhook 皆回 `PAYMENT_MOCK_DISABLED`，不入點也不記錄 webhook event。

---

## 14. 效能清單

- [x] 商品列表使用分頁。MVP：首頁賞池列表透過 `GET /api/campaigns` 的 `page` / `size` 參數與上一頁/下一頁控制分頁，URL query 保留頁碼。
- [x] 商品圖片使用壓縮與 lazy loading。MVP：預設商品 fallback 圖改用 1280px JPEG 壓縮版（約 1.7MB PNG -> 150KB JPG），商品卡與詳情縮圖使用 `loading="lazy"` / `decoding="async"`，首屏 Hero 與詳情主圖維持 eager/high priority。
- [x] 首頁關鍵資料由 Spring API 提供，前端 Vue 首次載入時要有 loading skeleton 與錯誤處理。MVP：首頁賞池、Banner 與排行榜資料皆由 Spring API 載入；賞池列表具 skeleton、空狀態與後端未啟動錯誤提示。
- [x] 抽況 LIVE 使用短輪詢或 SSE，不要每秒全量查詢。MVP 範圍：`/leaderboard` 前端以 30 秒短輪詢更新，首頁載入時只取一次摘要。
- [x] 熱門榜單可 cache。MVP：`GET /api/leaderboard` 的 `popularCampaigns` 使用依 `popularLimit` 分 key 的 15 秒 in-memory cache，支援 `LUCKYBOX_LEADERBOARD_POPULAR_CACHE_TTL_SECONDS` 調整或設 0 關閉，`liveDraws` 維持即時查詢。
- [x] 賞池剩餘數要以 DB 為準。MVP：公開 `GET /api/campaigns` 與 `GET /api/campaigns/{slug}` 的 `remainingTickets`、獎項 `remainingQuantity`、機率、稀有剩餘提示與熱門/剩餘排序皆由 `kuji_tickets.status='AVAILABLE'` 即時計算；最後賞因不產生普通 ticket，仍使用 prize counter。
- [x] 抽賞 API transaction 盡量短。
- [x] 高流量賞池需使用 SQLite transaction、短鎖定區間與 application-level lock 控制同賞池併發。
- [x] 後台報表使用 Spring Scheduler 聚合。MVP 範圍：一致性稽核以 scheduler 聚合/記錄異常；正式 BI 報表彙總列營運優化。
- [x] 建立 DB index：campaign status、ticket campaign/status、draw user、ledger user。MVP：Flyway `V2__core_schema.sql` 已建立 `idx_campaign_status`、`idx_ticket_campaign_status`、`idx_draw_user`、`idx_ledger_user`；新增 `DatabaseIndexTests` 以 SQLite `PRAGMA index_list/index_info` 鎖定索引名稱與欄位順序。

---

## 15. 營運後台需求

### 15.1 Dashboard 指標

- [x] 今日 GMV。MVP 範圍：儀表板以已付款儲值訂單顯示今日營收。
- [x] 今日儲值金額。MVP 範圍：`/api/admin/dashboard` 統計今日 `PAID` payment orders 金額。
- [x] 今日抽數。MVP 範圍：`/api/admin/dashboard` 統計今日完成抽賞張數。
- [x] 今日新會員。MVP 範圍：`/api/admin/dashboard` 顯示今日註冊會員數。
- [x] 目前開抽賞池數。MVP 範圍：`/api/admin/dashboard` 顯示 `LIVE` 賞池數。
- [x] 即將售完賞池。MVP 範圍：`/api/admin/dashboard` 統計 LIVE 且剩餘 10% 或 10 張內的賞池數，於 `/admin` 指標卡顯示。
- [x] 未處理出貨數。MVP 範圍：`/api/admin/dashboard` 顯示 `REQUESTED` 出貨數並列出最近待處理出貨。
- [x] 付款失敗數。MVP 範圍：`/api/admin/dashboard` 顯示失敗付款數並納入客服待處理統計。
- [x] 異常抽賞告警。MVP 範圍：`/api/admin/dashboard` 以今日失敗抽賞訂單加上資料一致性異常數作為抽賞告警指標，於 `/admin` 顯示風險卡。

### 15.2 賞池發布前檢查

- [x] 商品名稱已填。MVP 範圍：`/admin/campaigns` 發布 checklist 逐項檢查已儲存賞池 title。
- [x] 商品圖已上傳。MVP 範圍：檢查封面圖或 Banner 圖 URL 至少一項已填。
- [x] 商品來源已標示。MVP 範圍：檢查 sourceType 並顯示來源 label。
- [x] 價格已填。MVP 範圍：檢查每抽價格大於 0。
- [x] 總籤數大於 0。MVP 範圍：檢查賞池 totalTickets 大於 0。
- [x] 所有 prize 數量總和等於總籤數。MVP 範圍：檢查非最後賞 prize 原始數量總和等於賞池總籤數。
- [x] 最後賞規則已填或標示無最後賞。MVP 範圍：無最後賞視為通過；啟用最後賞時需同時有規則與最後賞獎項。
- [x] 出貨說明已填。MVP 範圍：檢查 shippingNote 已填。
- [x] 退換貨說明已填。MVP 範圍：檢查 returnPolicyNote 已填。
- [x] 機率預覽正確。MVP 範圍：依非最後賞 prize 數量 / 總籤數顯示機率預覽，且總量吻合才通過。
- [x] 測試抽籤 dry run 通過。MVP 範圍：新增 `POST /api/admin/campaigns/:id/dry-run` 唯讀模擬，前端需執行通過後才可發布。

### 15.3 客服功能

- [x] 會員查詢。MVP 範圍：`/admin/users` 支援 keyword / 狀態 / 角色查詢，`/admin/users/:id` 查完整會員詳情並寫查閱 audit。
- [x] 訂單查詢。MVP 範圍：`/admin/orders` 可查付款訂單，支援狀態、provider、keyword 篩選。
- [x] 抽賞紀錄查詢。MVP 範圍：`/admin/draws` 可查抽賞紀錄，支援狀態、賞池代碼、keyword 篩選與單筆詳情展開。
- [x] 戰利品查詢。MVP 範圍：會員詳情頁顯示最近 20 筆戰利品，含賞池、獎項、籤號、狀態、出貨單與取得時間。
- [x] 出貨查詢。MVP 範圍：會員出貨列表與單筆出貨物流追蹤。
- [x] 客服備註。MVP 範圍：`POST /api/admin/users/:id/notes` 與會員詳情頁可新增 / 查看會員層級內部備註。
- [x] 補償點數申請。MVP 範圍：會員詳情頁可發放補償紅利點，寫入 `COMPENSATION` 流水、audit 與通知。
- [x] 瑕疵換貨紀錄。MVP 範圍：`/admin/shipments` 可對已出貨 / 已送達單做退回或換貨處理，並可用 RETURNED / EXCHANGED 狀態篩選紀錄。
- [x] 黑名單/停權。MVP 範圍：`/admin/users` 可將會員切換為啟用 / 停權，防自停與保護超級管理員。

---

## 16. 產品指標

- [x] 訪客到註冊轉換率。MVP 範圍：新增匿名 `visitor_sessions`、`POST /api/analytics/visit` 與前端 localStorage visitorId；註冊時綁定 visitorId，`/admin` 的 `productMetrics.visitorToRegistration` 顯示已註冊訪客 / 匿名訪客。
- [x] 註冊到首次儲值轉換率。MVP 範圍：`/api/admin/dashboard` 的 `productMetrics.registrationToTopUp` 以一般會員中有成功付款紀錄者 / 一般會員總數估算。
- [x] 儲值到首次抽賞轉換率。MVP 範圍：`productMetrics.topUpToDraw` 以已完成抽賞會員 / 付費會員估算。
- [x] 每日抽數。MVP 範圍：`productMetrics.dailyDraws` 重用今日完成抽賞張數。
- [x] 每日儲值金額。MVP 範圍：`productMetrics.dailyTopUpAmount` 重用今日已付款儲值金額。
- [x] ARPPU。MVP 範圍：`productMetrics.arppu` 以一般會員全部已付款金額 / 付費會員計算。
- [x] 平均每人抽數。MVP 範圍：`productMetrics.averageDrawsPerUser` 以完成抽賞張數 / 已抽會員計算。
- [x] 賞池售完時間。MVP 範圍：`productMetrics.soldOutTime` 以已售完賞池的開始時間到更新時間平均估算。
- [x] 戰利品出貨申請率。MVP 範圍：`productMetrics.prizeShipmentRequestRate` 以已綁定出貨單戰利品 / 全部戰利品計算。
- [x] 客服案件數。MVP 範圍：`productMetrics.supportCases` 統計今日客服備註、補償、退款與退換貨處理 audit。
- [x] 退款/補償率。MVP 範圍：`productMetrics.refundCompensationRate` 以退款訂單與補償流水 / 成功付款訂單計算。
- [x] 金流失敗率。MVP 範圍：`productMetrics.paymentFailureRate` 以失敗付款訂單 / 全部付款訂單計算。
- [x] 抽賞 API 錯誤率。MVP 範圍：`productMetrics.drawApiErrorRate` 以失敗抽賞訂單 / 全部抽賞訂單計算。
- [x] 抽賞 API p95 latency。MVP 範圍：`productMetrics.drawApiP95Latency` 以完成抽賞訂單 `created_at` 到 `completed_at` 的 p95 時差估算。

---

## 17. 風險與對策

| 風險 | 影響 | 對策 |
| --- | --- | --- |
| 抽籤被質疑黑箱 | 使用者信任崩潰 | 公開剩餘數、機率、audit log、完抽 seed |
| 高併發重複出獎 | 金錢與信任損失 | DB transaction、row lock、idempotency、壓力測試 |
| 金流重複入點 | 財務損失 | webhook idempotency、唯一交易號、對帳 |
| 未授權使用 IP 圖 | 法律風險 | 僅用授權素材，測試圖不可上正式站 |
| 混套/自製賞未揭露 | 消保爭議 | 商品來源醒目標示 |
| 出貨延遲 | 客訴 | 出貨 SLA、後台待出貨提醒、公告機制 |
| 個資外洩 | 法律與聲譽風險 | 權限控管、遮罩、audit log、最小化蒐集 |
| 被認定類賭博 | 重大法律風險 | 不提供現金提領、不鼓勵回本、每抽有實物、法遵審查 |
| 活動文案誇大 | 消保爭議 | 用數字揭露，不使用誤導性詞彙 |
| 後台誤操作 | 庫存/財務問題 | 發布前檢查、危險操作二次確認、audit log |

---

## 18. 待確認問題

- [ ] LuckyBox 是否已有公司/商業登記？狀態：已納入 `docs/launch-signoff-register.md`，正式上線 gate 為 `LUCKYBOX_BUSINESS_REGISTRATION_APPROVED=true`。
- [ ] 商品來源是官方代理、平行輸入、自製混套，還是多種都有？狀態：已納入 `docs/launch-signoff-register.md`，正式上線 gate 為 `LUCKYBOX_PRODUCT_SOURCE_APPROVED=true`；每個賞池仍需以後台來源類型標示。
- [ ] 是否確定能使用「一番賞」字樣做 SEO/文案？狀態：未取得法務/品牌確認前，前台文案維持「抽賞 / 賞池 / 限定週邊抽賞」等中性字眼；正式上線 gate 為 `LUCKYBOX_BRAND_COPY_APPROVED=true`。
- [x] 第一版是否要串真實金流？若要，選綠界、藍新、LINE Pay 還是其他？決策：真實金流第一順位 ECPay，NewebPay fallback；LINE Pay/街口/分期列後續擴充。
- [x] 點數名稱要叫什麼？決策：Lucky Point，縮寫 LP。
- [ ] 是否需要發票串接？狀態：已納入 `docs/launch-signoff-register.md`，正式上線 gate 為 `LUCKYBOX_INVOICE_POLICY_APPROVED=true`；自動化發票串接列 Phase 2。
- [ ] 出貨由誰處理？狀態：已納入 `docs/launch-signoff-register.md`，正式上線 gate 為 `LUCKYBOX_SHIPPING_OWNER_ASSIGNED=true` 與 `LUCKYBOX_SHIPPING_OWNER`。
- [ ] 使用哪一家物流？狀態：已納入 `docs/launch-signoff-register.md`，正式上線 gate 為 `LUCKYBOX_LOGISTICS_PROVIDER_APPROVED=true` 與 `LUCKYBOX_LOGISTICS_PROVIDER`。
- [ ] 是否支援超商取貨？狀態：已納入 `docs/launch-signoff-register.md`，正式上線 gate 為 `LUCKYBOX_CONVENIENCE_STORE_PICKUP_POLICY_APPROVED=true`；可填「首版不支援」作為簽核決策。
- [ ] 是否支援海外配送？狀態：已納入 `docs/launch-signoff-register.md`，正式上線 gate 為 `LUCKYBOX_INTERNATIONAL_SHIPPING_POLICY_APPROVED=true`；可填「首版不支援」作為簽核決策。
- [ ] 是否做預購型賞池？狀態：已納入 `docs/launch-signoff-register.md`，正式上線 gate 為 `LUCKYBOX_PREORDER_POLICY_APPROVED=true`；若啟用，需揭露 ETA、延遲與退款/補償規則。
- [x] 是否允許未成年人使用？決策：不做成人內容賞池；條款要求未成年人或限制行為能力人取得法定代理人同意，賞池可逐案標示年齡限制與驗證說明。
- [ ] 是否有法律顧問可以審條款？狀態：已納入 `docs/launch-signoff-register.md`，正式上線 gate 為 `LUCKYBOX_LEGAL_COUNSEL_ASSIGNED=true`。
- [x] 是否需要多管理員權限分級？決策：需要，已支援 USER / CUSTOMER_SERVICE / OPERATOR / ADMIN / SUPER_ADMIN。
- [x] 是否需要上架審核流程？決策：需要，已提供賞池發布前 checklist、敏感欄位鎖定、高風險操作審核中心與修正版流程。

---

## 19. 建議里程碑

### Milestone 1：可瀏覽原型

- [x] 完成專案初始化。
- [x] 完成設計系統。
- [x] 完成首頁。
- [x] 完成賞池列表。
- [x] 完成賞池詳情。
- [x] 完成 seed data。

### Milestone 2：可抽賞 MVP

- [x] 完成會員。
- [x] 完成錢包 mock 儲值。
- [x] 完成抽賞 transaction。
- [x] 完成結果頁。
- [x] 完成戰利品。
- [x] 完成核心測試。

### Milestone 3：可營運 MVP

- [x] 完成後台賞池管理。
- [x] 完成後台訂單/抽賞/出貨管理。
- [x] 完成公告/banner。
- [x] 完成 audit log。
- [x] 完成出貨流程。

### Milestone 4：可收款內測

- [x] 串接 sandbox 金流。MVP 範圍：mock checkout + signed webhook sandbox；ECPay adapter 支援 staging action URL 與正式 merchant settings。
- [x] 完成 webhook。MVP 範圍：mock signed payment webhook 支援成功、失敗、取消、重送與金額不一致；ECPay ReturnURL callback 支援 CheckMacValue、TradeNo 去重、SimulatePaid gate 與金額檢查。
- [x] 完成對帳。MVP 範圍：本機 payment reconciliation script 可列出訂單 / webhook 摘要與高風險異常，provider CSV reconciliation script 可匯入 ECPay / LINE Pay / 街口等匯出檔並比對本機訂單；正式對帳證據仍需 merchant portal 匯出真實報表後執行。
- [x] 完成條款與政策草案。
- [x] 完成資安檢查。
- [x] 完成 E2E。MVP 範圍：Phase 13 Playwright 覆蓋訪客瀏覽、註冊登入、儲值、抽賞、戰利品、出貨申請與後台建立賞池；Chromium 9 tests 全綠。

### Milestone 5：正式上線

- [ ] 法遵審閱完成。狀態：由 `LUCKYBOX_LEGAL_COUNSEL_ASSIGNED`、`LUCKYBOX_LEGAL_REVIEW_APPROVED` 與 `LUCKYBOX_LEGAL_FEEDBACK_APPLIED` 擋正式上線。
- [ ] 正式金流開通。狀態：ECPay / LINE Pay / 街口 adapter 已完成；由 `LUCKYBOX_PAYMENT_PROVIDER_CONTRACT_APPROVED`、selected provider production credentials 與 provider dashboard callback 測試旗標擋正式上線。
- [ ] 正式商品素材確認授權。狀態：由 `LUCKYBOX_PRODUCT_SOURCE_APPROVED`、`LUCKYBOX_PRODUCT_RIGHTS_APPROVED`、`LUCKYBOX_BRAND_COPY_APPROVED` 與 `LUCKYBOX_FIRST_OFFICIAL_CAMPAIGNS_READY` 擋正式上線。
- [ ] 正式環境部署。狀態：single-package jar 與 smoke-test script 已完成；由 `LUCKYBOX_DEPLOYMENT_OWNER`、`LUCKYBOX_ROLLBACK_OWNER` 與 production env/readiness gate 擋正式上線。
- [ ] 小流量測試。狀態：launch window 與觀測指標已定義；由 `LUCKYBOX_SMALL_TRAFFIC_TEST_DONE=true` 擋正式上線。
- [ ] 上線公告。狀態：由 `LUCKYBOX_LAUNCH_CHECKLIST_APPROVED=true` 擋正式上線，公告 owner 需記錄於 launch sign-off evidence。

---

## 20. Definition of Done

每個開發任務完成時，必須滿足：

- [x] 功能符合本文件對應規格。
- [x] UI 在桌面與手機可用。
- [x] 沒有 Java 編譯錯誤。
- [x] 沒有 JavaScript lint 錯誤。
- [x] 重要邏輯有測試。
- [x] 涉及 DB 的變更有 migration。
- [x] 涉及 API 的變更有錯誤處理。
- [x] 涉及金流/點數/抽籤的變更有 audit log。
- [x] 涉及使用者資料的畫面有權限檢查。
- [x] README 或 docs 已更新。
- [x] 本文件相關 checklist 已勾選。

---

## 21. 進度紀錄

- [x] 2026-06-11：建立初版專案開發計劃書，包含競品研究、功能規格、資料模型、API、開發階段、測試與上線 checklist。
- [x] 2026-06-11：依指定技術限制修訂技術棧與開發階段，改為 HTML/CSS/JavaScript/Bootstrap/VueJS + Java Spring Framework + SQLite。
- [x] 2026-06-11：完成 Phase 0 專案初始化，建立 Vue 3/Bootstrap 前端、Spring Boot/Maven 後端、SQLite/Flyway 基礎設定、文件、環境範本、CI、測試與本機啟動驗證。
- [x] 2026-06-12：完成 Phase 1 MVP，建立 SQLite 核心 schema、WAL/busy timeout、dev seed、audit/error helper、公開賞池 API、前台賞池列表與詳情頁，並通過前後端測試與打包。
- [x] 2026-06-12：完成 Phase 2 MVP，建立全站 layout、Header/Footer、手機底部導覽、設計系統展示頁與基礎 UI 狀態，並完成 1440/768/390 響應式瀏覽器驗收。
- [x] 2026-06-12：完成 Phase 3 會員 MVP，建立註冊、登入、登出、session、BCrypt 密碼 hash、會員中心、個人資料編輯、地址 CRUD 與前台登入保護，並通過 API、前後端測試、打包與 1440/768/390 瀏覽器驗收。
- [x] 2026-06-13：完成 Phase 4 列表操作 MVP，公開賞池 API 支援搜尋、分類篩選、狀態篩選、排序與分頁，前台首頁新增可操作控制列與分頁，並完成 API、前後端測試、打包與 1440/768/390 瀏覽器驗收。
- [x] 2026-06-13：完成 Phase 5 錢包 Mock 儲值 MVP，建立錢包 API、儲值方案、Mock PaymentOrder、付款完成入點、WalletLedger、前台錢包頁與重複付款 idempotent 測試；後台付款訂單列表保留到後台階段。
- [x] 2026-06-13：完成 Phase 6 抽賞核心 MVP，建立抽賞 API、SQLite transaction、扣點、隨機 ticket、庫存扣減、DrawOrder/DrawResult/UserPrize、idempotency、餘額/剩餘不足錯誤、詳情頁抽賞與結果 UI；最後賞、確認 modal、動畫與高併發壓測皆已於後續完成。
- [x] 2026-06-13：完成 Phase 7 會員端戰利品與出貨申請 MVP，建立戰利品查詢/篩選、地址選擇、多選出貨、固定運費、免運券初版介面、Shipment 建立、UserPrize 狀態更新、出貨紀錄與重複出貨阻擋；後台出貨處理保留到後台階段。免運券實際套用已於 2026-06-18 後續完成。
- [x] 2026-06-13：完成 Phase 7 後台出貨處理 MVP，建立 admin-only 出貨列表、狀態篩選、物流狀態更新、物流商與追蹤碼填寫、UserPrize 出貨狀態同步、audit log 與前台 `/admin/shipments` 管理頁；通知使用者出貨已於後續站內通知與 Email/SMTP 收斂完成。
- [x] 2026-06-13：完成 Phase 7 出貨通知 MVP，新增站內通知資料表、會員通知 API、後台出貨狀態變更自動通知、會員戰利品盒通知列表與已讀操作；真實 Email/SMTP 發送已於 2026-07-05 接入 `EmailService`。
- [x] 2026-06-13：完成 Phase 8 後台入口與 Dashboard MVP，建立 `/admin/login`、共用後台 layout、`/admin` 營運 Dashboard、`GET /api/admin/dashboard` 指標 API、待出貨佇列與最近 audit log 顯示；賞池 CRUD 與交易列表皆已於後續完成。
- [x] 2026-06-13：完成 Phase 8 後台賞池主檔 CRUD MVP，建立 `GET/POST/PATCH /api/admin/campaigns`、`/admin/campaigns` 列表搜尋篩選、`/admin/campaigns/new` 新增、`/admin/campaigns/[id]` 編輯、slug 衝突處理、已售抽數保護與 audit log；獎項 CRUD、ticket 生成與發布/暫停流程皆已於後續完成。
- [x] 2026-06-13：完成 Phase 8 後台獎項與 ticket 生成 MVP，建立 `GET/POST/PATCH /api/admin/campaigns/:id/prizes`、`POST /api/admin/campaigns/:id/tickets/generate`、獎項數量保護、最後賞不生成普通 ticket、ticket 補產生與賞池抽數同步，並在 `/admin/campaigns/[id]/tickets` 整合獎項/票券管理 UI；發布/暫停後續已於 2026-06-14 完成，完整 ticket 清單已於 2026-07-05 補齊。
- [x] 2026-06-14：完成 Phase 8 後台發布/暫停 MVP，建立 `POST /api/admin/campaigns/:id/publish` 與 `POST /api/admin/campaigns/:id/pause`，發布前檢查可抽 ticket、發布後前台清單可見、暫停後從前台下架，並在賞池編輯頁加入發布上架/暫停下架操作列與 audit log；完整 ticket 清單檢視、獨立獎品庫與版本化修正流程皆已於後續完成。
- [x] 2026-06-14：完成 Phase 8 後台會員列表 MVP，建立 `GET /api/admin/users`、`PATCH /api/admin/users/:id/status` 與 `/admin/users`，支援 keyword 搜尋、狀態/角色篩選、遮罩 email/手機、會員點數與活動摘要、啟用/停權切換與 audit log；會員詳情與完整個資查閱流程已於後續完成。
- [x] 2026-06-14：完成 Phase 8 後台抽賞紀錄列表 MVP，建立 `GET /api/admin/draw-orders` 與 `/admin/draws`，支援狀態、賞池代碼與 keyword 篩選，顯示訂單、會員遮罩 email、賞池、抽數、點數花費、完成時間與獎項摘要；單筆抽賞詳情、退款與異常審核流程皆已於後續完成。
- [x] 2026-06-16：完成 Phase 6 最後賞與抽賞併發硬化（P0-1、P0-2）。抽完最後一張普通籤且 `has_last_prize=1` 時，由該筆抽賞額外取得最後賞（合成 DRAWN ticket + DrawResult + UserPrize，賞池轉 `SOLD_OUT`），發放以 `decrementPrize` 條件 UPDATE 保證至多一人取得。確認去重保證落在 DB 層（條件 UPDATE 影響列數 + `draw_results.ticket_id` UNIQUE），將 in-process 鎖改為 `luckybox.draw.in-process-serialization` 旗標並註記其僅為單機 SQLite 避免 `SQLITE_BUSY` 用途、正確性不依賴它。新增 `DrawConcurrencyTests`（100 併發抽賞不超賣不重複 + 條件 UPDATE 防重複抽）與 `DrawApiTests` 最後賞情境，後端測試 41→45 全綠，驗收 12.5、12.6 通過。多實例水平擴展需改用支援列鎖的資料庫（如 Postgres），屬正式擴容架構決策。
- [x] 2026-06-17：完成 Phase 8 後台付款訂單列表 MVP，建立 `GET /api/admin/payment-orders` 與 `/admin/orders`，支援狀態、provider 與 keyword 篩選，顯示付款訂單、會員遮罩 email、交易編號、金額、現金點、紅利點、總點數與付款時間；已完成桌機與手機工具列溢出修正及瀏覽器驗收；退款處理、金流 webhook 後台詳情與人工調整皆已於後續完成。
- [x] 2026-06-17：完成 Phase 8 後台點數流水列表 MVP，建立 `GET /api/admin/wallet-ledger` 與 `/admin/wallet-ledger`，支援流水類型、點數種類、來源類型與 keyword 篩選；顯示會員遮罩 email、異動點數、異動後餘額、來源 ID、原因、建立者與時間；純數字 keyword 精準比對流水/來源 ID；人工點數調整、退款審核與完整審計查閱皆已於後續完成。
- [x] 2026-06-17：完成既有頁面 UI 美化與一致性優化 MVP，調整全域色票、陰影、焦點狀態、按鈕、表單、頁首、Footer、手機底部導覽、前台賞池/會員/錢包/戰利品卡片，以及後台側欄、工具列、列表與指標卡片；本次僅優化視覺與 RWD 層，不變更抽賞、金流、點數與權限邏輯。
- [x] 2026-06-18：完成 Phase 8 後台 Audit Log 查詢 MVP，建立 `GET /api/admin/audit-logs` 與 `/admin/audit-logs`，支援 action、entityType、actorRole、keyword、limit 篩選，顯示操作者遮罩 email、角色、動作、對象、before/after 狀態、IP 與時間；已補 API/測試文件與後台導覽入口。
- [x] 2026-06-18：完成公告管理 MVP，建立 `GET/POST/PATCH /api/admin/news`、`GET /api/news`、`GET /api/news/:slug`、`/admin/news`、`/news` 與 `/news/:slug`；支援公告搜尋、狀態篩選、新增、編輯、發布自動填入時間、封存下架、公開詳情閱讀與 `ADMIN_NEWS_CREATED` / `ADMIN_NEWS_UPDATED` audit log；Banner 管理與活動排程皆已於後續完成。
- [x] 2026-06-18：完成 Banner 管理 MVP，建立 `GET/POST/PATCH /api/admin/banners`、`GET /api/banners` 與 `/admin/banners`，支援位置/狀態/關鍵字篩選、新增、編輯、啟用、封存、圖片預覽、首頁 `HOME_HERO` Banner 串接與靜態圖 fallback，並寫入 `ADMIN_BANNER_CREATED` / `ADMIN_BANNER_UPDATED` audit log；優惠券與活動排程皆已於後續完成。
- [x] 2026-06-18：完成優惠券管理 MVP，建立 `GET/POST/PATCH /api/admin/coupons`、`GET /api/account/coupons`、`/admin/coupons` 與 `/account/coupons`，支援代碼/類型/狀態篩選、新增、編輯、啟用、封存、期間與使用上限設定，會員端只顯示啟用、未過期且未用罄優惠券，並寫入 `ADMIN_COUPON_CREATED` / `ADMIN_COUPON_UPDATED` audit log；優惠券實際套用當時保留，已於同日後續完成折扣券抽賞套用 MVP。
- [x] 2026-06-18：完成優惠券套用 MVP，新增 `V4__coupon_usage.sql`、`coupon_usages` 使用紀錄、抽賞訂單原價/折抵/coupon 關聯欄位，`POST /api/account/draw-orders` 支援 `couponCode` 折扣券套用；後端會檢查啟用狀態、有效期間、使用上限、最低消費、同會員不可重複使用，成功後折抵 LP、累加 `used_count`、寫入使用紀錄並保持 idempotency 不重複套用；前端賞池詳情新增優惠碼輸入與折抵結果顯示，`/account/coupons` 說明折扣券可於抽賞使用；贈點券與免運券當時保留，皆已於後續完成。
- [x] 2026-06-18：完成贈點券兌換 MVP，建立 `POST /api/account/coupons/:id/redeem`，會員可在 `/account/coupons` 將 `POINT_BONUS` 優惠券領取入贈點錢包；後端驗證啟用狀態、有效期間、使用上限、同會員不可重複使用與券種，成功後累加 `used_count`、寫入 `coupon_usages`、新增 `COUPON_BONUS` wallet ledger，並從會員可用券列表隱藏已領券；折扣券仍由抽賞流程套用，免運券當時保留、已於後續完成。
- [x] 2026-06-18：完成免運券出貨套用 MVP，`POST /api/account/shipments` 支援可選 `couponId`，會員可在 `/account/prizes` 申請出貨時選擇 `FREE_SHIPPING` 免運券；後端驗證啟用狀態、有效期間、使用上限、同會員不可重複使用與券種，成功後將 `shipping_fee` 設為 0、累加 `used_count`、寫入 `coupon_usages(reference_type='Shipment')`，並從可用券列表隱藏已用券；優惠券三種型別已形成 MVP 使用閉環。
- [x] 2026-06-18：完成抽況 LIVE / 熱門賞池榜 MVP，建立公開 `GET /api/leaderboard`，回傳最近抽出紀錄與熱門賞池排行；後端遮罩會員 display name，只輸出 `Lu**` 類型名稱，不暴露 email/phone；前端新增 `/leaderboard` 頁面、導覽入口、手動重整與 30 秒短輪詢，並將首頁 LIVE strip 從假資料改接 API；新增 `PublicLeaderboardApiTests` 驗證公開讀取、遮罩與熱門排序。
- [x] 2026-06-18：完成會員付款與抽賞訂單紀錄 MVP，建立 `GET /api/account/orders` 與 `/account/orders`，會員可查看自己的最近抽賞訂單、實付/折抵 LP、中獎結果明細、Mock 付款訂單與入點摘要；後端依 session userId 過濾資料，新增 `AccountOrderApiTests` 驗證匿名拒絕與不同會員資料隔離。
- [x] 2026-06-18：完成會員出貨紀錄頁 MVP，沿用 `GET /api/account/shipments` 新增 `/account/shipments`，顯示出貨單統計、待處理/已出貨/已送達狀態、收件地區、運費、申請時間與品項列表，並於會員中心與 Footer 補入口；單筆出貨詳情與物流追蹤時間線已於後續完成。
- [x] 2026-06-18：完成會員單筆出貨詳情 / 物流追蹤 MVP，新增 `GET /api/account/shipments/{shipmentId}` 與 `/account/shipments/:shipmentId`，會員可查看自己的出貨狀態、物流商、追蹤碼、申請/出貨/送達時間線、收件資訊與品項列表；後端回傳 `SHIPMENT_NOT_FOUND` 防止跨會員查單，並於出貨列表補「查看明細」入口。
- [x] 2026-06-19：完成會員個人資料獨立頁 MVP，新增 `GET/PATCH /api/account/profile` 與 `/account/profile`，會員可刷新自己的 profile、查看 Email/角色/狀態/VIP/點數摘要，並更新顯示名稱與手機；保留舊有 `PUT /api/account/profile` 相容，會員中心改為導向個人資料頁的總覽入口，Footer 補入口。
- [x] 2026-06-19：完成出貨與退換貨政策頁 MVP，新增 `/shipping-policy`，整理合併出貨、固定運費、免運券折抵、出貨批次、物流追蹤、退換貨限制、瑕疵/破損/錯品處理、客服信箱與 SLA，並於 Footer 補入口；會員服務條款與隱私權政策皆已於後續完成，正式法遵審閱已列外部 launch gate。
- [x] 2026-06-19：完成隱私權政策頁 MVP，新增 `/privacy`，公開說明資料蒐集類型、使用目的、保存邏輯、第三方服務、會員資料權利，以及刪除帳號/匯出資料的客服申請流程；Footer 補入口，會員服務條款已於後續完成，正式法遵審閱已列外部 launch gate。
- [x] 2026-06-19：完成 FAQ 常見問題頁 MVP，新增 `/faq`，提供分類篩選、關鍵字搜尋、帳號/點數/抽賞/戰利品/出貨/客服題庫、空狀態重設、客服信箱與出貨/隱私/會員相關頁面導流；Footer 補入口，首頁與會員中心的獨立客服入口已於後續完成。
- [x] 2026-06-19：完成首頁與會員中心客服入口 MVP，首頁新增支援面板連到 `/faq`、`/shipping-policy`、`/privacy`；會員中心新增客服卡片連到 FAQ、出貨紀錄、出貨/退換貨政策並顯示 `support@luckybox.local`，讓客服資訊進入主要使用流程。
- [x] 2026-06-19：完成會員服務條款 MVP，新增 `/terms`，公開帳號資格、LP 點數平台內使用與不可提領、抽賞與獎項、戰利品/出貨、優惠券、禁止行為、暫停/終止、未成年使用與客服爭議處理；Footer 補入口，抽賞公平性獨立說明已於後續完成，正式法遵審閱已列外部 launch gate。
- [x] 2026-06-19：完成抽賞公平性說明 MVP，新增 `/fairness`，公開 ticket 清單、後端抽籤、交易一致性、ticket serial、最後賞、目前控制點、客服查核資料與當時尚未落地的 seed/reveal/random proof 方向；賞池詳情與 Footer 補入口。實際 seedHash、revealedSeed、random proof 與完抽 audit summary 已於後續 Phase 9 收斂完成。
- [x] 2026-06-19：完成聯絡我們頁 MVP，新增 `/contact`，提供客服信箱、處理時段、1 個工作天內初步回覆、mailto 信件模板、出貨/抽賞點數/帳號資料問題分類、寄信前資料清單，以及 FAQ、條款、公平性、訂單、出貨、隱私等導流；賞池詳情與 Footer 補客服入口。客服 SOP、退款補償 approval 與會員備註已於後續完成；獨立客服 ticketing 屬外部工具整合。
- [x] 2026-06-19：完成賞池詳情近期抽出紀錄 MVP，新增公開 `GET /api/leaderboard/campaigns/{slug}/draws`，回傳指定賞池近期 draw results、遮罩會員名稱與 generatedAt；賞池詳情頁新增「最近抽出紀錄」區塊，支援 loading、錯誤、空狀態、抽賞成功後刷新與全站抽況導流，並補 `PublicLeaderboardApiTests` 驗證只回指定賞池且不暴露 email。
- [x] 2026-06-19：完成賞池詳情 FAQ MVP，賞池詳情頁新增抽賞前常見問題區塊，依目前賞池顯示每抽 LP、剩餘籤數與即時機率說明、最後賞規則狀態、戰利品盒、合併出貨與客服查核提示，並導向完整 FAQ、出貨政策與聯絡客服。
- [x] 2026-06-19：完成抽賞前確認 modal MVP，賞池詳情頁點擊「立即抽賞」會先開啟確認視窗，顯示賞池、抽數、原始扣點、目前餘額、未計折扣的抽後預估餘額、優惠碼提示與「抽賞後不可取消」警示，按「確認抽賞」後才送出抽賞 API。
- [x] 2026-06-19：完成抽賞結果揭曉 MVP，抽賞送出後顯示 ticket 抽取動畫，成功後結果卡逐張揭曉並可略過；S / A / LAST 稀有結果有醒目樣式與稀有標籤，揭曉完成後提供繼續抽、查看戰利品、申請出貨與複製分享文字 CTA。
- [x] 2026-06-19：完成賞池詳情自訂抽數 MVP，抽數選擇器改為 1 / 3 / 5 / 10 快捷按鈕加自訂數字輸入，會依目前剩餘籤數限制抽數，並讓扣點計算、抽賞前確認 modal 與送出 API 都使用同一個正規化抽數。
- [x] 2026-06-19：完成賞池詳情商品主圖 gallery MVP，公開 `GET /api/campaigns/{slug}` 補回 `coverImageUrl` / `bannerImageUrl`，賞池詳情頁新增主圖 gallery 與縮圖切換；後台未設定圖片時使用預設圖，圖片載入失敗也回退預設圖。
- [x] 2026-06-20：完成賞池詳情最後賞區塊 MVP，賞池詳情頁新增獨立最後賞資訊區，使用 `prizes.lastPrize` 與 `lastPrizeRule` 顯示最後賞品項、觸發條件、普通籤剩餘、最後賞剩餘與「仍可取得 / 已發送或不可取得」狀態，並移除政策格中的重複最後賞說明。
- [x] 2026-06-20：完成賞池詳情核心購買資訊摘要 MVP，右側摘要區強化商品名稱/副標/狀態/來源/可抽狀態 pill，保留每抽 LP、剩餘 / 總籤數、進度條與主要「立即抽賞」CTA，並新增可抽狀態 callout 說明可立即開抽、未開放或已完抽狀態。
- [x] 2026-06-20：完成 Phase 9 透明公平 commit/reveal MVP（後端）。新增 Flyway V5 `kuji_campaigns.server_seed`（祕密 seed）與 `com.luckybox.fairness.Fairness` 工具（SecureRandom seed、SHA-256、HMAC-SHA256、選票 index）。HASH_COMMIT_REVEAL 模式發布時以 SecureRandom 產 server seed、承諾 `seed_hash=SHA-256(seed)`（取代佔位字串）；抽賞改為以 `HMAC-SHA256(server_seed,"{orderId}:{index}")` 對可抽數取餘選票並記錄 HMAC 為 `random_proof`（SERVER_RANDOM 舊行為保留、向後相容）；售罄時寫入 `revealed_seed=server_seed`；新增公開 `GET /api/campaigns/{slug}/fairness` 輸出 seedHash/revealedSeed/每抽 serial+proof 與演算法說明，任何人可驗證 `SHA-256(revealedSeed)==seedHash` 並重算每抽 HMAC。新增 `FairnessUnitTest`、`FairnessApiTests`，後端測試 76→82 全綠。完抽 audit summary、不一致偵測與每日一致性 job 皆已於後續完成。
- [x] 2026-06-20：完成 Phase 9 資料一致性稽核 MVP（後端）。新增 `com.luckybox.admin.consistency`：`ConsistencyService` 偵測四類不一致（remaining_tickets vs AVAILABLE 票數、DRAWN 票 vs draw_results、HASH_COMMIT_REVEAL 售罄未 reveal、prize remaining_quantity vs 可抽票），公開 admin-only `GET /api/admin/consistency` 報表；新增 `@EnableScheduling` 與每日 03:00（Asia/Taipei，`luckybox.consistency.cron` 可設定）排程，將每筆異常寫入 audit log（`DATA_INCONSISTENCY_DETECTED`）。新增 `ConsistencyApiTests`（一致無誤報、偵測 remaining 不一致、job 寫 audit、非管理員 403），後端測試 82→86 全綠。完抽 audit summary 與敏感欄位鎖定/blocked audit 已於後續完成。
- [x] 2026-06-20：完成 Phase 9 完抽賞池 audit summary MVP（後端）。新增 `com.luckybox.admin.auditsummary`：admin-only `GET /api/admin/audit-summary/{slug}` 彙整賞池抽賞結果 —— 獎項分布（原始/已抽/剩餘數、是否最後賞）、總抽數、獨立中獎人數、訂單數、首末抽時間、最後賞是否發出，以及 seedHash 與 reveal 狀態。新增 `AuditSummaryApiTests`（抽賞結果彙整、非管理員 403、未知賞池 404），後端測試 86→89 全綠。至此 Phase 9 透明公平與審計除「管理員無法無紀錄修改敏感資料」之完整覆蓋外皆已完成。
- [x] 2026-06-20：完成 Phase 13 後端安全與政策測試補強。新增 `DrawSecurityTests`（未登入抽賞 401、抽數越界 400、點數伺服器端依 DB 價格計算、贈點優先於現金扣點）與 `AdminAccessSecurityTests`（匿名 401、一般會員打 admin API 403）；並補勾既有覆蓋（抽賞 transaction/多抽/最後賞整合測試、100 併發 load test 與「高併發無重複 ticket」完成條件）。後端測試 89→95 全綠。E2E、機率/總籤數/優惠券折抵單元測試與金流 webhook 重送測試皆已於後續完成。
- [x] 2026-06-20：完成本 session 後端改動的多代理對抗式程式碼審查與修正。審查 5 面向（併發/正確性、公平性密碼學、SQL/資料完整性、授權/安全、測試品質），每條發現經獨立反駁式驗證，確認 10 項（0 critical）。已修：(1) `deductBonusPoints`/`deductCashPoints` 改為檢查受影響列數、0 列時丟 `INSUFFICIENT_BALANCE` 並回滾，避免非預設多實例組態下的免費抽賞 + 帳本不一致；(2) 冪等鍵全域 UNIQUE 跨使用者重用時，捕捉約束違反回 409 `DRAW_IDEMPOTENCY_CONFLICT` 而非 500；(3) 最後賞合成 ticket serial 加上 orderId 確保跨生命週期唯一；(4) audit log JSON 轉義 slug 與控制字元；(5) 公平性演算法說明與 100 併發測試註記改為誠實描述可驗證範圍。新增 `DrawSecurityTests` 冪等衝突測試與 `DrawConcurrencyTests` 扣點守衛測試，後端 95→97 全綠。已知保留（縱深防禦/跨檔案、暫不動以免與前端機器衝突）：`/api/admin/**` filter 層角色規則、`requireAdmin` null 防護、HASH 模式 lazy commit、admin 測試依賴 dev-seed 管理員。
- [x] 2026-06-21：完成全後端 bug-hunt 與修正（6 hunters × 對抗式驗證，確認 8 bug／10 優化，0 critical）。已修：(1) **dev seed 生產安全**——`DevSeedRunner` 加 `@Profile("!prod")`，prod profile 下不再建立含已知密碼的示範管理員（dev/test 行為不變）；(2) leaderboard 公開即時開獎牆 `latestDraws()` 補狀態白名單，不再洩漏 DRAFT/PAUSED/ENDED 活動開獎；(3) 出貨/儀表板回應改用新 `common.PiiMasking.maskEmail` 遮罩 email（消除 5/7 遮罩、2/7 未遮罩的不一致）；(4) `updatePrize` 的 `remaining_quantity` 改為實際 AVAILABLE 票數、不預先計入未產生票券（消除 `PRIZE_QUANTITY_MISMATCH` 誤報，generateTickets 後由 sync 補正）；(5) wallet ledger 型別白名單補 `COUPON_BONUS`；(6) 公平性 `drawnTickets` 改以 `draws` 筆數計，修正售罄末賞 off-by-one。優化：新增 Flyway V6 六條熱路徑索引（payment_orders、draw_results×2、user_addresses、user_prizes×2）。新增公平性末賞一致性測試、更新三筆既有測試斷言至修正後值，後端 97→98 全綠。保留（衝突風險/設計決策）：CSRF 補償控制、N+1 重構（AccountOrder/PrizeBox/AdminShipment）、`/api/admin/**` filter 層規則——建議與另一台機器整合穩定後處理。`DevSeedService` 的 `@Transactional` no-op 已由另一台機器同步改為 `TransactionTemplate`。
- [x] 2026-06-21：完成稽核建議的 N+1 查詢優化。`AccountOrderRepository.drawOrders`（最多 50 筆訂單，原本每筆再查一次中獎結果，51→2 查詢）、`PrizeBoxRepository.findShipments` 與 `AdminShipmentRepository.findShipments`（原本每筆出貨再查一次品項，N+1→2 查詢）皆改為收集 id 後以 `WHERE ... shipment_id/draw_order_id IN (...)` 單次查詢、在記憶體依 id 分組；`AdminShipmentRepository.findShipments` 並補 `LIMIT 200` 與其他 admin 列表一致。行為不變、後端 98 測試全綠。剩餘保留項僅 CSRF 補償控制與 `/api/admin/**` filter 層角色規則，兩者皆動到 `SecurityConfig`、需先決定 SPA 認證策略，建議與另一台機器整合時一併處理。
- [x] 2026-06-21：完成 `/api/admin/**` filter 層角色規則（縱深防禦）。`SecurityConfig` 新增 `.requestMatchers("/api/admin/**").hasAnyRole("ADMIN","SUPER_ADMIN")`，並補 `accessDeniedHandler` 回 403 `ADMIN_REQUIRED`（與既有 in-service `requireAdmin()` 回應一致），未登入仍回 401 `AUTH_REQUIRED`；將 401/403 回應抽出共用 `writeApiError`。非管理員現於 security filter 層即被擋下，不再僅依賴各 service 的 `requireAdmin()`。後端 98 測試全綠（含 `AdminAccessSecurityTests` 匿名 401／會員 403）。當時剩餘稽核項 CSRF 補償控制已於 2026-06-23 完成。
- [x] 2026-06-21：完成忘記密碼 / 密碼重設後端流程（Phase 3 唯一真缺項）。新增 Flyway V7 `password_reset_tokens`（只存 token 的 SHA-256 hash），`PasswordResetController/Service/Repository` 與 `ForgotPasswordRequest`/`ResetPasswordRequest`；`POST /api/auth/forgot-password` 產一次性 token（SecureRandom 32 bytes）、30 分過期、dev log email stub、永遠回 202（不洩漏 email 是否註冊），`POST /api/auth/reset-password` 驗證 token 有效/未過期/未使用後以 BCrypt 改密並標記單次使用；`SecurityConfig` 放行兩端點。新增 `PasswordResetApiTests`（建立 token、未知 email 不建 token、有效 token 改密後新密碼可登入舊密碼失效、無效 token 400、過期 400、單次使用），後端測試 98→104 全綠。前端忘記密碼/重設表單與真實 email 寄送當時保留，皆已於後續完成。
- [x] 2026-06-22：完成 Phase 11 首儲活動（first-deposit bonus，後端）。`WalletService.completePaymentOrder` 在使用者第一筆 PAID 付款訂單完成時，額外發放設定化首儲紅利（`@Value luckybox.promo.first-deposit-bonus`，預設 0／關閉；主 `application.properties` dev 設 100、`LUCKYBOX_FIRST_DEPOSIT_BONUS` 可覆寫；測試 profile 維持 0，既有 `WalletApiTests` 不受影響），以 `WalletRepository.countPaidPaymentOrders==1` 判定首筆、`addFirstDepositBonus` 寫入 `FIRST_DEPOSIT_BONUS` 流水並記 audit，僅發放一次（idempotent，重複完成或第二筆不再發）。錢包總覽新增 `firstDepositPromo`（贈點額度＋是否符合資格）供前端宣傳；後台點數帳本型別白名單與 `typeLabel` 補「首儲贈點」。新增 `FirstDepositPromoTests`（首筆發 100、第二筆不發、資格旗標翻轉），以 `@TestPropertySource` 啟用促銷，後端測試 104→105 全綠。
- [x] 2026-06-22：完成首儲活動前端錢包橫幅。`WalletView` 用 `overview.firstDepositPromo`（`eligible` + `bonusPoints`）在儲值方案區頂端顯示 teal callout「首儲限定優惠：完成第一筆儲值再送 N LP 贈點」+「+N LP」pill，僅對符合資格者（無 PAID 訂單）顯示；`main.css` 新增 `.first-deposit-promo` 樣式（沿用 `--lb-teal`/`--lb-surface-cool`，含手機回流）。前端 build/lint 全綠，並以 Preview 實機驗證（新註冊用戶顯示橫幅、admin 已有儲值則正確隱藏，後端 dev promo=100 生效）。
- [x] 2026-06-22：完成 Phase 11 滿額贈點（消費門檻紅利，後端）。`DrawService` 注入 `luckybox.promo.spend-threshold` / `spend-threshold-bonus`（dev 預設 1000/80、環境變數可覆寫；測試 profile 預設 0，既有抽賞測試不受影響），於 `completeDrawOrder` 後以 `WalletRepository.totalDrawSpend`（COMPLETED draw_orders 之 point_spent 總和）計算「跨越前 < 門檻 ≤ 跨越後」判定首次跨越，天然冪等只發一次，`addSpendThresholdBonus` 寫入 `SPEND_THRESHOLD_BONUS` 紅利流水 + audit；後台點數帳本型別白名單與 `typeLabel` 補「消費門檻紅利」。新增 `SpendThresholdPromoTests`（跨越發 50、再抽不重發、未達門檻不發），以 `@TestPropertySource` 啟用促銷，後端測試 105→107 全綠。前端宣傳橫幅已於後續完成。
- [x] 2026-06-21：完成忘記密碼 / 密碼重設前端表單。新增 `ForgotPasswordView`（輸入 Email、送出後顯示「若已註冊則寄出連結」之不洩漏訊息）與 `ResetPasswordView`（讀取 `?token=` query、新密碼 + 確認、處理無效/過期/不一致錯誤、成功後導向登入）；新增 `/forgot-password`、`/reset-password` 公開路由、`authApi.requestPasswordReset/resetPassword`，並於會員登入頁加「忘記密碼？」入口。前端 build、lint（0 warning/0 error）、unit test 全綠。至此忘記密碼為前後端端到端完成；真實 email 寄送已於 2026-06-23 接入 `EmailService`。
- [x] 2026-06-22：完成 Phase 11 歐氣榜（幸運會員排行）。公開 `GET /api/leaderboard` 新增 `luckyMembers`（`luckyLimit` 參數，clamp 1~20，預設 10），`LeaderboardRepository.luckyMembers` 彙整抽中高稀有度（賞別 S/A 或最後賞）且賞池為 LIVE/SCHEDULED/SOLD_OUT 的會員，依幸運次數→S 賞數→最後賞數→名稱排序，沿用既有 `maskDisplayName` 遮罩名稱不洩漏個資。前台 `/leaderboard` 新增「歐氣榜」面板（🥇🥈🥉 / #N 名次、S賞/最後賞標籤、歐氣次數、top-3 高亮）與「本期歐氣王」指標卡。`PublicLeaderboardApiTests` 補歐氣榜斷言（抽中 A 賞會員出現、luckyWins=2、不洩漏 email）。前端 build/lint 全綠，並以 Preview 實機驗證（dev DB 既有抽賞資料下歐氣榜顯示 2 名遮罩會員 + 金/銀牌）。
- [x] 2026-06-22：完成 Phase 11 每日登入任務（簽到送點）。新增 Flyway V8 `daily_check_ins`（`user_id`+`check_in_date` 唯一）與 `com.luckybox.checkin` 模組：`GET /api/account/check-in` 回傳今日是否已簽到、連續/累積天數與獎勵額度；`POST /api/account/check-in` 以 Asia/Taipei 日期每日一次發放設定化紅利（`luckybox.promo.daily-check-in-bonus`，dev 預設 20；測試 profile 預設 0 不影響既有測試），靠唯一鍵保證冪等（重複簽到不重發點、不重複建列），`addCheckInBonus` 寫入 `CHECK_IN_BONUS` 流水 + audit；後台點數帳本型別白名單與 `typeLabel` 補「每日簽到獎勵」。前台會員中心新增每日簽到卡（連續/累積天數、每日獎勵、立即簽到/今日已簽到狀態）。新增 `CheckInApiTests`（新會員未簽到、簽到發 20 點且冪等），以 `@TestPropertySource` 啟用獎勵。前端 build/lint 全綠，並以 Preview 實機驗證（簽到後連續/累積→1、提示「獲得 20 紅利點」、按鈕轉為已簽到）。連續簽到階梯獎勵已於 2026-07-05 補齊。
- [x] 2026-06-22：完成 Phase 11 許願牆 MVP。新增 Flyway V9 `wishes`（狀態 PENDING/APPROVED/REJECTED/HIDDEN）與 `com.luckybox.wish` 模組：會員 `POST /api/account/wishes` 投稿（4~200 字、每日上限 5 則）與 `GET /api/account/wishes` 查看自己的願望與狀態；公開 `GET /api/wishes`（`SecurityConfig` 放行 GET）僅回傳 APPROVED 且作者匿名遮罩、不洩漏 email；管理員 `GET/PATCH /api/admin/wishes` 列表（可見真實名稱/email）與審核（核准/隱藏/退回）並寫 `ADMIN_WISH_MODERATED` audit。投稿是否自動上架由 `luckybox.wish.auto-approve`（dev 預設 true）控制。前台新增 `/wishes` 公開許願牆（投稿表單 + 字數計數 + 我的願望狀態 + 匿名牆 + 未登入 CTA）、導覽列與頁尾入口；後台新增 `/admin/wishes` 審核頁與側欄入口。新增 `WishApiTests`（投稿→匿名上牆→管理員下架、非管理員審核 403、內容過短 400、公開牆免登入）與 `WishModerationApiTests`（auto-approve=false 下 PENDING 不上牆、核准後匿名上牆）。後端全套 114 測試全綠、9 Flyway migration 驗證通過；前端 build/lint 全綠，並以 Preview 實機驗證（投稿後匿名「Lu**」上牆、管理員可見 email、審核 API 正常）。
- [x] 2026-06-23：完成 Phase 11 滿額贈點前端宣傳橫幅（補齊先前缺項）。錢包總覽 `WalletOverviewResponse` 新增 `spendThresholdPromo`（`active`/`threshold`/`bonusPoints`/`totalSpend`/`remaining`/`reached`），`WalletService` 注入消費門檻設定並以 `WalletRepository.totalDrawSpend`（COMPLETED draw_orders 之 point_spent 總和）計算進度；前台 `WalletView` 儲值面板顯示滿額贈點橫幅：未達標為 amber 進度條「再消費 X LP 即可獲得 Y LP 紅利」+ 已消費/門檻刻度，達標後轉 teal「已入帳」。`SpendThresholdPromoTests` 補錢包總覽斷言（未消費 active/remaining=門檻、跨越後 reached/remaining=0）。後端 117 測試全綠、前端 build/lint 全綠，並以 Preview 實機驗證（新會員 amber 進度、admin 已達標 teal）。
- [x] 2026-06-23：完成人工點數調整（後台客服運維，計畫 API line 682）。新增 `POST /api/admin/wallet-adjustments`（`com.luckybox.admin.walletledger` 新增 `AdminWalletAdjustmentController` + `WalletAdjustmentRequest`）：管理員對指定會員的現金點/贈點做正負調整，必填原因；service 驗證會員存在、點數種類、金額非 0、原因非空白，repository 以條件 UPDATE（`balance + amount >= 0`）原子防止扣成負餘額（影響列數 0 → 回 `WALLET_ADJUSTMENT_INSUFFICIENT`），成功寫入 `ADJUSTMENT` 流水（reference_type='Adjustment'、created_by=管理員）並記 `ADMIN_WALLET_ADJUSTED` audit，回傳該筆遮罩後流水。後台 `/admin/wallet-ledger` 新增「人工點數調整」表單（會員 ID／點數種類／調整點數／原因，成功顯示異動後餘額並刷新流水）。新增 `AdminWalletAdjustmentApiTests`（加/扣點與餘額、扣成負數被拒不留紀錄、缺原因/零金額/未知會員 400/404、一般會員 403）。後端 119 測試全綠、前端 build/lint 全綠，並以 Preview 實機驗證（表單調整 +150 紅利 → ADJUSTMENT 流水 balance_after=150、wallet bonus=150、audit payload 正確）。退款審核與「申請→審核」雙步流程已於後續 approval center 完成。
- [x] 2026-06-23：完成 Phase 13 登入/抽賞 rate limiting（安全硬化）。新增 `com.luckybox.ratelimit`：無外部依賴的單機 in-memory token-bucket `RateLimiter`（per-key 容量＋時間窗自然補充、@Scheduled 清理閒置 bucket 防記憶體成長），`RateLimitInterceptor`（HandlerInterceptor，於 controller 前短路）對 `/api/auth/login`、`/api/auth/register`、`/api/auth/forgot-password` 以來源 IP 計數、對 `/api/account/draw-orders` 以登入使用者計數，超限回 429 `RATE_LIMITED`（含 `Retry-After`）；`RateLimitWebConfig` 註冊攔截路徑。全部以 `luckybox.ratelimit.*` 設定（dev：auth 20/分、draw 30/分；`enabled` 可關），測試 profile `luckybox.ratelimit.enabled=false` 維持既有 119 測試不受影響。新增 `RateLimitApiTests`（登入/忘記密碼 per-IP 超限、抽賞 per-user 超限、auth per-IP 桶隔離、抽賞 per-user 桶隔離）。經 3 視角對抗式審查（bypass/安全、correctness/併發、測試充分性）+ 逐條反駁式驗證，確認 0 critical/high：已補 `Bucket.lastRefillNanos` volatile（消除無鎖讀 race detector 警示）與兩條桶隔離回歸測試。後端 119→124 測試全綠。X-Forwarded-For / Forwarded 來源解析已於 2026-07-05 完成；分散式節流（多實例/Redis）屬水平擴展項。
- [x] 2026-06-23：完成後台 Admin 2FA（TOTP，安全硬化）。新增 Flyway V10 `users.totp_secret`/`totp_enabled`（opt-in、預設關閉）與 `com.luckybox.account.TotpService`（RFC 6238/4226：HMAC-SHA1、6 碼、30 秒、±1 步容錯、base32 金鑰、otpauth URI，無外部依賴，相容 Google Authenticator/Authy）。管理員自助端點 `GET/POST /api/admin/2fa`（status/setup/enable/disable，`Admin2faController`+`Admin2faService`，啟用/停用皆需通過驗證碼並寫 `ADMIN_2FA_ENABLED`/`DISABLED` audit）。登入流程：`LoginRequest` 加選填 `totpCode`，`AccountService.login` 對已啟用 2FA 的帳號要求驗證碼，缺碼回 401 `TWO_FACTOR_REQUIRED`、錯碼回 `TWO_FACTOR_INVALID`（未啟用者行為不變，既有登入測試零影響）。前端：後台側欄新增 `/admin/security` 安全設定頁（顯示金鑰＋otpauth URI 供掃描/手動輸入、輸入驗證碼啟用、已啟用可停用），登入頁偵測 `TWO_FACTOR_REQUIRED` 顯示第二步驗證碼欄。新增 `TotpServiceTest`（金鑰/驗證碼/格式拒絕/otpauth URI）與 `Admin2faApiTests`（啟用後登入需碼、停用後免碼、一般會員 403；以暫時提升的測試管理員操作、不動既有 seeded admin）。後端 124→131 測試全綠、10 Flyway migration 驗證；前端 build/lint 全綠，並以 Preview 全程實機驗證（後台啟用→登入第二步輸入由 python 依 RFC 6238 計算之驗證碼→成功進入 /admin）。正式上線強制全後台 2FA 與備援碼屬 Phase 14 營運政策。
- [x] 2026-06-23：完成重新啟用 CSRF 防護（安全硬化，補齊先前延後項；P0 安全三件套收尾）。`SecurityConfig` 改為 config 化（`luckybox.security.csrf-enabled`，dev/prod 預設開、測試 profile 設 false 以維持既有 131 測試與所有變更 API 測試不變）。啟用時採 SPA cookie 模式：`CookieCsrfTokenRepository.withHttpOnlyFalse()`（token 存於 JS 可讀的 `XSRF-TOKEN` cookie）+ plain `CsrfTokenRequestAttributeHandler`（header 值等同 cookie raw token，故 axios 預設行為即自動以 `X-XSRF-TOKEN` 回送，前端零改動）；新增 `CsrfCookieFilter` 強制延遲 token 於每次回應寫出 cookie；CSRF 失敗由 `accessDeniedHandler` 辨識 `CsrfException` 回 403 `CSRF_TOKEN_INVALID`（與授權失敗的 `ADMIN_REQUIRED` 區隔）；`/api/webhooks/**` 預先 `ignoringRequestMatchers` 供 Phase 10 金流 webhook。取捨：放棄 BREACH XOR 隨機化以換 SPA cookie 相容（業界常見）。新增 `CsrfApiTests`（無 token POST→403、GET 寫出 XSRF cookie 且帶 cookie+header POST→201、錯誤 token→403）。後端 131→134 測試全綠；並以 Preview 全程實機驗證：頁面載入後 XSRF-TOKEN cookie 已寫出、原生 fetch 未帶 header 之 POST 回 403、前端 axios 註冊與已登入簽到等 mutation 皆正常通過。X-Forwarded-For 信任已於 2026-07-05 完成；跨多實例的 CSRF token/session 共享屬水平擴展與部署架構項。至此 P0 安全三件套（rate limiting、Admin 2FA、CSRF）全數完成。
- [x] 2026-06-23：完成後台會員詳情頁（Phase 8 完成條件補齊；含個資查閱稽核，回應 3.3 個資治理）。新增 `GET /api/admin/users/:id`（`AdminUserController`/`Service`/`Repository` + `AdminMemberDetailResponse`）：回傳單一會員的完整未遮罩 email/手機、角色/狀態/VIP、錢包（現金/紅利/鎖定/可用）、活動統計（抽賞數、完成抽賞、累積抽賞消費、已付款訂單數與金額、戰利品、出貨）、收件地址清單與近期 10 筆點數流水。**查閱完整個資屬敏感存取，service 每次寫入 `ADMIN_MEMBER_DETAIL_VIEWED` audit（操作者、角色、時間、查閱對象 userId）**，落實「管理員查閱完整個資要留 audit log」。前台新增 `/admin/users/:id` 詳情頁（Profile + 錢包與活動 + 收件地址 + 近期流水，含「本頁顯示完整個資、已記錄稽核」提示）與會員列表「查看明細」入口。新增 `AdminMemberDetailApiTests`（管理員看到未遮罩 email/手機 + 錢包/地址/流水且查閱被稽核、一般會員 403、未知會員 404）。後端 134→137 測試全綠；前端 build/lint 全綠，並以 Preview 實機驗證（列表遮罩 `ph***@e***.com`、詳情未遮罩完整 email、audit 每次查閱各記一筆）。
- [x] 2026-06-23：完成客服備註（會員層級，回應 4.4 客服）。新增 Flyway V11 `member_notes`（user_id/author_id/content/created_at，append-only）；`POST /api/admin/users/:id/notes` 由管理員對會員新增內部備註（內容必填、上限 500 字、寫 `ADMIN_MEMBER_NOTE_ADDED` audit），會員詳情回應新增 `notes` 清單（含作者顯示名稱）。前端會員詳情頁新增「客服備註」區塊（新增表單 + 依時間排序的備註列表）。`AdminMemberDetailApiTests` 擴充（新增備註→詳情可見且被稽核、空白內容 400、一般會員 403）。後端 137→139 測試全綠；前端 build/lint 全綠，並以 Preview 實機驗證（新增備註即時顯示作者「LuckyBox Admin」+ 時間）。修正一處前端 class 命名衝突（個資稽核提示與備註卡共用 `.admin-member-note` → 提示改為 `.admin-member-pii-note`）。
- [x] 2026-06-23：完成退款審核（付款訂單，回應 4.6 退款審核、Phase 10 退款雛形）。`POST /api/admin/payment-orders/:id/refund`（`AdminPaymentOrderController/Service/Repository` + `RefundRequest`）：管理員以必填原因退款 PAID 訂單 —— 條件式將 PAID→REFUNDED 防併發重複退款，並回收此訂單原先入帳的現金點與紅利點（以 `balance >= amount` 條件 UPDATE 原子扣回，餘額不足回 400 `REFUND_INSUFFICIENT_BALANCE` 並整筆交易回滾，請改走人工點數調整），寫入負額 `REFUND` 流水（reference=PaymentOrder）+ `ADMIN_PAYMENT_REFUNDED` audit。**僅回收該訂單本身入帳之點數，與訂單無關的促銷贈點（如首儲）不受影響。** 真實金流退錢屬 Phase 10。前端後台付款訂單頁對 PAID 訂單新增「退款」操作（內含原因輸入 + 確認）。新增 `AdminPaymentRefundApiTests`（退款→REFUNDED + 回收點數 + 2 筆負額流水 + audit、重複退款 400、缺原因 400、餘額不足 400 且訂單維持 PAID 不寫流水、一般會員 403、未知訂單 404）。後端 139→142 測試全綠；前端 build/lint 全綠，並以 Preview 實機驗證（退款 collector 訂單 → REFUNDED、現金 1000 全回收、紅利回收訂單之 150 而保留首儲 100、REFUND 流水與 audit 正確）。
- [x] 2026-06-23：完成真實 Email/SMTP 抽象（解鎖密碼重設信閉環；回應 5.2 Java Mail Sender、Phase 14 通知信）。新增 `com.luckybox.mail.EmailService`：config 化（`luckybox.mail.enabled`，預設 false）；以 `Optional<JavaMailSender>` 注入——dev/test 無 `spring.mail.host` 時無 JavaMailSender bean → 退回 log 模式（不需 SMTP 伺服器、既有測試零影響），正式環境設定 SMTP + `enabled=true` 後經 `JavaMailSender` 實寄；寄信失敗吞例外並記 warn 不中斷主流程。`PasswordResetService` 改用 EmailService 寄出重設信（連結 = 可設定 `luckybox.app.base-url` + `/reset-password?token=<原始token>`，DB 仍只存 SHA-256 雜湊；維持永遠 202 不洩漏帳號是否存在）。`application.properties` 新增 `luckybox.mail.*`/`luckybox.app.base-url` 與 SMTP 範例（註解）。新增 `EmailServiceTest`（啟用寄送、停用、無 bean fallback、寄信失敗吞例外、空收件人略過）與 `PasswordResetServiceTest`（重設信含原始 token 連結且 base-url 去尾斜線、信中 token 之 SHA-256 等於 DB 雜湊、未知帳號不寄信不建 token）。經 3 視角對抗式審查 + 逐條驗證，採納並修正 2 項：(1) **停用/log 模式原本於 INFO 印出含 token 的信件內文** → 改為 INFO 只記中繼資料、內文移至 DEBUG（正式環境預設關閉），避免誤設為 log 模式時 token 進入保留日誌；(2) 補 `PasswordResetService.requestReset` 重設連結建構的單元測試覆蓋。後端 142→149 測試全綠。真實 SMTP 供應商帳密與寄送量設定屬 Phase 14 上線設定。
- [x] 2026-06-23：完成瑕疵/換貨出貨處理（回應 4.4 客服「處理瑕疵」、退換貨政策落地為系統能力）。新增 `POST /api/admin/shipments/:id/resolve`（`ResolveShipmentRequest{resolution,reason}`）：對 SHIPPED/DELIVERED 出貨單做 **退回(RETURNED)** 或 **換貨(EXCHANGED)** 處理，原因必填且併入 `admin_note`（保留原備註）、寫 `ADMIN_SHIPMENT_RESOLVED` audit 並通知會員。退回：所屬 `user_prizes` 回到 `IN_BOX` 並清除 `shipment_id` → 退回戰利品盒、可重新申請出貨；換貨：標記 `EXCHANGED`（換貨後續離線處理）。`NotificationService` 補 `SHIPMENT_RETURNED`/`SHIPMENT_EXCHANGED` 站內通知。前端後台出貨頁對已出貨/已送達單新增「退回/換貨」表單（處理方式 + 原因）。新增 `AdminShipmentResolveApiTests`（退回→RETURNED+戰利品回 IN_BOX 解除連結+audit+通知、換貨→EXCHANGED、未出貨不可處理 400、缺原因 400、不合法處理方式 400、一般會員 403）。後端 149→152 測試全綠；前端 build/lint 全綠，並以 Preview 實機驗證（後台對 SHIPPED 出貨單退回 → RETURNED、note 併入原因、戰利品回 IN_BOX 並解除連結、audit 與 SHIPMENT_RETURNED 通知皆正確）。
- [x] 2026-06-23：完成客服補償發點（回應 4.4 客服「補償」、3.4 補償點落地）。新增 `POST /api/admin/users/:id/compensation`（`CompensationRequest{amount,reason}`）：管理員以必填原因發放正整數補償點，入紅利餘額並寫一筆 `COMPENSATION` 點數流水（point_kind=BONUS、reference_type=Compensation、reason、created_by=管理員），記 `ADMIN_COMPENSATION_GRANTED` audit，並透過新增的 `NotificationService.notifyCompensation`／`NotificationRepository.createNotification`（通用通知）發出 `COMPENSATION_GRANTED` 站內通知。前端會員詳情頁新增「客服補償發點」表單（點數 + 原因，成功後回填錢包/流水）。新增 `AdminMemberCompensationApiTests`（發放→紅利+50、COMPENSATION 流水、audit、通知；金額非正 400、缺原因 400、未知會員 404、一般會員 403）。後端 152→154 測試全綠；前端 build/lint 全綠，並以 Preview 實機驗證（發放 80 點 → 紅利 150→230、COMPENSATION 流水/audit/通知皆正確）。**補償點以紅利點入帳並以流水型別 `COMPENSATION` 分類追蹤；獨立「可花用補償餘額」第三點種屬另一次記帳模型變更，不列 launch blocker。**
- [x] 2026-06-24：完成 Banner/News 排程上下架（自動上下架，補齊活動排程缺項）。新增 Flyway V12 `banners.publish_at`/`unpublish_at`、`news.unpublish_at`。**公開查詢以時間窗過濾達成免背景排程的自動上下架**：`BannerRepository.findActiveBanners` 改為 `status='ACTIVE' AND (publish_at IS NULL OR publish_at<=now) AND (unpublish_at IS NULL OR unpublish_at>now)`；`NewsRepository` 公開列表與單篇同樣以 `published_at<=now AND (unpublish_at IS NULL OR unpublish_at>now)` 過濾（先前 PUBLISHED 但未來 published_at 會誤顯示，現已修正為真正排程上架）。後台 Banner/公告請求/回應/儲存補上下架時間欄位，service 驗證 ISO 格式且下架須晚於上架（`INVALID_BANNER_SCHEDULE`/`INVALID_NEWS_SCHEDULE`，以 `Instant` 比較避免 ISO 字串次秒精度排序陷阱）。前端後台 Banner 編輯加 `datetime-local` 上/下架時間欄、公告編輯加下架時間欄（沿用既有 ISO 欄位風格）。新增 `ContentSchedulingApiTests`（news/banner 未來上架隱藏、過期下架隱藏、窗內顯示；下架早於上架 400）。後端 154→157 測試全綠、12 Flyway migration 驗證；前端 build/lint 全綠，並以 Preview 實機驗證（建立未來上架 banner → 公開不顯示；改為過去上架 → 公開顯示；後台日期欄正常）。
- [x] 2026-06-24：完成 VIP 等級制度（回應 4.3 「可查看累積消費與等級進度」）。新增 `com.luckybox.vip` 模組：依會員累積抽賞消費（COMPLETED draw_orders 之 point_spent 總和）自動判定 VIP 等級 REGULAR/SILVER/GOLD/PLATINUM，門檻 config 化（`luckybox.vip.silver/gold/platinum-threshold`，dev 預設 1000/5000/20000、建構子強制遞增排序防設定顛倒）。`GET /api/account/vip` 回傳目前等級、累積消費、下一級名稱/門檻、距離下一級所需消費與進度百分比，並以 write-on-read 把 `users.vip_level` 刷新為計算後等級（供後台/個人資料顯示）。前端會員個人資料頁（`AccountProfileView`，Codex 未動的獨立檔以降衝突）新增 VIP 進度卡：等級徽章、累積消費、amber 進度條與「再消費 X LP 升級為 Y」（達頂級顯示已達最高）。新增 `VipStatusApiTests`（以 `@TestPropertySource` 小門檻驗證 REGULAR→SILVER→PLATINUM 升級、進度% 計算、vip_level 落地、未登入 401）。後端 185→187 測試全綠；前端 build/lint 全綠，並以 Preview 實機驗證（新會員 REGULAR、注入 1200 消費後 → 銀卡、進度 5%、再消費 3800 升級金卡、users.vip_level 刷新為 SILVER）。VIP 專屬優惠券/活動 gating 已於後續完成；等級到期/降級屬留存規則決策，不列 launch blocker。
（併行說明：本輪為多代理協作，Codex 併行完成分析事件追蹤 `analytics`/V13、Phase 10 金流 webhook＋簽章＋對帳＋mock checkout `wallet`/`admin.payment`/V14；本次 VIP 刻意選在無交集的獨立模組，合併後全庫 187 測試全綠。）
- [x] 2026-06-29：完成賞池詳情獎項揭露與出貨/退換貨政策卡 MVP。獎項區新增摘要 chips、機率揭露說明、每獎項剩餘 / 原始數量、即時機率、最後賞條件提示、剩餘比例條與低庫存 / 已抽完 / 最後賞狀態；手機版改為單欄可掃讀清單。政策區改為出貨與退換貨雙卡，顯示本賞池備註、合併出貨、地址 / 免運券、瑕疵照片保留、客服查核與完整政策 / 戰利品盒 / 客服導流。至此 10.4 賞池詳情頁剩餘四項（獎項列表、原始/剩餘/機率、出貨說明、退換貨說明）已完成 MVP。
- [x] 2026-06-29：完成首頁商品卡資訊 MVP（10.3）。公開賞池列表 API 補回 `coverImageUrl` 供商品卡顯示主圖，前端卡片新增商品圖 fallback、狀態 badge、來源類型 badge、商品名稱 / 副標、每抽 LP、剩餘 / 總抽數、剩餘比例 progress bar、最稀有剩餘提示、最後賞 badge 與依狀態切換的「立即開抽 / 查看詳情 / 查看結果」CTA；手機版保持單欄可掃讀。新增 Campaign API 測試確認列表回傳 `coverImageUrl`。
- [x] 2026-06-29：完成首頁探索模組 MVP（10.2 補齊）。既有首頁賞池區已提供狀態 tabs、類型篩選、商品列表與 LIVE strip，本次新增熱門賞池榜與新手任務入口；熱門榜使用 `/api/leaderboard` 的 `popularCampaigns` 顯示排名、抽數、玩家數、每抽 LP、售出進度與稀有提示，新手任務導向註冊、賞池列表與出貨政策。同步擴充來源類型支援 `GK`（首頁篩選、後台建立 / 編輯、公開與後台 label）。至此 10.2 除頂部導覽完整搜尋整合外皆已完成 MVP。
- [x] 2026-06-29：完成頂部導覽搜尋 MVP（10.2 收尾）。`App.vue` 新增桌機導覽搜尋與手機頂部搜尋列，可搜尋賞池、品牌與描述，送出後導向 `/#campaigns` 並以 `q` query 保留條件；首頁讀取 / 回寫 `q`、狀態、類型、排序與分頁 query，搜尋結果列顯示目前關鍵字，空結果可一鍵清除篩選。至此 10.2 首頁模組全部完成 MVP。
- [x] 2026-06-29：完成會員中心總覽 MVP（10.6）。`/account` 第一屏整合錢包、快速 Mock 儲值、最近抽賞、戰利品總數 / 可出貨 / 待出貨、優惠券統計與 VIP 等級；資料來自既有 `/api/account/wallet`、`/api/account/orders`、`/api/account/prizes`、`/api/account/shipments`、`/api/account/coupons`，快速儲值成功後刷新 session 與總覽。至此 10.6 會員中心全部完成 MVP。
- [x] 2026-06-29：完成後台賞池列表搜尋 / 篩選 / 排序 MVP（10.7 部分）。`GET /api/admin/campaigns` 新增白名單 `sort` 參數（latest、updatedDesc、status、titleAsc、priceAsc、priceDesc、remainingAsc、remainingDesc），前端 `/admin/campaigns` toolbar 新增排序選單並即時重載列表；新增測試驗證價格降序、剩餘數升序與非法 sort 400。搜尋與狀態篩選沿用既有能力，至此 10.7 第一項完成。
- [x] 2026-06-29：完成後台賞池編輯分段表單 MVP（10.7 部分）。`/admin/campaigns` 編輯區改為基本資料、商品與銷售、媒體與說明、出貨與售後、公平性與最後賞五段式 fieldset，同一個建立/更新 payload 與儲存按鈕不變；同時補上原 payload 已支援但畫面缺少的 Banner 圖 URL 欄位，讓營運建立賞池時可一次維護列表封面與詳情頁主視覺。
- [x] 2026-06-29：完成後台獎項即時計算總籤數 MVP（10.7 部分）。獎項管理區新增普通總籤數摘要與編輯表單即時計算提示，依目前選取獎項、原始數量與「最後賞」勾選狀態推算儲存後普通 ticket 總量，並顯示與賞池總抽數相符、尚差或超出的差額，降低生成 tickets 前的數量錯配風險。
- [x] 2026-06-29：完成後台賞池危險操作二次確認 MVP（10.7 部分）。`/admin/campaigns` 的生成 Ticket、發布上架與暫停下架在送出 API 前先顯示確認對話框，確認文字包含目前賞池名稱與操作影響，避免營運誤觸造成前台可抽狀態或 ticket 數量變動。
- [x] 2026-06-30：完成後台賞池發布前 checklist MVP（10.7 部分）。`/admin/campaigns` 發布控制區新增六項發布檢查：主檔資料、公開說明、獎項數量、Ticket 生成、最後賞與狀態；每項顯示通過狀態與原因，發布按鈕需 checklist 全部通過才可送出，讓營運在上架前能直接看到尚缺資料。
- [x] 2026-06-30：完成後台營運儀表板核心指標 MVP（10.7 收尾）。`/api/admin/dashboard` 新增今日活躍會員（今日儲值或抽賞 distinct users）與客服待處理（待審願望 + 失敗付款），並把原今日儲值卡更新為「今日營收」；`/admin` 現可顯示今日營收、今日抽數、今日活躍會員、未出貨與客服待處理。同步更新 `AdminDashboardApiTests` 與 `docs/api.md` 範例，至此 10.7 後台 checklist 全部完成 MVP。
- [x] 2026-06-30：完成 Dashboard 即將售完與抽賞告警 MVP（15.1 部分）。`/api/admin/dashboard` 新增 `nearSoldCampaigns`（LIVE 且剩餘 10% 或 10 張內）與 `drawAlerts`（今日 FAILED draw_orders + 即時資料一致性異常數）兩張指標卡；補強 `AdminDashboardApiTests` 測試新 metric key / label，並更新 `docs/api.md` 範例。至此 15.1 Dashboard 指標項目全部完成 MVP。
- [x] 2026-06-30：完成賞池發布前檢查 MVP（15.2 全部）。`/admin/campaigns` 將發布 checklist 拆成商品名稱、商品圖、來源、價格、總籤數、prize 數量、最後賞、出貨、退換貨、機率預覽、Ticket 生成、Dry Run 與狀態檢查；新增機率預覽表與 Dry Run 結果區。後端新增唯讀 `POST /api/admin/campaigns/:id/dry-run`，抽樣目前 AVAILABLE tickets 並回傳獎項 rank/name，不扣庫存也不建立訂單；前端需 dry run 通過才可發布。
- [x] 2026-06-30：完成客服功能 MVP（15.3 全部）。整合既有 `/admin/users`、`/admin/orders`、`/admin/draws`、`/admin/shipments` 與會員詳情能力：客服可查會員、付款訂單、抽賞紀錄、出貨與退換貨；本次補上會員詳情最近 20 筆戰利品明細（賞池、獎項、籤號、狀態、出貨單、取得時間），並修正後台出貨列表可用 RETURNED / EXCHANGED 篩選退回與換貨紀錄。會員詳情既有客服備註、補償發點、停權能力納入 15.3 完成範圍。
- [x] 2026-06-30：完成產品指標 Dashboard MVP（16 部分）。`/api/admin/dashboard` 新增 `productMetrics`，`/admin` 顯示註冊→首儲、首儲→首抽、每日抽數、每日儲值、ARPPU、平均每人抽數、售完時間、戰利品出貨申請率、客服案件數、退款/補償率、金流失敗率、抽賞 API 錯誤率與抽賞 p95 latency。訪客→註冊轉換率已於同日後續用 `visitor_sessions` 與註冊綁定補齊。
- [x] 2026-06-30：完成訪客到註冊轉換率 MVP（16 收尾）。新增 Flyway V13 `visitor_sessions`，公開 `POST /api/analytics/visit` 以匿名 visitorId upsert 訪客 session；前端 App 於初次載入與路由切換送出 visit，註冊 payload 自動帶同一 visitorId；註冊成功後將 visitor session 綁定 userId，Dashboard `visitorToRegistration` 改為真實百分比。新增 `VisitorAnalyticsApiTests` 驗證 visit、註冊綁定與 Dashboard 轉換率。至此 16 產品指標全部完成 MVP。
- [x] 2026-06-30：完成 Mock 金流 webhook sandbox MVP（Phase 10 部分）。新增 Flyway V14 `payment_webhook_events`，建立 `POST /api/webhooks/payment/mock` signed webhook：以 `X-LuckyBox-Signature` 驗 HMAC-SHA256，支援 PAID / FAILED / CANCELED，PAID 重用錢包入點與首儲邏輯，FAILED/CANCELED 不入點並標記付款訂單終態；provider + event id 唯一防重送，duplicate 回 `duplicate=true` 且不重複入點，金額不一致記 `AMOUNT_MISMATCH` 並保留訂單狀態。新增 webhook rate limit 設定與測試，更新 `docs/api.md`、`README.md` 與本計劃書 checklist。新增 `PaymentWebhookApiTests` 與擴充 `RateLimitApiTests`。真實供應商選型、信用卡 sandbox 導頁、provider raw payload 後台詳情與 reconciliation script 皆已於後續完成；正式 merchant/callback 證據仍屬外部 gate。
- [x] 2026-06-30：完成後台付款 provider raw payload 檢視 MVP（Phase 10 部分）。新增 admin-only `GET /api/admin/payment-orders/:id`，回傳付款訂單摘要、完整 `provider_payload`、最近 50 筆對應 `payment_webhook_events`（eventId、status、amount、processed、message、created/processed time、rawPayload）；後台 `/admin/orders` 增加「詳情」展開，營運可直接檢查 mock webhook 原始 payload、處理結果與錯誤訊息，列表仍維持遮罩會員 email。新增 `AdminPaymentOrderApiTests` 覆蓋 admin 可讀詳情、一般會員不可讀、webhook raw payload 不洩漏會員 email；更新 `docs/api.md` 與本計劃書 checklist。真實 provider 對帳 script 與信用卡 sandbox 導頁皆已於後續完成。
- [x] 2026-06-30：完成付款 reconciliation script MVP（Phase 10 收尾但不含真實供應商）。新增可直接執行的 `backend/scripts/reconcile-payments.sh`，預設檢查 `backend/data/luckybox-dev.sqlite`，也可用 `--db` 指定 SQLite 檔；報表輸出付款訂單 provider/status 摘要、webhook provider/status/message 摘要與異常清單，並支援 `--strict` 在偵測到問題時 exit 2。異常類型包含已付款但 ledger 入點不符、失敗/取消卻有入點、PAID webhook 但訂單未付款、FAILED/CANCELED webhook 與訂單狀態不一致、金額不一致、webhook 找不到付款訂單。新增 `PaymentReconciliationScriptTests` 以臨時 SQLite 驗證 clean/dirty DB；更新 `README.md`、`docs/api.md`、`docs/testing.md` 與計劃書。provider CSV 對帳檔匯入已於 2026-07-05 補齊。
- [x] 2026-06-30：完成 Mock checkout 付款導頁 MVP（9.4 / Phase 10 部分）。會員中心與錢包儲值建立 `PENDING` PaymentOrder 後導向 `/payment/mock/:orderId`，顯示信用卡 sandbox 表單；確認付款呼叫 `POST /api/account/payment-orders/:id/mock-checkout/confirm`，由後端產生 signed mock webhook 並走既有 `payment_webhook_events`、入點、ledger、audit 流程，前端不持有 webhook secret，也不送出或儲存卡號。成功頁提供回錢包與訂單紀錄。新增 `MockPaymentCheckoutService` 與 `WalletApiTests` 覆蓋 checkout 入點、冪等與跨會員不可讀；更新 `docs/api.md`、`docs/testing.md`、`README.md` 與本計劃書 checklist。驗證：後端 176 tests、前端 lint、Vitest、build 全綠；新版 8080 live HTTP 註冊 / 建單 / confirm / 錢包查詢成功。真實金流供應商選型已於 2026-07-04 收斂，provider 對帳檔匯入已於 2026-07-05 補齊。
- [x] 2026-06-30：完成後台賞池敏感欄位與獎項鎖定 MVP（9.1 / Phase 9）。已開抽、暫停、完抽、結束或已有抽賞紀錄的賞池，禁止直接修改 slug、來源、價格、總籤數、狀態、開賣時間、公平性與最後賞設定，阻擋時回 `CAMPAIGN_SENSITIVE_FIELDS_LOCKED` 並寫 `ADMIN_CAMPAIGN_SENSITIVE_CHANGE_BLOCKED` audit；`LIVE` / `PAUSED` / `SOLD_OUT` / `ENDED` 賞池禁止新增/修改獎項與補生成 ticket，回 `CAMPAIGN_PRIZES_LOCKED` 並寫 blocked audit。為配合 SQLite 單連線設定，blocked audit 採同一交易搭配 `noRollbackFor=ApiException` 保留。後台表單同步鎖定敏感欄位與獎項操作，但仍允許更新標題、圖片、描述、出貨與退換貨等展示資訊。新增 `AdminCampaignApiTests` 覆蓋敏感修改阻擋、展示欄位可更新與公開後獎項阻擋；更新 `docs/api.md`、`docs/testing.md` 與本計劃書。驗證：後端 179 tests、前端 lint、Vitest、build 全綠。
- [x] 2026-07-01：完成首頁與賞池圖片效能 MVP（14 部分）。新增 `luckybox-prize-banner-optimized.jpg` 作為壓縮預設商品圖，將首頁與詳情頁 fallback 圖由 1.7MB PNG 改為約 150KB JPEG；首頁商品卡與詳情縮圖加上 `loading="lazy"` / `decoding="async"`，Hero 與詳情主圖加上 eager/high priority，保持首屏優先載入。同步補勾既有首頁分頁、API 載入 skeleton 與錯誤狀態。驗證：前端 lint、Vitest、build 全綠。
- [x] 2026-07-01：完成熱門榜單短 TTL cache MVP（14 效能）。`LeaderboardService` 對 `popularCampaigns` 增加依 `popularLimit` 分 key 的 in-memory cache，預設 15 秒，可用 `LUCKYBOX_LEADERBOARD_POPULAR_CACHE_TTL_SECONDS` 調整或設 0 關閉；測試 profile 預設關閉避免整合測試資料清理被 cache 干擾。新增 `LeaderboardServiceTest` 覆蓋 TTL 內命中、過期刷新、limit 分離與關閉快取；更新 `docs/api.md` 與 `docs/testing.md`。驗證：`./mvnw test` 後端 183 tests 全綠。
- [x] 2026-07-01：完成 DB 熱路徑索引回歸測試 MVP（14 效能）。確認核心 schema 已建立 campaign status、ticket campaign/status、draw user、ledger user 索引，新增 `DatabaseIndexTests` 直接查 SQLite `PRAGMA index_list/index_info` 驗證索引存在與欄位順序，避免後續 migration 重整時誤刪抽賞/列表熱路徑索引；更新 `docs/testing.md` 與本計劃書。驗證：`./mvnw test` 後端 184 tests 全綠。
- [x] 2026-07-01：完成公開賞池剩餘數 DB 來源 MVP（14 效能/一致性）。公開賞池列表與詳情頁不再直接信任 `kuji_campaigns.remaining_tickets` / `prizes.remaining_quantity` counter，而是從 `kuji_tickets.status='AVAILABLE'` 聚合計算剩餘籤數、普通獎項剩餘數、機率、稀有剩餘提示與熱門/剩餘排序；最後賞因不產生普通 ticket，維持使用 prize counter。新增 `CampaignApiTests.publicCampaignRemainingCountsUseAvailableTicketsWhenCountersAreStale` 建立 counter 失真資料並驗證前台仍顯示 ticket 真相；更新 `docs/api.md`、`docs/testing.md` 與本計劃書。驗證：`./mvnw test` 後端 185 tests 全綠。
- [x] 2026-07-01：完成錯誤回應不暴露 stack trace MVP（13 安全）。新增 `GlobalExceptionHandlerTest` 直接驗證未預期例外的 API response 固定回 `INTERNAL_ERROR`、泛用中文訊息與空 details，且不包含原始 exception message、Java 類名或 stack trace 片段；`docs/testing.md` 同步納入後端測試範圍。
- [x] 2026-07-01：完成抽賞 API 價格信任邊界與抽數上限 MVP（13 安全）。強化 `DrawSecurityTests`：驗證超出 1-10 抽會回 `VALIDATION_FAILED` 與 `details.quantity`，且 client 夾帶 `pricePerDraw`、`pointSpent`、`originalPointSpent`、現金/紅利扣點欄位時，後端仍以 SQLite campaign price 計算 `originalPointSpent` / `pointSpent`；更新 `docs/api.md`、`docs/testing.md` 與本計劃書。
- [x] 2026-07-01：完成 Audit log 後台不可刪除 MVP（13 安全）。新增 `DELETE /api/admin/audit-logs/{id}` 明確拒絕刪除並回 405 `AUDIT_LOG_IMMUTABLE`；擴充 `AdminAuditLogApiTests` 驗證一般後台管理員刪除嘗試後 audit row 仍存在；更新 `docs/api.md`、`docs/testing.md` 與本計劃書。
- [x] 2026-07-01：完成 Production 關閉 debug payment route MVP（13 安全）。新增 `luckybox.payment.mock-enabled` 開關與 `application-prod.properties`（prod 預設 seed off、mock payment off）；當 mock payment 關閉時，`/api/account/payment-orders/{id}/complete`、`/api/account/payment-orders/{id}/mock-checkout/confirm`、`/api/webhooks/payment/mock` 均回 404 `PAYMENT_MOCK_DISABLED`，且不寫 webhook event、不入點。新增 `PaymentMockDisabledTests`；更新 `README.md`、`docs/api.md`、`docs/testing.md` 與本計劃書。
- [x] 2026-07-01：完成環境變數不進 git MVP（13 安全）。補強 root/backend/frontend `.gitignore`：忽略 `.env*`、本機 SQLite DB、uploads、logs，僅允許 `.env.example`；擴充 `.env.example` 只放本機 placeholder，不放真實 payment secret、SMTP password 或 dev admin credential；新增 `EnvironmentSecretPolicyTests` 驗證 ignore 規則、placeholder 與 prod profile dev seed/mock payment 預設關閉；更新 `docs/operations.md`、`docs/testing.md` 與本計劃書。
- [x] 2026-07-02：完成 API input Bean Validation MVP（13 安全）。補齊所有 `@RequestBody` 的 `@Valid`，並為後台 banner/news/coupon、退款、出貨退換貨、會員備註/補償、人工點數、2FA 與許願池 request DTO 加上基本必填、長度與數值邊界；新增 `RequestBodyValidationPolicyTests` 鎖定「request body 必須走 Jakarta Bean Validation」規則。驗證：政策測試、受影響 36 個 API 測試、完整 `./mvnw test` 後端 195 tests 全綠。
- [x] 2026-07-02：完成後台完整個資預設遮罩 MVP（3.3 個資治理 / 13 安全）。`GET /api/admin/users/:id` 預設回 `piiRevealed=false` 並遮罩 email、手機、收件人、收件電話與地址；需要完整資料時由前端會員詳情頁點擊「顯示完整個資」，改打 `?reveal=true`，後端回未遮罩資料並寫 `ADMIN_MEMBER_DETAIL_VIEWED` audit。更新 `docs/api.md`、`docs/testing.md` 與本計劃書。驗證：`AdminMemberDetailApiTests` 覆蓋預設不含原始 PII、揭露才 audit；完整後端 `./mvnw test` 195 tests、前端 lint、Vitest、build 全綠。
- [x] 2026-07-02：完成後台圖片上傳格式與 MIME 限制 MVP（5.2 / 13 安全）。新增 admin-only `POST /api/admin/uploads/images`，以 `LUCKYBOX_UPLOAD_DIR` 儲存本機圖片並回傳 `/uploads/images/{date}/{uuid}.ext`，公開 `GET /uploads/**` 讀取；上傳端僅允許 JPG / PNG / WebP，預設大小上限 2 MB，可用 `LUCKYBOX_UPLOAD_MAX_IMAGE_SIZE_BYTES` 調整，且同時驗證宣告 `Content-Type` 與 magic bytes，避免副檔名偽裝。新增 `AdminImageUploadApiTests` 覆蓋管理員上傳、公開 URL 讀取、一般會員 403、超大檔案與 MIME 不符拒絕；更新 `docs/api.md`、`docs/operations.md`、`docs/testing.md` 與本計劃書。
- [x] 2026-07-02：完成後台圖片上傳 UI MVP（4.5 / 9.1）。新增共用 `AdminImageUploadField` 與 `adminUploadApi`，後台 Banner 圖片、賞池封面圖、賞池 Banner 圖與獎項圖片欄位都保留手動貼 URL，同時可直接選檔上傳並自動填入 `/uploads/**` URL；上傳成功 / 失敗在表單旁顯示狀態，獎項鎖定時同步禁用上傳。新增 `AdminImageUploadField` unit test 覆蓋選檔後呼叫上傳 API 並回填 URL；更新 `docs/api.md`、`docs/testing.md` 與本計劃書。
- [x] 2026-07-02：完成抽賞計算 unit test 補強（12 驗收測試情境）。將公開賞池剩餘比例 / 機率公式抽為 `CampaignMath` 並由 `CampaignRepository` 使用，新增 `CampaignMathTest` 覆蓋四捨五入、零剩餘普通籤與最後賞機率歸 0；將後台普通獎項總量公式抽為 `AdminCampaignPrizeMath` 並由 `AdminCampaignPrizeRepository` 使用，新增 `AdminCampaignPrizeMathTest` 驗證最後賞不計入普通 ticket 總量；將抽賞折抵上限抽為 `DrawPriceCalculator` 並由 `DrawService` 使用，新增 `DrawPriceCalculatorTest` 驗證折抵不超過原始消費且最終扣點不低於 0。同時補勾既有 `AdminCampaignApiTests.adminPublishesAndPausesCampaign` 已覆蓋的後台發布賞池 integration test。本機後端測試待安裝 Java 21 後重跑。
- [x] 2026-07-02：補勾儲值入點 integration test（12 驗收測試情境）。既有 `WalletApiTests` 已驗證 legacy mock complete 與 `/payment/mock/:orderId` sandbox checkout 均會將付款訂單標為 PAID、入現金點 / 贈點、寫入 2 筆 wallet ledger、刷新 `/api/auth/me` 餘額，且重送不重複入點；mock checkout 另驗證只保留 1 筆 webhook event。
- [x] 2026-07-02：完成訪客瀏覽 E2E MVP（12 驗收測試情境）。`frontend/e2e/vue.spec.js` 改為 mock `/api/auth/me`、訪客分析、公開賞池、Banner、Leaderboard 與公告 API，驗證訪客可瀏覽首頁 hero、賞池卡、搜尋 query、LIVE strip、熱門賞池榜與公告頁；`frontend/playwright.config.js` 新增 `PLAYWRIGHT_WEB_SERVER_COMMAND`、`PLAYWRIGHT_WEB_SERVER_PORT`、`PLAYWRIGHT_BASE_URL` 與 `PLAYWRIGHT_HEADLESS` 支援，讓 npm/pnpm 與 headless 環境都可執行。驗證：安裝 Playwright Chromium 後，`PLAYWRIGHT_HEADLESS=true ... playwright test --project=chromium` 通過 2 tests。
- [x] 2026-07-02：完成註冊登入與儲值 E2E MVP（12 驗收測試情境）。`frontend/e2e/vue.spec.js` 的 mock auth 狀態改為可隨註冊 / 登入 / mock checkout 更新，新增註冊成功、登入成功與會員儲值流程測試：註冊/登入後導覽顯示會員與 LP，儲值流程從錢包建立付款訂單、進入 mock checkout、確認付款、回錢包顯示成功訊息與更新後 895 LP。驗證：`PLAYWRIGHT_HEADLESS=true ... playwright test --project=chromium` 通過 5 tests。
- [x] 2026-07-02：完成抽賞 E2E MVP（12 驗收測試情境）。`frontend/e2e/vue.spec.js` 新增會員抽賞流程 mock：賞池詳情前後剩餘數、`POST /api/account/draw-orders`、抽後 `/api/auth/me` 餘額刷新與 campaign draw history 都由測試控制；驗證登入 redirect、2 抽 + 折扣碼確認、結果卡揭曉、實扣 / 原價 / 折抵摘要、剩餘籤數 24→22、導覽列 LP 345→145 與戰利品入口。驗證：`PLAYWRIGHT_HEADLESS=true ... playwright test --project=chromium` 通過 6 tests。
- [x] 2026-07-02：完成戰利品與出貨申請 E2E MVP（12 驗收測試情境）。`frontend/e2e/vue.spec.js` 新增 `/account/prizes` 相關 mock（戰利品、地址、免運券、通知、出貨紀錄），驗證會員可查看戰利品與狀態篩選；另新增出貨申請流程，勾選 2 件戰利品、套用 `SHIPFREE` 免運券、建立 #8201 出貨申請、刷新戰利品狀態與出貨紀錄，並進入 `/account/shipments` 確認品項。驗證：`PLAYWRIGHT_HEADLESS=true ... playwright test --project=chromium` 通過 8 tests。
- [x] 2026-07-03：完成後台建立賞池 E2E MVP（12 驗收測試情境）。`frontend/e2e/vue.spec.js` 新增 admin user 與 `/api/admin/campaigns**` 可變 mock，驗證管理員登入後台、建立賞池主檔、建立普通獎項、生成 Ticket、Dry Run 通過、發布上架、列表顯示開抽中與敏感欄位鎖定。驗證：`PLAYWRIGHT_HEADLESS=true ... playwright test --project=chromium` 通過 9 tests；前端 lint、Vitest、build 全綠；後端 `./mvnw test` 205 tests 全綠。
- [x] 2026-07-03：完成規格相容 API 與前端路由別名收尾。後端新增 `/api/me`、`/api/wallet`、`/api/wallet/ledger`、`/api/payments/top-up`、`/api/payments/mock/complete`、`/api/addresses` CRUD、`GET /api/campaigns/{slug}/probabilities`、`GET /api/draw-orders/{id}`、`GET /api/draw-results/{id}` 等相容端點，抽賞 detail 端點依登入會員隔離資料；前端新增 `/kuji`、`/kuji/:slug/draw`、`/result/:drawId`、`/account/top-up`、`/admin/settings` 相容路由。新增 `CompatibilityApiTests` 覆蓋錢包/付款/地址/機率/抽賞 detail 相容路徑與跨會員不可讀；同步更新 `docs/api.md`、`docs/testing.md` 與本計劃書。
- [x] 2026-07-03：完成計劃書狀態校正與本機可驗證項目收尾。將已由現有 API、頁面、schema 與測試覆蓋的 MVP 成功標準、角色能力、資料模型、核心流程、驗收情境、安全清單、Milestone 與 DoD 補勾；保留真實金流、正式部署、法遵審閱、素材授權、SOP、正式環境與供應商選型等外部決策項目。驗證：後端 `./mvnw test` 209 tests 全綠、`./mvnw package` 成功、前端 lint/Vitest/build 全綠、Playwright Chromium E2E 9 tests 全綠。
- [x] 2026-07-03：完成 VIP 專屬優惠券、後台角色管理、高風險操作審核中心與賞池修正版本流程。新增 `coupons.vip_tier` 與 VIP gating，會員可見券、贈點券領取、抽賞折扣券、出貨免運券皆依 VIP 等級檢查；後台優惠券可設定銀卡/金卡/白金限制。新增超級管理員角色調整 API 與 `/admin/users` 角色選單。新增 `admin_approval_requests` 審核佇列、`/api/admin/approval-requests` API、`/admin/approvals` 審核中心，以及點數調整/退款/補償的送審入口；超級管理員核准後才執行既有調整、退款或補償服務，駁回不執行。新增 `POST /api/admin/campaigns/:id/correction-version`，停用原賞池並複製主檔/獎項到 DRAFT 修正版且留下 audit。同步補 `/admin/prizes` route alias、SMTP 與營運文件狀態。
- [x] 2026-07-03：完成紅利點有效期限標示 MVP。`GET /api/account/wallet` 的 wallet summary 新增 `bonusPointExpiryDays` 與 `bonusPointExpiryLabel`，預設 `LUCKYBOX_BONUS_POINT_EXPIRY_DAYS=365` 並可由環境變數調整；前台錢包頁在贈點餘額旁顯示「紅利點自入帳日起 N 天有效」政策；`WalletApiTests` 補 API 斷言，`docs/api.md` 同步更新。
- [x] 2026-07-03：完成本輪收尾驗證。後端 `./mvnw test` 221 tests 全綠，普通 `./mvnw -DskipTests package` 與 `./mvnw -Psingle-package -DskipTests package` 均成功，single-package jar 內含 `static/index.html` 與前端主 CSS/JS；前端 lint、Vitest、build 全綠；Playwright Chromium E2E 新增 desktop/mobile 首頁截圖 smoke test 後 10 tests 全綠。計畫書中本機可完成的功能項已收斂，剩餘未勾為真實金流供應商、正式部署/環境、法遵審閱、素材授權、公司/物流/SOP 與正式 launch checklist 等外部決策或上線作業。
- [x] 2026-07-03：完成單一部署包打包路徑。後端 `pom.xml` 新增 Maven `single-package` profile，可在 `frontend/dist` 已產出後，將 Vue build output 複製進 Spring Boot static resources，再產出包含前端靜態檔的 jar；`README.md`、`docs/operations.md`、`docs/architecture.md` 同步記錄 single-package 與 split deployment 兩種部署形態。
- [x] 2026-07-03：完成監控與產品事件追蹤項目收斂。新增 Spring Boot Actuator，公開 `/actuator/health` 供 uptime monitor，`/actuator/metrics` 維持需認證；既有 `visitor_sessions` / `/api/analytics/visit` / Dashboard product metrics 補勾為自建事件表追蹤。`HealthControllerTests` 補 actuator health 與 metrics 權限測試；`docs/operations.md` 更新監控與部署目標說明。
- [x] 2026-07-03：完成賞池合規、素材商用確認、官方授權確認、年齡限制揭露與 GK DB 約束收尾。新增 Flyway V17 合規欄位與 V18 source_type CHECK 相容 `GK`（含 non-transactional Flyway script config），後台建立/編輯賞池可維護 `commercialUseConfirmed`、`officialLicenseConfirmed`、`rightsNotice`、`ageRestricted`、`minimumAge`、`ageVerificationNote`；發布與 dry-run 前檢查商用素材、官方授權與年齡限制完整性，前台賞池詳情顯示來源/授權與年齡資格。新增/更新 AdminCampaign 與 public Campaign API 測試、E2E mock 與文件。驗證：後端 `./mvnw test` 226 tests 全綠；前端 lint、Vitest、build 全綠；Playwright Chromium E2E 10 tests 全綠；`./mvnw clean -DskipTests package` 與 `./mvnw -Psingle-package -DskipTests package` 成功，single-package jar 內含 `static/index.html` 與前端 assets。
- [x] 2026-07-04：完成 Phase 14 上線 readiness 收斂。本機可交付項目已補齊：第一個真實金流工程預設 ECPay first / NewebPay fallback；新增 `docs/launch-readiness.md`、客服/出貨/緊急下架/退款補償 SOP；新增 `scripts/check-launch-readiness.sh`、`scripts/backup-luckybox.sh`、`scripts/smoke-test.sh`；`.env.example` 補 `.env.production` 註解範本；`README.md`、`docs/operations.md` 與計畫書同步更新。正式法務審閱、正式金流開通、正式商品素材授權、正式部署、小流量真實測試與上線公告仍保留 Milestone 5 外部簽核。
- [x] 2026-07-04：完成 ECPay 真實金流 adapter MVP。後端新增 ECPay AioCheckOut checkout 產生器與 `CheckMacValue` SHA256 驗證；`POST /api/account/payment-orders/:id/ecpay-checkout` 回傳 ECPay hidden form fields，前端錢包頁收到 `provider=ECPAY` 後自動 POST 到 ECPay；`POST /api/webhooks/payment/ecpay` 接收 form-urlencoded ReturnURL callback，驗 MerchantID / CheckMacValue、以 TradeNo 去重、比對金額、預設忽略 SimulatePaid=1、合法 paid callback 透過既有 WalletService 入點。prod profile 預設 provider=ECPAY、mock payment 關閉。新增 `EcpayChecksumTests` 與 `EcpayPaymentApiTests` 覆蓋 checkout fields、callback 成功入點、重送冪等、錯誤檢查碼不記錄、金額不符不入點、模擬付款不入點；文件與 readiness gate 同步更新。正式 merchant account 開通、production credentials 與 ECPay dashboard callback 測試仍屬 Milestone 5。
- [x] 2026-07-04：完成剩餘外部上線項目的工程 gate 化。新增 `docs/launch-signoff-register.md`，將公司/商業登記、法律顧問、法律意見回填、商品來源、品牌文案、素材授權、發票政策、出貨 owner、物流商、超商取貨、海外配送、預購策略、正式部署 owner、rollback owner、正式 smoke test 與小流量測試全部對應到明確 env gate；`scripts/check-launch-readiness.sh` 與 `.env.example` 同步加入檢查，`LaunchReadinessArtifactsTests` 鎖定文件/腳本/env 範本不可遺漏；前台許願牆範例移除未核准品牌詞，改用中性「限定週邊抽賞」。這些項目的實際商務/法務/部署簽核仍不得由程式碼代替。
- [x] 2026-07-04：完成 Phase 2 金流擴充的本機可交付收斂。ECPay adapter 新增可選 `CreditInstallment` 欄位，支援 `3,6,12,18,24` 或 `30N` 分期設定，checkout 前驗證必須搭配 `ChoosePayment=Credit`，簽章會納入分期欄位；`scripts/check-launch-readiness.sh` 會在啟用 `LUCKYBOX_PAYMENT_ECPAY_CREDIT_INSTALLMENT` 時要求 `LUCKYBOX_PAYMENT_ECPAY_INSTALLMENT_CONTRACT_APPROVED=true`。新增 `EcpayInstallmentPaymentApiTests` 與 `docs/payment-provider-expansion.md`，將 LINE Pay / 街口的 request/confirm/result callback 範圍與外部 merchant account/key 需求列清楚，未完成 adapter 前不允許成為 production provider。
- [x] 2026-07-04：完成 LINE Pay / 街口支付 adapter。後端新增 LINE Pay Online API v3 request/confirm HTTP client、HMAC header 簽章、checkout redirect response、confirm/cancel redirect endpoint 與 transactionId 冪等；新增街口 Entry API client、api-key/digest 簽章、confirm_url 與 result_url callback，依 tradeNo 去重、驗訂單金額/status 後入點。前端錢包與會員中心支援 LINEPAY/JKOPAY redirect checkout；readiness gate 與 `.env.example` 新增 LINE Pay / 街口正式 credential 與 callback-tested 旗標。新增 `LinePayPaymentApiTests`、`JkoPayPaymentApiTests`，文件同步更新。正式 merchant account、provider dashboard callback 測試與合約核准仍屬 Milestone 5 外部 gate。
- [x] 2026-07-05：完成剩餘外部 gate 的 evidence handoff 收斂。新增 `scripts/generate-launch-evidence-template.sh`，可由 `.env.production` 產生 redacted launch evidence packet，整理公司/法務/素材授權/金流/物流/部署/smoke test/小流量/公告等 owner、approver、evidence link 與 gate；`docs/launch-signoff-register.md` 補齊 LINE Pay、街口與 NewebPay fallback 的正式 credentials/callback evidence 要求；`docs/launch-readiness.md`、`docs/operations.md`、`README.md` 與 `LaunchReadinessArtifactsTests` 同步鎖定新流程。剩餘未勾項仍為真實法遵審閱、正式商務/金流/物流/部署與上線測試簽核，不得由程式碼代簽。
- [x] 2026-07-05：校正前台公平性說明頁至現行完成狀態。`/fairness` 不再把 seedHash / revealedSeed / random_proof / audit summary 描述為未完成項，改為「可驗證能力」並說明 HASH_COMMIT_REVEAL、SHA-256 seed commitment、HMAC-SHA256 proof、公開 fairness API 與一致性檢查；Playwright E2E 新增公開公平性頁斷言，避免頁面退回舊文案。
- [x] 2026-07-05：完成出貨 Email/SMTP 通知收斂。`NotificationService` 在出貨狀態轉為 SHIPPED / DELIVERED / RETURNED / EXCHANGED 時，先建立既有站內通知，再以 `NotificationRepository.findUserEmail` 讀取未遮罩會員 email 並透過 `EmailService` 寄送出貨狀態信；若唯一索引判定站內通知已存在則不重寄，dev/test 未啟用 SMTP 時仍退回 log fallback。新增 `NotificationServiceTest` 覆蓋實寄收件人、base-url 去尾斜線、狀態未變不寄、通知已存在不寄、缺 email 不寄但保留站內通知。
- [x] 2026-07-05：完成 rate limit 反向代理來源 IP 支援。`RateLimitInterceptor` 新增 `luckybox.ratelimit.trust-forwarded-headers` / `LUCKYBOX_RATELIMIT_TRUST_FORWARDED_HEADERS`，預設 false 時忽略客戶端可偽造 header；開啟後以 `X-Forwarded-For` 第一個有效 client IP 或 RFC 7239 `Forwarded: for=` 作為 auth/webhook/draw fallback IP bucket，並處理 quoted/bracketed IPv6 與 IPv4:port。新增 `RateLimitInterceptorTest` 覆蓋預設忽略、信任 XFF 分桶、Forwarded IPv6 與 malformed fallback；`.env.example`、`docs/operations.md`、`docs/launch-readiness.md`、`docs/testing.md` 同步。
- [x] 2026-07-05：完成 Admin 2FA QR 影像收斂。新增 `TotpQrCodeService` 以 ZXing 產生 PNG QR code data URI，`POST /api/admin/2fa/setup` 回傳 `secret`、`otpauthUri` 與 `qrCodeDataUri`，後台安全設定頁可直接顯示 192px QR 供驗證器掃描，仍保留手動金鑰與 otpauth URI。新增 `TotpQrCodeServiceTest` 與 `Admin2faApiTests` QR data URI 斷言，API/測試/營運文件同步。
- [x] 2026-07-05：完成後台獨立獎品庫、完整 ticket 清單與系統設定頁收斂。新增 `GET /api/admin/prizes` 跨賞池獎品庫、`GET /api/admin/campaigns/:id/tickets` 完整 ticket 清單與 `GET /api/admin/settings` 非敏感 runtime 設定摘要；前端 `/admin/prizes`、`/admin/settings` 不再是 route alias，`/admin/campaigns/:id/tickets` 顯示序號、狀態、獎項、draw id 與遮罩抽出者 email。新增 `AdminCampaignApiTests` 與 `AdminSettingsApiTests` 覆蓋 admin-only、篩選、遮罩與不外洩 secret。
- [x] 2026-07-05：完成每日簽到連續天數階梯加碼收斂。`GET/POST /api/account/check-in` 現在回傳基礎獎勵、連續簽到加碼、下一階門檻與距離天數；實際簽到會把基礎 + 加碼總額寫入 `daily_check_ins.reward_amount`、`CHECK_IN_BONUS` 流水與 audit payload。新增 `LUCKYBOX_DAILY_CHECK_IN_STREAK_BONUSES`（預設 `3:30,7:80,14:150,30:500`）、後台系統設定摘要顯示、會員中心加碼提示，並補 `CheckInApiTests` 第三天加碼情境與 API/測試/營運文件。
- [x] 2026-07-05：完成 provider CSV 對帳檔匯入比對。新增 `backend/scripts/reconcile-provider-payments.py`，可用 `--provider`、CSV 檔與欄位對應，將 ECPay / LINE Pay / 街口等 provider export 與本機 `payment_orders` 比對，偵測 provider row 找不到訂單、金額不一致、狀態不一致、本機終態訂單未出現在 provider 檔、重複交易列與 malformed row；支援 `--strict` exit 2。`PaymentReconciliationScriptTests` 補 provider clean/dirty CSV 情境，README 與 API/測試/營運/launch 文件同步。
- [x] 2026-07-05：完成後台抽賞單筆詳情收斂。新增 admin-only `GET /api/admin/draw-orders/:id`，回傳遮罩會員資料、冪等鍵、原始/折抵/實際扣點、優惠碼、逐筆抽賞結果、ticket serial、最後賞旗標、random proof 與 DrawOrder 關聯點數流水；`/admin/draws` 可展開單筆詳情。新增 `AdminDrawOrderApiTests` 與 `AdminAccessSecurityTests` 覆蓋詳情、遮罩、403 與 404，README/API/測試/計劃書同步。
- [x] 2026-07-05：完成開發期腳手架與狀態頁收斂。`/status` 由靜態初始化頁改為實際呼叫 `/api/health` 的系統狀態頁，顯示前端載入、後端 API、最後檢查時間與離線錯誤，並補手機響應式排列與 `StatusView` unit tests；移除未使用的 Vite starter 元件與 HelloWorld 樣板測試；前台/後台可見文案移除 Phase/MVP 開發期措辭，`docs/launch-signoff-register.md` 的 Legal Feedback Log 改為明確 pending external review 列。驗證：前端 unit 3 tests、lint、build 全綠；後端完整 `./mvnw test` 260 tests 全綠。
- [x] 2026-07-05：完成全專案安全優化收斂。前端新增共用 `apiClient`，集中 `VITE_API_BASE_URL`、timeout、credentials 與 query param compact 行為，30+ 個 service 改為 endpoint wrapper，並新增 `apiClient` unit tests；後端新增 `SecurityPrincipals` 集中 authenticated/admin/super-admin principal 判斷與錯誤碼，後台 service 的重複 `SecurityContextHolder` 檢查改委派到共用 helper，並補 `SecurityPrincipalsTest`。同步清除 `.DS_Store` source tree 雜訊與測試文件。
