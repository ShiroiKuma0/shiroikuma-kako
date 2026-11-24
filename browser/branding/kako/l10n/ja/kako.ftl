# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at http://mozilla.org/MPL/2.0/.

### 白い熊 火狐 -- every string the fork adds on the desktop.
###
### Served by KakoTheme.sys.mjs from chrome://branding/content/l10n/{locale}/.
### The UI page reads both languages from a registry of its own; the other
### documents get English from the application's registry, and Japanese where
### a Japanese language pack is installed. The two files must hold exactly the
### same message ids.


## Settings, Privacy: extensions on restricted sites

kako-extensions-group =
    .label = 拡張機能
    .description = 一部のサイトでは通常、すべての拡張機能が一律にブロックされます。どの拡張機能でも、どのようにインストールしたものでも例外はありません。
kako-ignore-restricted-domains =
    .label = 制限されたサイトでも拡張機能を実行する
    .description = addons.mozilla.org、support.mozilla.org、アカウントと同期のサーバーでも、ほかのサイトと同じように拡張機能が動作します。

## The extensions button’s context menu

# “Install Add-on From File…”, first entry on the extensions button’s context
# menu. Wording matches about:addons’ own menu item (addon-install-from-file)
# so the two read as one command in two places.
kako-toolbar-context-menu-install-addon-from-file =
    .label = ファイルからアドオンをインストール…
    .accesskey = I
# Title of the file picker that entry opens.
kako-toolbar-context-menu-install-addon-from-file-dialog-title = インストールするアドオンを選択してください
# Name of the file-type filter in that picker.
kako-toolbar-context-menu-install-addon-from-file-filter-name = アドオン

## Add-ons Manager

# The installed version, shown under every add-on in the list instead of only
# inside the expanded card.
# Variables:
#   $version (string) - The version of the installed add-on.
kako-addon-card-version = バージョン { $version }

## Entry points of the UI page

# The application menu, next to Settings.
kako-appmenu-ui =
    .label = 白い熊 火狐 UI
# The pill at the top of Settings.
kako-prefs-ui-link = 白い熊 火狐 UI
kako-prefs-ui-link-desc = ブラウザー自体の画面の色を、項目ごとに設定します。

## The UI page

kako-ui-title = 白い熊 火狐 UI
kako-ui-app-settings = アプリの設定
kako-ui-app-settings-desc = 検索、プライバシー、アドオンなど、ブラウザー本体の設定です。
kako-ui-index =
    .aria-label = このページのセクション
kako-ui-master-switch = 黒黄テーマを使う
kako-ui-master-switch-desc = オフにするとテーマ本来の外観になり、下の色は使われません。
kako-ui-foundation-note = ほかの色は、個別に設定するまでここの色に従います。
kako-ui-menu-icons = アイコン（項目の働き別）
kako-ui-section-icons = このページのセクションアイコン
kako-ui-role-changed =
    .title = 既定から変更されています
kako-ui-role-pick =
    .title = 色を選ぶ
kako-ui-role-hex =
    .title = 色の値：#RRGGBB、不透明度を含める場合は #RRGGBBAA
    .aria-label = 色の値
kako-ui-role-reset =
    .title = 既定に戻す
    .aria-label = 既定に戻す
kako-ui-reset-order = セクションの順序を元に戻す
kako-ui-reset-order-desc = このページのセクションを最初の順序に戻します。
kako-ui-reset-colors = すべての色を元に戻す
kako-ui-reset-colors-desc = すべての色を既定値に戻します。ページの並びは変わりません。
kako-ui-reset-colors-question = すべての色を既定値に戻しますか？
kako-ui-reset-colors-confirm = 元に戻す
kako-ui-cancel = キャンセル

## Sections of the UI page

kako-section-foundation = 基本
kako-section-text = テキスト
kako-section-buttons = ボタン
kako-section-toolbar = ツールバーとアドレスバー
kako-section-menu = メニュー
kako-section-tabs = タブ
kako-section-switches = スイッチ
kako-section-dialogs = ダイアログとパネル
kako-section-marks = チェックボックス・ラジオボタン・スライダー
kako-section-lists = リストとカード
kako-section-fields = テキスト入力欄とドロップダウン
kako-section-window = ウィンドウ
kako-section-pages = 設定ページ
kako-section-newtab = 新しいタブ
kako-section-toasts = 通知バー・バッジ・ツールチップ
kako-section-maintenance = メンテナンス

## Colour roles

