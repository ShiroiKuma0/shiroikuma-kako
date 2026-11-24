/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.compose.base.fork

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog as M3AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedButton as M3OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import mozilla.components.compose.base.badge.BadgedIcon
import mozilla.components.compose.base.theme.AcornForkOverrides
import mozilla.components.compose.base.theme.AcornTheme
import mozilla.components.compose.base.theme.ForkIconRole
import mozilla.components.compose.base.theme.ForkMenuStyle
import mozilla.components.compose.base.theme.ForkPillStyle
import mozilla.components.compose.base.theme.ForkSwitchStyle
import mozilla.components.ui.icons.R as iconsR

/*
 * Fork (白い熊 火狐): the shared widgets of the black-yellow look, drawn from the styles in
 * AcornForkOverrides. The compose-base Button, TextButton, Switch and DropdownMenu call these
 * when the fork theme is on, so every screen that uses those gets the look by itself.
 */

private const val DISABLED_ALPHA = 0.38f

/** Which pill a button is. */
enum class ForkPillKind {
    /** Any action that is not the main one: thin frame, normal weight. */
    REGULAR,

    /** The main action of its block: thick frame, bold label. */
    MAIN,

    /** A deleting action that is not the main one: thin, in the delete colour. */
    DELETE,

    /** A deleting main action: thick and bold, in the delete colour. */
    MAIN_DELETE,
}

/**
 * Set around the button slots of a dialog: `true` in the confirm slot, `false` in the dismiss
 * slot, `null` anywhere else. A text button reads it to know whether it is the main pill.
 */
val LocalForkMainAction = staticCompositionLocalOf<Boolean?> { null }

/** The frame colour of a pill of [kind]. */
fun ForkPillStyle.frameOf(kind: ForkPillKind): Color =
    when (kind) {
        ForkPillKind.REGULAR -> frame
        ForkPillKind.MAIN -> mainFrame
        ForkPillKind.DELETE,
        ForkPillKind.MAIN_DELETE -> delete
    }

/** The label colour of a pill of [kind]. */
fun ForkPillStyle.labelOf(kind: ForkPillKind): Color =
    when (kind) {
        ForkPillKind.REGULAR -> label
        ForkPillKind.MAIN -> mainLabel
        ForkPillKind.DELETE,
        ForkPillKind.MAIN_DELETE -> delete
    }

/** The frame width of a pill of [kind]. */
fun ForkPillStyle.widthOf(kind: ForkPillKind): Dp =
    when (kind) {
        ForkPillKind.MAIN,
        ForkPillKind.MAIN_DELETE -> thick
        ForkPillKind.REGULAR,
        ForkPillKind.DELETE -> thin
    }

/** Whether a pill of [kind] has a bold label. */
fun ForkPillKind.isMain(): Boolean = this == ForkPillKind.MAIN || this == ForkPillKind.MAIN_DELETE

/** The label weight of a pill of [kind]. */
fun ForkPillKind.weight(): FontWeight = if (isMain()) FontWeight.Bold else FontWeight.Normal

/**
 * The pill button: fully round, an outline, the ground showing through, never filled.
 *
 * @param onClick Invoked when the pill is pressed.
 * @param style The colours and widths.
 * @param modifier [Modifier] to be applied to the pill.
 * @param kind Which pill this is.
 * @param enabled A disabled pill keeps its shape, frame and content at 38 %.
 * @param contentPadding Padding inside the frame.
 * @param content The label, and an icon where there is one. The label must take its weight from
 *   [ForkPillKind.weight] when it sets a text style of its own.
 */
@Composable
fun ForkPillButton(
    onClick: () -> Unit,
    style: ForkPillStyle,
    modifier: Modifier = Modifier,
    kind: ForkPillKind = ForkPillKind.REGULAR,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    val frame = style.frameOf(kind)
    val label = style.labelOf(kind)
    M3OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = CircleShape,
        colors =
            ButtonDefaults.outlinedButtonColors(
                containerColor = style.ground,
                contentColor = label,
                disabledContainerColor = style.ground,
                disabledContentColor = label.copy(alpha = DISABLED_ALPHA),
            ),
        border =
            BorderStroke(
                width = style.widthOf(kind),
                color = if (enabled) frame else frame.copy(alpha = DISABLED_ALPHA),
            ),
        contentPadding = contentPadding,
        content = content,
    )
}

