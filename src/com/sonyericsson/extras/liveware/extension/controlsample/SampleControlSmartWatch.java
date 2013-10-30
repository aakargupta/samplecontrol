/*
 Copyright (c) 2011, Sony Ericsson Mobile Communications AB

 All rights reserved.

 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions are met:

 * Redistributions of source code must retain the above copyright notice, this
 list of conditions and the following disclaimer.

 * Redistributions in binary form must reproduce the above copyright notice,
 this list of conditions and the following disclaimer in the documentation
 and/or other materials provided with the distribution.

 * Neither the name of the Sony Ericsson Mobile Communications AB nor the names
 of its contributors may be used to endorse or promote products derived from
 this software without specific prior written permission.

 THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.sonyericsson.extras.liveware.extension.controlsample;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.ArrayList;

import com.sonyericsson.extras.liveware.aef.control.Control;
import com.sonyericsson.extras.liveware.aef.sensor.Sensor;


import com.sonyericsson.extras.liveware.extension.util.Dbg;
import com.sonyericsson.extras.liveware.extension.util.control.ControlExtension;
import com.sonyericsson.extras.liveware.extension.util.control.ControlTouchEvent;
import com.sonyericsson.extras.liveware.extension.util.sensor.AccessorySensor;
import com.sonyericsson.extras.liveware.extension.util.sensor.AccessorySensorEvent;
import com.sonyericsson.extras.liveware.extension.util.sensor.AccessorySensorEventListener;
import com.sonyericsson.extras.liveware.extension.util.sensor.AccessorySensorException;
import com.sonyericsson.extras.liveware.extension.util.sensor.AccessorySensorManager;
import com.sonyericsson.extras.liveware.extension.util.sensor.AccessorySensorType;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Handler;
import android.text.TextPaint;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.ViewGroup.LayoutParams;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The sample control for SmartWatch handles the control on the accessory.
 * This class exists in one instance for every supported host application that
 * we have registered to
 */
class SampleControlSmartWatch extends ControlExtension {

	private static final Bitmap.Config BITMAP_CONFIG = Bitmap.Config.RGB_565;

	private static final int ANIMATION_X_POS = 46;

	private static final int ANIMATION_Y_POS = 46;

	private static final int ANIMATION_DELTA_MS = 500;

	private Handler mHandler;

	private boolean mIsShowingAnimation = false;

	private boolean mIsVisible = false;

	//private Animation mAnimation = null;

	private final int width;

	private final int height;

	private Bitmap mCurrentImage = null;

	private static final int NUMBER_TILE_TEXT_SIZE = 14;

	private TextPaint mNumberTextPaint;

	private ArrayList<TilePosition> mTilePositions;

	private ArrayList<GameTile> mGameTiles;

	public Process process;
	public DataOutputStream out;


	private AccessorySensor mSensor = null;

	private boolean accelOn = false;

	float[] estimates = new float[3];

	float[] cumuEsti = new float[3];

	float[] vestimates = new float[3];

	float[] alpha = new float[3];

	float[] beta = new float[3];

	ArrayList<float[]> estiTrend;

	float[] crossed = new float[3];

	//-1 - Below the cross line. 1 - above
	int[] direction = new int[3];

	long swipeTime;

	long startTime;

	boolean first = false;

	long prevTime;

	String str;
	
	int globalI = 0;
	int globalX;
	int inputLimitCount = 0;
	
	boolean newnavigate = false;
	
	int canstop = 1;
	
	boolean tRunning = false;

	Thread t = new Thread();

	/**
	 * Create sample control.
	 *
	 * @param hostAppPackageName Package name of host application.
	 * @param context The context.
	 * @param handler The handler to use
	 */
	SampleControlSmartWatch(final String hostAppPackageName, final Context context,
			Handler handler) {
		super(context, hostAppPackageName);
		if (handler == null) {
			throw new IllegalArgumentException("handler == null");
		}

		mHandler = handler;
		mNumberTextPaint = new TextPaint();
		mNumberTextPaint.setTextSize(NUMBER_TILE_TEXT_SIZE);
		mNumberTextPaint.setTypeface(Typeface.DEFAULT_BOLD);
		mNumberTextPaint.setColor(Color.WHITE);
		mNumberTextPaint.setAntiAlias(true);


		width = getSupportedControlWidth(context);
		height = getSupportedControlHeight(context);

		AccessorySensorManager manager = new AccessorySensorManager(context, hostAppPackageName);
		mSensor = manager.getSensor(Sensor.SENSOR_TYPE_ACCELEROMETER);


	}

	/**
	 * Get supported control width.
	 *
	 * @param context The context.
	 * @return the width.
	 */
	public static int getSupportedControlWidth(Context context) {
		return context.getResources().getDimensionPixelSize(R.dimen.smart_watch_control_width);
	}

	/**
	 * Get supported control height.
	 *
	 * @param context The context.
	 * @return the height.
	 */
	public static int getSupportedControlHeight(Context context) {
		return context.getResources().getDimensionPixelSize(R.dimen.smart_watch_control_height);
	}

	@Override
	public void onDestroy() {

		Log.d(SampleExtensionService.LOG_TAG, "SampleControlSmartWatch onDestroy");
		//stopAnimation();
		mHandler = null;

		// Stop sensor
		if (mSensor != null) {
			mSensor.unregisterListener();
			mSensor = null;
		}

	};