kako-role-background = 背景
kako-role-text = テキスト
kako-role-text-secondary = 補助テキスト
kako-role-accent = アクセント
kako-role-border = 枠線
kako-role-text-on-accent = アクセント上のテキスト
kako-role-focus-outline = フォーカスの枠
kako-role-text-heading = 見出し
kako-role-text-link = リンク
kako-role-text-disabled = 無効な項目の文字
kako-role-text-error = エラーの文字
kako-role-text-cursor = カーソル
kako-role-text-selection = 選択範囲
kako-role-text-selection-text = 選択した文字
kako-role-button-background = ボタンの背景
kako-role-button-text = ボタンの文字
kako-role-button-border = ボタンの枠
kako-role-button-main-text = 主ボタンの文字
kako-role-button-main-border = 主ボタンの枠
kako-role-button-delete = 削除ボタン
kako-role-button-ripple = ポインターを重ねたとき・押したときの強調
kako-role-icon-button = アイコンボタン
kako-role-toolbar-fill = ツールバーの背景
kako-role-toolbar-label = ツールバーの文字
kako-role-toolbar-icon = ツールバーのアイコン
kako-role-toolbar-top-border = ツールバーの境界線
kako-role-toolbar-separator = ボタン間の区切り線
kako-role-toolbar-button-hover = ポインターを重ねたツールバーボタン
kako-role-toolbar-button-active = 押したツールバーボタン
kako-role-addressbar-fill = アドレスバーの背景
kako-role-addressbar-border = アドレスバーの枠
kako-role-addressbar-border-focused = 入力中のアドレスバーの枠
kako-role-toolbar-text = アドレスの文字
kako-role-toolbar-hint = アドレスバーのヒント
kako-role-urlbar-highlight = 選択中の候補の背景
kako-role-urlbar-highlight-text = 選択中の候補の文字
kako-role-menu-background = メニューの背景
kako-role-menu-border = メニューの枠
kako-role-menu-row-frame = メニュー項目の枠
kako-role-menu-label = メニュー項目の文字
kako-role-menu-header = メニューの見出し行
kako-role-menu-divider = メニューの区切り線
kako-role-menu-highlight = ポインターを重ねたメニュー項目の背景
kako-role-menu-icon-connect = アイコン：接続・再読み込み・同期
kako-role-menu-icon-message = アイコン：書く・編集・翻訳
kako-role-menu-icon-contact = アイコン：アカウント・ログイン情報
kako-role-menu-icon-share = アイコン：コピー・共有・保存
kako-role-menu-icon-view = アイコン：表示・開く・設定
kako-role-menu-icon-block = アイコン：ブロック・プライベート・ロック
kako-role-menu-delete = 削除する項目
kako-role-tab-selected = 選択中のタブの背景
kako-role-tab-active-border = 選択中のタブの線
kako-role-tab-text = 選択中のタブのタイトル
kako-role-tab-text-inactive = その他のタブのタイトル
kako-role-tab-close = タブを閉じるボタン
kako-role-tab-loading = タブの読み込み表示
kako-role-switch-on = オン：輪郭
kako-role-switch-on-dot = オン：丸
kako-role-switch-off = オフ：輪郭
kako-role-switch-off-dot = オフ：丸
kako-role-dialog-background = ダイアログの背景
kako-role-dialog-border = ダイアログの枠
kako-role-dialog-title = ダイアログのタイトル
kako-role-dialog-text = ダイアログの本文
kako-role-panel-background = パネルの背景
kako-role-panel-border = パネルの枠
kako-role-panel-text = パネルの文字
kako-role-scrim = ダイアログ背後の暗幕
kako-role-check-on = チェックボックス：オン
kako-role-check-off = チェックボックス：オフ
kako-role-check-mark = チェックボックス：チェック印
kako-role-radio-on = ラジオボタン：選択
kako-role-radio-off = ラジオボタン：未選択
kako-role-slider-active = スライダー：設定済みの部分とつまみ
kako-role-slider-inactive = スライダー：残りの部分
kako-role-progress = 進行状況の表示
kako-role-list-text = 行のタイトル
kako-role-list-divider = リストの区切り線
kako-role-list-selected = 選択中の行の背景
kako-role-list-selected-text = 選択中の行の文字
kako-role-list-hover = ポインターを重ねた行の背景
kako-role-card-background = カードの背景
kako-role-card-border = カードの枠
kako-role-field-text = 入力した文字
kako-role-field-hint = ヒントとラベル
kako-role-field-ground = 入力欄の背景
kako-role-field-frame = 入力欄の枠
kako-role-field-frame-focused = 入力中の枠
kako-role-window-frame = タイトルバーとタブバーの背景
kako-role-window-frame-inactive = 前面にないウィンドウのタイトルバーの背景
kako-role-window-ring = ウィンドウの縁取り
kako-role-window-ring-inactive = 前面にないウィンドウの縁取り
kako-role-sidebar-background = サイドバーの背景
kako-role-sidebar-text = サイドバーの文字
kako-role-sidebar-border = サイドバーの境界線
kako-role-scrollbar-thumb = スクロールバーのつまみ
kako-role-scrollbar-track = スクロールバーの溝
kako-role-note = 説明と注記
kako-role-page-heading = セクションの見出し
kako-role-page-divider = セクションの区切り線
kako-role-page-rule = 説明付きの行の左の線
kako-role-chip-frame = 目次チップの枠
kako-role-chip-text = 目次チップの文字
kako-role-section-base = セクションアイコン：基本
kako-role-section-controls = セクションアイコン：操作部品
kako-role-section-words = セクションアイコン：文字
kako-role-section-frame = セクションアイコン：枠組み
kako-role-section-reach = セクションアイコン：受け渡し
kako-role-section-warning = セクションアイコン：警告
kako-role-section-flash = セクションアイコン：一時表示
kako-role-newtab-background = 新しいタブの背景
kako-role-newtab-text = 新しいタブの文字
kako-role-newtab-secondary-text = 新しいタブの補助テキスト
kako-role-newtab-wordmark = ロゴ横の名前
kako-role-newtab-icon = 新しいタブのアイコン
kako-role-newtab-card-background = タイルとカードの背景
kako-role-newtab-card-border = タイルとカードの枠
kako-role-newtab-search-ground = 検索ボックスの背景
kako-role-newtab-search-border = 検索ボックスの枠
kako-role-toast-background = 通知バーの背景
kako-role-toast-border = 通知バーの枠
kako-role-toast-text = 通知バーの文字
kako-role-tooltip-background = ツールチップの背景
kako-role-tooltip-border = ツールチップの枠
kako-role-tooltip-text = ツールチップの文字
kako-role-badge-frame = バッジの枠
kako-role-badge-text = バッジの文字
kako-role-badge-filled = 塗りつぶしバッジの背景
kako-role-badge-filled-text = 塗りつぶしバッジの文字
