package com.example.flightcapture;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.Toast;
import android.app.Activity;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;

public class MainActivity extends Activity 
		implements SurfaceHolder.Callback, Camera.ShutterCallback, Camera.PictureCallback {

	private static final String TAG = "MyActivity";
		
	public static final int MEDIA_TYPE_IMAGE = 1;
	public static final int MEDIA_TYPE_VIDEO = 2;

	private static String storeDir = "FlightCapture";
	private static boolean CAMERA_READY = true;
	private static boolean STREAM_CAPTURE = false;
	private static int TOTAL_FRAMES = 10;			//Default number of frames to snap
	private static int TOTAL_COUNT = 0;
	private static final int TIME_BETWEEN_GPS = 0;
	
	// Time Mode
	private static float TIME_TO_TRAVEL = 3000;		//Time in milliseconds
	
	// Distance Mode
	public static boolean DISTANCE = false;
	public static float DISTANCE_TO_TRAVEL = 2;	//Distance in meters
	public static boolean FIRST_DISTANCE = false;
	
	Camera mCamera;
    SurfaceView mSurfaceView;
    SurfaceHolder mHolder;
    
    LocationManager locationManager;
    Location currentLocation, previousLocation;


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
        // Install a SurfaceHolder.Callback so we get notified when the
        // underlying surface is created and destroyed.
		mSurfaceView = (SurfaceView)findViewById(R.id.preview);
        mHolder = mSurfaceView.getHolder();
        mHolder.addCallback(this);
		
        if (checkCameraHardware(this))
        {
        	safeCameraOpen(false);
        }
        
        locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

        // For now I can use the keep screen on flag and leave in portrait to keep the app working
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);		// Band-aid
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);	// Band-aid


	}


	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.activity_main, menu);
		return true;
	}
		
	@Override
	public void onStart()
	{
		super.onStart();
	}
	
	@Override
	public void onResume()
	{
		super.onResume();
		safeCameraOpen(false);
		safeLocationStart();
	}
	
	@Override
	public void onPause()
	{
		super.onPause();
		stopPreviewAndFreeCamera();
		locationManager.removeUpdates(listener);
	}
	
	@Override
	public void onStop()
	{
		super.onStop();
	}
	
	@Override
	public void onDestroy()
	{
		super.onDestroy();
		stopPreviewAndFreeCamera();
		locationManager.removeUpdates(listener);
	}
	
	private boolean safeCameraOpen(boolean qOpened) {
	  
	    try {
	        releaseCameraAndPreview();
	        mCamera = Camera.open();		// Override 'id' and set to first back facing camera
	        qOpened = (mCamera != null);
	    } catch (Exception e) {
	        Log.e(getString(R.string.app_name), "failed to open Camera");
	        e.printStackTrace();
	    }

	    return qOpened;    
	}
	
	private void releaseCameraAndPreview() {

	    if (mCamera != null) {
	        mCamera.release();
	        mCamera = null;
	    }
	}
	
	/**
	  * When this function returns, mCamera will be null.
	  */
	private void stopPreviewAndFreeCamera() {
	    if (mCamera != null) {
	        mCamera.stopPreview();	// Call stopPreview() to stop updating the preview surface.
	        mCamera.release();		// To release the camera for use by other apps
	        mCamera = null;
	    }
	}
	
	public void onCancelClick(View v)
	{
		finish();
	}
	
	public void onSnapClick(View v)
	{
		//Snap a single photo
		STREAM_CAPTURE = false;
		DISTANCE = false;
		FIRST_DISTANCE = false;
		storeDir = "SingleShot";
		mCamera.takePicture(this, null, null, this);
	}
	
	public void onStartTimeClick(View v)
	{
		//Snap a stream of photos based on a time interval
		STREAM_CAPTURE = true;
		DISTANCE = false;
		FIRST_DISTANCE = false;
		TOTAL_COUNT = 0;
		CAMERA_READY = true;
		String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
		storeDir = "TimePics_" + timeStamp;
		try 
		{
			EditText readTime = (EditText) findViewById(R.id.editTime);
			if (readTime != null)
				TIME_TO_TRAVEL = Float.parseFloat(readTime.getText().toString());
			EditText readFrames = (EditText) findViewById(R.id.editFrames);
			if (readFrames != null)
				TOTAL_FRAMES = Integer.parseInt(readFrames.getText().toString());
		} catch (NumberFormatException nfe) {
			// Catch and continue with defaults
		}
		Toast.makeText(getBaseContext(), "Time Mode", Toast.LENGTH_SHORT).show();
		continuousCapture();
	}
	
	public void onStartDistClick(View v)
	{
		//Snap a stream of photos based on distance between snaps
		STREAM_CAPTURE = true;
		DISTANCE = true;
		FIRST_DISTANCE = true;
		TOTAL_COUNT = 0;
		CAMERA_READY = true;
		String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
		storeDir = "DistPics_" + timeStamp;
		try {
			EditText readDist = (EditText) findViewById(R.id.editDist);
			if (readDist != null)
				DISTANCE_TO_TRAVEL = Float.parseFloat(readDist.getText().toString());
			EditText readFrames = (EditText) findViewById(R.id.editFrames);
			if (readFrames != null)
				TOTAL_FRAMES = Integer.parseInt(readFrames.getText().toString());
		} catch (NumberFormatException nfe) {
			// Catch and continue with defaults
		}
		Toast.makeText(getBaseContext(), "Distance Mode", Toast.LENGTH_SHORT).show();
		continuousCapture();
	}
	
	public void continuousCapture()
	{
		if (TOTAL_COUNT >= TOTAL_FRAMES) {
			return; // Done
		}
		
		long prevTime = System.currentTimeMillis();	// Time at loop start
		long currentTime;
		if (DISTANCE) {	//Snap a series of photos based on distance traveled

			if (CAMERA_READY && !FIRST_DISTANCE) {
				TOTAL_COUNT++;
				CAMERA_READY = false;
				mCamera.takePicture( this, null, null, this);
			}
		} else {	// DISTANCE == false, Snap a series of photos based on time passed								
			while(true) {
				currentTime = System.currentTimeMillis();
				if (((currentTime - prevTime) > TIME_TO_TRAVEL) && CAMERA_READY) {	// Not the right way to do this
					TOTAL_COUNT++;
					CAMERA_READY = false;
					mCamera.takePicture( this, null, null, this);
					break;
				}						
			}
		}
	}
	
	//Camera Callback Methods
	@Override
	public void onShutter()
	{

	}
	
	@Override
	public void onPictureTaken(byte[] data, Camera camera)
	{
		//Add GPS properties to image
		addGpsToImg();
		
		//Store the picture
        File pictureFile = getOutputMediaFile(MEDIA_TYPE_IMAGE, this);
        if (pictureFile == null){
            Log.d(TAG, "Error creating media file, check storage permissions.");
            return;
        }

        try {
            FileOutputStream fos = new FileOutputStream(pictureFile);
            fos.write(data);
            fos.close();
        } catch (FileNotFoundException e) {
            Log.d(TAG, "File not found: " + e.getMessage());
        } catch (IOException e) {
            Log.d(TAG, "Error accessing file: " + e.getMessage());
        }
		
		//Must restart preview
		camera.startPreview();
		CAMERA_READY = true;
		if (!DISTANCE && STREAM_CAPTURE)
		{
			continuousCapture();
		}
	}
	
	//Surface Callback Methods
	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height)
	{
		Camera.Parameters params = mCamera.getParameters();
		List<Camera.Size> prevsizes = params.getSupportedPreviewSizes();
		Camera.Size prevselected = prevsizes.get(0);
		params.setPreviewSize(prevselected.width, prevselected.height);

		params.setPictureSize(3264, 2448);		// Hard-coded image size to Galaxy S3		
		params.setJpegQuality(100);

		mCamera.setParameters(params);		
        mCamera.setDisplayOrientation(90);
		mCamera.startPreview();
	}
	
	@Override
	public void surfaceCreated(SurfaceHolder holder)
	{
		try
		{
			mCamera.setPreviewDisplay(mHolder);
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	@Override
	public void surfaceDestroyed(SurfaceHolder holder)
	{
		// Do Nothing
	}
	
	public void addGpsToImg()
	{
		Camera.Parameters params = mCamera.getParameters();
		// Set current GPS parameters
		if(currentLocation != null) {
			params.setGpsAltitude(currentLocation.getAltitude());
			params.setGpsLatitude(currentLocation.getLatitude());
			params.setGpsLongitude(currentLocation.getLongitude());
			params.setGpsTimestamp(currentLocation.getTime()/1000);		// setGpsTimestamp takes seconds, not milliseconds (returned by getTime()
		}		
		mCamera.setParameters(params);
	}
	
	// Create a File for saving an image or video
	private static File getOutputMediaFile(int type, Context context){
	    // To be safe, you should check that the SDCard is mounted
	    // using Environment.getExternalStorageState() before doing this.

	    File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
	              Environment.DIRECTORY_PICTURES), storeDir);
	    // This location works best if you want the created images to be shared
	    // between applications and persist after your app has been uninstalled.

	    // Create the storage directory if it does not exist
	    if (! mediaStorageDir.exists()){
	        if (! mediaStorageDir.mkdirs()){
	        	Toast.makeText(context, "Couldn't create dir", Toast.LENGTH_SHORT).show();
	            Log.d("FlightCapture", "failed to create directory");
	            return null;
	        }
	    }

	    // Create a media file name
	    String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
	    File mediaFile;
	    if (type == MEDIA_TYPE_IMAGE){
	        mediaFile = new File(mediaStorageDir.getPath() + File.separator +
	        "IMG_"+ timeStamp + ".jpg");
	    } else if(type == MEDIA_TYPE_VIDEO) {
	        mediaFile = new File(mediaStorageDir.getPath() + File.separator +
	        "VID_"+ timeStamp + ".mp4");
	    } else {
	        return null;
	    }
	    return mediaFile;
	}	
	
	protected void safeLocationStart() {
	    // This verification should be done during onStart() because the system calls
	    // this method when the user returns to the activity, which ensures the desired
	    // location provider is enabled each time the activity resumes from the stopped state.
	    final boolean gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);

	    if (!gpsEnabled) {
	        // Build an alert dialog here that requests that the user enable
	        // the location services, then when the user clicks the "OK" button,
	        // call enableLocationSettings()
	    	//Toast.makeText(getBaseContext(),  "GPS Not Enabled", Toast.LENGTH_SHORT).show();
	    	AlertDialog.Builder builder = new AlertDialog.Builder(this);
	    	builder.setTitle("Location Manager");
	    	builder.setMessage("Turn on the GPS?");
	    	builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					//No location service, no Activity
					enableLocationSettings();			// sends user to Android Settings to turn on GPS
	    			//finish();		//ends the activity			
				}
	    	});
	    	builder.create().show();	    	
	    }
	    
	    //Get a cached location, if it exists
	    currentLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);

	    //Register for updates
	    int minTime = TIME_BETWEEN_GPS;
	    float minDist = 0;
	    locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, minTime, minDist, listener);
	}
	    
	private final LocationListener listener = new LocationListener() {
	    @Override
	    public void onLocationChanged(Location location) {
	        // A new location update is received.  Do something useful with it.
	    	if (isBetterLocation(location, currentLocation))
	    		currentLocation = location;
	    	if (currentLocation == null)
	    		return;		//Skip routine if location is invalid
	    	if (previousLocation == null)
	    		previousLocation = currentLocation;			// Prevents a capture if null

	    	float distance = 0;
	    	if (DISTANCE && STREAM_CAPTURE) {

	    		if (FIRST_DISTANCE) {
		    		Toast.makeText(getBaseContext(), "First!", Toast.LENGTH_SHORT).show();

		    		previousLocation = currentLocation;
		    		FIRST_DISTANCE = false;
		    		continuousCapture();
		    	} else {	// Makes sure first distance has succeeded

    				distance = currentLocation.distanceTo(previousLocation);

    	    		if (distance > DISTANCE_TO_TRAVEL) {	
    	    			String temp = "Accuracy = " + Float.toString(currentLocation.getAccuracy());
    		    		Toast.makeText(getBaseContext(), temp, Toast.LENGTH_SHORT).show();
    		    		previousLocation = currentLocation;
    		    		continuousCapture();
    		    	} 
    			}

	    	}		    		
	    }
	    
	    @Override
	    public void onProviderDisabled(String provider){}
	    
	    @Override
	    public void onProviderEnabled(String provider) {}
	    
	    @Override
	    public void onStatusChanged(String provider, int status, Bundle extras) {}
	};


	private void enableLocationSettings() {
	    Intent settingsIntent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
	    startActivity(settingsIntent);
	}		
	
	// Check if this device has a camera
	private boolean checkCameraHardware(Context context) {
	    if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)){	        
	        return true;	// this device has a camera
	    } else {	        
	        return false;	// no camera on this device
	    }
	}


	private static final int TWO_MINUTES = 1000 * 60 * 2;

