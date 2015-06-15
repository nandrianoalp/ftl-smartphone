package com.ngc.skyline.imagecapturetest;

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
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.ExifInterface;
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
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity 
		implements SurfaceHolder.Callback, Camera.ShutterCallback, Camera.PictureCallback, SensorEventListener {
	
	//private static final String JPEG_FILE_PREFIX = "img";
	//private static final String JPEG_FILE_SUFFIX = ".jpg";
	//String mCurrentPhotoPath = "";
	
	//private static final int REQUEST_IMAGE = 100;
	//ImageView imageView;
	
	private static final String TAG = "MyActivity";
		
	public static final int MEDIA_TYPE_IMAGE = 1;
	public static final int MEDIA_TYPE_VIDEO = 2;
//	private static final int LOOPTIMEOUT = 100000;	//In milliseconds
	
	private static String storeDir = "ImageCapture";
	private static boolean CAMERA_READY = true;
	private static boolean STREAM_CAPTURE = false;
	private static int TOTAL_FRAMES = 10;			//Default number of frames to snap
	private static int TOTAL_COUNT = 0;
	private static final int TIME_BETWEEN_GPS = 0;
	
	// Time Mode
	private static float TIME_TO_TRAVEL = 3000;		//Time in milliseconds
	
	// Distance Mode
	public static boolean DISTANCE = false;
	public static float DISTANCE_TO_TRAVEL = 1;	//Distance in meters
	public static boolean FIRST_DISTANCE = false;
	//public static double[] CURRENT_GPS = {0,0,0,0};
	
//	PowerManager pm;
//	PowerManager.WakeLock mWakeLock;
	
	Camera mCamera;
	//SurfaceView mPreview;
    SurfaceView mSurfaceView;
    SurfaceHolder mHolder;
    
    LocationManager locationManager;
    static Location currentLocation;
	static Location previousLocation;
    TextView locationView;
    
    private SensorManager mSensorManager;
    private Sensor mSensor;
    
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
        	safeCameraOpen(0);
        }
		//imageView = (ImageView)findViewById(R.id.image);
        
//        locationView = new TextView(this);
//        setContentView(locationView);
        
        locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        //LocationProvider provider = locationManager.getProvider(LocationManager.GPS_PROVIDER);
        
        // Might need to use WakeLock to keep the app going 
