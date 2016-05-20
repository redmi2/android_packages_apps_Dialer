/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.dialer.calllog;

import android.content.Context;
import android.os.SystemProperties;
import android.provider.CallLog.Calls;
import android.text.format.DateUtils;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.android.contacts.common.CallUtil;
import com.android.dialer.PhoneCallDetails;
import com.android.dialer.R;
import com.android.dialer.util.DialerUtils;
import com.google.common.collect.Lists;

import java.util.ArrayList;
import android.util.Log;
import org.codeaurora.presenceserv.IPresenceService;
/**
 * Adapter for a ListView containing history items from the details of a call.
 */
public class CallDetailHistoryAdapter extends BaseAdapter {
    /** The top element is a blank header, which is hidden under the rest of the UI. */
    private static final int VIEW_TYPE_HEADER = 0;
    /** Each history item shows the detail of a call. */
    private static final int VIEW_TYPE_HISTORY_ITEM = 1;

    /* Temporarily remove below values from "framework/base" due to the code of framework/base
           can't merge to atel.lnx.1.0-dev.1.0. */
    private static final int INCOMING_IMS_TYPE = 5;
    private static final int OUTGOING_IMS_TYPE = 6;
    private static final int MISSED_IMS_TYPE = 7;

    private final Context mContext;
    private final LayoutInflater mLayoutInflater;
    private final CallTypeHelper mCallTypeHelper;
    private final PhoneCallDetails[] mPhoneCallDetails;

    /**
     * List of items to be concatenated together for duration strings.
     */
    private ArrayList<CharSequence> mDurationItems = Lists.newArrayList();

    public CallDetailHistoryAdapter(Context context, LayoutInflater layoutInflater,
            CallTypeHelper callTypeHelper, PhoneCallDetails[] phoneCallDetails) {
        mContext = context;
        mLayoutInflater = layoutInflater;
        mCallTypeHelper = callTypeHelper;
        mPhoneCallDetails = phoneCallDetails;
    }

    @Override
    public boolean isEnabled(int position) {
        // None of history will be clickable.
        return false;
    }

    @Override
    public int getCount() {
        return mPhoneCallDetails.length + 1;
    }

    @Override
    public Object getItem(int position) {
        if (position == 0) {
            return null;
        }
        return mPhoneCallDetails[position - 1];
    }

    @Override
    public long getItemId(int position) {
        if (position == 0) {
            return -1;
        }
        return position - 1;
    }

    @Override
    public int getViewTypeCount() {
        return 2;
    }

    @Override
    public int getItemViewType(int position) {
        if (position == 0) {
            return VIEW_TYPE_HEADER;
        }
        return VIEW_TYPE_HISTORY_ITEM;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (position == 0) {
            final View header = convertView == null
                    ? mLayoutInflater.inflate(R.layout.call_detail_history_header, parent, false)
                    : convertView;
            return header;
        }

        // Make sure we have a valid convertView to start with
        final View result  = convertView == null
                ? mLayoutInflater.inflate(R.layout.call_detail_history_item, parent, false)
                : convertView;

        PhoneCallDetails details = mPhoneCallDetails[position - 1];
        CallTypeIconsView callTypeIconView =
                (CallTypeIconsView) result.findViewById(R.id.call_type_icon);
        TextView callTypeTextView = (TextView) result.findViewById(R.id.call_type_text);
        TextView dateView = (TextView) result.findViewById(R.id.date);
        TextView durationView = (TextView) result.findViewById(R.id.duration);

        int callType = details.callTypes[0];
        boolean isVideoCall;
        boolean enablePresence = SystemProperties.getBoolean(
                        "persist.presence.enable", false);
        if (enablePresence) {
            boolean showVideoCall = DialerUtils.startAvailabilityFetch(
                    details.number.toString());
            isVideoCall = (details.features & Calls.FEATURES_VIDEO) == Calls.FEATURES_VIDEO
                    && CallUtil.isVideoEnabled(mContext) && showVideoCall;
        } else {
            isVideoCall = (details.features & Calls.FEATURES_VIDEO) == Calls.FEATURES_VIDEO;
        }
        boolean isVoLTE = (callType == INCOMING_IMS_TYPE) ||
                          (callType == OUTGOING_IMS_TYPE) ||
                          (callType == MISSED_IMS_TYPE);
        Log.d("CallDetailHistoryAdapter", "isVideoCall = " + isVideoCall
                    + ", isVoLTE = " + isVoLTE);
        callTypeIconView.clear();
        callTypeIconView.add(callType);
        if (CallTypeIconsView.isCarrierOneEnabled()) {
             callTypeIconView.addImsOrVideoIcon(callType, isVideoCall);
        } else {
             callTypeIconView.setShowVideo(isVideoCall);
        }
        boolean imsCallLogEnabled = mContext.getResources()
                .getBoolean(R.bool.ims_call_type_enabled);
        if (!imsCallLogEnabled) {
            switch (callType) {
                case INCOMING_IMS_TYPE:
                    callType = Calls.INCOMING_TYPE;
                    break;
                case OUTGOING_IMS_TYPE:
                    callType = Calls.OUTGOING_TYPE;
                    break;
                case MISSED_IMS_TYPE:
                    callType = Calls.MISSED_TYPE;
                    break;
                default:
            }
        }
        callTypeTextView.setText(mCallTypeHelper.getCallTypeText(callType, isVideoCall));
        // Set the date.
        CharSequence dateValue = DateUtils.formatDateRange(mContext, details.date, details.date,
                DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_DATE |
                DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_SHOW_YEAR);
        dateView.setText(dateValue);
        // Set the duration
        boolean callDurationEnabled = mContext.getResources()
                .getBoolean(R.bool.call_duration_enabled);
        if (Calls.VOICEMAIL_TYPE == callType || CallTypeHelper.isMissedCallType(callType) ||
                !callDurationEnabled) {
            durationView.setVisibility(View.GONE);
        } else {
            durationView.setVisibility(View.VISIBLE);
            durationView.setText(formatDurationAndDataUsage(details.duration, details.dataUsage));
        }

        return result;
    }

    private CharSequence formatDuration(long elapsedSeconds) {
        long minutes = 0;
        long seconds = 0;

        if (elapsedSeconds >= 60) {
            minutes = elapsedSeconds / 60;
            elapsedSeconds -= minutes * 60;
            seconds = elapsedSeconds;
            return mContext.getString(R.string.callDetailsDurationFormat, minutes, seconds);
        } else {
            seconds = elapsedSeconds;
            return mContext.getString(R.string.callDetailsShortDurationFormat, seconds);
        }
    }

    /**
     * Formats a string containing the call duration and the data usage (if specified).
     *
     * @param elapsedSeconds Total elapsed seconds.
     * @param dataUsage Data usage in bytes, or null if not specified.
     * @return String containing call duration and data usage.
     */
    private CharSequence formatDurationAndDataUsage(long elapsedSeconds, Long dataUsage) {
        CharSequence duration = formatDuration(elapsedSeconds);

        if (dataUsage != null) {
            mDurationItems.clear();
            mDurationItems.add(duration);
            mDurationItems.add(Formatter.formatShortFileSize(mContext, dataUsage));

            return DialerUtils.join(mContext.getResources(), mDurationItems);
        } else {
            return duration;
        }
    }
}
