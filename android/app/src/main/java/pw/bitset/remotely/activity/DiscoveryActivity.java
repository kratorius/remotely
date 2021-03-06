package pw.bitset.remotely.activity;

import android.app.ActivityOptions;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Bundle;
import android.support.annotation.LayoutRes;
import android.support.annotation.Nullable;
import android.support.annotation.StringDef;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import pw.bitset.remotely.R;
import pw.bitset.remotely.api.Api;
import pw.bitset.remotely.api.Pong;
import pw.bitset.remotely.data.Service;
import retrofit.Callback;
import retrofit.Response;
import retrofit.Retrofit;

public class DiscoveryActivity extends BaseActivity {
    private static final String TAG = "DiscoveryActivity";

    private static final String ZEROCONF_SERVICE_TYPE = "_http._tcp.";

    @StringDef({BROADCAST_INTENT_SERVICE_ADD, BROADCAST_INTENT_SERVICE_DELETE})
    @Retention(RetentionPolicy.SOURCE)
    public @interface BroadcastIntentServiceType {}
    private static final String BROADCAST_INTENT_SERVICE_ADD = "service_add";
    private static final String BROADCAST_INTENT_SERVICE_DELETE = "service_delete";

    private ServicesAdapter hostListAdapter;
    private NsdManager nsdManager;
    private NsdManager.DiscoveryListener discoveryListener;

