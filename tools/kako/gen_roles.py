#!/usr/bin/env python3
"""Generate the colour-role table of 白い熊 火狐 UI.

One table, four outputs, so that a role cannot exist in one place and be missing in another:

  fenix/kako/KakoRoleTable.kt        the KakoSection and KakoSlot enums (key, section, label,
                                     colour resource, default)
  res/values/kako_roles.xml          one colour resource per role, holding the role's computed
                                     default; upstream's XML reaches a role through it, and
                                     KakoPalette lays the stored values over these at run time.
                                     In the default values/ only: a qualified copy would beat
                                     the run-time value.
  res/values/kako_role_strings.xml   the English labels
  res/values-ja/kako_role_strings.xml  the Japanese labels

Run from the repository root after editing the table:  tools/kako/gen_roles.py
"""
import os
import re
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
APP = os.path.join(ROOT, 'mobile/android/fenix/app/src/main')

BLACK = 0xFF000000
YELLOW = 0xFFFFFF00

# (id, English, Japanese) — the sections a role is set in, in the UI page's default order.
SECTIONS = [
    ('FOUNDATION', 'Foundation', '基本'),
    ('TEXT', 'Text', 'テキスト'),
    ('BUTTONS', 'Buttons', 'ボタン'),
    ('TOOLBAR', 'Search bar and toolbar', '検索バーとツールバー'),
    ('MENU', 'Menus', 'メニュー'),
    ('TABS', 'Tabs', 'タブ'),
    ('SWITCHES', 'Switches', 'スイッチ'),
    ('DIALOGS', 'Dialogs and sheets', 'ダイアログとシート'),
    ('MARKS', 'Check boxes, radio buttons and sliders', 'チェックボックス・ラジオボタン・スライダー'),
    ('LISTS', 'Lists and cards', 'リストとカード'),
    ('FIELDS', 'Text fields', 'テキスト入力欄'),
    ('BARS', 'Bars', 'バー'),
    ('PAGES', 'Settings pages', '設定ページ'),
    ('TOASTS', 'Toasts and snackbars', 'トーストとスナックバー'),
    ('SCREENS', 'Shared screen colours', '画面共通の色'),
]

