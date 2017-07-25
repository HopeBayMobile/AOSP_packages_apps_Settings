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

package com.android.settings.deviceinfo;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.util.Log;
import android.os.IBinder;
import android.os.RemoteException;
import android.text.format.Formatter.BytesResult;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import java.util.Locale;

import com.hopebaytech.hcfsmgmt.terafonnapiservice.AppStatus;
import com.hopebaytech.hcfsmgmt.terafonnapiservice.AppStatus;
import com.hopebaytech.hcfsmgmt.terafonnapiservice.IFetchAppDataListener;
import com.hopebaytech.hcfsmgmt.terafonnapiservice.ITeraFonnApiService;
import com.hopebaytech.hcfsmgmt.terafonnapiservice.ITrackAppStatusListener;
import com.android.settings.deviceinfo.StorageSettings.getAPIServiceCallback;

public class StorageTera {

    private Context mContext;
    private final String CLASSNAME = getClass().getSimpleName();
    private final String TAG = "StorageTera";
    private final String system_path = "/system";
    private final String cache_path = "/cache";
    private final String data_path = "/data";
    private final String tera_path = "/data/data";
    List<String> paths = Arrays.asList("/system", "/cache", "/data","/data/data");
    private static ITeraFonnApiService mTeraService = null;
    private boolean mIsBound = false;
    private getAPIServiceCallback cb = null;
    private static boolean isHCFSEnabled = false;
    private final ServiceConnection mConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "on Service connected");
            mTeraService = ITeraFonnApiService.Stub.asInterface(service);
            mIsBound = true;
            if (cb != null)
                cb.onAPIServiceConnected();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "on Service disconnected");
            mTeraService = null;
            bindAPIService();
        }

    };

    public StorageTera(Context context, getAPIServiceCallback cb) {
        mContext=context;
        this.cb = cb;
        bindAPIService();
    }

    public StorageTera(Context context) {
            mContext=context;
            bindAPIService();
    }

    public StorageTera() {
        //Create instance for get total and free space
    }

    public void bindAPIService() {
        if (mTeraService == null) {
            Intent intent = new Intent();
            final String packageName = "com.hopebaytech.hcfsmgmt";
            final String className = "com.hopebaytech.hcfsmgmt.terafonnapiservice.TeraFonnApiService";
            intent.setComponent(new ComponentName(packageName, className));
            ComponentName serviceComponentName = mContext.startService(intent);
            if (serviceComponentName != null) {
                mIsBound = mContext.bindService(intent, mConn, Context.BIND_AUTO_CREATE);
            } else {
                Log.e(TAG, "Failed to startService");
            }
        }
    }

    public long getTeraFreeSpace() {
        long freeSpace = 0;
        try {
            freeSpace = mTeraService.getTeraFreeSpace();
        } catch (RemoteException e) {
            Log.e(TAG, Log.getStackTraceString(e));
        }
        return freeSpace;
    }

    public long getTeraTotalSpace() {
        long totalSpace = 0;
        try {
            totalSpace = mTeraService.getTeraTotalSpace();
        } catch (RemoteException e) {
            Log.e(TAG, Log.getStackTraceString(e));
        }
        return totalSpace;
    }

    public String getHCFSStat() {
        String result="{\"result\":\"false\"}";
        try {
            result = mTeraService.getHCFSStat();
        } catch (RemoteException e) {
            Log.e(TAG, Log.getStackTraceString(e));
        }
        return result;
    }

    public boolean hcfsEnabled() {
        boolean enabled = false;
        if (isHCFSEnabled)
            return true;
        if (!serviceReady()) {
            return false;
        }
        try {
            enabled = mTeraService.hcfsEnabled();
            isHCFSEnabled = enabled;
        } catch (RemoteException e) {
            Log.e(TAG, Log.getStackTraceString(e));
        }
        return enabled;
    }

    public boolean serviceReady() {
        if (mTeraService == null) {
            return false;
        } else {
            return true;
        }
    }

    public void unbind() {
        if (mIsBound) {
            Log.d(TAG, "HCFS API Service unbind");
            mContext.unbindService(mConn);
            mIsBound = false;
            mTeraService = null;
        }
    }

    public static BytesResult convertByteToProperUnit(long amount) {
        float result = amount;
        String[] unit = new String[] { "Byte", "KB", "MB", "GB", "TB", "PB", "EB", "ZB", "YB" };
        int unitIndex = 0;
        while (true) {
                float tmp = result / 1024f;
            if (result >= 1000) {
                if (tmp > 0) {
                    result = tmp;
                    unitIndex++;
                } else {
                    break;
                }
            } else if (result < 0) {
                result = 0;
                break;
            } else {
                break;
            }
        }

        float number = Float.parseFloat(String.format(Locale.getDefault(), "%.1f", result));
        String mUnit = unit[unitIndex];
        if (mUnit.equals(unit[0])) {
            if ((long) number > 1) {
                mUnit = "Bytes";
            }
        }
        if ((long) number == number) {
            return new BytesResult(String.format(Locale.getDefault(), "%d", (long) number), mUnit, 0);
        } else {
            return new BytesResult(String.format(Locale.getDefault(), "%.1f", number), mUnit, 0);
        }
    }
}
