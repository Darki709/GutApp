package com.example.gutapp.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.splashscreen.SplashScreen;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.gutapp.R;
import com.example.gutapp.data.UserGlobals;
import com.example.gutapp.database.DB_Helper;
import com.example.gutapp.session.DataType;
import com.example.gutapp.session.NetworkClient;
import com.example.gutapp.session.SessionCallback;
import com.example.gutapp.session.SessionManager;
import com.example.gutapp.session.background.NetworkService;
import com.google.firebase.FirebaseApp;

public class LoginPage extends AppCompatActivity implements View.OnClickListener, SessionCallback {

    private MediaPlayer startupPlayer;

    //ask for permissions
    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (!isGranted) {
                    // Explain to the user that they won't see alert notifications
                    Toast.makeText(this, "Notification permission is required for alerts", Toast.LENGTH_LONG).show();
                }
            });
    
    //declaring global pointer to core elements of the page
    TextView textTitle, textDescription;
    EditText editTextUsername, editTextPassword;
    Button buttonLogin, buttonRegister;
    private View loadingOverlay;

    public final static String APP_LOG_TAG = "GutAppUi";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen splashScreen = SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login_page);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        playStartupSound();

        FirebaseApp.initializeApp(this);
        DB_Helper.getInstance(this);//make sure db is ready

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED) {

            // Trigger the system popup
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        }

        NetworkClient.getInstance(this).getSessionManager().setUiCallback(this);
        //start background alert listener
        Intent serviceIntent = new Intent(this, NetworkService.class);
        startForegroundService(serviceIntent);
        //check if the background service has an active logged in connection
        NetworkClient.getInstance(this).start();
        if(UserGlobals.LOGGED_IN) startActivity(new Intent(this, HomeActivity.class));

        //bind pointers to elements
        textTitle = findViewById(R.id.textTitle);
        editTextUsername = findViewById(R.id.editTextUsername);
        editTextPassword = findViewById(R.id.editTextPassword);
        textDescription = findViewById(R.id.textDescription);
        buttonLogin = findViewById(R.id.buttonLogin);
        buttonRegister = findViewById(R.id.buttonRegister);
        loadingOverlay = findViewById(R.id.loadingOverlay);

        buttonLogin.setOnClickListener(this);
        buttonRegister.setOnClickListener(this);
        Log.i(APP_LOG_TAG, "Login page loaded");
        setLoading(true);

        //if it's the first time running the app the background process has no active connection so start the network thread
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if(id == R.id.buttonLogin){
            UserLogin(editTextUsername.getText().toString(), editTextPassword.getText().toString());
        }
        else if(id == R.id.buttonRegister){
            UserRegister(editTextUsername.getText().toString(), editTextPassword.getText().toString());
        }
    }

    private void playStartupSound() {
        try {
            startupPlayer = MediaPlayer.create(this, R.raw.app_startup);
            if (startupPlayer != null) {
                // Set volume if you want it to be a subtle background jingle
                startupPlayer.setVolume(0.6f, 0.6f);
                startupPlayer.setLooping(true);
                startupPlayer.start();
            }
        } catch (Exception e) {
            Log.e("Startup", "Failed to play startup sound", e);
        }
    }

    public void UserLogin(String username, String password){
        setLoading(true);
        NetworkClient.getInstance(this).getSessionManager().pushCredentials(username, password, 1);
    }

    public void UserRegister(String username, String password){
        setLoading(true);
        NetworkClient.getInstance(this).getSessionManager().pushCredentials(username, password, 0);
    }


    //callback methods to work with the session manager thread
    @Override
    public void onDataReceived(DataType msgType, Object parsedData) {
        String error;
        switch (msgType) {
            case AUTH_SUCCESS:
                Toast.makeText(this, "Logged in as " + UserGlobals.USER_NAME, Toast.LENGTH_SHORT).show();
                UserGlobals.LOGGED_IN = true;
                Intent intent = new Intent(this, HomeActivity.class);
                startActivity(intent);
                break;
            case REGISTER_ERROR:
                setLoading(false);
                error = (String) parsedData == "" ? "This user already exist" : (String) parsedData;
                Toast.makeText(this, "Registration failed: " + error, Toast.LENGTH_SHORT).show();
                break;
            case LOGIN_ERROR:
                setLoading(false);
                Toast.makeText(this, "Login failed: " + (String)parsedData, Toast.LENGTH_SHORT).show();
                break;
            default:
                break;

        }
    }

    @Override
    public void onActionRequired(int actionType, Object data) {
            switch(actionType){
                case SessionManager.ACTION_SHOW_LOGIN_UI:
                    setLoading(false);
                    break;
                default:
                    break;
            }
    }

    private void setLoading(boolean loading) {
        if (loading) loadingOverlay.setVisibility(View.VISIBLE);
        else loadingOverlay.setVisibility(View.GONE);
    }

    @Override
    protected void onStop(){
        super.onStop();
        if (startupPlayer != null && startupPlayer.isPlaying()) {
            startupPlayer.stop();
            startupPlayer.release();
            startupPlayer = null;
        }
    }
}
