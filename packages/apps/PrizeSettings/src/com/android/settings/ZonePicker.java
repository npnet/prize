/*
 * Copyright (C) 2006 The Android Open Source Project
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

import android.annotation.NonNull;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.ListFragment;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.SimpleAdapter;

import com.android.settingslib.datetime.ZoneGetter;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import android.widget.FrameLayout;

/**
 * The class displaying a list of time zones that match a filter string
 * such as "Africa", "Europe", etc. Choosing an item from the list will set
 * the time zone. Pressing Back without choosing from the list will not
 * result in a change in the time zone setting.
 */
public class ZonePicker extends ListFragment {
    private static final String TAG = "ZonePicker";

    public interface ZoneSelectionListener {
        // You can add any argument if you really need it...
        void onZoneSelected(TimeZone tz);
    }

    private static final int MENU_TIMEZONE = Menu.FIRST+1;
    private static final int MENU_ALPHABETICAL = Menu.FIRST;

    private boolean mSortedByTimezone;

    private SimpleAdapter mTimezoneSortedAdapter;
    private SimpleAdapter mAlphabeticalAdapter;

    private ZoneSelectionListener mListener;

    /**
     * Constructs an adapter with TimeZone list. Sorted by TimeZone in default.
     *
     * @param sortedByName use Name for sorting the list.
     */
    public static SimpleAdapter constructTimezoneAdapter(Context context,
            boolean sortedByName) {
        return constructTimezoneAdapter(context, sortedByName,
                R.layout.date_time_setup_custom_list_item_2);
    }

    /**
     * Constructs an adapter with TimeZone list. Sorted by TimeZone in default.
     *
     * @param sortedByName use Name for sorting the list.
     */
    public static SimpleAdapter constructTimezoneAdapter(Context context,
            boolean sortedByName, int layoutId) {
        final String[] from = new String[] {ZoneGetter.KEY_DISPLAYNAME, ZoneGetter.KEY_GMT};
        final int[] to = new int[] {android.R.id.text1, android.R.id.text2};

        final String sortKey = (sortedByName ? ZoneGetter.KEY_DISPLAYNAME : ZoneGetter.KEY_OFFSET);
        final MyComparator comparator = new MyComparator(sortKey);
        final List<Map<String, Object>> sortedList = ZoneGetter.getZonesList(context);
        Collections.sort(sortedList, comparator);
        final SimpleAdapter adapter = new SimpleAdapter(context,
                sortedList,
                layoutId,
                from,
                to);

        return adapter;
    }

    /**
     * Searches {@link TimeZone} from the given {@link SimpleAdapter} object, and returns
     * the index for the TimeZone.
     *
     * @param adapter SimpleAdapter constructed by
     * {@link #constructTimezoneAdapter(Context, boolean)}.
     * @param tz TimeZone to be searched.
     * @return Index for the given TimeZone. -1 when there's no corresponding list item.
     * returned.
     */
    public static int getTimeZoneIndex(SimpleAdapter adapter, TimeZone tz) {
        final String defaultId = tz.getID();
        final int listSize = adapter.getCount();
        for (int i = 0; i < listSize; i++) {
            // Using HashMap<String, Object> induces unnecessary warning.
            final HashMap<?,?> map = (HashMap<?,?>)adapter.getItem(i);
            final String id = (String)map.get(ZoneGetter.KEY_ID);
            if (defaultId.equals(id)) {
                // If current timezone is in this list, move focus to it
                return i;
            }
        }
        return -1;
    }