	@Override
	public void onStart() {
		// Nothing to do. Animation is handled in onResume.
		try
		{
			process = Runtime.getRuntime().exec("su");
			out = new DataOutputStream(process.getOutputStream());
		}
		catch(Exception e)
		{
			Dbg.d("XYZY: process ");
			Log.d("Singleton", "XYZY: gfdhf");
		}
	}

	@Override
	public void onStop() {
		// Nothing to do. Animation is handled in onPause.

	}

	@Override
	public void onResume() {
		mIsVisible = true;

		Log.d(SampleExtensionService.LOG_TAG, "Starting animation");

		startNewGame();

		//0.15322891 is the smallest unit of measurement
		estimates[0] = (float)0.15322891;
		estimates[1] = (float)0.15322891;
		estimates[2] = (float)9.65322891;

		vestimates[0] = 0;
		vestimates[1] = 0;
		vestimates[2] = 0;        
		
		newnavigate = false;
		canstop = 1;
		tRunning=false;
		str = "";
		globalI = 0;

		alpha[0] = (float)0.05;
		alpha[1] = (float)0.05;
		alpha[2] = (float)0.05;

		beta[0] = (float)0.001;
		beta[1] = (float)0.001;
		beta[2] = (float)0.001;
		
		//direction[0] = 0;
		//direction[1] = 0;
		//direction[2] = 0;
		globalX = 750;
		inputLimitCount=0;

		first = false;

		estiTrend = new ArrayList<float[]>();


		prevTime = -1;
		setScreenState(Control.Intents.SCREEN_STATE_ON);
		// Animation not showing. Show animation.
		mIsShowingAnimation = true;
		// mAnimation = new Animation();
		//  mAnimation.run();

		// Start listening for sensor updates.
		if (mSensor != null) {
			try {

				mSensor.registerFixedRateListener(mListener, Sensor.SensorRates.SENSOR_DELAY_FASTEST);
			} catch (AccessorySensorException e) {
				Log.d(SampleExtensionService.LOG_TAG, "Failed to register listener");
			}
		}
	}

	private final AccessorySensorEventListener mListener = new AccessorySensorEventListener() {

		public void onSensorEvent(AccessorySensorEvent sensorEvent) {
			processSensor(sensorEvent);
		}		
	};

	private void processSensor(AccessorySensorEvent sensorEvent) {


		// Process the values.
		if (sensorEvent != null && accelOn==true) {

			float[] values = sensorEvent.getSensorValues();

			if (values != null && values.length == 3) {

				setScreenState(Control.Intents.SCREEN_STATE_ON);

				long currTime = sensorEvent.getTimestamp();
				if (prevTime == -1)
				{
					prevTime = currTime;
					estimates[0] = values[0];
					estimates[1] = values[1];
					estimates[2] = values[2];

					cumuEsti[0] = 0;
					cumuEsti[1] = 0;
					cumuEsti[2] = 0;

					swipeTime = currTime;
					startTime = currTime;

					crossed[0] = estimates[0];
					crossed[1] = estimates[1];
					crossed[2] = estimates[2];

					direction[0] = 0;
					direction[1] = 0;
					direction[2] = 0;       	        

				}
				else
				{
					// TextView xView = (TextView)sampleLayout.findViewById(R.id.accelerometer_value_x);
					// TextView yView = (TextView)sampleLayout.findViewById(R.id.accelerometer_value_y);
					// TextView zView = (TextView)sampleLayout.findViewById(R.id.accelerometer_value_z);

					// Show values with one decimal.
					//xView.setText(String.format("%.1f", values[0]));
					//yView.setText(String.format("%.1f", values[1]));
					//zView.setText(String.format("%.1f", values[2]));

					//Log.d("Accel", "ACEL: "+values[0]+ " "+values[1]+" "+values[2]+" "+sensorEvent.getAccuracy());

					if(first==false)
					{
						estimates[0] = values[0];
						estimates[1] = values[1];
						estimates[2] = values[2];

						crossed[0] = estimates[0];
						crossed[1] = estimates[1];
						crossed[2] = estimates[2];
						first = true;
					}


					float[] x = new float[3];
					float[] v = new float[3];
					float[] r = new float[3];

					long dt = currTime-prevTime;
					dt/=1000;
					float[] temp = new float[3];
					for(int i=0;i<3;i++)
					{   temp[i] = estimates[i];     				        				
					x[i] = estimates[i]+dt*vestimates[i];
					v[i] = vestimates[i];
					r[i] = values[i]-x[i];

					x[i] = x[i]+alpha[i]*r[i];
					v[i] = v[i]+beta[i]/dt*r[i];

					estimates[i] = x[i];
					vestimates[i] = v[i];

					Log.d("Accel", "AACEL: "+values[0]+ " "+values[1]+" "+values[2]+" "+estimates[0]+ " "+estimates[1]+" "+estimates[2]+" "+vestimates[0]+ " "+vestimates[1]+" "+vestimates[2]);

					cumuEsti[i] += estimates[i]-temp[i];

					navigate(currTime);
					}

					//estiTrend.add(estimates);        			

				}
			}      
		}		
	}

