/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

"use strict";

/*
 * 白い熊 火狐 fork: the `menus` (a.k.a. `contextMenus`) WebExtension API for
 * GeckoView.
 *
 * Upstream ships this API only for desktop (browser/components/extensions),
 * so on Android `browser.menus` is simply absent and the options an extension
 * hangs off its toolbar button on the PC have nowhere to live. Bug 1595822 has
 * been open since 2019; GeckoView carries the Java scaffolding for it
 * (WebExtension.Menu, MenuContextFlags.BROWSER_ACTION) but the JS half answers
 * "Not implemented".
 *
 * This module is the missing JS half, cut down to what the fork actually
 * renders: the items an extension registers for its *browser action*, which
 * Fenix merges into the long-press menu of the pinned toolbar button. Items
 * for page/link/selection/tab contexts are still accepted and stored -- an
 * extension that registers them must not break -- they are simply never shown.
 *
 * The model layer (MenuItem, context matching, click info, persistence via
 * ExtensionMenus.sys.mjs) follows the desktop implementation closely so the
 * observable behaviour matches Firefox for Desktop. What replaces the desktop
 * XUL builder is a push over the GeckoView EventDispatcher: every change to an
 * extension's menu republishes that extension's browser-action item list to
 * the embedder, and a tap comes back the same way.
 */

ChromeUtils.defineESModuleGetters(this, {
  ExtensionMenus: "resource://gre/modules/ExtensionMenus.sys.mjs",
  setTimeout: "resource://gre/modules/Timer.sys.mjs",
});

var { ExtensionError, parseMatchPatterns } = ExtensionUtils;

// Events exchanged with the embedder. The fork uses its own names rather than
// upstream's "GeckoView:WebExtension:Menu*" so that GeckoViewWebExtension's
// stub listeners for those (which reply "Not implemented") stay untouched.
const EVENT_UPDATE = "Kako:ExtensionMenu:Update";
const EVENT_CLICK = "Kako:ExtensionMenu:Click";
const EVENT_SHOWN = "Kako:ExtensionMenu:Shown";
const EVENT_HIDDEN = "Kako:ExtensionMenu:Hidden";

// Map[Extension -> Map[MenuId -> MenuItem]]. Enumerated on every publish, so
// this cannot be a weak map.
var gMenuMap = new Map();

// Map[Extension -> MenuItem]: the invisible root every top-level item hangs off.
var gRootItems = new Map();

// Map[extensionId -> Map[key -> MenuItem]] for the items last handed to the
// embedder, so a click can be resolved back to the item that produced it.
var gPublishedItems = new Map();

// Map[extensionId -> Extension] for the extensions whose API is live.
var gExtensions = new Map();

// If an id is not specified for an item we use an integer.
var gNextMenuItemID = 0;

// Used to assign unique names to radio groups.
var gNextRadioGroupID = 0;

/**
 * The set of contexts in force when an extension's own action is invoked,
 * mirroring getMenuContexts() in the desktop implementation: the action
 * context itself plus "all", which by definition covers it.
 *
 * @param {object} extension The extension whose action was invoked.
 * @returns {Set<string>} The contexts an item must intersect to be shown.
 */
function actionContextsFor(extension) {
  return new Set([
    extension.manifestVersion < 3 ? "browser_action" : "action",
    "all",
  ]);
}

/**
 * The URL of the page the action was invoked on, used for documentUrlPatterns.
 *
 * @returns {string|null} The active tab's URL, or null when there is no tab.
 */
function activePageUrl() {
  return tabTracker.activeTab?.browser?.currentURI?.spec ?? null;
}

class MenuItem {
  constructor(extension, createProperties, isRoot = false) {
    this.extension = extension;
    this.children = [];
    this.parent = null;
    this.tabManager = extension.tabManager;

    this.setDefaults();
    this.setProps(createProperties);

    if (!this.hasOwnProperty("_id")) {
      this.id = gNextMenuItemID++;
    }
    // If the item is not the root and has no parent it must be a child of the
    // root.
    if (!isRoot && !this.parent) {
      this.root.addChild(this);
    }
  }

