/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.google.android.material.color;

import android.content.Context;
import android.content.res.loader.ResourcesLoader;
import android.os.Build;
import androidx.annotation.Keep;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import java.util.Map;

/**
 * Fork (白い熊 火狐): the one class the fork keeps in Material's package. Material's colour-table
 * creator — it builds an in-memory resource table that overrides colour resources by id — is
 * package-private; this hands it to org.mozilla.fenix.kako.KakoPalette.
 */
@Keep
public final class KakoColorLoaderBridge {
    private KakoColorLoaderBridge() {}

    /** A loader that gives every colour resource id in the map its new ARGB value, or null. */
    @Nullable
    @RequiresApi(Build.VERSION_CODES.R)
    public static ResourcesLoader create(Context context, Map<Integer, Integer> colors) {
        return ColorResourcesLoaderCreator.create(context, colors);
    }
}
