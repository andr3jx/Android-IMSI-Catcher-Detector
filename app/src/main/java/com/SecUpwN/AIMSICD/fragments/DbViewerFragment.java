package com.SecUpwN.AIMSICD.fragments;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Spinner;

import com.SecUpwN.AIMSICD.R;
import com.SecUpwN.AIMSICD.adapters.AIMSICDDbAdapter;
import com.SecUpwN.AIMSICD.adapters.BaseInflaterAdapter;
import com.SecUpwN.AIMSICD.adapters.CardItemData;
import com.SecUpwN.AIMSICD.adapters.CellCardInflater;
import com.SecUpwN.AIMSICD.adapters.DefaultLocationCardInflater;
import com.SecUpwN.AIMSICD.adapters.MeasuredCellStrengthCardData;
import com.SecUpwN.AIMSICD.adapters.MeasuredCellStrengthCardInflater;
import com.SecUpwN.AIMSICD.adapters.OpenCellIdCardInflater;
import com.SecUpwN.AIMSICD.adapters.SilentSmsCardData;
import com.SecUpwN.AIMSICD.adapters.SilentSmsCardInflater;

public class DbViewerFragment extends Fragment {

    private AIMSICDDbAdapter mDb;
    private String mTableSelected;
    private boolean mMadeSelection;
    private Context mContext;

    //Layout items
    private Spinner tblSpinner;
    private ListView lv;
    private View emptyView;

    public DbViewerFragment() {
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mContext = activity.getBaseContext();
        mDb = new AIMSICDDbAdapter(mContext);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.db_view,
                container, false);

        if (view != null) {
            lv = (ListView) view.findViewById(R.id.list_view);
            emptyView = view.findViewById(R.id.db_list_empty);

            tblSpinner = (Spinner) view.findViewById(R.id.table_spinner);
            tblSpinner.setOnItemSelectedListener(new spinnerListener());

            Button loadTable = (Button) view.findViewById(R.id.load_table_data);

            loadTable.setOnClickListener(new btnClick());
        }

