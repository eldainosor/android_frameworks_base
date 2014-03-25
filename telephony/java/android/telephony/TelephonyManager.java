/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.telephony;

import android.annotation.PrivateApi;
import android.annotation.SdkConstant;
import android.annotation.SdkConstant.SdkConstantType;
import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.telephony.Rlog;
import android.util.Log;

import com.android.internal.telephony.IPhoneSubInfo;
import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.ITelephonyListener;
import com.android.internal.telephony.ITelephonyRegistry;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.RILConstants;
import com.android.internal.telephony.TelephonyProperties;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Provides access to information about the telephony services on
 * the device. Applications can use the methods in this class to
 * determine telephony services and states, as well as to access some
 * types of subscriber information. Applications can also register
 * a listener to receive notification of telephony state changes.
 * <p>
 * You do not instantiate this class directly; instead, you retrieve
 * a reference to an instance through
 * {@link android.content.Context#getSystemService
 * Context.getSystemService(Context.TELEPHONY_SERVICE)}.
 * <p>
 * Note that access to some telephony information is
 * permission-protected. Your application cannot access the protected
 * information unless it has the appropriate permissions declared in
 * its manifest file. Where permissions apply, they are noted in the
 * the methods through which you access the protected information.
 */
public class TelephonyManager {
    private static final String TAG = "TelephonyManager";

    private static ITelephonyRegistry sRegistry;

    /**
     * The allowed states of Wi-Fi calling.
     *
     * @hide
     */
    public interface WifiCallingChoices {
        /** Always use Wi-Fi calling */
        static final int ALWAYS_USE = 0;
        /** Ask the user whether to use Wi-Fi on every call */
        static final int ASK_EVERY_TIME = 1;
        /** Never use Wi-Fi calling */
        static final int NEVER_USE = 2;
    }

    private final HashMap<CallStateListener,Listener> mListeners
            = new HashMap<CallStateListener,Listener>();
    private final Context mContext;

    private static class Listener extends ITelephonyListener.Stub {
        final CallStateListener mListener;
        private static final int WHAT = 1;

