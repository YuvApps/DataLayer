/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.example.android.wearable.datalayer;

import static com.example.android.wearable.datalayer.DataLayerListenerService.IMAGE_KEY;
import static com.example.android.wearable.datalayer.DataLayerListenerService.CONFO_PATH;
import static com.example.android.wearable.datalayer.DataLayerListenerService.LOGD;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Vibrator;
import android.support.wearable.view.DotsPageIndicator;
import android.support.wearable.view.FragmentGridPagerAdapter;
import android.support.wearable.view.GridViewPager;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Toast;

import com.example.android.wearable.datalayer.fragments.AssetFragment;
import com.example.android.wearable.datalayer.fragments.DataFragment;
import com.example.android.wearable.datalayer.fragments.DiscoveryFragment;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.CapabilityClient;
import com.google.android.gms.wearable.CapabilityInfo;
import com.google.android.gms.wearable.DataClient;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageClient;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

/**
 * The main activity with a view pager, containing three pages:<p/>
 * <ul>
 * <li>
 * Page 1: shows a list of DataItems received from the phone application
 * </li>
 * <li>
 * Page 2: shows the photo that is sent from the phone application
 * </li>
 * <li>
 * Page 3: includes two buttons to show the connected phone and watch devices
 * </li>
 * </ul>
 */
