/*
 * Copyright (C) 2015 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.phone.settings;

import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.view.MenuItem;

import com.android.phone.R;

public class AccessibilitySettingsActivity extends PreferenceActivity {

   @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        getActionBar().setTitle(R.string.accessibility_settings_activity_title);

        /*PRIZE-Add-DialerV8-wangzhong-2017_7_19-start*/
        getActionBar().setElevation(this.getResources().getDimensionPixelOffset(R.dimen.prize_elevation_top));
        /*PRIZE-Add-DialerV8-wangzhong-2017_7_19-end*/

        getFragmentManager().beginTransaction().replace(
                android.R.id.content, new AccessibilitySettingsFragment()).commit();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
