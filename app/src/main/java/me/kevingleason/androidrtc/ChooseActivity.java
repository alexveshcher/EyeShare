package me.kevingleason.androidrtc;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.pubnub.api.Callback;
import com.pubnub.api.Pubnub;
import com.pubnub.api.PubnubError;
import com.pubnub.api.PubnubException;

import org.json.JSONException;
import org.json.JSONObject;

import me.kevingleason.androidrtc.util.Constants;

public class ChooseActivity extends Activity {
    private Pubnub mPubNub;
    private String username = "blind";
    private String stdByChannel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_choose);

        this.stdByChannel = this.username + Constants.STDBY_SUFFIX;
        initPubNub();
    }

    /**When user clicks 'I can help' */
    public void volunteer(View view){
        String username = "volunteer";


        SharedPreferences sp = getSharedPreferences(Constants.SHARED_PREFS,MODE_PRIVATE);
        SharedPreferences.Editor edit = sp.edit();
        edit.putString(Constants.USER_NAME, username);
        edit.apply();

        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
    }

    public void blind(View view) {
        String callNum = "volunteer";
        dispatchCall(callNum);
    }


    /**TODO: Debate who calls who. Should one be on standby? Or use State API for busy/available
     * Check that user is online. If they are, dispatch the call by publishing to their standby
     *   channel. If the publish was successful, then change activities over to the video chat.
     * The called user will then have the option to accept of decline the call. If they accept,
     *   they will be brought to the video chat activity as well, to connect video/audio. If
     *   they decline, a hangup will be issued, and the VideoChat adapter's onHangup callback will
     *   be invoked.
     * @param callNum Number to publish a call to.
     */
    public void dispatchCall(final String callNum){
        final String callNumStdBy = callNum + Constants.STDBY_SUFFIX;
        this.mPubNub.hereNow(callNumStdBy, new Callback() {
            @Override
            public void successCallback(String channel, Object message) {
                Log.d("MA-dC", "HERE_NOW: " +" CH - " + callNumStdBy + " " + message.toString());
                try {
                    int occupancy = ((JSONObject) message).getInt(Constants.JSON_OCCUPANCY);
                    /*if (occupancy == 0) {
                        //showToast("User is not online!");
                        //return;
                    }*/
                    JSONObject jsonCall = new JSONObject();
                    jsonCall.put(Constants.JSON_CALL_USER, username);
                    jsonCall.put(Constants.JSON_CALL_TIME, System.currentTimeMillis());
                    mPubNub.publish(callNumStdBy, jsonCall, new Callback() {
                        @Override
                        public void successCallback(String channel, Object message) {
                            Log.d("MA-dC", "SUCCESS: " + message.toString());
                            Intent intent = new Intent(ChooseActivity.this, VideoChatActivity.class);
                            intent.putExtra(Constants.USER_NAME, username);
                            intent.putExtra(Constants.CALL_USER, callNum);  // Only accept from this number?
                            startActivity(intent);
                        }
                    });
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    /**
     * Ensures that toast is run on the UI thread.
     * @param message
     */
    private void showToast(final String message){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(ChooseActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Subscribe to standby channel so that it doesn't interfere with the WebRTC Signaling.
     */
    public void initPubNub(){
        this.mPubNub  = new Pubnub(Constants.PUB_KEY, Constants.SUB_KEY);
        this.mPubNub.setUUID(this.username);
        subscribeStdBy();
    }

    /**
     * Subscribe to standby channel
     */
    private void subscribeStdBy(){
        try {
            this.mPubNub.subscribe(this.stdByChannel, new Callback() {
                @Override
                public void successCallback(String channel, Object message) {
                    Log.d("MA-iPN", "MESSAGE: " + message.toString());
                    if (!(message instanceof JSONObject)) return; // Ignore if not JSONObject
                    JSONObject jsonMsg = (JSONObject) message;
                    try {
                        if (!jsonMsg.has(Constants.JSON_CALL_USER)) return;     //Ignore Signaling messages.
                        String user = jsonMsg.getString(Constants.JSON_CALL_USER);
                        dispatchIncomingCall(user);
                    } catch (JSONException e){
                        e.printStackTrace();
                    }
                }

                @Override
                public void connectCallback(String channel, Object message) {
                    Log.d("MA-iPN", "CONNECTED: " + message.toString());
                    setUserStatus(Constants.STATUS_AVAILABLE);
                }

                @Override
                public void errorCallback(String channel, PubnubError error) {
                    Log.d("MA-iPN","ERROR: " + error.toString());
                }
            });
        } catch (PubnubException e){
            Log.d("HERE","HEREEEE");
            e.printStackTrace();
        }
    }

    /**
     * Handle incoming calls. TODO: Implement an accept/reject functionality.
     * @param userId
     */
    private void dispatchIncomingCall(String userId){
        showToast("Call from: " + userId);
        Intent intent = new Intent(ChooseActivity.this, IncomingCallActivity.class);
        intent.putExtra(Constants.USER_NAME, username);
        intent.putExtra(Constants.CALL_USER, userId);
        startActivity(intent);
    }

    private void setUserStatus(String status){
        try {
            JSONObject state = new JSONObject();
            state.put(Constants.JSON_STATUS, status);
            this.mPubNub.setState(this.stdByChannel, this.username, state, new Callback() {
                @Override
                public void successCallback(String channel, Object message) {
                    Log.d("MA-sUS","State Set: " + message.toString());
                }
            });
        } catch (JSONException e){
            e.printStackTrace();
        }
    }

}
