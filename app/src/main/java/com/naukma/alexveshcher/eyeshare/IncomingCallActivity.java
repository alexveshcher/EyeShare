package com.naukma.alexveshcher.eyeshare;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.naukma.alexveshcher.eyeshare.util.Constants;
import com.pubnub.api.Callback;
import com.pubnub.api.Pubnub;

import org.json.JSONObject;

import me.kevingleason.pnwebrtc.PnPeerConnectionClient;

/** Activity starts for volunteer when blind needs help */
public class IncomingCallActivity extends Activity {
    public final static String ROLE = "ROLE";
    //private SharedPreferences mSharedPreferences;
    private String username;
    private String callUser;
    private Pubnub mPubNub;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_incoming_call);

        Intent intent = getIntent();
        username = intent.getStringExtra(Constants.USER_NAME);
        Bundle extras = getIntent().getExtras();
        this.callUser = extras.getString(Constants.CALL_USER);
        this.mPubNub  = new Pubnub(Constants.PUB_KEY, Constants.SUB_KEY);
        this.mPubNub.setUUID(this.username);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_incoming_call, menu);
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

    public void acceptCall(View view){
        Intent intent = new Intent(IncomingCallActivity.this, VideoChatActivity.class);
        intent.putExtra(Constants.USER_NAME, this.username);
        intent.putExtra(Constants.CALL_USER, this.callUser);
        intent.putExtra(ROLE,"VOLUNTEER");
        startActivity(intent);
        finish();
    }

    /**
     * Publish a hangup command if rejecting call.
     * @param view
     */
    public void rejectCall(View view){
        JSONObject hangupMsg = PnPeerConnectionClient.generateHangupPacket(this.username);
        this.mPubNub.publish(this.callUser,hangupMsg, new Callback() {
            @Override
            public void successCallback(String channel, Object message) {
                Intent intent = new Intent(IncomingCallActivity.this, WaitActivity.class);
                intent.putExtra(Constants.USER_NAME,username);
                startActivity(intent);
            }
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
        if(this.mPubNub!=null){
            this.mPubNub.unsubscribeAll();
        }
    }
}