public class MainActivity extends Activity implements
        DataClient.OnDataChangedListener,
        MessageClient.OnMessageReceivedListener,
        CapabilityClient.OnCapabilityChangedListener {

    private static final String TAG = "MainActivity";
    private static final String CAPABILITY_1_NAME = "capability_1";
    private static final String CAPABILITY_2_NAME = "capability_2";

    private GridViewPager mPager;
    private DataFragment mDataFragment;
    private AssetFragment mAssetFragment;

    public String strCommand = "";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setupViews();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Instantiates clients without member variables, as clients are inexpensive to create and
        // won't lose their listeners. (They are cached and shared between GoogleApi instances.)
        Wearable.getDataClient(this).addListener(this);
        Wearable.getMessageClient(this).addListener(this);
        Wearable.getCapabilityClient(this)
                .addListener(
                        this, Uri.parse("wear://"), CapabilityClient.FILTER_REACHABLE);
    }

    @Override
    protected void onPause() {
        super.onPause();

        Wearable.getDataClient(this).removeListener(this);
        Wearable.getMessageClient(this).removeListener(this);
        Wearable.getCapabilityClient(this).removeListener(this);
    }

    public void onConformation(View view) {

        PutDataMapRequest dataMap = PutDataMapRequest.create(CONFO_PATH);
        dataMap.getDataMap().putAsset(IMAGE_KEY, toAsset());
        dataMap.getDataMap().putLong("time", new Date().getTime());
        dataMap.getDataMap().putBoolean("conformation", true);
        PutDataRequest request = dataMap.asPutDataRequest();
        request.setUrgent();

        moveToPage(1);
        mAssetFragment.setBackgroundImage(BitmapFactory.decodeResource(getResources(), R.mipmap.backgraund_round));

        Task<DataItem> dataItemTask = Wearable.getDataClient(this).putDataItem(request);

        dataItemTask.addOnSuccessListener(new OnSuccessListener<DataItem>() {
            @Override
            public void onSuccess(DataItem dataItem) {
                LOGD(TAG, "Sending image was successful: " + dataItem);
            }
        });
    }

    private static Asset toAsset() {
        ByteArrayOutputStream byteStream = null;
        try {
            byteStream = new ByteArrayOutputStream();
            return Asset.createFromBytes(byteStream.toByteArray());
        } finally {
            if (null != byteStream) {
                try {
                    byteStream.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        LOGD(TAG, "onDataChanged(): " + dataEvents);

        //moveToPage(1);
        //mAssetFragment.setBackgroundImage(BitmapFactory.decodeResource(getResources(), R.mipmap.backgraund_round));

        for (DataEvent event : dataEvents) {
            if (event.getType() == DataEvent.TYPE_CHANGED) {
                String path = event.getDataItem().getUri().getPath();
                DataMapItem dataMapItem = DataMapItem.fromDataItem(event.getDataItem());
                strCommand = dataMapItem.getDataMap().getString("command");
                //if (DataLayerListenerService.IMAGE_PATH.equals(path)) {
                if (strCommand != null) {
                    Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
                    long[] vibrationPattern = {0, 500, 50, 500};
                    long[] vibrationPatternSplit = {0, 600, 200, 600};
                    long[] vibrationPatternUnite = {0, 1000};
                    long[] vibrationPatternRight = {0, 100, 200, 800};
                    long[] vibrationPatternLeft = {0, 800, 200, 100};
                    long[] vibrationPatternForceCheck = {0, 300, 100, 300, 100, 300, 100, 300};
                    //-1 - don't repeat
                    final int indexInPatternToRepeat = -1;

                    Asset photoAsset = dataMapItem.getDataMap()
                            .getAsset(IMAGE_KEY);
                    Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.mipmap.split_round);

                    LOGD(TAG, "Setting background image on second page..");
                    moveToPage(1);

                    switch (strCommand){
                        case "Split": {
                            bitmap = BitmapFactory.decodeResource(getResources(), R.mipmap.split_round);
                            vibrationPattern = vibrationPatternSplit;
                            break;
                        }
                        case "Unite": {
                            bitmap = BitmapFactory.decodeResource(getResources(), R.mipmap.unite_round);
                            vibrationPattern = vibrationPatternUnite;
                            break;
                        }
                        case "Right": {
                            bitmap = BitmapFactory.decodeResource(getResources(), R.mipmap.right_round);
                            vibrationPattern = vibrationPatternRight;
                            break;
                        }
                        case "Left": {
                            bitmap = BitmapFactory.decodeResource(getResources(), R.mipmap.left_round);
                            vibrationPattern = vibrationPatternLeft;
                            break;
                        }
                        case "Force Check": {
                            bitmap = BitmapFactory.decodeResource(getResources(), R.mipmap.force_check_round);
                            vibrationPattern = vibrationPatternForceCheck;
                            break;
                        }
                    }

                    vibrator.vibrate(vibrationPattern, indexInPatternToRepeat);
                    mAssetFragment.setBackgroundImage(bitmap);


                    // Loads image on background thread.
                    new LoadBitmapAsyncTask().execute(photoAsset);
                    //new LoadStringAsyncTask().execute(photoAsset);

                } else if (DataLayerListenerService.COUNT_PATH.equals(path)) {
                    LOGD(TAG, "Data Changed for COUNT_PATH");
                    mDataFragment.appendItem("DataItem Changed", event.getDataItem().toString());
                } else {
                    LOGD(TAG, "Unrecognized path: " + path);
                }

            } else if (event.getType() == DataEvent.TYPE_DELETED) {
                mDataFragment.appendItem("DataItem Deleted", event.getDataItem().toString());
            } else {
                mDataFragment.appendItem("Unknown data event type", "Type = " + event.getType());
            }
        }
    }

    public void onClicked(View view) {
        switch (view.getId()) {
            case R.id.capability_2_btn:
                showNodes(CAPABILITY_2_NAME);
                break;
            case R.id.capabilities_1_and_2_btn:
                showNodes(CAPABILITY_1_NAME, CAPABILITY_2_NAME);
                break;
            default:
                Log.e(TAG, "Unknown click event registered");
        }
    }

    @Override
    public void onMessageReceived(MessageEvent event) {
        LOGD(TAG, "onMessageReceived: " + event);
        mDataFragment.appendItem("Message", event.toString());
    }

    @Override
    public void onCapabilityChanged(CapabilityInfo capabilityInfo) {
        LOGD(TAG, "onCapabilityChanged: " + capabilityInfo);
        mDataFragment.appendItem("onCapabilityChanged", capabilityInfo.toString());
    }

    /**
     * Find the connected nodes that provide at least one of the given capabilities
     */
    private void showNodes(final String... capabilityNames) {

        Task<Map<String, CapabilityInfo>> capabilitiesTask =
                Wearable.getCapabilityClient(this)
                        .getAllCapabilities(CapabilityClient.FILTER_REACHABLE);

        capabilitiesTask.addOnSuccessListener(new OnSuccessListener<Map<String, CapabilityInfo>>() {
            @Override
            public void onSuccess(Map<String, CapabilityInfo> capabilityInfoMap) {
                Set<Node> nodes = new HashSet<>();

                if (capabilityInfoMap.isEmpty()) {
                    showDiscoveredNodes(nodes);
                    return;
                }
                for (String capabilityName : capabilityNames) {
                    CapabilityInfo capabilityInfo = capabilityInfoMap.get(capabilityName);
                    if (capabilityInfo != null) {
                        nodes.addAll(capabilityInfo.getNodes());
                    }
                }
                showDiscoveredNodes(nodes);
            }
        });
    }

    private void showDiscoveredNodes(Set<Node> nodes) {
        List<String> nodesList = new ArrayList<>();
        for (Node node : nodes) {
            nodesList.add(node.getDisplayName());
        }
        LOGD(TAG, "Connected Nodes: " + (nodesList.isEmpty()
                ? "No connected device was found for the given capabilities"
                : TextUtils.join(",", nodesList)));
        String msg;
        if (!nodesList.isEmpty()) {
            msg = getString(R.string.connected_nodes,
                    TextUtils.join(", ", nodesList));
        } else {
            msg = getString(R.string.no_device);
        }
        Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
    }

    private void setupViews() {
        mPager = (GridViewPager) findViewById(R.id.pager);
        mPager.setOffscreenPageCount(2);
        DotsPageIndicator dotsPageIndicator = (DotsPageIndicator) findViewById(R.id.page_indicator);
        dotsPageIndicator.setDotSpacing((int) getResources().getDimension(R.dimen.dots_spacing));
        dotsPageIndicator.setPager(mPager);
        mDataFragment = new DataFragment();
        mAssetFragment = new AssetFragment();
        DiscoveryFragment discoveryFragment = new DiscoveryFragment();
        List<Fragment> pages = new ArrayList<>();
        pages.add(mDataFragment);
        pages.add(mAssetFragment);
        pages.add(discoveryFragment);
        final MyPagerAdapter adapter = new MyPagerAdapter(getFragmentManager(), pages);
        mPager.setAdapter(adapter);
    }

    /**
     * Switches to the page {@code index}. The first page has index 0.
     */
    private void moveToPage(int index) {
        mPager.setCurrentItem(0, index, true);
    }

    private class MyPagerAdapter extends FragmentGridPagerAdapter {

        private List<Fragment> mFragments;

        public MyPagerAdapter(FragmentManager fm, List<Fragment> fragments) {
            super(fm);
            mFragments = fragments;
        }

        @Override
        public int getRowCount() {
            return 1;
        }

        @Override
        public int getColumnCount(int row) {
            return mFragments == null ? 0 : mFragments.size();
        }

        @Override
        public Fragment getFragment(int row, int column) {
            return mFragments.get(column);
        }

    }

    private class LoadStringAsyncTask extends AsyncTask<Asset, Void, String>{

        @Override
        protected String doInBackground(Asset... params) {

            if(params.length > 0) {

                Asset asset = params[0];

                Task<DataClient.GetFdForAssetResponse> getFdForAssetResponseTask =
                        Wearable.getDataClient(getApplicationContext()).getFdForAsset(asset);

                try {
                    // Block on a task and get the result synchronously. This is generally done
                    // when executing a task inside a separately managed background thread. Doing
                    // this on the main (UI) thread can cause your application to become
                    // unresponsive.
                    DataClient.GetFdForAssetResponse getFdForAssetResponse =
                            Tasks.await(getFdForAssetResponseTask);

                    InputStream assetInputStream = getFdForAssetResponse.getInputStream();

                    if (assetInputStream != null) {
                        return assetInputStream.toString();

                    } else {
                        Log.w(TAG, "Requested an unknown Asset.");
                        return null;
                    }

                } catch (ExecutionException exception) {
                    Log.e(TAG, "Failed retrieving asset, Task failed: " + exception);
                    return null;

                } catch (InterruptedException exception) {
                    Log.e(TAG, "Failed retrieving asset, interrupt occurred: " + exception);
                    return null;
                }

            } else {
                Log.e(TAG, "Asset must be non-null");
                return null;
            }
        }

        @Override
        protected void onPostExecute(String string) {

            if (string != null) {
                LOGD(TAG, "Setting background image on second page..");
                moveToPage(1);
                Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_split_l);
                switch (string) {
                    case ("1"):
                        bitmap = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_split_l);
                    case("2"):

                }

                mAssetFragment.setBackgroundImage(bitmap);
            }
        }
    }
    /*
     * Extracts {@link android.graphics.Bitmap} data from the
     * {@link com.google.android.gms.wearable.Asset}
     */
    private class LoadBitmapAsyncTask extends AsyncTask<Asset, Void, Bitmap> {

        @Override
        protected Bitmap doInBackground(Asset... params) {

            if (params.length > 0) {

                Asset asset = params[0];

                Task<DataClient.GetFdForAssetResponse> getFdForAssetResponseTask =
                        Wearable.getDataClient(getApplicationContext()).getFdForAsset(asset);

                try {
                    // Block on a task and get the result synchronously. This is generally done
                    // when executing a task inside a separately managed background thread. Doing
                    // this on the main (UI) thread can cause your application to become
                    // unresponsive.
                    DataClient.GetFdForAssetResponse getFdForAssetResponse =
                Tasks.await(getFdForAssetResponseTask);

        InputStream assetInputStream = getFdForAssetResponse.getInputStream();

                    if (assetInputStream != null) {
            return BitmapFactory.decodeStream(assetInputStream);

        } else {
            Log.w(TAG, "Requested an unknown Asset.");
            return null;
        }

    } catch (ExecutionException exception) {
        Log.e(TAG, "Failed retrieving asset, Task failed: " + exception);
        return null;

    } catch (InterruptedException exception) {
        Log.e(TAG, "Failed retrieving asset, interrupt occurred: " + exception);
        return null;
    }

} else {
        Log.e(TAG, "Asset must be non-null");
        return null;
        }
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {

            if (bitmap != null) {
                LOGD(TAG, "Setting background image on second page..");
                moveToPage(1);
                bitmap = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_split_l);
                //mAssetFragment.setBackgroundImage(bitmap);
            }
        }
    }
}