//	   Determines whether one Location reading is better than the current Location fix
//	    @param location  The new Location that you want to evaluate
//	    @param currentBestLocation  The current Location fix, to which you want to compare the new one

	protected boolean isBetterLocation(Location location, Location currentBestLocation) {
	    if (currentBestLocation == null) {
	        // A new location is always better than no location
	        return true;
	    }

	    // Check whether the new location fix is newer or older
	    long timeDelta = location.getTime() - currentBestLocation.getTime();
	    boolean isSignificantlyNewer = timeDelta > TWO_MINUTES;
	    boolean isSignificantlyOlder = timeDelta < -TWO_MINUTES;
	    boolean isNewer = timeDelta > 0;

	    // If it's been more than two minutes since the current location, use the new location
	    // because the user has likely moved
	    if (isSignificantlyNewer) {
	        return true;
	    // If the new location is more than two minutes older, it must be worse
	    } else if (isSignificantlyOlder) {
	        return false;
	    }

	    // Check whether the new location fix is more or less accurate
	    int accuracyDelta = (int) (location.getAccuracy() - currentBestLocation.getAccuracy());
	    boolean isLessAccurate = accuracyDelta > 0;
	    boolean isMoreAccurate = accuracyDelta < 0;
	    boolean isSignificantlyLessAccurate = accuracyDelta > 200;

	    // Check if the old and new location are from the same provider
	    boolean isFromSameProvider = isSameProvider(location.getProvider(),
	            currentBestLocation.getProvider());

	    // Determine location quality using a combination of timeliness and accuracy
	    if (isMoreAccurate) {
	        return true;
	    } else if (isNewer && !isLessAccurate) {
	        return true;
	    } else if (isNewer && !isSignificantlyLessAccurate && isFromSameProvider) {
	        return true;
	    }
	    return false;
	}

	// Checks whether two providers are the same
	private boolean isSameProvider(String provider1, String provider2) {
	    if (provider1 == null) {
	      return provider2 == null;
	    }
	    return provider1.equals(provider2);
	}
	
}