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
package android.net.wifi;

import android.annotation.Nullable;
import android.os.Parcel;
import android.os.Parcelable;
import android.security.Credentials;
import android.text.TextUtils;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.HashMap;
import java.util.Map;

/**
 * Enterprise configuration details for Wi-Fi. Stores details about the EAP method
 * and any associated credentials.
 */
public class WifiEnterpriseConfig implements Parcelable {

    /** @hide */
    public static final String EMPTY_VALUE         = "NULL";
    /** @hide */
    public static final String EAP_KEY             = "eap";
    /** @hide */
    public static final String PHASE2_KEY          = "phase2";
    /** @hide */
    public static final String IDENTITY_KEY        = "identity";
    /** @hide */
    public static final String ANON_IDENTITY_KEY   = "anonymous_identity";
    /** @hide */
    public static final String PASSWORD_KEY        = "password";
    /** @hide */
    public static final String SUBJECT_MATCH_KEY   = "subject_match";
    /** @hide */
    public static final String ALTSUBJECT_MATCH_KEY = "altsubject_match";
    /** @hide */
    public static final String DOM_SUFFIX_MATCH_KEY = "domain_suffix_match";
    /** @hide */
    public static final String OPP_KEY_CACHING     = "proactive_key_caching";
    /**
     * String representing the keystore OpenSSL ENGINE's ID.
     * @hide
     */
    public static final String ENGINE_ID_KEYSTORE = "keystore";

    /**
     * String representing the keystore URI used for wpa_supplicant.
     * @hide
     */
    public static final String KEYSTORE_URI = "keystore://";

    /**
     * String representing the keystore URI used for wpa_supplicant,
     * Unlike #KEYSTORE_URI, this supports a list of space-delimited aliases
     * @hide
     */
    public static final String KEYSTORES_URI = "keystores://";

    /**
     * String to set the engine value to when it should be enabled.
     * @hide
     */
    public static final String ENGINE_ENABLE = "1";

    /**
     * String to set the engine value to when it should be disabled.
     * @hide
     */
    public static final String ENGINE_DISABLE = "0";

    /** @hide */
    public static final String CA_CERT_PREFIX = KEYSTORE_URI + Credentials.CA_CERTIFICATE;
    ///M: @{
    /** @hide */
    public static final String CA_CERT2_PREFIX = KEYSTORE_URI + Credentials.WAPI_SERVER_CERTIFICATE;
    private static final String CLIENT_CERT2_PREFIX = KEYSTORE_URI + Credentials.WAPI_USER_CERTIFICATE;
    /** @hide */
    public static final String USER_PRIVATE_KEY2_PREFIX =  KEYSTORE_URI + Credentials.WAPI_USER_CERTIFICATE;
    ///@}
    /** @hide */
    public static final String CLIENT_CERT_PREFIX = KEYSTORE_URI + Credentials.USER_CERTIFICATE;
    /** @hide */
    public static final String CLIENT_CERT_KEY     = "client_cert";
    /** @hide */
    public static final String CA_CERT_KEY         = "ca_cert";
    /** @hide */
    public static final String CA_PATH_KEY         = "ca_path";
    /** @hide */
    public static final String ENGINE_KEY          = "engine";
    /** @hide */
    public static final String ENGINE_ID_KEY       = "engine_id";
    /** @hide */
    public static final String PRIVATE_KEY_ID_KEY  = "key_id";
    /** @hide */
    public static final String REALM_KEY           = "realm";
    /** @hide */
    public static final String PLMN_KEY            = "plmn";
    /** @hide */
    public static final String CA_CERT_ALIAS_DELIMITER = " ";

    ///M: @{
    /** @hide */
    public static final String CA_CERT2_KEY         = "ca_cert2";
    ///@}

    // Fields to copy verbatim from wpa_supplicant.
    private static final String[] SUPPLICANT_CONFIG_KEYS = new String[] {
            IDENTITY_KEY,
            ANON_IDENTITY_KEY,
            PASSWORD_KEY,
            CLIENT_CERT_KEY,
            CA_CERT_KEY,
            CA_CERT2_KEY,
            SUBJECT_MATCH_KEY,
            ENGINE_KEY,
            ENGINE_ID_KEY,
            PRIVATE_KEY_ID_KEY,
            ALTSUBJECT_MATCH_KEY,
            DOM_SUFFIX_MATCH_KEY,
            CA_PATH_KEY
    };

    private HashMap<String, String> mFields = new HashMap<String, String>();
    private X509Certificate[] mCaCerts;
    private PrivateKey mClientPrivateKey;
    private X509Certificate mClientCertificate;
    private int mEapMethod = Eap.NONE;
    private int mPhase2Method = Phase2.NONE;

    private static final String TAG = "WifiEnterpriseConfig";