/**
 * The outlined switch: a round outline with the ground showing through and a solid dot inside
 * it, right when on and left when off. Nothing is ever filled.
 *
 * @param checked Whether the switch is on.
 * @param onCheckedChange Invoked when the switch is pressed; `null` makes it a picture only.
 * @param style The four colours.
 * @param modifier [Modifier] to be applied to the switch.
 * @param enabled A disabled switch is drawn at 38 %.
 * @param compact The smaller form, for a row of a menu.
 */
@Composable
fun ForkSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    style: ForkSwitchStyle,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    compact: Boolean = false,
) {
    val width = if (compact) 44.dp else 52.dp
    val height = if (compact) 24.dp else 28.dp
    val position by
        animateFloatAsState(
            targetValue = if (checked) 1f else 0f,
            animationSpec = tween(durationMillis = 180, easing = LinearOutSlowInEasing),
            label = "forkSwitch",
        )
    val interaction = remember { MutableInteractionSource() }
    val toggle =
        if (onCheckedChange != null) {
            Modifier.minimumInteractiveComponentSize()
                .toggleable(
                    value = checked,
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled,
                    role = Role.Switch,
                    onValueChange = onCheckedChange,
                )
        } else {
            Modifier
        }
    Canvas(modifier = modifier.then(toggle).size(width, height).alpha(if (enabled) 1f else DISABLED_ALPHA)) {
        val stroke = 2.dp.toPx()
        val track = lerp(style.off, style.on, position)
        drawRoundRect(
            color = track,
            topLeft = Offset(stroke / 2f, stroke / 2f),
            size = Size(size.width - stroke, size.height - stroke),
            cornerRadius = CornerRadius((size.height - stroke) / 2f),
            style = Stroke(width = stroke),
        )
        val radius = size.height * 0.46f / 2f
        val gap = (size.height - radius * 2f) / 2f
        val left = gap + radius
        val right = size.width - gap - radius
        drawCircle(
            color = lerp(style.offDot, style.onDot, position),
            radius = radius,
            center = Offset(left + (right - left) * position, size.height / 2f),
        )
    }
}

/**
 * The icon of a menu row that has none of its own, told from [name]: the row's test tag, the entry
 * name of its label, or both. `null` when the name tells nothing.
 */
@DrawableRes
@Suppress("CyclomaticComplexMethod")
fun forkMenuIconForName(name: String): Int? {
    val n = name.lowercase()
    fun has(vararg words: String) = words.any { it in n }
    return when {
        has("delete", "remove", "clear", "trash", "erase") -> iconsR.drawable.mozac_ic_delete_24
        has("share", "send") -> iconsR.drawable.mozac_ic_share_android_24
        has("copy") -> iconsR.drawable.mozac_ic_copy_24
        has("edit", "rename") -> iconsR.drawable.mozac_ic_edit_24
        has("private") -> iconsR.drawable.mozac_ic_private_mode_24
        has("bookmark") -> iconsR.drawable.mozac_ic_bookmark_24
        has("download") -> iconsR.drawable.mozac_ic_download_24
        has("sync", "refresh", "reload") -> iconsR.drawable.mozac_ic_arrow_clockwise_24
        has("settings", "manage", "customi") -> iconsR.drawable.mozac_ic_settings_24
        has("select") -> iconsR.drawable.mozac_ic_select_all_24
        has("close", "dismiss", "cancel") -> iconsR.drawable.mozac_ic_cross_24
        has("history", "recently") -> iconsR.drawable.mozac_ic_history_24
        has("sort") -> iconsR.drawable.mozac_ic_sort_24
        has("search", "find") -> iconsR.drawable.mozac_ic_search_24
        has("shortcut", "pin") -> iconsR.drawable.mozac_ic_pin_24
        has("collection") -> iconsR.drawable.mozac_ic_collection_24
        has("move") -> iconsR.drawable.mozac_ic_folder_arrow_right_24
        has("print") -> iconsR.drawable.mozac_ic_print_24
        has("save") -> iconsR.drawable.mozac_ic_save_24
        has("password", "login") -> iconsR.drawable.mozac_ic_login_24
        has("account", "sign") -> iconsR.drawable.mozac_ic_avatar_circle_24
        has("link", "url") -> iconsR.drawable.mozac_ic_link_24
        has("tab", "open") -> iconsR.drawable.mozac_ic_tab_24
        has("add", "new") -> iconsR.drawable.mozac_ic_plus_24
        else -> null
    }
}

