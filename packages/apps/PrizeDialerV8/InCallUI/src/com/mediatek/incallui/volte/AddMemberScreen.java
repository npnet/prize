package com.mediatek.incallui.volte;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract.PhoneLookup;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.telephony.PhoneNumberUtils;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.util.Rfc822Tokenizer;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;

import com.android.incallui.Log;
import com.android.internal.app.AlertActivity;
import com.android.internal.app.AlertController;
import com.android.incallui.Call;
import com.android.incallui.CallList;
import com.android.incallui.R;
import com.android.incallui.TelecomAdapter;
import com.mediatek.incallui.recorder.BaseAlertScreen;
import com.mediatek.incallui.volte.AddMemberEditView.AddMemberEditViewAdatper;

public class AddMemberScreen extends BaseAlertScreen implements DialogInterface.OnClickListener {

    private static ImageButton mChooseContactsView;
    public static final String ADD_CONFERENCE_MEMBER_DIALOG = "add conference_member";
    private static final String LOG_TAG = "VoLteConfAddMemberScreen";
    private int mConferenceId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        log("onCreate...");
        super.onCreate(savedInstanceState);
        final AlertController.AlertParams p = mAlertParams;
        p.mView = createView();
        p.mTitle = getResources().getString(R.string.volte_add_conference_member_title);
        p.mPositiveButtonText = getString(com.android.internal.R.string.ok);
        p.mNegativeButtonText = getString(com.android.internal.R.string.cancel);
        p.mPositiveButtonListener =  this;
        p.mNegativeButtonListener = this;
        AddMemberScreenController.getInstance().setAddMemberScreen(this);
        setupAlert();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        log("onDestroy...");
        mContactsMap.clear();
        AddMemberScreenController.getInstance().clearAddMemberScreen();
        super.onDestroy();
    }

    private View createView() {
        View view = getLayoutInflater().inflate(R.layout.mtk_volte_add_conference_member, null);

        mChooseContactsView = (ImageButton) view.findViewById(R.id.choose_contacts);
        mChooseContactsView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // TODO Auto-generated method stub
                chooseFromContacts(AddMemberScreen.this);
            }
        });

        mEditView = (AddMemberEditView) view.findViewById(R.id.memeber_editor);
        mEditView.setAdapter(new AddMemberEditViewAdatper(this));
        mEditView.setTokenizer(new Rfc822Tokenizer());
        mEditView.requestFocus();
        return view;
    }

    private void processAddConferenceMemberAction() {
        List<String> numberList = getInputNumbers();
        if (numberList == null || numberList.isEmpty()) {
            log("[processAddConferenceMemberAction]empty input");
            return;
        }

        String conferenceCallId = AddMemberScreenController.getInstance().getConferenceCallId();
        Call conferenceCall = CallList.getInstance().getCallById(conferenceCallId);
        if (conferenceCall == null
                || !conferenceCall.can(android.telecom.Call.Details
                .CAPABILITY_INVITE_PARTICIPANTS)) {
            log("processAddConferenceMemberAction()...can not find a VoLTE conference.");
            return;
        }

        /// M: [log optimize]
        Log.op(conferenceCall, Log.CcOpAction.INVITE, "invite " + numberList.size() + " members");
        TelecomAdapter.getInstance().inviteConferenceParticipants(conferenceCallId, numberList);
    }

    private void log(String msg) {
        Log.d(LOG_TAG, msg);
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        // TODO Auto-generated method stub
        if (DialogInterface.BUTTON_POSITIVE == which) {
            processAddConferenceMemberAction();
            finish();
        } else if (DialogInterface.BUTTON_NEGATIVE == which) {
            finish();
        }
    }

    private List<String> getInputNumbers() {
        Spanned sp = mEditView.getText();
        String inputString = sp.toString();
        inputString = inputString.trim();
        log("getNumbers, numbers = " + inputString);
        List<String> numbers = new ArrayList<String>();
        List<String> formatNumbers = new ArrayList<String>();

        int i = 0;
        int breakIndex  = -1;
        int cursor = inputString.length();
        String number = "";

        while (i < cursor) {
            char c = inputString.charAt(i);
            if (c == ',' || c == ';') {
                if (breakIndex + 1 < i) {
                    number = inputString.substring(breakIndex + 1, i);
                    if (!TextUtils.isEmpty(number)) {
                        numbers.add(number);
                    }
                }
                breakIndex = i;
            }
            i++;
        }
        if (breakIndex + 1 < cursor) {
            number = inputString.substring(breakIndex + 1, cursor);
            if (!TextUtils.isEmpty(number)) {
                numbers.add(number);
            }
        }

        for (String str : numbers) {
            formatNumbers.add(PhoneNumberUtils.stripSeparators(str));
        }
        log("[getInputNumbers]all input numbers: ");
        dumpNumberList(formatNumbers);
        return formatNumbers;
    }

    private String getContactsName(String number) {
        String ret = number;
        if (mContactsMap.containsKey(number)) {
            log("getContactsName, find in map ~~");
            ret = mContactsMap.get(number);
        } else {
            String normalizedNumber = PhoneNumberUtils.normalizeNumber(number);
            if (!TextUtils.isEmpty(normalizedNumber)) {
                Cursor c = getContentResolver().query(
                        Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, normalizedNumber),
                        new String[] { Phone.DISPLAY_NAME }, null, null, null);
                try {
                    if (c != null && c.moveToFirst()) {
                        ret = c.getString(0);
                        mContactsMap.put(number, ret);
                    }
                } finally {
                    // M: fix CR:ALPS02360492,dereference after null check.
                    if (c != null) {
                        c.close();
                    }
                }
            }
        }
        log("getContactsName for " + number + ", name =" + ret);
        return ret;
    }

    private void dumpNumberList(List<String> list) {
        log("--------dump NumberList begin-------");
        log("list.size = " + list.size());
        for (int i = 0; i < list.size(); i++) {
            log("index / number: " + i + " / " + list.get(i));
        }
        log("--------dump NumberList end-------");
    }
}