# (name, section, English, Japanese, default)
# default: '#RRGGBB' | 'BLACK' | 'YELLOW' | ROLE | mix(A,B,f) = f of the way from A to B, opaque
#          | alpha(A,f) = A at opacity f.  A role may only name roles listed above it.
ROLES = [
    # Foundation — everything else inherits from these.
    ('BACKGROUND', 'FOUNDATION', 'Background', '背景', 'BLACK'),
    ('TEXT', 'FOUNDATION', 'Text', 'テキスト', 'YELLOW'),
    ('TEXT_SECONDARY', 'FOUNDATION', 'Secondary text', '補助テキスト', 'alpha(TEXT,0.6)'),
    ('ACCENT', 'FOUNDATION', 'Accent', 'アクセント', 'YELLOW'),
    ('BORDER', 'FOUNDATION', 'Borders', '枠線', 'ACCENT'),
    ('TEXT_ON_ACCENT', 'FOUNDATION', 'Text on accent', 'アクセント上のテキスト', 'BACKGROUND'),

    # Settings pages come early in the table because other defaults name the note colour.
    ('NOTE', 'PAGES', 'Notes and explanations', '説明と注記', 'mix(BACKGROUND,TEXT,0.784)'),
    ('PAGE_HEADING', 'PAGES', 'Section headings', 'セクションの見出し', 'ACCENT'),
    ('PAGE_DIVIDER', 'PAGES', 'Section dividers', 'セクションの区切り線', 'mix(BACKGROUND,ACCENT,0.7)'),
    ('PAGE_RULE', 'PAGES', 'Left rule of explained rows', '説明付きの行の左の線', 'NOTE'),
    ('CHIP_FRAME', 'PAGES', 'Index chip frame', '目次チップの枠', 'BORDER'),
    ('CHIP_TEXT', 'PAGES', 'Index chip label', '目次チップの文字', 'TEXT'),
    ('PREF_CATEGORY', 'PAGES', 'Category titles in Settings', '設定のカテゴリ名', 'ACCENT'),
    ('SECTION_BASE', 'PAGES', 'Section icon: the base', 'セクションアイコン：基本', 'ACCENT'),
    ('SECTION_CONTROLS', 'PAGES', 'Section icon: controls', 'セクションアイコン：操作部品', '#00A550'),
    ('SECTION_WORDS', 'PAGES', 'Section icon: words', 'セクションアイコン：文字', '#00B0FF'),
    ('SECTION_FRAME', 'PAGES', 'Section icon: the frame', 'セクションアイコン：枠組み', '#0066FF'),
    ('SECTION_REACH', 'PAGES', 'Section icon: reaching out', 'セクションアイコン：受け渡し', '#FF8000'),
    ('SECTION_WARNING', 'PAGES', 'Section icon: warning', 'セクションアイコン：警告', '#FF0000'),
    ('SECTION_FLASH', 'PAGES', 'Section icon: a brief flash', 'セクションアイコン：一時表示', '#FF3D7F'),

    # Text.
    ('TEXT_HEADING', 'TEXT', 'Headings', '見出し', 'ACCENT'),
    ('TEXT_LINK', 'TEXT', 'Links', 'リンク', 'ACCENT'),
    ('TEXT_DISABLED', 'TEXT', 'Disabled text', '無効な項目の文字', 'alpha(TEXT,0.38)'),
    ('TEXT_ERROR', 'TEXT', 'Error text', 'エラーの文字', '#FF0000'),
    ('TEXT_CURSOR', 'TEXT', 'Cursor', 'カーソル', 'ACCENT'),
    ('TEXT_SELECTION', 'TEXT', 'Text selection', '選択範囲', 'alpha(ACCENT,0.3)'),

    # Buttons.
    ('BUTTON_BACKGROUND', 'BUTTONS', 'Button ground', 'ボタンの背景', 'BACKGROUND'),
    ('BUTTON_TEXT', 'BUTTONS', 'Button label', 'ボタンの文字', 'ACCENT'),
    ('BUTTON_BORDER', 'BUTTONS', 'Button frame', 'ボタンの枠', 'BORDER'),
    ('BUTTON_MAIN_TEXT', 'BUTTONS', 'Main button label', '主ボタンの文字', 'BUTTON_TEXT'),
    ('BUTTON_MAIN_BORDER', 'BUTTONS', 'Main button frame', '主ボタンの枠', 'BUTTON_BORDER'),
    ('BUTTON_DELETE', 'BUTTONS', 'Deleting button', '削除ボタン', '#FF0000'),
    ('BUTTON_RIPPLE', 'BUTTONS', 'Press highlight', '押したときの強調', 'alpha(ACCENT,0.3)'),
    ('ICON_BUTTON', 'BUTTONS', 'Icon buttons', 'アイコンボタン', 'ACCENT'),
    ('FAB_GROUND', 'BUTTONS', 'Floating button ground', 'フローティングボタンの背景', 'BACKGROUND'),
    ('FAB_RING', 'BUTTONS', 'Floating button ring', 'フローティングボタンの枠', 'ACCENT'),
    ('FAB_ICON', 'BUTTONS', 'Floating button icon', 'フローティングボタンのアイコン', 'ACCENT'),

    # Search bar / toolbar.
    ('TOOLBAR_FILL', 'TOOLBAR', 'Toolbar ground', 'ツールバーの背景', 'BACKGROUND'),
    ('ADDRESSBAR_BORDER', 'TOOLBAR', 'Address bar frame', 'アドレスバーの枠', 'BORDER'),
    ('TOOLBAR_TOP_BORDER', 'TOOLBAR', 'Toolbar edge line', 'ツールバーの境界線', 'BORDER'),
    ('TOOLBAR_TEXT', 'TOOLBAR', 'Address text', 'アドレスの文字', 'TEXT'),
    ('TOOLBAR_HINT', 'TOOLBAR', 'Address bar hint', 'アドレスバーのヒント', 'TEXT_SECONDARY'),
    ('TOOLBAR_ICON', 'TOOLBAR', 'Toolbar icons', 'ツールバーのアイコン', 'ACCENT'),
    ('LOADING_BAR', 'TOOLBAR', 'Page loading bar', 'ページ読み込みバー', 'ACCENT'),

    # Menus.
    ('MENU_BACKGROUND', 'MENU', 'Menu ground', 'メニューの背景', 'BACKGROUND'),
    ('MENU_BORDER', 'MENU', 'Menu frame', 'メニューの枠', 'BORDER'),
    ('MENU_ROW_FRAME', 'MENU', 'Menu row frame', 'メニュー項目の枠', 'BORDER'),
    ('MENU_LABEL', 'MENU', 'Menu row label', 'メニュー項目の文字', 'TEXT'),
    ('MENU_HEADER', 'MENU', 'Menu header line', 'メニューの見出し行', 'NOTE'),
    ('MENU_DIVIDER', 'MENU', 'Menu group divider', 'メニューの区切り線', 'mix(BACKGROUND,ACCENT,0.7)'),
    ('MENU_ICON_CONNECT', 'MENU', 'Icon: connect, reload, sync', 'アイコン：接続・再読み込み・同期', '#00A550'),
    ('MENU_ICON_MESSAGE', 'MENU', 'Icon: write, edit, translate', 'アイコン：書く・編集・翻訳', '#0066FF'),
    ('MENU_ICON_CONTACT', 'MENU', 'Icon: account, logins', 'アイコン：アカウント・ログイン情報', '#00B0FF'),
    ('MENU_ICON_SHARE', 'MENU', 'Icon: copy, share, save', 'アイコン：コピー・共有・保存', '#FF8000'),
    ('MENU_ICON_VIEW', 'MENU', 'Icon: view, open, settings', 'アイコン：表示・開く・設定', 'ACCENT'),
    ('MENU_ICON_BLOCK', 'MENU', 'Icon: block, private, lock', 'アイコン：ブロック・プライベート・ロック', '#FF3D7F'),
    ('MENU_DELETE', 'MENU', 'Deleting row', '削除する項目', '#FF0000'),

    # Tab strip.
    ('TAB_SELECTED', 'TABS', 'Selected tab ground', '選択中のタブの背景', 'MENU_BACKGROUND'),
    ('TAB_UNSELECTED', 'TABS', 'Other tabs ground', 'その他のタブの背景', 'MENU_BACKGROUND'),
    ('TAB_BORDER', 'TABS', 'Tab frame', 'タブの枠', 'BORDER'),
    ('TAB_ACTIVE_BORDER', 'TABS', 'Selected tab frame', '選択中のタブの枠', 'ACCENT'),
    ('TAB_TEXT', 'TABS', 'Selected tab title', '選択中のタブのタイトル', 'TEXT'),
    ('TAB_TEXT_INACTIVE', 'TABS', 'Other tabs title', 'その他のタブのタイトル', 'TEXT'),
    ('TAB_CLOSE', 'TABS', 'Tab close button', 'タブを閉じるボタン', 'TEXT_SECONDARY'),

    # Switches.
    ('SWITCH_ON', 'SWITCHES', 'On: outline', 'オン：輪郭', 'ACCENT'),
    ('SWITCH_ON_DOT', 'SWITCHES', 'On: dot', 'オン：丸', 'SWITCH_ON'),
    ('SWITCH_OFF', 'SWITCHES', 'Off: outline', 'オフ：輪郭', 'mix(BACKGROUND,TEXT,0.55)'),
    ('SWITCH_OFF_DOT', 'SWITCHES', 'Off: dot', 'オフ：丸', 'SWITCH_OFF'),

    # Dialogs and sheets.
    ('DIALOG_BACKGROUND', 'DIALOGS', 'Dialog ground', 'ダイアログの背景', 'BACKGROUND'),
    ('DIALOG_BORDER', 'DIALOGS', 'Dialog frame', 'ダイアログの枠', 'BORDER'),
    ('DIALOG_TITLE', 'DIALOGS', 'Dialog title', 'ダイアログのタイトル', 'TEXT_HEADING'),
    ('DIALOG_TEXT', 'DIALOGS', 'Dialog text', 'ダイアログの本文', 'TEXT'),
    ('SHEET_BACKGROUND', 'DIALOGS', 'Sheet ground', 'シートの背景', 'MENU_BACKGROUND'),
    ('SHEET_BORDER', 'DIALOGS', 'Sheet frame', 'シートの枠', 'MENU_BORDER'),
    ('SHEET_HANDLE', 'DIALOGS', 'Sheet handle', 'シートのつまみ', 'ACCENT'),
    ('SCRIM', 'DIALOGS', 'Shade behind dialogs', 'ダイアログ背後の暗幕', 'alpha(BLACK,0.6)'),

    # Check boxes, radio buttons, sliders — selection marks, never switches.
    ('CHECK_ON', 'MARKS', 'Check box: ticked', 'チェックボックス：オン', 'ACCENT'),
    ('CHECK_OFF', 'MARKS', 'Check box: empty', 'チェックボックス：オフ', 'SWITCH_OFF'),
    ('CHECK_MARK', 'MARKS', 'Check box: the tick', 'チェックボックス：チェック印', 'BACKGROUND'),
    ('RADIO_ON', 'MARKS', 'Radio button: chosen', 'ラジオボタン：選択', 'ACCENT'),
    ('RADIO_OFF', 'MARKS', 'Radio button: not chosen', 'ラジオボタン：未選択', 'SWITCH_OFF'),
    ('SLIDER_ACTIVE', 'MARKS', 'Slider: filled part and thumb', 'スライダー：設定済みの部分とつまみ', 'ACCENT'),
    ('SLIDER_INACTIVE', 'MARKS', 'Slider: remaining part', 'スライダー：残りの部分', 'SWITCH_OFF'),
    ('PROGRESS', 'MARKS', 'Progress indicators', '進行状況の表示', 'ACCENT'),

    # Lists and cards.
    ('LIST_TEXT', 'LISTS', 'Row title', '行のタイトル', 'TEXT'),
    ('LIST_SECONDARY', 'LISTS', 'Row second line', '行の補足', 'TEXT_SECONDARY'),
    ('LIST_ICON', 'LISTS', 'Row icon', '行のアイコン', 'ACCENT'),
    ('LIST_DIVIDER', 'LISTS', 'List divider', 'リストの区切り線', 'mix(BACKGROUND,ACCENT,0.7)'),
    ('LIST_SELECTED', 'LISTS', 'Selected row ground', '選択中の行の背景', 'mix(BACKGROUND,ACCENT,0.25)'),
    ('CARD_BACKGROUND', 'LISTS', 'Card ground', 'カードの背景', 'MENU_BACKGROUND'),
    ('CARD_BORDER', 'LISTS', 'Card frame', 'カードの枠', 'MENU_BORDER'),

    # Text fields.
    ('FIELD_TEXT', 'FIELDS', 'Entered text', '入力した文字', 'TEXT'),
    ('FIELD_HINT', 'FIELDS', 'Hint and label', 'ヒントとラベル', 'TEXT_SECONDARY'),
    ('FIELD_GROUND', 'FIELDS', 'Field ground', '入力欄の背景', 'BACKGROUND'),
    ('FIELD_FRAME', 'FIELDS', 'Field frame', '入力欄の枠', 'mix(BACKGROUND,ACCENT,0.7)'),
    ('FIELD_FRAME_FOCUSED', 'FIELDS', 'Field frame while typing', '入力中の枠', 'ACCENT'),

    # Bars.
    ('STATUS_BAR', 'BARS', 'Status bar', 'ステータスバー', 'BACKGROUND'),
    ('NAVIGATION_BAR', 'BARS', 'Navigation bar', 'ナビゲーションバー', 'BACKGROUND'),
    ('TOP_BAR_GROUND', 'BARS', 'Top bar ground', '上部バーの背景', 'BACKGROUND'),
    ('TOP_BAR_TITLE', 'BARS', 'Top bar title', '上部バーのタイトル', 'TEXT'),
    ('TOP_BAR_ICON', 'BARS', 'Top bar icons', '上部バーのアイコン', 'ACCENT'),

    # Toasts and snackbars.
    ('TOAST_BACKGROUND', 'TOASTS', 'Toast ground', 'トーストの背景', 'MENU_BACKGROUND'),
    ('TOAST_BORDER', 'TOASTS', 'Toast frame', 'トーストの枠', 'MENU_BORDER'),
    ('TOAST_TEXT', 'TOASTS', 'Toast text', 'トーストの文字', 'TEXT'),
    ('TOAST_ACTION', 'TOASTS', 'Toast action', 'トーストの操作ボタン', 'ACCENT'),

    # Entries of the Material colour scheme that upstream's screens read directly.
    ('SCHEME_CONTAINER', 'SCREENS', 'Highlighted area ground', '強調された領域の背景', 'mix(BACKGROUND,ACCENT,0.25)'),
    ('SCHEME_ON_CONTAINER', 'SCREENS', 'Highlighted area text', '強調された領域の文字', 'TEXT'),
    ('SCHEME_SECONDARY', 'SCREENS', 'Secondary accent', '第二のアクセント', 'TEXT_SECONDARY'),
    ('SCHEME_SURFACE_VARIANT', 'SCREENS', 'Raised area ground', '一段高い領域の背景', 'MENU_BACKGROUND'),
    ('SCHEME_OUTLINE_VARIANT', 'SCREENS', 'Faint outlines', '薄い輪郭線', 'alpha(BORDER,0.6)'),
    ('SCHEME_ERROR', 'SCREENS', 'Error and delete', 'エラーと削除', '#FF0000'),
    ('SCHEME_ON_ERROR', 'SCREENS', 'Text on error', 'エラー色の上の文字', 'BACKGROUND'),
    ('SCHEME_INFORMATION', 'SCREENS', 'Information', '情報', '#00B0FF'),
    ('SCHEME_SUCCESS', 'SCREENS', 'Success', '成功', '#00A550'),
    ('SCHEME_WARNING', 'SCREENS', 'Warning', '警告', '#FF8000'),
    ('SCHEME_INVERSE_SURFACE', 'SCREENS', 'Inverted area ground', '反転した領域の背景', 'TEXT'),
    ('SCHEME_INVERSE_TEXT', 'SCREENS', 'Inverted area text', '反転した領域の文字', 'BACKGROUND'),
    ('PRIVATE_ICON', 'SCREENS', 'Private browsing mark', 'プライベートブラウジングの印', 'ACCENT'),
]

