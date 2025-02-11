/*
 * Copyright (C) 2012 The Android Open Source Project
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

import android.os.Parcel;
import android.os.Parcelable;
import android.os.SystemProperties;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.util.Locale;

/**
 * Stores SSID octets and handles conversion.
 *
 * For Ascii encoded string, any octet < 32 or > 127 is encoded as
 * a "\x" followed by the hex representation of the octet.
 * Exception chars are ", \, \e, \n, \r, \t which are escaped by a \
 * See src/utils/common.c for the implementation in the supplicant.
 *
 * @hide
 */
public class WifiSsid implements Parcelable {
    private static final String TAG = "WifiSsid";

    public final ByteArrayOutputStream octets = new ByteArrayOutputStream(32);

    private static final int HEX_RADIX = 16;
    public static final String NONE = "<unknown ssid>";

    ///M: For GBK
    private boolean mIsGbkEncoding = false;

    private WifiSsid() {
    }

    public static WifiSsid createFromAsciiEncoded(String asciiEncoded) {
        WifiSsid a = new WifiSsid();
        a.convertToBytes(asciiEncoded);
        return a;
    }

    public static WifiSsid createFromHex(String hexStr) {
        WifiSsid a = new WifiSsid();
        if (hexStr == null) return a;

        if (hexStr.startsWith("0x") || hexStr.startsWith("0X")) {
            hexStr = hexStr.substring(2);
        }

        for (int i = 0; i < hexStr.length()-1; i += 2) {
            int val;
            try {
                val = Integer.parseInt(hexStr.substring(i, i + 2), HEX_RADIX);
            } catch(NumberFormatException e) {
                val = 0;
            }
            a.octets.write(val);
        }
        ///M: For GBK
        a.checkAndSetIsGbkEncoding();
        return a;
    }

    /* This function is equivalent to printf_decode() at src/utils/common.c in
     * the supplicant */
    private void convertToBytes(String asciiEncoded) {
        int i = 0;
        int val = 0;
        while (i< asciiEncoded.length()) {
            char c = asciiEncoded.charAt(i);
            switch (c) {
                case '\\':
                    i++;
                    switch(asciiEncoded.charAt(i)) {
                        case '\\':
                            octets.write('\\');
                            i++;
                            break;
                        case '"':
                            octets.write('"');
                            i++;
                            break;
                        case 'n':
                            octets.write('\n');
                            i++;
                            break;
                        case 'r':
                            octets.write('\r');
                            i++;
                            break;
                        case 't':
                            octets.write('\t');
                            i++;
                            break;
                        case 'e':
                            octets.write(27); //escape char
                            i++;
                            break;
                        case 'x':
                            i++;
                            try {
                                val = Integer.parseInt(asciiEncoded.substring(i, i + 2), HEX_RADIX);
                            } catch (NumberFormatException e) {
                                val = -1;
                            }
                            if (val < 0) {
                                val = Character.digit(asciiEncoded.charAt(i), HEX_RADIX);
                                if (val < 0) break;
                                octets.write(val);
                                i++;
                            } else {
                                octets.write(val);
                                i += 2;
                            }
                            break;
                        case '0':
                        case '1':
                        case '2':
                        case '3':
                        case '4':
                        case '5':
                        case '6':
                        case '7':
                            val = asciiEncoded.charAt(i) - '0';
                            i++;
                            if (asciiEncoded.charAt(i) >= '0' && asciiEncoded.charAt(i) <= '7') {
                                val = val * 8 + asciiEncoded.charAt(i) - '0';
                                i++;
                            }
                            if (asciiEncoded.charAt(i) >= '0' && asciiEncoded.charAt(i) <= '7') {
                                val = val * 8 + asciiEncoded.charAt(i) - '0';
                                i++;
                            }
                            octets.write(val);
                            break;
                        default:
                            break;
                    }
                    break;
                default:
                    octets.write(c);
                    i++;
                    break;
            }
        }
        ///M: For GBK
        checkAndSetIsGbkEncoding();
    }

    @Override
    public String toString() {
        byte[] ssidBytes = octets.toByteArray();
        // Supplicant returns \x00\x00\x00\x00\x00\x00\x00\x00 hex string
        // for a hidden access point. Make sure we maintain the previous
        // behavior of returning empty string for this case.
        if (octets.size() <= 0 || isArrayAllZeroes(ssidBytes)) return "";
        // TODO: Handle conversion to other charsets upon failure
        boolean DBG = SystemProperties.get("persist.wifi.gbk.debug").equals("1");
        boolean ssidGbkEncoding = SystemProperties.get("persist.wifi.gbk.encoding").equals("1");
        Charset charset = Charset.forName("UTF-8");
        if (ssidGbkEncoding || mIsGbkEncoding) {
            charset = Charset.forName("GB2312");
        }
        CharsetDecoder decoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);
        CharBuffer out = CharBuffer.allocate(32);

