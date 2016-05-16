package com.naukma.alexveshcher.eyeshare;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.naukma.alexveshcher.eyeshare.util.Constants;
import com.pubnub.api.Callback;
import com.pubnub.api.Pubnub;
import com.pubnub.api.PubnubError;
import com.pubnub.api.PubnubException;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class ChooseActivity extends Activity  {
    public final static String ROLE = "ROLE";

    private Pubnub mPubNub;
    private String username = "blind";
    private String stdByChannel;
    ///private String users_online = "nil";
    //private String user = "nouser";
    private List<String> channels = new ArrayList<>();
    TextView view;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_choose);

        this.stdByChannel = this.username + Constants.STDBY_SUFFIX;
        initPubNub();
        view = (TextView) findViewById(R.id.online);
        getOnlineUsersCount();
        getOnlineUsers();


        //showToast(user);
    }

    /**When user clicks 'I can help' */
    public void volunteer(View view){
        String username = "volunteer";
        //showToast(channels.get(0)+channels.get(1));
        if(isInternetAvailable()){
            SharedPreferences sp = getSharedPreferences(Constants.SHARED_PREFS,MODE_PRIVATE);
            SharedPreferences.Editor edit = sp.edit();
            edit.putString(Constants.USER_NAME, username);
            edit.apply();

            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
        }
        else
            Toast.makeText(getApplicationContext(), "You are not online", Toast.LENGTH_SHORT).show();
    }

    public void blind(View view) {
        //String callNum = "volunteer";
        //get random volunteer
        String callNum = channels.get(new Random().nextInt(channels.size()));
        Log.d("logd_random_res",callNum);
        if(isInternetAvailable())
            dispatchCall(callNum);
        else
            Toast.makeText(getApplicationContext(), "You are not online", Toast.LENGTH_SHORT).show();
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
        final String callNumStdBy = callNum+ Constants.STDBY_SUFFIX;
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
                            intent.putExtra(ROLE,"BLIND");
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


    private void getOnlineUsersCount(){
        mPubNub.hereNow(true, false, new Callback() {
            @Override
            public void successCallback(String channel, Object message) {
                Log.d("lolo","HERE NOW : " + message);
                if (!(message instanceof JSONObject)) return; // Ignore if not JSONObject
                JSONObject jsonMsg = (JSONObject) message;
                try {
                    JSONObject users = jsonMsg.getJSONObject("channels");
                    Iterator<String> keys=users.keys();
                    while(keys.hasNext())
                    {
                        String key=keys.next();
                        Log.d("keys",key);
                        //String value=users.getString(key);
                    }

                } catch (JSONException e) {
                    e.printStackTrace();
                }
                try {
                    final String users_online = jsonMsg.getString("total_occupancy");
                    Log.d("onlinne", users_online);
                    Thread timer = new Thread(){
                        public void run(){
                            runOnUiThread(new Runnable() {

                                @Override
                                public void run() {
                                    view.setText("Online: "+users_online);
                                }
                            });
                        }
                    };
                    timer.start();

                } catch (JSONException e){
                    e.printStackTrace();
                }
            }

            @Override
            public void errorCallback(String channel, PubnubError error) {
                Log.d("lolod","HERE NOW : " + error);
            }
        });
    }

    private void getOnlineUsers(){
        mPubNub.hereNow(true, false, new Callback() {
            @Override
            public void successCallback(String channel, Object message) {
                Log.d("lolo","HERE NOW : " + message);
                if (!(message instanceof JSONObject)) return; // Ignore if not JSONObject
                JSONObject jsonMsg = (JSONObject) message;
                try {
                    JSONObject users = jsonMsg.getJSONObject("channels");
                    Iterator<String> keys=users.keys();
                    while(keys.hasNext())
                    {
                        String key=keys.next();
                        String s2 = key.substring(0,key.length()-6);


                        Log.d("keys",s2);
                        channels.add(s2);
                        //user=key;
                        //mutableMessage.setValue(key);
                    }
                } catch (JSONException e) {
                    Log.d("err",e.toString());
                }
            }
            @Override
            public void errorCallback(String channel, PubnubError error) {
                Log.d("lolod","HERE NOW : " + error);
            }
        });
    }

    public boolean isInternetAvailable() {
        ConnectivityManager cm =
                (ConnectivityManager)getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        boolean isConnected = activeNetwork != null &&
                activeNetwork.isConnectedOrConnecting();
        return isConnected;

    }

}
