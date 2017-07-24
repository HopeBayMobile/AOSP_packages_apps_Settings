package com.android.settings.tera.permissions;


import java.util.ArrayList;

/**
 * Created by Vince on 2016/8/22.
 */

public class SetupwizardPermissionsInfo {
    ArrayList permissions;

    public String getPkgName() {
        String pkgName = "com.google.android.setupwizard";
        return pkgName;
    }

    public ArrayList<String> getPermissions() {
        permissions = new ArrayList();
        permissions.add("android.permission.READ_PHONE_STATE");

        return permissions;
    }
}
