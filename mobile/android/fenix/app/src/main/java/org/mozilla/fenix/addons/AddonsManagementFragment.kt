/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.addons

import android.content.Context
import android.graphics.Typeface
import android.graphics.fonts.FontStyle.FONT_WEIGHT_MEDIUM
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mozilla.components.concept.engine.webextension.InstallationMethod
import mozilla.components.feature.addons.Addon
import mozilla.components.feature.addons.AddonManager
import mozilla.components.feature.addons.AddonManagerException
import mozilla.components.feature.addons.ui.AddonsManagerAdapter
import mozilla.components.support.base.log.logger.Logger
import org.mozilla.fenix.R
import org.mozilla.fenix.databinding.FragmentAddOnsManagementBinding
import org.mozilla.fenix.e2e.SystemInsetsPaddedFragment
import org.mozilla.fenix.ext.components
import org.mozilla.fenix.ext.openToBrowser
import org.mozilla.fenix.ext.requireComponents
import org.mozilla.fenix.ext.runIfFragmentIsAttached
import org.mozilla.fenix.ext.showToolbar
import org.mozilla.fenix.settings.SupportUtils.AMO_HOMEPAGE_FOR_ANDROID
import org.mozilla.fenix.theme.ThemeManager
import java.io.File
import java.io.IOException
import com.google.android.material.R as materialR
import mozilla.components.feature.addons.R as addonsR

// Fork: identifiers for the "install add-on from file" entry.
private const val KAKO_INSTALL_FROM_FILE_ITEM_ID = 0x4B41_4B4F
private const val KAKO_XPI_STAGING_DIR = "kako-xpi"
private const val KAKO_XPI_STAGING_NAME = "install.xpi"

/**
 * Fragment use for managing add-ons.
 */
@Suppress("TooManyFunctions", "LargeClass")
class AddonsManagementFragment : Fragment(R.layout.fragment_add_ons_management), SystemInsetsPaddedFragment {

    private var binding: FragmentAddOnsManagementBinding? = null

    private var addons: List<Addon> = emptyList()

    private var adapter: AddonsManagerAdapter? = null

