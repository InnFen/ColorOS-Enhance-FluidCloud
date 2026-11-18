# 流體雲增強（ColorOS 增強精簡版）

基於 Howard 的 [ColorOS-Enhance](https://github.com/Howard-Tse/ColorOS-Enhance) 1.31.1（MIT License），只保留「流體雲增強」。

## 改了什麼

- **只剩流體雲**：LSPosed 入口只載入 `dev.lsposed.fluidcloud.HookEntry`，作用域只剩「系統界面」和「系統框架」。狀態欄電池、狀態欄圖標、控制中心、全景息屏、SIM PIN、安裝器替換、系統設定內嵌入口的 hook 都不再載入，設定 App 也只顯示流體雲。電池、息屏、控制中心的原始碼已刪除；其餘幾個被設定 App 共用的舊類別還留在原始碼裡，但不會執行。
- **內容上移，只留攝像頭間距**：原版不是寫死的 dp：卡片頂部移到小膠囊頂部（攝像頭位置），再把「膠囊頂部到系統原本卡片位置」這整段用空白填滿，內容保持原位。現在改成內容整體上移：量出卡片內容最上方第一個可見元素，把它放到攝像頭下緣再往下「流體雲 → 展開卡片 → 內容與攝像頭間距」（0–24dp，預設 2dp）的位置，原本的空白不再保留（不會比原版更低）。
  - 內容自己的上邊距（容器 padding、子元素 margin）不夠減時也會一路往下扣，直到第一個可見元素到位。
- **卡片上移到攝像頭**：新增「卡片上移到攝像頭」（預設開，-28dp），意思和主題的 `offset_between_status_bar_and_card` 一樣：讀出韌體原本的值，按差值把卡片往上移。關掉就按小膠囊頂部對齐。別和主題的同名設定一起用。
  - LSPosed 日誌搜 `Card top offset` 看原生值和實際上移的 px。
  - LSPosed 日誌可搜 `Camera card avoidance`（原始空白、套用的 header、目標位置）、`Instant camera card`（即時卡片的原生邊距、內容偏移、實際 padding）、`Camera card content lead`。
- **深色背景描邊補到避讓區**：卡片的淺色描邊（前景 GradientDrawable，或 SDK 的 `cardStroke` 層）原本只畫在原生內容範圍，現在一併延伸到上方避讓區。
- 獨立包名 `io.github.howard_tse.colorosenhance.fluidcloud`，可以和原版並存；拿掉了自動檢查更新與網路權限。

主要改動：`CameraAvoidance.java`、`NativeCameraCard.java`（`plan()`、`extendStrokes()`）、`HookEntry.java`、`SettingsCatalog.java`。

## 編譯

用 GitHub Actions（不用自己裝 Android SDK）：
1. 把這份原始碼推到你自己的 GitHub 倉庫。
2. Actions → 「Build fluid cloud APK」→ Run workflow。
3. 跑完在 Artifacts 下載 `fluidcloud-debug-apk`。

每次跑都會產生新的簽名；想讓之後的版本能直接覆蓋安裝，可以把固定的 PKCS12 金鑰 base64 後放進倉庫 Secrets `DEBUG_KEYSTORE_B64` 和 `DEBUG_KEYSTORE_PASSWORD`（alias 要是 `coloros-enhance-debug`）。

本機編譯：JDK 21 + Android SDK 37 + Build Tools 37.0.0，建立 `.local/debug.p12` 和 `.local/debug.properties` 後執行 `./gradlew :app:assembleDebug`。

## 安裝

1. 在 LSPosed 裡停用原版 ColorOS 增強（或至少關掉原版的流體雲），兩個一起改流體雲會衝突。
2. 安裝本 APK，在 LSPosed 啟用並勾選「系統界面」和「系統框架」。
3. 打開 App 一次，重新設定流體雲（設定不會從原版帶過來），然後完整重啟手機。

單元測試沒有跟著更新，`assembleDebug` 不會跑測試。
