/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.gallery3d.filtershow.category;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.android.gallery3d.R;
import com.android.gallery3d.filtershow.FilterShowActivity;
import com.android.gallery3d.filtershow.imageshow.MasterImage;
import com.android.gallery3d.filtershow.state.StatePanel;
import com.android.gallery3d.filtershow.ui.BottomView;
import com.prize.sticker.StickerTool;
import com.prize.util.OptionConfig;

public class MainPanel extends Fragment {

    private static final String LOGTAG = "MainPanel";

    private LinearLayout mMainView;
	/* PRIZE-photoeditor UI-wanzhijuan-2015-11-20-start*/
    private BottomView looksButton;
    private BottomView bordersButton;
    private BottomView geometryButton;
    private BottomView filtersButton;
	/** PRIZE-watermark-wanzhijuan-2016-1-21-start **/
    private BottomView stickerButton;
	/** PRIZE-watermark-wanzhijuan-2016-1-21-end **/
	/* PRIZE-photoeditor UI-wanzhijuan-2015-11-20-end*/

    public static final String FRAGMENT_TAG = "MainPanel";
    public static final int LOOKS = 0;
    public static final int BORDERS = 1;
    public static final int GEOMETRY = 2;
    public static final int FILTERS = 3;
    public static final int VERSIONS = 4;
	/** PRIZE-watermark-wanzhijuan-2016-1-21-start **/
    public static final int STICKER = 5;
	/** PRIZE-watermark-wanzhijuan-2016-1-21-end **/

    private int mCurrentSelected = -1;
    private int mPreviousToggleVersions = -1;

