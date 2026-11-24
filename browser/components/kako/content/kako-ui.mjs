/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

/*
 * 白い熊 火狐 UI: a folding accordion with a draggable chip index on top.
 *
 * The first element is the pill that opens the browser's own settings. Under
 * it, one chip per section: a click unfolds the section and scrolls to it, a
 * drag reorders the chips live and the sections follow on drop. Every section
 * is folded on a fresh profile; fold state and order are kept by KakoTheme.
 *
 * Rows: one that does something is a pill hugging its label, its explanation
 * beneath; one with a control (a switch, a swatch) stays plain. Every row and
 * every heading has an icon coloured from a role.
 *
 * No colour is written here. The page declares every role on its own root so
 * that it keeps its colours while the master switch is off.
 */

const { KakoTheme } = ChromeUtils.importESModule(
  "moz-src:///browser/components/kako/KakoTheme.sys.mjs"
);

const SETTINGS_URL = "about:preferences";
const ICONS = "chrome://global/skin/icons/";
const SKIN = "chrome://browser/skin/";
const FOLDED = "›";
const OPEN = "⌄";
const DRAG_TYPE = "application/x-kako-section";

// The page's own localization: kako.ftl from the fork's own registry, in the
// locales 白い熊 asked for.
const l10n = new DOMLocalization(
  [KakoTheme.L10N_FILE],
  false,
  KakoTheme.pageRegistry(),
  KakoTheme.locales()
);

const $ = id => document.getElementById(id);

function el(tag, props = {}, ...children) {
  let node = document.createElement(tag);
  for (let [attr, value] of Object.entries(props)) {
    if (attr == "l10n") {
      node.setAttribute("data-l10n-id", value);
    } else if (attr == "class") {
      node.className = value;
    } else {
      node.setAttribute(attr, value);
    }
  }
  node.append(...children);
  return node;
}

// An icon in the colour of `role`. The colour goes in through the CSSOM, as a role, never as a value.
function icon(src, role) {
  let img = el("img", { class: "icon", src, alt: "", draggable: "false" });
  let colour = `var(--kako-${role})`;
  img.style.setProperty("fill", colour);
  img.style.setProperty("stroke", colour);
  return img;
}

/*
 * The icon of a role's row, by the kind of thing the role colours, and the
 * colour that kind is shown in. A role that is itself an icon colour (a menu
 * icon role, a section icon role) shows its icon in its own colour.
 */
function roleIcon(key) {
  if (key.startsWith("menu_icon_")) {
    return [ICONS + "more.svg", key];
  }
  if (key.startsWith("section_")) {
    return [ICONS + "settings.svg", key];
  }
  if (/delete|error|warning/.test(key)) {
    return [ICONS + "delete.svg", "section_warning"];
  }
  if (/border|frame|divider|rule|ring|outline|separator|line/.test(key)) {
    return [ICONS + "edit-outline.svg", "section_base"];
  }
  if (/text|label|title|heading|hint|link|cursor|header|note/.test(key)) {
    return [ICONS + "edit.svg", "section_words"];
  }
  if (
    /icon|dot|mark|thumb|close|loading|progress|slider|check|radio|switch/.test(
      key
    )
  ) {
    return [ICONS + "cursor-arrow.svg", "section_controls"];
  }
  if (
    /background|fill|ground|selected|highlight|hover|active|scrim|track|ripple|filled/.test(
      key
    )
  ) {
    return [ICONS + "canvas.svg", "section_frame"];
  }
  return [ICONS + "color-picker-20.svg", "section_reach"];
}

// ----- rows ----------------------------------------------------------------

// A row that does something: a pill hugging its label, the explanation beneath.
function actionRow({ label, desc, src, role, main, opens, destructive, run }) {
  let classes = ["pill"];
  if (main) {
    classes.push("main");
  }
  if (opens) {
    classes.push("opens");
  }
  if (destructive) {
    classes.push("destructive");
  }
  let button = el(
    "button",
    { class: classes.join(" "), type: "button" },
    icon(src, role),
    el("span", { l10n: label })
  );
  button.addEventListener("click", run);
  return el(
    "div",
    { class: "action-row" },
    button,
    el("p", { class: "explanation", l10n: desc })
  );
}

const roleRows = new Map();

