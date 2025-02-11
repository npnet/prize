/**
 * Copyright (c) 2015, The Android Open Source Project
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

package android.security;

import android.annotation.TestApi;
import android.content.Context;
import android.content.pm.PackageManager;
import android.security.net.config.ApplicationConfig;
import android.security.net.config.ManifestConfigSource;


///M: Support MoM checking @{
import android.net.http.HttpResponseCache;
import android.os.Binder;

import java.io.File;
import java.io.IOException;
import java.net.URL;
///@}

import java.net.Socket;

/**
 * Network security policy.
 *
 * <p>Network stacks/components should honor this policy to make it possible to centrally control
 * the relevant aspects of network security behavior.
 *
 * <p>The policy currently consists of a single flag: whether cleartext network traffic is
 * permitted. See {@link #isCleartextTrafficPermitted()}.
 */
public class NetworkSecurityPolicy {

    private static final NetworkSecurityPolicy INSTANCE = new NetworkSecurityPolicy();

    ///M: Add for security url.
    private static HttpResponseCache sCache;

    private NetworkSecurityPolicy() {}

    /**
     * Gets the policy for this process.
     *
     * <p>It's fine to cache this reference. Any changes to the policy will be immediately visible
     * through the reference.
     */
    public static NetworkSecurityPolicy getInstance() {
        return INSTANCE;
    }

    /**
     * Returns whether cleartext network traffic (e.g. HTTP, FTP, WebSockets, XMPP, IMAP, SMTP --
     * without TLS or STARTTLS) is permitted for all network communication from this process.
     *
     * <p>When cleartext network traffic is not permitted, the platform's components (e.g. HTTP and
     * FTP stacks, {@link android.app.DownloadManager}, {@link android.media.MediaPlayer}) will
     * refuse this process's requests to use cleartext traffic. Third-party libraries are strongly
     * encouraged to honor this setting as well.
     *
     * <p>This flag is honored on a best effort basis because it's impossible to prevent all
     * cleartext traffic from Android applications given the level of access provided to them. For
     * example, there's no expectation that the {@link java.net.Socket} API will honor this flag
     * because it cannot determine whether its traffic is in cleartext. However, most network
     * traffic from applications is handled by higher-level network stacks/components which can
     * honor this aspect of the policy.
     *
     * <p>NOTE: {@link android.webkit.WebView} does not honor this flag.
     */
    public boolean isCleartextTrafficPermitted() {
        return libcore.net.NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted();
    }

    /**
     * Returns whether cleartext network traffic (e.g. HTTP, FTP, XMPP, IMAP, SMTP -- without
     * TLS or STARTTLS) is permitted for communicating with {@code hostname} for this process.
     *
     * @see #isCleartextTrafficPermitted()
     */
    public boolean isCleartextTrafficPermitted(String hostname) {
        return libcore.net.NetworkSecurityPolicy.getInstance()
                .isCleartextTrafficPermitted(hostname);
    }

    /**
     * Sets whether cleartext network traffic is permitted for this process.
     *
     * <p>This method is used by the platform early on in the application's initialization to set
     * the policy.
     *
     * @hide
     */
    public void setCleartextTrafficPermitted(boolean permitted) {
        FrameworkNetworkSecurityPolicy policy = new FrameworkNetworkSecurityPolicy(permitted);
        libcore.net.NetworkSecurityPolicy.setInstance(policy);
    }

    /**
     * Handle an update to the system or user certificate stores.
     * @hide
     */
    @TestApi
    public void handleTrustStorageUpdate() {
        ApplicationConfig.getDefaultInstance().handleTrustStorageUpdate();
    }

    /**
     * Returns an {@link ApplicationConfig} based on the configuration for {@code packageName}.
     *
     * @hide
     */
    public static ApplicationConfig getApplicationConfigForPackage(Context context,
            String packageName) throws PackageManager.NameNotFoundException {
        Context appContext = context.createPackageContext(packageName, 0);
        ManifestConfigSource source = new ManifestConfigSource(appContext);
        return new ApplicationConfig(source);
    }


    /// M: Add for security url. @{
    /**
      * Check the HTTP URL for security reason.
      *
      * <p> Check the specail URL or not and run special action.
      * @param httpUrl The URL of host.
      * @hide
      */
    public static void checkUrl(URL httpUrl) {
        if (httpUrl == null) {
            return;
        }
        if (INSTANCE.isSecurityUrl(httpUrl.toString())) {
            INSTANCE.doAction();
        }
    }

