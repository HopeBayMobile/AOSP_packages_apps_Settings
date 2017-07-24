package com.android.settings.tera;

/**
 * @author Vince
 *         Created by Vince on 2016/8/16.
 */

import com.android.settings.R;
import com.android.settings.Settings.SimSettingsActivity;
import com.android.settings.Settings.PrivacySettingsActivity;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.Context;
import android.content.res.Resources;
import java.io.File;
import android.util.Log;
import android.content.pm.IPackageManager;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.os.RemoteException;
import java.io.IOException;
import java.util.ArrayList;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.android.settings.tera.permissions.*;
import com.android.settings.teraapi.TeraService;
import android.util.Log;
import android.support.v4.app.NotificationCompat;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;


public class TeraReceiver extends BroadcastReceiver {
    private String TAG = "TeraReceiver";
    private final String eraseFlagPath = "/data/eraseFlag";
    private final String grantPermsPath = "/data/grantPerms";

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        if (action.equals(Intent.ACTION_BOOT_COMPLETED)) {
            File flagPath = new File(eraseFlagPath);
            if(flagPath.exists()){
                Intent resetIntent = new Intent("android.intent.action.MASTER_CLEAR");
                resetIntent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
                resetIntent.putExtra("android.intent.extra.REASON", "MasterClearConfirm");
                context.sendBroadcast(resetIntent);

            } else {
                try {
                    flagPath = new File(grantPermsPath);
                    if(!flagPath.exists()) {
                        GrantPermissions grantPerms = new GrantPermissions();

                        MgmtPermissionsInfo mgmtPermissions = new MgmtPermissionsInfo();
                        grantPerms.run(mgmtPermissions.getPkgName(),
                                       mgmtPermissions.getPermissions());

                    //    SetupwizardPermissionsInfo setupwizardPermissions = new SetupwizardPermissionsInfo();
                    //    grantPerms.run(setupwizardPermissions.getPkgName(),
                    //                   setupwizardPermissions.getPermissions());
                    //
                    //    GmsPermissionsInfo gmsPermissions = new GmsPermissionsInfo();
                    //    grantPerms.run(gmsPermissions.getPkgName(),
                    //                   gmsPermissions.getPermissions());

                        flagPath.createNewFile();
                    }

                    System.exit(0);

                } catch (Exception e) {
                    Log.e(TAG, e.toString());
                }
            }
        } else if (action.equals(TeraService.ACTION_UPLOAD_COMPLETED)) {
            SharedPreferences sharedPreferences =
                PreferenceManager.getDefaultSharedPreferences(context);
            boolean isSync = sharedPreferences.getBoolean("isSync", false);
            if (isSync) {
                final Resources resources = context.getResources();
                NotificationCompat.Builder builder = new NotificationCompat.Builder(context);

                Bitmap largeIcon = BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_tera_app_default);
                builder = (NotificationCompat.Builder) builder
                        .setLargeIcon(largeIcon)
                        .setSmallIcon(R.drawable.ic_tera_logo_status_bar)
                        .setContentTitle(resources.getString(R.string.tera_sync_complete_title))
                        .setContentText(resources.getString(R.string.tera_sync_complete_desc));
                Intent resultIntent = new Intent(context, ShowFinalConfirm.class);
                PendingIntent resultPendingIntent = PendingIntent.getActivity(context, 0, resultIntent, 0);
                builder.setContentIntent(resultPendingIntent);
                NotificationManager notificationManager =
                        (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                notificationManager.notify(R.drawable.ic_tera_logo_status_bar, builder.build());
                sharedPreferences =
                    PreferenceManager.getDefaultSharedPreferences(context);
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putBoolean("isSync", false);
                editor.apply();
            }
        }
    }

    public final class GrantPermissions {
        String TAG = "GrantPermissions";
        public void run(String pkg, ArrayList<String> perms) throws IOException, RemoteException {
            IPackageManager mPm;
            mPm = IPackageManager.Stub.asInterface(ServiceManager.getService("package"));
            int userId = UserHandle.USER_OWNER;

            for (String perm : perms) {
                try {
                    mPm.grantRuntimePermission(pkg, perm, userId);

                } catch (Exception e) {
                    Log.e(TAG, e.toString());
                    break;
                }
            }
        }
    }
}