// A role: icon, label, the mark of a changed role, its value, its swatch and its reset.
function roleRow(role) {
  let [src, tone] = roleIcon(role.key);
  let changed = el("span", { class: "changed", l10n: "kako-ui-role-changed" });
  let hex = el("input", {
    class: "hex",
    type: "text",
    maxlength: "9",
    spellcheck: "false",
    l10n: "kako-ui-role-hex",
  });
  let swatch = el("input", {
    class: "swatch",
    type: "color",
    l10n: "kako-ui-role-pick",
  });
  let reset = el(
    "button",
    { class: "pill reset", type: "button", l10n: "kako-ui-role-reset" },
    icon(ICONS + "undo.svg", "menu_icon_connect")
  );

  // The picker reports every step of a drag; one write per frame is plenty.
  let pending = null;
  swatch.addEventListener("input", () => {
    let first = pending === null;
    pending = swatch.value;
    if (first) {
      requestAnimationFrame(() => {
        KakoTheme.set(role.key, pending);
        pending = null;
      });
    }
  });
  hex.addEventListener("change", () => {
    try {
      KakoTheme.set(role.key, hex.value);
    } catch (e) {
      hex.value = KakoTheme.get(role.key);
    }
  });
  hex.addEventListener("keydown", event => {
    if (event.key == "Enter") {
      hex.blur();
    }
  });
  reset.addEventListener("click", () => KakoTheme.reset(role.key));

  let row = el(
    "div",
    { class: "row role", "data-role": role.key },
    icon(src, tone),
    el("span", { class: "label", l10n: role.l10nId }),
    changed,
    hex,
    swatch,
    reset
  );
  roleRows.set(role.key, { changed, hex, swatch, reset });
  return row;
}

let masterSwitch = null;

// The master switch: a control row with an explanation.
function masterRow() {
  masterSwitch = el("button", {
    class: "toggle-button",
    type: "button",
    role: "switch",
    "aria-labelledby": "master-label",
  });
  masterSwitch.addEventListener("click", () => {
    KakoTheme.enabled = !KakoTheme.enabled;
  });
  return el(
    "div",
    { class: "explained" },
    el(
      "div",
      { class: "row" },
      icon(SKIN + "customize.svg", "menu_icon_view"),
      el("span", {
        class: "label",
        id: "master-label",
        l10n: "kako-ui-master-switch",
      }),
      masterSwitch
    ),
    el("p", { class: "description", l10n: "kako-ui-master-switch-desc" })
  );
}

// The rows of a section, built when it is first opened.
function buildRows(key) {
  let rows = [];
  if (key == "language") {
    rows.push(languageRow());
  }
  if (key == "foundation") {
    rows.push(masterRow());
    rows.push(el("p", { class: "note", l10n: "kako-ui-foundation-note" }));
  }
  if (key == "maintenance") {
    rows.push(
      actionRow({
        label: "kako-ui-reset-order",
        desc: "kako-ui-reset-order-desc",
        src: ICONS + "undo.svg",
        role: "menu_icon_connect",
        run: () => KakoTheme.resetOrder(),
      }),
      actionRow({
        label: "kako-ui-reset-colors",
        desc: "kako-ui-reset-colors-desc",
        src: ICONS + "delete.svg",
        role: "menu_delete",
        destructive: true,
        run: () =>
          ask(
            "kako-ui-reset-colors-question",
            "kako-ui-reset-colors-confirm",
            () => KakoTheme.resetAll()
          ),
      })
    );
  }
  let subheading = null;
  for (let role of KakoTheme.roles) {
    if (role.section != key) {
      continue;
    }
    // The two groups of icon colours get a sub-heading where they begin.
    let group = role.key.startsWith("menu_icon_")
      ? "kako-ui-menu-icons"
      : role.key.startsWith("section_") && "kako-ui-section-icons";
    if (group && group != subheading) {
      subheading = group;
      rows.push(el("div", { class: "subheading", l10n: group }));
    }
    rows.push(roleRow(role));
  }
  return rows;
}

// ----- the app's language ---------------------------------------------------

// A language's own name and region, each in that language.
function languageNames(tag) {
  let locale = new Intl.Locale(tag);
  let name = new Intl.DisplayNames([tag], { type: "language" }).of(
    locale.language
  );
  let region = locale.region
    ? new Intl.DisplayNames([tag], { type: "region" }).of(locale.region)
    : "";
  return [name.charAt(0).toLocaleUpperCase(tag) + name.slice(1), region];
}

