/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.server.telecom.callfiltering;

import android.net.Uri;

import com.android.internal.telephony.CallerInfo;
import com.android.server.telecom.Call;
import com.android.server.telecom.CallerInfoLookupHelper;
import com.android.server.telecom.Log;

import java.util.Objects;

public class DirectToVoicemailCallFilter implements IncomingCallFilter.CallFilter {
    private final CallerInfoLookupHelper mCallerInfoLookupHelper;

    public DirectToVoicemailCallFilter(CallerInfoLookupHelper callerInfoLookupHelper) {
        mCallerInfoLookupHelper = callerInfoLookupHelper;
    }

    @Override
    public void startFilterLookup(final Call call, CallFilterResultCallback callback) {
        Log.event(call, Log.Events.DIRECT_TO_VM_INITIATED);
        final Uri callHandle = call.getHandle();

        /// M: FIXME: We've already have the CallerInfo in call. Is it necessary to query again?
        mCallerInfoLookupHelper.startLookup(callHandle,
                new CallerInfoLookupHelper.OnQueryCompleteListener() {
                    @Override
                    public void onCallerInfoQueryComplete(Uri handle, CallerInfo info) {
                        CallFilteringResult result;

                        ///M: if info is null ,return result without reject
                        if (Objects.equals(callHandle, handle)) {
                            if (info != null && info.shouldSendToVoicemail) {

                                result = new CallFilteringResult(
                                        false, // shouldAllowCall
                                        true, // shouldReject
                                        true, // shouldAddToCallLog
                                        true // shouldShowNotification
                                );
                            } else {
                                result = new CallFilteringResult(
                                        true, // shouldAllowCall
                                        false, // shouldReject
                                        true, // shouldAddToCallLog
                                        true // shouldShowNotification
                                );
                            }
                            Log.event(call, Log.Events.DIRECT_TO_VM_FINISHED, result);
                            callback.onCallFilteringComplete(call, result);
                        } else {
                            Log.w(this, "CallerInfo lookup returned with a different handle than " +
                                    "what was passed in. Was %s, should be %s", handle, callHandle);
                        }
                    }

                    @Override
                    public void onContactPhotoQueryComplete(Uri handle, CallerInfo info) {
                        // ignore
                    }
                ///M: to distinguish which phone's voicemail number
                }, call.getSubId());
    }
}