/**
 * An action row of a menu: an outlined pill as wide as the menu, its icon inside at the left,
 * coloured by what the row does, and a › at the right when the row opens something.
 *
 * @param label The row's label.
 * @param onClick Invoked when the row is pressed; a row with neither this nor [onLongClick] cannot be pressed.
 * @param style The menu colours.
 * @param modifier [Modifier] to be applied to the row.
 * @param icon The row's icon; a row without one keeps the icon column when [iconColumn] is set.
 * @param role What the row does, which colours the icon.
 * @param opens Whether the row opens a screen, a dialog, a picker or a submenu.
 * @param destructive A deleting row lends the delete colour to frame, icon and label.
 * @param enabled A disabled row is drawn at 38 %.
 * @param supportingText A second, smaller line under the label.
 * @param iconColumn Whether rows of this menu have an icon column at all.
 * @param iconTint Overrides the colour of the icon; [Color.Unspecified] draws it as it is, for an
 *   icon that is a picture (a favicon, an extension's own icon).
 * @param trailing Content at the right end of the pill, before the ›.
 * @param highlighted Whether the icon carries a notification dot.
 * @param onLongClick Invoked when the row is long-pressed.
 * @param framed `false` for a line that only tells something: the same layout, without the frame.
 */
@Composable
fun ForkMenuPillRow(
    label: String,
    onClick: (() -> Unit)?,
    style: ForkMenuStyle,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    role: ForkIconRole = ForkIconRole.VIEW,
    opens: Boolean = false,
    destructive: Boolean = false,
    enabled: Boolean = true,
    supportingText: String? = null,
    iconColumn: Boolean = true,
    iconTint: Color? = null,
    trailing: (@Composable () -> Unit)? = null,
    highlighted: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    framed: Boolean = true,
) {
    val frame = if (destructive) style.delete else style.rowFrame
    val ink = if (destructive) style.delete else style.label
    val iconColor = iconTint ?: if (destructive) style.delete else style.icon(role)
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 3.dp)
                .alpha(if (enabled) 1f else DISABLED_ALPHA)
                .heightIn(min = 42.dp)
                .clip(CircleShape)
                .then(if (framed) Modifier.border(1.25.dp, frame, CircleShape) else Modifier)
                .then(
                    if (onClick != null || onLongClick != null) {
                        Modifier.combinedClickable(
                            interactionSource = interaction,
                            indication = ripple(color = ink),
                            enabled = enabled,
                            role = Role.Button,
                            onLongClick = onLongClick,
                            onClick = { onClick?.invoke() },
                        )
                    } else {
                        Modifier
                    }
                )
                .padding(horizontal = 14.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            if (highlighted) {
                BadgedIcon(
                    painter = icon,
                    isHighlighted = true,
                    modifier = Modifier.size(24.dp),
                    containerColor = style.delete,
                    tint = iconColor,
                )
            } else {
                Icon(painter = icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = iconColor)
            }
            Spacer(Modifier.width(12.dp))
        } else if (iconColumn) {
            Spacer(Modifier.width(36.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = ink,
                style = AcornTheme.typography.subtitle1,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (supportingText != null) {
                Text(
                    text = supportingText,
                    color = ink.copy(alpha = 0.78f),
                    style = AcornTheme.typography.caption,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            CompositionLocalProvider(LocalContentColor provides ink) { trailing() }
        }
        if (opens) {
            Spacer(Modifier.width(8.dp))
            Text(text = "›", color = ink, fontSize = 22.sp)
        }
    }
}

/**
 * A toggle row of a menu: plain, no pill, 36 dp tall, its icon and label lined up with the
 * contents of the pills above, and the outlined switch at the right, on the pills' right edge.
 *
 * @param label The row's label.
 * @param checked Whether the row is on.
 * @param onCheckedChange Invoked when the row is pressed.
 * @param style The menu colours.
 * @param modifier [Modifier] to be applied to the row.
 * @param icon The row's icon; a row without one keeps the icon column when [iconColumn] is set.
 * @param role What the row does, which colours the icon.
 * @param enabled A disabled row is drawn at 38 %.
 * @param iconColumn Whether rows of this menu have an icon column at all.
 * @param iconTint Overrides the colour of the icon; [Color.Unspecified] draws it as it is.
 * @param trailing Content between the label and the switch.
 * @param supportingText A second, smaller line under the label; the row grows to hold it.
 */
@Composable
fun ForkMenuToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    style: ForkMenuStyle,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    role: ForkIconRole = ForkIconRole.VIEW,
    enabled: Boolean = true,
    iconColumn: Boolean = true,
    iconTint: Color? = null,
    trailing: (@Composable () -> Unit)? = null,
    supportingText: String? = null,
) {
    val switches = AcornForkOverrides.switches
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .alpha(if (enabled) 1f else DISABLED_ALPHA)
                .heightIn(min = 36.dp)
                .toggleable(
                    value = checked,
                    interactionSource = interaction,
                    indication = ripple(color = style.label),
                    enabled = enabled,
                    role = Role.Switch,
                    onValueChange = onCheckedChange,
                )
                .padding(start = 22.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                painter = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = iconTint ?: style.icon(role),
            )
            Spacer(Modifier.width(12.dp))
        } else if (iconColumn) {
            Spacer(Modifier.width(36.dp))
        }
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                text = label,
                color = style.label,
                style = AcornTheme.typography.subtitle1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (supportingText != null) {
                Text(
                    text = supportingText,
                    color = style.label.copy(alpha = 0.78f),
                    style = AcornTheme.typography.caption,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            CompositionLocalProvider(LocalContentColor provides style.label) { trailing() }
            Spacer(Modifier.width(8.dp))
        }
        if (switches != null) {
            ForkSwitch(checked = checked, onCheckedChange = null, style = switches, compact = true)
        }
    }
}

