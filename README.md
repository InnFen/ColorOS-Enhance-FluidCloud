# Pixel 鎖屏（ColorOS LSPosed 模組）

把 ColorOS 鎖屏改成 Pixel 的樣子：

- **雙行大時鐘**：小時在上、分鐘在下，粗體字型（Google Sans Flex），數字像 Pixel 一樣緊密重疊，重疊處留一圈鏤空間隙，兩種深淺交錯，顏色跟隨系統取色（Monet）。日期移到時鐘下方（可關）。
- **自動讓位**：鎖屏有通知或流體雲卡片時，時鐘縮小或改成一行；連一行都放不下就暫時換回原本的時鐘。
- **息屏顯示**：息屏時也顯示 Pixel 時鐘（較細的字重）。
- **快捷按鈕取色色塊**：左下、右下角按鈕（手電筒、相機）換成跟隨系統色的淺色實心圓（深淺可調），圖示依底色自動變深或變淺；按下時會變成圓角方塊。不碰控制中心或其他磁貼。
- 不碰指紋圖示，也**不碰解鎖密碼盤**（可以和 ColorOS-SplashScreen-Evolution 的 M3E 密碼盤一起用）。

## 需求

- Root + LSPosed（支援 libxposed API 102，和 COS遮罩進化、流體雲增強同一種）
- 作用域勾 **系統介面（com.android.systemui）**，要息屏時鐘再勾 **息屏顯示（com.oplus.aod）**
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

原時鐘不會被移除，只是設成透明，模組在同一位置畫 Pixel 時鐘，每一幀同步原時鐘的透明度、縮放和位置，所以 ColorOS 的解鎖、下拉通知等動畫照樣會作用在新時鐘上。

## 排查

LSPosed 日誌裡搜尋 `PixelLockscreen`：

- `Clock bound: ...` 原時鐘的類名、id、位置、字號，以及新時鐘的字號和顏色
- `Reserved clock height ...` 為雙行時鐘預留的高度
- `Shortcuts: left=... right=...` 找到的兩個按鈕
- `No lock screen clock recognised ...` 沒找到時鐘

時鐘沒變、位置不對或按鈕沒變色：在模組設定打開「輸出鎖屏結構到 LSPosed 日誌」，鎖屏亮著等 20 秒，把 `[dump 1] [dump 2] ...` 那幾段日誌拿去排查。日誌只記介面結構，通知等文字只記字數。用完把開關關掉。

## 授權

GPL-3.0。時鐘字型 Google Sans Flex 採 SIL Open Font License 1.1（見 `app/src/main/assets/fonts/OFL.txt`）。詳見 NOTICE.md。
