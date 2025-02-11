/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
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
 * limitations under the License
 */
package com.android.contacts.common;

import android.content.Context;
import android.content.CursorLoader;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;

import com.mediatek.contacts.util.ContactsPortableUtils;
import com.mediatek.contacts.util.ContactsPreferencesUtils;

import java.util.List;

/**
 * Used to create {@link CursorLoader}s to load different groups of
 * {@link com.android.contacts.list.ContactTileView}.
 */
public final class ContactTileLoaderFactory {

    public final static int CONTACT_ID = 0;
    public final static int DISPLAY_NAME = 1;
    public final static int STARRED = 2;
    public final static int PHOTO_URI = 3;
    public final static int LOOKUP_KEY = 4;
    public final static int CONTACT_PRESENCE = 5;
    public final static int CONTACT_STATUS = 6;

    // Only used for StrequentPhoneOnlyLoader
    public final static int PHONE_NUMBER = 5;
    public final static int PHONE_NUMBER_TYPE = 6;
    public final static int PHONE_NUMBER_LABEL = 7;
    public final static int IS_DEFAULT_NUMBER = 8;
    public final static int PINNED = 9;
    // The _ID field returned for strequent items actually contains data._id instead of
    // contacts._id because the query is performed on the data table. In order to obtain the
    // contact id for strequent items, we thus have to use Phone.contact_id instead.
    public final static int CONTACT_ID_FOR_DATA = 10;
    public final static int DISPLAY_NAME_ALTERNATIVE = 11;

    private static final String[] COLUMNS_INTERNAL = new String[] {
        Contacts._ID, // ..........................................0
        Contacts.DISPLAY_NAME, // .................................1
        Contacts.STARRED, // ......................................2
        Contacts.PHOTO_URI, // ....................................3
        Contacts.LOOKUP_KEY, // ...................................4
        Contacts.CONTACT_PRESENCE, // .............................5
        Contacts.CONTACT_STATUS, // ...............................6
    };
    private static final String[] COLUMNS;

    static {
        List<String> projectionList = Lists.newArrayList(COLUMNS_INTERNAL);

        if (ContactsPortableUtils.MTK_PHONE_BOOK_SUPPORT) {
            /// M: Add some columns for Contacts extensions. @{
            projectionList.add(Contacts.INDICATE_PHONE_SIM);   //7
            projectionList.add(Contacts.IS_SDN_CONTACT);       //8
            /// @}
        }

        COLUMNS = projectionList.toArray(new String[projectionList.size()]);
    }

    /**
     * Projection used for the {@link Contacts#CONTENT_STREQUENT_URI}
     * query when {@link ContactsContract#STREQUENT_PHONE_ONLY} flag
     * is set to true. The main difference is the lack of presence
     * and status data and the addition of phone number and label.
     */
    @VisibleForTesting
    public static final String[] COLUMNS_PHONE_ONLY_INTERNAL = new String[] {
        Contacts._ID, // ..........................................0
        Contacts.DISPLAY_NAME_PRIMARY, // .........................1
        Contacts.STARRED, // ......................................2
        Contacts.PHOTO_URI, // ....................................3
        Contacts.LOOKUP_KEY, // ...................................4
        Phone.NUMBER, // ..........................................5
        Phone.TYPE, // ............................................6
        Phone.LABEL, // ...........................................7
        Phone.IS_SUPER_PRIMARY, //.................................8
        Contacts.PINNED, // .......................................9
        Phone.CONTACT_ID, //.......................................10
        Contacts.DISPLAY_NAME_ALTERNATIVE, // .....................11
    };
    public static final String[] COLUMNS_PHONE_ONLY;

    static {
        List<String> projectionList = Lists.newArrayList(COLUMNS_PHONE_ONLY_INTERNAL);

        if (ContactsPortableUtils.MTK_PHONE_BOOK_SUPPORT) {
            /// M: Add some columns for Contacts extensions. @{
            projectionList.add(Contacts.INDICATE_PHONE_SIM);   //12
            projectionList.add(Contacts.IS_SDN_CONTACT);       //13
            /// @}
        }

        COLUMNS_PHONE_ONLY = projectionList.toArray(new String[projectionList.size()]);
    }

    private static final String STARRED_ORDER = Contacts.DISPLAY_NAME+" COLLATE NOCASE ASC";

    public static CursorLoader createStrequentLoader(Context context) {
        /** M: Bug Fix for CR ALPS00319593 @{ */
        CursorLoader cursorLoader;
        if (ContactsPortableUtils.MTK_PHONE_BOOK_SUPPORT) {
            cursorLoader = new CursorLoader(context,
                    Contacts.CONTENT_STREQUENT_URI, COLUMNS,
                    Contacts.INDICATE_PHONE_SIM + "=-1 ", null, null);
        } else {
            cursorLoader = new CursorLoader(context,
                    Contacts.CONTENT_STREQUENT_URI, COLUMNS,
                    null, null, null);
        }

        ContactsPreferencesUtils.fixSortOrderByPreference(cursorLoader, DISPLAY_NAME, context);
        return cursorLoader;
        /** @} */
    }

    public static CursorLoader createStrequentPhoneOnlyLoader(Context context) {
        Uri uri = Contacts.CONTENT_STREQUENT_URI.buildUpon()
                .appendQueryParameter(ContactsContract.STREQUENT_PHONE_ONLY, "true").build();

        /** M: Bug Fix for CR ALPS00319593 @{ */
        CursorLoader cursorLoader;
        if (ContactsPortableUtils.MTK_PHONE_BOOK_SUPPORT) {
            cursorLoader = new CursorLoader(context, uri, COLUMNS_PHONE_ONLY,
                    Contacts.INDICATE_PHONE_SIM + "=-1 ",
                    null, null);
        } else {
            cursorLoader = new CursorLoader(context, uri, COLUMNS_PHONE_ONLY,
                    null, null, null);
        }
        ContactsPreferencesUtils.fixSortOrderByPreference(cursorLoader, DISPLAY_NAME, context);
        return cursorLoader;
        /** @} */
    }

    public static CursorLoader createStarredLoader(Context context) {
        CursorLoader cursorLoader = new CursorLoader(context, Contacts.CONTENT_URI, COLUMNS,
                Contacts.STARRED + "=?",
                new String[] { "1" }, Contacts.DISPLAY_NAME + " ASC");
        ContactsPreferencesUtils.fixSortOrderByPreference(cursorLoader, DISPLAY_NAME, context);
        return cursorLoader;
    }

    public static CursorLoader createFrequentLoader(Context context) {
        CursorLoader cursorLoader = new CursorLoader(context, Contacts.CONTENT_FREQUENT_URI,
                COLUMNS, Contacts.STARRED
                + "=?", new String[] { "0" }, null);
        ContactsPreferencesUtils.fixSortOrderByPreference(cursorLoader, DISPLAY_NAME, context);
        return cursorLoader;
    }
}