LICENSE_KT = '''/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
'''
LICENSE_XML = '''<?xml version="1.0" encoding="utf-8"?>
<!-- This Source Code Form is subject to the terms of the Mozilla Public
   - License, v. 2.0. If a copy of the MPL was not distributed with this
   - file, You can obtain one at http://mozilla.org/MPL/2.0/. -->
'''


def mix(a, b, f):
    def ch(shift):
        x = (a >> shift) & 0xFF
        y = (b >> shift) & 0xFF
        return max(0, min(255, int(x + (y - x) * f + 0.5)))
    return 0xFF000000 | (ch(16) << 16) | (ch(8) << 8) | ch(0)


def alpha(c, f):
    return (c & 0x00FFFFFF) | (max(0, min(255, int(f * 255 + 0.5))) << 24)


CALL = re.compile(r'^(mix|alpha)\((.*)\)$')


def evaluate(expr, values):
    expr = expr.strip()
    if expr == 'BLACK':
        return BLACK
    if expr == 'YELLOW':
        return YELLOW
    if expr.startswith('#'):
        return 0xFF000000 | int(expr[1:], 16)
    m = CALL.match(expr)
    if m:
        args = [a.strip() for a in m.group(2).split(',')]
        if m.group(1) == 'mix':
            return mix(evaluate(args[0], values), evaluate(args[1], values), float(args[2]))
        return alpha(evaluate(args[0], values), float(args[1]))
    if expr not in values:
        sys.exit(f'{expr}: a default may only name a role listed above it')
    return values[expr]


