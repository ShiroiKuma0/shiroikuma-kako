/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import mozilla.components.lib.crash.CrashReporter
import mozilla.components.lib.crash.runtimetagproviders.BuildRuntimeTagProvider
import mozilla.components.lib.crash.runtimetagproviders.EnvironmentRuntimeProvider
import mozilla.components.lib.crash.runtimetagproviders.ExperimentDataRuntimeTagProvider
import mozilla.components.lib.crash.runtimetagproviders.VersionInfoProvider
import mozilla.components.lib.crash.service.CrashReporterService
import mozilla.components.lib.crash.service.socorro.MozillaSocorroService
import mozilla.components.lib.crash.store.CrashReportOption
import mozilla.components.support.utils.ext.packageManagerCompatHelper
import org.mozilla.fenix.HomeActivity
import org.mozilla.fenix.R
import org.mozilla.fenix.crashes.CrashFactCollector
import org.mozilla.fenix.crashes.NimbusExperimentDataProvider
import org.mozilla.fenix.crashes.ReleaseRuntimeTagProvider
import org.mozilla.fenix.crashes.crashReportOption
import org.mozilla.fenix.perf.lazyMonitored
import org.mozilla.fenix.utils.Settings
import org.mozilla.geckoview.BuildConfig.MOZ_APP_BUILDID
import org.mozilla.geckoview.BuildConfig.MOZ_APP_VENDOR
import org.mozilla.geckoview.BuildConfig.MOZ_APP_VERSION
import org.mozilla.geckoview.BuildConfig.MOZ_UPDATE_CHANNEL

/**
 * Component group for crash reporting. 白い熊 火狐 collects no telemetry, so there is no
 * metrics controller here.
 */
class Analytics(
    private val context: Context,
    private val settings: Settings,
    private val nimbusComponents: NimbusComponents,
) {
    val crashReporter: CrashReporter by lazyMonitored {
        val services = mutableListOf<CrashReporterService>()
        val distributionId = "Mozilla"

        // The name "Fenix" here matches the product name on Socorro and is unrelated to the actual app name:
        // https://bugzilla.mozilla.org/show_bug.cgi?id=1523284
        val socorroService = MozillaSocorroService(
            context,
            appName = "Fenix",
            vendor = MOZ_APP_VENDOR,
            releaseChannel = MOZ_UPDATE_CHANNEL,
            distributionId = distributionId,
        )
        services.add(socorroService)

        val intent = Intent(context, HomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val crashReportingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE
        } else {
            0 // No flags. Default behavior.
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            crashReportingIntentFlags,
        )

        CrashReporter(
            context = context,
            services = services,
            // 白い熊 火狐 ships without Glean, so there is no telemetry crash service.
            telemetryServices = emptyList(),
            shouldPrompt = CrashReporter.Prompt.ALWAYS,
            promptConfiguration = CrashReporter.PromptConfiguration(
                appName = context.getString(R.string.app_name),
                organizationName = "Mozilla",
            ),
            enabled = true,
            nonFatalCrashIntent = pendingIntent,
            useLegacyReporting = settings.crashReportOption() != CrashReportOption.Auto,
            runtimeTagProviders = listOf(
                ReleaseRuntimeTagProvider(),
                BuildRuntimeTagProvider(context.versionInfoProvider),
                EnvironmentRuntimeProvider(),
                ExperimentDataRuntimeTagProvider(
                    NimbusExperimentDataProvider(
                        nimbusApi = lazyMonitored { nimbusComponents.sdk },
                    ),
                ),
            ),
        )
    }

    val crashFactCollector: CrashFactCollector by lazyMonitored {
        CrashFactCollector(crashReporter)
    }

}

private val Context.versionInfoProvider: VersionInfoProvider
    get() {
        val packageInfo = applicationContext.packageManagerCompatHelper.getPackageInfoCompat(
            applicationContext.packageName,
            0,
        )
        return VersionInfoProvider.fromPackageInfo(packageInfo)
    }
