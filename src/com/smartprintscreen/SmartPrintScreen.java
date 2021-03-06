package com.smartprintscreen;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.annotation.TargetApi;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.os.FileObserver;
import android.os.Handler;
import android.os.IBinder;
import android.util.Base64;
import android.util.Log;

public class SmartPrintScreen extends Service {
	private String TAG = "SmartPrintScreen";
	private String ClientId = "a964b399e5b6022";
	
	private static String eStorage = Environment.getExternalStorageDirectory().toString();
	private static String sep = File.separator;
	private static FileObserver[] fileObserver;
	private static FileObserver[] fileObserverLvlUp;
	//Folder that is supposed to contain "Screenshots" folder
	private static String[] screenshotsFolder = {
		eStorage,
		eStorage + sep + Environment.DIRECTORY_PICTURES};
	
	boolean removeShots = false;
	
	private Service service;
	public SmartPrintScreen() {
		super();
		Log.i(TAG, "SmartPrintScreen()");
	}
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	@Override
	public void onCreate() {
		saveLog();
        handler = new Handler();
		super.onCreate();
		service = this;
		service.setTheme(android.R.style.Theme_Holo);
		
		loadParameters();
		
		Log.i(TAG, "onCreate");
	}
	@Override
	public void onDestroy() {
		super.onDestroy();
		Log.i(TAG, "onDestroy");
		//I hope it's not that bad
		new Thread() {
			@Override
			public void run() {
				Intent in = new Intent(service, SmartPrintScreen.class);
				Log.i(TAG, "restarting service com.smartprintscreen.SmartPrintScreen");
				startService(in);
			}
		}.start();
	}
	
