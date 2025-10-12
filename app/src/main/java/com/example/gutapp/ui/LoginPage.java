package com.example.gutapp.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.gutapp.R;
import com.example.gutapp.database.DB_Helper;
import com.example.gutapp.database.DB_Index;
import com.example.gutapp.database.UserTableHelper;

public class LoginPage extends AppCompatActivity implements View.OnClickListener {

    
    //declaring global pointer to core elements of the page
    DB_Helper db_helper;
    UserTableHelper userTableHelper;
    TextView textTitle;
    EditText editTextUsername;
    EditText editTextPassword;
    TextView textDescription;
    Button buttonLogin;
    Button buttonRegister;

    


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

        db_helper = new DB_Helper(this);
        db_helper.getWritableDatabase();
        userTableHelper = (UserTableHelper)db_helper.getHelper(DB_Index.USER_TABLE);
        
        //bind pointers to elements
        textTitle = findViewById(R.id.textTitle);
        editTextUsername = findViewById(R.id.editTextUsername);
        editTextPassword = findViewById(R.id.editTextPassword);
        textDescription = findViewById(R.id.textDescription);
        buttonLogin = findViewById(R.id.buttonLogin);
        buttonRegister = findViewById(R.id.buttonRegister);

        buttonLogin.setOnClickListener(this);
        buttonRegister.setOnClickListener(this);
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
        if(!userTableHelper.validateUser(username, password)) {
            Toast.makeText(this, "No Account with these 2 credentials", Toast.LENGTH_LONG).show();
            return;
        }
        Intent intent = new Intent(this, HomeActivity.class);
        startActivity(intent);
    }

    public void UserRegister(String username, String password){
            if(!userTableHelper.insertUser(username, password)){
                Toast.makeText(this, "Account with this username already exists", Toast.LENGTH_LONG).show();
                return;
            }
            Toast.makeText(this, "Account created successfully", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(this, HomeActivity.class);
            startActivity(intent);
    }
}
