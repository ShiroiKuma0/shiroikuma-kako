/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.menu.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import mozilla.components.compose.base.button.IconButton
import mozilla.components.compose.base.fork.ForkMenuPillRow
import mozilla.components.compose.base.fork.ForkMenuToggleRow
import mozilla.components.compose.base.modifier.thenConditional
import mozilla.components.compose.base.theme.ForkIconRole
import mozilla.components.ui.icons.R as iconsR
import org.mozilla.fenix.R
import org.mozilla.fenix.components.menu.MenuDialogTestTag.WEB_EXTENSION_ITEM
import org.mozilla.fenix.compose.list.IconListItem
import org.mozilla.fenix.compose.list.TextListItem
import org.mozilla.fenix.kako.kakoMenuStyle
import org.mozilla.fenix.theme.FirefoxTheme
import org.mozilla.fenix.utils.DURATION_MS_MAIN_MENU_ITEM

private val MENU_ITEM_HEIGHT_WITHOUT_DESC = 52.dp

private val MENU_ITEM_HEIGHT_WITH_DESC = 56.dp

/**
 * An [IconListItem] wrapper for menu items in a [MenuGroup] with an optional icon at the end.
 *
 * @param label The label in the menu item.
 * @param beforeIconPainter [Painter] used to display an [Icon] before the list item.
 * @param modifier [Modifier] to be applied to the layout.
 * @param labelModifier [Modifier] to be applied to the label.
 * @param beforeIconDescription Content description of the icon.
 * @param isBeforeIconHighlighted Whether or not the menu item should be highlighted with a notification icon.
 * @param description An optional description text below the label.
 * @param maxDescriptionLines An optional maximum number of lines for the description text to span.
 * @param stateDescription Extra content description about state to be added after the label and description.
 * @param state The state of the menu item to display.
 * @param descriptionState The state of menu item description to display.
 * @param onClick Invoked when the user clicks on the item.
 * @param showDivider Whether or not to display a vertical divider line before the [IconButton] at the end.
 * @param afterIconPainter [Painter] used to display an [IconButton] after the list item.
 * @param afterIconDescription Content description of the icon.
 * @param collectionItemInfo [CollectionItemInfo] to be applied to the MenuItem.
 * @param onAfterIconClick Invoked when the user clicks on the icon. An [IconButton] will be displayed if this is
 *   provided. Otherwise, an [Icon] will be displayed.
 * @param iconRole Fork: what the row does, which colours its icon in the 白い熊 火狐 UI.
 * @param opens Fork: whether the row opens a screen, a dialog or a picker, which gives it a ›.
 * @param forkChecked Fork: set for a row that switches something on and off; the 白い熊 火狐 UI
 *   draws it as a plain row with the outlined switch showing this state.
 * @param afterContent Optional Composable for adding UI to the end of the list item.
 */
