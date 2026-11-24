/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.compose.browser.toolbar.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import mozilla.components.compose.base.fork.ForkMenuDivider
import mozilla.components.compose.base.fork.ForkMenuHeader
import mozilla.components.compose.base.fork.ForkMenuPillRow
import mozilla.components.compose.base.fork.forkMenuIconForName
import mozilla.components.compose.base.modifier.thenConditional
import mozilla.components.compose.base.theme.AcornForkOverrides
import mozilla.components.compose.base.theme.AcornTheme
import mozilla.components.compose.base.theme.ForkIconRole
import mozilla.components.compose.base.theme.ForkMenuStyle
import mozilla.components.compose.base.theme.forkIconRoleForName
import mozilla.components.compose.browser.toolbar.store.BrowserToolbarInteraction
import mozilla.components.compose.browser.toolbar.store.BrowserToolbarInteraction.BrowserToolbarEvent
import mozilla.components.compose.browser.toolbar.store.BrowserToolbarInteraction.BrowserToolbarMenu
import mozilla.components.compose.browser.toolbar.store.BrowserToolbarInteraction.CombinedEventAndMenu
import mozilla.components.compose.browser.toolbar.store.BrowserToolbarMenuItem
import mozilla.components.compose.browser.toolbar.store.BrowserToolbarMenuItem.BrowserToolbarMenuButton
import mozilla.components.compose.browser.toolbar.store.BrowserToolbarMenuItem.BrowserToolbarMenuButton.ContentDescription.StringContentDescription
import mozilla.components.compose.browser.toolbar.store.BrowserToolbarMenuItem.BrowserToolbarMenuButton.ContentDescription.StringResContentDescription
import mozilla.components.compose.browser.toolbar.store.BrowserToolbarMenuItem.BrowserToolbarMenuButton.Icon.DrawableIcon
import mozilla.components.compose.browser.toolbar.store.BrowserToolbarMenuItem.BrowserToolbarMenuButton.Icon.DrawableResIcon
import mozilla.components.compose.browser.toolbar.store.BrowserToolbarMenuItem.BrowserToolbarMenuButton.Text.StringResText
import mozilla.components.compose.browser.toolbar.store.BrowserToolbarMenuItem.BrowserToolbarMenuButton.Text.StringText
import mozilla.components.compose.browser.toolbar.store.BrowserToolbarMenuItem.BrowserToolbarMenuAccentDivider
import mozilla.components.compose.browser.toolbar.store.BrowserToolbarMenuItem.BrowserToolbarMenuDivider

@Stable
internal fun BrowserToolbarInteraction.toMenuItems(): List<BrowserToolbarMenuItem> =
    when (this) {
        is BrowserToolbarMenu -> items()
        is CombinedEventAndMenu -> menu.items()
        else -> emptyList()
    }

@Composable
@Suppress("LongMethod")
internal fun menuItemComposable(
    source: BrowserToolbarMenuItem,
    onInteraction: (BrowserToolbarEvent) -> Unit,
): @Composable () -> Unit {
    return when (source) {
        is BrowserToolbarMenuButton -> {
            // Fork: the 白い熊 火狐 UI draws the row as a full-width pill with a role-coloured icon.
            val fork = AcornForkOverrides.menus
            if (fork != null) {
                return { ForkToolbarMenuRow(source, fork, onInteraction) }
            }

            @Composable {
                val contentDescription = source.contentDescription()

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier.background(MaterialTheme.colorScheme.surfaceBright)
                            .thenConditional(
                                Modifier.clickable(
                                    role = Role.Button,
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication =
                                        ripple(
                                            bounded = true,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        ),
                                    onClick = { source.onClick?.let { onInteraction(it) } },
                                )
                            ) {
                                source.onClick != null
                            }
                            .clearAndSetSemantics {
                                this.contentDescription = contentDescription
                            }
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .padding(horizontal = 16.dp),
                ) {
                    when (source.icon) {
                        is DrawableIcon -> {
                            Image(
                                painter = rememberDrawablePainter(source.icon.drawable),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                contentScale = ContentScale.Crop,
                                colorFilter =
                                    when (source.icon.shouldTint) {
                                        true -> ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
                                        else -> null
                                    },
                            )
                        }
                        is DrawableResIcon -> {
                            Icon(
                                painter = painterResource(source.icon.resourceId),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        null -> {}
                    }

                    if (source.icon != null) {
                        Spacer(modifier = Modifier.width(20.dp))
                    }

                    Text(
                        text = source.text(),
                        modifier = Modifier.fillMaxSize().wrapContentSize(Alignment.CenterStart),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        style = AcornTheme.typography.subtitle1,
                    )
                }
            }
        }

        is BrowserToolbarMenuDivider -> {
            @Composable {
                // Fork: the clear group divider of the 白い熊 火狐 UI.
                val fork = AcornForkOverrides.menus
                if (fork != null) ForkMenuDivider(style = fork) else HorizontalDivider()
            }
        }

        is BrowserToolbarMenuAccentDivider -> {
            @Composable {
                val fork = AcornForkOverrides.menus
                if (fork != null) {
                    ForkMenuDivider(style = fork)
                } else {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}

/**
 * Fork: a toolbar menu button as the 白い熊 火狐 UI draws it. A button nothing happens on is the
 * plain header line of its menu; an icon that is a picture (a favicon, an extension's own icon)
 * keeps its colours.
 */
@Composable
private fun ForkToolbarMenuRow(
    source: BrowserToolbarMenuButton,
    style: ForkMenuStyle,
    onInteraction: (BrowserToolbarEvent) -> Unit,
) {
    val contentDescription = source.contentDescription()
    val label = source.text()
    val onClick = source.onClick
    if (onClick == null) {
        ForkMenuHeader(text = label, style = style)
        return
    }

    val resources = LocalContext.current.resources
    fun entryName(id: Int) = runCatching { resources.getResourceEntryName(id) }.getOrDefault("")
    val textName = (source.text as? StringResText)?.let { entryName(it.resourceId) }.orEmpty()
    val icon = source.icon
    val iconName = (icon as? DrawableResIcon)?.let { entryName(it.resourceId) }.orEmpty()
    val painter =
        when (icon) {
            is DrawableIcon -> rememberDrawablePainter(icon.drawable)
            is DrawableResIcon -> painterResource(icon.resourceId)
            null -> forkMenuIconForName(textName)?.let { painterResource(it) }
        }
    val role = forkIconRoleForName(iconName).takeIf { it != ForkIconRole.VIEW } ?: forkIconRoleForName(textName)

    ForkMenuPillRow(
        label = label,
        onClick = { onInteraction(onClick) },
        style = style,
        modifier = Modifier.semantics { this.contentDescription = contentDescription },
        icon = painter,
        role = role,
        opens = label.endsWith("…") || label.endsWith("..."),
        destructive = role == ForkIconRole.DELETE,
        iconTint = if (icon is DrawableIcon && !icon.shouldTint) Color.Unspecified else null,
    )
}

@Composable
@ReadOnlyComposable
private fun BrowserToolbarMenuButton.text() =
    when (text) {
        is StringText -> text.text
        is StringResText -> stringResource(text.resourceId)
    }

@Composable
@ReadOnlyComposable
private fun BrowserToolbarMenuButton.contentDescription() =
    when (contentDescription) {
        is StringContentDescription -> contentDescription.text
        is StringResContentDescription -> stringResource(contentDescription.resourceId)
    }