        private Handler mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                mListener.onCallStateChanged(msg.arg1, msg.arg2, (String)msg.obj);
            }
        };

        Listener(CallStateListener listener) {
            mListener = listener;
        }

        @Override
        public void onUpdate(final int callId, final int state, final String number) {
            if (mHandler != null) {
                mHandler.sendMessage(mHandler.obtainMessage(WHAT, callId, state, number));
            }
        }

        void clearQueue() {
            mHandler.removeMessages(WHAT);

            // Don't accept more incoming binder calls either.
            mHandler = null;
        }
    }

    /** @hide */
    public TelephonyManager(Context context) {
        Context appContext = context.getApplicationContext();
        if (appContext != null) {
            mContext = appContext;
        } else {
            mContext = context;
        }

        if (sRegistry == null) {
            sRegistry = ITelephonyRegistry.Stub.asInterface(ServiceManager.getService(
                    "telephony.registry"));
        }
    }

    /** @hide */
    private TelephonyManager() {
        mContext = null;
    }

    private static TelephonyManager sInstance = new TelephonyManager();

    /** @hide
    /* @deprecated - use getSystemService as described above */
    public static TelephonyManager getDefault() {
        return sInstance;
    }

    /** {@hide} */
    public static TelephonyManager from(Context context) {
        return (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
    }

    //
    // Broadcast Intent actions
    //

    /**
     * Broadcast intent action indicating that the call state (cellular)
     * on the device has changed.
     *
     * <p>
     * The {@link #EXTRA_STATE} extra indicates the new call state.
     * If the new state is RINGING, a second extra
     * {@link #EXTRA_INCOMING_NUMBER} provides the incoming phone number as
     * a String.
     *
     * <p class="note">
     * Requires the READ_PHONE_STATE permission.
     *
     * <p class="note">
     * This was a {@link android.content.Context#sendStickyBroadcast sticky}
     * broadcast in version 1.0, but it is no longer sticky.
     * Instead, use {@link #getCallState} to synchronously query the current call state.
     *
     * @see #EXTRA_STATE
     * @see #EXTRA_INCOMING_NUMBER
     * @see #getCallState
     */
    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String ACTION_PHONE_STATE_CHANGED =
            "android.intent.action.PHONE_STATE";

    /**
     * The Phone app sends this intent when a user opts to respond-via-message during an incoming
     * call. By default, the device's default SMS app consumes this message and sends a text message
     * to the caller. A third party app can also provide this functionality by consuming this Intent
     * with a {@link android.app.Service} and sending the message using its own messaging system.
     * <p>The intent contains a URI (available from {@link android.content.Intent#getData})
     * describing the recipient, using either the {@code sms:}, {@code smsto:}, {@code mms:},
     * or {@code mmsto:} URI schema. Each of these URI schema carry the recipient information the
     * same way: the path part of the URI contains the recipient's phone number or a comma-separated
     * set of phone numbers if there are multiple recipients. For example, {@code
     * smsto:2065551234}.</p>
     *
     * <p>The intent may also contain extras for the message text (in {@link
     * android.content.Intent#EXTRA_TEXT}) and a message subject
     * (in {@link android.content.Intent#EXTRA_SUBJECT}).</p>
     *
     * <p class="note"><strong>Note:</strong>
     * The intent-filter that consumes this Intent needs to be in a {@link android.app.Service}
     * that requires the
     * permission {@link android.Manifest.permission#SEND_RESPOND_VIA_MESSAGE}.</p>
     * <p>For example, the service that receives this intent can be declared in the manifest file
     * with an intent filter like this:</p>
     * <pre>
     * &lt;!-- Service that delivers SMS messages received from the phone "quick response" -->
     * &lt;service android:name=".HeadlessSmsSendService"
     *          android:permission="android.permission.SEND_RESPOND_VIA_MESSAGE"
     *          android:exported="true" >
     *   &lt;intent-filter>
     *     &lt;action android:name="android.intent.action.RESPOND_VIA_MESSAGE" />
     *     &lt;category android:name="android.intent.category.DEFAULT" />
     *     &lt;data android:scheme="sms" />
     *     &lt;data android:scheme="smsto" />
     *     &lt;data android:scheme="mms" />
     *     &lt;data android:scheme="mmsto" />
     *   &lt;/intent-filter>
     * &lt;/service></pre>
     * <p>
     * Output: nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_RESPOND_VIA_MESSAGE =
            "android.intent.action.RESPOND_VIA_MESSAGE";

    /**
     * The lookup key used with the {@link #ACTION_PHONE_STATE_CHANGED} broadcast
     * for a String containing the new call state.
     *
     * @see #EXTRA_STATE_IDLE
     * @see #EXTRA_STATE_RINGING
     * @see #EXTRA_STATE_OFFHOOK
     *
     * <p class="note">
     * Retrieve with
     * {@link android.content.Intent#getStringExtra(String)}.
     */
    public static final String EXTRA_STATE = PhoneConstants.STATE_KEY;

    /**
     * Value used with {@link #EXTRA_STATE} corresponding to
     * {@link #CALL_STATE_IDLE}.
     */
    public static final String EXTRA_STATE_IDLE = PhoneConstants.State.IDLE.toString();

    /**
     * Value used with {@link #EXTRA_STATE} corresponding to
     * {@link #CALL_STATE_RINGING}.
     */
    public static final String EXTRA_STATE_RINGING = PhoneConstants.State.RINGING.toString();

    /**
     * Value used with {@link #EXTRA_STATE} corresponding to
     * {@link #CALL_STATE_OFFHOOK}.
     */
    public static final String EXTRA_STATE_OFFHOOK = PhoneConstants.State.OFFHOOK.toString();

    /**
     * The lookup key used with the {@link #ACTION_PHONE_STATE_CHANGED} broadcast
     * for a String containing the incoming phone number.
     * Only valid when the new call state is RINGING.
     *
     * <p class="note">
     * Retrieve with
     * {@link android.content.Intent#getStringExtra(String)}.
     */
    public static final String EXTRA_INCOMING_NUMBER = "incoming_number";

    /**
     * Broadcast intent action indicating that a precise call state
     * (cellular) on the device has changed.
     *
     * <p>
     * The {@link #EXTRA_RINGING_CALL_STATE} extra indicates the ringing call state.
     * The {@link #EXTRA_FOREGROUND_CALL_STATE} extra indicates the foreground call state.
     * The {@link #EXTRA_BACKGROUND_CALL_STATE} extra indicates the background call state.
     * The {@link #EXTRA_DISCONNECT_CAUSE} extra indicates the disconnect cause.
     * The {@link #EXTRA_PRECISE_DISCONNECT_CAUSE} extra indicates the precise disconnect cause.
     *
     * <p class="note">
     * Requires the READ_PRECISE_PHONE_STATE permission.
     *
     * @see #EXTRA_RINGING_CALL_STATE
     * @see #EXTRA_FOREGROUND_CALL_STATE
     * @see #EXTRA_BACKGROUND_CALL_STATE
     * @see #EXTRA_DISCONNECT_CAUSE
     * @see #EXTRA_PRECISE_DISCONNECT_CAUSE
     *
     * <p class="note">
     * Requires the READ_PRECISE_PHONE_STATE permission.
     *
     * @hide
     */
    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String ACTION_PRECISE_CALL_STATE_CHANGED =
            "android.intent.action.PRECISE_CALL_STATE";

    /**
     * The lookup key used with the {@link #ACTION_PRECISE_CALL_STATE_CHANGED} broadcast
     * for an integer containing the state of the current ringing call.
     *
     * @see PreciseCallState#PRECISE_CALL_STATE_NOT_VALID
     * @see PreciseCallState#PRECISE_CALL_STATE_IDLE
     * @see PreciseCallState#PRECISE_CALL_STATE_ACTIVE
     * @see PreciseCallState#PRECISE_CALL_STATE_HOLDING
     * @see PreciseCallState#PRECISE_CALL_STATE_DIALING
     * @see PreciseCallState#PRECISE_CALL_STATE_ALERTING
     * @see PreciseCallState#PRECISE_CALL_STATE_INCOMING
     * @see PreciseCallState#PRECISE_CALL_STATE_WAITING
     * @see PreciseCallState#PRECISE_CALL_STATE_DISCONNECTED
     * @see PreciseCallState#PRECISE_CALL_STATE_DISCONNECTING
     *
     * <p class="note">
     * Retrieve with
     * {@link android.content.Intent#getIntExtra(String name, int defaultValue)}.
     *
     * @hide
     */
    public static final String EXTRA_RINGING_CALL_STATE = "ringing_state";

    /**
     * The lookup key used with the {@link #ACTION_PRECISE_CALL_STATE_CHANGED} broadcast
     * for an integer containing the state of the current foreground call.
     *
     * @see PreciseCallState#PRECISE_CALL_STATE_NOT_VALID
     * @see PreciseCallState#PRECISE_CALL_STATE_IDLE
     * @see PreciseCallState#PRECISE_CALL_STATE_ACTIVE
     * @see PreciseCallState#PRECISE_CALL_STATE_HOLDING
     * @see PreciseCallState#PRECISE_CALL_STATE_DIALING
     * @see PreciseCallState#PRECISE_CALL_STATE_ALERTING
     * @see PreciseCallState#PRECISE_CALL_STATE_INCOMING
     * @see PreciseCallState#PRECISE_CALL_STATE_WAITING
     * @see PreciseCallState#PRECISE_CALL_STATE_DISCONNECTED
     * @see PreciseCallState#PRECISE_CALL_STATE_DISCONNECTING
     *
     * <p class="note">
     * Retrieve with
     * {@link android.content.Intent#getIntExtra(String name, int defaultValue)}.
     *
     * @hide
     */
    public static final String EXTRA_FOREGROUND_CALL_STATE = "foreground_state";

    /**
     * The lookup key used with the {@link #ACTION_PRECISE_CALL_STATE_CHANGED} broadcast
     * for an integer containing the state of the current background call.
     *
     * @see PreciseCallState#PRECISE_CALL_STATE_NOT_VALID
     * @see PreciseCallState#PRECISE_CALL_STATE_IDLE
     * @see PreciseCallState#PRECISE_CALL_STATE_ACTIVE
     * @see PreciseCallState#PRECISE_CALL_STATE_HOLDING
     * @see PreciseCallState#PRECISE_CALL_STATE_DIALING
     * @see PreciseCallState#PRECISE_CALL_STATE_ALERTING
     * @see PreciseCallState#PRECISE_CALL_STATE_INCOMING
     * @see PreciseCallState#PRECISE_CALL_STATE_WAITING
     * @see PreciseCallState#PRECISE_CALL_STATE_DISCONNECTED
     * @see PreciseCallState#PRECISE_CALL_STATE_DISCONNECTING
     *
     * <p class="note">
     * Retrieve with
     * {@link android.content.Intent#getIntExtra(String name, int defaultValue)}.
     *
     * @hide
     */
    public static final String EXTRA_BACKGROUND_CALL_STATE = "background_state";

    /**
     * The lookup key used with the {@link #ACTION_PRECISE_CALL_STATE_CHANGED} broadcast
     * for an integer containing the disconnect cause.
     *
     * @see DisconnectCause
     *
     * <p class="note">
     * Retrieve with
     * {@link android.content.Intent#getIntExtra(String name, int defaultValue)}.
     *
     * @hide
     */
    public static final String EXTRA_DISCONNECT_CAUSE = "disconnect_cause";

    /**
     * The lookup key used with the {@link #ACTION_PRECISE_CALL_STATE_CHANGED} broadcast
     * for an integer containing the disconnect cause provided by the RIL.
     *
     * @see PreciseDisconnectCause
     *
     * <p class="note">
     * Retrieve with
     * {@link android.content.Intent#getIntExtra(String name, int defaultValue)}.
     *
     * @hide
     */
    public static final String EXTRA_PRECISE_DISCONNECT_CAUSE = "precise_disconnect_cause";

    /**
     * Broadcast intent action indicating a data connection has changed,
     * providing precise information about the connection.
     *
     * <p>
     * The {@link #EXTRA_DATA_STATE} extra indicates the connection state.
     * The {@link #EXTRA_DATA_NETWORK_TYPE} extra indicates the connection network type.
     * The {@link #EXTRA_DATA_APN_TYPE} extra indicates the APN type.
     * The {@link #EXTRA_DATA_APN} extra indicates the APN.
     * The {@link #EXTRA_DATA_CHANGE_REASON} extra indicates the connection change reason.
     * The {@link #EXTRA_DATA_IFACE_PROPERTIES} extra indicates the connection interface.
     * The {@link #EXTRA_DATA_FAILURE_CAUSE} extra indicates the connection fail cause.
     *
     * <p class="note">
     * Requires the READ_PRECISE_PHONE_STATE permission.
     *
     * @see #EXTRA_DATA_STATE
     * @see #EXTRA_DATA_NETWORK_TYPE
     * @see #EXTRA_DATA_APN_TYPE
     * @see #EXTRA_DATA_APN
     * @see #EXTRA_DATA_CHANGE_REASON
     * @see #EXTRA_DATA_IFACE
     * @see #EXTRA_DATA_FAILURE_CAUSE
     * @hide
     */
    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String ACTION_PRECISE_DATA_CONNECTION_STATE_CHANGED =
            "android.intent.action.PRECISE_DATA_CONNECTION_STATE_CHANGED";

    /**
     * The lookup key used with the {@link #ACTION_PRECISE_DATA_CONNECTION_STATE_CHANGED} broadcast
     * for an integer containing the state of the current data connection.
     *
     * @see TelephonyManager#DATA_UNKNOWN
     * @see TelephonyManager#DATA_DISCONNECTED
     * @see TelephonyManager#DATA_CONNECTING
     * @see TelephonyManager#DATA_CONNECTED
     * @see TelephonyManager#DATA_SUSPENDED
     *
     * <p class="note">
     * Retrieve with
     * {@link android.content.Intent#getIntExtra(String name, int defaultValue)}.
     *
     * @hide
     */
    public static final String EXTRA_DATA_STATE = PhoneConstants.STATE_KEY;

    /**
     * The lookup key used with the {@link #ACTION_PRECISE_DATA_CONNECTION_STATE_CHANGED} broadcast
     * for an integer containing the network type.
     *
     * @see TelephonyManager#NETWORK_TYPE_UNKNOWN
     * @see TelephonyManager#NETWORK_TYPE_GPRS
     * @see TelephonyManager#NETWORK_TYPE_EDGE
     * @see TelephonyManager#NETWORK_TYPE_UMTS
     * @see TelephonyManager#NETWORK_TYPE_CDMA
     * @see TelephonyManager#NETWORK_TYPE_EVDO_0
     * @see TelephonyManager#NETWORK_TYPE_EVDO_A
     * @see TelephonyManager#NETWORK_TYPE_1xRTT
     * @see TelephonyManager#NETWORK_TYPE_HSDPA
     * @see TelephonyManager#NETWORK_TYPE_HSUPA
     * @see TelephonyManager#NETWORK_TYPE_HSPA
     * @see TelephonyManager#NETWORK_TYPE_IDEN
     * @see TelephonyManager#NETWORK_TYPE_EVDO_B
     * @see TelephonyManager#NETWORK_TYPE_LTE
     * @see TelephonyManager#NETWORK_TYPE_EHRPD
     * @see TelephonyManager#NETWORK_TYPE_HSPAP
     *
     * <p class="note">
     * Retrieve with
     * {@link android.content.Intent#getIntExtra(String name, int defaultValue)}.
     *
     * @hide
     */
    public static final String EXTRA_DATA_NETWORK_TYPE = PhoneConstants.DATA_NETWORK_TYPE_KEY;

    /**
     * The lookup key used with the {@link #ACTION_PRECISE_DATA_CONNECTION_STATE_CHANGED} broadcast
     * for an String containing the data APN type.
     *
     * <p class="note">
     * Retrieve with
     * {@link android.content.Intent#getStringExtra(String name)}.
     *
     * @hide
     */
    public static final String EXTRA_DATA_APN_TYPE = PhoneConstants.DATA_APN_TYPE_KEY;

    /**
     * The lookup key used with the {@link #ACTION_PRECISE_DATA_CONNECTION_STATE_CHANGED} broadcast
     * for an String containing the data APN.
     *
     * <p class="note">
     * Retrieve with
     * {@link android.content.Intent#getStringExtra(String name)}.
     *
     * @hide
     */
    public static final String EXTRA_DATA_APN = PhoneConstants.DATA_APN_KEY;

    /**
     * The lookup key used with the {@link #ACTION_PRECISE_DATA_CONNECTION_STATE_CHANGED} broadcast
     * for an String representation of the change reason.
     *
     * <p class="note">
     * Retrieve with
     * {@link android.content.Intent#getStringExtra(String name)}.
     *
     * @hide
     */
    public static final String EXTRA_DATA_CHANGE_REASON = PhoneConstants.STATE_CHANGE_REASON_KEY;

    /**
     * The lookup key used with the {@link #ACTION_PRECISE_DATA_CONNECTION_STATE_CHANGED} broadcast
     * for an String representation of the data interface.
     *
     * <p class="note">
     * Retrieve with
     * {@link android.content.Intent#getParcelableExtra(String name)}.
     *
     * @hide
     */
    public static final String EXTRA_DATA_LINK_PROPERTIES_KEY = PhoneConstants.DATA_LINK_PROPERTIES_KEY;

    /**
     * The lookup key used with the {@link #ACTION_PRECISE_DATA_CONNECTION_STATE_CHANGED} broadcast
     * for the data connection fail cause.
     *
     * <p class="note">
     * Retrieve with
     * {@link android.content.Intent#getStringExtra(String name)}.
     *
     * @hide
     */
    public static final String EXTRA_DATA_FAILURE_CAUSE = PhoneConstants.DATA_FAILURE_CAUSE_KEY;

    //
    //
    // Device Info
    //
    //

    /**
     * Returns the software version number for the device, for example,
     * the IMEI/SV for GSM phones. Return null if the software version is
     * not available.
     *
     * <p>Requires Permission:
     *   {@link android.Manifest.permission#READ_PHONE_STATE READ_PHONE_STATE}
     */
    public String getDeviceSoftwareVersion() {
        try {
            return getSubscriberInfo().getDeviceSvn();
        } catch (RemoteException ex) {
            return null;
        } catch (NullPointerException ex) {
            return null;
        }
    }

    /**
     * Returns the unique device ID, for example, the IMEI for GSM and the MEID
     * or ESN for CDMA phones. Return null if device ID is not available.
     *
     * <p>Requires Permission:
     *   {@link android.Manifest.permission#READ_PHONE_STATE READ_PHONE_STATE}
     */
    public String getDeviceId() {
        try {
            return getSubscriberInfo().getDeviceId();
        } catch (RemoteException ex) {
            return null;
        } catch (NullPointerException ex) {
            return null;
        }
    }

    /**
     * Returns the current location of the device.
     *<p>
     * If there is only one radio in the device and that radio has an LTE connection,
     * this method will return null. The implementation must not to try add LTE
     * identifiers into the existing cdma/gsm classes.
     *<p>
     * In the future this call will be deprecated.
     *<p>
     * @return Current location of the device or null if not available.
     *
     * <p>Requires Permission:
     * {@link android.Manifest.permission#ACCESS_COARSE_LOCATION ACCESS_COARSE_LOCATION} or
     * {@link android.Manifest.permission#ACCESS_COARSE_LOCATION ACCESS_FINE_LOCATION}.
     */
    public CellLocation getCellLocation() {
        try {
            Bundle bundle = getITelephony().getCellLocation();
            if (bundle.isEmpty()) return null;
            CellLocation cl = CellLocation.newFromBundle(bundle);
            if (cl.isEmpty())
                return null;
            return cl;
        } catch (RemoteException ex) {
            return null;
        } catch (NullPointerException ex) {
            return null;
        }
    }

    /**
     * Enables location update notifications.  {@link PhoneStateListener#onCellLocationChanged
     * PhoneStateListener.onCellLocationChanged} will be called on location updates.
     *
     * <p>Requires Permission: {@link android.Manifest.permission#CONTROL_LOCATION_UPDATES
     * CONTROL_LOCATION_UPDATES}
     *
     * @hide
     */
    public void enableLocationUpdates() {
        try {
            getITelephony().enableLocationUpdates();
        } catch (RemoteException ex) {
        } catch (NullPointerException ex) {
        }
    }

    /**
     * Disables location update notifications.  {@link PhoneStateListener#onCellLocationChanged
     * PhoneStateListener.onCellLocationChanged} will be called on location updates.
     *
     * <p>Requires Permission: {@link android.Manifest.permission#CONTROL_LOCATION_UPDATES
     * CONTROL_LOCATION_UPDATES}
     *
     * @hide
     */
    public void disableLocationUpdates() {
        try {
            getITelephony().disableLocationUpdates();
        } catch (RemoteException ex) {
        } catch (NullPointerException ex) {
        }
    }

    /**
     * Returns the neighboring cell information of the device. The getAllCellInfo is preferred
     * and use this only if getAllCellInfo return nulls or an empty list.
     *<p>
     * In the future this call will be deprecated.
     *<p>
     * @return List of NeighboringCellInfo or null if info unavailable.
     *
     * <p>Requires Permission:
     * (@link android.Manifest.permission#ACCESS_COARSE_UPDATES}
     */
    public List<NeighboringCellInfo> getNeighboringCellInfo() {
        try {
            return getITelephony().getNeighboringCellInfo(mContext.getOpPackageName());
        } catch (RemoteException ex) {
            return null;
        } catch (NullPointerException ex) {
            return null;
        }
    }

    /** No phone radio. */
    public static final int PHONE_TYPE_NONE = PhoneConstants.PHONE_TYPE_NONE;
    /** Phone radio is GSM. */
    public static final int PHONE_TYPE_GSM = PhoneConstants.PHONE_TYPE_GSM;
    /** Phone radio is CDMA. */
    public static final int PHONE_TYPE_CDMA = PhoneConstants.PHONE_TYPE_CDMA;
    /** Phone is via SIP. */
    public static final int PHONE_TYPE_SIP = PhoneConstants.PHONE_TYPE_SIP;

    /**
     * Returns the current phone type.
     * TODO: This is a last minute change and hence hidden.
     *
     * @see #PHONE_TYPE_NONE
     * @see #PHONE_TYPE_GSM
     * @see #PHONE_TYPE_CDMA
     * @see #PHONE_TYPE_SIP
     *
     * {@hide}
     */
    public int getCurrentPhoneType() {
        try{
            ITelephony telephony = getITelephony();
            if (telephony != null) {
                return telephony.getActivePhoneType();
            } else {
                // This can happen when the ITelephony interface is not up yet.
                return getPhoneTypeFromProperty();
            }
        } catch (RemoteException ex) {
            // This shouldn't happen in the normal case, as a backup we
            // read from the system property.
            return getPhoneTypeFromProperty();
        } catch (NullPointerException ex) {
            // This shouldn't happen in the normal case, as a backup we
            // read from the system property.
            return getPhoneTypeFromProperty();
        }
    }

    /**
     * Returns a constant indicating the device phone type.  This
     * indicates the type of radio used to transmit voice calls.
     *
     * @see #PHONE_TYPE_NONE
     * @see #PHONE_TYPE_GSM
     * @see #PHONE_TYPE_CDMA
     * @see #PHONE_TYPE_SIP
     */
    public int getPhoneType() {
        if (!isVoiceCapable()) {
            return PHONE_TYPE_NONE;
        }
        return getCurrentPhoneType();
    }

    private int getPhoneTypeFromProperty() {
        int type =
            SystemProperties.getInt(TelephonyProperties.CURRENT_ACTIVE_PHONE,
                    getPhoneTypeFromNetworkType());
        return type;
    }

    private int getPhoneTypeFromNetworkType() {
        // When the system property CURRENT_ACTIVE_PHONE, has not been set,
        // use the system property for default network type.
        // This is a fail safe, and can only happen at first boot.
        int mode = SystemProperties.getInt("ro.telephony.default_network", -1);
        if (mode == -1)
            return PHONE_TYPE_NONE;
        return getPhoneType(mode);
    }

    /**
     * This function returns the type of the phone, depending
     * on the network mode.
     *
     * @param networkMode
     * @return Phone Type
     *
     * @hide
     */
    public static int getPhoneType(int networkMode) {
        switch(networkMode) {
        case RILConstants.NETWORK_MODE_CDMA:
        case RILConstants.NETWORK_MODE_CDMA_NO_EVDO:
        case RILConstants.NETWORK_MODE_EVDO_NO_CDMA:
            return PhoneConstants.PHONE_TYPE_CDMA;

        case RILConstants.NETWORK_MODE_WCDMA_PREF:
        case RILConstants.NETWORK_MODE_GSM_ONLY:
        case RILConstants.NETWORK_MODE_WCDMA_ONLY:
        case RILConstants.NETWORK_MODE_GSM_UMTS:
        case RILConstants.NETWORK_MODE_LTE_GSM_WCDMA:
        case RILConstants.NETWORK_MODE_LTE_WCDMA:
        case RILConstants.NETWORK_MODE_LTE_CDMA_EVDO_GSM_WCDMA:
            return PhoneConstants.PHONE_TYPE_GSM;

        // Use CDMA Phone for the global mode including CDMA
        case RILConstants.NETWORK_MODE_GLOBAL:
        case RILConstants.NETWORK_MODE_LTE_CDMA_EVDO:
            return PhoneConstants.PHONE_TYPE_CDMA;

        case RILConstants.NETWORK_MODE_LTE_ONLY:
            if (getLteOnCdmaModeStatic() == PhoneConstants.LTE_ON_CDMA_TRUE) {
                return PhoneConstants.PHONE_TYPE_CDMA;
            } else {
                return PhoneConstants.PHONE_TYPE_GSM;
            }
        default:
            return PhoneConstants.PHONE_TYPE_GSM;
        }
    }

    /**
     * The contents of the /proc/cmdline file
     */
    private static String getProcCmdLine()
    {
        String cmdline = "";
        FileInputStream is = null;
        try {
            is = new FileInputStream("/proc/cmdline");
            byte [] buffer = new byte[2048];
            int count = is.read(buffer);
            if (count > 0) {
                cmdline = new String(buffer, 0, count);
            }
        } catch (IOException e) {
            Rlog.d(TAG, "No /proc/cmdline exception=" + e);
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                }
            }
        }
        Rlog.d(TAG, "/proc/cmdline=" + cmdline);
        return cmdline;
    }

    /** Kernel command line */
    private static final String sKernelCmdLine = getProcCmdLine();

    /** Pattern for selecting the product type from the kernel command line */
    private static final Pattern sProductTypePattern =
        Pattern.compile("\\sproduct_type\\s*=\\s*(\\w+)");

    /** The ProductType used for LTE on CDMA devices */
    private static final String sLteOnCdmaProductType =
        SystemProperties.get(TelephonyProperties.PROPERTY_LTE_ON_CDMA_PRODUCT_TYPE, "");

    /**
     * Return if the current radio is LTE on CDMA. This
     * is a tri-state return value as for a period of time
     * the mode may be unknown.
     *
     * @return {@link PhoneConstants#LTE_ON_CDMA_UNKNOWN}, {@link PhoneConstants#LTE_ON_CDMA_FALSE}
     * or {@link PhoneConstants#LTE_ON_CDMA_TRUE}
     *
     * @hide
     */
    public static int getLteOnCdmaModeStatic() {
        int retVal;
        int curVal;
        String productType = "";

        curVal = SystemProperties.getInt(TelephonyProperties.PROPERTY_LTE_ON_CDMA_DEVICE,
                    PhoneConstants.LTE_ON_CDMA_UNKNOWN);
        retVal = curVal;
        if (retVal == PhoneConstants.LTE_ON_CDMA_UNKNOWN) {
            Matcher matcher = sProductTypePattern.matcher(sKernelCmdLine);
            if (matcher.find()) {
                productType = matcher.group(1);
                if (sLteOnCdmaProductType.equals(productType)) {
                    retVal = PhoneConstants.LTE_ON_CDMA_TRUE;
                } else {
                    retVal = PhoneConstants.LTE_ON_CDMA_FALSE;
                }
            } else {
                retVal = PhoneConstants.LTE_ON_CDMA_FALSE;
            }
        }

        Rlog.d(TAG, "getLteOnCdmaMode=" + retVal + " curVal=" + curVal +
                " product_type='" + productType +
                "' lteOnCdmaProductType='" + sLteOnCdmaProductType + "'");
        return retVal;
    }

    //
    //
    // Current Network
    //
    //

    /**
     * Returns the alphabetic name of current registered operator.
     * <p>
     * Availability: Only when user is registered to a network. Result may be
     * unreliable on CDMA networks (use {@link #getPhoneType()} to determine if
     * on a CDMA network).
     */
    public String getNetworkOperatorName() {
        return SystemProperties.get(TelephonyProperties.PROPERTY_OPERATOR_ALPHA);
    }

    /**
     * Returns the numeric name (MCC+MNC) of current registered operator.
     * <p>
     * Availability: Only when user is registered to a network. Result may be
     * unreliable on CDMA networks (use {@link #getPhoneType()} to determine if
     * on a CDMA network).
     */
    public String getNetworkOperator() {
        return SystemProperties.get(TelephonyProperties.PROPERTY_OPERATOR_NUMERIC);
    }

    /**
     * Returns true if the device is considered roaming on the current
     * network, for GSM purposes.
     * <p>
     * Availability: Only when user registered to a network.
     */
    public boolean isNetworkRoaming() {
        return "true".equals(SystemProperties.get(TelephonyProperties.PROPERTY_OPERATOR_ISROAMING));
    }

    /**
     * Returns the ISO country code equivalent of the current registered
     * operator's MCC (Mobile Country Code).
     * <p>
     * Availability: Only when user is registered to a network. Result may be
     * unreliable on CDMA networks (use {@link #getPhoneType()} to determine if
     * on a CDMA network).
     */
    public String getNetworkCountryIso() {
        return SystemProperties.get(TelephonyProperties.PROPERTY_OPERATOR_ISO_COUNTRY);
    }

    /** Network type is unknown */
    public static final int NETWORK_TYPE_UNKNOWN = 0;
    /** Current network is GPRS */
    public static final int NETWORK_TYPE_GPRS = 1;
    /** Current network is EDGE */
    public static final int NETWORK_TYPE_EDGE = 2;
    /** Current network is UMTS */
    public static final int NETWORK_TYPE_UMTS = 3;
    /** Current network is CDMA: Either IS95A or IS95B*/
    public static final int NETWORK_TYPE_CDMA = 4;
    /** Current network is EVDO revision 0*/
    public static final int NETWORK_TYPE_EVDO_0 = 5;
    /** Current network is EVDO revision A*/
    public static final int NETWORK_TYPE_EVDO_A = 6;
    /** Current network is 1xRTT*/
    public static final int NETWORK_TYPE_1xRTT = 7;
    /** Current network is HSDPA */
    public static final int NETWORK_TYPE_HSDPA = 8;
    /** Current network is HSUPA */
    public static final int NETWORK_TYPE_HSUPA = 9;
    /** Current network is HSPA */
    public static final int NETWORK_TYPE_HSPA = 10;
    /** Current network is iDen */
    public static final int NETWORK_TYPE_IDEN = 11;
    /** Current network is EVDO revision B*/
    public static final int NETWORK_TYPE_EVDO_B = 12;
    /** Current network is LTE */
    public static final int NETWORK_TYPE_LTE = 13;
    /** Current network is eHRPD */
    public static final int NETWORK_TYPE_EHRPD = 14;
    /** Current network is HSPA+ */
    public static final int NETWORK_TYPE_HSPAP = 15;

    /**
     * @return the NETWORK_TYPE_xxxx for current data connection.
     */
    public int getNetworkType() {
        return getDataNetworkType();
    }

    /**
     * Returns a constant indicating the radio technology (network type)
     * currently in use on the device for data transmission.
     * @return the network type
     *
     * @see #NETWORK_TYPE_UNKNOWN
     * @see #NETWORK_TYPE_GPRS
     * @see #NETWORK_TYPE_EDGE
     * @see #NETWORK_TYPE_UMTS
     * @see #NETWORK_TYPE_HSDPA
     * @see #NETWORK_TYPE_HSUPA
     * @see #NETWORK_TYPE_HSPA
     * @see #NETWORK_TYPE_CDMA
     * @see #NETWORK_TYPE_EVDO_0
     * @see #NETWORK_TYPE_EVDO_A
     * @see #NETWORK_TYPE_EVDO_B
     * @see #NETWORK_TYPE_1xRTT
     * @see #NETWORK_TYPE_IDEN
     * @see #NETWORK_TYPE_LTE
     * @see #NETWORK_TYPE_EHRPD
     * @see #NETWORK_TYPE_HSPAP
     *
     * @hide
     */
    public int getDataNetworkType() {
        try{
            ITelephony telephony = getITelephony();
            if (telephony != null) {
                return telephony.getDataNetworkType();
            } else {
                // This can happen when the ITelephony interface is not up yet.
                return NETWORK_TYPE_UNKNOWN;
            }
        } catch(RemoteException ex) {
            // This shouldn't happen in the normal case
            return NETWORK_TYPE_UNKNOWN;
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            return NETWORK_TYPE_UNKNOWN;
        }
    }

    /**
     * Returns the NETWORK_TYPE_xxxx for voice
     *
     * @hide
     */
    public int getVoiceNetworkType() {
        try{
            ITelephony telephony = getITelephony();
            if (telephony != null) {
                return telephony.getVoiceNetworkType();
            } else {
                // This can happen when the ITelephony interface is not up yet.
                return NETWORK_TYPE_UNKNOWN;
            }
        } catch(RemoteException ex) {
            // This shouldn't happen in the normal case
            return NETWORK_TYPE_UNKNOWN;
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            return NETWORK_TYPE_UNKNOWN;
        }
    }

    /** Unknown network class. {@hide} */
    public static final int NETWORK_CLASS_UNKNOWN = 0;
    /** Class of broadly defined "2G" networks. {@hide} */
    public static final int NETWORK_CLASS_2_G = 1;
    /** Class of broadly defined "3G" networks. {@hide} */
    public static final int NETWORK_CLASS_3_G = 2;
    /** Class of broadly defined "4G" networks. {@hide} */
    public static final int NETWORK_CLASS_4_G = 3;

    /**
     * Return general class of network type, such as "3G" or "4G". In cases
     * where classification is contentious, this method is conservative.
     *
     * @hide
     */
    public static int getNetworkClass(int networkType) {
        switch (networkType) {
            case NETWORK_TYPE_GPRS:
            case NETWORK_TYPE_EDGE:
            case NETWORK_TYPE_CDMA:
            case NETWORK_TYPE_1xRTT:
            case NETWORK_TYPE_IDEN:
                return NETWORK_CLASS_2_G;
            case NETWORK_TYPE_UMTS:
            case NETWORK_TYPE_EVDO_0:
            case NETWORK_TYPE_EVDO_A:
            case NETWORK_TYPE_HSDPA:
            case NETWORK_TYPE_HSUPA:
            case NETWORK_TYPE_HSPA:
            case NETWORK_TYPE_EVDO_B:
            case NETWORK_TYPE_EHRPD:
            case NETWORK_TYPE_HSPAP:
                return NETWORK_CLASS_3_G;
            case NETWORK_TYPE_LTE:
                return NETWORK_CLASS_4_G;
            default:
                return NETWORK_CLASS_UNKNOWN;
        }
    }

    /**
     * Returns a string representation of the radio technology (network type)
     * currently in use on the device.
     * @return the name of the radio technology
     *
     * @hide pending API council review
     */
    public String getNetworkTypeName() {
        return getNetworkTypeName(getNetworkType());
    }

    /** {@hide} */
    public static String getNetworkTypeName(int type) {
        switch (type) {
            case NETWORK_TYPE_GPRS:
                return "GPRS";
            case NETWORK_TYPE_EDGE:
                return "EDGE";
            case NETWORK_TYPE_UMTS:
                return "UMTS";
            case NETWORK_TYPE_HSDPA:
                return "HSDPA";
            case NETWORK_TYPE_HSUPA:
                return "HSUPA";
            case NETWORK_TYPE_HSPA:
                return "HSPA";
            case NETWORK_TYPE_CDMA:
                return "CDMA";
            case NETWORK_TYPE_EVDO_0:
                return "CDMA - EvDo rev. 0";
            case NETWORK_TYPE_EVDO_A:
                return "CDMA - EvDo rev. A";
            case NETWORK_TYPE_EVDO_B:
                return "CDMA - EvDo rev. B";
            case NETWORK_TYPE_1xRTT:
                return "CDMA - 1xRTT";
            case NETWORK_TYPE_LTE:
                return "LTE";
            case NETWORK_TYPE_EHRPD:
                return "CDMA - eHRPD";
            case NETWORK_TYPE_IDEN:
                return "iDEN";
            case NETWORK_TYPE_HSPAP:
                return "HSPA+";
            default:
                return "UNKNOWN";
        }
    }

    //
    //
    // SIM Card
    //
    //

    /** SIM card state: Unknown. Signifies that the SIM is in transition
     *  between states. For example, when the user inputs the SIM pin
     *  under PIN_REQUIRED state, a query for sim status returns
     *  this state before turning to SIM_STATE_READY. */
    public static final int SIM_STATE_UNKNOWN = 0;
    /** SIM card state: no SIM card is available in the device */
    public static final int SIM_STATE_ABSENT = 1;
    /** SIM card state: Locked: requires the user's SIM PIN to unlock */
    public static final int SIM_STATE_PIN_REQUIRED = 2;
    /** SIM card state: Locked: requires the user's SIM PUK to unlock */
    public static final int SIM_STATE_PUK_REQUIRED = 3;
    /** SIM card state: Locked: requries a network PIN to unlock */
    public static final int SIM_STATE_NETWORK_LOCKED = 4;
    /** SIM card state: Ready */
    public static final int SIM_STATE_READY = 5;

    /**
     * @return true if a ICC card is present
     */
    public boolean hasIccCard() {
        try {
            return getITelephony().hasIccCard();
        } catch (RemoteException ex) {
            // Assume no ICC card if remote exception which shouldn't happen
            return false;
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            return false;
        }
    }

    /**
     * Returns a constant indicating the state of the
     * device SIM card.
     *
     * @see #SIM_STATE_UNKNOWN
     * @see #SIM_STATE_ABSENT
     * @see #SIM_STATE_PIN_REQUIRED
     * @see #SIM_STATE_PUK_REQUIRED
     * @see #SIM_STATE_NETWORK_LOCKED
     * @see #SIM_STATE_READY
     */
    public int getSimState() {
        String prop = SystemProperties.get(TelephonyProperties.PROPERTY_SIM_STATE);
        if ("ABSENT".equals(prop)) {
            return SIM_STATE_ABSENT;
        }
        else if ("PIN_REQUIRED".equals(prop)) {
            return SIM_STATE_PIN_REQUIRED;
        }
        else if ("PUK_REQUIRED".equals(prop)) {
            return SIM_STATE_PUK_REQUIRED;
        }
        else if ("NETWORK_LOCKED".equals(prop)) {
            return SIM_STATE_NETWORK_LOCKED;
        }
        else if ("READY".equals(prop)) {
            return SIM_STATE_READY;
        }
        else {
            return SIM_STATE_UNKNOWN;
        }
    }

    /**
     * Returns the MCC+MNC (mobile country code + mobile network code) of the
     * provider of the SIM. 5 or 6 decimal digits.
     * <p>
     * Availability: SIM state must be {@link #SIM_STATE_READY}
     *
     * @see #getSimState
     */
    public String getSimOperator() {
        return SystemProperties.get(TelephonyProperties.PROPERTY_ICC_OPERATOR_NUMERIC);
    }

    /**
     * Returns the Service Provider Name (SPN).
     * <p>
     * Availability: SIM state must be {@link #SIM_STATE_READY}
     *
     * @see #getSimState
     */
    public String getSimOperatorName() {
        return SystemProperties.get(TelephonyProperties.PROPERTY_ICC_OPERATOR_ALPHA);
    }

    /**
     * Returns the ISO country code equivalent for the SIM provider's country code.
     */
    public String getSimCountryIso() {
        return SystemProperties.get(TelephonyProperties.PROPERTY_ICC_OPERATOR_ISO_COUNTRY);
    }

    /**
     * Returns the serial number of the SIM, if applicable. Return null if it is
     * unavailable.
     * <p>
     * Requires Permission:
     *   {@link android.Manifest.permission#READ_PHONE_STATE READ_PHONE_STATE}
     */
    public String getSimSerialNumber() {
        try {
            return getSubscriberInfo().getIccSerialNumber();
        } catch (RemoteException ex) {
            return null;
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            return null;
        }
    }

    /**
     * Return if the current radio is LTE on CDMA. This
     * is a tri-state return value as for a period of time
     * the mode may be unknown.
     *
     * @return {@link PhoneConstants#LTE_ON_CDMA_UNKNOWN}, {@link PhoneConstants#LTE_ON_CDMA_FALSE}
     * or {@link PhoneConstants#LTE_ON_CDMA_TRUE}
     *
     * @hide
     */
    public int getLteOnCdmaMode() {
        try {
            return getITelephony().getLteOnCdmaMode();
        } catch (RemoteException ex) {
            // Assume no ICC card if remote exception which shouldn't happen
            return PhoneConstants.LTE_ON_CDMA_UNKNOWN;
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            return PhoneConstants.LTE_ON_CDMA_UNKNOWN;
        }
    }

    //
    //
    // Subscriber Info
    //
    //

    /**
     * Returns the unique subscriber ID, for example, the IMSI for a GSM phone.
     * Return null if it is unavailable.
     * <p>
     * Requires Permission:
     *   {@link android.Manifest.permission#READ_PHONE_STATE READ_PHONE_STATE}
     */
    public String getSubscriberId() {
        try {
            return getSubscriberInfo().getSubscriberId();
        } catch (RemoteException ex) {
            return null;
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            return null;
        }
    }

    /**
     * Returns the Group Identifier Level1 for a GSM phone.
     * Return null if it is unavailable.
     * <p>
     * Requires Permission:
     *   {@link android.Manifest.permission#READ_PHONE_STATE READ_PHONE_STATE}
     */
    public String getGroupIdLevel1() {
        try {
            return getSubscriberInfo().getGroupIdLevel1();
        } catch (RemoteException ex) {
            return null;
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            return null;
        }
    }

    /**
     * Returns the phone number string for line 1, for example, the MSISDN
     * for a GSM phone. Return null if it is unavailable.
     * <p>
     * Requires Permission:
     *   {@link android.Manifest.permission#READ_PHONE_STATE READ_PHONE_STATE}
     */
    public String getLine1Number() {
        try {
            return getSubscriberInfo().getLine1Number();
        } catch (RemoteException ex) {
            return null;
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            return null;
        }
    }

    /**
     * Returns the alphabetic identifier associated with the line 1 number.
     * Return null if it is unavailable.
     * <p>
     * Requires Permission:
     *   {@link android.Manifest.permission#READ_PHONE_STATE READ_PHONE_STATE}
     * @hide
     * nobody seems to call this.
     */
    public String getLine1AlphaTag() {
        try {
            return getSubscriberInfo().getLine1AlphaTag();
        } catch (RemoteException ex) {
            return null;
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            return null;
        }
    }

    /**
     * Returns the MSISDN string.
     * for a GSM phone. Return null if it is unavailable.
     * <p>
     * Requires Permission:
     *   {@link android.Manifest.permission#READ_PHONE_STATE READ_PHONE_STATE}
     *
     * @hide
     */
    public String getMsisdn() {
        try {
            return getSubscriberInfo().getMsisdn();
        } catch (RemoteException ex) {
            return null;
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            return null;
        }
    }

    /**
     * Returns the voice mail number. Return null if it is unavailable.
     * <p>
     * Requires Permission:
     *   {@link android.Manifest.permission#READ_PHONE_STATE READ_PHONE_STATE}
     */
    public String getVoiceMailNumber() {
        try {
            return getSubscriberInfo().getVoiceMailNumber();
        } catch (RemoteException ex) {
            return null;
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            return null;
        }
    }

    /**
     * Returns the complete voice mail number. Return null if it is unavailable.
     * <p>
     * Requires Permission:
     *   {@link android.Manifest.permission#CALL_PRIVILEGED CALL_PRIVILEGED}
     *
     * @hide
     */
    public String getCompleteVoiceMailNumber() {
        try {
            return getSubscriberInfo().getCompleteVoiceMailNumber();
        } catch (RemoteException ex) {
            return null;
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            return null;
        }
    }

    /**
     * Returns the voice mail count. Return 0 if unavailable.
     * <p>
     * Requires Permission:
     *   {@link android.Manifest.permission#READ_PHONE_STATE READ_PHONE_STATE}
     * @hide
     */
    public int getVoiceMessageCount() {
        try {
            return getITelephony().getVoiceMessageCount();
        } catch (RemoteException ex) {
            return 0;
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            return 0;
        }
    }

    /**
     * Retrieves the alphabetic identifier associated with the voice
     * mail number.
     * <p>
     * Requires Permission:
     *   {@link android.Manifest.permission#READ_PHONE_STATE READ_PHONE_STATE}
     */
    public String getVoiceMailAlphaTag() {
        try {
            return getSubscriberInfo().getVoiceMailAlphaTag();
        } catch (RemoteException ex) {
            return null;
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            return null;
        }
    }

    /**
     * Returns the IMS private user identity (IMPI) that was loaded from the ISIM.
     * @return the IMPI, or null if not present or not loaded
     * @hide
     */
    public String getIsimImpi() {
        try {
            return getSubscriberInfo().getIsimImpi();
        } catch (RemoteException ex) {
            return null;
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            return null;
        }
    }

    /**
     * Returns the IMS home network domain name that was loaded from the ISIM.
     * @return the IMS domain name, or null if not present or not loaded
     * @hide
     */
    public String getIsimDomain() {
        try {
            return getSubscriberInfo().getIsimDomain();
        } catch (RemoteException ex) {
            return null;
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            return null;
        }
    }

    /**
     * Returns the IMS public user identities (IMPU) that were loaded from the ISIM.
     * @return an array of IMPU strings, with one IMPU per string, or null if
     *      not present or not loaded
     * @hide
     */
    public String[] getIsimImpu() {
        try {
            return getSubscriberInfo().getIsimImpu();
        } catch (RemoteException ex) {
            return null;
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            return null;
        }
    }

    private IPhoneSubInfo getSubscriberInfo() {
        // get it each time because that process crashes a lot
        return IPhoneSubInfo.Stub.asInterface(ServiceManager.getService("iphonesubinfo"));
    }


    /** Device call state: No activity. */
    public static final int CALL_STATE_IDLE = 0;
    /** Device call state: Ringing. A new call arrived and is
     *  ringing or waiting. In the latter case, another call is
     *  already active. */
    public static final int CALL_STATE_RINGING = 1;
    /** Device call state: Off-hook. At least one call exists
      * that is dialing, active, or on hold, and no calls are ringing
      * or waiting. */
    public static final int CALL_STATE_OFFHOOK = 2;

    /**
     * Returns a constant indicating the call state (cellular) on the device.
     */
    public int getCallState() {
        try {
            return getITelephony().getCallState();
        } catch (RemoteException ex) {
            // the phone process is restarting.
            return CALL_STATE_IDLE;
        } catch (NullPointerException ex) {
          // the phone process is restarting.
          return CALL_STATE_IDLE;
      }
    }

    /** Data connection activity: No traffic. */
    public static final int DATA_ACTIVITY_NONE = 0x00000000;
    /** Data connection activity: Currently receiving IP PPP traffic. */
    public static final int DATA_ACTIVITY_IN = 0x00000001;
    /** Data connection activity: Currently sending IP PPP traffic. */
    public static final int DATA_ACTIVITY_OUT = 0x00000002;
    /** Data connection activity: Currently both sending and receiving
     *  IP PPP traffic. */
    public static final int DATA_ACTIVITY_INOUT = DATA_ACTIVITY_IN | DATA_ACTIVITY_OUT;
    /**
     * Data connection is active, but physical link is down
     */
    public static final int DATA_ACTIVITY_DORMANT = 0x00000004;

    /**
     * Returns a constant indicating the type of activity on a data connection
     * (cellular).
     *
     * @see #DATA_ACTIVITY_NONE
     * @see #DATA_ACTIVITY_IN
     * @see #DATA_ACTIVITY_OUT
     * @see #DATA_ACTIVITY_INOUT
     * @see #DATA_ACTIVITY_DORMANT
     */
    public int getDataActivity() {
        try {
            return getITelephony().getDataActivity();
        } catch (RemoteException ex) {
            // the phone process is restarting.
            return DATA_ACTIVITY_NONE;
        } catch (NullPointerException ex) {
          // the phone process is restarting.
          return DATA_ACTIVITY_NONE;
      }
    }

    /** Data connection state: Unknown.  Used before we know the state.
     * @hide
     */
    public static final int DATA_UNKNOWN        = -1;
    /** Data connection state: Disconnected. IP traffic not available. */
    public static final int DATA_DISCONNECTED   = 0;
    /** Data connection state: Currently setting up a data connection. */
    public static final int DATA_CONNECTING     = 1;
    /** Data connection state: Connected. IP traffic should be available. */
    public static final int DATA_CONNECTED      = 2;
    /** Data connection state: Suspended. The connection is up, but IP
     * traffic is temporarily unavailable. For example, in a 2G network,
     * data activity may be suspended when a voice call arrives. */
    public static final int DATA_SUSPENDED      = 3;

    /**
     * Returns a constant indicating the current data connection state
     * (cellular).
     *
     * @see #DATA_DISCONNECTED
     * @see #DATA_CONNECTING
     * @see #DATA_CONNECTED
     * @see #DATA_SUSPENDED
     */
    public int getDataState() {
        try {
            return getITelephony().getDataState();
        } catch (RemoteException ex) {
            // the phone process is restarting.
            return DATA_DISCONNECTED;
        } catch (NullPointerException ex) {
            return DATA_DISCONNECTED;
        }
    }

    private ITelephony getITelephony() {
        return ITelephony.Stub.asInterface(ServiceManager.getService(Context.TELEPHONY_SERVICE));
    }

    //
    //
    // PhoneStateListener
    //
    //

    /**
     * Registers a listener object to receive notification of changes
     * in specified telephony states.
     * <p>
     * To register a listener, pass a {@link PhoneStateListener}
     * and specify at least one telephony state of interest in
     * the events argument.
     *
     * At registration, and when a specified telephony state
     * changes, the telephony manager invokes the appropriate
     * callback method on the listener object and passes the
     * current (updated) values.
     * <p>
     * To unregister a listener, pass the listener object and set the
     * events argument to
     * {@link PhoneStateListener#LISTEN_NONE LISTEN_NONE} (0).
     *
     * @param listener The {@link PhoneStateListener} object to register
     *                 (or unregister)
     * @param events The telephony state(s) of interest to the listener,
     *               as a bitwise-OR combination of {@link PhoneStateListener}
     *               LISTEN_ flags.
     */
    public void listen(PhoneStateListener listener, int events) {
        String pkgForDebug = mContext != null ? mContext.getPackageName() : "<unknown>";
        try {
            Boolean notifyNow = true;
            sRegistry.listen(pkgForDebug, listener.callback, events, notifyNow);
        } catch (RemoteException ex) {
            // system process dead
        } catch (NullPointerException ex) {
            // system process dead
        }
    }

    /**
     * Returns the CDMA ERI icon index to display
     *
     * @hide
     */
    public int getCdmaEriIconIndex() {
        try {
            return getITelephony().getCdmaEriIconIndex();
        } catch (RemoteException ex) {
            // the phone process is restarting.
            return -1;
        } catch (NullPointerException ex) {
            return -1;
        }
    }

    /**
     * Returns the CDMA ERI icon mode,
     * 0 - ON
     * 1 - FLASHING
     *
     * @hide
     */
    public int getCdmaEriIconMode() {
        try {
            return getITelephony().getCdmaEriIconMode();
        } catch (RemoteException ex) {
            // the phone process is restarting.
            return -1;
        } catch (NullPointerException ex) {
            return -1;
        }
    }

    /**
     * Returns the CDMA ERI text,
     *
     * @hide
     */
    public String getCdmaEriText() {
        try {
            return getITelephony().getCdmaEriText();
        } catch (RemoteException ex) {
            // the phone process is restarting.
            return null;
        } catch (NullPointerException ex) {
            return null;
        }
    }

    /**
     * @return true if the current device is "voice capable".
     * <p>
     * "Voice capable" means that this device supports circuit-switched
     * (i.e. voice) phone calls over the telephony network, and is allowed
     * to display the in-call UI while a cellular voice call is active.
     * This will be false on "data only" devices which can't make voice
     * calls and don't support any in-call UI.
     * <p>
     * Note: the meaning of this flag is subtly different from the
     * PackageManager.FEATURE_TELEPHONY system feature, which is available
     * on any device with a telephony radio, even if the device is
     * data-only.
     *
     * @hide pending API review
     */
    public boolean isVoiceCapable() {
        if (mContext == null) return true;
        return mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_voice_capable);
    }

    /**
     * @return true if the current device supports sms service.
     * <p>
     * If true, this means that the device supports both sending and
     * receiving sms via the telephony network.
     * <p>
     * Note: Voicemail waiting sms, cell broadcasting sms, and MMS are
     *       disabled when device doesn't support sms.
     *
     * @hide pending API review
     */
    public boolean isSmsCapable() {
        if (mContext == null) return true;
        return mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_sms_capable);
    }

    /**
     * Returns all observed cell information from all radios on the
     * device including the primary and neighboring cells. This does
     * not cause or change the rate of PhoneStateListner#onCellInfoChanged.
     *<p>
     * The list can include one or more of {@link android.telephony.CellInfoGsm CellInfoGsm},
     * {@link android.telephony.CellInfoCdma CellInfoCdma},
     * {@link android.telephony.CellInfoLte CellInfoLte} and
     * {@link android.telephony.CellInfoWcdma CellInfoCdma} in any combination.
     * Specifically on devices with multiple radios it is typical to see instances of
     * one or more of any these in the list. In addition 0, 1 or more CellInfo
     * objects may return isRegistered() true.
     *<p>
     * This is preferred over using getCellLocation although for older
     * devices this may return null in which case getCellLocation should
     * be called.
     *<p>
     * @return List of CellInfo or null if info unavailable.
     *
     * <p>Requires Permission: {@link android.Manifest.permission#ACCESS_COARSE_LOCATION}
     */
    public List<CellInfo> getAllCellInfo() {
        try {
            return getITelephony().getAllCellInfo();
        } catch (RemoteException ex) {
            return null;
        } catch (NullPointerException ex) {
            return null;
        }
    }

    /**
     * Sets the minimum time in milli-seconds between {@link PhoneStateListener#onCellInfoChanged
     * PhoneStateListener.onCellInfoChanged} will be invoked.
     *<p>
     * The default, 0, means invoke onCellInfoChanged when any of the reported
     * information changes. Setting the value to INT_MAX(0x7fffffff) means never issue
     * A onCellInfoChanged.
     *<p>
     * @param rateInMillis the rate
     *
     * @hide
     */
    public void setCellInfoListRate(int rateInMillis) {
        try {
            getITelephony().setCellInfoListRate(rateInMillis);
        } catch (RemoteException ex) {
        } catch (NullPointerException ex) {
        }
    }

    /**
     * Inform the phone about a new incoming third party call. The phone will bind to the service
     * identified by component to handle the call.
     * @param component the component that should handle the intent.
     * @param callId the unique id of the call. This id is passed to the service via {@link
     *               ThirdPartyCallService#incomingCallAttach incomingCallAttach}.
     * @param callerDisplayName the name shown to the user. Normally this will be the caller's phone
     *                          number.
     */
    public void newIncomingThirdPartyCall(ComponentName component, String callId,
            String callerDisplayName) {
        try {
            getITelephony().newIncomingThirdPartyCall(component, callId, callerDisplayName);
        } catch (RemoteException ex) {
        } catch (NullPointerException ex) {
        }
    }

    /**
     * Returns the MMS user agent.
     */
    public String getMmsUserAgent() {
        if (mContext == null) return null;
        return mContext.getResources().getString(
                com.android.internal.R.string.config_mms_user_agent);
    }

    /**
     * Returns the MMS user agent profile URL.
     */
    public String getMmsUAProfUrl() {
        if (mContext == null) return null;
        return mContext.getResources().getString(
                com.android.internal.R.string.config_mms_user_agent_profile_url);
    }

    /**
     * Opens a logical channel to the ICC card.
     *
     * Input parameters equivalent to TS 27.007 AT+CCHO command.
     *
     * <p>Requires Permission:
     *   {@link android.Manifest.permission#SIM_COMMUNICATION SIM_COMMUNICATION}
     *
     * @param AID Application id. See ETSI 102.221 and 101.220.
     * @return The logical channel id which is negative on error.
     */
    public int iccOpenLogicalChannel(String AID) {
        try {
            return getITelephony().iccOpenLogicalChannel(AID);
        } catch (RemoteException ex) {
        } catch (NullPointerException ex) {
        }
        return -1;
    }

    /**
     * Closes a previously opened logical channel to the ICC card.
     *
     * Input parameters equivalent to TS 27.007 AT+CCHC command.
     *
     * <p>Requires Permission:
     *   {@link android.Manifest.permission#SIM_COMMUNICATION SIM_COMMUNICATION}
     *
     * @param channel is the channel id to be closed as retruned by a successful
     *            iccOpenLogicalChannel.
     * @return true if the channel was closed successfully.
     */
    public boolean iccCloseLogicalChannel(int channel) {
        try {
            return getITelephony().iccCloseLogicalChannel(channel);
        } catch (RemoteException ex) {
        } catch (NullPointerException ex) {
        }
        return false;
    }

    /**
     * Transmit an APDU to the ICC card over a logical channel.
     *
     * Input parameters equivalent to TS 27.007 AT+CGLA command.
     *
     * <p>Requires Permission:
     *   {@link android.Manifest.permission#SIM_COMMUNICATION SIM_COMMUNICATION}
     *
     * @param channel is the channel id to be closed as returned by a successful
     *            iccOpenLogicalChannel.
     * @param cla Class of the APDU command.
     * @param instruction Instruction of the APDU command.
     * @param p1 P1 value of the APDU command.
     * @param p2 P2 value of the APDU command.
     * @param p3 P3 value of the APDU command. If p3 is negative a 4 byte APDU
     *            is sent to the SIM.
     * @param data Data to be sent with the APDU.
     * @return The APDU response from the ICC card with the status appended at
     *            the end. If an error occurs, an empty string is returned.
     */
    public String iccTransmitApduLogicalChannel(int channel, int cla,
            int instruction, int p1, int p2, int p3, String data) {
        try {
            return getITelephony().iccTransmitApduLogicalChannel(channel, cla,
                    instruction, p1, p2, p3, data);
        } catch (RemoteException ex) {
        } catch (NullPointerException ex) {
        }
        return "";
    }

    /**
     * Send ENVELOPE to the SIM and return the response.
     *
     * <p>Requires Permission:
     *   {@link android.Manifest.permission#SIM_COMMUNICATION SIM_COMMUNICATION}
     *
     * @param content String containing SAT/USAT response in hexadecimal
     *                format starting with command tag. See TS 102 223 for
     *                details.
     * @return The APDU response from the ICC card, with the last 4 bytes
     *         being the status word. If the command fails, returns an empty
     *         string.
     */
    public String sendEnvelopeWithStatus(String content) {
        try {
            return getITelephony().sendEnvelopeWithStatus(content);
        } catch (RemoteException ex) {
        } catch (NullPointerException ex) {
        }
        return "";
    }

    /**
     * Read one of the NV items defined in {@link com.android.internal.telephony.RadioNVItems}.
     * Used for device configuration by some CDMA operators.
     *
     * @param itemID the ID of the item to read.
     * @return the NV item as a String, or null on any failure.
     * @hide
     */
    public String nvReadItem(int itemID) {
        try {
            return getITelephony().nvReadItem(itemID);
        } catch (RemoteException ex) {
            Rlog.e(TAG, "nvReadItem RemoteException", ex);
        } catch (NullPointerException ex) {
            Rlog.e(TAG, "nvReadItem NPE", ex);
        }
        return "";
    }


    /**
     * Write one of the NV items defined in {@link com.android.internal.telephony.RadioNVItems}.
     * Used for device configuration by some CDMA operators.
     *
     * @param itemID the ID of the item to read.
     * @param itemValue the value to write, as a String.
     * @return true on success; false on any failure.
     * @hide
     */
    public boolean nvWriteItem(int itemID, String itemValue) {
        try {
            return getITelephony().nvWriteItem(itemID, itemValue);
        } catch (RemoteException ex) {
            Rlog.e(TAG, "nvWriteItem RemoteException", ex);
        } catch (NullPointerException ex) {
            Rlog.e(TAG, "nvWriteItem NPE", ex);
        }
        return false;
    }

    /**
     * Update the CDMA Preferred Roaming List (PRL) in the radio NV storage.
     * Used for device configuration by some CDMA operators.
     *
     * @param preferredRoamingList byte array containing the new PRL.
     * @return true on success; false on any failure.
     * @hide
     */
    public boolean nvWriteCdmaPrl(byte[] preferredRoamingList) {
        try {
            return getITelephony().nvWriteCdmaPrl(preferredRoamingList);
        } catch (RemoteException ex) {
            Rlog.e(TAG, "nvWriteCdmaPrl RemoteException", ex);
        } catch (NullPointerException ex) {
            Rlog.e(TAG, "nvWriteCdmaPrl NPE", ex);
        }
        return false;
    }

    /**
     * Perform the specified type of NV config reset. The radio will be taken offline
     * and the device must be rebooted after the operation. Used for device
     * configuration by some CDMA operators.
     *
     * @param resetType reset type: 1: reload NV reset, 2: erase NV reset, 3: factory NV reset
     * @return true on success; false on any failure.
     * @hide
     */
    public boolean nvResetConfig(int resetType) {
        try {
            return getITelephony().nvResetConfig(resetType);
        } catch (RemoteException ex) {
            Rlog.e(TAG, "nvResetConfig RemoteException", ex);
        } catch (NullPointerException ex) {
            Rlog.e(TAG, "nvResetConfig NPE", ex);
        }
        return false;
    }

    /*
     * Obtain the current state of Wi-Fi calling.
     *
     * @hide
     * @see android.telephony.TelephonyManager.WifiCallingChoices
     */
    public int getWhenToMakeWifiCalls() {
        try {
            return getITelephony().getWhenToMakeWifiCalls();
        } catch (RemoteException ex) {
            return WifiCallingChoices.NEVER_USE;
        }
    }

    /**
     * Set the current state of Wi-Fi calling.
     *
     * @hide
     * @see android.telephony.TelephonyManager.WifiCallingChoices
     */
    public void setWhenToMakeWifiCalls(int state) {
        try {
            getITelephony().setWhenToMakeWifiCalls(state);
        } catch (RemoteException ex) {
        }
    }

    /**
     * Get the preferred network type.
     * Used for device configuration by some CDMA operators.
     *
     * @return the preferred network type, defined in RILConstants.java.
     * @hide
     */
    public int getPreferredNetworkType() {
        try {
            return getITelephony().getPreferredNetworkType();
        } catch (RemoteException ex) {
            Rlog.e(TAG, "getPreferredNetworkType RemoteException", ex);
        } catch (NullPointerException ex) {
            Rlog.e(TAG, "getPreferredNetworkType NPE", ex);
        }
        return -1;
    }

    /**
     * Set the preferred network type.
     * Used for device configuration by some CDMA operators.
     *
     * @param networkType the preferred network type, defined in RILConstants.java.
     * @return true on success; false on any failure.
     * @hide
     */
    public boolean setPreferredNetworkType(int networkType) {
        try {
            return getITelephony().setPreferredNetworkType(networkType);
        } catch (RemoteException ex) {
            Rlog.e(TAG, "setPreferredNetworkType RemoteException", ex);
        } catch (NullPointerException ex) {
            Rlog.e(TAG, "setPreferredNetworkType NPE", ex);
        }
        return false;
    }

    /**
     * Expose the rest of ITelephony to @PrivateApi
     */

    /** @hide */
    @PrivateApi
    public void dial(String number) {
        try {
            getITelephony().dial(number);
        } catch (RemoteException e) {
            Log.e(TAG, "Error calling ITelephony#dial", e);
        }
    }

    /** @hide */
    @PrivateApi
    public void call(String callingPackage, String number) {
        try {
            getITelephony().call(callingPackage, number);
        } catch (RemoteException e) {
            Log.e(TAG, "Error calling ITelephony#call", e);
        }
    }

    /** @hide */
    @PrivateApi
    public boolean showCallScreen() {
        try {
            return getITelephony().showCallScreen();
        } catch (RemoteException e) {
            Log.e(TAG, "Error calling ITelephony#showCallScreen", e);
        }
        return false;
    }

    /** @hide */
    @PrivateApi
    public boolean showCallScreenWithDialpad(boolean showDialpad) {
        try {
            return getITelephony().showCallScreenWithDialpad(showDialpad);
        } catch (RemoteException e) {
            Log.e(TAG, "Error calling ITelephony#showCallScreenWithDialpad", e);
        }
        return false;
    }

    /** @hide */
    @PrivateApi
    public boolean endCall() {
        try {
            return getITelephony().endCall();
        } catch (RemoteException e) {
            Log.e(TAG, "Error calling ITelephony#endCall", e);
        }
        return false;
    }

    /** @hide */
    @PrivateApi
    public void answerRingingCall() {
        try {
            getITelephony().answerRingingCall();
        } catch (RemoteException e) {
            Log.e(TAG, "Error calling ITelephony#answerRingingCall", e);
        }
    }

    /** @hide */
    @PrivateApi
    public void toggleHold() {
        try {
            getITelephony().toggleHold();
        } catch (RemoteException e) {
            Log.e(TAG, "Error calling ITelephony#toggleHold", e);
        }
    }

    /** @hide */
    @PrivateApi
    public void merge() {
        try {
            getITelephony().merge();
        } catch (RemoteException e) {
            Log.e(TAG, "Error calling ITelephony#merge", e);
        }
    }

    /** @hide */
    @PrivateApi
    public void swap() {
        try {
            getITelephony().swap();
        } catch (RemoteException e) {
            Log.e(TAG, "Error calling ITelephony#swap", e);
        }
    }

    /** @hide */
    @PrivateApi
    public void mute(boolean mute) {
        try {
            getITelephony().mute(mute);
        } catch (RemoteException e) {
            Log.e(TAG, "Error calling ITelephony#mute", e);
        }
    }

    /** @hide */
    @PrivateApi
    public void silenceRinger() {
        try {
            getITelephony().silenceRinger();
        } catch (RemoteException e) {
            Log.e(TAG, "Error calling ITelephony#silenceRinger", e);
        }
    }

    /** @hide */
    @PrivateApi
    public boolean isOffhook() {
        try {
            return getITelephony().isOffhook();
        } catch (RemoteException e) {
            Log.e(TAG, "Error calling ITelephony#isOffhook", e);
        }
        return false;
    }

    /** @hide */
    @PrivateApi
    public boolean isRinging() {
        try {
            return getITelephony().isRinging();
        } catch (RemoteException e) {
            Log.e(TAG, "Error calling ITelephony#isRinging", e);
        }
        return false;
    }

    /** @hide */
    @PrivateApi
    public boolean isIdle() {
        try {
            return getITelephony().isIdle();
        } catch (RemoteException e) {
            Log.e(TAG, "Error calling ITelephony#isIdle", e);
        }
        return true;
    }

    /** @hide */
    @PrivateApi
    public boolean isRadioOn() {
        try {
            return getITelephony().isRadioOn();
        } catch (RemoteException e) {
            Log.e(TAG, "Error calling ITelephony#isRadioOn", e);
        }
        return false;
    }

    /** @hide */
    @PrivateApi
    public boolean isSimPinEnabled() {
        try {
            return getITelephony().isSimPinEnabled();
        } catch (RemoteException e) {
            Log.e(TAG, "Error calling ITelephony#isSimPinEnabled", e);
        }
        return false;
    }

    /** @hide */
    @PrivateApi
    public void cancelMissedCallsNotification() {
        try {
            getITelephony().cancelMissedCallsNotification();
        } catch (RemoteException e) {
            Log.e(TAG, "Error calling ITelephony#cancelMissedCallsNotification", e);
        }
    }

    /** @hide */
    @PrivateApi
    public boolean supplyPin(String pin) {
        try {
            return getITelephony().supplyPin(pin);
        } catch (RemoteException e) {
            Log.e(TAG, "Error calling ITelephony#supplyPin", e);
        }
        return false;
    }

    /** @hide */
    @PrivateApi
    public boolean supplyPuk(String puk, String pin) {
        try {
            return getITelephony().supplyPuk(puk, pin);
        } catch (RemoteException e) {
            Log.e(TAG, "Error calling ITelephony#supplyPuk", e);
        }
        return false;
    }

    /** @hide */
    @PrivateApi
    public int[] supplyPinReportResult(String pin) {
        try {
            return getITelephony().supplyPinReportResult(pin);
        } catch (RemoteException e) {
            Log.e(TAG, "Error calling ITelephony#supplyPinReportResult", e);
        }
        return new int[0];
    }

    /** @hide */
    @PrivateApi
    public int[] supplyPukReportResult(String puk, String pin) {
        try {
            return getITelephony().supplyPukReportResult(puk, pin);
        } catch (RemoteException e) {
            Log.e(TAG, "Error calling ITelephony#]", e);
        }
        return new int[0];
    }

    /** @hide */
    @PrivateApi
    public boolean handlePinMmi(String dialString) {
        try {
            return getITelephony().handlePinMmi(dialString);
        } catch (RemoteException e) {
            Log.e(TAG, "Error calling ITelephony#handlePinMmi", e);
        }
        return false;
    }

    /** @hide */
    @PrivateApi
    public void toggleRadioOnOff() {
        try {
            getITelephony().toggleRadioOnOff();
        } catch (RemoteException e) {
            Log.e(TAG, "Error calling ITelephony#toggleRadioOnOff", e);
        }
    }

    /** @hide */
    @PrivateApi
    public boolean setRadio(boolean turnOn) {
        try {
            return getITelephony().setRadio(turnOn);
        } catch (RemoteException e) {
            Log.e(TAG, "Error calling ITelephony#setRadio", e);
        }
        return false;
    }

    /** @hide */
    @PrivateApi
    public boolean setRadioPower(boolean turnOn) {
        try {
            return getITelephony().setRadioPower(turnOn);
        } catch (RemoteException e) {
            Log.e(TAG, "Error calling ITelephony#setRadioPower", e);
        }
        return false;
    }

    /** @hide */
    @PrivateApi
    public void updateServiceLocation() {
        try {
            getITelephony().updateServiceLocation();
        } catch (RemoteException e) {
            Log.e(TAG, "Error calling ITelephony#updateServiceLocation", e);
        }
    }

    /** @hide */
    @PrivateApi
    public int enableApnType(String type) {
        try {
            return getITelephony().enableApnType(type);
        } catch (RemoteException e) {
            Log.e(TAG, "Error calling ITelephony#enableApnType", e);
        }
        return PhoneConstants.APN_REQUEST_FAILED;
    }

    /** @hide */
    @PrivateApi
    public int disableApnType(String type) {
        try {
            return getITelephony().disableApnType(type);
        } catch (RemoteException e) {
            Log.e(TAG, "Error calling ITelephony#disableApnType", e);
        }
        return PhoneConstants.APN_REQUEST_FAILED;
    }

    /** @hide */
    @PrivateApi
    public boolean enableDataConnectivity() {
        try {
            return getITelephony().enableDataConnectivity();
        } catch (RemoteException e) {
            Log.e(TAG, "Error calling ITelephony#enableDataConnectivity", e);
        }
        return false;
    }

    /** @hide */
    @PrivateApi
    public boolean disableDataConnectivity() {
        try {
            return getITelephony().disableDataConnectivity();
        } catch (RemoteException e) {
            Log.e(TAG, "Error calling ITelephony#disableDataConnectivity", e);
        }
        return false;
    }

    /** @hide */
    @PrivateApi
    public boolean isDataConnectivityPossible() {
        try {
            return getITelephony().isDataConnectivityPossible();
        } catch (RemoteException e) {
            Log.e(TAG, "Error calling ITelephony#isDataConnectivityPossible", e);
        }
        return false;
    }

    /** @hide */
    @PrivateApi
    public boolean needsOtaServiceProvisioning() {
        try {
            return getITelephony().needsOtaServiceProvisioning();
        } catch (RemoteException e) {
            Log.e(TAG, "Error calling ITelephony#needsOtaServiceProvisioning", e);
        }
        return false;
    }

    /** @hide */
    @PrivateApi
    public void playDtmfTone(char digit, boolean timedShortCode) {
        try {
            getITelephony().playDtmfTone(digit, timedShortCode);
        } catch (RemoteException e) {
            Log.e(TAG, "Error calling ITelephony#playDtmfTone", e);
        }
    }

    /** @hide */
    @PrivateApi
    public void stopDtmfTone() {
        try {
            getITelephony().stopDtmfTone();
        } catch (RemoteException e) {
            Log.e(TAG, "Error calling ITelephony#stopDtmfTone", e);
        }
    }

    /** @hide */
    @PrivateApi
    public void addCallStateListener(CallStateListener listener) {
        try {
            if (listener == null) {
                throw new RuntimeException("Listener can't be null");
            }
            if (!mListeners.containsKey(listener)) {
                final Listener l = new Listener(listener);
                mListeners.put(listener, l);
                getITelephony().addListener(l);
            }
        } catch (RemoteException e) {
            Log.e(TAG, "Error calling ITelephony#addListener", e);
        }
    }

    /** @hide */
    @PrivateApi
    public void removeCallStateListener(CallStateListener listener) {
        try {
            final Listener l = mListeners.remove(listener);
            if (l != null) {
                // Make sure that no callbacks that are already in flight come.
                l.clearQueue();
                getITelephony().removeListener(l);
            }
        } catch (RemoteException e) {
            Log.e(TAG, "Error calling ITelephony#removeListener", e);
        }
    }
}