    @Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.i(TAG, "onStartCommand");
    	service = this;
    	fileObserver = new FileObserver[screenshotsFolder.length];
    	fileObserverLvlUp = new FileObserver[screenshotsFolder.length];
    	for (int i = 0; i < screenshotsFolder.length; i++) {
    		final int j = i;
    		final String ssFolder = screenshotsFolder[i] + sep + "Screenshots";
    		boolean found = false;
		    //check if "Screenshots" even exists, if no then it can be created afterward so we monitor level up folder
    		if (!(new File(ssFolder)).exists()) {
			    Log.i(TAG, ssFolder + " missing, checking level up");
			    if (!(new File(screenshotsFolder[i])).exists()) {
				    Log.i(TAG, screenshotsFolder[i] + " missing");
				    continue;
			    } else {
			    	Log.i(TAG, screenshotsFolder[i]);
			    }
			    fileObserverLvlUp[i] = new FileObserver(screenshotsFolder[i]) {
			        @Override
			        public void onEvent(int event, String path) {
			            if ((event & FileObserver.CREATE) != 0 && path.equalsIgnoreCase("Screenshots")) {
			            	fileObserver[j].startWatching();
					    	Log.i(TAG, "Created " + path + " in " + screenshotsFolder[j] + ", start monitoring");
			            }
			        }
			    };
			    fileObserverLvlUp[i].startWatching();
		    } else {
		    	Log.i(TAG, ssFolder);
		    	found = true;
		    }
		    fileObserver[i] = new FileObserver(ssFolder) {
		        @Override
		        public void onEvent(int event, String path) {
		            if ((event & FileObserver.CLOSE_WRITE) != 0) {
		            	final String screenshotFile = ssFolder + sep + path;
		            	if (!(new File(screenshotFile)).exists()) {
		    			    Log.e(TAG, screenshotFile + " not found");
		    		    	return;
		    		    }
			            Log.i(TAG, screenshotFile);
			            BitmapFactory.Options opt = new BitmapFactory.Options();
			            opt.inDither = true;
			            opt.inPreferredConfig = Bitmap.Config.ARGB_8888;
		            	final Bitmap bitmap = BitmapFactory.decodeFile(screenshotFile, opt);
		            	if (bitmap != null) {
		            		runOnUiThread(new Runnable() {
		    					@Override
		    					public void run() {
		    						new uploadToImgurTask().execute(bitmap, screenshotFile);
		    					}
		    				});
		            	}
		            }
		        }
		    };
		    if (found)
		    	fileObserver[i].startWatching();
    	}
		return START_STICKY;
	}
    
    Handler handler;
    private void runOnUiThread(Runnable runnable) {
        handler.post(runnable);
    }
    
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
	class uploadToImgurTask extends AsyncTask<Object, Void, String[]> {
		@Override
	    protected String[] doInBackground(Object... params) {
	    	try {
				Log.i("getUploadedShotURL", "start");
				ByteArrayOutputStream byteArray = new ByteArrayOutputStream();
				((Bitmap)params[0]).compress(Bitmap.CompressFormat.PNG, 100, byteArray); // Not sure whether this should be jpeg or png, try both and see which works best
				URL url = new URL("https://api.imgur.com/3/image");
			    byte[] byteImage = byteArray.toByteArray();
			    String dataImage = Base64.encodeToString(byteImage, Base64.DEFAULT);
			    String data = URLEncoder.encode("image", "UTF-8") + "=" + URLEncoder.encode(dataImage, "UTF-8");
				
				HttpURLConnection conn = (HttpURLConnection)url.openConnection();
			    conn.setDoOutput(true);
			    conn.setDoInput(true);
			    conn.setRequestMethod("POST");
			    conn.setRequestProperty("Authorization", "Client-ID " + ClientId);
			    
			    conn.connect();
			    OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream());
			    wr.write(data);
			    wr.flush();
			    wr.close();

				InputStream is;
				int response = conn.getResponseCode();
				if (response != HttpURLConnection.HTTP_OK) {
					Log.w("getUploadedShotURL", "bad https response: " + response);
				    is = conn.getErrorStream();
				} else {
				    is = conn.getInputStream();
				}
				
			    BufferedReader rd = new BufferedReader(new InputStreamReader(is));

			    StringBuilder stb = new StringBuilder();
			    String line;
			    while ((line = rd.readLine()) != null) {
			        stb.append(line);
			    }
			    String result = stb.toString();
				Log.d("getUploadedShotURL", "result: " + result);
				if (response != HttpURLConnection.HTTP_OK)
					return null;
			    
			    Pattern reg = Pattern.compile("link\":\"(.*?)\"");
				Log.d("getUploadedShotURL", "reg: " + reg);
				Matcher match = reg.matcher(result);
				Log.d("getUploadedShotURL", "match: " + match);
				Log.i("getUploadedShotURL", "end");
				//our image url
				if (match.find()) {
					String ret[] = {match.group(0).replace("link\":\"", "").replace("\"", "").replace("\\/", "/"), ((String)params[1])};
					return ret;
				}
			} catch (Exception e) {
				Log.e("getUploadedShotURL", e.getMessage());
				e.printStackTrace();
			}
			return null;
	    }
	    @Override
	    protected void onPostExecute(String []params) {
	    	WifiManager wifi = (WifiManager)getSystemService(Context.WIFI_SERVICE);
        	if (params != null) {
        		String url = params[0], screenshotFile = params[1];
        		if (Shared.copyToClipboard(service, url)) {
            		Log.i(TAG, "Screenshot URL copied to clipboard: " + url);
            		Shared.showToast(service, Shared.resStr(service, R.string.copied_to_clipboard_toast) + ":\n" + url);
            		//we reached here, so it got uploaded fine
            		if (removeShots) {
            			File f = new File(screenshotFile);
            			if (f.exists() && !f.isDirectory()) {
            				if (f.delete()) {
            					Log.i(TAG, "local screenshot has been deleted successfully");
            				} else {
            					Log.w(TAG, "local screenshot has failed to get deleted");
            				}
            			}
            		}
            		
            		String[] data = {url};
            		try {
						SaveLoadData.saveData(service, data, "screenshotsURLs", true);
					} catch (IOException e) {
						e.printStackTrace();
					}
        		} else {
            		Log.i(TAG, "Screenshot URL failed to get copied: " + url);
        		}
        	//if wi-fi is enabled then we actually failed
        	} else if (wifi.isWifiEnabled()) {
        		Log.w(TAG, "Failed to upload file");
        	}
	        super.onPostExecute(params);
	    }
	}
	
	private void saveLog() {
		try {
        	String[] command = new String[] {"logcat", "-v", "threadtime", "-f", getExternalFilesDir(null).toString()+"/SmartPrintScreen.log"};
            Runtime.getRuntime().exec(command);
        } catch (Exception e) {
        	Log.e(TAG + " saveLog", "getCurrentProcessLog failed", e);
        }
	}

	private void loadParameters() {
		String[] parameters = null;
		try {
			parameters = SaveLoadData.loadData(this, "parameters");
		} catch (IOException e) {
			e.printStackTrace();
		}
		if (parameters != null && parameters.length > 1)
			removeShots = Boolean.parseBoolean(parameters[1]);
	}
}