  setProps(createProperties) {
    ExtensionMenus.mergeMenuProperties(this, createProperties);

    if (createProperties.documentUrlPatterns != null) {
      this.documentUrlMatchPattern = parseMatchPatterns(
        this.documentUrlPatterns,
        { restrictSchemes: this.extension.restrictSchemes }
      );
    }

    if (createProperties.targetUrlPatterns != null) {
      this.targetUrlMatchPattern = parseMatchPatterns(this.targetUrlPatterns, {
        // restrictSchemes defaults to false when matching links instead of
        // pages (see bug 1280370 for the rationale).
        restrictSchemes: false,
      });
    }

    // A child that specifies no contexts inherits its parent's.
    if (createProperties.parentId && !createProperties.contexts) {
      this.contexts = this.parent.contexts;
    }
  }

  setDefaults() {
    this.setProps({
      type: "normal",
      checked: false,
      contexts: ["all"],
      enabled: true,
      visible: true,
    });
  }

  set id(id) {
    if (this.hasOwnProperty("_id")) {
      throw new ExtensionError("ID of a MenuItem cannot be changed");
    }
    if (gMenuMap.get(this.extension).has(id)) {
      throw new ExtensionError(`ID already exists: ${id}`);
    }
    this._id = id;
  }

  get id() {
    return this._id;
  }

  /**
   * The opaque handle the embedder identifies this item by. Ids are unique
   * only within one extension and may be either a string or an auto-assigned
   * integer, so the type is encoded in the key to keep the round trip exact.
   *
   * @returns {string} The key sent to, and received back from, the embedder.
   */
  get itemKey() {
    return typeof this.id === "number" ? `n:${this.id}` : `s:${this.id}`;
  }

  ensureValidParentId(parentId) {
    if (parentId === undefined) {
      return;
    }
    const menuMap = gMenuMap.get(this.extension);
    if (!menuMap.has(parentId)) {
      throw new ExtensionError(`Cannot find menu item with id ${parentId}`);
    }
    for (let item = menuMap.get(parentId); item; item = item.parent) {
      if (item === this) {
        throw new ExtensionError(
          "MenuItem cannot be an ancestor (or self) of its new parent."
        );
      }
    }
  }

  set parentId(parentId) {
    this.ensureValidParentId(parentId);

    if (this.parent) {
      this.parent.detachChild(this);
    }

    if (parentId === undefined) {
      this.root.addChild(this);
    } else {
      gMenuMap.get(this.extension).get(parentId).addChild(this);
    }
  }

  get parentId() {
    return this.parent ? this.parent.id : undefined;
  }

  addChild(child) {
    if (child.parent) {
      throw new Error("Child MenuItem already has a parent.");
    }
    this.children.push(child);
    child.parent = this;
  }

  detachChild(child) {
    const idx = this.children.indexOf(child);
    if (idx < 0) {
      throw new Error("Child MenuItem not found, it cannot be removed.");
    }
    this.children.splice(idx, 1);
    child.parent = null;
  }

  get root() {
    const extension = this.extension;
    if (!gRootItems.has(extension)) {
      gRootItems.set(
        extension,
        new MenuItem(extension, { title: extension.name }, /* isRoot = */ true)
      );
    }
    return gRootItems.get(extension);
  }

  get descendantIds() {
    return this.children
      ? this.children.flatMap(m => [m.id, ...m.descendantIds])
      : [];
  }

  remove() {
    if (this.parent) {
      this.parent.detachChild(this);
    }
    for (const child of this.children.slice(0)) {
      child.remove();
    }

    gMenuMap.get(this.extension).delete(this.id);
    if (this.root == this) {
      gRootItems.delete(this.extension);
    }
  }