@Composable
internal fun MenuItem(
    label: String,
    beforeIconPainter: Painter,
    modifier: Modifier = Modifier,
    labelModifier: Modifier = Modifier,
    beforeIconDescription: String? = null,
    isBeforeIconHighlighted: Boolean = false,
    description: String? = null,
    maxDescriptionLines: Int = 2,
    stateDescription: String = "",
    state: MenuItemState = MenuItemState.ENABLED,
    descriptionState: MenuItemState = MenuItemState.ENABLED,
    onClick: (() -> Unit)? = null,
    showDivider: Boolean = false,
    afterIconPainter: Painter? = null,
    afterIconDescription: String? = null,
    collectionItemInfo: CollectionItemInfo? = null,
    onAfterIconClick: (() -> Unit)? = null,
    iconRole: ForkIconRole = ForkIconRole.VIEW,
    opens: Boolean = false,
    forkChecked: Boolean? = null,
    afterContent: (@Composable () -> Unit)? = null,
) {
    val labelTextColor = getLabelTextColor(state = state)
    val descriptionTextColor = getDescriptionTextColor(state = descriptionState)
    val iconTint = getIconTint(state = state)
    val enabled = state != MenuItemState.DISABLED

    var contentDescription = label

    if (description != null) {
        contentDescription = "$contentDescription $description"
    }

    if (stateDescription.isNotEmpty()) {
        contentDescription = "$contentDescription $stateDescription"
    }

    // Fork: the 白い熊 火狐 UI draws the row as a full-width outlined pill with a role-coloured
    // icon, a row that switches something as a plain row with the outlined switch, and a row
    // that only tells something without a frame.
    val fork = kakoMenuStyle()
    if (fork != null && forkChecked != null) {
        ForkMenuToggleRow(
            label = label,
            checked = forkChecked,
            onCheckedChange = { onClick?.invoke() },
            style = fork,
            modifier = modifier,
            icon = beforeIconPainter,
            role = iconRole,
            enabled = enabled,
            supportingText = description,
        )
        return
    }
    if (fork != null) {
        ForkMenuPillRow(
            label = label,
            onClick = onClick,
            style = fork,
            modifier =
                modifier.forkRowSemantics(
                    description = contentDescription,
                    isButton = onClick != null || !enabled,
                    enabled = enabled,
                    itemInfo = collectionItemInfo,
                    click = onClick,
                ),
            icon = beforeIconPainter,
            role = iconRole,
            opens = opens,
            destructive = state == MenuItemState.WARNING,
            enabled = enabled,
            supportingText = description,
            iconTint = if (state == MenuItemState.CRITICAL) Color.Unspecified else null,
            trailing = afterContent ?: forkAfterIcon(afterIconPainter, afterIconDescription, onAfterIconClick),
            highlighted = isBeforeIconHighlighted,
            framed = onClick != null || !enabled,
        )
        return
    }

    IconListItem(
        label = label,
        modifier =
            modifier
                .clearAndSetSemantics {
                    if (onClick != null || state == MenuItemState.DISABLED) {
                        role = Role.Button
                    }
                    this.contentDescription = contentDescription
                    if (collectionItemInfo != null) {
                        this.collectionItemInfo = collectionItemInfo
                    }
                    if (!enabled) {
                        disabled()
                    }
                    if (onClick != null && enabled) {
                        onClick {
                            onClick()
                            true
                        }
                    }
                }
                .wrapContentSize()
                .clip(shape = MaterialTheme.shapes.extraSmall)
                .background(color = MaterialTheme.colorScheme.surfaceBright),
        labelModifier = labelModifier,
        colors =
            ListItemDefaults.colors(
                headlineColor = labelTextColor,
                supportingColor = descriptionTextColor,
            ),
        maxLabelLines = 2,
        description = description,
        maxDescriptionLines = maxDescriptionLines,
        enabled = enabled,
        minHeight =
            if (description != null) {
                MENU_ITEM_HEIGHT_WITH_DESC
            } else {
                MENU_ITEM_HEIGHT_WITHOUT_DESC
            },
        onClick = onClick,
        beforeIconPainter = beforeIconPainter,
        beforeIconDescription = beforeIconDescription,
        beforeIconTint = iconTint,
        isBeforeIconHighlighted = isBeforeIconHighlighted,
        showDivider = showDivider,
        afterIconPainter = afterIconPainter,
        afterIconDescription = afterIconDescription,
        afterIconTint = iconTint,
        onAfterIconClick = onAfterIconClick,
        afterListAction = afterContent,
    )
}

/**
 * An [IconListItem] wrapper for menu items in a [MenuGroup] with an optional icon at the end.
 *
 * @param label The label in the menu item.
 * @param modifier [Modifier] to be applied to the layout.
 * @param description An optional description text below the label.
 * @param maxDescriptionLines An optional maximum number of lines for the description text to span.
 * @param iconPainter [Painter] used to display an [Icon] after the list item.
 * @param enabled Controls the enabled state of the list item. When `false`, the list item will not be clickable.
 * @param onClick Invoked when the user clicks on the item.
 */
@Composable
internal fun MenuTextItem(
    label: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    maxDescriptionLines: Int = 1,
    iconPainter: Painter? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    // Fork: a text row that can be pressed is a pill; one that cannot stays a plain line.
    val fork = kakoMenuStyle()
    if (fork != null && onClick != null) {
        ForkMenuPillRow(
            label = label,
            onClick = onClick,
            style = fork,
            modifier = modifier,
            enabled = enabled,
            supportingText = description,
            trailing = forkAfterIcon(iconPainter, null, null),
        )
        return
    }
    TextListItem(
        label = label,
        maxLabelLines = 2,
        description = description,
        maxDescriptionLines = maxDescriptionLines,
        enabled = enabled,
        minHeight =
            if (description != null) {
                MENU_ITEM_HEIGHT_WITH_DESC
            } else {
                MENU_ITEM_HEIGHT_WITHOUT_DESC
            },
        modifier =
            modifier
                .clip(shape = MaterialTheme.shapes.extraSmall)
                .thenConditional(Modifier.background(color = MaterialTheme.colorScheme.surfaceBright)) {
                    fork == null
                },
        iconPainter = iconPainter,
        onClick = onClick,
    )
}

