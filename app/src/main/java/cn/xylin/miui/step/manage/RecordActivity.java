package cn.xylin.miui.step.manage;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import cn.xylin.miui.step.manage.util.Final;

public class RecordActivity extends Activity {
    private final String[] QUERY_FILED = {Final.ID, Final.BEGIN_TIME, Final.END_TIME, Final.MODE, Final.STEPS};
    private SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault());
    private SimpleDateFormat stepTimeFormat = new SimpleDateFormat("MM/dd HH:mm:ss", Locale.getDefault());

    private List<ContentValues> list = new ArrayList<>();
    private RecordAdapter recordAdapter;
    private int todayStepCount;
    private TextView todaySteps;
    private SensorManager sensorManager;
    private int mStepDetector = 0;  // 自应用运行以来STEP_DETECTOR检测到的步数
    private int mStepCounter = 0;   // 自系统开机以来STEP_COUNTER检测到的步数
    private MySensorEventListener mySensorEventListener;
    private TextView sensorSteps;
    private TextView recordSteps;
    private TextView titleId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_record);

        todaySteps = findViewById(R.id.today_steps);
        sensorSteps = findViewById(R.id.sensor_steps);
        recordSteps = findViewById(R.id.record_steps);
        titleId = findViewById(R.id.title_id);

        ListView recordListView = findViewById(R.id.recordListView);
        recordAdapter = new RecordAdapter();
        recordListView.setAdapter(recordAdapter);
        getTodayStep();

        recordListView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                if (position > -1) {
                    final ContentValues contentValues = list.get(position);
                    new AlertDialog.Builder(RecordActivity.this)
                            .setTitle("确认删除")
                            .setMessage("ID: " + contentValues.getAsString(Final.ID) + ", 步数: " + contentValues.getAsString(Final.STEPS))
                            .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    deleteOne(contentValues.getAsInteger(Final.ID));
                                    getTodayStep();
                                }
                            })
                            .create()
                            .show();
                }
                return false;
            }
        });

        recordListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                ContentValues contentValues = list.get(position);
                Boolean selected = contentValues.getAsBoolean("selected");
                contentValues.put("selected", !selected);
                recordAdapter.notifyDataSetChanged();
            }
        });

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mySensorEventListener = new MySensorEventListener();
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean b = sensorManager.registerListener(mySensorEventListener, sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR),
                SensorManager.SENSOR_DELAY_FASTEST);
        boolean b1 = sensorManager.registerListener(mySensorEventListener, sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER),
                SensorManager.SENSOR_DELAY_FASTEST);
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(mySensorEventListener);
    }

    public void delete(View view) {
        new AlertDialog.Builder(RecordActivity.this)
                .setTitle("确认删除")
                .setMessage("确定删除已标记的记录吗?")
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        for (ContentValues contentValues : list) {
                            Boolean selected = contentValues.getAsBoolean("selected");
                            if (selected) {
                                deleteOne(contentValues.getAsInteger(Final.ID));
                            }
                        }
                        getTodayStep();
                    }
                })
                .create()
                .show();

    }

    private void deleteOne(int id) {
        int delete = getContentResolver().delete(Final.STEP_URI, "_id=" + id, null);
        if (delete < 1) {
            Toast.makeText(RecordActivity.this, "删除一条失败", Toast.LENGTH_SHORT).show();
        }
    }

    private String getTodayTime(boolean flag) {
        return String.format("%s%s", timeFormat.format(System.currentTimeMillis()).substring(0, 11), flag ? "00:00:00" : "23:59:59");
    }

    private void getTodayStep() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Cursor cursor = getContentResolver().query(Final.STEP_URI, QUERY_FILED, null, null, null);
                    long todayBeginTime = timeFormat.parse(getTodayTime(true)).getTime();
                    long todayEndTime = timeFormat.parse(getTodayTime(false)).getTime();
                    if (cursor != null) {
                        list.clear();
                        todayStepCount = 0;
                        while (cursor.moveToNext()) {
                            ContentValues contentValues = new ContentValues();
                            // 开始时间
                            int beginTimeIndex = cursor.getColumnIndex(Final.BEGIN_TIME);
                            long beginTime = cursor.getLong(beginTimeIndex);
                            contentValues.put(Final.BEGIN_TIME, stepTimeFormat.format(new Date(beginTime)));
                            // 结束时间
                            int endTimeIndex = cursor.getColumnIndex(Final.END_TIME);
                            long endTime = cursor.getLong(endTimeIndex);
                            contentValues.put(Final.END_TIME, stepTimeFormat.format(new Date(endTime)));
                            //步数
                            int stepsIndex = cursor.getColumnIndex(Final.STEPS);
                            String steps = cursor.getString(stepsIndex);
                            contentValues.put(Final.STEPS, steps);
                            //id
                            int idIndex = cursor.getColumnIndex(Final.ID);
                            int id = cursor.getInt(idIndex);
                            contentValues.put(Final.ID, id);
                            //mode
                            int modeIndex = cursor.getColumnIndex(Final.MODE);
                            String mode = cursor.getString(modeIndex);
                            contentValues.put(Final.MODE, mode);

                            contentValues.put("selected", false);

                            //筛选出今天的数据
                            if (cursor.getLong(1) > todayBeginTime && cursor.getLong(2) < todayEndTime) {
                                todayStepCount += cursor.getInt(4);
                                list.add(contentValues);
                            }
                        }
                        cursor.close();
                    }
                    RecordActivity.this.runOnUiThread(new Runnable() {
                        @SuppressLint("SetTextI18n")
                        @Override
                        public void run() {
                            todaySteps.setText("今日步数: " + todayStepCount);
                            titleId.setText("ID " + list.size());
                            recordAdapter.notifyDataSetChanged();
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private class RecordAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return list.size();
        }

        @Override
        public Object getItem(int position) {
            return list.get(position);
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ContentValues contentValues = list.get(position);
            if (convertView == null) {
                convertView = LayoutInflater.from(parent.getContext()).inflate(R.layout.adpter_record, parent, false);
                RecordHolder recordHolder = new RecordHolder(convertView);
                convertView.setTag(recordHolder);
            }
            RecordHolder recordHolder = (RecordHolder) convertView.getTag();
            recordHolder.id.setText(contentValues.getAsString(Final.ID));
            recordHolder.beginTime.setText(contentValues.getAsString(Final.BEGIN_TIME));
            recordHolder.endTime.setText(contentValues.getAsString(Final.END_TIME));
            recordHolder.mode.setText(contentValues.getAsString(Final.MODE));
            recordHolder.steps.setText(contentValues.getAsString(Final.STEPS));
            Boolean selected = contentValues.getAsBoolean("selected");
            if (selected) {
                convertView.setBackgroundColor(Color.parseColor("#50ff0000"));
            } else {
                convertView.setBackgroundColor(Color.WHITE);
            }
            return convertView;
        }

        private class RecordHolder {
            private TextView beginTime;
            private TextView endTime;
            private TextView id;
            private TextView mode;
            private TextView steps;

            RecordHolder(View view) {
                id = view.findViewById(R.id.column_id);
                beginTime = view.findViewById(R.id.column_begin_time);
                endTime = view.findViewById(R.id.column_end_time);
                mode = view.findViewById(R.id.column_mode);
                steps = view.findViewById(R.id.column_steps);
            }
        }
    }


    class MySensorEventListener implements SensorEventListener {
        @SuppressLint("SetTextI18n")
        @Override
        public void onSensorChanged(SensorEvent event) {
            System.out.println("@@@:" + event.sensor.getType() + "--" + Sensor.TYPE_STEP_DETECTOR + "--" + Sensor.TYPE_STEP_COUNTER);
            if (event.sensor.getType() == Sensor.TYPE_STEP_DETECTOR) {
                if (event.values[0] == 1.0f) {
                    mStepDetector++;
                }
            } else if (event.sensor.getType() == Sensor.TYPE_STEP_COUNTER) {
                mStepCounter = (int) event.values[0];
                Log.i("RecordActivity", Arrays.toString(event.values));
            }

            recordSteps.setText("软件: " + mStepDetector);
            sensorSteps.setText("传感器: " + mStepCounter);
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    }


}