def kotlin(expr):
    expr = expr.strip()
    if expr == 'BLACK':
        return 'KAKO_PALETTE_BLACK'
    if expr == 'YELLOW':
        return 'KAKO_PALETTE_YELLOW'
    if expr.startswith('#'):
        return f'0xFF{expr[1:].upper()}.toInt()'
    m = CALL.match(expr)
    if m:
        args = [a.strip() for a in m.group(2).split(',')]
        if m.group(1) == 'mix':
            return f'mix({kotlin(args[0])}, {kotlin(args[1])}, {args[2]}f)'
        return f'alpha({kotlin(args[0])}, {args[1]}f)'
    return f'c(KakoSlot.{expr})'


def xml_text(s):
    return (s.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;')
             .replace("'", "\\'").replace('"', '\\"'))


def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        f.write(text)
    print('wrote', os.path.relpath(path, ROOT))


def main():
    names = [r[0] for r in ROLES]
    if len(set(names)) != len(names):
        sys.exit('duplicate role name')
    section_ids = {s[0] for s in SECTIONS}
    values = {}
    for name, section, _en, _ja, default in ROLES:
        if section not in section_ids:
            sys.exit(f'{name}: unknown section {section}')
        values[name] = evaluate(default, values)

    kt = [LICENSE_KT, 'package org.mozilla.fenix.kako', '',
          'import androidx.annotation.ColorRes', 'import androidx.annotation.StringRes',
          'import org.mozilla.fenix.R', '',
          '// GENERATED by tools/kako/gen_roles.py — edit the table there, never this file.', '',
          '/** The sections a colour role is set in, in the UI page\'s default order. */',
          'enum class KakoSection(@param:StringRes val labelRes: Int) {']
    for sid, _en, _ja in SECTIONS:
        kt.append(f'    {sid}(R.string.kako_section_{sid.lower()}),')
    kt += ['}', '',
           '/**',
           ' * One colour role: one kind of thing 白い熊 can see, settable by itself on the UI page.',
           ' * [key] is the preferences key of its override, [colorRes] the colour resource upstream\'s XML',
           ' * reaches it through, [default] what it shows while unset — one of the two primaries, a',
           ' * colour that carries meaning, or a value derived from roles above it.',
           ' */',
           'enum class KakoSlot(',
           '    val key: String,',
           '    val section: KakoSection,',
           '    @param:StringRes val labelRes: Int,',
           '    @param:ColorRes val colorRes: Int,',
           '    internal val default: KakoDefaults.() -> Int,',
           ') {']
    for name, section, _en, _ja, default in ROLES:
        low = name.lower()
        kt += [f'    {name}(',
               f'        "kako_theme_{low}",',
               f'        KakoSection.{section},',
               f'        R.string.kako_slot_{low},',
               f'        R.color.kako_role_{low},',
               f'        {{ {kotlin(default)} }},',
               '    ),']
    kt += ['}', '']
    write(os.path.join(APP, 'java/org/mozilla/fenix/kako/KakoRoleTable.kt'), '\n'.join(kt))

    colors = [LICENSE_XML,
              '<!-- GENERATED by tools/kako/gen_roles.py. One colour per role of 白い熊 火狐 UI, holding the',
              '     role\'s computed default. KakoPalette lays the stored values over these at run time.',
              '     Keep them in the default values/ only: a qualified copy beats the run-time value. -->',
              '<resources>']
    for name, *_ in ROLES:
        colors.append(f'    <color name="kako_role_{name.lower()}">#{values[name]:08X}</color>')
    colors += ['</resources>', '']
    write(os.path.join(APP, 'res/values/kako_roles.xml'), '\n'.join(colors))

    for lang, idx in (('values', 1), ('values-ja', 2)):
        out = [LICENSE_XML, '<!-- GENERATED by tools/kako/gen_roles.py. -->', '<resources>']
        for s in SECTIONS:
            out.append(f'    <string name="kako_section_{s[0].lower()}">{xml_text(s[idx])}</string>')
        for r in ROLES:
            out.append(f'    <string name="kako_slot_{r[0].lower()}">{xml_text(r[idx + 1])}</string>')
        out += ['</resources>', '']
        write(os.path.join(APP, f'res/{lang}/kako_role_strings.xml'), '\n'.join(out))
    print(len(ROLES), 'roles in', len(SECTIONS), 'sections')


if __name__ == '__main__':
    main()
