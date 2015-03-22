package com.orbotix.sample.buttondrive;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.TextView;

import com.getpebble.android.kit.PebbleKit;
import com.getpebble.android.kit.PebbleKit.PebbleDataReceiver;
import com.getpebble.android.kit.util.PebbleDictionary;

import java.util.Random;
import java.util.UUID;

import orbotix.robot.base.BackLEDOutputCommand;
import orbotix.robot.base.Robot;
import orbotix.sphero.ConnectionListener;
import orbotix.sphero.Sphero;
import orbotix.view.connection.SpheroConnectionView;

/** Activity for controlling the Sphero with five control buttons. */
public class ButtonDriveActivity extends Activity {

    Random rand = new Random();

    //Constants
    public static final String TAG = ButtonDriveActivity.class.getName();
    private static final int NUM_SAMPLES = 15;

    //State
    private int sampleCount = 0;
    private long lastAverageTime = 0;
    private int[] latest_data;
    private int totalData = 0;

    //Layout members
    private TextView
            xView,
            yView,
            zView,
            rView,
            rateView;

    //Other members
    private PebbleDataReceiver receiver;
    private UUID uuid = UUID.fromString("3a6df6f0-4138-4ac2-9519-8b72fd24a0ba");
    private Handler handler = new Handler();

    private Sphero mRobot;
    // Set speed. 60% of full speed
    float speed = 0.6f;
    float heading = 0f;

    /**
     * The Sphero Connection View
     */
    private SpheroConnectionView mSpheroConnectionView;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        mSpheroConnectionView = (SpheroConnectionView) findViewById(R.id.sphero_connection_view);
        mSpheroConnectionView.addConnectionListener(new ConnectionListener() {

            @Override
            public void onConnected(Robot robot) {
                //SpheroConnectionView is made invisible on connect by default
                mRobot = (Sphero) robot;
            }

            @Override
            public void onConnectionFailed(Robot sphero) {
                // let the SpheroConnectionView handle or hide it and do something here...
            }

            @Override
            public void onDisconnected(Robot sphero) {
                mSpheroConnectionView.startDiscovery();
            }
        });

        xView = (TextView) findViewById(R.id.x_view);
        yView = (TextView) findViewById(R.id.y_view);
        zView = (TextView) findViewById(R.id.z_view);
        rView = (TextView) findViewById(R.id.r_view);
        rateView = (TextView) findViewById(R.id.rate_view);

