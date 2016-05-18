package com.naukma.alexveshcher.eyeshare;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;

import com.naukma.alexveshcher.eyeshare.util.Constants;

/**
 * Login Activity for the first time the app is opened, or when a user clicks the sign out button.
 * Saves the username in SharedPreferences.
 */
public class LoginActivity extends Activity {

    private EditText mUsername;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mUsername = (EditText) findViewById(R.id.login_username);

        /*Bundle extras = getIntent().getExtras();
        if (extras != null){
            String lastUsername = extras.getString("oldUsername", "");
            mUsername.setText(lastUsername);
        }*/
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_login, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Takes the username from the EditText, check its validity and saves it if valid.
     *   Then, redirects to the WaitActivity.
     * @param view Button clicked to trigger call to joinChat
     */
    public void joinChat(View view){
        String username = mUsername.getText().toString();
        if (!validUsername(username))
            return;
        Intent intent = new Intent(this, WaitActivity.class);
        intent.putExtra(Constants.USER_NAME,username);
        startActivity(intent);
        finish();
    }

    /**
     * Optional function to specify what a username in your chat app can look like.
     * @param username The name entered by a user.
     * @return is username valid
     */
    private boolean validUsername(String username) {
        if (username.length() == 0) {
            mUsername.setError("Username cannot be empty.");
            return false;
        }
        if (username.length() > 16) {
            mUsername.setError("Username too long.");
            return false;
        }
        return true;
    }
}
