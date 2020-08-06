package cn.xylin.miui.step.manage;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import cn.xylin.miui.step.manage.util.Final;
import cn.xylin.miui.step.manage.util.RootTool;
import cn.xylin.miui.step.manage.util.Shared;

/**
 * @author XyLin
 * @date 2019/12/27
 **/
public class MainActivity extends Activity {
    private final String[] QUERY_FILED = {Final.ID, Final.BEGIN_TIME, Final.END_TIME, Final.MODE, Final.STEPS};
    private TextView tvTodaySteps;
    private EditText edtAddSteps;
    private int todayStepCount;
    private long clickTime = 0L;
    private SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.CHINA);
    private SimpleDateFormat timeFormat2 = new SimpleDateFormat("HH:mm:ss", Locale.CHINA);

    private Shared shared;
    private AlertDialog.Builder dialogAppTip;
    private int currentWorkMode;
    private long endTime;
    private long randomStartTime;
    private long randomEndTime;
    private TextView randomSteps;
    private TextView randomStepsTime;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        tvTodaySteps = findViewById(R.id.tvTodaySteps);
        edtAddSteps = findViewById(R.id.edtAddSteps);
        randomSteps = findViewById(R.id.randomSteps);
        randomStepsTime = findViewById(R.id.randomStepsTime);
        Switch swhAutoAddSteps = findViewById(R.id.swhAutoAddSteps);
        dialogAppTip = new AlertDialog.Builder(this)
                .setNegativeButton(R.string.btn_ok, null);
        shared = new Shared(this);
        currentWorkMode = shared.getInt(Shared.KEY_WORK_MODE);
        swhAutoAddSteps.setChecked(shared.getBoolean(Shared.KEY_NEW_DAY_AUTO_ADD));
        if (swhAutoAddSteps.isChecked()) {
            int currentMonthDay = Calendar.getInstance().get(Calendar.DAY_OF_MONTH);
            if (shared.getInt(Shared.KEY_CURRENT_DAY) != currentMonthDay) {
                edtAddSteps.setText(String.valueOf(shared.getInt(Shared.KEY_AUTO_ADD_STEPS)));
                shared.getEdit().putInt(Shared.KEY_CURRENT_DAY, currentMonthDay).editApply();
                findViewById(R.id.btnAddSteps).callOnClick();
                finish();
                return;
            }
        }
        swhAutoAddSteps.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                try {
                    shared.getEdit()
                            .putBoolean(Shared.KEY_NEW_DAY_AUTO_ADD, isChecked)
                            .putInt(Shared.KEY_AUTO_ADD_STEPS, isChecked ? Integer.parseInt(edtAddSteps.getText().toString().trim()) : Final.INTEGER_NULL)
                            .editApply();
                } catch (NumberFormatException ignored) {
                    dialogAppTip.setMessage(R.string.dialog_message_int_parser_error);
                    dialogAppTip.show();
                    compoundButton.setChecked(false);
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        getTodayStep();
    }

    private void getTodayStep() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Cursor cursor = getContentResolver().query(Final.STEP_URI, QUERY_FILED, null, null, null);
                    long todayBeginTime = timeFormat.parse(getTodayTime(true)).getTime();
                    long todayEndTime = timeFormat.parse(getTodayTime(false)).getTime();
                    todayStepCount = 0;
                    endTime = 0;
                    if (cursor != null) {
                        while (cursor.moveToNext()) {
                            if (cursor.getLong(1) > todayBeginTime && cursor.getLong(2) < todayEndTime && cursor.getInt(3) == 2) {
                                todayStepCount += cursor.getInt(4);
                                int endTimeIndex = cursor.getColumnIndex(Final.END_TIME);
                                endTime = cursor.getLong(endTimeIndex);
                            }
                        }
                        cursor.close();
                    }
                    MainActivity.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            tvTodaySteps.setText(String.format(Locale.CHINA, "今日步数：%d, 时间:%s", todayStepCount, timeFormat.format(new Date(endTime))));
                            long dTime = (System.currentTimeMillis() - endTime) / 1000;
                            //1秒1.5步
                            int steps = (int) (dTime * 1.5);
                            edtAddSteps.setHint("建议最大步数" + steps);
                            randomAdd(null);
                        }
                    });
                } catch (Exception ignored) {
                }
            }
        }).start();
    }

    private String getTodayTime(boolean flag) {
        return String.format("%s%s", timeFormat.format(System.currentTimeMillis()).substring(0, 11), flag ? "00:00:00" : "23:59:59");
    }

    private ContentValues getAddStepValues(int type) throws NumberFormatException {
        if (type == 0) {
            int steps = Integer.parseInt(edtAddSteps.getText().toString());
            long second = (long) (steps / 1.5);
            ContentValues values = new ContentValues();
            values.put(Final.BEGIN_TIME, (System.currentTimeMillis() - second * 1000));
            values.put(Final.END_TIME, System.currentTimeMillis());
            values.put(Final.MODE, 2);
            values.put(Final.STEPS, steps);
            return values;
        } else {
            ContentValues values = new ContentValues();
            values.put(Final.BEGIN_TIME, randomStartTime);
            values.put(Final.END_TIME, randomEndTime);
            values.put(Final.MODE, 2);
            values.put(Final.STEPS, randomSteps.getText().toString());
            return values;
        }
    }

    @SuppressLint("SetTextI18n")
    public void randomAdd(View view) {
        if (randomStartTime == 0) {
            randomStartTime = endTime + (long) (100 * Math.random() * 1000);
        }
        if (randomEndTime == 0) {
            randomEndTime = randomStartTime;
        }
        // 每调用一次都会增加一次
        randomEndTime = randomEndTime + (long) (1000 * Math.random() * 1000);
        if (randomStartTime > System.currentTimeMillis()) {
            randomStartTime = System.currentTimeMillis();
        }
        if (randomEndTime > System.currentTimeMillis()) {
            randomEndTime = System.currentTimeMillis();
        }
        int steps = (int) ((randomEndTime - randomStartTime) / 1000 * 1.5);
        randomSteps.setText(steps + "");
        randomStepsTime.setText(timeFormat2.format(new Date(randomStartTime)) + "~" + timeFormat2.format(new Date(randomEndTime)));
    }

    public void resetRandom(View view) {
        randomStartTime = 0;
        randomEndTime = 0;
        randomAdd(null);
    }

    public void startStepAdd(View view) {
        int type = Integer.parseInt((String) view.getTag());
        try {
            switch (currentWorkMode) {
                case Final.WORK_MODE_CORE:
                case Final.WORK_MODE_SYSTEM: {
                    if (currentWorkMode == Final.WORK_MODE_SYSTEM) {
                        if (!RootTool.isSystemApp(this)) {
                            if (!shared.getBoolean(Shared.KEY_TRY_CONVERT_SYSTEM_APP)) {
                                shared.getEdit().putBoolean(Shared.KEY_TRY_CONVERT_SYSTEM_APP, true).editApply();
                                if (!RootTool.convertSystemApp(this)) {
                                    throw new NullPointerException();
                                }
                            }
                            Toast.makeText(this, R.string.convert_system_app_fail, Toast.LENGTH_LONG).show();
                            return;
                        }
                    }
                    getContentResolver().insert(Final.STEP_URI, getAddStepValues(type));
                    break;
                }
                case Final.WORK_MODE_ROOT: {
                    if (!shared.getBoolean(Shared.KEY_UNZIP_SQLITE_FILE)) {
                        InputStream stream = getAssets().open(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ? "sqlite3_21" : "sqlite3");
                        byte[] fileBytes = new byte[stream.available()];
                        stream.read(fileBytes);
                        stream.close();
                        OutputStream outputStream = new FileOutputStream(String.format("%s/sqlite3", getExternalCacheDir()));
                        outputStream.write(fileBytes);
                        outputStream.flush();
                        outputStream.close();
                        RootTool.copySqliteFileToSystem(this);
                        shared.getEdit().putBoolean(Shared.KEY_UNZIP_SQLITE_FILE, true).editApply();
                    }
                    RootTool.addStepsByRootMode(getAddStepValues(type));
                    break;
                }
                default: {
                    return;
                }
            }
            Toast.makeText(this, R.string.toast_add_steps_success, Toast.LENGTH_SHORT).show();
            getTodayStep();
            return;
        } catch (SecurityException ignored) {
            dialogAppTip.setMessage(
                    currentWorkMode == Final.WORK_MODE_CORE ?
                            R.string.add_step_error_core :
                            currentWorkMode == Final.WORK_MODE_ROOT ?
                                    R.string.add_step_error_root :
                                    R.string.add_step_error_system
            );
        } catch (NumberFormatException e) {
            dialogAppTip.setMessage(R.string.dialog_message_int_parser_error);
        } catch (IOException ignored) {
            dialogAppTip.setMessage(R.string.unzip_sqlite_file_fail);
        } catch (NullPointerException ignored) {
            if (currentWorkMode == Final.WORK_MODE_ROOT) {
                dialogAppTip.setMessage(R.string.copy_sqlite_file_fail);
            } else if (currentWorkMode == Final.WORK_MODE_SYSTEM) {
                dialogAppTip.setMessage(R.string.convert_system_app_fail);
            }
        }
        dialogAppTip.show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_menu, menu);
        setWorkModeMenuItemTitle(menu.findItem(R.id.menuSwitchWorkMode));
        if (RootTool.isSystemApp(this)) {
            menu.findItem(R.id.menuUninstallAppByRoot).setVisible(true);
        }
        return super.onCreateOptionsMenu(menu);
    }

    private void setWorkModeMenuItemTitle(MenuItem item) {
        item.setTitle(
                String.format(
                        Final.CURRENT_WORK_MODE,
                        currentWorkMode == Final.WORK_MODE_CORE ?
                                Final.WORK_MODE_NAME_CORE :
                                currentWorkMode == Final.WORK_MODE_ROOT ?
                                        Final.WORK_MODE_NAME_ROOT :
                                        Final.WORK_MODE_NAME_SYSTEM
                )
        );
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_DOWN) {
            if ((System.currentTimeMillis() - clickTime) > 2000L) {
                clickTime = System.currentTimeMillis();
                Toast.makeText(this, R.string.toast_exit_app, Toast.LENGTH_SHORT).show();
                return true;
            }
            android.os.Process.killProcess(android.os.Process.myPid());
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menuSwitchWorkMode: {
                currentWorkMode++;
                currentWorkMode %= 3;
                shared.getEdit().putInt(Shared.KEY_WORK_MODE, currentWorkMode).editApply();
                setWorkModeMenuItemTitle(item);
                return true;
            }
            case R.id.menuUninstallAppByRoot: {
                if (RootTool.isSystemApp(this)) {
                    try {
                        shared.getEdit().putBoolean(Shared.KEY_TRY_CONVERT_SYSTEM_APP, Final.BOOL_NULL).editApply();
                        RootTool.uninstallAppByRoot();
                    } catch (SecurityException e) {
                        dialogAppTip.setMessage(R.string.uninstall_system_app_fail);
                    }
                }
                return true;
            }
            case R.id.menuWorkModeDescription:
            case R.id.menuAboutApp: {
                dialogAppTip.setMessage(item.getItemId() == R.id.menuWorkModeDescription ? R.string.work_mode_description : R.string.about_app);
                dialogAppTip.show();
                return true;
            }
            default: {
                return Final.BOOL_NULL;
            }
        }
    }

    public void checkSteps(View view) {
        startActivity(new Intent(this, RecordActivity.class));
    }
}