    private void selection(int position, boolean value) {
        if (value) {
            FilterShowActivity activity = (FilterShowActivity) getActivity();
            activity.setCurrentPanel(position);
        }
        switch (position) {
            case LOOKS: {
                looksButton.setSelected(value);
                break;
            }
            case BORDERS: {
                bordersButton.setSelected(value);
                break;
            }
            case GEOMETRY: {
                geometryButton.setSelected(value);
                break;
            }
            case FILTERS: {
                filtersButton.setSelected(value);
                break;
            }
			/** PRIZE-watermark-wanzhijuan-2016-1-21-start **/
            case STICKER: {
            	stickerButton.setSelected(value);
            	break;
            }
			/** PRIZE-watermark-wanzhijuan-2016-1-21-end **/
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mMainView != null) {
            if (mMainView.getParent() != null) {
                ViewGroup parent = (ViewGroup) mMainView.getParent();
                parent.removeView(mMainView);
            }
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        mMainView = (LinearLayout) inflater.inflate(
                R.layout.filtershow_main_panel, null, false);

        looksButton = (BottomView) mMainView.findViewById(R.id.fxButton);
        bordersButton = (BottomView) mMainView.findViewById(R.id.borderButton);
        geometryButton = (BottomView) mMainView.findViewById(R.id.geometryButton);
        filtersButton = (BottomView) mMainView.findViewById(R.id.colorsButton);
        
		/** PRIZE-watermark-wanzhijuan-2016-1-21-start **/
        stickerButton = (BottomView) mMainView.findViewById(R.id.stickerButton);
        if (OptionConfig.SHOW_WATERMARK) {
        	stickerButton.setVisibility(View.VISIBLE);
        } else {
        	stickerButton.setVisibility(View.GONE);
        }
		/** PRIZE-watermark-wanzhijuan-2016-1-21-end **/

        looksButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPanel(LOOKS);
            }
        });
        bordersButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPanel(BORDERS);
            }
        });
        geometryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPanel(GEOMETRY);
            }
        });
        filtersButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPanel(FILTERS);
            }
        });
        
		/** PRIZE-watermark-wanzhijuan-2016-1-21-start **/
        stickerButton.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				showPanel(STICKER);
			}
		});
		/** PRIZE-watermark-wanzhijuan-2016-1-21-end **/

        FilterShowActivity activity = (FilterShowActivity) getActivity();
        showImageStatePanel(activity.isShowingImageStatePanel());
        showPanel(activity.getCurrentPanel());
        return mMainView;
    }

    private boolean isRightAnimation(int newPos) {
        if (newPos < mCurrentSelected) {
            return false;
        }
        return true;
    }

    private void setCategoryFragment(CategoryPanel category, boolean fromRight) {
        FragmentTransaction transaction = getChildFragmentManager().beginTransaction();
        if (fromRight) {
            transaction.setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_right);
        } else {
            transaction.setCustomAnimations(R.anim.slide_in_left, R.anim.slide_out_left);
        }
        transaction.replace(R.id.category_panel_container, category, CategoryPanel.FRAGMENT_TAG);
        transaction.commitAllowingStateLoss();
    }

    public void loadCategoryLookPanel(boolean force) {
        if (!force && mCurrentSelected == LOOKS) {
            return;
        }
        boolean fromRight = isRightAnimation(LOOKS);
        selection(mCurrentSelected, false);
        CategoryPanel categoryPanel = new CategoryPanel();
        categoryPanel.setAdapter(LOOKS);
        setCategoryFragment(categoryPanel, fromRight);
        mCurrentSelected = LOOKS;
        selection(mCurrentSelected, true);
    }

    public void loadCategoryBorderPanel() {
        if (mCurrentSelected == BORDERS) {
            return;
        }
        boolean fromRight = isRightAnimation(BORDERS);
        selection(mCurrentSelected, false);
        CategoryPanel categoryPanel = new CategoryPanel();
        categoryPanel.setAdapter(BORDERS);
        setCategoryFragment(categoryPanel, fromRight);
        mCurrentSelected = BORDERS;
        selection(mCurrentSelected, true);
    }

    public void loadCategoryGeometryPanel() {
        if (mCurrentSelected == GEOMETRY) {
            return;
        }
        /// M: [BUG.ADD] @{
        //add to resolve NullPointerException @{
        if (MasterImage.getImage().getPreset() == null) {
            return;
        }
        /// @}
        if (MasterImage.getImage().hasTinyPlanet()) {
            return;
        }
        boolean fromRight = isRightAnimation(GEOMETRY);
        selection(mCurrentSelected, false);
        CategoryPanel categoryPanel = new CategoryPanel();
        categoryPanel.setAdapter(GEOMETRY);
        setCategoryFragment(categoryPanel, fromRight);
        mCurrentSelected = GEOMETRY;
        selection(mCurrentSelected, true);
    }

    public void loadCategoryFiltersPanel() {
        if (mCurrentSelected == FILTERS) {
            return;
        }
        boolean fromRight = isRightAnimation(FILTERS);
        selection(mCurrentSelected, false);
        CategoryPanel categoryPanel = new CategoryPanel();
        categoryPanel.setAdapter(FILTERS);
        setCategoryFragment(categoryPanel, fromRight);
        mCurrentSelected = FILTERS;
        selection(mCurrentSelected, true);
    }
    
	/** PRIZE-watermark-wanzhijuan-2016-1-21-start **/
    public void loadCategorySticker() {
    	if (mCurrentSelected != STICKER) {
    		return;
    	}
    	CategoryPanel categoryPanel = new CategoryPanel();
        categoryPanel.setAdapter(STICKER);
        setCategoryFragment(categoryPanel, false);
    }
	/** PRIZE-watermark-wanzhijuan-2016-1-21-end **/

    public void loadCategoryVersionsPanel() {
        if (mCurrentSelected == VERSIONS) {
            return;
        }
        FilterShowActivity activity = (FilterShowActivity) getActivity();
        activity.updateVersions();
        boolean fromRight = isRightAnimation(VERSIONS);
        selection(mCurrentSelected, false);
        CategoryPanel categoryPanel = new CategoryPanel();
        categoryPanel.setAdapter(VERSIONS);
        setCategoryFragment(categoryPanel, fromRight);
        mCurrentSelected = VERSIONS;
        selection(mCurrentSelected, true);
    }
    
	/** PRIZE-watermark-wanzhijuan-2016-1-21-start **/
    public void loadCategoryStickerPanel() {
    	/*if (mCurrentSelected == STICKER) {
    		return;
    	}*/
    	boolean fromRight = isRightAnimation(STICKER);
        selection(mCurrentSelected, false);
        /*CategoryPanel categoryPanel = new CategoryPanel();
        categoryPanel.setAdapter(STICKER);
        setCategoryFragment(categoryPanel, fromRight);*/
        mCurrentSelected = STICKER;
        FilterShowActivity activity = (FilterShowActivity) getActivity();
        activity.gotoPickSticker();
        selection(mCurrentSelected, true);
    }
	/** PRIZE-watermark-wanzhijuan-2016-1-21-end **/

    public void showPanel(int currentPanel) {
        switch (currentPanel) {
            case LOOKS: {
                loadCategoryLookPanel(false);
                break;
            }
            case BORDERS: {
                loadCategoryBorderPanel();
                break;
            }
            case GEOMETRY: {
                loadCategoryGeometryPanel();
                break;
            }
            case FILTERS: {
                loadCategoryFiltersPanel();
                break;
            }
            case VERSIONS: {
                loadCategoryVersionsPanel();
                break;
            }
			/** PRIZE-watermark-wanzhijuan-2016-1-21-start **/
            case STICKER: {
            	loadCategoryStickerPanel();
            	break;
            }
			/** PRIZE-watermark-wanzhijuan-2016-1-21-end **/
        }
    }

    public void setToggleVersionsPanelButton(ImageButton button) {
        if (button == null) {
            return;
        }
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mCurrentSelected == VERSIONS) {
                    showPanel(mPreviousToggleVersions);
                } else {
                    mPreviousToggleVersions = mCurrentSelected;
                    showPanel(VERSIONS);
                }
            }
        });
    }

    public void showImageStatePanel(boolean show) {
        View container = mMainView.findViewById(R.id.state_panel_container);
        FragmentTransaction transaction = null;
        if (container == null) {
            FilterShowActivity activity = (FilterShowActivity) getActivity();
            container = activity.getMainStatePanelContainer(R.id.state_panel_container);
        } else {
            transaction = getChildFragmentManager().beginTransaction();
        }
        if (container == null) {
            return;
        } else {
            transaction = getFragmentManager().beginTransaction();
        }
        int currentPanel = mCurrentSelected;
        if (show) {
            container.setVisibility(View.VISIBLE);
            StatePanel statePanel = new StatePanel();
            statePanel.setMainPanel(this);
            FilterShowActivity activity = (FilterShowActivity) getActivity();
            activity.updateVersions();
            transaction.replace(R.id.state_panel_container, statePanel, StatePanel.FRAGMENT_TAG);
        } else {
            container.setVisibility(View.GONE);
            Fragment statePanel = getChildFragmentManager().findFragmentByTag(StatePanel.FRAGMENT_TAG);
            if (statePanel != null) {
                transaction.remove(statePanel);
            }
            if (currentPanel == VERSIONS) {
                currentPanel = LOOKS;
            }
        }
        mCurrentSelected = -1;
        showPanel(currentPanel);
        /// M: [BUG.MODIFY] @{
        /* transaction.commit();*/
        // update UI allowing state loss (cause no related state needs saving) @{
        // SDK tells us this invocation (inside Fragment.onCreate()) may happened
        // before Activity finishes creating or after it is paused.
        // A more complete solution is to mark whether the Activity is still alive
        transaction.commitAllowingStateLoss();
        /// @}

    }

    /// M: [BUG.ADD] fix bug: version button does not response when click @{
    public void setPreviousToggleVersion(int version) {
        mPreviousToggleVersions = version;
    }

    public int getPreviousToggleVersion() {
        return mPreviousToggleVersions;
    }
    /// @}
}
