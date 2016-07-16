package com.karmeleon.wearbatterymonitor;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.BatteryManager;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.wearable.activity.WearableActivity;
import android.support.wearable.view.WatchViewStub;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TableLayout;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.CapabilityApi;
import com.google.android.gms.wearable.CapabilityInfo;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.util.Set;

public class MainActivity extends WearableActivity implements MessageApi.MessageListener {

	private static final String TAG = "BatteryInfo";
	private static final int REFRESH_PERIOD = 1000; // ms

	private static final boolean MSG_DEBUG = true;

	private static final String BATTERY_INFO_CAPABILITY_NAME = "battery_info";
	private static final String BATTERY_INFO_MESSAGE_PATH = "/battery_info";
	private String batteryInfoNodeId = null;

	private GoogleApiClient mGoogleApiClient;
	private BatteryMonitorTask mMonitorTask;

	private boolean mListeningForMessages = false;

	private TextView mBatteryPercentageText;
	private TextView mCurrentDisplay;
	private TextView mTemperatureDisplay;
	private TextView mVoltageDisplay;
	private TextView mSourceDisplayText;

	private ImageView mSourceDisplayImage;

	private View mBatteryPercentageMeter;

	private boolean mIsAmbient = false;
	private boolean mHasDrawnScreen = false;

	private JSONObject mLastBatteryInfo;

	/* Messaging setup stuff */

	protected synchronized void buildGoogleApiClient() {
		this.mGoogleApiClient = new GoogleApiClient.Builder(this)
				.addApi(Wearable.API)
				.build();
		mGoogleApiClient.connect();
	}

	private void setupBatteryInfo() {
		CapabilityApi.GetCapabilityResult result =
				Wearable.CapabilityApi.getCapability(
						mGoogleApiClient, BATTERY_INFO_CAPABILITY_NAME,
						CapabilityApi.FILTER_REACHABLE).await();

		updateBatteryInfoCapability(result.getCapability());

		CapabilityApi.CapabilityListener capabilityListener =
				new CapabilityApi.CapabilityListener() {
					@Override
					public void onCapabilityChanged(CapabilityInfo capabilityInfo) {
						updateBatteryInfoCapability(capabilityInfo);
					}
				};

		Wearable.CapabilityApi.addCapabilityListener(
				mGoogleApiClient,
				capabilityListener,
				BATTERY_INFO_CAPABILITY_NAME);

		Wearable.MessageApi.addListener(mGoogleApiClient, this);
		mListeningForMessages = true;
		Log.d(TAG, "Started listening for messages (setupBatteryInfo)");
	}

	private void updateBatteryInfoCapability(CapabilityInfo capabilityInfo) {
		Set<Node> connectedNodes = capabilityInfo.getNodes();

		batteryInfoNodeId = pickBestNodeId(connectedNodes);
	}

	private String pickBestNodeId(Set<Node> nodes) {
		String bestNodeId = null;
		// Find a nearby node or pick one arbitrarily
		for (Node node : nodes) {
			Log.i(TAG, "Chosen " + node.getDisplayName() + " as node");
			if (node.isNearby()) {
				return node.getId();
			}
			bestNodeId = node.getId();
		}
		return bestNodeId;
	}

	/* Actual app logic */

	private void requestBatteryInfo() {
		if (batteryInfoNodeId != null) {
			Wearable.MessageApi.sendMessage(mGoogleApiClient, batteryInfoNodeId,
					BATTERY_INFO_MESSAGE_PATH, "sup".getBytes()).setResultCallback(
					new ResultCallback<MessageApi.SendMessageResult>() {
						@Override
						public void onResult(MessageApi.SendMessageResult sendMessageResult) {
							if (!sendMessageResult.getStatus().isSuccess()) {
								Log.e(TAG, "Send unsuccessful to node " + batteryInfoNodeId);
							} else if(MSG_DEBUG) {
								Log.d(TAG, "Message successfully sent to node " + batteryInfoNodeId);
							}
						}
					}
			);
		} else {
			// Unable to retrieve node with battery info capability
		}
	}

