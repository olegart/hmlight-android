package com.example.lightcontrol.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.nsd.NsdManager;
import android.os.Bundle;
import android.content.Intent;
import android.os.CountDownTimer;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.util.Log;
import android.net.nsd.NsdServiceInfo;
import android.net.nsd.NsdManager.ResolveListener;
import android.net.NetworkInfo;

public class MainActivity extends Activity {

    private WebView mWebView;
    String serverURL;

    NsdManager.DiscoveryListener mDiscoveryListener;
    NsdManager mNsdManager;
    final String TAG = "Homelight";
    final String SERVICE_TYPE = "_http._tcp.";
    Boolean isNsdRunning;
    Boolean isNsdUsed;
    CountDownTimer NsdTimer;

    ProgressDialog progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ConnectivityManager connManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo mWifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

        Log.i(TAG, "Checking for connection");

        if (!mWifi.isConnected()) {
            showDialog("Нет подключения Wi-Fi", "Для управления светом необходимо подключение к домашней сети Wi-Fi");
        }

        mWebView = (WebView) findViewById(R.id.activity_main_webview);
        mWebView.getSettings().setJavaScriptEnabled(true);
        mWebView.setWebViewClient(new WebViewClient() {
                                      public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                                          showDialog("Ошибка загрузки", "Ошибка загрузки страницы управления. Пожалуйста, проверьте настройки программы и сервера.");
                                      }
                                  });
        serverURL = "";
        isNsdRunning = false;
        NsdTimer = new CountDownTimer(2500, 250) {

            public void onTick(long millisUntilFinished) {
            }

            public void onFinish() {
                stopProgress();
                if (mNsdManager != null) {
                    mNsdManager.stopServiceDiscovery(mDiscoveryListener);
                }
                showDialog("Автоматический поиск", "Сервер управления светом не найден. Пожалуйста, проверьте настройки приложения и сервера.");
            }
        };

        loadPref();
        if (isNsdUsed) {
            registerService();
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Intent intent = new Intent();
        intent.setClass(MainActivity.this, SetPreferenceActivity.class);
        startActivityForResult(intent, 0);
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        loadPref();
    }

    private void loadPref(){
        SharedPreferences mySharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        if (mySharedPreferences.getBoolean("connect_auto", true)) {
            // automatic server discovery
            isNsdUsed = true;
//            registerService();
        }
        else {
            isNsdUsed = false;
            serverURL = mySharedPreferences.getString("connect_addr", "");
            if (!serverURL.startsWith("http://") && !serverURL.startsWith("https://")) {
                serverURL = "http://" + serverURL;
            }
            mWebView.loadUrl(serverURL);
        }
    }

    protected void onRestart() {
        super.onRestart();
        if (!isNsdRunning && isNsdUsed) {
            registerService();
        }
        mWebView.loadUrl(serverURL);
    }

    public void showDialog(String title, String txt) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(txt)
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                    }
                })
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    public void showProgress() {
        progress = ProgressDialog.show(this, "Поиск сервера", "Автоматический поиск сервера управления светом в сети Wi-Fi.", true);
    }

    public void stopProgress() {
        progress.dismiss();
    }

    public void registerService() {
        mNsdManager = (NsdManager) getApplicationContext().getSystemService(Context.NSD_SERVICE);
        initializeDiscoveryListener();
        mNsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, mDiscoveryListener);
    }

    public void initializeDiscoveryListener() {
        mDiscoveryListener = new NsdManager.DiscoveryListener() {

            @Override
            public void onDiscoveryStarted(String regType) {
                NsdTimer.start();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        showProgress();
                    }
                });
                Log.i(TAG, "Service discovery started");
                isNsdRunning = true;
            }

            @Override
            public void onServiceFound(NsdServiceInfo service) {
                if (service.getServiceName().contains("Homelight")){
                    mNsdManager.resolveService(service, new ResolveListener() {

                        @Override
                        public void onServiceResolved(NsdServiceInfo serviceInfo) {
                            serverURL = "http:/" + serviceInfo.getHost().toString();
                            mNsdManager.stopServiceDiscovery(mDiscoveryListener);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    NsdTimer.cancel();
                                    mWebView.loadUrl(serverURL);
                                    stopProgress();
                                }
                            });
                        }

                        @Override
                        public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
                            // TODO Auto-generated method stub
                        }
                    });
                }
            }

            @Override
            public void onServiceLost(NsdServiceInfo service) {
                // When the network service is no longer available.
                // Internal bookkeeping code goes here.
                Log.e(TAG, "service lost" + service);
            }

            @Override
            public void onDiscoveryStopped(String serviceType) {
                isNsdRunning = false;
                Log.i(TAG, "Discovery stopped: " + serviceType);
            }

            @Override
            public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                isNsdRunning = false;
                Log.e(TAG, "Discovery failed: Error code:" + errorCode);
            }

            @Override
            public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                isNsdRunning = false;
                Log.e(TAG, "Discovery failed: Error code:" + errorCode);
            }
        };
    }
}