    /**
     * @param item one of items in adapters. The adapter should be constructed by
     * {@link #constructTimezoneAdapter(Context, boolean)}.
     * @return TimeZone object corresponding to the item.
     */
    public static TimeZone obtainTimeZoneFromItem(Object item) {
        return TimeZone.getTimeZone((String)((Map<?, ?>)item).get(ZoneGetter.KEY_ID));
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        final Activity activity = getActivity();
        mTimezoneSortedAdapter = constructTimezoneAdapter(activity, false);
        mAlphabeticalAdapter = constructTimezoneAdapter(activity, true);

        // Sets the adapter
        setSorting(true);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        final View view = super.onCreateView(inflater, container, savedInstanceState);
		FrameLayout mFrameLayout = (FrameLayout) view.findViewById(com.android.internal.R.id.listContainer);
        final ListView list = (ListView) view.findViewById(android.R.id.list);
        Utils.forcePrepareCustomPreferencesList(container, view, list, false);
		prizeSetLayoutParams(list);
		mFrameLayout.setBackgroundResource(R.color.settings_layout_background);
        return view;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        menu.add(0, MENU_ALPHABETICAL, 0, R.string.zone_list_menu_sort_alphabetically)
            .setIcon(android.R.drawable.ic_menu_sort_alphabetically);
        menu.add(0, MENU_TIMEZONE, 0, R.string.zone_list_menu_sort_by_timezone)
            .setIcon(R.drawable.ic_menu_3d_globe);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        if (mSortedByTimezone) {
            menu.findItem(MENU_TIMEZONE).setVisible(false);
            menu.findItem(MENU_ALPHABETICAL).setVisible(true);
        } else {
            menu.findItem(MENU_TIMEZONE).setVisible(true);
            menu.findItem(MENU_ALPHABETICAL).setVisible(false);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {

            case MENU_TIMEZONE:
                setSorting(true);
                return true;

            case MENU_ALPHABETICAL:
                setSorting(false);
                return true;

            default:
                return false;
        }
    }

    public void setZoneSelectionListener(ZoneSelectionListener listener) {
        mListener = listener;
    }

    private void setSorting(boolean sortByTimezone) {
        final SimpleAdapter adapter =
                sortByTimezone ? mTimezoneSortedAdapter : mAlphabeticalAdapter;
        setListAdapter(adapter);
        mSortedByTimezone = sortByTimezone;
        final int defaultIndex = getTimeZoneIndex(adapter, TimeZone.getDefault());
        if (defaultIndex >= 0) {
            setSelection(defaultIndex);
        }
    }

    @Override
    public void onListItemClick(ListView listView, View v, int position, long id) {
        // Ignore extra clicks
        if (!isResumed()) return;
        final Map<?, ?> map = (Map<?, ?>)listView.getItemAtPosition(position);
        final String tzId = (String) map.get(ZoneGetter.KEY_ID);

        // Update the system timezone value
        final Activity activity = getActivity();
        final AlarmManager alarm = (AlarmManager) activity.getSystemService(Context.ALARM_SERVICE);
        alarm.setTimeZone(tzId);
        final TimeZone tz = TimeZone.getTimeZone(tzId);
        if (mListener != null) {
            mListener.onZoneSelected(tz);
        } else {
            getActivity().onBackPressed();
        }
    }

    private static class MyComparator implements Comparator<Map<?, ?>> {
        private String mSortingKey;

        public MyComparator(String sortingKey) {
            mSortingKey = sortingKey;
        }

        public void setSortingKey(String sortingKey) {
            mSortingKey = sortingKey;
        }

        public int compare(Map<?, ?> map1, Map<?, ?> map2) {
            Object value1 = map1.get(mSortingKey);
            Object value2 = map2.get(mSortingKey);

            /*
             * This should never happen, but just in-case, put non-comparable
             * items at the end.
             */
            if (!isComparable(value1)) {
                return isComparable(value2) ? 1 : 0;
            } else if (!isComparable(value2)) {
                return -1;
            }

            return ((Comparable) value1).compareTo(value2);
        }

        private boolean isComparable(Object value) {
            return (value != null) && (value instanceof Comparable);
        }
    }

	public void prizeSetLayoutParams(ListView listview){
		 if(listview.getLayoutParams() instanceof FrameLayout.LayoutParams){
			FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,FrameLayout.LayoutParams.WRAP_CONTENT);
			int top = getResources().getDimensionPixelSize(R.dimen.prize_preferencefragment_card_magintop);
			int left = getResources().getDimensionPixelSize(R.dimen.prize_preferencefragment_card_maginleft);
		    layoutParams.setMargins(left,top,left,top);
			listview.setLayoutParams(layoutParams);
			listview.setBackgroundResource(R.drawable.toponepreferencecategory_selector);
			listview.setDivider(getResources().getDrawable(R.drawable.prize_sleep_gesture_lines,null));
			listview.setDividerHeight(1);
         }
	}
}
