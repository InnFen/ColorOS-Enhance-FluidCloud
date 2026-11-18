# Pixel 鎖屏（ColorOS LSPosed 模組）

把 ColorOS 鎖屏改成 Pixel 的樣子：

- **雙行大時鐘**：小時在上、分鐘在下，粗體字型（Google Sans Flex），數字像 Pixel 一樣緊密重疊，重疊處留一圈鏤空間隙，兩種深淺交錯，顏色跟隨系統取色（Monet）。日期移到時鐘下方（可關）。
- **直接替換系統時鐘**：不另外疊一個時鐘，而是讓 ColorOS 自己的時鐘元件畫成 Pixel 樣式，所以系統的位置、縮放、解鎖和息屏動畫都照常，畫面上永遠只有一個時鐘。
- **有通知時變一行**：鎖屏有通知或流體雲卡片時，時鐘縮小成一行「10:50」（同樣的重疊鏤空樣式）收到左上角，日期靠左放在下面；最後一則通知消失就回到置中的兩行大字。
- **雙行和一行之間變形動畫**：切換時每個數字從原位置移動、縮放到新位置，冒號淡入，日期跟著滑過去。
- **跟著深淺色模式和取色**：深色模式用動態取色的淺色調，淺色模式用深色調；切換模式、換桌布或系統取色後，下次亮屏前就跟著換，不用重啟。
- **息屏顏色漸變**：進全景息屏時顏色和字重一起漸變到淺色調。
- **流體雲卡片**：鎖屏通知堆疊裡的所有卡片（流體雲、音樂、自訂卡片、膠囊）都會讓時鐘收成一行。
- **淺色流體雲卡片**（可關）：淺色模式時，鎖屏和通知欄的流體雲卡片改用淺底深字，切深色模式自動變回黑色。狀態列膠囊（要和挖孔融在一起）和息屏時維持原本的黑色。卡片由 SystemUI 內載入的流體雲 SDK（來自智慧建議服務 com.oplus.pantanal.ums 的 SeedlingSdk.apk）繪製，所以作用域仍然只要系統介面。
- **息屏字重漸變**：進出全景息屏時，數字的粗細用可變字體連續變化，不再瞬間跳變。
- **配合系統全景息屏**：模組不畫自己的息屏。ColorOS 17 的鎖屏時鐘來自時鐘外掛（com.oplus.keyguard.clock.base / personality.clocks），全景息屏顯示的也是同一個時鐘，模組把它原地畫成 Pixel 樣式（息屏時較細字重），位置、動畫交給系統。
- **快捷按鈕取色色塊**：左下、右下角按鈕（手電筒、相機）換成跟隨系統色的淺色實心圓（深淺可調），圖示依底色自動變深或變淺；按下時會變成圓角方塊。不碰控制中心或其他磁貼。
- 不碰指紋圖示，也**不碰解鎖密碼盤**（可以和 ColorOS-SplashScreen-Evolution 的 M3E 密碼盤一起用）。

## 需求

- Root + LSPosed（支援 libxposed API 102，和 COS遮罩進化、流體雲增強同一種）
- 作用域只要勾 **系統介面（com.android.systemui）**（時鐘外掛和全景息屏都在系統介面裡執行）
- 針對 Android 14 以上的 ColorOS 設計，**尚未在實機上測試**

## 編譯

這個環境沒辦法直接出 APK，用 GitHub Actions 編：

1. 把整個資料夾推到你自己的 GitHub repo（私人也行）。
2. 打開 repo 的 **Actions** → **Build APK** → **Run workflow**（推送到 pixel-lockscreen 分支也會自動跑）。
3. 跑完後在該次執行的 **Artifacts** 下載 `pixel-lockscreen-apk`，解壓得到 APK。

