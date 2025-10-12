package com.example.gutapp.database;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.example.gutapp.data.UserGlobals;

public class UserTableHelper implements Table{

    //table constants
    private static final String TABLE_NAME = "users";
    private static final String COLUMN_ID = "_id";
    private static final String COLUMN_USERNAME = "username";
    private static final String COLUMN_PASSWORD = "password";

    //database helper for access to the database
    private DB_Helper DB_HELPER;


    //creates the helper and gives it it's database helper for database access
    public UserTableHelper(DB_Helper db_helper){
        DB_HELPER = db_helper;
    }


    //returns true if user added successfully, false if the username already exists
    public boolean insertUser(String username, String password) {
        SQLiteDatabase db = DB_HELPER.getWritableDatabase();
        Cursor cursor;
        //check that there is no user with the same username in the database
        String query = "SELECT username FROM " + TABLE_NAME + " WHERE username = ?";
        String[] args = {username};
        try {
            cursor = db.rawQuery(query, args);
        } catch (Exception e) {
            Log.e(DB_HELPER.DB_LOG_TAG, "error " + e.getMessage());
            throw e;
        }
        if (cursor.getCount() > 0) {
            return false;
        }
        //if there was no user with the same username on the database already inserts new user
        // to the databasegit
        cursor.close();
        String insertQuery = "INSERT INTO " + TABLE_NAME + " (" + COLUMN_USERNAME + ", " +
                COLUMN_PASSWORD + ") VALUES (?, ?)";
        args = new String[]{username, password};
        try {
            db.execSQL(insertQuery, args);
        } catch (Exception e) {
            Log.e(DB_HELPER.DB_LOG_TAG, "error" + e.getMessage());
            throw e;
        }
        //retrieve the newly created user id
        query = "SELECT "+ COLUMN_ID +" FROM " + TABLE_NAME + " WHERE username = ?";
        args = new String[]{username};
        try{
            cursor = db.rawQuery(query, args);
            Log.i(DB_HELPER.DB_LOG_TAG, "id retrieved " + cursor.getString(0));
        }
        catch (Exception e) {
            Log.e(DB_HELPER.DB_LOG_TAG, "error create table " + e.getMessage());
            throw e;
        }
        //inserts the logged in user data to the user globals class
        UserGlobals.USER_NAME = username;
        UserGlobals.ID = cursor.getString(0);
        UserGlobals.LOGGED_IN = true;
        return true;
    }


    //validate if the user exists in the database and has the correct password
    public boolean validateUser(String username, String password) {
        SQLiteDatabase db = DB_HELPER.getReadableDatabase();
        String query = "SELECT " + COLUMN_ID + " FROM " + TABLE_NAME + " WHERE username = ? AND password = ?";
        String[] args = {username, password};
        Cursor cursor;
        try{
            cursor = db.rawQuery(query, args);
            Log.i(DB_HELPER.DB_LOG_TAG, "user validated, user id " + cursor.getString(0));
        }
        catch (Exception e) {
            Log.e(DB_HELPER.DB_LOG_TAG, "error on validate user " + e.getMessage());
            throw e;
        }
        //checks if cursor has arguments, if it has then there is a line in the data base with the
        // inputted username and password, inserts user data to user global class
        if(cursor.getCount() > 0){
            UserGlobals.USER_NAME = username;
            UserGlobals.ID = cursor.getString(0);
            UserGlobals.LOGGED_IN = true;
            cursor.close();
            return true;
        }
        return false;
    }


    //default table creation query that db_helper uses to initialize the table on creation
    @Override
    public String createTable() {
        return "CREATE TABLE " + TABLE_NAME + " (" +
                COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COLUMN_USERNAME + " TEXT, " +
                COLUMN_PASSWORD + " TEXT)";
    }

    @Override
    public String getName() {
        return TABLE_NAME;
    }

}