// The one row of the section: a pill that opens the list.
function languageRow() {
  let current = KakoTheme.language;
  let value = el("span");
  if (current) {
    value.textContent = languageNames(current)[0];
  } else {
    value.setAttribute("data-l10n-id", "kako-ui-language-system");
  }
  let button = el(
    "button",
    { class: "pill main opens", type: "button" },
    icon(ICONS + "defaultFavicon.svg", "menu_icon_message"),
    el("span", { l10n: "kako-ui-language-row" }),
    value
  );
  button.addEventListener("click", openLanguages);
  return el("div", { class: "action-row" }, button);
}

/*
 * The list: "System default" first, then every language the package carries,
 * each in its own language, sorted by name. Rows are plain; only the current
 * one sits in a pill. Choosing a row applies it at once.
 */
function openLanguages() {
  let current = KakoTheme.language;
  let list = $("languages-list");
  list.replaceChildren();
  let entries = KakoTheme.shippedLanguages
    .map(tag => [tag, ...languageNames(tag)])
    .sort((a, b) => a[1].localeCompare(b[1]));
  for (let [tag, name, region] of [["", "", ""], ...entries]) {
    let label = el("span", { class: "language-name" });
    if (tag) {
      label.textContent = name;
    } else {
      label.setAttribute("data-l10n-id", "kako-ui-language-system");
    }
    let names = el("span", { class: "language-names" }, label);
    if (region) {
      let line = el("span", { class: "language-region" });
      line.textContent = region;
      names.append(line);
    }
    let row = el(
      "button",
      {
        class: "language" + (tag == current ? " current" : ""),
        type: "button",
        role: "radio",
        "aria-checked": String(tag == current),
      },
      el("span", { class: "language-mark", "aria-hidden": "true" }),
      names
    );
    if (tag) {
      let code = el("span", { class: "language-tag" });
      code.textContent = tag;
      row.append(code);
    }
    row.addEventListener("click", () => {
      $("languages").hidden = true;
      if (tag != current) {
        KakoTheme.language = tag;
        // The page reads its strings once; show it again in the new language.
        window.location.reload();
      }
    });
    list.append(row);
  }
  $("languages").hidden = false;
  $("languages-cancel").focus();
}

// ----- sections and their index ----------------------------------------------

const sections = new Map();

function setOpen(key, isOpen, persist = true) {
  let section = sections.get(key);
  if (isOpen && !section.built) {
    section.built = true;
    section.rows.append(...buildRows(key));
    refreshValues();
  }
  section.rows.hidden = !isOpen;
  section.fold.textContent = isOpen ? OPEN : FOLDED;
  section.heading.setAttribute("aria-expanded", String(isOpen));
  if (persist && KakoTheme.isOpen(key) != isOpen) {
    KakoTheme.setOpen(key, isOpen);
  }
}

function buildSection({ key, icon: src, colour }) {
  let label = "kako-section-" + key;
  let fold = el("span", { class: "fold", "aria-hidden": "true" });
  let heading = el(
    "div",
    { class: "heading", role: "button", tabindex: "0" },
    icon(src, colour),
    el("span", { class: "label", l10n: label }),
    fold
  );
  let rows = el("div", { class: "rows" });
  let node = el(
    "section",
    { class: "section", id: "section-" + key },
    heading,
    rows
  );
  let toggle = () => setOpen(key, rows.hidden);
  heading.addEventListener("click", toggle);
  heading.addEventListener("keydown", event => {
    if (event.key == "Enter" || event.key == " ") {
      event.preventDefault();
      toggle();
    }
  });

  // The chip: the same icon in the same colour, as wide as its content.
  let chip = el(
    "button",
    {
      class: "pill chip",
      type: "button",
      draggable: "true",
      "data-section": key,
    },
    icon(src, colour),
    el("span", { l10n: label })
  );
  chip.addEventListener("click", () => {
    setOpen(key, true);
    node.scrollIntoView({ behavior: "smooth", block: "start" });
  });

  sections.set(key, { node, heading, rows, fold, chip, built: false });
}

// Puts chips and sections in the stored order and the stored fold state.
function arrange() {
  let order = KakoTheme.order;
  $("index").replaceChildren(...order.map(key => sections.get(key).chip));
  $("sections").replaceChildren(...order.map(key => sections.get(key).node));
  for (let key of order) {
    setOpen(key, KakoTheme.isOpen(key), false);
  }
}