	private void navigate(long currTime) {
		/*if (Math.abs(cumuEsti[1]) >= 0.31 && (currTime-swipeTime)>200)		
    	{
			int distance = (int)cumuEsti[1]*400;
			if (cumuEsti[1]<0)
			{
				//LEFT
				try
				{
					out.writeBytes("input swipe 0 200 "+distance+" 200\n");					 
					out.flush();
					//process.waitFor();
				}
				catch (Exception e)
				{
					Dbg.d("KKFFF"+e.getMessage());
				}   
			}
			else
			{
				//RIGHT
				try
				{
					out.writeBytes("input swipe "+distance+" 200 0 200\n");				
					out.flush();
					//process.waitFor();
				}
				catch (Exception e)
				{
					Dbg.d("KKFFF"+e.getMessage());
				}   
			}
			cumuEsti[1]=0;
			swipeTime = currTime;
		}*/
		//Log.d("Accel", "AAXEL: start "+startTime);
		if (direction[1] ==0)
		{
			if (estimates[1]<crossed[1]-(float)0.25)		
			{
				newnavigate = true;
				//LEFT
				Log.d("Accel", "AAXEL:  "+startTime);

				try
				{
					/*if(currTime-startTime > 1000)
					{
						out.writeBytes("input swipe 10 200 500 200\n");					 
						out.flush();
						Log.d("Accel", "AAXEL: Left "+crossed[1]);
					}*/

					//crossed[1] = crossed[1]-(float)0.5;
					
					direction[1] = -1;
					if(currTime-startTime > 750)
					{						
						//runThread();
						
						drag(true);
					}
					crossed[1] = estimates[1];
					//process.waitFor();
				}
				catch (Exception e)
				{
					Dbg.d("KKFFF"+e.getMessage());
					Log.d("Accel", "AAXEL: EXC"+e.getMessage());
				}


			}
			else if(estimates[1]>crossed[1]+(float)0.25)		
			{
				newnavigate = true;
				Log.d("Accel", "AAXEL:  "+startTime);
				//Right
				try
				{
					/*if(currTime-startTime > 1000)
					{
						out.writeBytes("input swipe 500 200 10 200\n");					 
						out.flush();
						Log.d("Accel", "AAXEL: Right "+crossed[1]);
					}*/

					//crossed[1] = crossed[1]+(float)0.5;
					
					direction[1] = 1;
					if(currTime-startTime > 750)
					{						
						//runThread();
						drag(true);
					}
					crossed[1] = estimates[1];
					
					//process.waitFor();
				}
				catch (Exception e)
				{
					Dbg.d("KKFFF"+e.getMessage());
					Log.d("Accel", "AAXEL: EXC"+e.getMessage());
				}   			
			}
		}
		else if (direction[1]==-1)
		{
			if (estimates[1]<crossed[1]-(float)0.1)		
			{
				newnavigate = true;
				//LEFT
				try
				{
					/*if(currTime-startTime > 1000)
					{
						out.writeBytes("input swipe 10 200 500 200\n");					 
						out.flush();
						Log.d("Accel", "AAXEL: Left "+crossed[1]);
					}*/

					//crossed[1] = crossed[1]-(float)0.25;
					
					direction[1] = -1;
					if(currTime-startTime > 1000)
					{						
						//runThread();
						drag(false);
					}
					crossed[1] = estimates[1];
					//process.waitFor();
				}
				catch (Exception e)
				{
					Dbg.d("KKFFF"+e.getMessage());
					Log.d("Accel", "AAXEL: EXC"+e.getMessage());
				}


			}
			else if(estimates[1]>crossed[1]+(float)0.1)
			{
				newnavigate = true;
				//RIGHT
				try
				{
					/*if(currTime-startTime > 1000)
					{
						out.writeBytes("input swipe 500 200 10 200\n");				
						out.flush();
						Log.d("Accel", "AAXEL: Right "+crossed[1]);
					}*/

					//crossed[1] = crossed[1];
					direction[1] = 1;
					if(currTime-startTime > 750)
					{						
						//runThread();
						drag(false);
					}
					crossed[1] = estimates[1];
					//process.waitFor();
				}
				catch (Exception e)
				{
					Dbg.d("KKFFF"+e.getMessage());
					Log.d("Accel", "AAXEL: EXC"+e.getMessage());
				}
			}
		}
		else if (direction[1]==1)
		{
			if (estimates[1]>crossed[1]+(float)0.1)		
			{
				newnavigate = true;
				//Right
				try
				{
					/*if(currTime-startTime > 1000)
					{
						out.writeBytes("input swipe 500 200 10 200\n");					 
						out.flush();
						Log.d("Accel", "AAXEL: Right "+crossed[1]);
					}*/

					//crossed[1] = crossed[1]+(float)0.25;
					
					direction[1] = 1;
					if(currTime-startTime > 750)
					{						
						//runThread();
						drag(false);
					}
					crossed[1] = estimates[1];
					//process.waitFor();
				}
				catch (Exception e)
				{
					Dbg.d("KKFFF"+e.getMessage());
					Log.d("Accel", "AAXEL: EXC "+e.getMessage());
				}


			}
			else if(estimates[1]<crossed[1]-(float)0.1)
			{
				newnavigate = true;
				//Left
				try
				{
					/*if(currTime-startTime > 1000)
					{
						out.writeBytes("input swipe 10 200 500 200\n");				
						out.flush();
						Log.d("Accel", "AAXEL: Left "+crossed[1]);
					}*/

					//crossed[1] = crossed[1];
					
					direction[1] = -1;
					if(currTime-startTime > 750)
					{						
						//runThread();
						drag(false);
					}
					crossed[1] = estimates[1];
					//process.waitFor();
				}
				catch (Exception e)
				{
					Dbg.d("KKFFF"+e.getStackTrace());
					Log.d("Accel", "AAXEL: EXC"+e.getMessage());
				}   
			}
		}
	}

