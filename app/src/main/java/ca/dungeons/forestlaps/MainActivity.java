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
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.time.Duration;
import java.time.Instant;

public class MainActivity extends AppCompatActivity implements LocationListener  {

    public Location currentLocation;
    public Location startLocation;

    public Button btnStart;
    public TextView tvLocation;
    public TextView tvDistance;
    public EditText etTiming;
    public TextView tvTiming;
    public TextView tvAccuracy;

    // Tracks if we've left the starting area and we're timing this thing
    public boolean isRunning = false;
    public Integer lapCounter = 0;
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
                Double startLat = startLocation.getLatitude();
                Double startLong = startLocation.getLongitude();
                String locationText = getString(R.string.start_location) + ": " + startLat.toString() + "," + startLong.toString();
                tvLocation.setText(locationText);
                btnStart.setText(R.string.restart);
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

        Float accuracy = currentLocation.getAccuracy();
        Float accuracyRound = Math.round(accuracy * 100.0) / (float) 100.0;
        tvAccuracy.setText(accuracyRound.toString() + "m accuracy");

        if(!btnStart.isEnabled()) {
            btnStart.setEnabled(true);
            tvLocation.setText(R.string.start_ready);
        }

        if(startLocation != null) {
            Float distance = startLocation.distanceTo(currentLocation);
            distance = Math.round(distance * 100.0 ) / (float) 100.0;
            tvDistance.setText(getString(R.string.distance_to_start) + ": " + distance.toString() + "m");
            // You are outside of the start area if your distance is > measured gps accuracy + 1m
            if(distance > accuracy + 1) {
                // And we're off!
                if(!isRunning)
                {
                    isRunning = true;
                    startTime = Instant.now();
                } else {
                    currentTime = Instant.now();
                    Duration elapsedTime = Duration.between(startTime, currentTime);
                    Double secondsTaken = (double) elapsedTime.toMillis() / 1000.0;
                    Double roundedSeconds = Math.round(secondsTaken * 100) / 100.0;
                    tvTiming.setText("Lap time: " + roundedSeconds.toString() + "s");
                }
            } else {
                // And we're back!
                if(isRunning) {
                    isRunning = false;
                    endTime = Instant.now();
                    Duration elapsedTime = Duration.between(startTime, endTime);
                    lapCounter++;
                    Double secondsTaken = (double) elapsedTime.toMillis() / 1000.0;
                    Double roundedSeconds = Math.round(secondsTaken * 100) / 100.0;
                    etTiming.append("Lap " + lapCounter + ": " + roundedSeconds.toString() + "s\n");
                    tvTiming.setText("Last lap: " + roundedSeconds.toString() + "s");
                }
            }
        }
    }


}