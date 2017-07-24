/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.settings;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.service.persistentdata.PersistentDataBlockManager;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.android.internal.logging.MetricsProto.MetricsEvent;
import com.android.settings.teraapi.TeraService;
import com.android.settingslib.RestrictedLockUtils;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import static com.android.settingslib.RestrictedLockUtils.EnforcedAdmin;

import com.android.settings.teraapi.TeraService;

import com.hopebaytech.hcfsmgmt.terafonnapiservice.IGetJWTandIMEIListener;
import com.hopebaytech.hcfsmgmt.terafonnapiservice.BoostUnboostActivateStatus;
import com.hopebaytech.hcfsmgmt.terafonnapiservice.ViewPage;

/**
 * Confirm and execute a reset of the device to a clean "just out of the box"
 * state.  Multiple confirmations are required: first, a general "are you sure
 * you want to do this?" prompt, followed by a keyguard pattern trace if the user
 * has defined one, followed by a final strongly-worded "THIS WILL ERASE EVERYTHING
 * ON THE PHONE" prompt.  If at any time the phone is allowed to go to sleep, is
 * locked, et cetera, then the confirmation sequence is abandoned.
 *
 * This is the confirmation screen.
 */
public class MasterClearConfirm extends OptionsMenuFragment {

    private View mContentView;
    private boolean mEraseSdCard;

    //hopebay
    private boolean mEraseTera;
    private final String CACHE_BACKUP_DIR = "/cache/backup";
    private final String DOMAIN_NAME = "terafonnreg.hopebaytech.com";
    private final String DEVICE_API = "https://" + DOMAIN_NAME + "/api/user/v1/devices/";
    private final String TAG = "MasterClearConfirm";
    private final String eraseFlagPath = "/data/eraseFlag";

    private TeraService mTeraService;
    private String toViewPagerIndex = "ViewPagerIndex";
    private ProgressDialog mSyncAllDataProgressDialog;
    private ProgressDialog mProgressDialog;
    private SharedPreferences mSharedPreferences;

    private Thread mCheckThread;
    private Button btn;
    private IntentFilter filter = null;
    private boolean checkConnectionStatusFlag;

    private final int HTTP_STATUS_CODE_OK = 200;

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            mProgressDialog.hide();