//        pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
//        mWakeLock = pm.newWakeLock(
//                PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE, TAG);
//        mWakeLock.acquire();    // Keep the screen on and the activity alive    
        
        // For now I can use the keep screen on flag and leave in portrait to keep the app working
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);		// Band-aid
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);	// Band-aid	
        
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        List<Sensor> deviceSensors = mSensorManager.getSensorList(Sensor.TYPE_ALL);
//        for(int i=0; i < deviceSensors.size(); i++) {
//        		mSensor = deviceSensors.get(i);
//        		String temp = mSensor.getName();
//            	Toast.makeText(this.getBaseContext(), temp, Toast.LENGTH_LONG).show();
//        }        
        //mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
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
//		safeCameraOpen(0);
//		safeLocationStart();
	}
	
	@Override
	public void onResume()
	{
		super.onResume();
		safeCameraOpen(0);
		safeLocationStart();
		//mSensorManager.registerListener(this, mSensor, SensorManager.SENSOR_DELAY_NORMAL);
	}
	
	@Override
	public void onPause()
	{
		super.onPause();
		stopPreviewAndFreeCamera();
		locationManager.removeUpdates(listener);
		//mSensorManager.unregisterListener(this);
	}
	
	@Override
	public void onStop()
	{
		super.onStop();
//		stopPreviewAndFreeCamera();
//		locationManager.removeUpdates(listener);
	}
	
	@Override
	public void onDestroy()
	{
		super.onDestroy();
		stopPreviewAndFreeCamera();
		locationManager.removeUpdates(listener);
//		mWakeLock.release();
	}
	
	private boolean safeCameraOpen(int id) {
	    boolean qOpened = false;
	  
	    try {
	        releaseCameraAndPreview();
	        mCamera = Camera.open();		// Override 'id' and set to first back facing camera
	        qOpened = (mCamera != null);
	        //mCamera.enableShutterSound(false);
	    } catch (Exception e) {
	        Log.e(getString(R.string.app_name), "failed to open Camera");
	        e.printStackTrace();
	    }

	    return qOpened;    
	}
	
	private void releaseCameraAndPreview() {
	    //mPreview.setCamera(null);
		//setCamera(null);
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
		mCamera.takePicture( this, null, null, this);
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
		if (TOTAL_FRAMES == 0) {	//Continue
		} else if (TOTAL_COUNT >= TOTAL_FRAMES) {
			return; // Done
		}
		
		long prevTime = System.currentTimeMillis();	// Time at loop start
		long currentTime;
		if (DISTANCE == true) {	//Snap a series of photos based on distance traveled
			//String temp = "Distance " + Integer.toString(TOTAL_COUNT);
			//Toast.makeText(getBaseContext(), temp, Toast.LENGTH_SHORT).show();
			if ((CAMERA_READY == true) && (FIRST_DISTANCE == false)) {
				TOTAL_COUNT++;
				CAMERA_READY = false;
				mCamera.takePicture( this, null, null, this);
			}
		} else {	// DISTANCE == false, Snap a series of photos based on time passed								
			while(true) {
				currentTime = System.currentTimeMillis();
				if (((currentTime - prevTime) > TIME_TO_TRAVEL) && (CAMERA_READY == true)) {	// Not the right way to do this
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
		//Toast.makeText(this,  "Click!", Toast.LENGTH_SHORT).show();
		//CURRENT_GPS = saveGPS();
	}
	
	@Override
	public void onPictureTaken(byte[] data, Camera camera)
	{
		//Add GPS properties to image
//		addGpsToImg();
		//Toast.makeText(getBaseContext(), "onPictureTaken", Toast.LENGTH_SHORT).show();
		double[] curGPS = saveGPS();
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
        
        //Toast.makeText(getBaseContext(), pictureFile.getPath(), Toast.LENGTH_LONG).show();
        
        try {
	        ExifInterface exif = new ExifInterface(pictureFile.getPath());
	        addExifToImg(exif, pictureFile.getPath(), curGPS);		// Adds desired exif data to file
        } catch (IOException ioe) { }   
		
		//Must restart preview
		camera.startPreview();
		CAMERA_READY = true;
		if ((DISTANCE == false) && (STREAM_CAPTURE == true))
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
		
		//List<Camera.Size> imgsizes = params.getSupportedPictureSizes();
		//Camera.Size imgselected = imgsizes.get(imgsizes.size()-1) );	// Near full size
		//params.setPictureSize(imgselected.width, imgselected.height);
		params.setPictureSize(3264, 2448);		// Hard-coded image size to Galaxy S3		
		params.setJpegQuality(100);
		
		//addGpsToImg();
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
//			params.setGpsAltitude(currentLocation.getAltitude());
//			params.setGpsLatitude(currentLocation.getLatitude());
//			params.setGpsLongitude(currentLocation.getLongitude());
			params.setGpsTimestamp(currentLocation.getTime()/1000);		// setGpsTimestamp takes seconds, not milliseconds (returned by getTime()
			//params.setGpsProcessingMethod(currentLocation.getProvider());
			//Toast.makeText(getBaseContext(), "Added GPS data", Toast.LENGTH_SHORT).show();
		}		
		mCamera.setParameters(params);
	}
	
	public static void addExifToImg(ExifInterface exif, String mediaString, double[] curGPS)
	{
		if (curGPS == null)
			return;		// Without adding exif tags
		
		double lat, lon, alt, time;
		lat = curGPS[0];
		lon = curGPS[1];
		alt = curGPS[2];
		time = curGPS[3];
		
		// Band-aid
		try {
			String gpsTimeStamp = exif.getAttribute(ExifInterface.TAG_GPS_TIMESTAMP);
			String gpsAltitude = exif.getAttribute(ExifInterface.TAG_GPS_ALTITUDE);
			String gpsAltitudeRef = exif.getAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF);
		} catch (Exception e) { }
//		exif.setAttribute(ExifInterface.TAG_GPS_TIMESTAMP, gpsTimeStamp);
//		exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE, gpsAltitude);
//		exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF, gpsAltitudeRef);
		
		// Manually set for higher resolution
		if (lat < 0)
			exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "S");
		else
			exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "N");
		if (lon < 0)
			exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, "W");
		else
			exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, "E");
		if (alt < 0)
			exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF, "1");
		else
			exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF, "0");
		
		exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, toDegDecimal(lat));
		exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, toDegDecimal(lon));
		String tempAlt = Long.toString(Math.round(Math.abs(alt*10))) + "/10";
		exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE, tempAlt);
		//exif.setAttribute(ExifInterface.TAG_GPS_TIMESTAMP, toSecToUTC((long) time));			
		exif.setAttribute(ExifInterface.TAG_DATETIME, toSecToUTC((long) time));
		
		try {
			  exif.saveAttributes();
			 } catch (IOException e) {
			  // TODO Auto-generated catch block
			  e.printStackTrace();
			 }
	}
	
	public static String toDegDecimal(double coordinate)
	{
		String coord;
		long deg, multiplier = 100000;
		
		deg = Math.round(Math.abs(coordinate)*multiplier);
		coord = Long.toString(deg) + "/" + Long.toString(multiplier) + ",0/1,0/1";
		
		return coord;
	}
	
	public static String toSecToUTC(long milliseconds)		// Seconds from 1970 thingy
	{
		//android.text.format.
		String date;
		date = android.text.format.DateFormat.format("yyyy:MM:dd hh:mm:ss", milliseconds).toString();
		
		return date;
	}
	
	public static double[] saveGPS()		// Save GPS coordinates at the appropriate time
	{
		double[] gps = {currentLocation.getLatitude(), currentLocation.getLongitude(), 
				currentLocation.getAltitude(), (double) currentLocation.getTime()};
		
		return gps;
	}
	
	// Create a File for saving an image or video
	private static File getOutputMediaFile(int type, Context context){
	    // To be safe, you should check that the SDCard is mounted
	    // using Environment.getExternalStorageState() before doing this.

	    File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
	              Environment.DIRECTORY_PICTURES), storeDir);
	    // This location works best if you want the created images to be shared
	    // between applications and persist after your app has been uninstalled.
	    
	    //String temp = "Stored at: " + mediaStorageDir.toString();
	    //Toast.makeText(context,  temp, Toast.LENGTH_SHORT).show();
	    
	    // Create the storage directory if it does not exist
	    if (! mediaStorageDir.exists()){
	        if (! mediaStorageDir.mkdirs()){
	        	Toast.makeText(context, "Couldn't create dir", Toast.LENGTH_SHORT).show();
	            Log.d("ImageCaptureTest", "failed to create directory");
	            return null;
	        }
	    }

	    // Create a media file name
	    String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
	    File mediaFile;
	    if (type == MEDIA_TYPE_IMAGE){
	    	String mediaString = mediaStorageDir.getPath() + File.separator + "IMG_"+ timeStamp + ".jpg";
	        mediaFile = new File(mediaString);
//	        try {
//		        ExifInterface exif = new ExifInterface(mediaString);
//		        addExifToImg(exif, mediaString);		// Adds desired Exif data to file
//	        } catch (IOException ioe) { }        
	    } else if(type == MEDIA_TYPE_VIDEO) {
	    	String mediaString = mediaStorageDir.getPath() + File.separator + "VID_"+ timeStamp + ".mp4";
	        mediaFile = new File(mediaStorageDir.getPath() + File.separator + "VID_"+ timeStamp + ".mp4");
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
	    //previousLocation = currentLocation;
	    //updateGPSDisplay();
	    //Register for updates
	    int minTime = TIME_BETWEEN_GPS;
	    float minDist = 0;
	    locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, minTime, minDist, listener);
	    //Toast.makeText(getBaseContext(), "safeLocationStart", Toast.LENGTH_SHORT).show();
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
	    	//updateGPSDisplay();
	    	//Toast.makeText(getBaseContext(), "onLocationChanged", Toast.LENGTH_SHORT).show();
	    	float[] dist = {0};
	    	float distance = 0;
	    	if ((DISTANCE == true) && (STREAM_CAPTURE == true)) {
	    		//Toast.makeText(getBaseContext(), "Comparing location...", Toast.LENGTH_SHORT).show();
	    		if (FIRST_DISTANCE == true) {
		    		Toast.makeText(getBaseContext(), "First!", Toast.LENGTH_SHORT).show();
	    			//updateGPSDisplay();
		    		previousLocation = currentLocation;
		    		FIRST_DISTANCE = false;
		    		continuousCapture();
		    	} else {	// Makes sure first distance has succeeded
//	    			Location.distanceBetween(previousLocation.getLatitude(),previousLocation.getLongitude(), 
//    						currentLocation.getLatitude(), currentLocation.getLongitude(), dist);
    				//Toast.makeText(getBaseContext(), "Made it", Toast.LENGTH_SHORT).show();
    				distance = currentLocation.distanceTo(previousLocation);
//    			    if (dist[0] > DISTANCE_TO_TRAVEL) {
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
	
	  @Override
	  public final void onAccuracyChanged(Sensor sensor, int accuracy) {
	    // Do something here if sensor accuracy changes.
	  }

	  @Override
	  public final void onSensorChanged(SensorEvent event) {
	    // The light sensor returns a single value.
	    // Many sensors return 3 values, one for each axis.
	    float lux = event.values[0];
	    // Do something with this sensor value.
	  }

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
    
	//Update text view
	private void updateGPSDisplay() {
		if(currentLocation == null) {
			Toast.makeText(getBaseContext(),  "Determining location...", Toast.LENGTH_SHORT).show();
		} else {
			Toast.makeText(getBaseContext(),  String.format("Your Location (LLA):\n%.2f, %.2f, %.2f", currentLocation.getLatitude(),currentLocation.getLongitude(), currentLocation.getAltitude()), Toast.LENGTH_SHORT).show();
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


//////////////// ARCHIVED FUNCTIONS ///////////////
/*	
protected void onActivityResult(int requestCode, int resultCode, Intent data)
{
	if(requestCode == REQUEST_IMAGE && resultCode == Activity.RESULT_OK)
	{
		//Process and display the image
		Bitmap userImage = (Bitmap)data.getExtras().get("data");
		imageView.setImageBitmap(userImage);
	}
}

public void takePicIntent(View view)
{
	Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
	startActivityForResult(intent, REQUEST_IMAGE);
}
*/	

/*
public void dispatchTakePictureIntent(View view) {		// fn(int actionCode)
	int actionCode = 100;		// Temporary hard-code
    Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
   try 
   {
    	File f = createImageFile();
	    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(f));
	    startActivityForResult(takePictureIntent, actionCode);
	    //galleryAddPic();
    } 
    catch (IOException ie)
    {
    	// Do Nothing
    }

}

public static boolean isIntentAvailable(Context context, String action) {
    final PackageManager packageManager = context.getPackageManager();
    final Intent intent = new Intent(action);
    List<ResolveInfo> list =
            packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
    return list.size() > 0;
}
*/	
/*	
private File createImageFile() throws IOException {
    // Create an image file name
    String timeStamp = 
        new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
    String imageFileName = JPEG_FILE_PREFIX + timeStamp;
    File image = File.createTempFile(
        imageFileName, 
        JPEG_FILE_SUFFIX, 
        getAlbumDir()
    );
    mCurrentPhotoPath = image.getAbsolutePath();
    return image;
}

private File getAlbumDir() {
	File storageDir = new File(
		    Environment.getExternalStoragePublicDirectory(
		        Environment.DIRECTORY_PICTURES
		    ), 
		    getAlbumName()
		);
	return storageDir;
}

private String getAlbumName() {
	String albumDir = "ScanImages";
	return albumDir;		// Still need to decide on a reasonable album name
}
*/	
/*	
private void galleryAddPic() {
    Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
    File f = new File(mCurrentPhotoPath);
    Uri contentUri = Uri.fromFile(f);
    mediaScanIntent.setData(contentUri);
    this.sendBroadcast(mediaScanIntent);
}
*/
