package com.karmeleon.wearbatterymonitor;

import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.wearable.view.WatchViewStub;
import android.util.Log;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.CapabilityApi;
import com.google.android.gms.wearable.CapabilityInfo;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;

import java.util.Set;

public class MainActivity extends Activity implements
		GoogleApiClient.ConnectionCallbacks,
		GoogleApiClient.OnConnectionFailedListener {

	private static final String TAG = "BatteryInfo";

	private static final String BATTERY_INFO_CAPABILITY_NAME = "battery_info";
	private String batteryInfoNodeId = null;

	private GoogleApiClient mGoogleApiClient;

	/* Google Play Services stuff */

	protected synchronized void buildGoogleApiClient() {
		this.mGoogleApiClient = new GoogleApiClient.Builder(this)
				.addConnectionCallbacks(this)
				.addApi(Wearable.API)
				.addOnConnectionFailedListener(this)
				.build();
	}

	/*
	 * Called by Location Services when the request to connect the
	 * client finishes successfully. At this point, you can
	 * request the current location or start periodic updates
	 */
	@Override
	public void onConnected(Bundle bundle) {
		Log.v(TAG, "Google API connection successful");
	}

	/*
	 * Called by Location Services if the connection to the
	 * location client drops because of an error.
	 */
	@Override
	public void onConnectionSuspended(int i) {
		Log.w(TAG, "Google API connection suspended");
	}

	/*
	 * Called by Location Services if the attempt to
	 * Location Services fails.
	 */
	@Override
	public void onConnectionFailed(ConnectionResult connectionResult) {
		//mInProgress = false;

		Log.e(TAG, "Google API connection failed");

		/*
		 * Google Play services can resolve some errors it detects.
		 * If the error has a resolution, try sending an Intent to
		 * start a Google Play services activity that can resolve
		 * error.
		 */
		if (connectionResult.hasResolution()) {

			// If no resolution is available, display an error dialog
		} else {

		}
	}

	private void setupBatteryInfo() {
		CapabilityApi.GetCapabilityResult result =
				Wearable.CapabilityApi.getCapability(
						mGoogleApiClient, BATTERY_INFO_CAPABILITY_NAME,
						CapabilityApi.FILTER_REACHABLE).await();

		Log.v(TAG, "Passed await");
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
	}

	private void updateBatteryInfoCapability(CapabilityInfo capabilityInfo) {
		Set<Node> connectedNodes = capabilityInfo.getNodes();

		batteryInfoNodeId = pickBestNodeId(connectedNodes);
	}

	private String pickBestNodeId(Set<Node> nodes) {
		String bestNodeId = null;
		// Find a nearby node or pick one arbitrarily
		for (Node node : nodes) {
			if (node.isNearby()) {
				return node.getId();
			}
			bestNodeId = node.getId();
		}
		return bestNodeId;
	}

	private TextView mTextView;

	public static final String BATTERY_INFO_MESSAGE_PATH = "/battery_info";

	private void requestBatteryInfo() {
		if (batteryInfoNodeId != null) {
			Wearable.MessageApi.sendMessage(mGoogleApiClient, batteryInfoNodeId,
					BATTERY_INFO_MESSAGE_PATH, null).setResultCallback(
					new ResultCallback<MessageApi.SendMessageResult>() {
						@Override
						public void onResult(MessageApi.SendMessageResult sendMessageResult) {
							if (!sendMessageResult.getStatus().isSuccess()) {
								// Failed to send message
							}
						}
					}
			);
		} else {
			// Unable to retrieve node with battery info capability
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		final WatchViewStub stub = (WatchViewStub) findViewById(R.id.watch_view_stub);
		stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
			@Override
			public void onLayoutInflated(WatchViewStub stub) {
				mTextView = (TextView) stub.findViewById(R.id.text);
			}
		});
		new InitBatteryMonitorTask().execute();

	}

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
			new BatteryMonitorTask().execute();
		}
	}

	private class BatteryMonitorTask extends AsyncTask<Void, Integer, Void> {
		protected Void doInBackground(Void... voids) {
			while(true) {
				Log.v(TAG, "Requesting battery info");
				requestBatteryInfo();
				Log.v(TAG, "Got battery percentage 100");
				publishProgress(100);
				try {Thread.sleep(1000);} catch(InterruptedException e) {}
			}
		}

		protected void onProgressUpdate(Integer... integers) {
			mTextView.setText(integers[0].toString());
		}
	}
}