  getClickInfo(wasChecked) {
    const info = {
      menuItemId: this.id,
      editable: false,
      // An action menu is opened from the toolbar, not from a page or a
      // panel, so there is no view type to report.
      viewType: undefined,
    };
    if (this.parent) {
      info.parentMenuItemId = this.parentId;
    }

    const pageUrl = activePageUrl();
    if (pageUrl) {
      info.pageUrl = pageUrl;
    }

    if (this.type === "checkbox" || this.type === "radio") {
      info.checked = this.checked;
      info.wasChecked = wasChecked;
    }

    return info;
  }

  /**
   * Whether this item belongs in its extension's action menu, applying the
   * same rules the desktop builder applies for an onBrowserAction/onAction
   * context.
   *
   * @returns {boolean} True when the item should be shown.
   */
  enabledForActionContext() {
    if (!this.visible) {
      return false;
    }
    if (!this.contexts.some(n => actionContextsFor(this.extension).has(n))) {
      return false;
    }
    // getContextViewType() has nothing to report for an action context, so an
    // item restricted to particular view types never matches -- as on desktop.
    if (this.viewTypes) {
      return false;
    }
    // Likewise there is no link/image/media target to match against.
    if (this.targetUrlMatchPattern) {
      return false;
    }
    if (this.documentUrlMatchPattern) {
      const pageUrl = activePageUrl();
      if (!pageUrl) {
        return false;
      }
      if (!this.documentUrlMatchPattern.matches(Services.io.newURI(pageUrl))) {
        return false;
      }
    }
    return true;
  }
}

/**
 * Flattens an extension's action menu into the list the embedder renders.
 *
 * The fork's popup has no submenus, so the tree is walked depth-first and each
 * item carries its nesting depth for the embedder to indent by. Unlike desktop
 * there is no ACTION_MENU_TOP_LEVEL_LIMIT: desktop folds items past the sixth
 * into a submenu to keep the context menu short, which a scrolling popup does
 * not need.
 *
 * @param {object} extension The extension whose menu should be flattened.
 * @returns {object} The `items` array and the key -> MenuItem lookup for it.
 */
function flattenActionMenu(extension) {
  const items = [];
  const byKey = new Map();

  const walk = (parent, depth) => {
    let groupName;
    for (const child of parent.children) {
      // Assign radio groups exactly as the desktop builder does: a run of
      // adjacent radio siblings shares one group.
      if (child.type == "radio" && !child.groupName) {
        if (!groupName) {
          groupName = `webext-radio-group-${gNextRadioGroupID++}`;
        }
        child.groupName = groupName;
      } else {
        groupName = null;
      }

      if (!child.enabledForActionContext()) {
        continue;
      }

      byKey.set(child.itemKey, child);
      items.push({
        key: child.itemKey,
        // There is no selection behind a toolbar button, so the %s placeholder
        // resolves to nothing -- and "&" is an access-key marker on desktop,
        // which a touch menu has no use for.
        title: (child.title ?? "")
          .replace(/&([\S\s]|$)/g, (_, next) => (next === "&" ? "&" : next))
          .replace(/%s/g, ""),
        type: child.type,
        checked: !!child.checked,
        enabled: !!child.enabled,
        depth,
      });

      walk(child, depth + 1);
    }
  };

  const root = gRootItems.get(extension);
  if (root) {
    walk(root, 0);
  }

  return { items, byKey };
}

var gPublishTimer = null;
var gPendingPublish = new Set();

/**
 * Hands an extension's current action menu to the embedder.
 *
 * @param {object} extension The extension to publish.
 */
function publishNow(extension) {
  const { items, byKey } = flattenActionMenu(extension);

  gPublishedItems.set(extension.id, byKey);
  EventDispatcher.instance.sendRequest(EVENT_UPDATE, {
    extensionId: extension.id,
    items,
  });
}

/**
 * Queues a publish. Extensions commonly create or update a whole batch of
 * items in one turn, and the embedder only needs the end state.
 *
 * @param {object} extension The extension whose menu changed.
 */
function publish(extension) {
  gPendingPublish.add(extension);
  if (gPublishTimer) {
    return;
  }
  gPublishTimer = setTimeout(() => {
    gPublishTimer = null;
    const pending = gPendingPublish;
    gPendingPublish = new Set();
    for (const ext of pending) {
      if (gMenuMap.has(ext)) {
        publishNow(ext);
      }
    }
  }, 0);
}

