package com.github.slashmax.aamirror;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import eu.chainfire.libsuperuser.Shell;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        this.setContentView(R.layout.activity_main);
        Toolbar toolbar = this.findViewById(R.id.toolbar);
        this.setSupportActionBar(toolbar);

        Button unlockButton = this.findViewById(R.id.unlockButton);
        Button relockButton = this.findViewById(R.id.relockButton);

        unlockButton.setOnClickListener(view -> new LockerTask(unlockButton, relockButton, this.getResources()).execute((IConsoleFunction) Unlocker::unlock));
        relockButton.setOnClickListener(view -> new LockerTask(unlockButton, relockButton, this.getResources()).execute((IConsoleFunction) Unlocker::relock));

        TextView operationLog = findViewById(R.id.operationLog);

        if (!Shell.SU.available()) {
            operationLog.setText(R.string.no_root_detected);
            unlockButton.setEnabled(false);
            relockButton.setEnabled(false);
            this.findViewById(R.id.lockedTextView).setVisibility(View.GONE);
            this.findViewById(R.id.unlockedTextView).setVisibility(View.GONE);
            return;
        }

        updateLockedStatus();
    }

    private void updateLockedStatus() {
        boolean isLocked = Unlocker.isLocked();
        this.findViewById(R.id.unlockedTextView).setVisibility(isLocked ? View.GONE : View.VISIBLE);
        this.findViewById(R.id.lockedTextView).setVisibility(isLocked ? View.VISIBLE : View.GONE);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_settings) {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy");
        super.onDestroy();
    }

    @SuppressLint("StaticFieldLeak")
    private class LockerTask extends AsyncTask<IConsoleFunction, Void, List<String>> {
        private Button unlockButton;
        private Button relockButton;
        private Resources resources;

        LockerTask(Button unlockButton, Button relockButton, Resources resources) {
            super();

            this.unlockButton = unlockButton;
            this.relockButton = relockButton;
            this.resources = resources;
        }

        @Override
        protected void onPreExecute() {
            this.unlockButton.setEnabled(false);
            this.relockButton.setEnabled(false);
        }

        @Override
        protected List<String> doInBackground(IConsoleFunction... consoleFunctions) {
            List<String> result = new ArrayList<>();
            for (IConsoleFunction consoleFunction : consoleFunctions) {
                result.addAll(consoleFunction.call(this.resources));
            }
            return result;
        }

        @Override
        protected void onPostExecute(List<String> result) {
            try {
                TextView operationLog = findViewById(R.id.operationLog);

                StringBuilder sb = new StringBuilder();

                for (String item : result) {
                    sb.append(item);
                    sb.append("\n");
                }

                sb.append(this.resources.getString(R.string.finished));

                operationLog.setText(sb.toString());
            } finally {
                this.unlockButton.setEnabled(true);
                this.relockButton.setEnabled(true);

                updateLockedStatus();
            }
        }
    }

    private interface IConsoleFunction {
        List<String> call(Resources resources);
    }
}
