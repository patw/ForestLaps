package ca.dungeons.forestlaps;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements LocationListener  {

    public Location currentLocation;
    public Location startLocation;

    public Button btnStart;
    public TextView tvLocation;
    public TextView tvDistance;
    public EditText etTiming;
    public TextView tvTiming;
    public TextView tvAccuracy;
    public TextToSpeech tts;

    // Tracks if we've left the starting area and we're timing this thing
    public boolean isRunning = false;
    public Integer lapCounter = 0;
    public int locationReadings = 0;
    public float speedTotal = 0.0f;
    public float lapTopSpeed = 0.0f;
    public float avgLapSpeed = 0.0f;
    Instant startTime;
    Instant endTime;
    Instant currentTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Prevent screen from going into landscape or sleeping
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Get controls wired up
        tvLocation = (TextView) findViewById(R.id.tvLocation);
        btnStart = (Button) findViewById(R.id.btnStart);
        tvDistance = (TextView) findViewById(R.id.tvDistance);
        etTiming = (EditText) findViewById(R.id.etTiming);
        tvTiming = (TextView) findViewById(R.id.tvTiming);
        tvAccuracy = (TextView) findViewById(R.id.tvAccuracy);

        // Click a button, get some sensor data
        btnStart.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Capture the current location and accuracy so we can build our circular area
                startLocation = currentLocation;
                double startLat = startLocation.getLatitude();
                double startLong = startLocation.getLongitude();
                String locationText = getString(R.string.start_location) + ": " + Double.toString(startLat) + "," + Double.toString(startLong);
                tvLocation.setText(locationText);
                btnStart.setText(R.string.restart);
                isRunning = false;
            }
        });

        // Create TTS output listener
        tts = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if(status != TextToSpeech.ERROR) {
                    tts.setLanguage(Locale.CANADA);
                    tts.setSpeechRate(0.85f);  // default sounds too fast
                    tts.setPitch(0.85f); // sounds too .. squirrel at 1.0
                }
            }
        });

        // Request permissions for GPS if we don't have it otherwise start the location gathering
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        } else {
            LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 250, 0, this);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        // I don't care what you picked for privacy, we'll just restart until you say yes to gps :)
        this.recreate();
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        currentLocation = location;

        float accuracy = currentLocation.getAccuracy();
        float accuracyRound = Math.round(accuracy * 100.0) / (float) 100.0;
        tvAccuracy.setText(Float.toString(accuracyRound) + "m accuracy");

        if(!btnStart.isEnabled()) {
            btnStart.setEnabled(true);
            tvLocation.setText(R.string.start_ready);
        }

        // We have a start location bubble
        if(startLocation != null) {

            float distance = startLocation.distanceTo(currentLocation);
            distance = Math.round(distance * 100.0 ) / (float) 100.0;
            tvDistance.setText(getString(R.string.distance_to_start) + ": " + Float.toString(distance) + "m");
            // You are outside of the start area if your distance is > measured gps accuracy + 1.5m
            if(distance > accuracy + 1.5) {
                // And we're off!
                if(!isRunning)
                {
                    isRunning = true;
                    startTime = Instant.now();
                } else {
                    currentTime = Instant.now();
                    Duration elapsedTime = Duration.between(startTime, currentTime);
                    double secondsTaken = (double) elapsedTime.toMillis() / 1000.0;
                    double roundedSeconds = Math.round(secondsTaken * 100) / 100.0;
                    String lastLapText = "Current lap: " + Double.toString(roundedSeconds) + "s";
                    tvTiming.setText(lastLapText);

                    // Get the top speed and average speed for the lap
                    if(location.hasSpeed()) {
                        locationReadings++;

                        // Quick conversion from m/s to km/hr
                        float kmhSpeed = location.getSpeed() * 3.6f;

                        speedTotal += kmhSpeed;
                        avgLapSpeed = speedTotal / locationReadings;

                        if(kmhSpeed > lapTopSpeed) {
                            lapTopSpeed = kmhSpeed;
                        }
                    }
                }
            } else {
               // We're back in the location bubble (maybe!)
                if(isRunning) {
                    endTime = Instant.now();
                    Duration elapsedTime = Duration.between(startTime, endTime);
                    double secondsTaken = (double) elapsedTime.toMillis() / 1000.0;
                    double roundedSeconds = Math.round(secondsTaken * 100) / 100.0;

                    // Quick/dirty bubble debounce (sorry!)
                    // Any timing < 15s is invalid
                    if(roundedSeconds >= 15.0f) {
                        isRunning = false;
                        lapCounter++;

                        double roundedTopSpeed = Math.round(lapTopSpeed * 100) / 100.0;
                        double roundedAvgSpeed = Math.round(avgLapSpeed * 100) / 100.0;

                        // Adds the lap data to the text box
                        String lapTextBox = "Lap " + lapCounter + ": " +
                                Double.toString(roundedSeconds) + "s " +
                                Double.toString(roundedAvgSpeed) + " avg spd " +
                                Double.toString(roundedTopSpeed) + " top spd\n";
                        etTiming.append(lapTextBox);

                        // Updates the UI showing last lap data
                        String lastLapText = "Last lap: " + Double.toString(roundedSeconds) + "s";
                        tvTiming.setText(lastLapText);

                        // Text to Speech for Lap timing
                        String lapSpeech = "Lap " + lapCounter + " was " +
                                Double.toString(roundedSeconds) + " seconds long at a " +
                                Double.toString(roundedAvgSpeed) + " average speed and a " +
                                Double.toString(roundedTopSpeed) + " top speed";
                        tts.speak(lapSpeech, TextToSpeech.QUEUE_FLUSH, null);

                        // Reset lap top/avg speed
                        lapTopSpeed = 0.0f;
                        avgLapSpeed = 0.0f;
                        speedTotal = 0.0f;
                        locationReadings = 0;
                    }
                }
            }
        }
    }


}