package com.orbotix.sample.buttondrive;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.getpebble.android.kit.PebbleKit;
import com.getpebble.android.kit.PebbleKit.PebbleDataReceiver;
import com.getpebble.android.kit.util.PebbleDictionary;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.GraphView.GraphViewData;
import com.jjoe64.graphview.GraphViewSeries;
import com.jjoe64.graphview.GraphViewSeries.GraphViewSeriesStyle;
import com.jjoe64.graphview.LineGraphView;

import java.util.UUID;

import orbotix.robot.base.BackLEDOutputCommand;
import orbotix.robot.base.Robot;
import orbotix.robot.base.RobotProvider;
import orbotix.sphero.ConnectionListener;
import orbotix.sphero.Sphero;
import orbotix.view.connection.SpheroConnectionView;

/** Activity for controlling the Sphero with five control buttons. */
public class ButtonDriveActivity extends Activity {

    //Constants
    public static final String TAG = ButtonDriveActivity.class.getName();
    private static final int NUM_SAMPLES = 15;
    private static final int GRAPH_HISTORY = 200;

    //State
    private int sampleCount = 0;
    private long lastAverageTime = 0;
    private int[] latest_data;
    private GraphViewSeries seriesX, seriesY, seriesZ;
    private int sampleCounter = 0;
    private int totalData = 0;

    //Layout members
    private TextView
            xView,
            yView,
            zView,
            rateView;
    private Button startButton;
    private GraphView gView;

    //Other members
    private PebbleDataReceiver receiver;
    private UUID uuid = UUID.fromString("3a6df6f0-4138-4ac2-9519-8b72fd24a0ba");
    private Handler handler = new Handler();

    private Sphero mRobot;
    // Set speed. 60% of full speed
    float speed = 0.6f;
    float heading = 0f;

    /** The Sphero Connection View */
    private SpheroConnectionView mSpheroConnectionView;

    /** Called when the activity is first created. */
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

        xView = (TextView)findViewById(R.id.x_view);
        yView = (TextView)findViewById(R.id.y_view);
        zView = (TextView)findViewById(R.id.z_view);
        rateView = (TextView)findViewById(R.id.rate_view);
        startButton = (Button)findViewById(R.id.start_button);

        startButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                PebbleDictionary dict = new PebbleDictionary();
                dict.addInt32(0, 0);
                PebbleKit.sendDataToPebble(getApplicationContext(), uuid, dict);
            }

        });

        //Graph
        seriesX = new GraphViewSeries("X", new GraphViewSeriesStyle(Color.argb(255, 255, 0, 0), 2), new GraphViewData[] {
                new GraphViewData(1, 0)
        });
        seriesY = new GraphViewSeries("Y", new GraphViewSeriesStyle(Color.argb(255, 0, 255, 0), 2), new GraphViewData[] {
                new GraphViewData(1, 0)
        });
        seriesZ = new GraphViewSeries("Z", new GraphViewSeriesStyle(Color.argb(255, 0, 0, 255), 2), new GraphViewData[] {
                new GraphViewData(1, 0)
        });

        gView = new LineGraphView(this, "Pebble Accelerometer History");
        gView.setShowLegend(true);
        gView.setViewPort(0, GRAPH_HISTORY);
        gView.setScrollable(true);
        gView.addSeries(seriesX);
        gView.addSeries(seriesY);
        gView.addSeries(seriesZ);



    }


    /** Called when the user comes back to this app */
    @Override
    protected void onResume() {
        super.onResume();
        // Refresh list of Spheros
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
                for(int i = 0; i < NUM_SAMPLES; i++) {
                    for(int j = 0; j < 3; j++) {
                        try {
                            latest_data[(3 * i) + j] = data.getInteger((3 * i) + j).intValue();
                        } catch(Exception e) {
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

                        if(latest_data[1] > -250 && latest_data[1] < 250) {
                            mRobot.stop();
                        } else if(latest_data[1] >= 200 && latest_data[1] < 500) {
                            mRobot.drive(heading, speed * 0.2f);
                        } else if(latest_data[1] >= 500 && latest_data[1] < 800) {
                            mRobot.drive(heading, speed * 0.5f);
                        } else if(latest_data[1] >= 800) {
                            mRobot.drive(heading, speed);
                        } else if(latest_data[1] <= -200) {
                            mRobot.drive((heading + 180f) % 360f, speed * 0.6f);
                        }

                        if(latest_data[0] < -500 && latest_data[0] > -800) {
                            heading -= 5f;
                            if (heading < 0) heading += 360;

                            mRobot.drive(heading, speed * 0.5f);
                        } else if(latest_data[0] <= -800) {
                            heading -= 5f;
                            if (heading < 0) heading += 360;

                            mRobot.drive(heading, speed * 0.8f);
                        } else if(latest_data[0] > 400 && latest_data[0] < 700) {
                            heading += 5f;
                            if(heading > 360) heading -= 360;

                            mRobot.drive(heading, speed * 0.5f);
                        } else if(latest_data[0] >= 700) {
                            heading += 5f;
                            if(heading > 360) heading -= 360;

                            mRobot.drive(heading, speed * 0.8f);
                        }
                    }


                });

                //Show on graph
                for(int i = 0; i < NUM_SAMPLES; i++) {
                    seriesX.appendData(new GraphViewData(sampleCounter, latest_data[(3 * i)]), true, GRAPH_HISTORY);
                    seriesY.appendData(new GraphViewData(sampleCounter, latest_data[(3 * i) + 1]), true, GRAPH_HISTORY);
                    seriesZ.appendData(new GraphViewData(sampleCounter, latest_data[(3 * i) + 2]), true, GRAPH_HISTORY);
                    sampleCounter++;
                }

                if(System.currentTimeMillis() - lastAverageTime > 1000) {
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


    /** Called when the user presses the back or home button */
    @Override
    protected void onPause() {
        super.onPause();
        // Disconnect Robot properly
        RobotProvider.getDefaultProvider().disconnectControlledRobots();

        unregisterReceiver(receiver);
    }

    private String getTotalDataString() {
        if(totalData < 1000) {
            return "" + totalData + " Bytes.";
        } else if(totalData > 1000 && totalData < 1000000) {
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
    public void onStopClick(View v) {
        if (mRobot != null) {
            // Stop robot
            mRobot.stop();
        }
    }

    /**
     * When the user clicks a control button, roll the Robot in that direction
     */

    public void onControlClick(View v) {
        // Find the heading, based on which button was clicked
        boolean changeSpeed = false;
        System.out.println("speed: " + speed);


        switch (v.getId()) {

            case R.id.add_speed_button:
                changeSpeed = true;

                mRobot.startCalibration();
                mRobot.rotate(5f);
                mRobot.stopCalibration(true);
                BackLEDOutputCommand.sendCommand(mRobot, 1.0f);

                break;

            case R.id.decrease_speed_button:
                changeSpeed = true;

                mRobot.startCalibration();
                mRobot.rotate(-5f);
                mRobot.stopCalibration(true);
                BackLEDOutputCommand.sendCommand(mRobot, 1.0f);

                break;

            case R.id.forty_five_button:
                heading = 45f;
                changeSpeed = false;
                break;

            case R.id.ninety_button:
                heading = 90f;
                changeSpeed = false;
                break;

            case R.id.one_thirty_five_button:
                heading = 135f;
                changeSpeed = false;
                break;

            case R.id.one_eighty_button:
                heading = 180f;
                changeSpeed = false;
                break;

            case R.id.two_twenty_five_button:
                heading = 225f;
                changeSpeed = false;
                break;

            case R.id.two_seventy_button:
                heading = 270f;
                changeSpeed = false;
                break;

            case R.id.three_fifteen_button:
                heading = 315f;
                changeSpeed = false;
                break;

            default:
                heading = 0f;
                changeSpeed = false;
                break;
        }

        // Roll robot
        if(changeSpeed) {
            mRobot.drive(heading, 0f);
        } else {
            mRobot.drive(heading, speed);
        }
    }
}