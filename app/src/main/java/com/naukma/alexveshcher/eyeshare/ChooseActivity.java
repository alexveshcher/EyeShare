package com.naukma.alexveshcher.eyeshare;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
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
    }

    /**When user clicks 'I can help' */
    public void volunteer(View view){
        if(isInternetAvailable()){
            Intent intent = new Intent(this, LoginActivity.class);
            startActivity(intent);
            finish();
        }
        else Toast.makeText(getApplicationContext(), "You are not online", Toast.LENGTH_SHORT).show();
    }

    public void blind(View view) {
        //get random volunteer
        if(channels.size()>0){
            String callNum = channels.get(new Random().nextInt(channels.size()));
            //Log.d("logd_random_res",callNum);
            if(isInternetAvailable())
                dispatchCall(callNum);
            else
                Toast.makeText(getApplicationContext(), "You are not online", Toast.LENGTH_SHORT).show();
        }
        else showToast("No users online");
    }


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
                        //dispatchIncomingCall(user);
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
                    final String users_online = jsonMsg.getString("total_channels");
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
                    while(keys.hasNext()) {
                        String key=keys.next();
                        if(key.length()>6 && !key.equals("blind-stdby") && !key.equals("volunteer-stdby")&& !key.equals("volunteer")) {
                            String s2 = key.substring(0, key.length() - 6);
                            Log.d("keys",s2);
                            channels.add(s2);
                        }
                    }
                } catch (Exception e) {
                    Log.d("err",e.toString());
                }
            }
            @Override
            public void errorCallback(String channel, PubnubError error) {
                Log.d("lolod","HERE NOW : " + error);
            }
        });
    }

    //shows connection to the Internet
    public boolean isInternetAvailable() {
        ConnectivityManager cm =
                (ConnectivityManager)getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        boolean isConnected = activeNetwork != null &&
                activeNetwork.isConnectedOrConnecting();
        return isConnected;
    }
}