/**
 * A Web extension menu item used to display an image with a title and a badge.
 *
 * @param label The label in the list item.
 * @param iconPainter [Painter] used to display an [Icon] before the list item.
 * @param iconTint Tint color to be applied on the [Icon].
 * @param enabled Controls the enabled state of the list item. When `false`, the list item will not be clickable.
 * @param badgeText WebExtension badge text.
 * @param index The index of the item within the column.
 * @param onClick Called when the user clicks on the item.
 * @param onLongClick Called when the user long-presses the item.
 * @param onSettingsClick Called when the user clicks on the settings icon.
 */
@Composable
internal fun WebExtensionMenuItem(
    label: String,
    iconPainter: Painter,
    iconTint: Color = Color.Unspecified,
    enabled: Boolean?,
    badgeText: String?,
    index: Int = 0,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onSettingsClick: (() -> Unit)? = null,
) {
    // Fork: an extension's row is a pill like every other; its own icon is a picture and keeps
    // its colours.
    val fork = kakoMenuStyle()
    if (fork != null) {
        ForkMenuPillRow(
            label = label,
            onClick = onClick,
            style = fork,
            modifier =
                Modifier.testTag(WEB_EXTENSION_ITEM)
                    .forkRowSemantics(
                        description = label,
                        isButton = onClick != null,
                        enabled = enabled == true,
                        itemInfo =
                            CollectionItemInfo(
                                rowIndex = index,
                                rowSpan = 1,
                                columnIndex = 0,
                                columnSpan = 1,
                            ),
                        click = onClick,
                        longClick = onLongClick,
                        asResourceId = true,
                    ),
            icon = iconPainter,
            enabled = enabled == true,
            iconTint = iconTint.takeIf { it == Color.Unspecified },
            trailing = { ForkWebExtensionTrailing(badgeText = badgeText, onSettingsClick = onSettingsClick) },
            onLongClick = onLongClick,
        )
        return
    }
    IconListItem(
        label = label,
        enabled = enabled == true,
        beforeIconTint = iconTint,
        beforeIconPainter = iconPainter,
        onClick = onClick,
        onLongClick = onLongClick,
        modifier =
            Modifier.testTag(WEB_EXTENSION_ITEM)
                .clearAndSetSemantics {
                    onClick?.let { role = Role.Button }
                    onLongClick?.let { longClick ->
                        onLongClick {
                            longClick()
                            true
                        }
                    }
                    contentDescription = label
                    collectionItemInfo =
                        CollectionItemInfo(
                            rowIndex = index,
                            rowSpan = 1,
                            columnIndex = 0,
                            columnSpan = 1,
                        )
                    testTagsAsResourceId = true
                }
                .wrapContentSize()
                .clip(shape = MaterialTheme.shapes.extraSmall)
                .background(color = MaterialTheme.colorScheme.surfaceBright),
        afterListAction = {
            Row(
                modifier = Modifier.padding(start = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.End),
            ) {
                if (!badgeText.isNullOrEmpty()) {
                    Badge(badgeText = badgeText)
                }

                if (onSettingsClick != null) {
                    VerticalDivider()

                    IconButton(
                        onClick = onSettingsClick,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            painter = painterResource(iconsR.drawable.mozac_ic_settings_24),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            contentDescription = null,
                        )
                    }
                }
            }
        },
    )
}

@Composable
internal fun MenuBadgeItem(
    label: String,
    description: String,
    badgeText: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val state: MenuItemState =
        if (checked) {
            MenuItemState.ACTIVE
        } else {
            MenuItemState.DISABLED
        }

    // Fork: a row with an on/off badge is a toggle: a plain row with the outlined switch.
    val fork = kakoMenuStyle()
    if (fork != null) {
        ForkMenuToggleRow(
            label = label,
            checked = checked,
            onCheckedChange = { onClick() },
            style = fork,
            modifier = modifier.semantics { stateDescription = badgeText },
            enabled = enabled,
            iconColumn = false,
            supportingText = description,
        )
        return
    }

    Row(
        modifier =
            modifier
                .thenConditional(
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = LocalIndication.current,
                    ) {
                        onClick()
                    }
                ) {
                    enabled
                }
                .thenConditional(Modifier.semantics { disabled() }) { !enabled }
                .semantics { disabled() }
                .clip(shape = MaterialTheme.shapes.extraSmall)
                .background(color = MaterialTheme.colorScheme.surfaceBright)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                modifier = Modifier.defaultMinSize(minHeight = 24.dp).wrapContentHeight(),
                color = getLabelTextColor(state),
                style = FirefoxTheme.typography.body1,
            )

            Text(
                text = description,
                modifier = Modifier.defaultMinSize(minHeight = 20.dp).wrapContentHeight(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = FirefoxTheme.typography.caption,
            )
        }

        Badge(
            badgeText = badgeText,
            state = state,
        )
    }
}