	public void runThread()
	{
		//Runs for 500ms or until running is false
		//Traverses 200 pixels

		//t.stop();
		Log.d("Accel", "AXELL:1 ");		
		while(canstop==0)
		{}	
		Log.d("Accel", "AXELL:2 ");
		if (t.isAlive())
		{
			Log.d("Accel", "AXELL:2.5 ");
			t.interrupt();
		}
		Log.d("Accel", "AXELL:3 ");
		while (t.isAlive())
		{}
	
		Log.d("Accel", "AXELL: ");
		t = new Thread() {

			public void run() 
			{
				tRunning = true;
				String s="";
				try
				{
					canstop = 1;
					Log.d("Accel", "NADB: ");
					//startVibrator(50, 0, 1);
					/*try {
				Thread.sleep(6);
			} catch (InterruptedException e) {
				Log.d("Accel", "EXC");
			}*/

					Log.d("Accel", "NADB:2");	

					newnavigate = false;

					int x=750, y=400, k = 1;
					if(direction[1] == 1)				
					{
						x=600;
						k=-1;
					}

					try
					{
						canstop = 0;
						/*if(Thread.interrupted())
							throw new InterruptedException("0");*/
						//out.writeBytes("input swipe 500 200 10 200\n");		
						//out.flush();
						//out.writeBytes("sendevent /dev/input/event2 3 57 1000\nsendevent /dev/input/event2 3 53 400\n");
						String t1 = "sendevent /dev/input/event2 3 57 1000\n";
						String t2 = "sendevent /dev/input/event2 3 53 750\n";						
						String t3 = "sendevent /dev/input/event2 3 54 400\n";
						String t4 = "sendevent /dev/input/event2 3 58 50\n";
						String t5 = "sendevent /dev/input/event2 3 48 5\n";
						String t6 = "sendevent /dev/input/event2 0 0 0\n";
						String t7 = "sendevent /dev/input/event2 3 53 751\n";
						String t8 = "sendevent /dev/input/event2 3 54 400\n";
						String t9 = "sendevent /dev/input/event2 3 58 40\n";
						String t10 = "sendevent /dev/input/event2 0 0 0\n";
						
						/*String t9 = "sendevent /dev/input/event2 0 0 0\n";
						String t10 = "sendevent /dev/input/event2 3 53 404\n";
						String t11 = "sendevent /dev/input/event2 0 0 0\n";*/
						
						//out.writeBytes(t1+t2+t3+t4+t5+t6+t7+t8+t9+t10+t11);
						//String s= "";
						//out.flush();

						s += t1+t2+t3+t4+t5+t6+t7+t8+t9;
						
						if(Thread.interrupted())
							throw new InterruptedException("0");
						
						int x1;
						//x=x+6;
						for(int i=0;i<14;i++)
						{
							if(i==3)
							{
								k=k*12;								
							}
							if(i==10)
							{
								out.writeBytes(s);
								out.flush();
								s= "";
							}
							
							/*if(i==10)
								k=3;
							if(i==15)
								k=4;
							if(i==20)
								k=5;
							if(i==25)
								k=6;
							if(i==25)
								k=7;
							if(i==30)
								k=8;*/
							//Log.d("Accel", "NADB:3");
							/*if(newnavigate)
					{							
						break;
					}*/
							x1 = x+(k*(i+2));
							//int y1 = y+(i%2);
							canstop = 0;
							
							//out.writeBytes("sendevent /dev/input/event2 3 53 "+x1+"\nsendevent /dev/input/event2 0 0 0\n");
							s += "sendevent /dev/input/event2 3 53 "+x1+"\nsendevent /dev/input/event2 0 0 0\n";
							//out.flush();
							
							//out.writeBytes("sendevent /dev/input/event2 3 54 "+y1+"\n");
							//out.flush();
							//out.writeBytes("sendevent /dev/input/event2 0 0 0\n");
							//out.flush();
							canstop = 2;

							if(Thread.interrupted())
							{
								
								throw new InterruptedException("0");
							}
							Log.d("Accel", "ADB: ");
							
							/*long time = System.nanoTime();
							while(System.nanoTime()< time+5000000)
							{
								if(Thread.interrupted())
									throw new InterruptedException("0");
							}*/
							//Thread.sleep(1);
							
							/*try {
						Thread.sleep(5);
					} catch (InterruptedException e) {
						Log.d("Accel", "EXC");
					}*/

						}
						canstop=0;
						s+="sendevent /dev/input/event2 3 57 4294967295\n"+"sendevent /dev/input/event2 0 0 0\n";
						out.writeBytes(s);
						out.flush();
						//canstop = 0;
						//out.writeBytes("sendevent /dev/input/event2 3 57 4294967295\n");
						//out.flush();
						//out.writeBytes("sendevent /dev/input/event2 0 0 0\n");
						//out.flush();
						//canstop = 1;

					}
					catch (IOException e)
					{
						Dbg.d("KKFFF"+e.getMessage());
						Log.d("Accel", "AAXEL: EXC1 "+e.getMessage());						
					} 
				}
				catch(InterruptedException e)
				{
					
					try {
						s+="sendevent /dev/input/event2 3 57 4294967295\n"+"sendevent /dev/input/event2 0 0 0\n";
						out.writeBytes(s);
						out.flush();
						
						//out.writeBytes("sendevent /dev/input/event2 3 57 4294967295\n");
						//out.flush();
						//out.writeBytes("sendevent /dev/input/event2 0 0 0\n");
						//out.flush();
					} catch (IOException e1) {
						// TODO Auto-generated catch block
						Log.d("Accel", "AAXEL: EXC2 "+e.getMessage());
					}
					
				}
				tRunning = false;
				canstop = 1;				
			}
		};
		t.start();

	}
	
