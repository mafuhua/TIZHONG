/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.yuenkeji.tizhong;

import android.app.Activity;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ExpandableListView;
import android.widget.SimpleExpandableListAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * For a given BLE device, this Activity provides the user interface to connect,
 * display data, and display GATT services and characteristics supported by the
 * device. The Activity communicates with {@code BluetoothLeService}, which in
 * turn interacts with the Bluetooth LE API.
 */
public class DeviceControlActivity extends Activity {
	private final static String TAG = "mafuhua8989";
	public  String binary(byte[] bytes, int radix){
		return new BigInteger(1, bytes).toString(radix);// 这里的1代表正数
	}
	public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
	public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

	private TextView mConnectionState;
	private TextView mDataField;
	private String mDeviceName;
	private String mDeviceAddress;

	
	
	
	private Button button_send_value; // 数据发送按钮
//	private EditText edittext_input_value; // 输入发送的数据
	private TextView textview_return_result; // 返回结果按钮

	private ExpandableListView mGattServicesList;
	private BluetoothLeService mBluetoothLeService;
	private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics = new ArrayList<ArrayList<BluetoothGattCharacteristic>>();
	private boolean mConnected = false;
	private BluetoothGattCharacteristic mNotifyCharacteristic;

	private final String LIST_NAME = "NAME";
	private final String LIST_UUID = "UUID";

	// Code to manage Service lifecycle.
	private final ServiceConnection mServiceConnection = new ServiceConnection() {

		@Override
		public void onServiceConnected(ComponentName componentName,
				IBinder service) {
			mBluetoothLeService = ((BluetoothLeService.LocalBinder) service)
					.getService();
			if (!mBluetoothLeService.initialize()) {
				Log.e(TAG, "Unable to initialize Bluetooth");
				finish();
			}
			// Automatically connects to the device upon successful start-up
			// initialization.
			mBluetoothLeService.connect(mDeviceAddress);
		}

		@Override
		public void onServiceDisconnected(ComponentName componentName) {
			mBluetoothLeService = null;
		}
	};

	// Handles various events fired by the Service.
	// ACTION_GATT_CONNECTED: connected to a GATT server.
	// ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
	// ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
	// ACTION_DATA_AVAILABLE: received data from the device. This can be a
	// result of read
	// or notification operations.
	private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			final String action = intent.getAction();
			if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
				mConnected = true;
				updateConnectionState(R.string.connected);
				invalidateOptionsMenu();
			} else if (BluetoothLeService.ACTION_GATT_DISCONNECTED
					.equals(action)) {
				mConnected = false;
				updateConnectionState(R.string.disconnected);
				invalidateOptionsMenu();
				clearUI();
			} else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED
					.equals(action)) {
				// Show all the supported services and characteristics on the
				// user interface.
				displayGattServices(mBluetoothLeService
						.getSupportedGattServices());
			} else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
				displayData(intent
						.getStringExtra(BluetoothLeService.EXTRA_DATA));
			}
		}
	};

	// If a given GATT characteristic is selected, check for supported features.
	// This sample
	// demonstrates 'Read' and 'Notify' features. See
	// http://d.android.com/reference/android/bluetooth/BluetoothGatt.html for
	// the complete
	// list of supported characteristic features.
	private final ExpandableListView.OnChildClickListener servicesListClickListner = new ExpandableListView.OnChildClickListener() {
		@Override
		public boolean onChildClick(ExpandableListView parent, View v,
				int groupPosition, int childPosition, long id) {
			if (mGattCharacteristics != null) {
				final BluetoothGattCharacteristic characteristic = mGattCharacteristics
						.get(groupPosition).get(childPosition);
				Log.i("mafuhua", groupPosition + "---" + childPosition);
				final int charaProp = characteristic.getProperties();
				if ((charaProp | BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
					// If there is an active notification on a characteristic,
					// clear
					// it first so it doesn't update the data field on the user
					// interface.
					if (mNotifyCharacteristic != null) {
						mBluetoothLeService.setCharacteristicNotification(
								mNotifyCharacteristic, false);
						mNotifyCharacteristic = null;
					}
					mBluetoothLeService.readCharacteristic(characteristic);
				}
				if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
					mNotifyCharacteristic = characteristic;
					mBluetoothLeService.setCharacteristicNotification(
							characteristic, true);
				}
				return true;
			}
			return false;
		}
	};

	private void clearUI() {
		mGattServicesList.setAdapter((SimpleExpandableListAdapter) null);
		
		System.out.println("N0_Data @@@@@@@@@@@@"+R.string.no_data);
		mDataField.setText(R.string.no_data);
	}
    
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.gatt_services_characteristics);

		final Intent intent = getIntent();
		mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
		mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

		// Sets up UI references.
		((TextView) findViewById(R.id.device_address)).setText(mDeviceAddress);
		mGattServicesList = (ExpandableListView) findViewById(R.id.gatt_services_list);
		mGattServicesList.setOnChildClickListener(servicesListClickListner);
		mConnectionState = (TextView) findViewById(R.id.connection_state);
		mDataField = (TextView) findViewById(R.id.data_value);

		button_send_value = (Button) findViewById(R.id.button_send_value);