        logic();

    }


    public void logic() {

        mSpheroConnectionView.startDiscovery();

        receiver = new PebbleDataReceiver(uuid) {

            @Override
            public void receiveData(Context context, int transactionId, PebbleDictionary data) {
                PebbleKit.sendAckToPebble(getApplicationContext(), transactionId);

                //Count total data
                totalData += 3 * NUM_SAMPLES * 4;

                //Get data
                latest_data = new int[3 * NUM_SAMPLES];
//				Log.d(TAG, "NEW DATA PACKET");
                for (int i = 0; i < NUM_SAMPLES; i++) {
                    for (int j = 0; j < 3; j++) {
                        try {
                            latest_data[(3 * i) + j] = data.getInteger((3 * i) + j).intValue();
                        } catch (Exception e) {
                            latest_data[(3 * i) + j] = -1;
                        }
                    }
//					Log.d(TAG, "Sample " + i + " data: X: " + latest_data[(3 * i)] + ", Y: " + latest_data[(3 * i) + 1] + ", Z: " + latest_data[(3 * i) + 2]);
                }

                //Show
                handler.post(new Runnable() {

                    @Override
                    public void run() {
                        xView.setText("X: " + latest_data[0]);
                        yView.setText("Y: " + latest_data[1]);
                        zView.setText("Z: " + latest_data[2]);
                        rView.setText("H: " + heading);

                        if (latest_data[1] > -250 && latest_data[1] < 250) {
                            mRobot.stop();
                        } else if (latest_data[1] >= 200 && latest_data[1] < 500) {
                            mRobot.drive(heading, speed * 0.2f);
                        } else if (latest_data[1] >= 500 && latest_data[1] < 800) {
                            mRobot.drive(heading, speed * 0.5f);
                        } else if (latest_data[1] >= 800) {
                            mRobot.drive(heading, speed);
                        } else if (latest_data[1] <= -200) {
                            mRobot.drive((heading + 180f) % 360f, speed * 0.6f);
                        }


                        if ( latest_data[0] < 400 && latest_data[0] > 100 ){
                            heading += 2f;
                        }


                        else if ( latest_data[0] > -500 && latest_data[0] < -100 ){
                            heading -= 2f;
                        }



                        else if (latest_data[0] < -500 && latest_data[0] > -800) {

                            heading -= 5f;

                            if (heading < 0) heading += 360;
                            mRobot.drive(heading, speed * 0.5f);

                        } else if (latest_data[0] <= -800) {

                            heading -= 5f;

                            if (heading < 0) heading += 360;
                                mRobot.drive(heading, speed * 0.8f);

                        } else if (latest_data[0] > 400 && latest_data[0] < 700) {

                            heading += 5f;

                            if (heading > 360) heading -= 360;
                                mRobot.drive(heading, speed * 0.5f);

                        } else if (latest_data[0] >= 700) {

                            heading += 5f;

                            if (heading > 360) heading -= 360;
                            mRobot.drive(heading, speed * 0.8f);

                        }
                    }


                });


                if (System.currentTimeMillis() - lastAverageTime > 1000) {
                    lastAverageTime = System.currentTimeMillis();

                    rateView.setText("" + sampleCount + " samples per second."
                            + "\n"
                            + data.size() + " * 4-btye int * " + sampleCount + " samples = " + (4 * data.size() * sampleCount) + " Bps."
                            + "\n"
                            + "Total data received: " + getTotalDataString());
                    sampleCount = 0;
                } else {
                    sampleCount++;
                }
            }

        };

        PebbleKit.registerReceivedDataHandler(this, receiver);


    }

    /**
     * Called when the user comes back to this app
     */
    @Override
    protected void onResume() {
        super.onResume();
        // Refresh list of Spheros

    }


    /**
     * Called when the user presses the back or home button
     */
    @Override
    protected void onPause() {
        super.onPause();

    }

    private String getTotalDataString() {
        if (totalData < 1000) {
            return "" + totalData + " Bytes.";
        } else if (totalData > 1000 && totalData < 1000000) {
            return "" + totalData / 1000 + " KBytes.";
        } else {
            return "" + totalData / 1000000 + " MBytes.";
        }
    }

    /**
     * When the user clicks "STOP", stop the Robot.
     *
     * @param v The View that had been clicked
     */



    /**
     * When the user clicks a control button, roll the Robot in that direction
     */
    boolean firstClick = true;



    public void onStopClick(View v) {
        if (mRobot != null) {
            // Stop robot
            mRobot.stop();
        }
    }


    public void onStartClick(View v) {
        PebbleDictionary dict = new PebbleDictionary();
        dict.addInt32(0, 0);
        PebbleKit.sendDataToPebble(getApplicationContext(), uuid, dict);
    }



    public void onControlClick(View v) {
        // Find the heading, based on which button was clicked

        System.out.println("speed: " + speed);

        switch (v.getId()) {

            case R.id.add_speed_button:

                if(firstClick) {
                    mRobot.drive(0f, 0.5f);
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                    mRobot.stop();
                }
                firstClick = false;

                BackLEDOutputCommand.sendCommand(mRobot, 1.0f);
                mRobot.startCalibration();
                mRobot.rotate(5f);
                mRobot.stopCalibration(true);

                break;

            case R.id.decrease_speed_button:
                break;

            case R.id.color_button:
                mRobot.setColor(rand.nextInt(255),rand.nextInt(255),rand.nextInt(255));
                break;

            default:
                break;
        }

    }
}