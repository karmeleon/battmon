package com.karmeleon.battmon;

import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.data.FreezableUtils;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;
import com.manor.currentwidget.library.CurrentReaderFactory;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Created by shawn on 7/13/16.
 */
public class BatteryInfoListenerService extends WearableListenerService {

	private static final String TAG = "Battmon";
	private static final String DATA_ITEM_RECEIVED_PATH = "/battery_info";

	private static final boolean MSG_DEBUG = false;

	private BatteryManager mBatteryManager;

	private GoogleApiClient mGoogleApiClient;

	@Override
	public void onCreate() {
		super.onCreate();
		Log.v(TAG, "Started service");
		mBatteryManager = (BatteryManager) getSystemService(BATTERY_SERVICE);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		Log.v(TAG, "Destroyed service");
	}

	@Override
	public void onMessageReceived(MessageEvent messageEvent) {
		if(MSG_DEBUG)
			Log.d(TAG, "Received message");
		String nodeId = messageEvent.getSourceNodeId();

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
			// Determine the power source
			IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
			Intent batteryStatus = this.registerReceiver(null, ifilter);

			int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
			int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
			batteryInfo.put("capacity", (int)(100 * level / (float)scale));

			batteryInfo.put("source", batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1));
			batteryInfo.put("temperature", batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1));
			if(Build.VERSION.SDK_INT >= 21) {
				//batteryInfo.put("current", mBatteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) / 1000);
				batteryInfo.put("current", Integer.MAX_VALUE);
			} else {
				// older phones don't support the official battery current API, so use CurrentWidget's code to pull it from /sys/ manually
				Long current = CurrentReaderFactory.getValue();
				if(current == null)
					batteryInfo.put("current", Integer.MAX_VALUE);
				else
					batteryInfo.put("current", current);
			}
			batteryInfo.put("voltage", batteryStatus.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1));

		} catch (JSONException e) {
			Log.e(TAG, e.getStackTrace().toString());
		}

		// Send the RPC
		Wearable.MessageApi.sendMessage(mGoogleApiClient, nodeId, DATA_ITEM_RECEIVED_PATH, batteryInfo.toString().getBytes())
				.setResultCallback(new ResultCallback<MessageApi.SendMessageResult>() {
					@Override
					public void onResult(@NonNull MessageApi.SendMessageResult sendMessageResult) {
						if (!sendMessageResult.getStatus().isSuccess()) {
							Log.e(TAG, "Message failed to send");
						} else if(MSG_DEBUG) {
							Log.d(TAG, "Message sent successfully");
						}
					}
				});

	}
}
