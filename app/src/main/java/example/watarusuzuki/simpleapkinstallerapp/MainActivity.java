package example.watarusuzuki.simpleapkinstallerapp;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.vision.barcode.Barcode;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import example.watarusuzuki.simpleapkinstallerapp.barcodereader.BarcodeCaptureActivity;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();

    private static String URL_YOUR_APK = "(・∀・)???";

    private static final int RC_BARCODE_CAPTURE = 9001;
    private static final int RC_MANAGE_UNKNOWN_APP_SOURCES = 1234;
    private static final int RC_WRITE_EXTERNAL_STORAGE = 001;

    private FloatingActionButton fab;
    private TextView urlTextView;

    private SharedPreferences sharedPreferences;
    private static final String DATA_YOUR_APK = "DATA_YOUR_APK";
    private static final String KEY_URL_YOUR_APK = "KEY_URL_YOUR_APK";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && (!getPackageManager().canRequestPackageInstalls()) ) {
                    startActivityForResult(
                            new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                                    .setData(Uri.parse(String.format("package:%s", getPackageName()))),
                            RC_MANAGE_UNKNOWN_APP_SOURCES);
                } else {
                    try {
                        new HttpGetTask().execute(new URL(URL_YOUR_APK));
                    } catch (MalformedURLException e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, RC_WRITE_EXTERNAL_STORAGE);
            }
        }
        urlTextView = findViewById(R.id.description);
        sharedPreferences = getSharedPreferences(DATA_YOUR_APK, MODE_PRIVATE);
        if (!TextUtils.isEmpty(sharedPreferences.getString(KEY_URL_YOUR_APK, ""))) {
            URL_YOUR_APK = sharedPreferences.getString(KEY_URL_YOUR_APK, "");
            urlTextView.setText(URL_YOUR_APK);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            Snackbar.make(fab, "(・A・)", Snackbar.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == RC_BARCODE_CAPTURE) {
            String message;
            if (resultCode == CommonStatusCodes.SUCCESS) {
                if (data != null) {
                    Barcode barcode = data.getParcelableExtra(BarcodeCaptureActivity.BarcodeObject);
                    URL_YOUR_APK = barcode.displayValue;
                    urlTextView.setText(URL_YOUR_APK);
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putString(KEY_URL_YOUR_APK, URL_YOUR_APK);
                    editor.apply();

                    message = getString(R.string.barcode_success);
                    Log.d(TAG, "Barcode read: " + barcode.displayValue);
                } else {
                    message = getString(R.string.barcode_failure);
                    Log.d(TAG, "No barcode captured, intent data is null");
                }
            } else {
                message = String.format(getString(R.string.barcode_error), CommonStatusCodes.getStatusCodeString(resultCode));
            }
            Snackbar.make(fab, message, Snackbar.LENGTH_LONG).show();
        }
        else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_read_barcode) {
            Intent intent = new Intent(this, BarcodeCaptureActivity.class);
            intent.putExtra(BarcodeCaptureActivity.AutoFocus, true);
            intent.putExtra(BarcodeCaptureActivity.UseFlash, false);

            startActivityForResult(intent, RC_BARCODE_CAPTURE);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private Intent downloadMakaizouApk(URL jenkinsUrl) {
        try {
            HttpURLConnection c = (HttpURLConnection) jenkinsUrl.openConnection();
//            final String userPassword = USERNAME + ":" + PASSWORD;
//            final String encodeAuthorization = Base64.encodeToString(userPassword.getBytes(), Base64.NO_WRAP);
//            c.setRequestProperty("Authorization", "Basic " + encodeAuthorization);
            c.setRequestMethod("GET");
            c.connect();

            String PATH = Environment.getExternalStorageDirectory() + "/external_share_apk/";
            File file = new File(PATH);
            file.mkdirs();

            File outputFile = new File(file, "app.apk");
            FileOutputStream fos = new FileOutputStream(outputFile);

            InputStream is = c.getInputStream();
            byte[] buffer = new byte[1024];
            int len = 0;
            while ((len = is.read(buffer)) != -1) {
                fos.write(buffer, 0, len);
            }
            fos.close();
            is.close();

            Intent intent;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Uri apkUri = FileProvider.getUriForFile(this, "example.watarusuzuki.simpleapkinstallerapp.fileprovider", outputFile);
                intent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
                intent.setData(apkUri);
                intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } else {
                Uri apkUri = Uri.fromFile(outputFile);
                intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }

            return intent;

        } catch (MalformedURLException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return null;
    }

    private class HttpGetTask extends AsyncTask<URL, Void, Intent> {

        @Override
        protected Intent doInBackground(URL... urls) {
            final URL url = urls[0];
            return downloadMakaizouApk(url);
        }

        @Override
        protected void onPostExecute(final Intent intent) {
            super.onPostExecute(intent);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    startActivity(intent);
                }
            });
        }
    }
}