    private BroadcastReceiver hostEventReceiver;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_discovery);

        hostEventReceiver = new HostEventReceiver();
        LocalBroadcastManager lm = LocalBroadcastManager.getInstance(this);
        lm.registerReceiver(hostEventReceiver, new IntentFilter(BROADCAST_INTENT_SERVICE_ADD));
        lm.registerReceiver(hostEventReceiver, new IntentFilter(BROADCAST_INTENT_SERVICE_DELETE));

        nsdManager = (NsdManager) getSystemService(Context.NSD_SERVICE);

        hostListAdapter = new ServicesAdapter(this, R.layout.vh_device_view);

        ListView hostList = (ListView) findViewById(R.id.lst_hosts);
        hostList.setAdapter(hostListAdapter);
        hostList.setEmptyView(findViewById(R.id.lst_empty));
        hostList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Service service = hostListAdapter.getItem(position);

                View statusBar = findViewById(android.R.id.statusBarBackground);
                View navigationBar = findViewById(android.R.id.navigationBarBackground);
                View toolbar = findViewById(R.id.toolbar);

                Pair[] pairs = new Pair[] {
                        Pair.create(statusBar, Window.STATUS_BAR_BACKGROUND_TRANSITION_NAME),
                        Pair.create(navigationBar, Window.NAVIGATION_BAR_BACKGROUND_TRANSITION_NAME),
                        Pair.create(toolbar, toolbar.getTransitionName())
                };

                //noinspection unchecked
                Bundle transitionBundle = ActivityOptions
                        .makeSceneTransitionAnimation(DiscoveryActivity.this, pairs)
                        .toBundle();

                ControlActivity.show(DiscoveryActivity.this, service, transitionBundle);
            }
        });

        discoveryListener = new RemotelyDiscoveryListener(this, nsdManager);
        nsdManager.discoverServices(ZEROCONF_SERVICE_TYPE,
                NsdManager.PROTOCOL_DNS_SD,
                discoveryListener);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        LocalBroadcastManager lm = LocalBroadcastManager.getInstance(this);
        lm.unregisterReceiver(hostEventReceiver);

        nsdManager.stopServiceDiscovery(discoveryListener);
        discoveryListener = null;
    }

    static void sendServiceEvent(Context context,
                                 @BroadcastIntentServiceType String intentAction,
                                 Service service) {
        Intent intent = new Intent(intentAction);
        intent.putExtra("service", service);

        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }

    private static class ServicesAdapter extends ArrayAdapter<Service> {
        private LayoutInflater inflater;
        @LayoutRes private int resource;

        public ServicesAdapter(Context context, @LayoutRes int resource) {
            super(context, resource);

            this.resource = resource;
            this.inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @Override
        public View getView(int position, @Nullable View convertView, ViewGroup parent) {
            return createViewFromResource(inflater, position, convertView, parent, resource);
        }

        private View createViewFromResource(LayoutInflater inflater,
                                            int position,
                                            @Nullable View convertView,
                                            ViewGroup parent,
                                            @LayoutRes int resource) {
            View view;
            Service service = getItem(position);

            if (convertView == null) {
                view = inflater.inflate(resource, parent, false);
            } else {
                view = convertView;
            }

            final TextView header = (TextView) view.findViewById(R.id.txt_header);
            final TextView sub = (TextView) view.findViewById(R.id.txt_sub);
            final ImageView ok = (ImageView) view.findViewById(R.id.img_ok);

            header.setText(service.name);
            sub.setText(String.format("%s:%d", service.host, service.port));

            Api.get(service).ping().enqueue(new Callback<Pong>() {
                @Override
                public void onResponse(Response<Pong> response, Retrofit retrofit) {
                    if (response.isSuccess() && response.body().pong) {
                        ok.setVisibility(View.VISIBLE);
                        Animation fadeInAnimation = AnimationUtils.loadAnimation(getContext(), R.anim.fade_in);
                        ok.startAnimation(fadeInAnimation);
                    }
                }

                @Override
                public void onFailure(Throwable t) {
                    // Do nothing, most likely this is just another http server.
                }
            });

            return view;
        }
    }

    private class HostEventReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Service service = intent.getParcelableExtra("service");
            if (service == null) {
                throw new IllegalArgumentException("This broadcast receiver can only deal with service objects.");
            }

            @BroadcastIntentServiceType String action = intent.getAction();
            switch (action) {
                case BROADCAST_INTENT_SERVICE_ADD:
                    hostListAdapter.add(service);
                    break;
                case BROADCAST_INTENT_SERVICE_DELETE:
                    hostListAdapter.remove(service);
                    break;
                default:
                    throw new IllegalArgumentException("Unexpected broadcast event.");
            }

            hostListAdapter.notifyDataSetChanged();
        }
    }

    private static class RemotelyDiscoveryListener implements NsdManager.DiscoveryListener {
        private final Context context;
        private final NsdManager nsdManager;

        public RemotelyDiscoveryListener(Context context, NsdManager nsdManager) {
            this.context = context;
            this.nsdManager = nsdManager;
        }

        @Override
        public void onServiceFound(NsdServiceInfo nsdServiceInfo) {
            if (nsdServiceInfo.getServiceType().equals(ZEROCONF_SERVICE_TYPE)) {
                resolveAndSendEventFor(nsdServiceInfo, BROADCAST_INTENT_SERVICE_ADD);
            } else {
                Log.d(TAG, "Unknown service type: " + nsdServiceInfo.getServiceType());
            }
        }

        @Override
        public void onServiceLost(NsdServiceInfo nsdServiceInfo) {
            if (nsdServiceInfo.getServiceType().equals(ZEROCONF_SERVICE_TYPE)) {
                resolveAndSendEventFor(nsdServiceInfo, BROADCAST_INTENT_SERVICE_DELETE);
            } else {
                Log.d(TAG, "Unknown service type: " + nsdServiceInfo.getServiceType());
            }
        }

        private void resolveAndSendEventFor(final NsdServiceInfo serviceInfo, final String serviceAction) {
            nsdManager.resolveService(serviceInfo, new NsdManager.ResolveListener() {
                @Override
                public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
                    // TODO update UI with error
                    Log.w(TAG, "Failed to resolve service: " + serviceInfo);
                }

                @Override
                public void onServiceResolved(NsdServiceInfo serviceInfo) {
                    Service service = fromNsdServiceInfo(serviceInfo);
                    sendServiceEvent(context, serviceAction, service);
                }
            });
        }

        @Override
        public void onDiscoveryStarted(String regType) {
            Log.d(TAG, "Service discovery started");
        }

        @Override
        public void onDiscoveryStopped(String serviceType) {
            Log.d(TAG, "Discovery stopped: " + serviceType);
        }

        @Override
        public void onStartDiscoveryFailed(String serviceType, int errorCode) {
            Log.e(TAG, "Discovery failed for " + serviceType + ". Error code=" + errorCode);
            nsdManager.stopServiceDiscovery(this);
        }

        @Override
        public void onStopDiscoveryFailed(String serviceType, int errorCode) {
            Log.e(TAG, "Discovery failed for " + serviceType + ". Error code=" + errorCode);
            nsdManager.stopServiceDiscovery(this);
        }

        private Service fromNsdServiceInfo(NsdServiceInfo nsdServiceInfo) {
            final String name = nsdServiceInfo.getServiceName();
            final String host = nsdServiceInfo.getHost().getHostAddress();
            final int port = nsdServiceInfo.getPort();

            return new Service(name, host, port);
        }
    }
}