        return view;
    }

    private class spinnerListener implements AdapterView.OnItemSelectedListener {

        @Override
        public void onItemSelected(AdapterView<?> parentView, View selectedItemView,
                int position, long id) {
            mTableSelected = String.valueOf(tblSpinner.getSelectedItem());
            mMadeSelection = true;
        }

        @Override
        public void onNothingSelected(AdapterView<?> parentView) {
            mMadeSelection = false;
        }
    }

    private class btnClick implements View.OnClickListener {

        @Override
        public void onClick(final View v) {
            if (mMadeSelection) {
                v.setEnabled(false);
                getActivity().setProgressBarIndeterminateVisibility(true);
                lv.setVisibility(View.GONE);

                new AsyncTask<Void, Void, BaseInflaterAdapter> () {

                    @Override
                    protected BaseInflaterAdapter doInBackground(Void... params) {
                        mDb.open();
                        Cursor result = null;

                        switch (mTableSelected) {
                            // The unique BTSs we have been connected to in the past
                            // EVA: Was "Cell Data" // Table: cellinfo
                            //      ToBe: "DBi_bts"
                            case "Unique BTS Data":
                                result = mDb.getCellData();
                                break;
                            // All BTS measurements we have done since start
                            // EVA: Was "Location Data" // Table: locationinfo
                            //      ToBe: "DBi_measure"
                            case "BTS Measurements":
                                result =  mDb.getLocationData();
                                break;
                            // EVA: Was "OpenCellID Data" // Table: opencellid
                            //      ToBe: "DBe_import"
                            case "Imported OCID Data":
                                result =  mDb.getOpenCellIDData();
                                break;
                            case "Default MCC Locations":
                                result =  mDb.getDefaultMccLocationData();
                                break;
                            case "Silent SMS":
                                result =  mDb.getSilentSmsData();
                                break;
                            //      ToBe merged into "DBi_measure:rx_signal"
                            case "Measured Signal Strengths":
                                result = mDb.getSignalStrengthMeasurementData();

                            // Table: "EventLog"
                            //case "EventLog":
                            //    result = mDb.getEventLogData();

                            // Table: "DetectionFlags"
                            //case "DetectionFlags":
                            //    result = mDb.getDetectionFlagsData();

                        }

                        BaseInflaterAdapter adapter = null;
                        if (result != null) {
                            adapter = BuildTable(result);
                        }
                        mDb.close();

                        return adapter;
                    }

                    @Override
                    protected void onPostExecute(BaseInflaterAdapter adapter) {
                        if (getActivity() == null) return; // fragment detached

                        lv.setEmptyView(emptyView);
                        if (adapter != null) {
                            lv.setAdapter(adapter);
                            lv.setVisibility(View.VISIBLE);
                        } else {
                            lv.setVisibility(View.GONE);
                            emptyView.setVisibility(View.VISIBLE);
                            //Helpers.msgShort(mContext, "Table contains no data to display.");
                        }

                        v.setEnabled(true);
                        getActivity().setProgressBarIndeterminateVisibility(false);
                    }
                }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            }
        }
    }

    // Lat/Lng:     Latitude / Longitude (We should use "Lon" instead of "Lng".)
    // AvgSignal:   Average Signal Strength
    // RSSI:        Received Signal Strength Indicator (previously "Signal Strength")
    //              Can have different meanings on different RAN's, e.g. RSCP in UMTS.
    // RAN:         Radio Access Network (GSM, UMTS, LTE etc.)
    // 2014-12-18 E:V:A
    //  1. Although "RAN" is more correct here, we'll use "RAT" (Radio Access Technology),
    //     which is the more common terminology. Thus reverting.
    //  2. Since Signal is not an "indicator" we should just call it "RSS" or "RXS"
    //
    private BaseInflaterAdapter BuildTable(Cursor tableData) {
        if (tableData != null && tableData.getCount() > 0) {
            switch (mTableSelected) {
                // Was: "OpenCellID Data" New: "Imported OCID Data"
                case "Imported OCID Data": {
                    BaseInflaterAdapter<CardItemData> adapter
                            = new BaseInflaterAdapter<>(
                            new OpenCellIdCardInflater());
                    int count = tableData.getCount();
                    while (tableData.moveToNext()) {
                        CardItemData data = new CardItemData(
                                "CID: " + tableData.getString(0),
                                "LAC: " + tableData.getString(1),
                                "MCC: " + tableData.getString(2),
                                "MNC: " + tableData.getString(3),
                                "Lat: " + tableData.getString(4),
                                "Lon: " + tableData.getString(5),
                                "AvgSignal: " + tableData.getString(6),
                                "Samples: " + tableData.getString(7),
                                "" + (tableData.getPosition() + 1) + " / " + count);
                        adapter.addItem(data, false);
                    }
                    return adapter;
                }
                case "Default MCC Locations": {
                    BaseInflaterAdapter<CardItemData> adapter
                            = new BaseInflaterAdapter<>(
                            new DefaultLocationCardInflater());
                    int count = tableData.getCount();
                    while (tableData.moveToNext()) {
                        CardItemData data = new CardItemData(
                                "Country: " + tableData.getString(0),
                                "MCC: " + tableData.getString(1),
                                "Lat: " + tableData.getString(2),
                                "Lon: " + tableData.getString(3),
                                "" + (tableData.getPosition() + 1) + " / " + count);
                        adapter.addItem(data, false);
                    }
                    return adapter;
                }
                case "Silent SMS": {
                    BaseInflaterAdapter<SilentSmsCardData> adapter
                            = new BaseInflaterAdapter<>(
                            new SilentSmsCardInflater());
                    //int count = tableData.getCount();
                    while (tableData.moveToNext()) {
                        SilentSmsCardData data = new SilentSmsCardData(
                                tableData.getString(0),
                                tableData.getString(1),
                                tableData.getString(2),
                                tableData.getString(3),
                                tableData.getString(4),
                                tableData.getLong(5));
                                //"" + (tableData.getPosition() + 1) + " / " + count);
                        adapter.addItem(data, false);
                    }
                    return adapter;
                }
                // ToDo: merge into "DBi_measure:rx_signal"
                case "Measured Signal Strengths": {
                    BaseInflaterAdapter<MeasuredCellStrengthCardData> adapter
                            = new BaseInflaterAdapter<>(
                            new MeasuredCellStrengthCardInflater());
                    //int count = tableData.getCount();
                    while (tableData.moveToNext()) {
                        MeasuredCellStrengthCardData data = new MeasuredCellStrengthCardData(
                                tableData.getInt(0),
                                tableData.getInt(1),
                                tableData.getLong(2));
                                //"" + (tableData.getPosition() + 1) + " / " + count);
                        adapter.addItem(data, false);
                    }
                    return adapter;
                }
                default: {
                    BaseInflaterAdapter<CardItemData> adapter
                            = new BaseInflaterAdapter<>(
                            new CellCardInflater());
                    int count = tableData.getCount();
                    while (tableData.moveToNext()) {
                        CardItemData data = new CardItemData(
                                "CID: " + tableData.getString(0),
                                "LAC: " + tableData.getString(1),
                                "RAT: " + tableData.getString(2),
                                "Lat: " + tableData.getString(3),
                                "Lon: " + tableData.getString(4),
                                "RSS: " + tableData.getString(5),
                                "" + (tableData.getPosition() + 1) + " / " + count);
                        adapter.addItem(data, false);
                    }
                    return adapter;
                }
            }
        } else {
            return null;
        }
    }
}
