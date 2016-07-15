package com.karmeleon.wearbatterymonitor;

import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.BatteryManager;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.data.FreezableUtils;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Created by shawn on 7/13/16.
 */
public class BatteryInfoListenerService extends WearableListenerService {

	private static final String TAG = "BatteryInfoListener";
	private static final String DATA_ITEM_RECEIVED_PATH = "/battery_info";

	private final BatteryManager mBatteryManager = (BatteryManager) getSystemService(BATTERY_SERVICE);

	private GoogleApiClient mGoogleApiClient;

	@Override
	public void onMessageReceived(MessageEvent messageEvent) {
		//super.onMessageReceived(messageEvent);

		Toast.makeText(BatteryInfoListenerService.this, "ayy lmao", Toast.LENGTH_SHORT).show();
		String nodeId = messageEvent.getSourceNodeId();
		Log.v(TAG, "Received message from wearable " + nodeId);

		if(mGoogleApiClient == null) {
			mGoogleApiClient = new GoogleApiClient.Builder(this)
					.addApi(Wearable.API)
					.build();

			ConnectionResult connectionResult = mGoogleApiClient.blockingConnect(30, TimeUnit.SECONDS);

			if (!connectionResult.isSuccess()) {
				Log.e(TAG, "Failed to connect to GoogleApiClient.");
				return;
			}
		}

		// Retrieve the battery info from Android

		JSONObject batteryInfo = new JSONObject();
		try {
			batteryInfo.put("capacity", (long) mBatteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY));
		} catch (JSONException e) {
			Log.e(TAG, e.getStackTrace().toString());
		}

		// Send the RPC
		Wearable.MessageApi.sendMessage(mGoogleApiClient, nodeId, DATA_ITEM_RECEIVED_PATH, batteryInfo.toString().getBytes());
		Log.v(TAG, "Sent reply to wearable");
	}
}
