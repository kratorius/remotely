package pw.bitset.remotely.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import pw.bitset.remotely.R;
import pw.bitset.remotely.api.Api;
import pw.bitset.remotely.api.DeltaCoordinates;
import pw.bitset.remotely.api.Keycode;
import pw.bitset.remotely.api.Pong;
import pw.bitset.remotely.api.RemotelyService;
import pw.bitset.remotely.data.Service;
import pw.bitset.remotely.trackpad.TrackpadListener;
import pw.bitset.remotely.trackpad.TrackpadView;
import retrofit.Callback;
import retrofit.Response;
import retrofit.Retrofit;

public class ControlActivity extends BaseActivity {
    private static final String TAG = "ControlActivity";

    private static final int PING_DELAY = 10;
    private static final String INTENT_KEY_SERVICE = "intent_key_service";

    private Service service;
    private RemotelyService api;
    private ScheduledExecutorService pingExecutor;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_control);

        postponeActivityTransitions();

        Intent intent = getIntent();
        service = intent.getParcelableExtra(INTENT_KEY_SERVICE);
        if (service == null) {
            Log.e(TAG, "Expected service.");
            finish();
            return;
        }

        api = Api.get(service);

        setupUI();
    }

    @Override
    protected void onResume() {
        super.onResume();

        pingExecutor = Executors.newSingleThreadScheduledExecutor();
        pingExecutor.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                performPing();
            }
        }, PING_DELAY, PING_DELAY, TimeUnit.SECONDS);
    }

    @Override
    protected void onPause() {
        super.onPause();
        pingExecutor.shutdown();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
                performVolumeUp();
                return true;
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                performVolumeDown();
                return true;
            case KeyEvent.KEYCODE_VOLUME_MUTE:
                performVolumeMute();
                return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    private void setupUI() {
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setTitle(service.name);
        toolbar.inflateMenu(R.menu.control_menu);
        toolbar.getMenu().findItem(R.id.menu_show_keyboard).setOnMenuItemClickListener(new SoftKeyboardListener());

        findViewById(R.id.btn_volume_down).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                performVolumeDown();
            }
        });
        findViewById(R.id.btn_volume_up).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                performVolumeUp();
            }
        });
        findViewById(R.id.btn_volume_mute).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                performVolumeMute();
            }
        });
        findViewById(R.id.btn_volume_play).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                performPlay();
            }
        });
        findViewById(R.id.btn_volume_pause).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                performPause();
            }
        });

        TrackpadView trackpadView = (TrackpadView) findViewById(R.id.trackpad);
        trackpadView.addListener(new TrackpadListener() {
            @Override
            public void onMove(int deltaX, int deltaY) {
                api.mouseMove(new DeltaCoordinates(deltaX, deltaY)).enqueue(newFireAndForgetRequest());
            }

            @Override
            public void onClick() {
                api.mouseClickLeft().enqueue(newFireAndForgetRequest());
                nudge();
            }
        });
    }

    private void performVolumeDown() {
        api.mediaVolumeDown().enqueue(newFireAndForgetRequest());
        nudge();
    }

    private void performVolumeUp() {
        api.mediaVolumeUp().enqueue(newFireAndForgetRequest());
        nudge();
    }

    private void performVolumeMute() {
        api.mediaVolumeMute().enqueue(newFireAndForgetRequest());
        nudge();
    }

    private void performPlay() {
        api.mediaPlay().enqueue(newFireAndForgetRequest());
        nudge();
    }

    private void performPause() {
        api.mediaPause().enqueue(newFireAndForgetRequest());
        nudge();
    }

    @WorkerThread
    private void performPing() {
        Log.v(TAG, "Pinging server.");
        api.ping().enqueue(new Callback<Pong>() {
            @Override
            public void onResponse(Response<Pong> response, Retrofit retrofit) {
                stopFailMode();
            }

            @Override
            public void onFailure(Throwable t) {
                startFailMode(R.string.error_cant_reach_server);
            }
        });
    }

    private class SoftKeyboardListener implements View.OnKeyListener, MenuItem.OnMenuItemClickListener {
        @Override
        public boolean onMenuItemClick(MenuItem item) {
            showKeyboard();
            return true;
        }

        @Override
        public boolean onKey(View v, int keyCode, KeyEvent event) {
            if (event.getAction() != KeyEvent.ACTION_UP) {
                return false;
            }

            // The keycode we receive is an internal android representation of the keycode, *not*
            // the ASCII equivalent (which might not even exist).
            int finalKeyCode;
            switch (keyCode) {
                case KeyEvent.KEYCODE_DEL:
                    finalKeyCode = '\b';
                    break;
                case KeyEvent.KEYCODE_TAB:
                    finalKeyCode = '\t';
                    break;
                case KeyEvent.KEYCODE_ENTER:
                    finalKeyCode = '\n';
                    break;
                default:
                    finalKeyCode = event.getUnicodeChar(event.getMetaState());
                    break;
            }

            if (keyCode <= 0 || keyCode > 255) {
                return false;
            }

            api.keyboardPress(new Keycode(finalKeyCode)).enqueue(newFireAndForgetRequest());

            return true;
        }

        private void showKeyboard() {
            View rootView = findViewById(R.id.root);
            rootView.setFocusable(true);
            rootView.setFocusableInTouchMode(true);

            InputMethodManager im = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            rootView.requestFocus();
            im.showSoftInput(rootView, InputMethodManager.SHOW_IMPLICIT);

            rootView.setOnKeyListener(this);
        }
    }

    static void show(Activity parentActivity, Service service, @Nullable Bundle transitionOptions) {
        Intent intent = new Intent(parentActivity, ControlActivity.class);
        intent.putExtra(INTENT_KEY_SERVICE, service);
        parentActivity.startActivity(intent, transitionOptions);
    }
}
