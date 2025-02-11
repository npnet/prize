/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2009 The Android Open Source Project
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

package android.content;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.res.AssetFileDescriptor;
import android.database.CrossProcessCursorWrapper;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.DeadObjectException;
import android.os.Handler;
import android.os.ICancellationSignal;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.os.SystemProperties; /// M: For aee warning
import android.util.Log;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.Preconditions;
import com.mediatek.aee.ExceptionLog; /// M: For aee warning

import dalvik.system.CloseGuard;

import java.io.BufferedReader; /// M: For aee warning
import java.io.FileInputStream; /// M: For aee warning
import java.io.FileNotFoundException;
import java.io.InputStream; /// M: For aee warning
import java.io.InputStreamReader; /// M: For aee warning
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The public interface object used to interact with a specific
 * {@link ContentProvider}.
 * <p>
 * Instances can be obtained by calling
 * {@link ContentResolver#acquireContentProviderClient} or
 * {@link ContentResolver#acquireUnstableContentProviderClient}. Instances must
 * be released using {@link #close()} in order to indicate to the system that
 * the underlying {@link ContentProvider} is no longer needed and can be killed
 * to free up resources.
 * <p>
 * Note that you should generally create a new ContentProviderClient instance
 * for each thread that will be performing operations. Unlike
 * {@link ContentResolver}, the methods here such as {@link #query} and
 * {@link #openFile} are not thread safe -- you must not call {@link #close()}
 * on the ContentProviderClient those calls are made from until you are finished
 * with the data they have returned.
 */
public class ContentProviderClient implements AutoCloseable {
    private static final String TAG = "ContentProviderClient";

    @GuardedBy("ContentProviderClient.class")
    private static Handler sAnrHandler;

    private final ContentResolver mContentResolver;
    private final IContentProvider mContentProvider;
    private final String mPackageName;
    private final boolean mStable;

    private final AtomicBoolean mClosed = new AtomicBoolean();
    private final CloseGuard mCloseGuard = CloseGuard.get();

    private long mAnrTimeout;
    private NotRespondingRunnable mAnrRunnable;


    /// M: To check ContentProviderClient leak @{
    private final Throwable mStackTrace;
    private static final boolean PROVIDER_LEAK_DETECT
                                         = Log.isLoggable("ProviderLeakDetecter", Log.VERBOSE);
    private static final boolean IS_ENG_BUILD
                                         = SystemProperties.get("ro.build.type").equals("eng");
    /// M: @}


    /** {@hide} */
    @VisibleForTesting
    public ContentProviderClient(
            ContentResolver contentResolver, IContentProvider contentProvider, boolean stable) {
        mContentResolver = contentResolver;
        mContentProvider = contentProvider;
        mPackageName = contentResolver.mPackageName;

        mStable = stable;

        mCloseGuard.open("close");


        /// M: To check ContentProviderClient leak @{
        if (IS_ENG_BUILD) {
            mStackTrace = new
              RuntimeException("Ensure that resources ContentProviderClient are closed after use")
              .fillInStackTrace();
        } else {
            mStackTrace = null;
        }
        /// M: @}
    }

    /** {@hide} */
    public void setDetectNotResponding(long timeoutMillis) {
        synchronized (ContentProviderClient.class) {
            mAnrTimeout = timeoutMillis;

            if (timeoutMillis > 0) {
                if (mAnrRunnable == null) {
                    mAnrRunnable = new NotRespondingRunnable();
                }
                if (sAnrHandler == null) {
                    sAnrHandler = new Handler(Looper.getMainLooper(), null, true /* async */);
                }
            } else {
                mAnrRunnable = null;
            }
        }
    }

    private void beforeRemote() {
        if (mAnrRunnable != null) {
            sAnrHandler.postDelayed(mAnrRunnable, mAnrTimeout);
        }
    }

    private void afterRemote() {
        if (mAnrRunnable != null) {
            sAnrHandler.removeCallbacks(mAnrRunnable);
        }
    }

    /** See {@link ContentProvider#query ContentProvider.query} */
    public @Nullable Cursor query(@NonNull Uri url, @Nullable String[] projection,
            @Nullable String selection, @Nullable String[] selectionArgs,
            @Nullable String sortOrder) throws RemoteException {
        return query(url, projection, selection,  selectionArgs, sortOrder, null);
    }

    /** See {@link ContentProvider#query ContentProvider.query} */
    public @Nullable Cursor query(@NonNull Uri url, @Nullable String[] projection,
            @Nullable String selection, @Nullable String[] selectionArgs,
            @Nullable String sortOrder, @Nullable CancellationSignal cancellationSignal)
                    throws RemoteException {
        Preconditions.checkNotNull(url, "url");

        beforeRemote();
        try {
            ICancellationSignal remoteCancellationSignal = null;
            if (cancellationSignal != null) {
                cancellationSignal.throwIfCanceled();
                remoteCancellationSignal = mContentProvider.createCancellationSignal();
                cancellationSignal.setRemote(remoteCancellationSignal);
            }
            final Cursor cursor = mContentProvider.query(mPackageName, url, projection, selection,
                    selectionArgs, sortOrder, remoteCancellationSignal);
            if (cursor == null) {
                return null;
            }

            if ("com.google.android.gms".equals(mPackageName)) {
                // They're casting to a concrete subclass, sigh
                return cursor;
            } else {
                return new CursorWrapperInner(cursor);
            }
        } catch (DeadObjectException e) {
            if (!mStable) {
                mContentResolver.unstableProviderDied(mContentProvider);
            }
            throw e;
        } finally {
            afterRemote();
        }
    }

    /** See {@link ContentProvider#getType ContentProvider.getType} */
    public @Nullable String getType(@NonNull Uri url) throws RemoteException {
        Preconditions.checkNotNull(url, "url");

        beforeRemote();
        try {
            return mContentProvider.getType(url);
        } catch (DeadObjectException e) {
            if (!mStable) {
                mContentResolver.unstableProviderDied(mContentProvider);
            }
            throw e;
        } finally {
            afterRemote();
        }
    }

    /** See {@link ContentProvider#getStreamTypes ContentProvider.getStreamTypes} */
    public @Nullable String[] getStreamTypes(@NonNull Uri url, @NonNull String mimeTypeFilter)
            throws RemoteException {
        Preconditions.checkNotNull(url, "url");
        Preconditions.checkNotNull(mimeTypeFilter, "mimeTypeFilter");

        beforeRemote();
        try {
            return mContentProvider.getStreamTypes(url, mimeTypeFilter);
        } catch (DeadObjectException e) {
            if (!mStable) {
                mContentResolver.unstableProviderDied(mContentProvider);
            }
            throw e;
        } finally {
            afterRemote();
        }
    }

    /** See {@link ContentProvider#canonicalize} */
    public final @Nullable Uri canonicalize(@NonNull Uri url) throws RemoteException {
        Preconditions.checkNotNull(url, "url");

        beforeRemote();
        try {
            return mContentProvider.canonicalize(mPackageName, url);
        } catch (DeadObjectException e) {
            if (!mStable) {
                mContentResolver.unstableProviderDied(mContentProvider);
            }
            throw e;
        } finally {
            afterRemote();
        }
    }

    /** See {@link ContentProvider#uncanonicalize} */
    public final @Nullable Uri uncanonicalize(@NonNull Uri url) throws RemoteException {
        Preconditions.checkNotNull(url, "url");

        beforeRemote();
        try {
            return mContentProvider.uncanonicalize(mPackageName, url);
        } catch (DeadObjectException e) {
            if (!mStable) {
                mContentResolver.unstableProviderDied(mContentProvider);
            }
            throw e;
        } finally {
            afterRemote();
        }
    }

    /** See {@link ContentProvider#insert ContentProvider.insert} */
    public @Nullable Uri insert(@NonNull Uri url, @Nullable ContentValues initialValues)
            throws RemoteException {
        Preconditions.checkNotNull(url, "url");

        beforeRemote();
        try {
            return mContentProvider.insert(mPackageName, url, initialValues);
        } catch (DeadObjectException e) {
            if (!mStable) {
                mContentResolver.unstableProviderDied(mContentProvider);
            }
            throw e;
        } finally {
            afterRemote();
        }
    }

    /** See {@link ContentProvider#bulkInsert ContentProvider.bulkInsert} */
    public int bulkInsert(@NonNull Uri url, @NonNull ContentValues[] initialValues)
            throws RemoteException {
        Preconditions.checkNotNull(url, "url");
        Preconditions.checkNotNull(initialValues, "initialValues");

        beforeRemote();
        try {
            return mContentProvider.bulkInsert(mPackageName, url, initialValues);
        } catch (DeadObjectException e) {
            if (!mStable) {
                mContentResolver.unstableProviderDied(mContentProvider);
            }
            throw e;
        } finally {
            afterRemote();
        }
    }

    /** See {@link ContentProvider#delete ContentProvider.delete} */
    public int delete(@NonNull Uri url, @Nullable String selection,
            @Nullable String[] selectionArgs) throws RemoteException {
        Preconditions.checkNotNull(url, "url");

        beforeRemote();
        try {
            return mContentProvider.delete(mPackageName, url, selection, selectionArgs);
        } catch (DeadObjectException e) {
            if (!mStable) {
                mContentResolver.unstableProviderDied(mContentProvider);
            }
            throw e;
        } finally {
            afterRemote();
        }
    }

    /** See {@link ContentProvider#update ContentProvider.update} */
    public int update(@NonNull Uri url, @Nullable ContentValues values, @Nullable String selection,
            @Nullable String[] selectionArgs) throws RemoteException {
        Preconditions.checkNotNull(url, "url");

        beforeRemote();
        try {
            return mContentProvider.update(mPackageName, url, values, selection, selectionArgs);
        } catch (DeadObjectException e) {
            if (!mStable) {
                mContentResolver.unstableProviderDied(mContentProvider);
            }
            throw e;
        } finally {
            afterRemote();
        }
    }

    /**
     * See {@link ContentProvider#openFile ContentProvider.openFile}.  Note that
     * this <em>does not</em>
     * take care of non-content: URIs such as file:.  It is strongly recommended
     * you use the {@link ContentResolver#openFileDescriptor
     * ContentResolver.openFileDescriptor} API instead.
     */
    public @Nullable ParcelFileDescriptor openFile(@NonNull Uri url, @NonNull String mode)
            throws RemoteException, FileNotFoundException {
        return openFile(url, mode, null);
    }

    /**
     * See {@link ContentProvider#openFile ContentProvider.openFile}.  Note that
     * this <em>does not</em>
     * take care of non-content: URIs such as file:.  It is strongly recommended
     * you use the {@link ContentResolver#openFileDescriptor
     * ContentResolver.openFileDescriptor} API instead.
     */
    public @Nullable ParcelFileDescriptor openFile(@NonNull Uri url, @NonNull String mode,
            @Nullable CancellationSignal signal) throws RemoteException, FileNotFoundException {
        Preconditions.checkNotNull(url, "url");
        Preconditions.checkNotNull(mode, "mode");

        beforeRemote();
        try {
            ICancellationSignal remoteSignal = null;
            if (signal != null) {
                signal.throwIfCanceled();
                remoteSignal = mContentProvider.createCancellationSignal();
                signal.setRemote(remoteSignal);
            }
            return mContentProvider.openFile(mPackageName, url, mode, remoteSignal, null);
        } catch (DeadObjectException e) {
            if (!mStable) {
                mContentResolver.unstableProviderDied(mContentProvider);
            }
            throw e;
        } finally {
            afterRemote();
        }
    }

    /**
     * See {@link ContentProvider#openAssetFile ContentProvider.openAssetFile}.
     * Note that this <em>does not</em>
     * take care of non-content: URIs such as file:.  It is strongly recommended
     * you use the {@link ContentResolver#openAssetFileDescriptor
     * ContentResolver.openAssetFileDescriptor} API instead.
     */
    public @Nullable AssetFileDescriptor openAssetFile(@NonNull Uri url, @NonNull String mode)
            throws RemoteException, FileNotFoundException {
        return openAssetFile(url, mode, null);
    }

    /**
     * See {@link ContentProvider#openAssetFile ContentProvider.openAssetFile}.
     * Note that this <em>does not</em>
     * take care of non-content: URIs such as file:.  It is strongly recommended
     * you use the {@link ContentResolver#openAssetFileDescriptor
     * ContentResolver.openAssetFileDescriptor} API instead.
     */
    public @Nullable AssetFileDescriptor openAssetFile(@NonNull Uri url, @NonNull String mode,
            @Nullable CancellationSignal signal) throws RemoteException, FileNotFoundException {
        Preconditions.checkNotNull(url, "url");
        Preconditions.checkNotNull(mode, "mode");

        beforeRemote();
        try {
            ICancellationSignal remoteSignal = null;
            if (signal != null) {
                signal.throwIfCanceled();
                remoteSignal = mContentProvider.createCancellationSignal();
                signal.setRemote(remoteSignal);
            }
            return mContentProvider.openAssetFile(mPackageName, url, mode, remoteSignal);
        } catch (DeadObjectException e) {
            if (!mStable) {
                mContentResolver.unstableProviderDied(mContentProvider);
            }
            throw e;
        } finally {
            afterRemote();
        }
    }

    /** See {@link ContentProvider#openTypedAssetFile ContentProvider.openTypedAssetFile} */
    public final @Nullable AssetFileDescriptor openTypedAssetFileDescriptor(@NonNull Uri uri,
            @NonNull String mimeType, @Nullable Bundle opts)
                    throws RemoteException, FileNotFoundException {
        return openTypedAssetFileDescriptor(uri, mimeType, opts, null);
    }

    /** See {@link ContentProvider#openTypedAssetFile ContentProvider.openTypedAssetFile} */
    public final @Nullable AssetFileDescriptor openTypedAssetFileDescriptor(@NonNull Uri uri,
            @NonNull String mimeType, @Nullable Bundle opts, @Nullable CancellationSignal signal)
                    throws RemoteException, FileNotFoundException {
        Preconditions.checkNotNull(uri, "uri");
        Preconditions.checkNotNull(mimeType, "mimeType");

        beforeRemote();
        try {
            ICancellationSignal remoteSignal = null;
            if (signal != null) {
                signal.throwIfCanceled();
                remoteSignal = mContentProvider.createCancellationSignal();
                signal.setRemote(remoteSignal);
            }
            return mContentProvider.openTypedAssetFile(
                    mPackageName, uri, mimeType, opts, remoteSignal);
        } catch (DeadObjectException e) {
            if (!mStable) {
                mContentResolver.unstableProviderDied(mContentProvider);
            }
            throw e;
        } finally {
            afterRemote();
        }
    }

    /** See {@link ContentProvider#applyBatch ContentProvider.applyBatch} */
    public @NonNull ContentProviderResult[] applyBatch(
            @NonNull ArrayList<ContentProviderOperation> operations)
                    throws RemoteException, OperationApplicationException {
        Preconditions.checkNotNull(operations, "operations");

        beforeRemote();
        try {
            return mContentProvider.applyBatch(mPackageName, operations);
        } catch (DeadObjectException e) {
            if (!mStable) {
                mContentResolver.unstableProviderDied(mContentProvider);
            }
            throw e;
        } finally {
            afterRemote();
        }
    }

    /** See {@link ContentProvider#call(String, String, Bundle)} */
    public @Nullable Bundle call(@NonNull String method, @Nullable String arg,
            @Nullable Bundle extras) throws RemoteException {
        Preconditions.checkNotNull(method, "method");

        beforeRemote();
        try {
            return mContentProvider.call(mPackageName, method, arg, extras);
        } catch (DeadObjectException e) {
            if (!mStable) {
                mContentResolver.unstableProviderDied(mContentProvider);
            }
            throw e;
        } finally {
            afterRemote();
        }
    }

    /**
     * Closes this client connection, indicating to the system that the
     * underlying {@link ContentProvider} is no longer needed.
     */
    @Override
    public void close() {
        closeInternal();
    }

    /**
     * @deprecated replaced by {@link #close()}.
     */
    @Deprecated
    public boolean release() {
        return closeInternal();
    }

    private boolean closeInternal() {
        mCloseGuard.close();
        if (mClosed.compareAndSet(false, true)) {
            if (mStable) {
                return mContentResolver.releaseProvider(mContentProvider);
            } else {
                return mContentResolver.releaseUnstableProvider(mContentProvider);
            }
        } else {
            return false;
        }
    }

    @Override
    @SuppressWarnings("illegalcatch") /// M: to avoid aee exception warning
    protected void finalize() throws Throwable {
        try {
            mCloseGuard.warnIfOpen();
            close();
        } finally {
            super.finalize();
        }

        /** M: For ContentProviderClient leak aee warning @{
            Only show red screen warning for resmon monitored applications */
        if (IS_ENG_BUILD ) {
            if (mStackTrace != null) {
                Log.v(TAG,
                      "Ensure that resources ContentProviderClient are closed after use.",
                      mStackTrace);
            }

            if (PROVIDER_LEAK_DETECT && checkAeeWarningList()) {
                try {
                    ExceptionLog exceptionLog = null;
                    if (SystemProperties.get("ro.have_aee_feature").equals("1")) {
                        exceptionLog = new ExceptionLog();
                    }
                    if (exceptionLog != null) {
                        exceptionLog.systemreport(
                            exceptionLog.AEE_WARNING_JNI,
                            "ContentProviderClient.java",
                            "Ensure that resources ContentProviderClient are closed after use.",
                            "/data/leak/traces.txt");
                    }
                } catch (Exception e) {
                    // AEE disabled or failed to allocate AEE object, no need to show message
                }
            }
        }
        /** @} */
    }

    /**
     * M: Read uid from resmon monitored uid list file,
     *    only report red screen warning for these uid.
     * @return true if pid in the resmon monitored list.
     */
    @SuppressWarnings("illegalcatch")
    private boolean checkAeeWarningList() {
        int uid = android.os.Process.myUid();
        InputStream inStream = null;

        try {
            inStream = new FileInputStream("/data/system/resmon-uid.txt");

            if (inStream != null) {
                InputStreamReader inputReader = new InputStreamReader(inStream);
                BufferedReader buffReader = new BufferedReader(inputReader);

                String line = buffReader.readLine();
                while (line != null) {
                    if (uid == Integer.valueOf(line)) {
                        return true;
                    }
                    line = buffReader.readLine();
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        } finally {
            if (inStream != null) {
                try {
                    inStream.close();
                } catch (Exception e) {
                    // Do nothing
                }
            }
        }
    }

    /**
     * Get a reference to the {@link ContentProvider} that is associated with this
     * client. If the {@link ContentProvider} is running in a different process then
     * null will be returned. This can be used if you know you are running in the same
     * process as a provider, and want to get direct access to its implementation details.
     *
     * @return If the associated {@link ContentProvider} is local, returns it.
     * Otherwise returns null.
     */
    public @Nullable ContentProvider getLocalContentProvider() {
        return ContentProvider.coerceToLocalContentProvider(mContentProvider);
    }

    /** {@hide} */
    public static void releaseQuietly(ContentProviderClient client) {
        if (client != null) {
            try {
                client.release();
            } catch (Exception ignored) {
            }
        }
    }

    private class NotRespondingRunnable implements Runnable {
        @Override
        public void run() {
            Log.w(TAG, "Detected provider not responding: " + mContentProvider);
            mContentResolver.appNotRespondingViaProvider(mContentProvider);
        }
    }

    private final class CursorWrapperInner extends CrossProcessCursorWrapper {
        private final CloseGuard mCloseGuard = CloseGuard.get();

        CursorWrapperInner(Cursor cursor) {
            super(cursor);
            mCloseGuard.open("close");
        }

        @Override
        public void close() {
            mCloseGuard.close();
            super.close();
        }

        @Override
        protected void finalize() throws Throwable {
            try {
                mCloseGuard.warnIfOpen();
                close();
            } finally {
                super.finalize();
            }
        }
    }
}