	public void onMessageReceived(MessageEvent messageEvent) {
		if(MSG_DEBUG)
			Log.d(TAG, "Message received");
		String data = "";
		try {
			data = new String(messageEvent.getData(), "UTF-8");
		} catch(UnsupportedEncodingException e) {
			Log.e(TAG, e.getStackTrace().toString());
		}

		JSONObject batteryInfo = null;
		try {
			batteryInfo = new JSONObject(data);
			mLastBatteryInfo = batteryInfo;
		} catch (JSONException e) {
			Log.e(TAG, e.getStackTrace().toString());
		}

		drawScreen(batteryInfo);
	}

	private void drawScreen(JSONObject batteryInfo) {
		try {
			// Update percentage
			int percentage = batteryInfo.getInt("capacity");
			mBatteryPercentageText.setText(percentage + "%");
			mBatteryPercentageMeter.setBackgroundColor(getBatteryColor(percentage));
			mBatteryPercentageMeter.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, percentage));

			// Update source
			int drawableId, textId;
			switch(batteryInfo.getInt("source")) {
				case(BatteryManager.BATTERY_PLUGGED_AC):
					drawableId = R.drawable.ic_power_grey_24dp;
					textId = R.string.ac;
					break;
				case(BatteryManager.BATTERY_PLUGGED_USB):
					drawableId = R.drawable.ic_usb_grey_24dp;
					textId = R.string.usb;
					break;
				case(BatteryManager.BATTERY_PLUGGED_WIRELESS):
					drawableId = R.drawable.ic_nfc_grey_24dp;
					textId = R.string.wireless;
					break;
				default:
					drawableId = R.drawable.ic_battery_grey_24dp;
					textId = R.string.discharging;
			}

			int color = mIsAmbient ? Color.WHITE : Color.LTGRAY;

			Drawable mWrappedDrawable = ContextCompat.getDrawable(this, drawableId).mutate();
			mWrappedDrawable = DrawableCompat.wrap(mWrappedDrawable);
			DrawableCompat.setTint(mWrappedDrawable, color);
			DrawableCompat.setTintMode(mWrappedDrawable, PorterDuff.Mode.SRC_IN);

			mSourceDisplayImage.setImageDrawable(mWrappedDrawable);
			mSourceDisplayText.setText(getResources().getString(textId));

			// Update the other fields
			String current = batteryInfo.getInt("current") + " mA";
			mCurrentDisplay.setText(current);

			String temperature = String.format("%.1f° C", batteryInfo.getInt("temperature") / 10.0f);
			mTemperatureDisplay.setText(temperature);

			String voltage = String.format("%.3f V", batteryInfo.getInt("voltage") / 1000.0f);
			mVoltageDisplay.setText(voltage);