@Composable
internal fun Badge(
    badgeText: String,
    modifier: Modifier = Modifier,
    state: MenuItemState = MenuItemState.ENABLED,
) {
    // Fork: a badge is outlined, never filled, and small enough to sit inside a pill.
    val fork = kakoMenuStyle()
    if (fork != null) {
        Text(
            text = badgeText,
            modifier =
                modifier
                    .clip(CircleShape)
                    .border(FORK_BADGE_FRAME, fork.rowFrame, CircleShape)
                    .padding(horizontal = 10.dp, vertical = 2.dp),
            color = fork.label,
            overflow = TextOverflow.Ellipsis,
            style = FirefoxTheme.typography.headline8,
            maxLines = 1,
        )
        return
    }
    Column(
        modifier =
            modifier
                .clip(CircleShape)
                .background(color = getBadgeColor(state))
                .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = badgeText,
            color = getLabelTextColor(state),
            overflow = TextOverflow.Ellipsis,
            style = FirefoxTheme.typography.headline8,
            maxLines = 1,
        )
    }
}

@Composable
internal fun ExpandableMenuItemAnimation(
    isExpanded: Boolean,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = isExpanded,
        enter =
            expandVertically(
                expandFrom = Alignment.Top,
                animationSpec =
                    tween(
                        durationMillis = DURATION_MS_MAIN_MENU_ITEM,
                        easing = LinearEasing,
                    ),
            ) +
                fadeIn(
                    animationSpec =
                        tween(
                            durationMillis = DURATION_MS_MAIN_MENU_ITEM,
                            easing = LinearEasing,
                        )
                ),
        exit =
            shrinkVertically(
                shrinkTowards = Alignment.Top,
                animationSpec =
                    tween(
                        durationMillis = DURATION_MS_MAIN_MENU_ITEM,
                        easing = LinearEasing,
                    ),
            ) +
                fadeOut(
                    animationSpec =
                        tween(
                            durationMillis = DURATION_MS_MAIN_MENU_ITEM,
                            easing = LinearEasing,
                        )
                ),
    ) {
        Column {
            content()
        }
    }
}

/** Enum containing all the supported state for the menu item. */
enum class MenuItemState {
    /** The menu item is enabled. */
    ENABLED,

    /** The menu item is disabled and is not clickable. */
    DISABLED,

    /** The menu item is highlighted to indicate the feature behind the menu item is active. */
    ACTIVE,

    /** The menu item is highlighted to indicate the feature behind the menu item is destructive. */
    WARNING,

    /** The menu item is highlighted to indicate the feature behind the menu item is critical. */
    CRITICAL,
}

