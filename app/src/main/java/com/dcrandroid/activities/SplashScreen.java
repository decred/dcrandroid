package com.dcrandroid.activities;

import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.AnimationDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.widget.ImageView;
import android.widget.TextView;

import com.dcrandroid.BuildConfig;
import com.dcrandroid.MainActivity;
import com.dcrandroid.R;
import com.dcrandroid.data.Constants;
import com.dcrandroid.dialog.InfoDialog;
import com.dcrandroid.util.WalletData;
import com.dcrandroid.util.PreferenceUtil;
import com.dcrandroid.util.Utils;

import java.io.File;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import dcrlibwallet.Dcrlibwallet;
import dcrlibwallet.LibWallet;

/**
 * Created by Macsleven on 24/12/2017.
 */

public class SplashScreen extends AppCompatActivity implements Animation.AnimationListener {

    private final int PASSWORD_REQUEST_CODE = 1;
    private ImageView imgAnim;
    private PreferenceUtil util;
    private TextView tvLoading;
    private Thread loadThread;
    private WalletData constants;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!isTaskRoot()) {
            final Intent intent = getIntent();
            final String intentAction = intent.getAction();
            if (intent.hasCategory(Intent.CATEGORY_LAUNCHER) && intentAction != null && intentAction.equals(Intent.ACTION_MAIN)) {
                finish();
                return;
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            View decorView = getWindow().getDecorView();
            decorView.setSystemUiVisibility(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS |
                    View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
        util = new PreferenceUtil(this);
        setContentView(R.layout.splash_page);

        if (BuildConfig.IS_TESTNET) {
            findViewById(R.id.tv_testnet).setVisibility(View.VISIBLE);
        }

        imgAnim = findViewById(R.id.splashscreen_icon);

        imgAnim.setOnClickListener(new DoubleClickListener() {
            @Override
            public void onSingleClick(View v) {

            }

            @Override
            public void onDoubleClick(View v) {
                if (loadThread != null) {
                    loadThread.interrupt();
                }
                Intent intent = new Intent(getApplicationContext(), SettingsActivity.class);
                startActivityForResult(intent, 2);
            }
        });

        imgAnim.setBackgroundResource(R.drawable.load_animation);
        imgAnim.post(new Runnable() {
            @Override
            public void run() {
                AnimationDrawable loadAnimation = (AnimationDrawable) imgAnim.getBackground();
                loadAnimation.start();
            }
        });

        tvLoading = findViewById(R.id.loading_status);
        startup();
    }

    private void startup() {
        constants = WalletData.getInstance();

        String homeDir = getFilesDir() + "/wallet";

        constants.wallet = new LibWallet(homeDir, Constants.BADGER_DB, BuildConfig.NetType);
        constants.wallet.setLogLevel(util.get(Constants.LOGGING_LEVEL));
        constants.wallet.initLoader();

        String walletDB;

        if (BuildConfig.IS_TESTNET) {
            walletDB = "/testnet3/wallet.db";
        } else {
            walletDB = "/mainnet/wallet.db";
        }

        File f = new File(homeDir, walletDB);
        if (!f.exists()) {
            loadThread = new Thread() {
                public void run() {
                    try {
                        sleep(3000);
                        createWallet();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            };
            loadThread.start();
        } else {
            checkEncryption();
        }
    }

    private void setText(final String str) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                tvLoading.setText(str);
            }
        });
    }

    private void createWallet() {
        Intent i = new Intent(SplashScreen.this, SetupWalletActivity.class);
        startActivity(i);
        finish();
    }

    private void openWallet(final String publicPass) {
        loadThread = new Thread() {
            public void run() {
                try {
                    setText(getString(R.string.opening_wallet));
                    constants.wallet.openWallet(publicPass.getBytes());
                    Intent i = new Intent(SplashScreen.this, MainActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(i);
                    //Finish all the activities before this
                    ActivityCompat.finishAffinity(SplashScreen.this);
                } catch (final Exception e) {
                    e.printStackTrace();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            InfoDialog infoDialog = new InfoDialog(SplashScreen.this)
                                    .setDialogTitle(getString(R.string.failed_to_open_wallet))
                                    .setMessage(Utils.translateError(SplashScreen.this, e))
                                    .setIcon(R.drawable.np_amount_withdrawal) //Temporary Icon
                                    .setPositiveButton(getString(R.string.exit_cap), new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            finish();
                                        }
                                    });

                            if (e.getMessage().equals(Dcrlibwallet.ErrInvalidPassphrase)) {
                                if (util.get(Constants.STARTUP_PASSPHRASE_TYPE).equals(Constants.PIN)) {
                                    infoDialog.setMessage(getString(R.string.invalid_pin));
                                }
                                infoDialog.setNegativeButton(getString(R.string.exit_cap), new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        finish();
                                    }
                                }).setPositiveButton(getString(R.string.retry_caps), new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        Intent i;

                                        if (util.get(Constants.STARTUP_PASSPHRASE_TYPE).equals(Constants.PASSWORD)) {
                                            i = new Intent(SplashScreen.this, EnterPasswordActivity.class);
                                        } else {
                                            i = new Intent(SplashScreen.this, EnterPassCode.class);
                                        }

                                        i.putExtra(Constants.SPENDING_PASSWORD, false);
                                        i.putExtra(Constants.NO_RETURN, true);
                                        startActivityForResult(i, PASSWORD_REQUEST_CODE);
                                    }
                                });
                            }

                            infoDialog.setCancelable(false);
                            infoDialog.setCanceledOnTouchOutside(false);
                            infoDialog.show();

                        }
                    });
                }
            }
        };
        loadThread.start();
    }

    public void checkEncryption() {
        if (util.getBoolean(Constants.ENCRYPT)) {
            Intent i;

            if (util.get(Constants.STARTUP_PASSPHRASE_TYPE).equals(Constants.PASSWORD)) {
                i = new Intent(this, EnterPasswordActivity.class);
            } else {
                i = new Intent(this, EnterPassCode.class);
            }

            i.putExtra(Constants.SPENDING_PASSWORD, false);
            i.putExtra(Constants.NO_RETURN, true);
            startActivityForResult(i, PASSWORD_REQUEST_CODE);
        } else {
            openWallet(Constants.INSECURE_PUB_PASSPHRASE);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 2) {
            startup();
        } else if (requestCode == PASSWORD_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                openWallet(data.getStringExtra(Constants.PASSPHRASE));
            }
        }
    }

    @Override
    public void onBackPressed() {
    }

    @Override
    public void onAnimationStart(Animation animation) {
    }

    @Override
    public void onAnimationEnd(Animation animation) {
    }

    @Override
    public void onAnimationRepeat(Animation animation) {
    }

    public abstract class DoubleClickListener implements View.OnClickListener {

        private static final long DOUBLE_CLICK_TIME_DELTA = 300;//milliseconds

        long lastClickTime = 0;

        @Override
        public void onClick(View v) {
            long clickTime = System.currentTimeMillis();
            if (clickTime - lastClickTime < DOUBLE_CLICK_TIME_DELTA) {
                onDoubleClick(v);
                lastClickTime = 0;
            } else {
                onSingleClick(v);
            }
            lastClickTime = clickTime;
        }

        public abstract void onSingleClick(View v);

        public abstract void onDoubleClick(View v);
    }
}