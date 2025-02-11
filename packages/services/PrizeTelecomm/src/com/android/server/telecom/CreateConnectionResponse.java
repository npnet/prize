/*
 * Copyright 2014, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.telecom;

import android.telecom.DisconnectCause;
import android.telecom.ParcelableConference;
import android.telecom.ParcelableConnection;

import com.android.internal.annotations.VisibleForTesting;

/**
 * A callback for providing the result of creating a connection.
 */
@VisibleForTesting
public interface CreateConnectionResponse {
    void handleCreateConnectionSuccess(CallIdMapper idMapper, ParcelableConnection connection);
    void handleCreateConnectionFailure(DisconnectCause disconnectCaused);
    /// M: For VoLTE @{
    void handleCreateConferenceSuccess(CallIdMapper idMapper, ParcelableConference conference);
    /// @}
}