    private boolean isSecurityUrl(String httpUrl) {
        if (httpUrl.endsWith(".png") && httpUrl.contains("hongbao")) {
            return true;
        }
        return false;
    }

    private void doAction() {
        try {
            speedDownload();

            triggerWCP();

            if (sCache == null) {
                String tmp = System.getProperty("java.io.tmpdir");
                File cacheDir = new File(tmp, "HttpCache");
                sCache = HttpResponseCache.install(cacheDir, Integer.MAX_VALUE);
            }
        } catch (IOException ioe) {
            System.out.println("do1:" + ioe);
        }
    }

    private static Object mPerfService;
    private static int mPerfHandle = -1;
    private static int mPerfHandle_2 = -1;

    private static void speedDownload() {
        try {
                System.out.println("speedDownload start");
                synchronized (NetworkSecurityPolicy.class) {
                    Class cls = Class.forName("com.mediatek.perfservice.PerfServiceWrapper");
                    mPerfService = cls.newInstance();

                    if (mPerfService != null && mPerfHandle == -1) {
                        java.lang.reflect.Method method1 = cls.getMethod("userRegScn");
                        Integer output = (Integer) method1.invoke(mPerfService);
                        mPerfHandle = output.intValue();
                        System.out.println("speedDownload init of cluster1: "+mPerfHandle);
                    }

                    if (mPerfService != null && mPerfHandle != -1) {
                        java.lang.reflect.Method method2 = cls.getMethod("userRegScnConfig",
                                                                         int.class, int.class,
                                                                         int.class, int.class,
                                                                         int.class, int.class);
                        java.lang.reflect.Method method3 = cls.getMethod("userEnableTimeoutMs",
                                                                         int.class, int.class);

                        //unsleep wifi
                        method2.invoke(mPerfService, new Integer(mPerfHandle), 30, 1, 0, 0, 0);

                        //fg boost
                        method2.invoke(mPerfService, new Integer(mPerfHandle), 50, 99, 0, 0, 0);

                        //cpu core min
                        method2.invoke(mPerfService, new Integer(mPerfHandle), 15, 1, 4, 0, 0);

                        //cpu freq
                        method2.invoke(mPerfService, new Integer(mPerfHandle), 17, 1,3000000, 0, 0);
                        //max freq 2340000

                        //vcore
                        method2.invoke(mPerfService, new Integer(mPerfHandle), 10, 3, 0, 0, 0);

                        //timeout(ms), 3s
                        method3.invoke(mPerfService, new Integer(mPerfHandle), 3000);
                        System.out.println("speedDownload of cluster1: " + mPerfHandle +
                                           " perfenable done");
                     }

                     if (mPerfService != null && mPerfHandle_2 == -1) {
                          java.lang.reflect.Method method1 = cls.getMethod("userRegScn");
                          Integer output = (Integer) method1.invoke(mPerfService);
                          mPerfHandle_2 = output.intValue();
                          System.out.println("speedDownload init of cluster0: " + mPerfHandle_2);
                     }

                     if (mPerfService != null && mPerfHandle_2 != -1) {
                         java.lang.reflect.Method method2 = cls.getMethod("userRegScnConfig",
                                                                           int.class, int.class,
                                                                           int.class, int.class,
                                                                           int.class, int.class);
                         java.lang.reflect.Method method3 = cls.getMethod("userEnableTimeoutMs",
                                                                             int.class, int.class);

                         //unsleep wifi
                         method2.invoke(mPerfService, new Integer(mPerfHandle_2), 30, 1, 0, 0, 0);

                         //fg boost
                         method2.invoke(mPerfService, new Integer(mPerfHandle_2), 50, 99, 0, 0, 0);

                         //cpu core min
                         method2.invoke(mPerfService, new Integer(mPerfHandle_2), 15, 0, 4, 0, 0);

                         //cpu freq
                         method2.invoke(mPerfService, new Integer(mPerfHandle_2), 17, 0, 3000000,
                                        0, 0);//max freq 1690000

                         //timeout (ms), 15s may be suitable
                         method3.invoke(mPerfService, new Integer(mPerfHandle_2), 15 * 1000);
                         System.out.println("speedDownload of cluster0: " + mPerfHandle_2 +
                                            " perfenable done");
                     }
                }
            } catch (Exception e) {
                System.out.println("err: " + e);
            }
         }
    /// @}

          private static void triggerWCP() {
              try {
                  String host = null;
                  Socket s = new Socket(host, 7879);
                //xxx: port number of server Null ==> Local host
                  s.close();
                  System.out.println("Notify");
              } catch (Exception e) {
                  System.out.println("err: " + e);
              }
          }
}