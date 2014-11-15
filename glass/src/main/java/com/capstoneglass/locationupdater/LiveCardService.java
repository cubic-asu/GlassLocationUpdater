package com.capstoneglass.locationupdater;

import com.google.android.glass.timeline.LiveCard;
import com.google.android.glass.timeline.LiveCard.PublishMode;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.widget.RemoteViews;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLEncoder;

import javax.net.ssl.HttpsURLConnection;

/*
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.plus.*;
import com.google.android.gms.plus.model.people.Person;
import com.google.android.gms.plus.PlusClient;
//*/
public class LiveCardService extends Service {

    private static final String LIVE_CARD_TAG = "LiveCardService";
    private LiveCard mLiveCard;

    private RemoteViews mLiveCardView;

    private final Handler mHandler = new Handler();
    private final UpdateLiveCardRunnable mUpdateLiveCardRunnable =
            new UpdateLiveCardRunnable();
    private static final long DELAY_MILLIS = 10000;
    private LocationManager locationManager;
    private Location mlocation;
    private AccountManager mAcctMgr;
    private String emailAddress;
    private final boolean DO_DEBUG = true;
    @Override
    public void onCreate() {
        super.onCreate();
    }


    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (mLiveCard == null) {
            //Instantiate Location manager
            locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
            Criteria criteria = new Criteria();
            String provider = locationManager.getBestProvider(criteria, true);
            LocationListener locationListener = new LocationListener()
            {
                public void onLocationChanged(Location location) {
                    // Called when a new location is found by the network location provider.
                    mlocation = location;
                }
                public void onStatusChanged(String provider, int status, Bundle extras) {}

                public void onProviderEnabled(String provider) {}

                public void onProviderDisabled(String provider) {}
            };

            //Subscribe to location updates
            //locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, DELAY_MILLIS, 1, locationListener);
            locationManager.requestLocationUpdates(provider, 0, 0, locationListener);
            //locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
            mlocation = locationManager.getLastKnownLocation(provider);
            //if (mlocation == null)
                //mlocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);

            if (mlocation == null)
                System.out.println("Location is null. ");




            // Publish the live card
            // Get an instance of a live card
            mLiveCard = new LiveCard(this, LIVE_CARD_TAG);

            // Inflate a layout into a remote view
            mLiveCardView = new RemoteViews(getPackageName(),
                    com.capstoneglass.locationupdater.R.layout.live_card);
            // Set up the live card's action with a pending intent
            // to show a menu when tapped
            Intent menuIntent = new Intent(this, LiveCardMenuActivity.class);
            menuIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                    Intent.FLAG_ACTIVITY_CLEAR_TASK);
            mLiveCard.setAction(PendingIntent.getActivity(
                    this, 0, menuIntent, 0));

            mLiveCard.setViews(mLiveCardView);
            mLiveCard.publish(PublishMode.REVEAL);

            //Retrieve the email address from Glass device
            mAcctMgr = AccountManager.get(this.getBaseContext());
            Account[] accounts = mAcctMgr.getAccountsByType("com.google");

            emailAddress = "unknown@email.com";
            if (accounts.length > 0 && accounts[0] != null &&  accounts[0].name != null && !accounts[0].name.equals(""))
            {
                emailAddress = accounts[0].name;
            }

            //Testing purposes only.
            //emailAddress = "116951401410984780665";


            /*

            Account[] testaccounts = mAcctMgr.getAccounts();
            for (Account a : testaccounts)
            {
                System.out.println(a.toString());
            }
            //*/



/*
            //testing retrieving the google+ id
            // This sample assumes a client object has been created.
// To learn more about creating a client, check out the starter:
//  https://developers.google.com/+/quickstart/java

            Plus plus = new Plus.Builder()
            Person mePerson = plus.people().get("me").execute();

            System.out.println("ID:\t" + mePerson.getId());
            System.out.println("Display Name:\t" + mePerson.getDisplayName());
            System.out.println("Image URL:\t" + mePerson.getImage().getUrl());
            System.out.println("Profile URL:\t" + mePerson.getUrl());

//*/
            // Queue the update text runnable
            mHandler.post(mUpdateLiveCardRunnable);
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (mLiveCard != null && mLiveCard.isPublished()) {
            //Stop the handler from queuing more Runnable jobs
            mUpdateLiveCardRunnable.setStop(true);

            mLiveCard.unpublish();
            mLiveCard = null;
        }
        super.onDestroy();
    }

    /*
    private GoogleApiClient buildGoogleApiClient() {
        // When we build the GoogleApiClient we specify where connected and
        // connection failed callbacks should be returned, which Google APIs our
        // app uses and which OAuth 2.0 scopes our app requests.
        return new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Plus.API, Plus.PlusOptions.builder().build())
                .addScope(Plus.SCOPE_PLUS_LOGIN)
                .build();
    }
    //*/

    private class UpdateLiveCardRunnable implements Runnable
    {
        private boolean mIsStopped = false;
        private final String USER_AGENT = "Mozilla/5.0";
        private final String CHARSET = "UTF-8";
        private final String UPDATE_URL = "https://capstoneglassapi.appspot.com/update";

        private String latitude;
        private String longitude;


        /*
         * Sends Location updates to capstoneglassapi.appspot.com/update
         */
        public void run(){
            if(!isStopped()){

                new Thread()
                {
                    public void run() {
                        try {
                            sendLocationUpdate();
                        }
                        catch (Exception e) {
                            System.out.println(e);
                            //e.printStackTrace();
                        }

                    }
                }.start();

                // Queue another score update in DELAY seconds.
                mHandler.postDelayed(mUpdateLiveCardRunnable, DELAY_MILLIS);
            }
        }

        public boolean isStopped() {
            return mIsStopped;
        }

        public void setStop(boolean isStopped) {
            this.mIsStopped = isStopped;
        }

        // HTTP POST request
        private void sendLocationUpdate() throws Exception
        {
            URL obj = new URL(UPDATE_URL);
            HttpsURLConnection con = (HttpsURLConnection) obj.openConnection();

            //add reuqest header
            con.setRequestMethod("POST");
            con.setRequestProperty("User-Agent", USER_AGENT);
            con.setRequestProperty("Accept-Language", "en-US,en;q=0.5");

            latitude = String.valueOf(mlocation.getLatitude());
            longitude = String.valueOf(mlocation.getLongitude());

            String query = String.format("lat=%s&long=%s&email=%s",
                    URLEncoder.encode(latitude, CHARSET),
                    URLEncoder.encode(longitude, CHARSET),
                    URLEncoder.encode(emailAddress, CHARSET));

            // Send post request
            con.setDoOutput(true);
            DataOutputStream wr = new DataOutputStream(con.getOutputStream());
            wr.writeBytes(query);
            wr.flush();
            wr.close();

            if (DO_DEBUG) {
                int responseCode = con.getResponseCode();

                System.out.println("\nSending 'POST' request to URL : " + UPDATE_URL);
                System.out.println("Post parameters : " + query);
                System.out.println("Response Code : " + responseCode);

                BufferedReader in = new BufferedReader(
                        new InputStreamReader(con.getInputStream()));
                String inputLine;
                StringBuffer response = new StringBuffer();

                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                //print result
                System.out.println(response.toString());
            }

            con.disconnect();
        }
    }
}