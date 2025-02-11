/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceScreen;
import android.util.Log;

import com.android.internal.logging.MetricsProto.MetricsEvent;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.search.Indexable;
import com.android.settings.search.SearchIndexableRaw;

import java.util.ArrayList;
import java.util.List;

public class WallpaperTypeSettings extends SettingsPreferenceFragment implements Indexable {
	
	private static final String TAG = "WallpaperTypeSettings";

    @Override
    protected int getMetricsCategory() {
        return MetricsEvent.WALLPAPER_TYPE;
    }

    @Override
    protected int getHelpResource() {
        return R.string.help_uri_wallpaper;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.wallpaper_settings);
    	populateWallpaperTypes();
    }

    private void populateWallpaperTypes() {
        // Search for activities that satisfy the ACTION_SET_WALLPAPER action
        final Intent intent = new Intent(Intent.ACTION_SET_WALLPAPER);
        final PackageManager pm = getPackageManager();
        final List<ResolveInfo> rList = pm.queryIntentActivities(intent,
                PackageManager.MATCH_DEFAULT_ONLY);

        final PreferenceScreen parent = getPreferenceScreen();
        parent.setOrderingAsAdded(false);
        // Add Preference items for each of the matching activities
        for (ResolveInfo info : rList) {
            Preference pref = new Preference(getPrefContext());
            /* Modify by zhudaopeng at 2016-11-23 Start */
            // pref.setLayoutResource(R.layout.preference_wallpaper_type);
            /* Modify by zhudaopeng at 2016-11-23 End */
            Intent prefIntent = new Intent(intent);
            prefIntent.setComponent(new ComponentName(
                    info.activityInfo.packageName, info.activityInfo.name));
            pref.setIntent(prefIntent);
            CharSequence label = info.loadLabel(pm);
            if (label == null) label = info.activityInfo.packageName;
            pref.setTitle(label);
            /* Modify by zhudaopeng at 2016-11-23 Start */
            // pref.setIcon(info.loadIcon(pm));
            pref.setIcon(null);
            /* Modify by zhudaopeng at 2016-11-23 End */
            parent.addPreference(pref);
        }
    } 
    
    public static final SearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
        new BaseSearchIndexProvider() {
            @Override
            public List<SearchIndexableRaw> getRawDataToIndex(Context context, boolean enabled) {
                final List<SearchIndexableRaw> result = new ArrayList<SearchIndexableRaw>();

                final Intent intent = new Intent(Intent.ACTION_SET_WALLPAPER);
                final PackageManager pm = context.getPackageManager();
                final List<ResolveInfo> rList = pm.queryIntentActivities(intent,
                        PackageManager.MATCH_DEFAULT_ONLY);

                // Add indexable data for each of the matching activities
                for (ResolveInfo info : rList) {
                    CharSequence label = info.loadLabel(pm);
                    if (label == null) label = info.activityInfo.packageName;

                    SearchIndexableRaw data = new SearchIndexableRaw(context);
                    data.title = label.toString();
                    data.screenTitle = context.getResources().getString(
                            R.string.wallpaper_settings_fragment_title);
                    data.intentAction = Intent.ACTION_SET_WALLPAPER;
                    data.intentTargetPackage = info.activityInfo.packageName;
                    data.intentTargetClass = info.activityInfo.name;
                    result.add(data);
                }

                return result;
            }
        };
}
