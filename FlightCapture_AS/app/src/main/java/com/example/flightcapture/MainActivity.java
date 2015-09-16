package com.example.flightcapture;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import android.media.ExifInterface;

import org.apache.http.impl.io.ContentLengthInputStream;

public class MainActivity extends Activity 
		implements SurfaceHolder.Callback, Camera.ShutterCallback, Camera.PictureCallback,
		SensorEventListener, RadioGroup.OnCheckedChangeListener {

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

	// ADDED SENSOR CODE
	private SensorManager mSensorManager = null;

	// angular speeds from gyro
	private float[] gyro = new float[3];

	// rotation matrix from gyro data
	private float[] gyroMatrix = new float[9];

	// orientation angles from gyro matrix
	private float[] gyroOrientation = new float[3];

	// magnetic field vector
	private float[] magnet = new float[3];

	// accelerometer vector
	private float[] accel = new float[3];

	// orientation angles from accel and magnet
	private float[] accMagOrientation = new float[3];

	// final orientation angles from sensor fusion
	private float[] fusedOrientation = new float[3];

	// accelerometer and magnetometer based rotation matrix
	private float[] rotationMatrix = new float[9];

	public static final float EPSILON = 0.000000001f;
	private static final float NS2S = 1.0f / 1000000000.0f;
	private int timestamp;
	private boolean initState = true;

	public static final int TIME_CONSTANT = 30;
	public static final float FILTER_COEFFICIENT = 0.98f;
	private Timer fuseTimer = new Timer();

	// The following members are only for displaying the sensor output.
	public Handler mHandler;
	private RadioGroup mRadioGroup;
	private TextView mAzimuthView;
	private TextView mPitchView;
	private TextView mRollView;
	private int radioSelection;
	DecimalFormat d = new DecimalFormat("#.##");
	// END ADDED SENSOR CODE


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


		// ADDED SENSOR CODE
		gyroOrientation[0] = 0.0f;
		gyroOrientation[1] = 0.0f;
		gyroOrientation[2] = 0.0f;

		// initialise gyroMatrix with identity matrix
		gyroMatrix[0] = 1.0f; gyroMatrix[1] = 0.0f; gyroMatrix[2] = 0.0f;
		gyroMatrix[3] = 0.0f; gyroMatrix[4] = 1.0f; gyroMatrix[5] = 0.0f;
		gyroMatrix[6] = 0.0f; gyroMatrix[7] = 0.0f; gyroMatrix[8] = 1.0f;

		// get sensorManager and initialise sensor listeners
		mSensorManager = (SensorManager) this.getSystemService(SENSOR_SERVICE);
		initListeners();

		// wait for one second until gyroscope and magnetometer/accelerometer
		// data is initialised then scedule the complementary filter task
		fuseTimer.scheduleAtFixedRate(new calculateFusedOrientationTask(),
				1000, TIME_CONSTANT);

		// GUI stuff
		mHandler = new Handler();
		radioSelection = 0;
		d.setRoundingMode(RoundingMode.HALF_UP);
		d.setMaximumFractionDigits(3);
		d.setMinimumFractionDigits(3);
		mRadioGroup = (RadioGroup)findViewById(R.id.radioGroup1);
		mAzimuthView = (TextView)findViewById(R.id.textView4);
		mPitchView = (TextView)findViewById(R.id.textView5);
		mRollView = (TextView)findViewById(R.id.textView6);
		mRadioGroup.setOnCheckedChangeListener(this);
		// END ADDED SENSOR CODE
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

		// ADDED SENSOR CODE
		// restore the sensor listeners when user resumes the application.
		initListeners();
		// END ADDED SENSOR CODE
	}
	
	@Override
	public void onPause()
	{
		super.onPause();
		stopPreviewAndFreeCamera();
		locationManager.removeUpdates(listener);

		// ADDED SENSOR CODE
		// unregister sensor listeners to prevent the activity from draining the device's battery.
		mSensorManager.unregisterListener(this);
		// END ADDED SENSOR CODE
	}
	
	@Override
	public void onStop()
	{
		super.onStop();

		// ADDED SENSOR CODE
		// unregister sensor listeners to prevent the activity from draining the device's battery.
		mSensorManager.unregisterListener(this);
		// END ADDED SENSOR CODE
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

        initCameraParameters();
        initDataFile();
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

        initCameraParameters();
        initDataFile();
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

		// Add other sensor information to image
		// addSensorDataToImg(pictureFile); Replace with recordDataFile by Gregg

        recordCameraParameters(); // update the camera parameters saved in global variables
        recordDataFile(pictureFile); // add a line item to the CSV data file for this image

        updateCameraParameters(pictureFile); // evaluate the image just take and correct settings for next image

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
			//params.setGpsProcessingMethod(currentLocation.getProvider());
			//Toast.makeText(getBaseContext(), "Added GPS data", Toast.LENGTH_SHORT).show();
		}
		mCamera.setParameters(params);
	}

	public void on()
	{
		if(currentLocation != null) {
			Camera.Parameters params = mCamera.getParameters();

			// Set current GPS parameters
			params.setGpsAltitude(currentLocation.getAltitude());
			params.setGpsLatitude(currentLocation.getLatitude());
			params.setGpsLongitude(currentLocation.getLongitude());
			params.setGpsTimestamp(currentLocation.getTime()/1000);		// setGpsTimestamp takes seconds, not milliseconds (returned by getTime()

			mCamera.setParameters(params);
		}
	}

	public void addSensorDataToImg(File pictureFile)
	{
		// Variable declaration & initialization
		String fileName = pictureFile.getName();
		String filePath = pictureFile.getAbsolutePath();
		CharSequence azimuthValue = mAzimuthView.getText();;
		CharSequence pitchValue = mPitchView.getText();
		CharSequence rollValue = mRollView.getText();

        String accuracyString = "Accuracy: " + Float.toString(currentLocation.getAccuracy()) + ';';
        String providerString = "Provider: " + currentLocation.getProvider() + ';';
        String speedString = "Speed: " + Float.toString(currentLocation.getSpeed()) + ';';

		// Combine sensor data into single string
		/*
		String customEXIF = "Azimuth: " + azimuthValue + "; Pitch: " + pitchValue +
				"; Roll: " + rollValue + ';';
		*/
		// /*
		String azimuthString = "Azimuth: " + azimuthValue + ';';
		String pitchString = "Pitch: " + pitchValue + ';';
		String rollString = "Roll: " + rollValue + ';';
		// */

		/*
		// Write sensor data to EXIF
		try {
			ExifInterface exif = new ExifInterface(filePath);

			// Read all GPS attributes
			double altitude = currentLocation.getAltitude();
			double latitude = currentLocation.getLatitude();
			double longitude = currentLocation.getLongitude();
			double time = currentLocation.getTime() / 1000;

			// Apparently exifInterface will not read the GPS tags in EXIF correctly & instead
			// returns null values
			String gpsAlt = String.valueOf(altitude);
			String gpsLat = String.valueOf(latitude);
			String gpsLong = String.valueOf(longitude);
			String gpsTimeStamp = String.valueOf(time);

			/*
			String gpsLatRef = exif.getAttribute(exif.TAG_GPS_LATITUDE_REF);
			String gpsLat = exif.getAttribute(exif.TAG_GPS_LATITUDE);
			String gpsLongRef = exif.getAttribute(exif.TAG_GPS_LONGITUDE_REF);
			String gpsLong = exif.getAttribute(exif.TAG_GPS_LONGITUDE);
			String gpsAltRef = exif.getAttribute(exif.TAG_GPS_ALTITUDE_REF);
			String gpsAlt = exif.getAttribute(exif.TAG_GPS_ALTITUDE);
			String gpsTimeStamp = exif.getAttribute(exif.TAG_GPS_TIMESTAMP);
			String gpsDateStamp = exif.getAttribute(exif.TAG_GPS_DATESTAMP);

			// Set all GPS attributes
			// exif.setAttribute("GPS Latitude Ref",gpsLatRef);
			// exif.setAttribute(exif.TAG_GPS_LATITUDE,gpsLat);
			// exif.setAttribute("GPS Longitude Ref",gpsLongRef);
			// exif.setAttribute(exif.TAG_GPS_LONGITUDE,gpsLong);
			// exif.setAttribute("GPS Altitude Ref",gpsAltRef);
			// exif.setAttribute(exif.TAG_GPS_ALTITUDE,gpsAlt);
			// exif.setAttribute(exif.TAG_GPS_TIMESTAMP,gpsTimeStamp);
			// exif.setAttribute("GPS Date Stamp",gpsDateStamp);

			// Set azimuth, pitch, & roll
			// exif.setAttribute("UserComment", customEXIF);

			// Save all attributes
			// exif.saveAttributes();

		} catch (IOException e) {
			Log.d(TAG, "Error accessing file: " + filePath + " " + e.getMessage());
		}
		*/

		// /*
		// Write data to text file
		writeToFile(fileName);
		writeToFile("\r\n");
		writeToFile(azimuthString);
		writeToFile("\r\n");
		writeToFile(pitchString);
		writeToFile("\r\n");
		writeToFile(rollString);
        writeToFile("\r\n");
        writeToFile(speedString);
        writeToFile("\r\n");
        writeToFile(accuracyString);
        writeToFile("\r\n");
        writeToFile(providerString);
		writeToFile("\r\n");
		writeToFile("\r\n");
		// */
	}

	private void writeToFile(String data) {
		// Declare & initialize variables
		File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
				Environment.DIRECTORY_PICTURES), storeDir);

		File sensorDataFile = new File(mediaStorageDir.getAbsolutePath() + File.separator + "sensorData.txt");

		// Create sensor data file if it does now exist
		if(!sensorDataFile.exists()) {
			try {
				sensorDataFile.createNewFile();
				sensorDataFile.setWritable(true);
			}
			catch (IOException e) {
				Log.e("Exception", "File creation failed: " + e.toString());
			}
		}

		/*
		try {
			OutputStreamWriter fileOut = new OutputStreamWriter(sensorDataFile, Context.MODE_APPEND);
			fileOut.write(data);
			fileOut.close();
		}
		catch (IOException e) {
			Log.e("Exception", "File write failed: " + e.toString());
		}
		*/

		// /*
		try {
			/*
			FileOutputStream fos = openFileOutput(sensorDataFile.getAbsolutePath(),getApplicationContext().MODE_APPEND);

			fos.write(data.getBytes());
			fos.close();
			*/

			FileOutputStream fOut = new FileOutputStream(sensorDataFile.getPath(),true);
			OutputStreamWriter myOutWriter = new OutputStreamWriter(fOut);

			myOutWriter.append(data);
			myOutWriter.close();
			fOut.close();

		} catch (IOException e) {
			Log.e("Exception", "File write failed: " + e.toString());
		}
		// */

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
	    	AlertDialog.Builder builder = new AlertDialog.Builder(this);
	    	builder.setTitle("Location Manager");
	    	builder.setMessage("Turn on the GPS?");
	    	builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					//No location service, no Activity
					enableLocationSettings();			// sends user to Android Settings to turn on GPS
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
	    boolean isLessAccurate = accuracyDelta > DISTANCE_TO_TRAVEL;
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

	// ADDED SENSOR CODE

	// This function registers sensor listeners for the accelerometer, magnetometer and gyroscope.
	public void initListeners(){
		mSensorManager.registerListener(this,
				mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
				SensorManager.SENSOR_DELAY_FASTEST);

		mSensorManager.registerListener(this,
				mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE),
				SensorManager.SENSOR_DELAY_FASTEST);

		mSensorManager.registerListener(this,
				mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD),
				SensorManager.SENSOR_DELAY_FASTEST);
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		switch(event.sensor.getType()) {
			case Sensor.TYPE_ACCELEROMETER:
				// copy new accelerometer data into accel array and calculate orientation
				System.arraycopy(event.values, 0, accel, 0, 3);
				calculateAccMagOrientation();
				break;

			case Sensor.TYPE_GYROSCOPE:
				// process gyro data
				gyroFunction(event);
				break;

			case Sensor.TYPE_MAGNETIC_FIELD:
				// copy new magnetometer data into magnet array
				System.arraycopy(event.values, 0, magnet, 0, 3);
				break;
		}
	}

	// calculates orientation angles from accelerometer and magnetometer output
	public void calculateAccMagOrientation() {
		if(SensorManager.getRotationMatrix(rotationMatrix, null, accel, magnet)) {
			SensorManager.getOrientation(rotationMatrix, accMagOrientation);
		}
	}

	// This function is borrowed from the Android reference
	// at http://developer.android.com/reference/android/hardware/SensorEvent.html#values
	// It calculates a rotation vector from the gyroscope angular speed values.
	private void getRotationVectorFromGyro(float[] gyroValues,
										   float[] deltaRotationVector,
										   float timeFactor)
	{
		float[] normValues = new float[3];

		// Calculate the angular speed of the sample
		float omegaMagnitude =
				(float)Math.sqrt(gyroValues[0] * gyroValues[0] +
						gyroValues[1] * gyroValues[1] +
						gyroValues[2] * gyroValues[2]);

		// Normalize the rotation vector if it's big enough to get the axis
		if(omegaMagnitude > EPSILON) {
			normValues[0] = gyroValues[0] / omegaMagnitude;
			normValues[1] = gyroValues[1] / omegaMagnitude;
			normValues[2] = gyroValues[2] / omegaMagnitude;
		}

		// Integrate around this axis with the angular speed by the timestep
		// in order to get a delta rotation from this sample over the timestep
		// We will convert this axis-angle representation of the delta rotation
		// into a quaternion before turning it into the rotation matrix.
		float thetaOverTwo = omegaMagnitude * timeFactor;
		float sinThetaOverTwo = (float)Math.sin(thetaOverTwo);
		float cosThetaOverTwo = (float)Math.cos(thetaOverTwo);
		deltaRotationVector[0] = sinThetaOverTwo * normValues[0];
		deltaRotationVector[1] = sinThetaOverTwo * normValues[1];
		deltaRotationVector[2] = sinThetaOverTwo * normValues[2];
		deltaRotationVector[3] = cosThetaOverTwo;
	}

	// This function performs the integration of the gyroscope data.
	// It writes the gyroscope based orientation into gyroOrientation.
	public void gyroFunction(SensorEvent event) {
		// don't start until first accelerometer/magnetometer orientation has been acquired
		if (accMagOrientation == null)
			return;

		// initialisation of the gyroscope based rotation matrix
		if(initState) {
			float[] initMatrix = new float[9];
			initMatrix = getRotationMatrixFromOrientation(accMagOrientation);
			float[] test = new float[3];
			SensorManager.getOrientation(initMatrix, test);
			gyroMatrix = matrixMultiplication(gyroMatrix, initMatrix);
			initState = false;
		}

		// copy the new gyro values into the gyro array
		// convert the raw gyro data into a rotation vector
		float[] deltaVector = new float[4];
		if(timestamp != 0) {
			final float dT = (event.timestamp - timestamp) * NS2S;
			System.arraycopy(event.values, 0, gyro, 0, 3);
			getRotationVectorFromGyro(gyro, deltaVector, dT / 2.0f);
		}

		// measurement done, save current time for next interval
		timestamp = (int) event.timestamp;

		// convert rotation vector into rotation matrix
		float[] deltaMatrix = new float[9];
		SensorManager.getRotationMatrixFromVector(deltaMatrix, deltaVector);

		// apply the new rotation interval on the gyroscope based rotation matrix
		gyroMatrix = matrixMultiplication(gyroMatrix, deltaMatrix);

		// get the gyroscope based orientation from the rotation matrix
		SensorManager.getOrientation(gyroMatrix, gyroOrientation);
	}

	private float[] getRotationMatrixFromOrientation(float[] o) {
		float[] xM = new float[9];
		float[] yM = new float[9];
		float[] zM = new float[9];

		float sinX = (float)Math.sin(o[1]);
		float cosX = (float)Math.cos(o[1]);
		float sinY = (float)Math.sin(o[2]);
		float cosY = (float)Math.cos(o[2]);
		float sinZ = (float)Math.sin(o[0]);
		float cosZ = (float)Math.cos(o[0]);

		// rotation about x-axis (pitch)
		xM[0] = 1.0f; xM[1] = 0.0f; xM[2] = 0.0f;
		xM[3] = 0.0f; xM[4] = cosX; xM[5] = sinX;
		xM[6] = 0.0f; xM[7] = -sinX; xM[8] = cosX;

		// rotation about y-axis (roll)
		yM[0] = cosY; yM[1] = 0.0f; yM[2] = sinY;
		yM[3] = 0.0f; yM[4] = 1.0f; yM[5] = 0.0f;
		yM[6] = -sinY; yM[7] = 0.0f; yM[8] = cosY;

		// rotation about z-axis (azimuth)
		zM[0] = cosZ; zM[1] = sinZ; zM[2] = 0.0f;
		zM[3] = -sinZ; zM[4] = cosZ; zM[5] = 0.0f;
		zM[6] = 0.0f; zM[7] = 0.0f; zM[8] = 1.0f;

		// rotation order is y, x, z (roll, pitch, azimuth)
		float[] resultMatrix = matrixMultiplication(xM, yM);
		resultMatrix = matrixMultiplication(zM, resultMatrix);
		return resultMatrix;
	}

	private float[] matrixMultiplication(float[] A, float[] B) {
		float[] result = new float[9];

		result[0] = A[0] * B[0] + A[1] * B[3] + A[2] * B[6];
		result[1] = A[0] * B[1] + A[1] * B[4] + A[2] * B[7];
		result[2] = A[0] * B[2] + A[1] * B[5] + A[2] * B[8];

		result[3] = A[3] * B[0] + A[4] * B[3] + A[5] * B[6];
		result[4] = A[3] * B[1] + A[4] * B[4] + A[5] * B[7];
		result[5] = A[3] * B[2] + A[4] * B[5] + A[5] * B[8];

		result[6] = A[6] * B[0] + A[7] * B[3] + A[8] * B[6];
		result[7] = A[6] * B[1] + A[7] * B[4] + A[8] * B[7];
		result[8] = A[6] * B[2] + A[7] * B[5] + A[8] * B[8];

		return result;
	}

	class calculateFusedOrientationTask extends TimerTask {
		public void run() {
			float oneMinusCoeff = 1.0f - FILTER_COEFFICIENT;

            /*
             * Fix for 179 degrees <--> -179 degrees transition problem:
             * Check whether one of the two orientation angles (gyro or accMag) is negative while the other one is positive.
             * If so, add 360 degrees (2 * math.PI) to the negative value, perform the sensor fusion, and remove the 360 degrees from the result
             * if it is greater than 180 degrees. This stabilizes the output in positive-to-negative-transition cases.
             */

			// azimuth
			if (gyroOrientation[0] < -0.5 * Math.PI && accMagOrientation[0] > 0.0) {
				fusedOrientation[0] = (float) (FILTER_COEFFICIENT * (gyroOrientation[0] + 2.0 * Math.PI) + oneMinusCoeff * accMagOrientation[0]);
				fusedOrientation[0] -= (fusedOrientation[0] > Math.PI) ? 2.0 * Math.PI : 0;
			}
			else if (accMagOrientation[0] < -0.5 * Math.PI && gyroOrientation[0] > 0.0) {
				fusedOrientation[0] = (float) (FILTER_COEFFICIENT * gyroOrientation[0] + oneMinusCoeff * (accMagOrientation[0] + 2.0 * Math.PI));
				fusedOrientation[0] -= (fusedOrientation[0] > Math.PI)? 2.0 * Math.PI : 0;
			}
			else {
				fusedOrientation[0] = FILTER_COEFFICIENT * gyroOrientation[0] + oneMinusCoeff * accMagOrientation[0];
			}

			// pitch
			if (gyroOrientation[1] < -0.5 * Math.PI && accMagOrientation[1] > 0.0) {
				fusedOrientation[1] = (float) (FILTER_COEFFICIENT * (gyroOrientation[1] + 2.0 * Math.PI) + oneMinusCoeff * accMagOrientation[1]);
				fusedOrientation[1] -= (fusedOrientation[1] > Math.PI) ? 2.0 * Math.PI : 0;
			}
			else if (accMagOrientation[1] < -0.5 * Math.PI && gyroOrientation[1] > 0.0) {
				fusedOrientation[1] = (float) (FILTER_COEFFICIENT * gyroOrientation[1] + oneMinusCoeff * (accMagOrientation[1] + 2.0 * Math.PI));
				fusedOrientation[1] -= (fusedOrientation[1] > Math.PI)? 2.0 * Math.PI : 0;
			}
			else {
				fusedOrientation[1] = FILTER_COEFFICIENT * gyroOrientation[1] + oneMinusCoeff * accMagOrientation[1];
			}

			// roll
			if (gyroOrientation[2] < -0.5 * Math.PI && accMagOrientation[2] > 0.0) {
				fusedOrientation[2] = (float) (FILTER_COEFFICIENT * (gyroOrientation[2] + 2.0 * Math.PI) + oneMinusCoeff * accMagOrientation[2]);
				fusedOrientation[2] -= (fusedOrientation[2] > Math.PI) ? 2.0 * Math.PI : 0;
			}
			else if (accMagOrientation[2] < -0.5 * Math.PI && gyroOrientation[2] > 0.0) {
				fusedOrientation[2] = (float) (FILTER_COEFFICIENT * gyroOrientation[2] + oneMinusCoeff * (accMagOrientation[2] + 2.0 * Math.PI));
				fusedOrientation[2] -= (fusedOrientation[2] > Math.PI)? 2.0 * Math.PI : 0;
			}
			else {
				fusedOrientation[2] = FILTER_COEFFICIENT * gyroOrientation[2] + oneMinusCoeff * accMagOrientation[2];
			}

			// overwrite gyro matrix and orientation with fused orientation
			// to compensate gyro drift
			gyroMatrix = getRotationMatrixFromOrientation(fusedOrientation);
			System.arraycopy(fusedOrientation, 0, gyroOrientation, 0, 3);


			// update sensor output in GUI
			mHandler.post(updateOreintationDisplayTask);
		}
	}


	// **************************** GUI FUNCTIONS *********************************

	@Override
	public void onCheckedChanged(RadioGroup group, int checkedId) {
		switch(checkedId) {
			case R.id.radio0:
				radioSelection = 0;
				break;
			case R.id.radio1:
				radioSelection = 1;
				break;
			case R.id.radio2:
				radioSelection = 2;
				break;
		}
	}

	public void updateOreintationDisplay() {
		switch(radioSelection) {
			case 0:
				mAzimuthView.setText(d.format(accMagOrientation[0] * 180/Math.PI));
				mPitchView.setText(d.format(accMagOrientation[1] * 180/Math.PI));
				mRollView.setText(d.format(accMagOrientation[2] * 180/Math.PI));
				break;
			case 1:
				mAzimuthView.setText(d.format(gyroOrientation[0] * 180/Math.PI));
				mPitchView.setText(d.format(gyroOrientation[1] * 180/Math.PI));
				mRollView.setText(d.format(gyroOrientation[2] * 180/Math.PI));
				break;
			case 2:
				mAzimuthView.setText(d.format(fusedOrientation[0] * 180/Math.PI));
				mPitchView.setText(d.format(fusedOrientation[1] * 180/Math.PI));
				mRollView.setText(d.format(fusedOrientation[2] * 180/Math.PI));
				break;
		}
	}

	private Runnable updateOreintationDisplayTask = new Runnable() {
		public void run() {
			updateOreintationDisplay();
		}
	};
	// END ADDED SENSOR CODE


    /* GREGG'S MERGE */
    public int prevExposureCompensationValue;
    public String prevIsoValue;
    public int maxExposureCompensationValue;
    public int minExposureCompensationValue;
    public int testBit = 0; // used to test camera controls, used to alternate ISO settings
    public File dataFile;


    public void initCameraParameters()
    {//Initialize Camera Settings (should be called before starting capturing sequence, before first photo) [Note: I believe that the preview should be started first]

        //** INITIALIZE CAMERA PARAMETERS **//

        //Retrieve current camera parameter settings
        Camera.Parameters params = mCamera.getParameters(); // Request Current Paramaters

        // Edit camera parameter settings
        // params.setAutoExposureLock(true); // Lock Auto Exposure so it can be controlled per snap, CHECK MIN VERSION
        // params.setAutoWhiteBalanceLock(true); // Lock AWB so we can post process the images, CHECK MIN VERSION
        // params.setWhiteBalance("no adjustment"); //this string value could we wrong, DECIDED TO USE AUTO WHITE BALANCE
        // Set GPS Altitude: 7/26/2015, waiting for KML group to decide GPS/ALT implementation. Remember to look at current addGpsToImg() etc. implementation
        params.setFocusMode("FOCUS_MOD_EDOF"); // set to continuous focus mode
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        storeDir = "TimePics_" + timeStamp;// set directory to save file (if external remember to put in manifest file)
        //params.setPictureFormat(256); //256 represents JPEG format, IS THIS NECESSARY?

        maxExposureCompensationValue = params.getMaxExposureCompensation();
        minExposureCompensationValue = params.getMinExposureCompensation();

        recordCameraParameters();

        //Set camera settings to modified values
        //** mCamera.setParameters(params); 8/8/15: REMOVED BECUASE IT CRASHED THE APP!
        //Toast.makeText(getBaseContext(), "Camera Settings Initialized", Toast.LENGTH_SHORT).show();
    }

    public void initDataFile()
    {   // NOTE: Column titles aren't getting recorded, not sure why
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), storeDir);
        dataFile = new File(mediaStorageDir.getPath() + File.separator +
                "DATA_"+ timeStamp + ".csv");
        String titleString;   //"Time Stamp," + colTitles + "\n";
		titleString = "FILENAME,TIME,ISO,EXPCOMP,AZIMUTH,PITCH,ROLL,GPS TIME,ALTITUDE,LATITUDE,LONGITUDE,GPS ACCURACY,SPEED,PROVIDER,/r/n";
		try {
            FileOutputStream fos = new FileOutputStream(dataFile, true);
            fos.write(titleString.getBytes());
            fos.close();
        } catch (FileNotFoundException e) {
            Log.d(TAG, "File not found: " + e.getMessage());
        } catch (IOException e) {
            Log.d(TAG, "Error accessing file: " + e.getMessage());
        }

    }

    public void recordDataFile(File pictureFile)
    {   // Records a data file to the same directory as the pictures
        // FINISH BY ADDING INPUTS FOR RECORDING!!!!
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String timeString = timeStamp + "," ;

        CharSequence azimuthValue = mAzimuthView.getText();;
        CharSequence pitchValue = mPitchView.getText();
        CharSequence rollValue = mRollView.getText();

        if (currentLocation == null){
            return;
        }

        double currentAltitude = currentLocation.getAltitude();
        double currentLatitude = currentLocation.getLatitude();
		double currentLongitude = currentLocation.getLongitude();
		double currentTimestamp = currentLocation.getTime();
		double currentGPSaccuracy = currentLocation.getAccuracy();
        //recordData(prevIsoValue + "," + prevExposureCompensationValue);
        String providerString = currentLocation.getProvider();
        double speed = currentLocation.getSpeed();

        String dataString = pictureFile + "," +
                            timeString + "," +
                            prevIsoValue + "," +
                            prevExposureCompensationValue + "," +
                            azimuthValue + "," +
                            pitchValue + "," +
                            rollValue + "," +
                            currentTimestamp + "," +
                            currentAltitude + "," +
                            currentLatitude + "," +
                            currentLongitude + "," +
                            currentGPSaccuracy + "," +
                            speed + "," +
                            providerString + "," + "/r/n";
        try {
            FileOutputStream fos = new FileOutputStream(dataFile, true);
            fos.write(timeString.getBytes());
            fos.write(dataString.getBytes());
            fos.write(("\n").getBytes());
            fos.close();
        } catch (FileNotFoundException e) {
            Log.d(TAG, "File not found: " + e.getMessage());
        } catch (IOException e) {
            Log.d(TAG, "Error accessing file: " + e.getMessage());
        }

    }

    public void recordCameraParameters()
    { // Called immediately after the photo is taken to record current parameters (b/ potentially in constant flux during preview)
        Camera.Parameters params = mCamera.getParameters();
        prevExposureCompensationValue = params.getExposureCompensation();
        prevIsoValue = params.get("iso");
        //recordData(prevIsoValue + "," + prevExposureCompensationValue);
        //Toast.makeText(getBaseContext(), prevExposureCompensationValue, Toast.LENGTH_SHORT).show(); // debugging purposes
        //Toast.makeText(getBaseContext(), prevIsoValue, Toast.LENGTH_SHORT).show(); // debugging purposes
    }

    public void updateCameraParameters(File pictureFile)
    {//Initialize Camera Settings (should be called before starting capturing sequence, before first photo)
        evaluatePreviousImage(pictureFile); // should return new values for exposure comp and iso

        Camera.Parameters params = mCamera.getParameters();
        if (testBit > 0){
            params.set("iso", "200");
            // Toast.makeText(getBaseContext(), "ISO: 200", Toast.LENGTH_SHORT).show();
            testBit = 0;
        } else {
            params.set("iso", "1600");
            // Toast.makeText(getBaseContext(), "ISO: 1600", Toast.LENGTH_SHORT).show();
            testBit = 1;
            //testBit = 0;// for testing exp comp alone 8/8/15
        }
        if (prevExposureCompensationValue == minExposureCompensationValue) {
            params.setExposureCompensation(maxExposureCompensationValue);
            // Toast.makeText(getBaseContext(), ((maxExposureCompensationValue) + ""), Toast.LENGTH_SHORT).show();
        } else {
            params.setExposureCompensation(prevExposureCompensationValue - 1); // params.getExposureCompensationStep());
            // Toast.makeText(getBaseContext(), ((prevExposureCompensationValue - 1) + ""), Toast.LENGTH_SHORT).show();
        }
        mCamera.setParameters(params);

        //** Camera.Parameters testParams = mCamera.getParameters();
        //** Toast.makeText(getBaseContext(), testParams.getExposureCompensation(), Toast.LENGTH_SHORT).show(); // debugging purposes
        //** Toast.makeText(getBaseContext(), testParams.get("iso"), Toast.LENGTH_SHORT).show(); // debugging purposes
    }

    public void evaluatePreviousImage(File pictureFile)
    { // Once the evaluation algorithms are settled to measure jitter, noise, and exposure we will implement here

        // open/find previous image, how to get previous image?

        // Implement algorithm or call library here

        return;// calculate and return new exposure comp and iso values
    }


}