/**
 * Relays taps and menu visibility from the embedder back into the extensions.
 */
const gEmbedderListener = {
  onEvent(event, data, callback) {
    switch (event) {
      case EVENT_CLICK: {
        const item = gPublishedItems.get(data.extensionId)?.get(data.key);
        if (!item) {
          callback?.onError(`Unknown menu item: ${data.key}`);
          return;
        }

        const wasChecked = item.checked;
        if (item.type == "checkbox") {
          item.checked = !item.checked;
        } else if (item.type == "radio") {
          for (const sibling of item.parent.children) {
            if (
              sibling.type == "radio" &&
              sibling.groupName == item.groupName
            ) {
              sibling.checked = false;
            }
          }
          item.checked = true;
        }

        const nativeTab = tabTracker.activeTab;
        if (nativeTab) {
          item.tabManager.addActiveTabPermission(nativeTab);
        }

        item.extension.emit(
          "webext-menu-menuitem-click",
          item.getClickInfo(wasChecked),
          nativeTab
        );

        // A checkbox or radio item now renders differently.
        if (item.type == "checkbox" || item.type == "radio") {
          publish(item.extension);
        }
        callback?.onSuccess(null);
        break;
      }

      case EVENT_SHOWN: {
        const extension = gExtensions.get(data.extensionId);
        if (extension) {
          const { byKey } = flattenActionMenu(extension);
          extension.emit(
            "webext-menu-shown",
            Array.from(byKey.values(), item => item.id),
            Array.from(actionContextsFor(extension))
          );
        }
        callback?.onSuccess(null);
        break;
      }

      case EVENT_HIDDEN: {
        gExtensions.get(data.extensionId)?.emit("webext-menu-hidden");
        callback?.onSuccess(null);
        break;
      }
    }
  },
};

var gListenerRegistered = false;

function ensureEmbedderListener() {
  if (gListenerRegistered) {
    return;
  }
  gListenerRegistered = true;
  EventDispatcher.instance.registerListener(gEmbedderListener, [
    EVENT_CLICK,
    EVENT_SHOWN,
    EVENT_HIDDEN,
  ]);
}

