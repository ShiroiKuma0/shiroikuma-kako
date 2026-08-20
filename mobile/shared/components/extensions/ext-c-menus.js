/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

"use strict";

/*
 * 白い熊 火狐 fork: the child half of the GeckoView `menus` API.
 *
 * Adapted from browser/components/extensions/child/ext-menus.js. The only
 * thing dropped is menus.overrideContext(), which drives the desktop content
 * area's context menu and has no counterpart here; it is left out of the
 * schema too, so extensions can feature-detect it.
 *
 * This half exists because menus.create() is specified to return the new id
 * *synchronously* while the item itself is created in the parent. It also
 * carries the deprecated `onclick` property, which many MV2 extensions still
 * use in place of an onClicked listener.
 */

var { withHandlingUserInput } = ExtensionCommon;

var { ExtensionError } = ExtensionUtils;

// If id is not specified for an item we use an integer. This ID need only be
// unique within a single addon. Since all addon code that can use this API
// runs in the same process, this local variable suffices.
var gNextMenuItemID = 0;

// Map[Extension -> Map[string or id, ContextMenusClickPropHandler]]
var gPropHandlers = new Map();

// The contextMenus API supports an "onclick" attribute in the create/update
// methods to register a callback. This class manages these onclick properties.
class ContextMenusClickPropHandler {
  constructor(context) {
    this.context = context;
    // Map[string or integer -> callback]
    this.onclickMap = new Map();
    this.dispatchEvent = this.dispatchEvent.bind(this);
  }

  // A listener on contextMenus.onClicked that forwards the event to the only
  // listener, if any.
  dispatchEvent(info, tab) {
    const onclick = this.onclickMap.get(info.menuItemId);
    if (onclick) {
      // No need for runSafe or anything because we are already being run inside
      // an event handler -- the event is just being forwarded to the actual
      // handler.
      withHandlingUserInput(this.context.contentWindow, () =>
        onclick(info, tab)
      );
    }
  }

  // Sets the `onclick` handler for the given menu item.
  // The `onclick` function MUST be owned by `this.context`.
  setListener(id, onclick) {
    if (this.onclickMap.size === 0) {
      this.context.childManager
        .getParentEvent("menusInternal.onClicked")
        .addListener(this.dispatchEvent);
      this.context.callOnClose(this);
    }
    this.onclickMap.set(id, onclick);

    let propHandlerMap = gPropHandlers.get(this.context.extension);
    if (!propHandlerMap) {
      propHandlerMap = new Map();
    } else {
      // If the current callback was created in a different context, remove it
      // from the other context.
      const propHandler = propHandlerMap.get(id);
      if (propHandler && propHandler !== this) {
        propHandler.unsetListener(id);
      }
    }
    propHandlerMap.set(id, this);
    gPropHandlers.set(this.context.extension, propHandlerMap);
  }

  // Deletes the `onclick` handler for the given menu item.
  // The `onclick` function MUST be owned by `this.context`.
  unsetListener(id) {
    if (!this.onclickMap.delete(id)) {
      return;
    }
    if (this.onclickMap.size === 0) {
      this.context.childManager
        .getParentEvent("menusInternal.onClicked")
        .removeListener(this.dispatchEvent);
      this.context.forgetOnClose(this);
    }
    const propHandlerMap = gPropHandlers.get(this.context.extension);
    propHandlerMap.delete(id);
    if (propHandlerMap.size === 0) {
      gPropHandlers.delete(this.context.extension);
    }
  }

  // Deletes the `onclick` handler for the given menu item, if any, regardless
  // of the context where it was created.
  unsetListenerFromAnyContext(id) {
    const propHandlerMap = gPropHandlers.get(this.context.extension);
    const propHandler = propHandlerMap && propHandlerMap.get(id);
    if (propHandler) {
      propHandler.unsetListener(id);
    }
  }

  // Remove all `onclick` handlers of the extension.
  deleteAllListenersFromExtension() {
    const propHandlerMap = gPropHandlers.get(this.context.extension);
    if (propHandlerMap) {
      for (const [id, propHandler] of propHandlerMap) {
        propHandler.unsetListener(id);
      }
    }
  }

  // Removes all `onclick` handlers from this context.
  close() {
    for (const id of this.onclickMap.keys()) {
      this.unsetListener(id);
    }
  }
}

this.menusInternal = class extends ExtensionAPI {
  getAPI(context) {
    const onClickedProp = new ContextMenusClickPropHandler(context);

    const api = {
      menus: {
        create(createProperties, callback) {
          const caller = context.getCaller();

          if (
            context.extension.persistentBackground &&
            createProperties.id === null
          ) {
            createProperties.id = ++gNextMenuItemID;
          }
          const { onclick } = createProperties;
          if (onclick && !context.extension.persistentBackground) {
            throw new ExtensionError(
              `Property "onclick" cannot be used in menus.create, replace with an "onClicked" event listener.`
            );
          }
          delete createProperties.onclick;
          context.childManager
            .callParentAsyncFunction("menusInternal.create", [createProperties])
            .then(() => {
              if (onclick) {
                onClickedProp.setListener(createProperties.id, onclick);
              }
              if (callback) {
                context.runSafeWithoutClone(callback);
              }
            })
            .catch(error => {
              context.withLastError(error, caller, () => {
                if (callback) {
                  context.runSafeWithoutClone(callback);
                }
              });
            });
          return createProperties.id;
        },

        update(id, updateProperties) {
          const { onclick } = updateProperties;
          if (onclick && !context.extension.persistentBackground) {
            throw new ExtensionError(
              `Property "onclick" cannot be used in menus.update, replace with an "onClicked" event listener.`
            );
          }
          delete updateProperties.onclick;
          return context.childManager
            .callParentAsyncFunction("menusInternal.update", [
              id,
              updateProperties,
            ])
            .then(() => {
              if (onclick) {
                onClickedProp.setListener(id, onclick);
              } else if (onclick === null) {
                onClickedProp.unsetListenerFromAnyContext(id);
              }
              // else onclick is not set so it should not be changed.
            });
        },

        remove(id) {
          onClickedProp.unsetListenerFromAnyContext(id);
          return context.childManager.callParentAsyncFunction(
            "menusInternal.remove",
            [id]
          );
        },

        removeAll() {
          onClickedProp.deleteAllListenersFromExtension();

          return context.childManager.callParentAsyncFunction(
            "menusInternal.removeAll",
            []
          );
        },

        onClicked: new EventManager({
          context,
          name: "menus.onClicked",
          // Parent event already resets idle if needed, no need to do it here.
          resetIdleOnEvent: false,
          register: fire => {
            const listener = (info, tab) => {
              withHandlingUserInput(context.contentWindow, () =>
                fire.sync(info, tab)
              );
            };

            const event = context.childManager.getParentEvent(
              "menusInternal.onClicked"
            );
            event.addListener(listener);
            return () => {
              event.removeListener(listener);
            };
          },
        }).api(),
      },
    };

    const result = {};
    if (context.extension.hasPermission("menus")) {
      result.menus = api.menus;
    }
    if (context.extension.hasPermission("contextMenus")) {
      result.contextMenus = api.menus;
    }
    return result;
  }
};