        CoderResult result = decoder.decode(ByteBuffer.wrap(ssidBytes), out, true);
        out.flip();
        if (result.isError()) {
            return NONE;
        }
        if (DBG) Log.d(TAG, "persist.wifi.gbk.encoding: " + ssidGbkEncoding
                + ", isGbk: " + mIsGbkEncoding + ", toString: " + out.toString());
        return out.toString();
    }

    private boolean isArrayAllZeroes(byte[] ssidBytes) {
        for (int i = 0; i< ssidBytes.length; i++) {
            if (ssidBytes[i] != 0) return false;
        }
        return true;
    }

    /** @hide */
    public boolean isHidden() {
        return isArrayAllZeroes(octets.toByteArray());
    }

    /** @hide */
    public byte[] getOctets() {
        return octets.toByteArray();
    }

    /** @hide */
    public String getHexString() {
        String out = "0x";
        byte[] ssidbytes = getOctets();
        for (int i = 0; i < octets.size(); i++) {
            out += String.format(Locale.US, "%02x", ssidbytes[i]);
        }
        return (octets.size() > 0) ? out : null;
    }

    /** Implement the Parcelable interface {@hide} */
    public int describeContents() {
        return 0;
    }

    /** Implement the Parcelable interface {@hide} */
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(octets.size());
        dest.writeByteArray(octets.toByteArray());
        dest.writeInt(mIsGbkEncoding? 1 : 0);
    }

    /** Implement the Parcelable interface {@hide} */
    public static final Creator<WifiSsid> CREATOR =
        new Creator<WifiSsid>() {
            public WifiSsid createFromParcel(Parcel in) {
                WifiSsid ssid = new WifiSsid();
                int length = in.readInt();
                byte b[] = new byte[length];
                in.readByteArray(b);
                ssid.octets.write(b, 0, length);
                ssid.mIsGbkEncoding = in.readInt() != 0;
                return ssid;
            }

            public WifiSsid[] newArray(int size) {
                return new WifiSsid[size];
            }
        };

    ///M: For GBK @{
    private static boolean isGBK(byte[] byteArray, int ssidStartPos,
                          int ssidEndPos) {
        boolean DBG = SystemProperties.get("persist.wifi.gbk.debug").equals("1");
        if (isNotUtf8(byteArray, ssidStartPos, ssidEndPos)) {
            if (DBG) Log.d(TAG, "is not utf8");
            return true;
        } else {
            if (DBG) Log.d(TAG, "is utf8 format");
            return false;
        }
    }

    private static boolean isNotUtf8(byte[] input, int ssidStartPos, int ssidEndPos) {
        int nBytes = 0, lastWildcar = 0;
        byte chr;
        boolean isAllAscii = true;
        boolean isAllGBK = true;
        boolean isWildcardChar = false;
        for (int i = ssidStartPos; i < ssidEndPos && i < input.length; i++) {
            chr = input[i];
            if (!isASCII(chr)) {
                isAllAscii = false;
                isWildcardChar = !isWildcardChar;
                if (isWildcardChar && i < input.length - 1) {
                    byte chr1 = input[i + 1];
                    if (!isGBKChar(chr, chr1)) {
                        isAllGBK = false;
                    }
                }
            } else {
                isWildcardChar = false;
            }
            if (0 == nBytes) {
                if ((chr & 0xFF) >= (0x80 & 0xFF)) {
                    lastWildcar = i;
                    nBytes = getUtf8CharLen(chr);
                    if (nBytes == 0) {
                        return true;
                    }
                    nBytes--;
                }
            } else {
                if ((chr & 0xC0) != 0x80) {
                    break;
                }
                nBytes--;
            }
        }
        //Log.d(TAG, "nBytes > 0: " + (nBytes > 0) + ", isAllAscii: " + isAllAscii);
        if (nBytes > 0) {
            if (isAllAscii) {
                return false;
            } else if (isAllGBK) {
                return true;
            } else {
                nBytes = getUtf8CharLen(input[lastWildcar]);
                for (int j = lastWildcar; j < (lastWildcar + nBytes) &&  j < input.length; j++) {
                    if (!isASCII(input[j])) {
                        input[j] = 0x20;
                    }
                }
                return false;
            }
        }
        return false;
    }

    private static int getUtf8CharLen(byte firstByte) {
        int nBytes = 0;
        if (firstByte >= (byte) 0xFC && firstByte <= (byte) 0xFD) {
            nBytes = 6;
        } else if (firstByte >= (byte) 0xF8) {
            nBytes = 5;
        } else if (firstByte >= (byte) 0xF0) {
            nBytes = 4;
        } else if (firstByte >= (byte) 0xE0) {
            nBytes = 3;
        } else if (firstByte >= (byte) 0xC0) {
            nBytes = 2;
        } else {
            return 0;
        }
        return nBytes;
    }

    private static boolean isASCII(byte b) {
        if ((b & 0x80) == 0) {
            return true;
        }
        return false;
    }

    private static boolean isGBKChar(byte head, byte tail) {
        int b0 = head & 0xff;
        int b1 = tail & 0xff;
        if ((b0 >= 0xA1 && b0 <= 0xA9 && b1 >= 0xA1 && b1 <= 0xFE) ||
            (b0 >= 0xB0 && b0 <= 0xF7 && b1 >= 0xA1 && b1 <= 0xFE) ||
            (b0 >= 0x81 && b0 <= 0xA0 && b1 >= 0x40 && b1 <= 0xFE) ||
            (b0 >= 0xAA && b0 <= 0xFE && b1 >= 0x40 && b1 <= 0xA0 && b1 != 0x7F) ||
            (b0 >= 0xA8 && b0 <= 0xA9 && b1 >= 0x40 && b1 <= 0xA0 && b1 != 0x7F) ||
            (b0 >= 0xAA && b0 <= 0xAF && b1 >= 0xA1 && b1 <= 0xFE && b1 != 0x7F) ||
            (b0 >= 0xF8 && b0 <= 0xFE && b1 >= 0xA1 && b1 <= 0xFE) ||
            (b0 >= 0xA1 && b0 <= 0xA7 && b1 >= 0x40 && b1 <= 0xA0 && b1 != 0x7F)) {
                return true;
            }
        return false;
    }

    private void checkAndSetIsGbkEncoding() {
        byte[] ssidBytes = octets.toByteArray();
        mIsGbkEncoding = isGBK(ssidBytes, 0, ssidBytes.length);
    }

    /** @hide */
    public boolean isGBK(){
        return mIsGbkEncoding;
    }
    ///@}
}
