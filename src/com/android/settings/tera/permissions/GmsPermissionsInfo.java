package com.android.settings.tera.permissions;


import java.util.ArrayList;

/**
 * Created by Vince on 2016/8/22.
 */

public class GmsPermissionsInfo {
    ArrayList permissions;

    public String getPkgName() {
        String pkgName = "com.google.android.gms";
        return pkgName;
    }

    public ArrayList<String> getPermissions() {
        permissions = new ArrayList();
        permissions.add("android.permission.READ_PHONE_STATE");
        permissions.add("android.permission.BODY_SENSORS");
        permissions.add("android.permission.READ_SMS");
        permissions.add("android.permission.RECEIVE_MMS");
        permissions.add("android.permission.READ_EXTERNAL_STORAGE");
        permissions.add("android.permission.WRITE_EXTERNAL_STORAGE");
        permissions.add("android.permission.READ_CALENDAR");
        permissions.add("android.permission.CAMERA");
        permissions.add("android.permission.READ_CONTACTS");
        permissions.add("android.permission.WRITE_CONTACTS");
        permissions.add("android.permission.GET_ACCOUNTS");
        permissions.add("android.permission.ACCESS_FINE_LOCATION");
        permissions.add("android.permission.ACCESS_COARSE_LOCATION");
        permissions.add("android.gms android.permission.RECORD_AUDIO");
        permissions.add("android.permission.READ_PHONE_STATE");
        permissions.add("android.permission.CALL_PHONE");
        permissions.add("android.permission.READ_CALL_LOG");
        permissions.add("android.permission.PROCESS_OUTGOING_CALLS");
        permissions.add("android.permission.SEND_SMS");
        permissions.add("android.permission.RECEIVE_SMS");

        return permissions;
    }
}