this.menusInternal = class extends ExtensionAPIPersistent {
  #promiseInitialized = null;

  constructor(extension) {
    super(extension);
    ensureEmbedderListener();
    gMenuMap.set(extension, new Map());
    gExtensions.set(extension.id, extension);
  }

  async initExtensionMenus() {
    const { extension } = this;

    await ExtensionMenus.asyncInitForExtension(extension);

    if (
      extension.hasShutdown ||
      !ExtensionMenus.shouldPersistMenus(extension)
    ) {
      return;
    }

    const menus = ExtensionMenus.getMenus(extension);
    if (!menus.size) {
      return;
    }

    const createErrorMenuIds = [];
    for (const createProperties of menus.values()) {
      // Order matters: parents are persisted before their children, so
      // recreating in sequence keeps the tree intact.
      try {
        const menuItem = new MenuItem(extension, createProperties);
        gMenuMap.get(extension).set(menuItem.id, menuItem);
      } catch (err) {
        Cu.reportError(
          `Unexpected error on recreating persisted menu ${createProperties?.id} for ${extension.id}: ${err}`
        );
        createErrorMenuIds.push(createProperties.id);
      }
    }

    if (createErrorMenuIds.length) {
      ExtensionMenus.deleteMenus(extension, createErrorMenuIds);
    }

    publish(extension);
  }

  onStartup() {
    this.#promiseInitialized = this.initExtensionMenus();
  }

  onShutdown() {
    const { extension } = this;

    if (gMenuMap.has(extension)) {
      gMenuMap.delete(extension);
      gRootItems.delete(extension);
      gExtensions.delete(extension.id);
      gPublishedItems.delete(extension.id);
      // Tell the embedder to drop the button's extra items with the extension.
      EventDispatcher.instance.sendRequest(EVENT_UPDATE, {
        extensionId: extension.id,
        items: [],
      });
    }
  }

  PERSISTENT_EVENTS = {
    onShown({ fire }) {
      const { extension } = this;
      const listener = (event, menuIds, contexts) => {
        fire.sync({ menuIds, contexts }, undefined);
      };
      extension.on("webext-menu-shown", listener);
      return {
        unregister() {
          extension.off("webext-menu-shown", listener);
        },
        convert(newFire) {
          fire = newFire;
        },
      };
    },

    onHidden({ fire }) {
      const { extension } = this;
      const listener = () => fire.sync();
      extension.on("webext-menu-hidden", listener);
      return {
        unregister() {
          extension.off("webext-menu-hidden", listener);
        },
        convert(newFire) {
          fire = newFire;
        },
      };
    },

    onClicked({ fire }) {
      const { extension } = this;
      const { tabManager } = extension;
      const listener = async (event, info, nativeTab) => {
        if (fire.wakeup) {
          await fire.wakeup();
        }
        const tab = nativeTab && tabManager.convert(nativeTab);
        fire.sync(info, tab);
      };
      extension.on("webext-menu-menuitem-click", listener);
      return {
        unregister() {
          extension.off("webext-menu-menuitem-click", listener);
        },
        convert(newFire) {
          fire = newFire;
        },
      };
    },
  };

  getAPI(context) {
    const { extension } = context;

    const menus = {
      refresh: () => {
        publish(extension);
      },

      onShown: new EventManager({
        context,
        module: "menusInternal",
        event: "onShown",
        name: "menus.onShown",
        extensionApi: this,
      }).api(),

      onHidden: new EventManager({
        context,
        module: "menusInternal",
        event: "onHidden",
        name: "menus.onHidden",
        extensionApi: this,
      }).api(),
    };

    return {
      contextMenus: menus,
      menus,
      menusInternal: {
        create: async createProperties => {
          await this.#promiseInitialized;
          if (extension.hasShutdown) {
            return;
          }

          // Event pages have no live scope to hold an auto-assigned id.
          if (ExtensionMenus.shouldPersistMenus(extension)) {
            if (!createProperties.id) {
              throw new ExtensionError(
                "menus.create requires an id for non-persistent background scripts."
              );
            }
            if (gMenuMap.get(extension).has(createProperties.id)) {
              throw new ExtensionError(
                `The menu id ${createProperties.id} already exists in menus.create.`
              );
            }
          }

          const menuItem = new MenuItem(extension, createProperties);
          ExtensionMenus.addMenu(extension, createProperties);
          gMenuMap.get(extension).set(menuItem.id, menuItem);
          publish(extension);
        },

        update: async (id, updateProperties) => {
          await this.#promiseInitialized;
          if (extension.hasShutdown) {
            return;
          }

          const menuItem = gMenuMap.get(extension).get(id);
          if (!menuItem) {
            throw new ExtensionError(`Cannot find menu item with id ${id}`);
          }

          menuItem.setProps(updateProperties);
          ExtensionMenus.updateMenu(extension, id, updateProperties);
          publish(extension);
        },

        remove: async id => {
          await this.#promiseInitialized;
          if (extension.hasShutdown) {
            return;
          }

          const menuItem = gMenuMap.get(extension).get(id);
          if (!menuItem) {
            throw new ExtensionError(`Cannot find menu item with id ${id}`);
          }

          const menuIds = [menuItem.id, ...menuItem.descendantIds];
          menuItem.remove();
          ExtensionMenus.deleteMenus(extension, menuIds);
          publish(extension);
        },

        removeAll: async () => {
          await this.#promiseInitialized;
          if (extension.hasShutdown) {
            return;
          }

          gRootItems.get(extension)?.remove();
          ExtensionMenus.deleteAllMenus(extension);
          publish(extension);
        },

        onClicked: new EventManager({
          context,
          module: "menusInternal",
          event: "onClicked",
          name: "menus.onClicked",
          extensionApi: this,
        }).api(),
      },
    };
  }
};