@Composable
private fun getLabelTextColor(state: MenuItemState): Color {
    return when (state) {
        MenuItemState.ACTIVE -> MaterialTheme.colorScheme.tertiary
        MenuItemState.WARNING -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
}

@Composable
private fun getDescriptionTextColor(state: MenuItemState): Color {
    return when (state) {
        MenuItemState.ACTIVE -> MaterialTheme.colorScheme.tertiary
        MenuItemState.WARNING -> MaterialTheme.colorScheme.error
        MenuItemState.DISABLED -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

@Composable
private fun getIconTint(state: MenuItemState): Color {
    return when (state) {
        MenuItemState.ACTIVE -> MaterialTheme.colorScheme.tertiary
        MenuItemState.WARNING -> MaterialTheme.colorScheme.error
        MenuItemState.CRITICAL -> Color.Unspecified
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

@Composable
private fun getBadgeColor(state: MenuItemState): Color {
    return when (state) {
        MenuItemState.ACTIVE -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
}

/** Fork: the frame width of a badge inside a menu row. */
private val FORK_BADGE_FRAME = 1.dp

/**
 * Fork: the semantics of a menu row drawn as a pill -- what upstream sets on the list item it
 * draws instead: one node that reads [description] and carries the row's own actions.
 */
private fun Modifier.forkRowSemantics(
    description: String,
    isButton: Boolean,
    enabled: Boolean,
    itemInfo: CollectionItemInfo?,
    click: (() -> Unit)?,
    longClick: (() -> Unit)? = null,
    asResourceId: Boolean = false,
): Modifier = clearAndSetSemantics {
    if (isButton) {
        role = Role.Button
    }
    contentDescription = description
    if (itemInfo != null) {
        collectionItemInfo = itemInfo
    }
    if (!enabled) {
        disabled()
    }
    if (click != null && enabled) {
        onClick {
            click()
            true
        }
    }
    if (longClick != null && enabled) {
        onLongClick {
            longClick()
            true
        }
    }
    if (asResourceId) {
        testTagsAsResourceId = true
    }
}

/** Fork: the content at the right end of a pill row for upstream's after-icon, or null without one. */
private fun forkAfterIcon(
    painter: Painter?,
    description: String?,
    onClick: (() -> Unit)?,
): (@Composable () -> Unit)? {
    if (painter == null) {
        return null
    }
    val content: @Composable () -> Unit = { ForkAfterIcon(painter, description, onClick) }
    return content
}

/** Fork: the icon at the right end of a pill row: a glyph, and a glyph button when it can be pressed. */
@Composable
private fun ForkAfterIcon(
    painter: Painter,
    description: String?,
    onClick: (() -> Unit)?,
) {
    val ink = LocalContentColor.current
    if (onClick == null) {
        Icon(painter = painter, contentDescription = description, modifier = Modifier.size(24.dp), tint = ink)
        return
    }
    IconButton(
        onClick = onClick,
        contentDescription = description,
        modifier = Modifier.size(32.dp),
    ) {
        Icon(painter = painter, contentDescription = null, tint = ink)
    }
}

/** Fork: what an extension's row shows at its right end: the extension's badge and its settings glyph. */
@Composable
private fun ForkWebExtensionTrailing(
    badgeText: String?,
    onSettingsClick: (() -> Unit)?,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
    ) {
        if (!badgeText.isNullOrEmpty()) {
            Badge(badgeText = badgeText)
        }

        if (onSettingsClick != null) {
            ForkAfterIcon(
                painter = painterResource(iconsR.drawable.mozac_ic_settings_24),
                description = null,
                onClick = onSettingsClick,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun WebExtensionMenuItemPreview() {
    FirefoxTheme {
        Column(modifier = Modifier.background(color = MaterialTheme.colorScheme.surface)) {
            WebExtensionMenuItem(
                label = "label",
                iconPainter = painterResource(iconsR.drawable.mozac_ic_extension_fill_24),
                iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                enabled = true,
                badgeText = "17",
                onClick = {},
                onSettingsClick = {},
            )
            // Web extensions may have multi-colored assets with no tint.
            WebExtensionMenuItem(
                label = "colorful icon",
                iconPainter = painterResource(iconsR.drawable.mozac_ic_shield_slash_multicolor_24),
                iconTint = Color.Unspecified,
                enabled = true,
                badgeText = "17",
                onClick = {},
                onSettingsClick = {},
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun MenuItemPreview() {
    FirefoxTheme {
        Column(modifier = Modifier.background(color = MaterialTheme.colorScheme.surface).padding(16.dp)) {
            MenuGroup {
                for (state in MenuItemState.entries) {
                    MenuItem(
                        label = stringResource(id = R.string.browser_menu_translations),
                        beforeIconPainter = painterResource(id = iconsR.drawable.mozac_ic_translate_24),
                        state = state,
                        onClick = {},
                    )
                }

                for (state in MenuItemState.entries) {
                    MenuItem(
                        label = stringResource(id = R.string.browser_menu_extensions),
                        beforeIconPainter = painterResource(id = iconsR.drawable.mozac_ic_extension_24),
                        state = state,
                        onClick = {},
                        afterIconPainter = painterResource(id = iconsR.drawable.mozac_ic_chevron_right_24),
                    )
                }

                for (state in MenuItemState.entries) {
                    MenuItem(
                        label = stringResource(id = R.string.browser_menu_extensions),
                        beforeIconPainter = painterResource(id = iconsR.drawable.mozac_ic_extension_24),
                        state = state,
                        onClick = {},
                        showDivider = true,
                        afterIconPainter = painterResource(id = iconsR.drawable.mozac_ic_plus_24),
                        onAfterIconClick = {},
                    )
                }
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun MenuBadgeItemPreview() {
    FirefoxTheme {
        Column(
            modifier =
                Modifier.background(color = MaterialTheme.colorScheme.surface)
                    .padding(all = FirefoxTheme.layout.space.static200)
        ) {
            MenuBadgeItem(
                label = stringResource(id = R.string.protection_panel_etp_toggle_label),
                description = stringResource(id = R.string.protection_panel_etp_toggle_enabled_description_2),
                badgeText = stringResource(id = R.string.protection_panel_etp_toggle_on),
                checked = true,
                onClick = {},
            )
        }
    }
}
