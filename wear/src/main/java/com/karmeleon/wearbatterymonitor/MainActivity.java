package com.karmeleon.wearbatterymonitor;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.wearable.activity.WearableActivity;
import android.support.wearable.view.WatchViewStub;
import android.util.Log;
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
	private static final int REFRESH_PERIOD = 5000; // ms

	private static final String BATTERY_INFO_CAPABILITY_NAME = "battery_info";
	private static final String BATTERY_INFO_MESSAGE_PATH = "/battery_info";
	private String batteryInfoNodeId = null;

	private GoogleApiClient mGoogleApiClient;
	private BatteryMonitorTask mMonitorTask;

	private boolean mListeningForMessages = false;

	private TextView mTextView;

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
							}
						}
					}
			);
		} else {
			// Unable to retrieve node with battery info capability
		}
	}

	public void onMessageReceived(MessageEvent messageEvent) {
		String data = "";
		try {
			data = new String(messageEvent.getData(), "UTF-8");
		} catch(UnsupportedEncodingException e) { /*this won't happen*/ }

		JSONObject batteryInfo = null;
		try {
			batteryInfo = new JSONObject(data);
		} catch (JSONException e) { /* this won't happen either */ }

		mTextView.setText(data);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		setAmbientEnabled();

		final WatchViewStub stub = (WatchViewStub) findViewById(R.id.watch_view_stub);
		stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
			@Override
			public void onLayoutInflated(WatchViewStub stub) {
				mTextView = (TextView) stub.findViewById(R.id.text);
			}
		});
		new InitBatteryMonitorTask().execute();

	}

	@Override
	protected void onResume() {
		super.onResume();
		// restart the existing monitor task, if one exists
		if(mMonitorTask != null) {
			mMonitorTask = new BatteryMonitorTask();
			mMonitorTask.execute();
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		// stop the monitor task from polling when the app isn't open
		if(mMonitorTask != null)
			mMonitorTask.cancel(true);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if(mListeningForMessages)
			Wearable.MessageApi.removeListener(mGoogleApiClient, this);
	}

	@Override
	public void onEnterAmbient(Bundle ambientDetails) {
		super.onEnterAmbient(ambientDetails);

		mTextView.getPaint().setAntiAlias(false);
		// cancel the rapid refresh rate task
		if(mMonitorTask != null)
			mMonitorTask.cancel(true);
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

		mTextView.getPaint().setAntiAlias(true);
		// restart the refresh task
		mMonitorTask = new BatteryMonitorTask();
		mMonitorTask.execute();
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
			mMonitorTask = new BatteryMonitorTask();
			mMonitorTask.execute();
		}
	}

	private class BatteryMonitorTask extends AsyncTask<Void, Void, Void> {
		protected Void doInBackground(Void... voids) {
			while(true) {
				if(isCancelled())
					return null;
				requestBatteryInfo();
				try {Thread.sleep(REFRESH_PERIOD);} catch(InterruptedException e) {}
			}
		}
	}
}
