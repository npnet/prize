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
 * limitations under the License.
 */
package com.android.server.telecom;

import com.android.internal.telephony.CallerInfoAsyncQuery;

import android.content.Context;

public interface CallerInfoAsyncQueryFactory {
    /* Google original code:
     CallerInfoAsyncQuery startQuery(int token, Context context, String number,
            CallerInfoAsyncQuery.OnQueryCompleteListener listener, Object cookie);
     */
    /// M: we need to distinguish which phone's voicemail number, so need pass
    // subId to query which Google default do not pass to CallerInfoAsyncQuery.
    CallerInfoAsyncQuery startQuery(int token, Context context, String number,
            CallerInfoAsyncQuery.OnQueryCompleteListener listener, Object cookie, int subId);
}