//		edittext_input_value = (EditText) findViewById(R.id.edittext_input_value);
		textview_return_result = (TextView) findViewById(R.id.textview_return_result);

		button_send_value.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				final BluetoothGattCharacteristic characteristic = mGattCharacteristics
						.get(3).get(3);
				mBluetoothLeService.setCharacteristicNotification(
						characteristic, true);
				mBluetoothLeService.readCharacteristic(characteristic);
				final BluetoothGattCharacteristic characteristic2 = mGattCharacteristics
						.get(3).get(3);
				mBluetoothLeService.setCharacteristicNotification(
						characteristic2, true);
				byte[] bb = new byte[40];
				bb[0] = (byte) 0xFF;
				bb[1] = (byte) 0xA0;
				bb[2] = (byte) 0xFF;
				bb[3] = (byte) 0xA0;
				bb[4] = (byte) 0xFF;
				bb[5] = (byte) 0xA0;
				bb[6] = (byte) 0xFF;
				bb[7] = (byte) 0xA0;
				bb[8] = (byte) 0xFF;
				bb[9] = (byte) 0xA0;
				bb[10] = (byte) 0xFF;
				bb[11] = (byte) 0xA0;
				bb[12] = (byte) 0xFF;
				bb[13] = (byte) 0xA0;
				bb[14] = (byte) 0xFF;
				bb[15] = (byte) 0xA0;
				bb[16] = (byte) 0xFF;
				bb[17] = (byte) 0xA0;
				bb[18] = (byte) 0xFF;
				bb[19] = (byte) 0xA0;
				bb[20] = (byte) 0xFF;
				bb[21] = (byte) 0xA0;
				bb[22] = (byte) 0xFF;
				bb[23] = (byte) 0xA0;
				bb[24] = (byte) 0xFF;
				bb[25] = (byte) 0xA0;
				bb[26] = (byte) 0xFF;
				bb[27] = (byte) 0xA0;
				bb[28] = (byte) 0xFF;
				bb[29] = (byte) 0xA0;
				bb[30] = (byte) 0xFF;
				bb[31] = (byte) 0xA0;
				bb[32] = (byte) 0xFF;
				bb[33] = (byte) 0xA0;
				bb[34] = (byte) 0xFF;
				bb[35] = (byte) 0xA0;
				bb[36] = (byte) 0xFF;
				bb[37] = (byte) 0xA0;
				bb[38] = (byte) 0xFF;
				bb[39] = (byte) 0xA0;
				mBluetoothLeService.writeLlsAlertLevel(2, bb);