			mHasDrawnScreen = true;

		} catch(JSONException e) {
			Log.e(TAG, e.getStackTrace().toString());
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		final WatchViewStub stub = (WatchViewStub)findViewById(R.id.watch_view_stub);
		stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
			@Override
			public void onLayoutInflated(WatchViewStub watchViewStub) {
				mBatteryPercentageText = (TextView) stub.findViewById(R.id.percent_text);
				mCurrentDisplay = (TextView) stub.findViewById(R.id.current_display);
				mTemperatureDisplay = (TextView) stub.findViewById(R.id.temperature_display);
				mVoltageDisplay = (TextView) stub.findViewById(R.id.voltage_display);
				mSourceDisplayText = (TextView) stub.findViewById(R.id.source_display);

				mSourceDisplayImage = (ImageView) stub.findViewById(R.id.power_src_icon);

				mBatteryPercentageMeter = stub.findViewById(R.id.battery_meter);
			}
		});

		setAmbientEnabled();
		new InitBatteryMonitorTask().execute();

	}

	@Override
	protected void onResume() {
		super.onResume();

		Log.d(TAG, "onResume");

		if(mGoogleApiClient != null && !mListeningForMessages) {
			Wearable.MessageApi.addListener(mGoogleApiClient, this);
			mListeningForMessages = true;
			Log.d(TAG, "Started listening for messages (onResume)");
		}

		// restart the existing monitor task, if one exists
		if(mMonitorTask != null && mMonitorTask.isCancelled()) {
			mMonitorTask = new BatteryMonitorTask();
			mMonitorTask.execute();
		}
	}

	@Override
	protected void onPause() {
		super.onPause();

		Log.d(TAG, "onPause");
		// stop the monitor task from polling when the app isn't open
		if(mMonitorTask != null) {
			mMonitorTask.cancel(true);
			Log.d(TAG, "Stopping monitor task");
		} else
			Log.d(TAG, "Not stopping monitor task " + mMonitorTask.toString());
		if(mListeningForMessages) {
			Wearable.MessageApi.removeListener(mGoogleApiClient, this);
			mListeningForMessages = false;
			Log.d(TAG, "Stopped listening for messages");
		} else
			Log.d(TAG, "Didn't stop listening for messages");
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
	}

	private void ambientifyTextView(TextView tv) {
		tv.getPaint().setAntiAlias(false);
		tv.getPaint().setColor(Color.WHITE);
	}

	private void unambientifyTextView(TextView tv) {
		tv.getPaint().setAntiAlias(true);
		tv.getPaint().setColor(Color.LTGRAY);
	}

	private int getBatteryColor(int percent) {
		int color1, color2;
		float frac = percent / 100.0f;

		if(percent <= 50) {
			color1 = ContextCompat.getColor(this, R.color.battery_low);
			color2 = ContextCompat.getColor(this, R.color.battery_med);
			frac *= 2;
		} else {
			color1 = ContextCompat.getColor(this, R.color.battery_med);
			color2 = ContextCompat.getColor(this, R.color.battery_high);
			frac = (frac - .5f) * 2;
		}

		int r = (int)(Color.red(color2) * frac + Color.red(color1) * (1.0f - frac));
		int g = (int)(Color.green(color2) * frac + Color.green(color1) * (1.0f - frac));
		int b = (int)(Color.blue(color2) * frac + Color.blue(color1) * (1.0f - frac));
		return Color.rgb(r, g, b);
	}

	@Override
	public void onEnterAmbient(Bundle ambientDetails) {
		super.onEnterAmbient(ambientDetails);

		mIsAmbient = true;

		// Android has no way to easily select all views of a type, so we have to do it manually :|
		mBatteryPercentageText.getPaint().setAntiAlias(false);
		ambientifyTextView(mCurrentDisplay);
		ambientifyTextView(mTemperatureDisplay);
		ambientifyTextView(mVoltageDisplay);
		ambientifyTextView(mSourceDisplayText);

		// cancel the rapid refresh rate task
		if(mMonitorTask != null)
			mMonitorTask.cancel(true);

		if(mLastBatteryInfo != null)
			drawScreen(mLastBatteryInfo);
	}

	@Override
	public void onUpdateAmbient() {
		super.onUpdateAmbient();

		// update the battery info once per minute
		requestBatteryInfo();
	}


	@Override
	public void onExitAmbient() {
		super.onExitAmbient();

		mIsAmbient = false;

		// Have to do this manually again

		mBatteryPercentageText.getPaint().setAntiAlias(true);
		unambientifyTextView(mCurrentDisplay);
		unambientifyTextView(mTemperatureDisplay);
		unambientifyTextView(mVoltageDisplay);
		unambientifyTextView(mSourceDisplayText);

		if(mLastBatteryInfo != null)
			drawScreen(mLastBatteryInfo);

		// restart the refresh task
		if(mMonitorTask != null && mMonitorTask.isCancelled()) {
			mMonitorTask = new BatteryMonitorTask();
			mMonitorTask.execute();
		}
	}

	/* Background tasks */

	private class InitBatteryMonitorTask extends AsyncTask<Void, Void, Void> {
		protected Void doInBackground(Void... voids) {
			Log.v(TAG, "Initializing Google API");
			buildGoogleApiClient();
			Log.v(TAG, "Initializing battery info");
			setupBatteryInfo();
			Log.v(TAG, "Finished initialization");
			return null;
		}

		protected void onPostExecute(Void result) {
			Log.v(TAG, "Launching battery poller");
			if(mMonitorTask == null || mMonitorTask.isCancelled()) {
				mMonitorTask = new BatteryMonitorTask();
				mMonitorTask.execute();
			}
		}
	}

	private class BatteryMonitorTask extends AsyncTask<Void, Void, Void> {
		protected Void doInBackground(Void... voids) {
			while(true) {
				if(isCancelled()) {
					Log.d(TAG, "Monitor canceled");
					return null;
				}
				requestBatteryInfo();
				try {Thread.sleep(REFRESH_PERIOD);} catch(InterruptedException e) {return null;}
			}
		}
	}
}
