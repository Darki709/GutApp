package com.example.gutapp.ui;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.splashscreen.SplashScreen;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.gutapp.R;
import com.example.gutapp.data.UserGlobals;
import com.example.gutapp.database.DB_Helper;
import com.example.gutapp.session.NetworkClient;
import com.example.gutapp.session.Responses.LoginResponse;
import com.example.gutapp.session.SessionCallback;
import com.example.gutapp.session.SessionManager;

public class LoginPage extends AppCompatActivity implements View.OnClickListener, SessionCallback {
    
    //declaring global pointer to core elements of the page
    TextView textTitle, textDescription;
    EditText editTextUsername, editTextPassword;
    Button buttonLogin, buttonRegister;
    private View loadingOverlay;

    public final static String APP_LOG_TAG = "GutAppUi";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login_page);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        DB_Helper.getInstance(this);//make sure db is ready

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
        NetworkClient.getInstance(this).start(this); //tell session manager to work with this activity

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
    public void onDataReceived(int msgType, Object parsedData) {
        String error;
        switch (msgType) {
            case SessionManager.TYPE_AUTH_SUCCESS:
                Toast.makeText(this, "Logged in as " + UserGlobals.USER_NAME, Toast.LENGTH_SHORT).show();
                UserGlobals.LOGGED_IN = true;
                Intent intent = new Intent(this, HomeActivity.class);
                startActivity(intent);
                break;
            case SessionManager.TYPE_REGISTER_ERROR:
                setLoading(false);
                error = (String) parsedData == "" ? "This user already exist" : (String) parsedData;
                Toast.makeText(this, "Registration failed: " + error, Toast.LENGTH_SHORT).show();
                break;
            case SessionManager.TYPE_LOGIN_ERROR:
                setLoading(false);
                Toast.makeText(this, "Login failed: " + (String)parsedData, Toast.LENGTH_SHORT).show();
                break;
            default:
                break;

        }
    }

    @Override
    public void onActionRequired(int actionType) {
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
        NetworkClient.getInstance(this).getSessionManager().removeCallback();
    }
}
