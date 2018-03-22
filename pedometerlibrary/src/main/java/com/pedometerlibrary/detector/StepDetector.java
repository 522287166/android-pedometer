package com.pedometerlibrary.detector;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.text.TextUtils;
import android.util.Log;

import com.pedometerlibrary.common.PedometerParam;
import com.pedometerlibrary.util.DateUtil;
import com.pedometerlibrary.util.LogUtil;

import java.util.Date;

/**
 * Author: SXF
 * E-mail: xue.com.fei@outlook.com
 * CreatedTime: 2017/12/17 23:02
 * <p>
 * StepDetector
 */

public class StepDetector implements SensorEventListener {
    private static final String TAG = StepDetector.class.getSimpleName();
    private Context context;

    /**
     * 当前App步数
     */
    private int currentAppStep;

    /**
     * 最后一次记录传感器步数
     */
    private int lastSensorStep;

    /**
     * 最后一次步数偏移量
     */
    private int lastOffsetStep;

    /**
     * 最后一次传感器时间
     */
    private long lastSensorTime;

    /**
     * 系统启动时间
     */
    private long systemBootTime;

    /**
     * 系统重新启动状态
     */
    private boolean systemRebootStatus;

    /**
     * 是否重置记录
     */
    private boolean isReset;

    /**
     * 传感器管理
     */
    private SensorManager sensorManager;

    /**
     * 传感器
     */
    private Sensor sensor;

    /**
     * 步测器回调接口
     */
    private StepListener stepListener;

    public StepDetector(Context context) {
        this.context = context;
        this.sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        init();
    }

    /**
     * 初始化参数
     */
    private void init() {
        lastSensorStep = PedometerParam.getLastSensorStep(context);
        lastSensorTime = PedometerParam.getLastSensorTime(context);
        lastOffsetStep = PedometerParam.getLastOffsetStep(context);
        currentAppStep = PedometerParam.getCurrentAppStep(context);
        systemBootTime = PedometerParam.getSystemBootTime(context);
        systemRebootStatus = PedometerParam.getSystemRebootStatus(context);

        if (isResetStep()) {
            currentAppStep = 0;
            lastSensorTime = DateUtil.getSystemTime();
            systemRebootStatus = false;
            PedometerParam.setCurrentAppStep(context, currentAppStep);
            PedometerParam.setLastSensorTime(context, lastSensorTime);
            PedometerParam.setSystemRebootStatus(context, systemRebootStatus);
            LogUtil.e(TAG, "执行重置步数");
        }
    }

    /**
     * 启动步测器（API 步测器）
     */
    public void start() {
        // Sensor.TYPE_STEP_COUNTER 开机被激活后统计步数，重启手机后该数据清空 每次返回统计步数
        Sensor countSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        // Sensor.TYPE_STEP_DETECTOR 临时步数，每次返回1.0
        Sensor detectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
        if (countSensor != null) {
            LogUtil.v(TAG, "Sensor.TYPE_STEP_COUNTER");
            sensor = countSensor;
            boolean isAvailable = sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL);
            if (!isAvailable) {
                LogUtil.v(TAG, "Sensor.TYPE_STEP_COUNTER unavailable");
            }
        } else if (detectorSensor != null) {
            LogUtil.v(TAG, "Sensor.TYPE_STEP_DETECTOR");
            sensor = detectorSensor;
            boolean isAvailable = sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL);
            if (!isAvailable) {
                LogUtil.v(TAG, "Sensor.TYPE_STEP_DETECTOR unavailable");
            }
            if (stepListener != null) {
                stepListener.onStep(currentAppStep);
            }
        } else {
            LogUtil.v(TAG, "The device does not support pedometer sensors");
            if (stepListener != null) {
                stepListener.onNotSupported();
            }
        }
    }

    /**
     * 停止步测器
     */
    public void stop() {
        sensorManager.unregisterListener(this);
    }

    /**
     * 注入步测器回调接口
     *
     * @param stepListener
     */
    public void setStepDetector(StepListener stepListener) {
        this.stepListener = stepListener;
    }

    /**
     * 是否重置步数
     *
     * @return
     */
    private boolean isResetStep() {
        if (DateUtil.differentDays(lastSensorTime, DateUtil.getSystemTime()) > 0) {
            return true;
        }
        if (DateUtil.isMidnightTime(new Date())) {
            return true;
        }
        return false;
    }

    /**
     * 是否重启系统
     *
     * @return
     */
    private boolean isSystemReboot() {
        return systemRebootStatus;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        int sensorType = sensor.getType();
        if (sensorType == Sensor.TYPE_STEP_COUNTER) {
            int tempStep = (int) event.values[0];
            LogUtil.e(TAG, "System Step：" + tempStep);
            if (isResetStep()) {
                currentAppStep = 0;
                lastOffsetStep = tempStep;
                systemBootTime = DateUtil.getSystemBootTime();
                PedometerParam.setLastOffsetStep(context, lastOffsetStep);
                PedometerParam.setCurrentAppStep(context, currentAppStep);
                PedometerParam.setSystemBootTime(context, systemBootTime);
                LogUtil.e(TAG, "执行重置操作");
            }

            if (isSystemReboot()) {
                lastOffsetStep = tempStep - currentAppStep;
                systemRebootStatus = false;
                systemBootTime = DateUtil.getSystemBootTime();
                PedometerParam.setLastOffsetStep(context, lastOffsetStep);
                PedometerParam.setSystemBootTime(context, systemBootTime);
                PedometerParam.setSystemRebootStatus(context, systemRebootStatus);
                LogUtil.e(TAG, "执行重启操作");
            }

            int currentStep = tempStep - lastOffsetStep;
            if (currentStep < currentAppStep) {
                lastOffsetStep = tempStep - currentAppStep;
                PedometerParam.setLastOffsetStep(context, lastOffsetStep);
            } else {
                currentAppStep = currentStep;
            }
            lastSensorStep = tempStep;
            lastSensorTime = DateUtil.getSystemTime();
            if (currentAppStep < 0) {
                currentAppStep = 0;
                lastOffsetStep = currentAppStep;
                PedometerParam.setLastOffsetStep(context, lastOffsetStep);
            }
            PedometerParam.setCurrentAppStep(context, currentAppStep);
            PedometerParam.setLastSensorStep(context, lastSensorStep);
            PedometerParam.setLastSensorTime(context, lastSensorTime);
            if (stepListener != null) {
                stepListener.onStep(currentAppStep);
            }
        } else if (sensorType == Sensor.TYPE_STEP_DETECTOR) {
            if (event.values[0] == 1.0) {
                if (isReset) {
                    isReset = false;
                    lastSensorTime = DateUtil.getSystemTime();
                    currentAppStep = 0;
                }
                currentAppStep++;
                lastSensorTime = DateUtil.getSystemTime();
                PedometerParam.setCurrentAppStep(context, currentAppStep);
                PedometerParam.setLastSensorTime(context, lastSensorTime);
            }
            if (stepListener != null) {
                stepListener.onStep(currentAppStep);
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        Log.v(TAG, "Sensor accuracy:" + accuracy);
    }

    /**
     * 步测器回调接口
     */
    public interface StepListener {

        void onStep(int step);

        void onNotSupported();
    }
}