// The chip drag: the chips reorder live under the pointer, the sections follow on drop.
function initDrag() {
  let index = $("index");
  let dragged = null;
  index.addEventListener("dragstart", event => {
    dragged = event.target.closest?.(".chip");
    if (!dragged) {
      return;
    }
    event.dataTransfer.setData(DRAG_TYPE, dragged.dataset.section);
    event.dataTransfer.effectAllowed = "move";
    // After the drag image was taken, so that the image is the whole chip.
    requestAnimationFrame(() => dragged?.classList.add("dragging"));
  });
  index.addEventListener("dragover", event => {
    if (!dragged) {
      return;
    }
    event.preventDefault();
    event.dataTransfer.dropEffect = "move";
    let over = document
      .elementFromPoint(event.clientX, event.clientY)
      ?.closest(".chip");
    if (!over || over == dragged || over.parentNode != index) {
      return;
    }
    // Into the slot under the pointer: before a chip that stood before the
    // dragged one, after a chip that stood after it.
    let chips = [...index.children];
    if (chips.indexOf(over) < chips.indexOf(dragged)) {
      over.before(dragged);
    } else {
      over.after(dragged);
    }
  });
  index.addEventListener("drop", event => event.preventDefault());
  index.addEventListener("dragend", () => {
    if (!dragged) {
      return;
    }
    dragged.classList.remove("dragging");
    dragged = null;
    let order = [...index.children].map(chip => chip.dataset.section);
    if (order.join() != KakoTheme.order.join()) {
      KakoTheme.order = order;
    }
    $("sections").replaceChildren(...order.map(key => sections.get(key).node));
  });
}

// ----- values ------------------------------------------------------------------

// Declares every role on the page's root, and brings every built row up to date.
function refreshValues() {
  let root = document.documentElement;
  for (let role of KakoTheme.roles) {
    let value = KakoTheme.get(role.key);
    root.style.setProperty("--kako-" + role.key, value);
    let row = roleRows.get(role.key);
    if (!row) {
      continue;
    }
    let set = KakoTheme.isSet(role.key);
    row.changed.hidden = !set;
    row.reset.disabled = !set;
    if (document.activeElement != row.hex) {
      row.hex.value = value;
    }
    // A colour input holds #rrggbb; the opacity of a role stays in the value field.
    row.swatch.value = value.slice(0, 7).toLowerCase();
  }
  masterSwitch?.setAttribute("aria-pressed", String(KakoTheme.enabled));
}

// ----- the confirmation -----------------------------------------------------------

let confirmed = null;

function ask(question, action, run) {
  $("confirm-text").setAttribute("data-l10n-id", question);
  $("confirm-ok").setAttribute("data-l10n-id", action);
  confirmed = run;
  $("confirm").hidden = false;
  $("confirm-cancel").focus();
}

function closeConfirm(run) {
  $("confirm").hidden = true;
  let action = confirmed;
  confirmed = null;
  if (run) {
    action?.();
  }
}

// ----- start ------------------------------------------------------------------------

const observer = {
  observe(subject, topic, data) {
    if (data.startsWith("kako.ui.section")) {
      arrange();
    } else {
      refreshValues();
    }
  },
};

function init() {
  $("app-settings-row").replaceWith(
    actionRow({
      label: "kako-ui-app-settings",
      desc: "kako-ui-app-settings-desc",
      src: ICONS + "settings.svg",
      role: "menu_icon_view",
      main: true,
      opens: true,
      run: () =>
        window.browsingContext.topChromeWindow.switchToTabHavingURI(
          SETTINGS_URL,
          true,
          {
            triggeringPrincipal:
              Services.scriptSecurityManager.getSystemPrincipal(),
          }
        ),
    })
  );
  for (let section of KakoTheme.sections) {
    buildSection(section);
  }
  refreshValues();
  arrange();
  initDrag();

  $("languages-cancel").addEventListener("click", () => {
    $("languages").hidden = true;
  });
  $("languages").addEventListener("click", event => {
    if (event.target == $("languages")) {
      $("languages").hidden = true;
    }
  });
  $("confirm-cancel").addEventListener("click", () => closeConfirm(false));
  $("confirm-ok").addEventListener("click", () => closeConfirm(true));
  $("confirm").addEventListener("click", event => {
    if (event.target == $("confirm")) {
      closeConfirm(false);
    }
  });
  document.addEventListener("keydown", event => {
    if (event.key == "Escape" && !$("confirm").hidden) {
      closeConfirm(false);
    }
    if (event.key == "Escape") {
      $("languages").hidden = true;
    }
  });

  Services.obs.addObserver(observer, KakoTheme.TOPIC_CHANGED);
  window.addEventListener(
    "unload",
    () => Services.obs.removeObserver(observer, KakoTheme.TOPIC_CHANGED),
    { once: true }
  );

  l10n.connectRoot(document.documentElement);
  l10n.translateRoots();
}

init();
