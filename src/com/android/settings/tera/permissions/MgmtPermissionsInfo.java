package com.android.settings.tera.permissions;


import java.util.ArrayList;

/**
 * Created by Vince on 2016/8/22.
 */

public class MgmtPermissionsInfo {
    ArrayList permissions;

    public String getPkgName() {
        String pkgName = "com.hopebaytech.hcfsmgmt";
        return pkgName;
    }

    public ArrayList<String> getPermissions() {
        permissions = new ArrayList();
        permissions.add("android.permission.READ_PHONE_STATE");
        permissions.add("android.permission.READ_EXTERNAL_STORAGE");
        permissions.add("android.permission.WRITE_EXTERNAL_STORAGE");

        return permissions;
    }
}