	public void drag(boolean dirChanged)
	{
		if(dirChanged)
		{
			globalI =3;
			globalX = 750;
			String s1 = initDrag();		
			sendDrag(s1, (-1*direction[1]));
			
			Log.d("Accel", "AAXEL: dir "+globalX);
		}
		else
		{	
			//Log.d("Accel", "AAXEL: !dir "+globalX);
			if(globalX>1450 || globalX<100)
			{				
				String s1 =initDrag();
				sendDrag(s1, (-1*direction[1]));				
			}
			else
			{
				sendDrag("", (-1*direction[1]));
				if(globalX>1450 || globalX<100)
				{
					String s="sendevent /dev/input/event2 3 57 4294967295\n"+"sendevent /dev/input/event2 0 0 0\n";
					
					try{
						out.writeBytes(s);
						out.flush();
						
					}
					catch(IOException e){
						Log.d("Accel", "EXC: Pau");
					}
				}
			}
			Log.d("Accel", "AAXEL: !dir2 "+globalX);
		}
	}

	public String initDrag()
	{
		//Close Previous drag
		/*String c="sendevent /dev/input/event2 3 57 4294967295\n"+"sendevent /dev/input/event2 0 0 0\n";
		
		try {
			//out.writeBytes("sendevent /dev/input/event2 3 57 4294967295\n");
			//out.flush();
			//out.writeBytes("sendevent /dev/input/event2 0 0 0\n");
			//out.flush();
			out.writeBytes(c);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}*/
		
		String s = "";
		int x=750, y=400, k = 1;
		if(direction[1] == 1)				
		{			
			k=-1;
			
		}
		int x1 = x+k;
		String t1 = "sendevent /dev/input/event2 3 57 1000\n";
		String t2 = "sendevent /dev/input/event2 3 53 750\n";						
		String t3 = "sendevent /dev/input/event2 3 54 400\n";
		String t4 = "sendevent /dev/input/event2 3 58 50\n";
		String t5 = "sendevent /dev/input/event2 3 48 5\n";
		String t6 = "sendevent /dev/input/event2 0 0 0\n";
		String t7 = "sendevent /dev/input/event2 3 53 "+x1+"\n";
		String t8 = "sendevent /dev/input/event2 3 54 400\n";
		String t9 = "sendevent /dev/input/event2 3 58 40\n";
		String t10 = "sendevent /dev/input/event2 0 0 0\n";
		
		s += t1+t2+t3+t4+t5+t6+t7+t8+t9;
		
		for(int i=0;i<2;i++)
		{			
			x1 = x+(k*(i+2));
			s += "sendevent /dev/input/event2 3 53 "+x1+"\nsendevent /dev/input/event2 0 0 0\n";
		}
		//s+="sendevent /dev/input/event2 3 57 4294967295\n"+"sendevent /dev/input/event2 0 0 0\n";
		/*try {
			out.writeBytes(s);
			out.flush();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}*/
		str = s;
		globalX = x;
		return s;
					
	}
	
