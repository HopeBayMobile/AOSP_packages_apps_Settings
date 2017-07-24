package com.android.settings.tera;

import android.util.Log;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.os.Bundle;

import com.android.settings.MasterClearConfirm;
import com.android.settings.R;

public class ShowFinalConfirm extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.show_final_confirm);
        setTitle(R.string.reset_network_confirm_title);
        showFinalConfirmation();
    }

    private void showFinalConfirmation() {
        Fragment fragment = new MasterClearConfirm();
        FragmentTransaction ft =  getFragmentManager().beginTransaction();

        ft.replace(R.id.fragment_container, fragment);

        ft.commit();
    }
}