            switch (msg.what) {
                case HTTP_STATUS_CODE_OK:
                    eraseData();
                    break;

                default:
                    eraseTeraErrorDialog(
                            getActivity().getString(R.string.erase_tera_error_dialog_title),
                            getActivity().getString(R.string.erase_tera_error_dialog_text));
            }
        }
    };

    private Handler mTransHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            try {
                if (mSyncAllDataProgressDialog == null){
                    //prevent when mSyncAllDataProgressDialog is dimissed,
                    //and let the following setMessage() got exception
                    return;
                }
                switch(msg.what) {
                    case TeraService.TRANS_FAILED:
                        mSyncAllDataProgressDialog.setMessage(
                                getActivity().getString(R.string.hcfs_conn_status_failed));
                        break;
                    case TeraService.TRANS_NOT_ALLOWED:
                        mSyncAllDataProgressDialog.setMessage(
                                getActivity().getString(R.string.hcfs_conn_status_not_allowed));
                        break;
                    case TeraService.TRANS_NORMAL:
                        mSyncAllDataProgressDialog.setMessage(
                                getActivity().getString(R.string.hcfs_conn_status_normal));
                        break;
                    case TeraService.TRANS_IN_PROGRESS:
                        mSyncAllDataProgressDialog.setMessage(
                                getActivity().getString(R.string.hcfs_conn_status_in_progress));
                        break;
                    case TeraService.TRANS_SLOW:
                        mSyncAllDataProgressDialog.setMessage(
                                getActivity().getString(R.string.hcfs_conn_status_slow));
                        break;
                    default:
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };

    // isUserLeaving and isDoingSyncAllData is used for communication between SubSettings.java and MasterClearConfirm.java
    //static boolean isUserLeaving = false; // package protected
    static boolean isDoingSyncAllData = false; // package protected

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent){
            String action = intent.getAction();
            if(action.equals(TeraService.ACTION_UPLOAD_COMPLETED)){ // Tera app send out this broadcast
                stopSyncAllDataAndRemoveProgressDialog(false);
                Log.d(TAG,"receive data sync intent");
                setMgmtServerFlag();
            }
        }
    };
    //end hopebay

    /**
     * The user has gone through the multiple confirmation, so now we go ahead
     * and invoke the Checkin Service to reset the device to its factory-default
     * state (rebooting in the process).
     */
    private Button.OnClickListener mFinalClickListener = new Button.OnClickListener() {

        public void onClick(View v) {
            if (Utils.isMonkeyRunning()) {
                return;
            }

            //android default factory reset
            if(!mTeraService.hcfsEnabled()) {
                eraseData();
                return;
            }

            if(!mEraseTera){
                int boostUnboostActivateStatus = getBoostUnboostActivateStatus();
                if(isBoostingOrUnboosting(boostUnboostActivateStatus)){
                    unableToSyncDataToCloudDialog(boostUnboostActivateStatus);
                    return;
                }

                boolean isWifiOnly = mTeraService.isWifiOnly();
                int networkType = getNetworkType();

                if(!isNetworkConnect(false)){
                    gotoWifiSettings();
                    return;
                }

                if (networkType == ConnectivityManager.TYPE_WIFI) {
                    syncDataToCloud();
                } else {
                    if (isWifiOnly) {
                        wifiOnlyDialog();
                    } else {//not wifi only
                        ifSyncByMobileDialog();
                    }
                    return;
                }
            } else { // mEraseTera == true
                if(!isNetworkConnect(false)){
                    gotoWifiSettings();
                    return;
                }
                setMgmtServerFlag();
            }
        }

        private ProgressDialog getProgressDialog() {
            final ProgressDialog progressDialog = new ProgressDialog(getActivity());
            progressDialog.setIndeterminate(true);
            progressDialog.setCancelable(false);
            progressDialog.setTitle(
                    getActivity().getString(R.string.master_clear_progress_title));
            progressDialog.setMessage(
                    getActivity().getString(R.string.master_clear_progress_text));
            return progressDialog;
        }
    };

    private void doMasterClear() {
        Intent intent = new Intent(Intent.ACTION_MASTER_CLEAR);
        intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        intent.putExtra(Intent.EXTRA_REASON, "MasterClearConfirm");
        intent.putExtra(Intent.EXTRA_WIPE_EXTERNAL_STORAGE, mEraseSdCard);
        getActivity().sendBroadcast(intent);
        // Intent handling is asynchronous -- assume it will happen soon.
    }

    /**
     * Configure the UI for the final confirmation interaction
     */
    private void establishFinalConfirmationState() {
        btn = (Button) mContentView.findViewById(R.id.execute_master_clear);
        btn.setOnClickListener(mFinalClickListener);
        if(ifNeedSyncDataToCloud()){
            btn.setText(R.string.master_clear_without_erase_tera_cloud_final_button_text);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        //hopebay
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        mTeraService = new TeraService(getActivity());
        //end hopebay

        final EnforcedAdmin admin = RestrictedLockUtils.checkIfRestrictionEnforced(
                getActivity(), UserManager.DISALLOW_FACTORY_RESET, UserHandle.myUserId());
        if (RestrictedLockUtils.hasBaseUserRestriction(getActivity(),
                UserManager.DISALLOW_FACTORY_RESET, UserHandle.myUserId())) {
            return inflater.inflate(R.layout.master_clear_disallowed_screen, null);
        } else if (admin != null) {
            View view = inflater.inflate(R.layout.admin_support_details_empty_view, null);
            ShowAdminSupportDetailsDialog.setAdminSupportDetails(getActivity(), view, admin, false);
            view.setVisibility(View.VISIBLE);
            return view;
        }
        mContentView = inflater.inflate(R.layout.master_clear_confirm, null);
        establishFinalConfirmationState();
        setAccessibilityTitle();
        return mContentView;
    }

    private void setAccessibilityTitle() {
        CharSequence currentTitle = getActivity().getTitle();
        TextView confirmationMessage =
                (TextView) mContentView.findViewById(R.id.master_clear_confirm);
        if (confirmationMessage != null) {
            if(ifNeedSyncDataToCloud()) {
                confirmationMessage.setText(R.string.master_clear_without_erase_tera_cloud_final_desc);
            }

            String accessibileText = new StringBuilder(currentTitle).append(",").append(
                    confirmationMessage.getText()).toString();
            getActivity().setTitle(Utils.createAccessibleSequence(currentTitle, accessibileText));
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle args = getArguments();
        mEraseSdCard = args != null
                && args.getBoolean(MasterClear.ERASE_EXTERNAL_EXTRA);

        // hopebay
        mEraseTera = args != null
                && args.getBoolean(MasterClear.ERASE_TERA);
    }

    @Override
    protected int getMetricsCategory() {
        return MetricsEvent.MASTER_CLEAR_CONFIRM;
    }

    ///////////////////
    ///// hopebay /////
    ///////////////////

    @Override
    public void onStart(){
        //Log.i(TAG, "onSart()");
        super.onStart();
        if(filter == null){ // prevent re-register which may cause memory leak
            filter = new IntentFilter();
            filter.addAction(TeraService.ACTION_UPLOAD_COMPLETED);
            getActivity().registerReceiver(mReceiver, filter);
        }

        if(ifNeedSyncDataToCloud()){
            btn.setText(R.string.master_clear_without_erase_tera_cloud_final_button_text);
        }
    }

/*
    @Override
    public void onStop(){
        super.onStop();
        //Log.e(TAG,"onStop()");
        if(MasterClearConfirm.isDoingSyncAllData){
            if(MasterClearConfirm.isUserLeaving){
                stopSyncAllDataAndRemoveProgressDialog(true);

                unregisterReceiver();
            } else {
                // power-key is hit or the device is timeout to sleep
                // do nothing;
            }
        } else {
            unregisterReceiver();
        }

        MasterClearConfirm.isUserLeaving = false;
    }
*/
    @Override
    public void onStop(){
        super.onStop();
        Log.e(TAG,"onStop()");
        if(!MasterClearConfirm.isDoingSyncAllData){
            unregisterReceiver();
        }
    }

    @Override
    public void onDestroy(){
        super.onDestroy();
        Log.e(TAG,"onDestroy()");
        if(MasterClearConfirm.isDoingSyncAllData){
            stopSyncAllDataAndRemoveProgressDialog(true);
            unregisterReceiver();
        }
    }

    private void gotoWifiSettings() {
        startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
    }

    private void goTeraAppSettings() {
        // go to Tera App settings view page
        PackageManager pm = getActivity().getPackageManager();
        Intent LaunchIntent = pm.getLaunchIntentForPackage("com.hopebaytech.hcfsmgmt");
        LaunchIntent.putExtra(ViewPage.KEY, ViewPage.SETTINGS);
        startActivity(LaunchIntent);
    }

    private boolean isBoostingOrUnboosting(int boostUnboostActivateStatus){
        return boostUnboostActivateStatus != BoostUnboostActivateStatus.NOT_BOOST_UNBOOST;
    }

    private int getBoostUnboostActivateStatus(){
         return mTeraService.getBoostUnboostActivateStatus();
    }

    private boolean ifNeedSyncDataToCloud(){
        return mTeraService.hcfsEnabled() && (!mEraseTera);
        //return true && (!mTeraCloud.isChecked());//debug onlu purpose
    }

    private boolean isNetworkConnect(boolean checkWifiOnlyMode) {
        ConnectivityManager cm = (ConnectivityManager) getActivity().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        boolean isWifiOnly = mTeraService.isWifiOnly();

        //if ( isDebug() && netInfo != null && netInfo.isConnected()) {
        if ( netInfo != null && netInfo.isConnected() ) {
            Log.d(TAG, "network type: " + netInfo.getType() );
            Log.d(TAG, "network is metered: " + cm.isActiveNetworkMetered() );
        }

        if( checkWifiOnlyMode && isWifiOnly ){
            if (netInfo != null && netInfo.isConnected() && netInfo.getType() == ConnectivityManager.TYPE_WIFI) {
                return true;
            }
        }
        else { // not wifi only
            if (netInfo != null && netInfo.isConnected()) {
                return true;
            }
        }
        return false;
    }//end isOnline()

    private int getNetworkType() {
        ConnectivityManager cm = (ConnectivityManager) getActivity().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo ni = cm.getActiveNetworkInfo();
        if (ni == null)
            return -1;
        else {
            return ni.getType();
        }
    }

    private Runnable check = new Runnable() {
        @Override
        public void run() {
            int status = TeraService.TRANS_NORMAL;
            while(checkConnectionStatusFlag) {
                try {
                    Thread.sleep(1000);
                    status = mTeraService.getConnStatus();
                    mTransHandler.sendEmptyMessage(status);
                } catch (Exception e) {
                    e.printStackTrace();
                    break;
                }
            }
        }
    };

    private void startCheckConnection() {
        checkConnectionStatusFlag = true;
        mCheckThread = new Thread(check);
        mCheckThread.start();
    }

    private void setMgmtServerFlag(){
        if (mEraseTera) {
            mProgressDialog = getProgressDialog(
                getActivity().getString(R.string.erase_tera_clear_progress_title),
                getActivity().getString(R.string.erase_tera_clear_progress_text));
        } else {
            mProgressDialog = getProgressDialog(
                getActivity().getString(R.string.erase_phone_clear_progress_title),
                getActivity().getString(R.string.erase_phone_clear_progress_text));
        }
        mProgressDialog.show();

        mTeraService.setJWTandIMEIListener(new IGetJWTandIMEIListener.Stub(){
            @Override
            public void onDataGet(final String imei, final String jwt) throws RemoteException {
                try {
                    Log.i(TAG, "imei: " + imei + " JWT: " + jwt);

                    new Thread(new Runnable(){
                        @Override
                        public void run() {
                            try {
                                if (imei == "" || jwt == "") {
                                    mHandler.sendEmptyMessage(500);
                                } else {
                                    int responseCode = 500;
                                    String url = DEVICE_API + imei;
                                    url += (mEraseTera ? "/close/" : "/tx_ready/");
                                    Log.i(TAG, "url = " + url);
                                    responseCode = closeDeviceService(url, jwt);
                                    //Log.i(TAG, "responseCode: " + String.valueOf(responseCode));
                                    mHandler.sendEmptyMessage(responseCode);
                                }
                                //Thread.sleep(100);
                            } catch(Exception e){
                                e.printStackTrace();
                            }
                        }
                    }).start();

                } catch (Exception e) {
                    Log.e(TAG, e.toString());
                }
            }//end onDataGet()
        });
        mTeraService.getJWTandIMEI();
    }//end setMgmtServerFlag()

    private int closeDeviceService(String urlString, String jwt) {
        HttpURLConnection connection = null;
        int responseCode = 500;

        try{
            URL url = new URL(urlString);
            connection = (HttpURLConnection)url.openConnection();
            connection.setConnectTimeout(10 * 1000);
            connection.setReadTimeout(35 * 1000);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);

            connection.setRequestProperty("Authorization", "JWT " + jwt);

            OutputStream out = connection.getOutputStream();
            out.flush();
            out.close();
            responseCode = connection.getResponseCode();

        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, e.toString());
            responseCode = 500;

        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }

        return responseCode;
    }//closeDeviceService()

    private void eraseData() {
        try {
            File flagPath = new File(eraseFlagPath);
            flagPath.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }

       //remove /cache/backup/ to prevent factory reset failed due to /cache space is not enough
       File cacheBackupDir = new File(CACHE_BACKUP_DIR);
       if(cacheBackupDir.exists()) {
           deleteDir(cacheBackupDir);
       }

        final PersistentDataBlockManager pdbManager = (PersistentDataBlockManager)
                getActivity().getSystemService(Context.PERSISTENT_DATA_BLOCK_SERVICE);

        if (pdbManager != null && !pdbManager.getOemUnlockEnabled()) {
            // if OEM unlock is enabled, this will be wiped during FR process.
            new AsyncTask<Void, Void, Void>() {
                int mOldOrientation;

                @Override
                protected Void doInBackground(Void... params) {
                    pdbManager.wipe();
                    return null;
                }

                @Override
                protected void onPostExecute(Void aVoid) {
                    mProgressDialog.hide();
                    getActivity().setRequestedOrientation(mOldOrientation);
                    doMasterClear();
                }

                @Override
                protected void onPreExecute() {
                    mProgressDialog = getProgressDialog(
                        getActivity().getString(R.string.master_clear_progress_title),
                        getActivity().getString(R.string.master_clear_progress_text));
                    mProgressDialog.show();

                    // need to prevent orientation changes as we're about to go into
                    // a long IO request, so we won't be able to access inflate resources on flash
                    mOldOrientation = getActivity().getRequestedOrientation();
                    getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
                }
            }.execute();
        } else {
            doMasterClear();
        }
    }

    private void eraseTeraErrorDialog(String title, String msg) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(title);
        TextView mTextView = new TextView(getActivity());
        mTextView.setText("\n" + msg + "\n");
        //mTextView.setPadding(10, 40, 10, 10);
        mTextView.setGravity(Gravity.CENTER);
        mTextView.setTextSize(18);

        builder.setView(mTextView);
        builder.setPositiveButton(getActivity().getString(R.string.okay),
                new DialogInterface.OnClickListener() {
            public void onClick( DialogInterface dialoginterface, int i) {}
        });

        builder.show();
    }

    private ProgressDialog getProgressDialog(String title, String msg) {
        final ProgressDialog progressDialog = new ProgressDialog(getActivity());
        progressDialog.setIndeterminate(true);
        progressDialog.setCancelable(false);
        progressDialog.setTitle(title);
        progressDialog.setMessage(msg);
        return progressDialog;
    }

    private void setBooleanSharedPreference(String key, boolean value){
        SharedPreferences.Editor editor = mSharedPreferences.edit();
        editor.putBoolean(key, value);
        editor.apply();
    }

    private boolean getBooleanSharedPreference(String key, boolean defaultValue){
        return mSharedPreferences.getBoolean(key, defaultValue);
    }

    private void startToSyncAllData() {
        if(mSyncAllDataProgressDialog == null){
            mSyncAllDataProgressDialog = getSyncAllDataProgressDialog();
        }
        mSyncAllDataProgressDialog.show();
        startCheckConnection();
        MasterClearConfirm.isDoingSyncAllData = true;
    }//end startToSyncAllData()

    private void stopSyncAllDataAndRemoveProgressDialog(boolean stopUploadTeraData){
        if (mSyncAllDataProgressDialog != null) { //MasterClearConfirm.isDoingSyncAllData = true;
            // Thread should be stopped before the dialog is dismiss to avoid NullPointerException on mTransHandler
            checkConnectionStatusFlag = false;
            try{
                mCheckThread.join();
            } catch(InterruptedException e) {
                e.printStackTrace();
            }

            mCheckThread = null;
            mSyncAllDataProgressDialog.dismiss();
            mSyncAllDataProgressDialog = null;
            MasterClearConfirm.isDoingSyncAllData = false;
            if(stopUploadTeraData) {
                mTeraService.stopUploadTeraData();// remount booster partition back
            }
        }
    }//end stopSyncAllDataAndRemoveProgressDialog()

    private void syncDataToCloud(){
        int syncRes = mTeraService.startUploadTeraData();
        Log.d(TAG, "startUpload tera data, got sync result = " + syncRes);
        if(syncRes < 0){ // sync failed!
            mTeraService.stopUploadTeraData();
            syncDataErrorDialog();
         } else if(syncRes == 1){ // no dirty data to sync
            setMgmtServerFlag();
         } else{ // sync dirty data: syncRes == 0
            startToSyncAllData();
         }
    } // end syncDataToCloud()

    private void syncDataErrorDialog(){
        new AlertDialog.Builder(getActivity())
            .setTitle(R.string.transmission_error_title)
            .setMessage(R.string.transmission_error_message)
            .setPositiveButton(R.string.okay, null)
            .setNegativeButton(R.string.reset_anyway, new DialogInterface.OnClickListener(){
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    setMgmtServerFlag();
                }//end onClick()
            })
            .show();
    }//end syncDataErrorDialog()

    private ProgressDialog getSyncAllDataProgressDialog() {
        // ProgressDialog cancel key---> just set it to null listener
        final ProgressDialog progressDialog = new ProgressDialog(getActivity());
        progressDialog.setIndeterminate(true);
        progressDialog.setCancelable(false);
        progressDialog.setTitle(getActivity().getString(R.string.sync_tera_cloud_title));
        progressDialog.setMessage(getActivity().getString(R.string.hcfs_conn_status_normal));
        progressDialog.setButton(getActivity().getString(R.string.sync_tera_cloud_cancel), new DialogInterface.OnClickListener(){
            @Override
            public void onClick(DialogInterface dialog, int which) {
                stopSyncAllDataAndRemoveProgressDialog(true);
            }//end onClick()
        });
        return progressDialog;
    }

    private void unableToSyncDataToCloudDialog(int boostUnboostActivateStatus) {
        int message = 0;
        if(boostUnboostActivateStatus == BoostUnboostActivateStatus.BOOST_ACTIVATED ){
            message = R.string.unable_sync_data_to_cloud_when_boosting_dialog_message;
        } else { //tera app is not unboost_activated
            message = R.string.unable_sync_data_to_cloud_when_unboosting_dialog_message;
        }

        new AlertDialog.Builder(getActivity())
            .setTitle(R.string.unable_to_sync_data_to_cloud_dialog_title)
            .setMessage(getActivity().getString(message))
            .setNegativeButton(R.string.okay, null)
            .show();
    } //end unableSyncDataToCloudDialog()

    private void wifiOnlyDialog() {
        new AlertDialog.Builder(getActivity())
             .setTitle(R.string.sync_data_stopped_title)
             .setMessage(R.string.sync_data_stopped)
             .setPositiveButton(R.string.sync_settings, new DialogInterface.OnClickListener(){
                 @Override
                 public void onClick(DialogInterface dialog, int which) {
                     goTeraAppSettings();
                 }
             })
             .setNegativeButton(R.string.connect_to_wifi, new DialogInterface.OnClickListener(){
                 @Override
                 public void onClick(DialogInterface dialog, int which) {
                     gotoWifiSettings();
                 }
             })
             .show();
    }// end wifiOnlyDialog()

    private void ifSyncByMobileDialog() {
        new AlertDialog.Builder(getActivity())
            .setTitle(R.string.if_sync_by_mobile_title)
            .setMessage(R.string.if_sync_by_mobile)
            .setPositiveButton(R.string.go_settings, new DialogInterface.OnClickListener(){
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    gotoWifiSettings();
                }
            })
            .setNegativeButton(R.string.continue_sync, new DialogInterface.OnClickListener(){
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    syncDataToCloud();
                }
            })
            .show();
    }// end ifSyncByMobileDialog()

    private void unregisterReceiver(){
        getActivity().unregisterReceiver(mReceiver);
        filter = null; //reset intentFilter to let onStart() to register the broadcastReceiver
    }

    private boolean deleteDir(File dir) {
        if (dir.isDirectory()) {
            String[] children = dir.list();
            if(children == null) {
                // prevent that dir is null when SELinux avc permission denied happens for some dir!
                return false;
            }
            for (int i=0; i<children.length; i++) {
                boolean success = deleteDir(new File(dir, children[i]));
                if (!success) {
                    return false;
                }
            }
        }

        // The directory is now empty so delete it
        return dir.delete();
    }// end deleteDir()
}