	public void sendDrag(String st, int k)
	{		
		k = k*30;
		String s=st;
		
		/*if(globalX>1450 || globalX<100)
		{
			/*int ip = inputLimitCount;
			inputLimitCount++;			
			String t0 = "sendevent /dev/input/event2 3 47 "+inputLimitCount+"\n";
			String t1 = "sendevent /dev/input/event2 3 57 1001\n";
			String t2 = "sendevent /dev/input/event2 3 53 750\n";						
			String t3 = "sendevent /dev/input/event2 3 54 400\n";
			String t31 = "sendevent /dev/input/event2 3 58 50\n";
			String t32 = "sendevent /dev/input/event2 3 48 5\n";
			String t4 = "sendevent /dev/input/event2 0 0 0\n";
			String t41 = "sendevent /dev/input/event2 3 53 751\n";
			String t42 = "sendevent /dev/input/event2 0 0 0\n";
			String t5 = "sendevent /dev/input/event2 3 47 "+ip+"\n";
			String t6 = "sendevent /dev/input/event2 3 57 4294967295\n";
			String t7 = "sendevent /dev/input/event2 0 0 0\n";
			String t8 = "sendevent /dev/input/event2 3 47 "+inputLimitCount+"\n";
			s+=t0+t1+t2+t3+t31+t32+t4+t41+t42+t5+t6+t7+t8;
			
			globalX = 750;
			int temp = globalX+k;
			s += "sendevent /dev/input/event2 3 53 "+temp+"\nsendevent /dev/input/event2 3 54 400"+"\nsendevent /dev/input/event2 0 0 0\n";
		}*/
		int x1=globalX,i;
		
		for(i=3; i<6;i++)
		{
			//try {
				x1 = globalX+(k*(i+2));
				s += "sendevent /dev/input/event2 3 53 "+x1+"\nsendevent /dev/input/event2 0 0 0\n";			;
				//out.writeBytes("sendevent /dev/input/event2 3 53 "+x1+"\nsendevent /dev/input/event2 0 0 0\n");
				//out.flush();
			//} catch (IOException e) {
				// TODO Auto-generated catch block
			//	e.printStackTrace();
			//}	
				
		}
		globalI = i;
		globalX = x1;
		//globalX = 750;
		//s+="sendevent /dev/input/event2 3 57 4294967295\n"+"sendevent /dev/input/event2 0 0 0\n";
		
		try {
			//out.writeBytes("sendevent /dev/input/event2 3 57 4294967295\n");
			//out.flush();
			//out.writeBytes("sendevent /dev/input/event2 0 0 0\n");
			//out.flush();
			out.writeBytes(s);
			out.flush();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		str = "";
	}
	
	@Override
	public void onPause() {
		Log.d(SampleExtensionService.LOG_TAG, "Stopping animation");
		mIsVisible = false;

		if (mIsShowingAnimation) {
			stopAnimation();

		}
		String s="sendevent /dev/input/event2 3 57 4294967295\n"+"sendevent /dev/input/event2 0 0 0\n";
		
		try{
			out.writeBytes(s);
			out.flush();
			
		}
		catch(IOException e){
			Log.d("Accel", "EXC: Pau");
		}
		setScreenState(Control.Intents.SCREEN_STATE_AUTO);
		// Stop sensor
		if (mSensor != null) {
			mSensor.unregisterListener();
		}
	}

	private void startNewGame() {
		//drawLoadingScreen();

		// Create game positions
		initTilePositions(new TilePosition(1, new Rect(1, 1, 25, 25)), new TilePosition(2,
				new Rect(26, 1, 50, 25)), new TilePosition(3, new Rect(51, 1, 75, 25)),  new TilePosition(4, new Rect(76, 1, 100, 25)), new TilePosition(5, new Rect(101, 1, 126, 25))
		, new TilePosition(6, new Rect(1, 26, 25, 50)), new TilePosition(7,
				new Rect(26, 26, 50, 50)), new TilePosition(8, new Rect(51, 26, 75, 50)),  new TilePosition(9, new Rect(76, 26, 100, 50)), new TilePosition(10, new Rect(101, 26, 126, 50))
		, new TilePosition(11, new Rect(1, 51, 25, 75)), new TilePosition(12,
				new Rect(26, 51, 50, 75)), new TilePosition(13, new Rect(51, 51, 75, 75)),  new TilePosition(14, new Rect(76, 51, 100, 75)), new TilePosition(15, new Rect(101, 51, 126, 75))
		, new TilePosition(16, new Rect(1, 76, 25, 100)), new TilePosition(17,
				new Rect(26, 76, 50, 100)), new TilePosition(18, new Rect(51, 76, 75, 100)),  new TilePosition(19, new Rect(76, 76, 100, 100)), new TilePosition(20, new Rect(101, 76, 126, 100))
		, new TilePosition(21, new Rect(1, 101, 25, 126)), new TilePosition(22,
				new Rect(26, 101, 50, 126)), new TilePosition(23, new Rect(51, 101, 75, 126)),  new TilePosition(24, new Rect(76, 101, 100, 126)), new TilePosition(25, new Rect(101, 101, 126, 126)));


		mCurrentImage = getNumberImage();


		// Create game tiles
		initTiles();



		// Draw initial game Bitmap
		getCurrentImage(true);

		// Init game state
		// mNumberOfMoves = 0;
		// mGameState = GameState.PLAYING;
		// Dbg.d("game started with empty tile index " + mEmptyTileIndex);
	}


	/**
	 * Init the 9 tile position objects.
	 *
	 * @param tilePositions The tile positions
	 */
	private void initTilePositions(TilePosition... tilePositions) {
		mTilePositions = new ArrayList<TilePosition>(25);
		for (TilePosition tilePosition : tilePositions) {
			mTilePositions.add(tilePosition);
		}
	}

	/**
	 * Get bitmap with number tiles drawn.
	 *
	 * @return The bitmap
	 */
	private Bitmap getNumberImage() {
		Bitmap bitmap = Bitmap.createBitmap(width, height, BITMAP_CONFIG);
		// Set the density to default to avoid scaling.
		bitmap.setDensity(DisplayMetrics.DENSITY_DEFAULT);

		Canvas canvas = new Canvas(bitmap);
		canvas.drawColor(Color.WHITE);

		Paint tilePaint = new Paint();
		tilePaint.setColor(Color.GRAY);
		for (TilePosition tilePosition : mTilePositions) {
			//if (tilePosition.position != 25) {
			canvas.drawRect(tilePosition.frame, tilePaint);
			canvas.drawText(tilePosition.position,
					tilePosition.frame.left + 7, tilePosition.frame.top + 15, mNumberTextPaint);
			//}
		}

		return bitmap;
	}

	/**
	 * Init the 9 tiles with index and bitmap, based on game type.
	 */
	private void initTiles() {
		mGameTiles = new ArrayList<GameTile>(25);
		// Force size to 9
		for (int i = 0; i < 25; i++) {
			mGameTiles.add(new GameTile());
		}

		int i = 1;
		for (TilePosition tp : mTilePositions) {
			GameTile gt = new GameTile();
			if (i != 26) {
				gt.correctPosition = i;

				char[] ls = "0ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
				String r = "";
				while(true) {
					r = ls[i % 26] + r;
					if(i < 26) {
						break;
					}
					i /= 26;
				}

				gt.text = r;
			}

			gt.tilePosition = tp;
			//  if (mGameType == GameType.NUMBERS) {
			setNumberTile(gt);
			//  } else {
			//      setImageTile(mCurrentImage, gt);
			//  }
			mGameTiles.set(i-1, gt);
			i++;
		}
	}

	/**
	 * Create number based bitmap for tile
	 *
	 * @param gt The tile
	 */
	private void setNumberTile(GameTile gt) {
		gt.bitmap = Bitmap.createBitmap(24, 24, BITMAP_CONFIG);
		// Set the density to default to avoid scaling.
		gt.bitmap.setDensity(DisplayMetrics.DENSITY_DEFAULT);

		Canvas canvas = new Canvas(gt.bitmap);
		//if (gt.text != null) {
		canvas.drawColor(Color.GRAY);
		canvas.drawText(gt.text, 7, 15, mNumberTextPaint);
		// } else {
		// Empty tile
		//    canvas.drawColor(Color.WHITE);
		//    mEmptyTileIndex = gt.tilePosition.position;
		//  }
	}

	/**
	 * Draw all tiles into bitmap and show it.
	 *
	 * @param show True if bitmap shown be shown, false otherwise
	 * @return The complete bitmap of the current game
	 */
	private Bitmap getCurrentImage(boolean show) {
		Bitmap bitmap = Bitmap.createBitmap(width, height, BITMAP_CONFIG);
		bitmap.setDensity(DisplayMetrics.DENSITY_DEFAULT);
		Canvas canvas = new Canvas(bitmap);
		// Set background
		canvas.drawColor(Color.BLACK);
		// Draw tiles
		for (GameTile gt : mGameTiles) {
			canvas.drawBitmap(gt.bitmap, gt.tilePosition.frame.left, gt.tilePosition.frame.top,
					null);
		}
		if (show) {
			showBitmap(bitmap);
		}

		return bitmap;
	}

	/**
	 * Stop showing animation on control.
	 */
	public void stopAnimation() {
		// Stop animation on accessory
		// if (mAnimation != null) {
		//  mAnimation.stop();
		//  mHandler.removeCallbacks(mAnimation);
		//   mAnimation = null;
		//  }
		mIsShowingAnimation = false;

		// If the control is visible then stop it
		if (mIsVisible) {
			stopRequest();
		}
	}

	@Override
	public void onTouch(final ControlTouchEvent event) {
		Log.d(SampleExtensionService.LOG_TAG, "onTouch() " + event.getAction());
		if (event.getAction() == Control.Intents.TOUCH_ACTION_RELEASE) {
			int tileReleaseIndex = getTileIndex(event);

			char[] ls = "0ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
			String r = "";
			while(true) {
				r = ls[tileReleaseIndex % 26] + r;
				if(tileReleaseIndex < 26) {
					break;
				}
				tileReleaseIndex /= 26;
			}    

			int keyindex = tileReleaseIndex+28; 
			try
			{
				out.writeBytes("input keyevent "+ keyindex+"\n");
				//out.writeBytes("input tap 500 600\n");


				//out.writeBytes("mv /system/file.old system/file.new\n");
				//out.writeBytes("exit\n");  
				out.flush();
				//process.waitFor();
			}
			catch (Exception e)
			{
				Dbg.d("KKFFF"+e.getMessage());
			}   
		}
	}

	@Override
	public void onSwipe(int direction) {

		switch (direction) {
		case Control.Intents.SWIPE_DIRECTION_LEFT:
			try
			{
				out.writeBytes("input keyevent 67\n");
				//out.writeBytes("input tap 500 600\n");


				//out.writeBytes("mv /system/file.old system/file.new\n");
				//out.writeBytes("exit\n");  
				out.flush();
				//process.waitFor();
			}
			catch (Exception e)
			{
				Dbg.d("KKFFF"+e.getMessage());
			}
		case Control.Intents.SWIPE_DIRECTION_RIGHT:

			try
			{
				out.writeBytes("input keyevent 62\n");
				//out.writeBytes("input tap 500 600\n");


				//out.writeBytes("mv /system/file.old system/file.new\n");
				//out.writeBytes("exit\n");  
				out.flush();
				//process.waitFor();
			}
			catch (Exception e)
			{
				Dbg.d("KKFFF"+e.getMessage());
			}
			break;
		case Control.Intents.SWIPE_DIRECTION_UP:
			if (accelOn == false)
			{
				accelOn = true;
				Log.d("Accel", "ACEL: on");
				//startVibrator(40, 0, 1);

			}

			else
			{
				accelOn = false;
				Log.d("Accel", "ACEL: off");
				//startVibrator(30, 30, 2);
			}
			/*try
			{
				out.writeBytes("input keyevent 62\n");
				//out.writeBytes("input tap 500 600\n");


				//out.writeBytes("mv /system/file.old system/file.new\n");
				//out.writeBytes("exit\n");  
				out.flush();
				//process.waitFor();
			}
			catch (Exception e)
			{
				Dbg.d("KKFFF"+e.getMessage());
			}*/
			break;
		default:
			break;
		}

	}

	/**
	 * Start repeating vibrator
	 *
	 * @param onDuration On duration in milliseconds.
	 * @param offDuration Off duration in milliseconds.
	 * @param repeats The number of repeats of the on/off pattern. Use
	 *            {@link Control.Intents#REPEAT_UNTIL_STOP_INTENT} to repeat
	 *            until explicitly stopped.
	 */
	public void startVibrator(int onDuration, int offDuration, int repeats) {
		if (Dbg.DEBUG) {
			Dbg.v("startVibrator: onDuration: " + onDuration + ", offDuration: " + offDuration
					+ ", repeats: " + repeats);
		}
		Intent intent = new Intent(Control.Intents.CONTROL_VIBRATE_INTENT);
		intent.putExtra(Control.Intents.EXTRA_ON_DURATION, onDuration);
		intent.putExtra(Control.Intents.EXTRA_OFF_DURATION, offDuration);
		intent.putExtra(Control.Intents.EXTRA_REPEATS, repeats);
		sendToHostApp(intent);
	}

	/**
	 * Get tile index for the coordinates in the event.
	 *
	 * @param event The touch event
	 * @return The tile index
	 */
	private int getTileIndex(ControlTouchEvent event) {
		int x = event.getX();
		int y = event.getY();

		//Finger correction
		if (x > 5)
			x = x-5;
		if (y>2)
			y = y-2;
		int rowIndex = x / 25;
		int columnIndex = y / 25;
		return 1 + rowIndex + columnIndex * 5;
	}
	/* *//**
	 * The animation class shows an animation on the accessory. The animation
	 * runs until mHandler.removeCallbacks has been called.
	 *//*
    private class Animation implements Runnable {
        private int mIndex = 1;

        private final Bitmap mBackground;

        private boolean mIsStopped = false;

	  *//**
	  * Create animation.
	  *//*
        Animation() {
            mIndex = 1;

            // Extract the last part of the host application package name.
            String packageName = mHostAppPackageName
                    .substring(mHostAppPackageName.lastIndexOf(".") + 1);

            // Create background bitmap for animation.
            mBackground = Bitmap.createBitmap(width, height, BITMAP_CONFIG);
            // Set default density to avoid scaling.
            mBackground.setDensity(DisplayMetrics.DENSITY_DEFAULT);

            LinearLayout root = new LinearLayout(mContext);
            root.setLayoutParams(new LayoutParams(width, height));

            LinearLayout sampleLayout = (LinearLayout)LinearLayout.inflate(mContext,
                    R.layout.sample_control, root);
            ((TextView)sampleLayout.findViewById(R.id.sample_control_text)).setText(packageName);
            sampleLayout.measure(width, height);
            sampleLayout.layout(0, 0, sampleLayout.getMeasuredWidth(),
                    sampleLayout.getMeasuredHeight());

            Canvas canvas = new Canvas(mBackground);
            sampleLayout.draw(canvas);

            showBitmap(mBackground);
        }

	   *//**
	   * Stop the animation.
	   *//*
        public void stop() {
            mIsStopped = true;
        }

        public void run() {
            int resourceId;
            switch (mIndex) {
                case 1:
                    resourceId = R.drawable.generic_anim_1_icn;
                    break;
                case 2:
                    resourceId = R.drawable.generic_anim_2_icn;
                    break;
                case 3:
                    resourceId = R.drawable.generic_anim_3_icn;
                    break;
                case 4:
                    resourceId = R.drawable.generic_anim_2_icn;
                    break;
                default:
                    Log.e(SampleExtensionService.LOG_TAG, "mIndex out of bounds: " + mIndex);
                    resourceId = R.drawable.generic_anim_1_icn;
                    break;
            }
            mIndex++;
            if (mIndex > 4) {
                mIndex = 1;
            }

            if (!mIsStopped) {
                updateAnimation(resourceId);
            }
            if (mHandler != null && !mIsStopped) {
                mHandler.postDelayed(this, ANIMATION_DELTA_MS);
            }
        }

	    *//**
	    * Update the animation on the accessory. Only updates the part of the
	    * screen which contains the animation.
	    *
	    * @param resourceId The new resource to show.
	    *//*
        private void updateAnimation(int resourceId) {
            Bitmap animation = BitmapFactory.decodeResource(mContext.getResources(), resourceId,
                    mBitmapOptions);

            // Create a bitmap for the part of the screen that needs updating.
            Bitmap bitmap = Bitmap.createBitmap(animation.getWidth(), animation.getHeight(),
                    BITMAP_CONFIG);
            bitmap.setDensity(DisplayMetrics.DENSITY_DEFAULT);
            Canvas canvas = new Canvas(bitmap);
            Paint paint = new Paint();
            Rect src = new Rect(ANIMATION_X_POS, ANIMATION_Y_POS, ANIMATION_X_POS
                    + animation.getWidth(), ANIMATION_Y_POS + animation.getHeight());
            Rect dst = new Rect(0, 0, animation.getWidth(), animation.getHeight());

            // Add first the background and then the animation.
            canvas.drawBitmap(mBackground, src, dst, paint);
            canvas.drawBitmap(animation, 0, 0, paint);

            showBitmap(bitmap, ANIMATION_X_POS, ANIMATION_Y_POS);
        }
    };*/

}