    // Fork: the file picker for "install from file". Registered as a field because
    // registerForActivityResult must be called before the fragment reaches STARTED.
    private val kakoXpiPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { kakoInstallAddonFromFile(it) }
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentAddOnsManagementBinding.bind(view)
        bindRecyclerView()
        kakoAddInstallFromFileMenu()
    }

    override fun onResume() {
        super.onResume()
        showToolbar(getString(R.string.preferences_extensions))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // letting go of the resources to avoid memory leak.
        adapter = null
        binding = null
    }

    @Suppress("CognitiveComplexMethod")
    private fun bindRecyclerView() {
        val managementView = AddonsManagementView(
            navController = findNavController(),
            onInstallButtonClicked = ::installAddon,
            onMoreAddonsButtonClicked = ::openAMO,
            onLearnMoreClicked = { link, addon ->
                binding?.root?.openLearnMoreLink(link, addon)
            },
        )

        val recyclerView = binding?.addOnsList
        recyclerView?.layoutManager = LinearLayoutManager(requireContext())
        val shouldRefresh = adapter != null

        lifecycleScope.launch(IO) {
            try {
                addons = requireContext().components.addonManager.getAddons()
                lifecycleScope.launch(Dispatchers.Main) {
                    runIfFragmentIsAttached {
                        if (!shouldRefresh) {
                            adapter = AddonsManagerAdapter(
                                addonsManagerDelegate = managementView,
                                addons = addons,
                                style = createAddonStyle(requireContext()),
                                store = requireComponents.core.store,
                            )
                        }
                        binding?.addOnsProgressBar?.isVisible = false
                        binding?.addOnsEmptyMessage?.isVisible = false

                        recyclerView?.adapter = adapter
                        recyclerView?.accessibilityDelegate = object : View.AccessibilityDelegate() {
                            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                                super.onInitializeAccessibilityNodeInfo(host, info)

                                adapter?.let {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                        info.collectionInfo = AccessibilityNodeInfo.CollectionInfo(
                                            it.itemCount,
                                            1,
                                            false,
                                        )
                                    } else {
                                        @Suppress("DEPRECATION")
                                        info.collectionInfo = AccessibilityNodeInfo.CollectionInfo.obtain(
                                            it.itemCount,
                                            1,
                                            false,
                                        )
                                    }
                                }
                            }
                        }

                        if (shouldRefresh) {
                            adapter?.updateAddons(addons)
                        }
                    }
                }
            } catch (e: AddonManagerException) {
                lifecycleScope.launch(Dispatchers.Main) {
                    runIfFragmentIsAttached {
                        binding?.let {
                            showSnackBar(
                                it.root,
                                getString(addonsR.string.mozac_feature_addons_failed_to_load_extensions),
                            )
                        }
                        binding?.addOnsProgressBar?.isVisible = false
                        binding?.addOnsEmptyMessage?.isVisible = true
                    }
                }
            }
        }
    }

    private fun createAddonStyle(context: Context): AddonsManagerAdapter.Style {
        val sectionsTypeFace = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Typeface.create(Typeface.DEFAULT, FONT_WEIGHT_MEDIUM, false)
        } else {
            Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        return AddonsManagerAdapter.Style(
            sectionsTextColor = ThemeManager.resolveAttribute(materialR.attr.colorOnSurface, context),
            addonNameTextColor = ThemeManager.resolveAttribute(materialR.attr.colorOnSurface, context),
            addonSummaryTextColor = ThemeManager.resolveAttribute(materialR.attr.colorOnSurfaceVariant, context),
            sectionsTypeFace = sectionsTypeFace,
            addonAllowPrivateBrowsingLabelDrawableRes = R.drawable.ic_add_on_private_browsing_label,
        )
    }

    @VisibleForTesting
    internal fun provideAddonManager(): AddonManager {
        return requireContext().components.addonManager
    }

    /**
     * Fork: a toolbar entry for installing an .xpi that is already on the device.
     *
     * Stock Fenix can only install what the configured AMO collection offers, which
     * means an add-on has to be publicly listed on AMO to be installable at all. Our
     * own extensions are signed but unlisted, so this is how they get on the phone:
     * adb push the .xpi to /sdcard/tmp/, then pick it here.
     *
     * The menu item is added in code rather than through a menu resource, to keep the
     * fork's footprint to one file.
     */
    private fun kakoAddInstallFromFileMenu() {
        requireActivity().addMenuProvider(
            object : MenuProvider {
                override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                    menu.add(
                        Menu.NONE,
                        KAKO_INSTALL_FROM_FILE_ITEM_ID,
                        Menu.NONE,
                        getString(R.string.kako_install_addon_from_file),
                    ).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
                }

                override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                    if (menuItem.itemId != KAKO_INSTALL_FROM_FILE_ITEM_ID) return false
                    // Some file providers do not tag .xpi as x-xpinstall, so */* is
                    // offered alongside it rather than hiding the file 白い熊 wants.
                    kakoXpiPicker.launch(arrayOf("application/x-xpinstall", "*/*"))
                    return true
                }
            },
            viewLifecycleOwner,
            Lifecycle.State.RESUMED,
        )
    }

    /**
     * Fork: installs the .xpi behind [uri].
     *
     * The engine accepts a local file: URI -- GeckoView's WebExtensionController.install
     * documents "a remote https: URI or a local file: or resource: URI" -- but it cannot
     * read the content: URI the picker hands back, and it needs read access. Copying into
     * the app's own cache directory satisfies both without any storage permission, and the
     * copy is deleted once the install settles.
     *
     * Signing still applies: the release GeckoView requires add-ons signed by Mozilla, so
     * an unlisted-but-AMO-signed .xpi installs and a wholly unsigned one does not.
     */
    private fun kakoInstallAddonFromFile(uri: Uri) {
        binding?.addonProgressOverlay?.overlayCardView?.visibility = View.VISIBLE

        viewLifecycleOwner.lifecycleScope.launch {
            val staged = withContext(IO) { kakoStageXpi(uri) }

            if (staged == null) {
                binding?.addonProgressOverlay?.overlayCardView?.visibility = View.GONE
                kakoShowInstallError(getString(R.string.kako_install_addon_from_file_read_failed))
                return@launch
            }

            provideAddonManager().installAddon(
                url = Uri.fromFile(staged).toString(),
                installationMethod = InstallationMethod.FROM_FILE,
                onSuccess = { addon ->
                    staged.delete()
                    runIfFragmentIsAttached {
                        adapter?.updateAddon(addon)
                        binding?.addonProgressOverlay?.overlayCardView?.visibility = View.GONE
                    }
                },
                onError = { error ->
                    staged.delete()
                    runIfFragmentIsAttached {
                        binding?.addonProgressOverlay?.overlayCardView?.visibility = View.GONE
                        kakoShowInstallError(
                            error.message
                                ?: getString(R.string.kako_install_addon_from_file_failed),
                        )
                    }
                },
            )
        }
    }

    /** Fork: copies the picked document into cache so the engine can read it. */
    private fun kakoStageXpi(uri: Uri): File? = try {
        val dir = File(requireContext().cacheDir, KAKO_XPI_STAGING_DIR).apply { mkdirs() }
        // Stale copies from an install that never reported back must not accumulate.
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, KAKO_XPI_STAGING_NAME)
        requireContext().contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        if (target.length() > 0) target else null
    } catch (e: IOException) {
        Logger("AddonsManagementFragment").error("kako: staging the .xpi failed", e)
        null
    }

    private fun kakoShowInstallError(message: String) {
        binding?.root?.let {
            Snackbar.make(it, message, Snackbar.LENGTH_LONG).show()
        }
    }

    internal fun installAddon(addon: Addon) {
        binding?.addonProgressOverlay?.overlayCardView?.visibility = View.VISIBLE

        if (requireComponents.appStore.state.mode.isPrivate) {
            binding?.addonProgressOverlay?.overlayCardView?.setBackgroundColor(
                ContextCompat.getColor(
                    requireContext(),
                    R.color.fx_mobile_private_layer_color_3,
                ),
            )
        }

        val installOperation = provideAddonManager().installAddon(
            url = addon.downloadUrl,
            installationMethod = InstallationMethod.MANAGER,
            onSuccess = {
                runIfFragmentIsAttached {
                    adapter?.updateAddon(it)
                    binding?.addonProgressOverlay?.overlayCardView?.visibility = View.GONE
                }
            },
            onError = { _ ->
                binding?.addonProgressOverlay?.overlayCardView?.visibility = View.GONE
            },
        )
        binding?.addonProgressOverlay?.cancelButton?.setOnClickListener {
            lifecycleScope.launch(Dispatchers.Main) {
                val safeBinding = binding
                // Hide the installation progress overlay once cancellation is successful.
                if (installOperation.cancel().await()) {
                    safeBinding?.addonProgressOverlay?.overlayCardView?.visibility = View.GONE
                }
            }
        }
    }

    private fun openAMO() {
        findNavController().openToBrowser()
        requireComponents.useCases.fenixBrowserUseCases.loadUrlOrSearch(
            searchTermOrURL = AMO_HOMEPAGE_FOR_ANDROID,
            newTab = true,
        )
    }
}
