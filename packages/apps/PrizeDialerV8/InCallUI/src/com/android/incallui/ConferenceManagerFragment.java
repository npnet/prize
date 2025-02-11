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
 * limitations under the License
 */

package com.android.incallui;

import android.app.ActionBar;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
/// M: add for Volte. @{
import android.widget.ImageButton;
import android.widget.Toast;
/// @}

import com.android.contacts.common.ContactPhotoManager;
import com.android.dialer.R;
/// M: add for Volte. @{
import com.mediatek.incallui.volte.AddMemberScreenController;
/// @}
import java.util.List;

/**
 * Fragment that allows the user to manage a conference call.
 */
public class ConferenceManagerFragment
        extends BaseFragment<ConferenceManagerPresenter,
                ConferenceManagerPresenter.ConferenceManagerUi>
        implements ConferenceManagerPresenter.ConferenceManagerUi {

    private static final String KEY_IS_VISIBLE = "key_conference_is_visible";

    private ListView mConferenceParticipantList;
    private int mActionBarElevation;
    private ContactPhotoManager mContactPhotoManager;
    private LayoutInflater mInflater;
    private ConferenceParticipantListAdapter mConferenceParticipantListAdapter;
    private boolean mIsVisible;
    private boolean mIsRecreating;

    @Override
    public ConferenceManagerPresenter createPresenter() {
        return new ConferenceManagerPresenter();
    }

    @Override
    public ConferenceManagerPresenter.ConferenceManagerUi getUi() {
        return this;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            mIsRecreating = true;
            mIsVisible = savedInstanceState.getBoolean(KEY_IS_VISIBLE);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        final View parent =
                inflater.inflate(R.layout.conference_manager_fragment, container, false);

        mConferenceParticipantList = (ListView) parent.findViewById(R.id.participantList);
        mContactPhotoManager =
                ContactPhotoManager.getInstance(getActivity().getApplicationContext());
        mActionBarElevation =
                (int) getResources().getDimension(R.dimen.incall_action_bar_elevation);
        mInflater = LayoutInflater.from(getActivity().getApplicationContext());

        /// M: for volte @{
        initAddMemberButton(parent);
        /// @}

        return parent;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mIsRecreating) {
            onVisibilityChanged(mIsVisible);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(KEY_IS_VISIBLE, mIsVisible);
        super.onSaveInstanceState(outState);
    }

    public void onVisibilityChanged(boolean isVisible) {
        mIsVisible = isVisible;
        ActionBar actionBar = getActivity().getActionBar();
        if (isVisible) {
            actionBar.setTitle(R.string.manageConferenceLabel);
            actionBar.setElevation(mActionBarElevation);
            actionBar.setHideOffset(0);
            actionBar.show();

            final CallList calls = CallList.getInstance();
            getPresenter().init(getActivity(), calls);
            // Request focus on the list of participants for accessibility purposes.  This ensures
            // that once the list of participants is shown, the first participant is announced.
            mConferenceParticipantList.requestFocus();
        } else {
            actionBar.setElevation(0);
            actionBar.setHideOffset(actionBar.getHeight());
            /// M: fix ALPS01899613, hide action bar subjectively. @{
            actionBar.hide();
            /// @}

            /// M: for volte @{
            AddMemberScreenController.getInstance().dismissAddMemberDialog();
            /// @}

        }
    }

    @Override
    public boolean isFragmentVisible() {
        return isVisible();
    }

    @Override
    public void update(Context context, List<Call> participants, boolean parentCanSeparate) {
        if (mConferenceParticipantListAdapter == null) {
            mConferenceParticipantListAdapter = new ConferenceParticipantListAdapter(
                    mConferenceParticipantList, context, mInflater, mContactPhotoManager);

            mConferenceParticipantList.setAdapter(mConferenceParticipantListAdapter);
        }
        mConferenceParticipantListAdapter.updateParticipants(participants, parentCanSeparate);
    }

    @Override
    public void refreshCall(Call call) {
        mConferenceParticipantListAdapter.refreshCall(call);
    }

    /// ----------------------------------------Mediatek--------------------------------------
    /// M: for volte. add member part. @{
    private View mFloatingAddMemberButtonContainer;
    private ImageButton mFloatingAddMemberButton;

    /**
     * M: init add member button.
     * @param view
     */
    private void initAddMemberButton(View view) {
        mFloatingAddMemberButtonContainer = view.findViewById(
                R.id.floating_add_member_action_button_container);
        mFloatingAddMemberButton = (ImageButton) view
                .findViewById(R.id.floating_add_member_action_button);
        mFloatingAddMemberButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(this, "onVolteAddConfMemberClicked()...");
                getPresenter().showAddMemberScreen();
            }
        });
        mFloatingAddMemberButtonContainer.setVisibility(View.GONE);
    }

    /**
     * M: show add member button if necessary.
     */
    @Override
    public void showAddMemberButton(boolean visible) {
        mFloatingAddMemberButtonContainer.setVisibility(visible ? View.VISIBLE : View.GONE);
    }
    /// @}
}