每次編譯的版本號是 `0.2.<執行編號>-<commit>`，APK 檔名和 Artifact 名稱都帶版本號，模組設定頁頂端也會顯示。簽名金鑰會快取在 Actions 裡，之後的版本可以直接覆蓋安裝（快取 7 天沒用會被清掉，那時要先解除安裝一次）。想直接覆蓋安裝，就自己產一個 PKCS12 金鑰，在 repo 的 Settings → Secrets 加上 `DEBUG_KEYSTORE_B64`（金鑰檔 base64）和 `DEBUG_KEYSTORE_PASSWORD`，別名用 `pixellock`。

本機編譯：Android SDK platform 37 + build-tools 37.0.0、JDK 21，執行 `./gradlew :app:assembleDebug`。

## 安裝

1. 安裝 APK，在 LSPosed 啟用「Pixel 鎖屏」，作用域勾 **系統介面**。
2. 重啟系統介面（模組設定頁最下面的按鈕，需 root 授權）或重開機。
3. 打開模組設定頁可以調時鐘大小、上下位置、數字圓潤度等，改動即時生效。

**衝突提醒**：如果有其他模組也在改鎖屏時鐘或左右下角快捷按鈕，先關掉那部分再開本模組。COS遮罩進化的密碼盤不受影響，本模組在出現數字鍵盤時完全不動那個畫面。

## 原理（為什麼不需要知道 ColorOS 內部類名）

模組攔截系統介面新增視窗（`WindowManagerGlobal.addView`），在全螢幕視窗裡按「長相」找元素：

- 時鐘：文字大於 36dp、內容像 `17:13`、位於螢幕上半部的 TextView（也支援時、分拆成兩個 TextView，或類名含 clock 的自繪 View）。
- 日期：時鐘上下方、含「月／日／週」等字的較小文字。
- 快捷按鈕：螢幕下方 22%、左右兩側 30% 內、36–120dp 的近似正方形按鈕。

找到的時鐘不加第二個 View，而是攔截它自己的繪製（`View.draw`、`onDraw`、`dispatchDraw` 及其類別的覆寫）和量尺寸（`View.measure`），讓 ColorOS 的時鐘本身畫出 Pixel 時鐘。位置、透明度和動畫都還是 ColorOS 在管，所以不會有兩個時鐘搶位置。若 2.5 秒內這個 View 一次都沒經過這些繪製入口，會在日誌說明並改用疊加備用模式。

## 排查

LSPosed 日誌裡搜尋 `PixelLockscreen`：

- `Clock layout` 現在是一行還是兩行，以及原因（哪個通知或卡片）
- `Fluid cloud cards: hooked v6.e2.t()` 流體雲卡片引擎已接上；若寫 `light cards unavailable`，表示智慧建議服務更新後 SDK 內部名稱變了
- `Fluid cloud card (LockScreen): light` 某個位置的卡片改成淺色了
- `Clock hooks ready` 繪製攔截已裝好
- `Clock candidates in '...'` 每個像時鐘的 View（類名#id、字號、是否顯示、為何跳過）和最後選中的那個
- `In-place clock bound/updated` 綁定的時鐘；`In-place clock drawing (via ...)` 確認真的由模組在畫
- `In-place clock never drew ...` 原地替換沒生效，改用疊加模式，附類別繼承鏈
- `Shortcut styling active/paused: ...` 快捷按鈕為何在改或暫停
- `Shortcuts: left=... right=... | corner views: ...` 找到的按鈕，及左右下角每個候選為何被跳過

時鐘沒變、位置不對或按鈕沒變色：在模組設定打開「輸出鎖屏結構到 LSPosed 日誌」，鎖屏亮著等 20 秒，把 `[dump 1] [dump 2] ...` 那幾段日誌拿去排查。日誌只記介面結構，通知等文字只記字數。用完把開關關掉。

## 授權

GPL-3.0。時鐘字型 Google Sans Flex 採 SIL Open Font License 1.1（見 `app/src/main/assets/fonts/OFL.txt`）。詳見 NOTICE.md。