    public WifiEnterpriseConfig() {
        // Do not set defaults so that the enterprise fields that are not changed
        // by API are not changed underneath
        // This is essential because an app may not have all fields like password
        // available. It allows modification of subset of fields.

    }

    /** Copy constructor */
    public WifiEnterpriseConfig(WifiEnterpriseConfig source) {
        for (String key : source.mFields.keySet()) {
            mFields.put(key, source.mFields.get(key));
        }
        mEapMethod = source.mEapMethod;
        mPhase2Method = source.mPhase2Method;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mFields.size());
        for (Map.Entry<String, String> entry : mFields.entrySet()) {
            dest.writeString(entry.getKey());
            dest.writeString(entry.getValue());
        }

        dest.writeInt(mEapMethod);
        dest.writeInt(mPhase2Method);
        writeCertificates(dest, mCaCerts);

        if (mClientPrivateKey != null) {
            String algorithm = mClientPrivateKey.getAlgorithm();
            byte[] userKeyBytes = mClientPrivateKey.getEncoded();
            dest.writeInt(userKeyBytes.length);
            dest.writeByteArray(userKeyBytes);
            dest.writeString(algorithm);
        } else {
            dest.writeInt(0);
        }

        writeCertificate(dest, mClientCertificate);
    }

    private void writeCertificates(Parcel dest, X509Certificate[] cert) {
        if (cert != null && cert.length != 0) {
            dest.writeInt(cert.length);
            for (int i = 0; i < cert.length; i++) {
                writeCertificate(dest, cert[i]);
            }
        } else {
            dest.writeInt(0);
        }
    }

    private void writeCertificate(Parcel dest, X509Certificate cert) {
        if (cert != null) {
            try {
                byte[] certBytes = cert.getEncoded();
                dest.writeInt(certBytes.length);
                dest.writeByteArray(certBytes);
            } catch (CertificateEncodingException e) {
                dest.writeInt(0);
            }
        } else {
            dest.writeInt(0);
        }
    }

    public static final Creator<WifiEnterpriseConfig> CREATOR =
            new Creator<WifiEnterpriseConfig>() {
                public WifiEnterpriseConfig createFromParcel(Parcel in) {
                    WifiEnterpriseConfig enterpriseConfig = new WifiEnterpriseConfig();
                    int count = in.readInt();
                    for (int i = 0; i < count; i++) {
                        String key = in.readString();
                        String value = in.readString();
                        enterpriseConfig.mFields.put(key, value);
                    }

                    enterpriseConfig.mEapMethod = in.readInt();
                    enterpriseConfig.mPhase2Method = in.readInt();
                    enterpriseConfig.mCaCerts = readCertificates(in);

                    PrivateKey userKey = null;
                    int len = in.readInt();
                    if (len > 0) {
                        try {
                            byte[] bytes = new byte[len];
                            in.readByteArray(bytes);
                            String algorithm = in.readString();
                            KeyFactory keyFactory = KeyFactory.getInstance(algorithm);
                            userKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(bytes));
                        } catch (NoSuchAlgorithmException e) {
                            userKey = null;
                        } catch (InvalidKeySpecException e) {
                            userKey = null;
                        }
                    }

                    enterpriseConfig.mClientPrivateKey = userKey;
                    enterpriseConfig.mClientCertificate = readCertificate(in);
                    return enterpriseConfig;
                }

                private X509Certificate[] readCertificates(Parcel in) {
                    X509Certificate[] certs = null;
                    int len = in.readInt();
                    if (len > 0) {
                        certs = new X509Certificate[len];
                        for (int i = 0; i < len; i++) {
                            certs[i] = readCertificate(in);
                        }
                    }
                    return certs;
                }

                private X509Certificate readCertificate(Parcel in) {
                    X509Certificate cert = null;
                    int len = in.readInt();
                    if (len > 0) {
                        try {
                            byte[] bytes = new byte[len];
                            in.readByteArray(bytes);
                            CertificateFactory cFactory = CertificateFactory.getInstance("X.509");
                            cert = (X509Certificate) cFactory
                                    .generateCertificate(new ByteArrayInputStream(bytes));
                        } catch (CertificateException e) {
                            cert = null;
                        }
                    }
                    return cert;
                }

                public WifiEnterpriseConfig[] newArray(int size) {
                    return new WifiEnterpriseConfig[size];
                }
            };

    /** The Extensible Authentication Protocol method used */
    public static final class Eap {
        /** No EAP method used. Represents an empty config */
        public static final int NONE    = -1;
        /** Protected EAP */
        public static final int PEAP    = 0;
        /** EAP-Transport Layer Security */
        public static final int TLS     = 1;
        /** EAP-Tunneled Transport Layer Security */
        public static final int TTLS    = 2;
        /** EAP-Password */
        public static final int PWD     = 3;
        /** EAP-Subscriber Identity Module */
        public static final int SIM     = 4;
        /** EAP-Authentication and Key Agreement */
        public static final int AKA     = 5;
        /** EAP-Authentication and Key Agreement Prime */
        public static final int AKA_PRIME = 6;
        /** Hotspot 2.0 r2 OSEN */
        public static final int UNAUTH_TLS = 7;

        ///M: @{
        /**
            * EAP-FAST.
            * @hide
            * @internal
            */
        public static final int FAST     = 8;
        ///@}

        /** @hide */
        public static final String[] strings =
                { "PEAP", "TLS", "TTLS", "PWD", "SIM", "AKA", "AKA'", "WFA-UNAUTH-TLS", "FAST" };

        /** Prevent initialization */
        private Eap() {}
    }

    /** The inner authentication method used */
    public static final class Phase2 {
        public static final int NONE        = 0;
        /** Password Authentication Protocol */
        public static final int PAP         = 1;
        /** Microsoft Challenge Handshake Authentication Protocol */
        public static final int MSCHAP      = 2;
        /** Microsoft Challenge Handshake Authentication Protocol v2 */
        public static final int MSCHAPV2    = 3;
        /** Generic Token Card */
        public static final int GTC         = 4;
        private static final String AUTH_PREFIX = "auth=";
        private static final String AUTHEAP_PREFIX = "autheap=";
        /** @hide */
        public static final String[] strings = {EMPTY_VALUE, "PAP", "MSCHAP",
                "MSCHAPV2", "GTC" };

        /** Prevent initialization */
        private Phase2() {}
    }

    // Loader and saver interfaces for exchanging data with wpa_supplicant.
    // TODO: Decouple this object (which is just a placeholder of the configuration)
    // from the implementation that knows what wpa_supplicant wants.
    /**
     * Interface used for retrieving supplicant configuration from WifiEnterpriseConfig
     * @hide
     */
    public interface SupplicantSaver {
        /**
         * Set a value within wpa_supplicant configuration
         * @param key index to set within wpa_supplciant
         * @param value the value for the key
         * @return true if successful; false otherwise
         */
        boolean saveValue(String key, String value);
    }

    /**
     * Interface used for populating a WifiEnterpriseConfig from supplicant configuration
     * @hide
     */
    public interface SupplicantLoader {
        /**
         * Returns a value within wpa_supplicant configuration
         * @param key index to set within wpa_supplciant
         * @return string value if successful; null otherwise
         */
        String loadValue(String key);
    }

    /**
     * Internal use only; supply field values to wpa_supplicant config.  The configuration
     * process aborts on the first failed call on {@code saver}.
     * @param saver proxy for setting configuration in wpa_supplciant
     * @return whether the save succeeded on all attempts
     * @hide
     */
    public boolean saveToSupplicant(SupplicantSaver saver, WifiConfiguration config) {
        ///M: Modified for WAPI
        if (!isEapMethodValid() && !config.isWapi()) {
            return false;
        }

        // wpa_supplicant can update the anonymous identity for these kinds of networks after
        // framework reads them, so make sure the framework doesn't try to overwrite them.
        boolean shouldNotWriteAnonIdentity = mEapMethod == WifiEnterpriseConfig.Eap.SIM
                || mEapMethod == WifiEnterpriseConfig.Eap.AKA
                || mEapMethod == WifiEnterpriseConfig.Eap.AKA_PRIME;
        for (String key : mFields.keySet()) {
            if (shouldNotWriteAnonIdentity && ANON_IDENTITY_KEY.equals(key)) {
                continue;
            }
            if (!saver.saveValue(key, mFields.get(key))) {
                return false;
            }
        }

        if (!config.isWapi() && !saver.saveValue(EAP_KEY, Eap.strings[mEapMethod])) {
            return false;
        }

        if (mEapMethod != Eap.TLS && mPhase2Method != Phase2.NONE) {
            boolean is_autheap = mEapMethod == Eap.TTLS && mPhase2Method == Phase2.GTC;
            String prefix = is_autheap ? Phase2.AUTHEAP_PREFIX : Phase2.AUTH_PREFIX;
            String value = convertToQuotedString(prefix + Phase2.strings[mPhase2Method]);
            return saver.saveValue(PHASE2_KEY, value);
        } else if (mPhase2Method == Phase2.NONE) {
            // By default, send a null phase 2 to clear old configuration values.
            return saver.saveValue(PHASE2_KEY, null);
        } else {
            Log.e(TAG, "WiFi enterprise configuration is invalid as it supplies a "
                    + "phase 2 method but the phase1 method does not support it.");
            return false;
        }
    }

    /**
     * Internal use only; retrieve configuration from wpa_supplicant config.
     * @param loader proxy for retrieving configuration keys from wpa_supplicant
     * @hide
     */
    public void loadFromSupplicant(SupplicantLoader loader) {
        for (String key : SUPPLICANT_CONFIG_KEYS) {
            String value = loader.loadValue(key);
            if (value == null) {
                mFields.put(key, EMPTY_VALUE);
            } else {
                mFields.put(key, value);
            }
        }
        String eapMethod  = loader.loadValue(EAP_KEY);
        mEapMethod = getStringIndex(Eap.strings, eapMethod, Eap.NONE);

        String phase2Method = removeDoubleQuotes(loader.loadValue(PHASE2_KEY));
        // Remove "auth=" or "autheap=" prefix.
        if (phase2Method.startsWith(Phase2.AUTH_PREFIX)) {
            phase2Method = phase2Method.substring(Phase2.AUTH_PREFIX.length());
        } else if (phase2Method.startsWith(Phase2.AUTHEAP_PREFIX)) {
            phase2Method = phase2Method.substring(Phase2.AUTHEAP_PREFIX.length());
        }
        mPhase2Method = getStringIndex(Phase2.strings, phase2Method, Phase2.NONE);
    }

    /**
     * Set the EAP authentication method.
     * @param  eapMethod is one {@link Eap#PEAP}, {@link Eap#TLS}, {@link Eap#TTLS} or
     *                   {@link Eap#PWD}
     * @throws IllegalArgumentException on an invalid eap method
     */
    public void setEapMethod(int eapMethod) {
        switch (eapMethod) {
            /** Valid methods */
            case Eap.TLS:
            case Eap.UNAUTH_TLS:
                setPhase2Method(Phase2.NONE);
                /* fall through */
            case Eap.PEAP:
            case Eap.PWD:
            case Eap.TTLS:
            case Eap.SIM:
            case Eap.AKA:
            case Eap.AKA_PRIME:
            ///M: @{
            case Eap.FAST:
            ///@}
                mEapMethod = eapMethod;
                mFields.put(OPP_KEY_CACHING, "1");
                break;
            default:
                throw new IllegalArgumentException("Unknown EAP method");
        }
    }

    /**
     * Get the eap method.
     * @return eap method configured
     */
    public int getEapMethod() {
        return mEapMethod;
    }

    /**
     * Set Phase 2 authentication method. Sets the inner authentication method to be used in
     * phase 2 after setting up a secure channel
     * @param phase2Method is the inner authentication method and can be one of {@link Phase2#NONE},
     *                     {@link Phase2#PAP}, {@link Phase2#MSCHAP}, {@link Phase2#MSCHAPV2},
     *                     {@link Phase2#GTC}
     * @throws IllegalArgumentException on an invalid phase2 method
     *
     */
    public void setPhase2Method(int phase2Method) {
        switch (phase2Method) {
            case Phase2.NONE:
            case Phase2.PAP:
            case Phase2.MSCHAP:
            case Phase2.MSCHAPV2:
            case Phase2.GTC:
                mPhase2Method = phase2Method;
                break;
            default:
                throw new IllegalArgumentException("Unknown Phase 2 method");
        }
    }

    /**
     * Get the phase 2 authentication method.
     * @return a phase 2 method defined at {@link Phase2}
     * */
    public int getPhase2Method() {
        return mPhase2Method;
    }

    /**
     * Set the identity
     * @param identity
     */
    public void setIdentity(String identity) {
        setFieldValue(IDENTITY_KEY, identity, "");
    }

    /**
     * Get the identity
     * @return the identity
     */
    public String getIdentity() {
        return getFieldValue(IDENTITY_KEY, "");
    }

    /**
     * Set anonymous identity. This is used as the unencrypted identity with
     * certain EAP types
     * @param anonymousIdentity the anonymous identity
     */
    public void setAnonymousIdentity(String anonymousIdentity) {
        setFieldValue(ANON_IDENTITY_KEY, anonymousIdentity, "");
    }

    /**
     * Get the anonymous identity
     * @return anonymous identity
     */
    public String getAnonymousIdentity() {
        return getFieldValue(ANON_IDENTITY_KEY, "");
    }

    /**
     * Set the password.
     * @param password the password
     */
    public void setPassword(String password) {
        setFieldValue(PASSWORD_KEY, password, "");
    }

    /**
     * Get the password.
     *
     * Returns locally set password value. For networks fetched from
     * framework, returns "*".
     */
    public String getPassword() {
        return getFieldValue(PASSWORD_KEY, "");
    }

    /**
     * Encode a CA certificate alias so it does not contain illegal character.
     * @hide
     */
    public static String encodeCaCertificateAlias(String alias) {
        byte[] bytes = alias.getBytes(StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte o : bytes) {
            sb.append(String.format("%02x", o & 0xFF));
        }
        return sb.toString();
    }

    /**
     * Decode a previously-encoded CA certificate alias.
     * @hide
     */
    public static String decodeCaCertificateAlias(String alias) {
        byte[] data = new byte[alias.length() >> 1];
        for (int n = 0, position = 0; n < alias.length(); n += 2, position++) {
            data[position] = (byte) Integer.parseInt(alias.substring(n,  n + 2), 16);
        }
        try {
            return new String(data, StandardCharsets.UTF_8);
        } catch (NumberFormatException e) {
            e.printStackTrace();
            return alias;
        }
    }

    /**
     * Set CA certificate alias.
     *
     * <p> See the {@link android.security.KeyChain} for details on installing or choosing
     * a certificate
     * </p>
     * @param alias identifies the certificate
     * @hide
     */
    public void setCaCertificateAlias(String alias) {
        setFieldValue(CA_CERT_KEY, alias, CA_CERT_PREFIX);
    }

    /**
     * Set CA certificate aliases. When creating installing the corresponding certificate to
     * the keystore, please use alias encoded by {@link #encodeCaCertificateAlias(String)}.
     *
     * <p> See the {@link android.security.KeyChain} for details on installing or choosing
     * a certificate.
     * </p>
     * @param aliases identifies the certificate
     * @hide
     */
    public void setCaCertificateAliases(@Nullable String[] aliases) {
        if (aliases == null) {
            setFieldValue(CA_CERT_KEY, null, CA_CERT_PREFIX);
        } else if (aliases.length == 1) {
            // Backwards compatibility: use the original cert prefix if setting only one alias.
            setCaCertificateAlias(aliases[0]);
        } else {
            // Use KEYSTORES_URI which supports multiple aliases.
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < aliases.length; i++) {
                if (i > 0) {
                    sb.append(CA_CERT_ALIAS_DELIMITER);
                }
                sb.append(encodeCaCertificateAlias(Credentials.CA_CERTIFICATE + aliases[i]));
            }
            setFieldValue(CA_CERT_KEY, sb.toString(), KEYSTORES_URI);
        }
    }

    /**
     * Get CA certificate alias
     * @return alias to the CA certificate
     * @hide
     */
    public String getCaCertificateAlias() {
        return getFieldValue(CA_CERT_KEY, CA_CERT_PREFIX);
    }

    /**
     * Get CA certificate aliases
     * @return alias to the CA certificate
     * @hide
     */
    @Nullable public String[] getCaCertificateAliases() {
        String value = getFieldValue(CA_CERT_KEY, "");
        if (value.startsWith(CA_CERT_PREFIX)) {
            // Backwards compatibility: parse the original alias prefix.
            return new String[] {getFieldValue(CA_CERT_KEY, CA_CERT_PREFIX)};
        } else if (value.startsWith(KEYSTORES_URI)) {
            String values = value.substring(KEYSTORES_URI.length());

            String[] aliases = TextUtils.split(values, CA_CERT_ALIAS_DELIMITER);
            for (int i = 0; i < aliases.length; i++) {
                aliases[i] = decodeCaCertificateAlias(aliases[i]);
                if (aliases[i].startsWith(Credentials.CA_CERTIFICATE)) {
                    aliases[i] = aliases[i].substring(Credentials.CA_CERTIFICATE.length());
                }
            }
            return aliases.length != 0 ? aliases : null;
        } else {
            return TextUtils.isEmpty(value) ? null : new String[] {value};
        }
    }

    /**
     * Specify a X.509 certificate that identifies the server.
     *
     * <p>A default name is automatically assigned to the certificate and used
     * with this configuration. The framework takes care of installing the
     * certificate when the config is saved and removing the certificate when
     * the config is removed.
     *
     * @param cert X.509 CA certificate
     * @throws IllegalArgumentException if not a CA certificate
     */
    public void setCaCertificate(@Nullable X509Certificate cert) {
        if (cert != null) {
            if (cert.getBasicConstraints() >= 0) {
                mCaCerts = new X509Certificate[] {cert};
            } else {
                throw new IllegalArgumentException("Not a CA certificate");
            }
        } else {
            mCaCerts = null;
        }
    }

    /**
     * Get CA certificate. If multiple CA certificates are configured previously,
     * return the first one.
     * @return X.509 CA certificate
     */
    @Nullable public X509Certificate getCaCertificate() {
        if (mCaCerts != null && mCaCerts.length > 0) {
            return mCaCerts[0];
        } else {
            return null;
        }
    }

    /**
     * Specify a list of X.509 certificates that identifies the server. The validation
     * passes if the CA of server certificate matches one of the given certificates.

     * <p>Default names are automatically assigned to the certificates and used
     * with this configuration. The framework takes care of installing the
     * certificates when the config is saved and removing the certificates when
     * the config is removed.
     *
     * @param certs X.509 CA certificates
     * @throws IllegalArgumentException if any of the provided certificates is
     *     not a CA certificate
     */
    public void setCaCertificates(@Nullable X509Certificate[] certs) {
        if (certs != null) {
            X509Certificate[] newCerts = new X509Certificate[certs.length];
            for (int i = 0; i < certs.length; i++) {
                if (certs[i].getBasicConstraints() >= 0) {
                    newCerts[i] = certs[i];
                } else {
                    throw new IllegalArgumentException("Not a CA certificate");
                }
            }
            mCaCerts = newCerts;
        } else {
            mCaCerts = null;
        }
    }

    /**
     * Get CA certificates.
     */
    @Nullable public X509Certificate[] getCaCertificates() {
        if (mCaCerts != null && mCaCerts.length > 0) {
            return mCaCerts;
        } else {
            return null;
        }
    }

    /**
     * @hide
     */
    public void resetCaCertificate() {
        mCaCerts = null;
    }

    /**
     * Set the ca_path directive on wpa_supplicant.
     *
     * From wpa_supplicant documentation:
     *
     * Directory path for CA certificate files (PEM). This path may contain
     * multiple CA certificates in OpenSSL format. Common use for this is to
     * point to system trusted CA list which is often installed into directory
     * like /etc/ssl/certs. If configured, these certificates are added to the
     * list of trusted CAs. ca_cert may also be included in that case, but it is
     * not required.
     * @param domain The path for CA certificate files
     * @hide
     */
    public void setCaPath(String path) {
        setFieldValue(CA_PATH_KEY, path);
    }

    /**
     * Get the domain_suffix_match value. See setDomSuffixMatch.
     * @return The path for CA certificate files.
     * @hide
     */
    public String getCaPath() {
        return getFieldValue(CA_PATH_KEY, "");
    }

    /** Set Client certificate alias.
     *
     * <p> See the {@link android.security.KeyChain} for details on installing or choosing
     * a certificate
     * </p>
     * @param alias identifies the certificate
     * @hide
     */
    public void setClientCertificateAlias(String alias) {
        setFieldValue(CLIENT_CERT_KEY, alias, CLIENT_CERT_PREFIX);
        setFieldValue(PRIVATE_KEY_ID_KEY, alias, Credentials.USER_PRIVATE_KEY);
        // Also, set engine parameters
        if (TextUtils.isEmpty(alias)) {
            mFields.put(ENGINE_KEY, ENGINE_DISABLE);
            mFields.put(ENGINE_ID_KEY, EMPTY_VALUE);
        } else {
            mFields.put(ENGINE_KEY, ENGINE_ENABLE);
            mFields.put(ENGINE_ID_KEY, convertToQuotedString(ENGINE_ID_KEYSTORE));
        }
    }

    /**
     * Get client certificate alias
     * @return alias to the client certificate
     * @hide
     */
    public String getClientCertificateAlias() {
        return getFieldValue(CLIENT_CERT_KEY, CLIENT_CERT_PREFIX);
    }

    /**
     * Specify a private key and client certificate for client authorization.
     *
     * <p>A default name is automatically assigned to the key entry and used
     * with this configuration.  The framework takes care of installing the
     * key entry when the config is saved and removing the key entry when
     * the config is removed.

     * @param privateKey
     * @param clientCertificate
     * @throws IllegalArgumentException for an invalid key or certificate.
     */
    public void setClientKeyEntry(PrivateKey privateKey, X509Certificate clientCertificate) {
        if (clientCertificate != null) {
            if (clientCertificate.getBasicConstraints() != -1) {
                throw new IllegalArgumentException("Cannot be a CA certificate");
            }
            if (privateKey == null) {
                throw new IllegalArgumentException("Client cert without a private key");
            }
            if (privateKey.getEncoded() == null) {
                throw new IllegalArgumentException("Private key cannot be encoded");
            }
        }

        mClientPrivateKey = privateKey;
        mClientCertificate = clientCertificate;
    }

    /**
     * Get client certificate
     *
     * @return X.509 client certificate
     */
    public X509Certificate getClientCertificate() {
        return mClientCertificate;
    }

    /**
     * @hide
     */
    public void resetClientKeyEntry() {
        mClientPrivateKey = null;
        mClientCertificate = null;
    }

    /**
     * @hide
     */
    public PrivateKey getClientPrivateKey() {
        return mClientPrivateKey;
    }

    /**
     * Set subject match (deprecated). This is the substring to be matched against the subject of
     * the authentication server certificate.
     * @param subjectMatch substring to be matched
     * @deprecated in favor of altSubjectMatch
     */
    public void setSubjectMatch(String subjectMatch) {
        setFieldValue(SUBJECT_MATCH_KEY, subjectMatch, "");
    }

    /**
     * Get subject match (deprecated)
     * @return the subject match string
     * @deprecated in favor of altSubjectMatch
     */
    public String getSubjectMatch() {
        return getFieldValue(SUBJECT_MATCH_KEY, "");
    }

    /**
     * Set alternate subject match. This is the substring to be matched against the
     * alternate subject of the authentication server certificate.
     * @param altSubjectMatch substring to be matched, for example
     *                     DNS:server.example.com;EMAIL:server@example.com
     */
    public void setAltSubjectMatch(String altSubjectMatch) {
        setFieldValue(ALTSUBJECT_MATCH_KEY, altSubjectMatch, "");
    }

    /**
     * Get alternate subject match
     * @return the alternate subject match string
     */
    public String getAltSubjectMatch() {
        return getFieldValue(ALTSUBJECT_MATCH_KEY, "");
    }

    /**
     * Set the domain_suffix_match directive on wpa_supplicant. This is the parameter to use
     * for Hotspot 2.0 defined matching of AAA server certs per WFA HS2.0 spec, section 7.3.3.2,
     * second paragraph.
     *
     * From wpa_supplicant documentation:
     * Constraint for server domain name. If set, this FQDN is used as a suffix match requirement
     * for the AAAserver certificate in SubjectAltName dNSName element(s). If a matching dNSName is
     * found, this constraint is met. If no dNSName values are present, this constraint is matched
     * against SubjectName CN using same suffix match comparison.
     * Suffix match here means that the host/domain name is compared one label at a time starting
     * from the top-level domain and all the labels in domain_suffix_match shall be included in the
     * certificate. The certificate may include additional sub-level labels in addition to the
     * required labels.
     * For example, domain_suffix_match=example.com would match test.example.com but would not
     * match test-example.com.
     * @param domain The domain value
     */
    public void setDomainSuffixMatch(String domain) {
        setFieldValue(DOM_SUFFIX_MATCH_KEY, domain);
    }

    /**
     * Get the domain_suffix_match value. See setDomSuffixMatch.
     * @return The domain value.
     */
    public String getDomainSuffixMatch() {
        return getFieldValue(DOM_SUFFIX_MATCH_KEY, "");
    }

    /**
     * Set realm for passpoint credential; realm identifies a set of networks where your
     * passpoint credential can be used
     * @param realm the realm
     */
    public void setRealm(String realm) {
        setFieldValue(REALM_KEY, realm, "");
    }

    /**
     * Get realm for passpoint credential; see {@link #setRealm(String)} for more information
     * @return the realm
     */
    public String getRealm() {
        return getFieldValue(REALM_KEY, "");
    }

    /**
     * Set plmn (Public Land Mobile Network) of the provider of passpoint credential
     * @param plmn the plmn value derived from mcc (mobile country code) & mnc (mobile network code)
     */
    public void setPlmn(String plmn) {
        setFieldValue(PLMN_KEY, plmn, "");
    }

    /**
     * Get plmn (Public Land Mobile Network) for passpoint credential; see {@link #setPlmn
     * (String)} for more information
     * @return the plmn
     */
    public String getPlmn() {
        return getFieldValue(PLMN_KEY, "");
    }

    /** See {@link WifiConfiguration#getKeyIdForCredentials} @hide */
    public String getKeyId(WifiEnterpriseConfig current) {
        // If EAP method is not initialized, use current config details
        if (mEapMethod == Eap.NONE) {
            return (current != null) ? current.getKeyId(null) : EMPTY_VALUE;
        }
        if (!isEapMethodValid()) {
            return EMPTY_VALUE;
        }
        return Eap.strings[mEapMethod] + "_" + Phase2.strings[mPhase2Method];
    }

    private String removeDoubleQuotes(String string) {
        if (TextUtils.isEmpty(string)) return "";
        int length = string.length();
        if ((length > 1) && (string.charAt(0) == '"')
                && (string.charAt(length - 1) == '"')) {
            return string.substring(1, length - 1);
        }
        return string;
    }

    private String convertToQuotedString(String string) {
        return "\"" + string + "\"";
    }

    /**
     * Returns the index at which the toBeFound string is found in the array.
     * @param arr array of strings
     * @param toBeFound string to be found
     * @param defaultIndex default index to be returned when string is not found
     * @return the index into array
     */
    private int getStringIndex(String arr[], String toBeFound, int defaultIndex) {
        if (TextUtils.isEmpty(toBeFound)) return defaultIndex;
        for (int i = 0; i < arr.length; i++) {
            if (toBeFound.equals(arr[i])) return i;
        }
        return defaultIndex;
    }

    /**
     * Returns the field value for the key.
     * @param key into the hash
     * @param prefix is the prefix that the value may have
     * @return value
     * @hide
     */
    public String getFieldValue(String key, String prefix) {
        // TODO: Should raise an exception if |key| is EAP_KEY or PHASE2_KEY since
        // neither of these keys should be retrieved in this manner.
        String value = mFields.get(key);
        // Uninitialized or known to be empty after reading from supplicant
        if (TextUtils.isEmpty(value) || EMPTY_VALUE.equals(value)) return "";

        value = removeDoubleQuotes(value);
        if (value.startsWith(prefix)) {
            return value.substring(prefix.length());
        } else {
            return value;
        }
    }

    /**
     * Set a value with an optional prefix at key
     * @param key into the hash
     * @param value to be set
     * @param prefix an optional value to be prefixed to actual value
     * @hide
     */
    public void setFieldValue(String key, String value, String prefix) {
        // TODO: Should raise an exception if |key| is EAP_KEY or PHASE2_KEY since
        // neither of these keys should be set in this manner.
        if (TextUtils.isEmpty(value)) {
            mFields.put(key, EMPTY_VALUE);
        } else {
            mFields.put(key, convertToQuotedString(prefix + value));
        }
    }


    /**
     * Set a value with an optional prefix at key
     * @param key into the hash
     * @param value to be set
     * @param prefix an optional value to be prefixed to actual value
     * @hide
     */
    public void setFieldValue(String key, String value) {
        // TODO: Should raise an exception if |key| is EAP_KEY or PHASE2_KEY since
        // neither of these keys should be set in this manner.
        if (TextUtils.isEmpty(value)) {
           mFields.put(key, EMPTY_VALUE);
        } else {
            mFields.put(key, convertToQuotedString(value));
        }
    }

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        for (String key : mFields.keySet()) {
            // Don't display password in toString().
            String value = PASSWORD_KEY.equals(key) ? "<removed>" : mFields.get(key);
            sb.append(key).append(" ").append(value).append("\n");
        }
        return sb.toString();
    }

    /**
     * Returns whether the EAP method data is valid, i.e., whether mEapMethod and mPhase2Method
     * are valid indices into {@code Eap.strings[]} and {@code Phase2.strings[]} respectively.
     */
    private boolean isEapMethodValid() {
        if (mEapMethod == Eap.NONE) {
            Log.e(TAG, "WiFi enterprise configuration is invalid as it supplies no EAP method.");
            return false;
        }
        if (mEapMethod < 0 || mEapMethod >= Eap.strings.length) {
            Log.e(TAG, "mEapMethod is invald for WiFi enterprise configuration: " + mEapMethod);
            return false;
        }
        if (mPhase2Method < 0 || mPhase2Method >= Phase2.strings.length) {
            Log.e(TAG, "mPhase2Method is invald for WiFi enterprise configuration: "
                    + mPhase2Method);
            return false;
        }
        return true;
    }

    ///M: add
     /**
       * For WAPI
       * Set CA certificate 2  alias.
       *
       * <p> See the {@link android.security.KeyChain} for details on installing or choosing
       * a certificate
       * </p>
       * @param alias identifies the certificate
       * @hide
       * @internal
       */
    public void setCaCertificateWapiAlias(String alias) {
        setFieldValue(CA_CERT2_KEY, alias, CA_CERT2_PREFIX);
    }

      /**
       * For WAPI
       * Get CA certificate 2 alias
       * @return alias to the CA certificate
       * @hide
       * @internal
       */
    public String getCaCertificateWapiAlias() {
        return getFieldValue(CA_CERT2_KEY, CA_CERT2_PREFIX);
    }


      /**
       * For WAPI
       * Set Client certificate 2 alias.
       *
       * <p> See the {@link android.security.KeyChain} for details on installing or choosing
       * a certificate
       * </p>
       * @param alias identifies the certificate
       * @hide
       * @internal
       */
      public void setClientCertificateWapiAlias(String alias) {

          setFieldValue(CLIENT_CERT_KEY, alias, CLIENT_CERT2_PREFIX);
          setFieldValue(PRIVATE_KEY_ID_KEY, alias, USER_PRIVATE_KEY2_PREFIX);
          // Also, set engine parameters
          if (TextUtils.isEmpty(alias)) {
              mFields.put(ENGINE_KEY, ENGINE_DISABLE);
              mFields.put(ENGINE_ID_KEY, EMPTY_VALUE);
          } else {
              mFields.put(ENGINE_KEY, ENGINE_ENABLE);
              mFields.put(ENGINE_ID_KEY, convertToQuotedString(ENGINE_ID_KEYSTORE));
          }
      }

      /**
       * For WAPI
       * Get client certificate 2 alias
       * @return alias to the client certificate
       * @hide
       * @internal
       */
      public String getClientCertificateWapiAlias() {
          return getFieldValue(CLIENT_CERT_KEY, CLIENT_CERT2_PREFIX);
      }

}