//				byte[] bb2 = new byte[1];
//				bb2[0] = 0x03; 
//				mBluetoothLeService.writeLlsAlertLevel(2, bb2);
				Toast.makeText(DeviceControlActivity.this, "数据已经发送出去",
						Toast.LENGTH_SHORT).show();

			}
		});


		Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
		bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
	}

	@Override
	protected void onResume() {
		super.onResume();
		registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
		if (mBluetoothLeService != null) {
			final boolean result = mBluetoothLeService.connect(mDeviceAddress);
			Log.d(TAG, "Connect request result=" + result);
		}
	}
   public void MyonClick(View view){
     switch (view.getId()) {
     //唤醒
	case R.id.button1:
		byte[] bb = new byte[100];
		mBluetoothLeService.writeLlsAlertLevel(2, bb);
//		byte[] bb2 = new byte[1];
//		bb2[0] = 0x03; 
//		mBluetoothLeService.writeLlsAlertLevel(2, bb2);
		Toast.makeText(DeviceControlActivity.this, "数据已经发送出去",
				Toast.LENGTH_SHORT).show();
		break;
	//停止测量	
   case R.id.button2:
	   byte[] bb2 = new byte[40];
		
		mBluetoothLeService.writeLlsAlertLevel(2, bb2);
//		byte[] bb2 = new byte[1];
//		bb2[0] = 0x03; 
//		mBluetoothLeService.writeLlsAlertLevel(2, bb2);
		Toast.makeText(DeviceControlActivity.this, "数据已经发送出去",
				Toast.LENGTH_SHORT).show();
		break;

	}	   
   }
	@Override
	protected void onPause() {
		super.onPause();
		unregisterReceiver(mGattUpdateReceiver);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		unbindService(mServiceConnection);
		mBluetoothLeService = null;
	}





	private void updateConnectionState(final int resourceId) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				System.out.println("ResourceId##########"+resourceId);
				mConnectionState.setText(resourceId);
			}
		});
	}

	private void displayData(String data) {
		if (data != null) {

			//	System.out.println("DATA##########"+data);
		//	Log.d("mafuhua","DATA##########"+ data.getBytes().toString());
			mDataField.setText(data);


			String datas = data.toString();


		//	Log.d("mafuhua", datas+"++++++");

			String replace = datas.replace(" ", "");
			replace = replace+"+++";
			Log.d("mafuhua", replace + "+++");
			String substring = replace.substring(3, 6);
			Log.d("mafuhua", substring + "****");
			Log.d("mafuhua", substring + "****");
			Integer valueOf = Integer.parseInt(substring, 16);
			Log.d("mafuhua", "体重********"+valueOf/10+"KG");


			textview_return_result.setText(data);
		}
	}

	// Demonstrates how to iterate through the supported GATT
	// Services/Characteristics.
	// In this sample, we populate the data structure that is bound to the
	// ExpandableListView
	// on the UI.
	private void displayGattServices(List<BluetoothGattService> gattServices) {
		if (gattServices == null)
			return;
		String uuid = null;
		String unknownServiceString = getResources().getString(
				R.string.unknown_service);
		String unknownCharaString = getResources().getString(
				R.string.unknown_characteristic);
		ArrayList<HashMap<String, String>> gattServiceData = new ArrayList<HashMap<String, String>>();
		ArrayList<ArrayList<HashMap<String, String>>> gattCharacteristicData = new ArrayList<ArrayList<HashMap<String, String>>>();
		mGattCharacteristics = new ArrayList<ArrayList<BluetoothGattCharacteristic>>();

		// Loops through available GATT Services.
		for (BluetoothGattService gattService : gattServices) {
			HashMap<String, String> currentServiceData = new HashMap<String, String>();
			uuid = gattService.getUuid().toString();
			Log.d("mafuhua", "服务------"+uuid);
			currentServiceData.put(LIST_NAME,
					SampleGattAttributes.lookup(uuid, unknownServiceString));
			currentServiceData.put(LIST_UUID, uuid);
			gattServiceData.add(currentServiceData);

			ArrayList<HashMap<String, String>> gattCharacteristicGroupData = new ArrayList<HashMap<String, String>>();
			List<BluetoothGattCharacteristic> gattCharacteristics = gattService
					.getCharacteristics();
			ArrayList<BluetoothGattCharacteristic> charas = new ArrayList<BluetoothGattCharacteristic>();

			// Loops through available Characteristics.
			for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
				charas.add(gattCharacteristic);
				HashMap<String, String> currentCharaData = new HashMap<String, String>();
				uuid = gattCharacteristic.getUuid().toString();
				Log.d("mafuhua", "特征********"+uuid);

				if (uuid.equals("0000fff4-0000-1000-8000-00805f9b34fb")){
					Log.d("mafuhua", "特征----"+uuid);
				}
				currentCharaData.put(LIST_NAME,
						SampleGattAttributes.lookup(uuid, unknownCharaString));
				currentCharaData.put(LIST_UUID, uuid);
				gattCharacteristicGroupData.add(currentCharaData);
			}
			mGattCharacteristics.add(charas);
			gattCharacteristicData.add(gattCharacteristicGroupData);
		}

		SimpleExpandableListAdapter gattServiceAdapter = new SimpleExpandableListAdapter(
				this, gattServiceData,
				android.R.layout.simple_expandable_list_item_2, new String[] {
						LIST_NAME, LIST_UUID }, new int[] { android.R.id.text1,
						android.R.id.text2 }, gattCharacteristicData,
				android.R.layout.simple_expandable_list_item_2, new String[] {
						LIST_NAME, LIST_UUID }, new int[] { android.R.id.text1,
						android.R.id.text2 });
		mGattServicesList.setAdapter(gattServiceAdapter);
	}

	private static IntentFilter makeGattUpdateIntentFilter() {
		final IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
		intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
		intentFilter
				.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
		intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
		return intentFilter;
	}
}