/** The divider between two groups of a menu: a clear 1 dp line whose ends meet the pill edges. */
@Composable
fun ForkMenuDivider(style: ForkMenuStyle, modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .height(1.dp)
                .clip(CircleShape)
                .border(1.dp, style.divider, CircleShape),
    )
}

/** The plain, unframed line a menu opens with: a name, a count, a time. */
@Composable
fun ForkMenuHeader(text: String, style: ForkMenuStyle, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = style.header,
        style = AcornTheme.typography.caption,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 6.dp),
    )
}

/**
 * Drop-in for Material's `AlertDialog`: the same arguments, so a call site changes its import and
 * nothing else. With the fork theme on, the panel takes the dialog roles and a frame, the confirm
 * slot is marked as the main action and the dismiss slot as a regular one, which is what makes a
 * text button in them the thick or the thin pill.
 */
@Composable
fun AlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    shape: Shape = AlertDialogDefaults.shape,
    containerColor: Color = AlertDialogDefaults.containerColor,
    iconContentColor: Color = AlertDialogDefaults.iconContentColor,
    titleContentColor: Color = AlertDialogDefaults.titleContentColor,
    textContentColor: Color = AlertDialogDefaults.textContentColor,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    properties: DialogProperties = DialogProperties(),
) {
    val fork = AcornForkOverrides.dialogs
    if (fork == null) {
        M3AlertDialog(
            onDismissRequest = onDismissRequest,
            confirmButton = confirmButton,
            modifier = modifier,
            dismissButton = dismissButton,
            icon = icon,
            title = title,
            text = text,
            shape = shape,
            containerColor = containerColor,
            iconContentColor = iconContentColor,
            titleContentColor = titleContentColor,
            textContentColor = textContentColor,
            tonalElevation = tonalElevation,
            properties = properties,
        )
        return
    }
    M3AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = { CompositionLocalProvider(LocalForkMainAction provides true) { confirmButton() } },
        modifier = modifier.border(fork.frameWidth, fork.frame, shape),
        dismissButton =
            dismissButton?.let { dismiss ->
                { CompositionLocalProvider(LocalForkMainAction provides false) { dismiss() } }
            },
        icon = icon,
        title = title,
        text = text,
        shape = shape,
        containerColor = fork.ground,
        iconContentColor = fork.title,
        titleContentColor = fork.title,
        textContentColor = fork.text,
        tonalElevation = 0.dp,
        properties = properties,
    )
}
