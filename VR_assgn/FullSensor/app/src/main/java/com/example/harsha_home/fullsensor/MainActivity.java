package com.example.harsha_home.fullsensor;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.TextView;

import android.app.Activity;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.widget.RadioGroup;
import android.widget.TextView;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends Activity
        implements SensorEventListener, RadioGroup.OnCheckedChangeListener {

    private SensorManager mSensorManager = null;

    // angular speeds from gyro
    public float[] gyro = new float[3];

    // rotation matrix from gyro data
    private float[] gyroMatrix = new float[9];

    // orientation angles from gyro matrix
    private float[] gyroOrientation = new float[3];

    // magnetic field vector
    private float[] magnet = new float[3];

    // accelerometer vector
    private float[] accel = new float[3];

    // orientation angles from accel and magnet
    public float[] accMagOrientation = new float[3];

    // final orientation angles from sensor fusion
    public float[] fusedOrientation = new float[3];
    public float[] kalmanOrientation = new float[3];

    // accelerometer and magnetometer based rotation matrix
    private float[] rotationMatrix = new float[9];

    public float dT = 0;

    public static final float EPSILON = 0.000000001f;
    private static final float NS2S = 1.0f / 1000000000.0f;
    private long timestamp;
    private boolean initState = true;
    private boolean initState1 = true;
    private boolean initState2 = true;
    private boolean initState3 = true;

    public static final int TIME_CONSTANT = 30;
    public static final float FILTER_COEFFICIENT = 0.98f;
    private Timer fuseTimer = new Timer();
    private Timer kalmanTimer = new Timer();

    // The following members are only for displaying the sensor output.
    public Handler mHandler;
    private RadioGroup mRadioGroup;
    private TextView mAzimuthView;
    private TextView mPitchView;
    private TextView mRollView;
    private int radioSelection;
    DecimalFormat d = new DecimalFormat("#.##");

    public float state0_prev [][]   = new float[2][1];
    public float state0_current [][]   = new float[2][1];
    public float state0_intermediate [][]   = new float[2][1];
    public float state1_prev [][]   = new float[2][1];
    public float state1_current [][]   = new float[2][1];
    public float state1_intermediate [][]   = new float[2][1];
    public float state2_prev [][]   = new float[2][1];
    public float state2_current [][]   = new float[2][1];
    public float state2_intermediate [][]   = new float[2][1];

    public float cov0_prev[][] = new float[2][2];
    public float cov0_current[][] = new float[2][2];
    public float cov0_intermediate[][] = new float[2][2];
    public float cov1_prev[][] = new float[2][2];
    public float cov1_current[][] = new float[2][2];
    public float cov1_intermediate[][] = new float[2][2];
    public float cov2_prev[][] = new float[2][2];
    public float cov2_current[][] = new float[2][2];
    public float cov2_intermediate[][] = new float[2][2];
    public float KalmanGain[][] = new float [2][1];

    public float upc[][] = new float[2][2];
    public float inverse = 0.0f;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        gyroOrientation[0] = 0.0f;
        gyroOrientation[1] = 0.0f;
        gyroOrientation[2] = 0.0f;

        // initialise gyroMatrix with identity matrix
        gyroMatrix[0] = 1.0f; gyroMatrix[1] = 0.0f; gyroMatrix[2] = 0.0f;
        gyroMatrix[3] = 0.0f; gyroMatrix[4] = 1.0f; gyroMatrix[5] = 0.0f;
        gyroMatrix[6] = 0.0f; gyroMatrix[7] = 0.0f; gyroMatrix[8] = 1.0f;

        // get sensorManager and initialise sensor listeners
        mSensorManager = (SensorManager) this.getSystemService(SENSOR_SERVICE);
        initListeners();

        // wait for one second until gyroscope and magnetometer/accelerometer
        // data is initialised then scedule the complementary filter task
        fuseTimer.scheduleAtFixedRate(new calculateFusedOrientationTask(),
                500, TIME_CONSTANT);

        kalmanTimer.scheduleAtFixedRate(new calculateKalmanOrientationTask(),
                500, TIME_CONSTANT);

        // GUI stuff
        mHandler = new Handler();
        radioSelection = 0;
        d.setRoundingMode(RoundingMode.HALF_UP);
        d.setMaximumFractionDigits(3);
        d.setMinimumFractionDigits(3);
        mRadioGroup = (RadioGroup)findViewById(R.id.radioGroup1);
        mAzimuthView = (TextView)findViewById(R.id.textView4);
        mPitchView = (TextView)findViewById(R.id.textView5);
        mRollView = (TextView)findViewById(R.id.textView6);
        mRadioGroup.setOnCheckedChangeListener(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        // unregister sensor listeners to prevent the activity from draining the device's battery.
        mSensorManager.unregisterListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // unregister sensor listeners to prevent the activity from draining the device's battery.
        mSensorManager.unregisterListener(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        // restore the sensor listeners when user resumes the application.
        initListeners();
    }

    // This function registers sensor listeners for the accelerometer, magnetometer and gyroscope.
    public void initListeners(){
        mSensorManager.registerListener(this,
                mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_FASTEST);

        mSensorManager.registerListener(this,
                mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE),
                SensorManager.SENSOR_DELAY_FASTEST);

        mSensorManager.registerListener(this,
                mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD),
                SensorManager.SENSOR_DELAY_FASTEST);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        switch(event.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                // copy new accelerometer data into accel array and calculate orientation
                System.arraycopy(event.values, 0, accel, 0, 3);
                calculateAccMagOrientation();
                break;

            case Sensor.TYPE_GYROSCOPE:
                // process gyro data
                gyroFunction(event);
                break;

            case Sensor.TYPE_MAGNETIC_FIELD:
                // copy new magnetometer data into magnet array
                System.arraycopy(event.values, 0, magnet, 0, 3);
                break;
        }



    }

    // calculates orientation angles from accelerometer and magnetometer output
    public void calculateAccMagOrientation() {
        if(SensorManager.getRotationMatrix(rotationMatrix, null, accel, magnet)) {
            SensorManager.getOrientation(rotationMatrix, accMagOrientation);
        }
    }

    // This function is borrowed from the Android reference
    // at http://developer.android.com/reference/android/hardware/SensorEvent.html#values
    // It calculates a rotation vector from the gyroscope angular speed values.
    private void getRotationVectorFromGyro(float[] gyroValues,
                                           float[] deltaRotationVector,
                                           float timeFactor)
    {
        float[] normValues = new float[3];

        // Calculate the angular speed of the sample
        float omegaMagnitude =
                (float)Math.sqrt(gyroValues[0] * gyroValues[0] +
                        gyroValues[1] * gyroValues[1] +
                        gyroValues[2] * gyroValues[2]);

        // Normalize the rotation vector if it's big enough to get the axis
        if(omegaMagnitude > EPSILON) {
            normValues[0] = gyroValues[0] / omegaMagnitude;
            normValues[1] = gyroValues[1] / omegaMagnitude;
            normValues[2] = gyroValues[2] / omegaMagnitude;
        }

        // Integrate around this axis with the angular speed by the timestep
        // in order to get a delta rotation from this sample over the timestep
        // We will convert this axis-angle representation of the delta rotation
        // into a quaternion before turning it into the rotation matrix.
        float thetaOverTwo = omegaMagnitude * timeFactor;
        float sinThetaOverTwo = (float)Math.sin(thetaOverTwo);
        float cosThetaOverTwo = (float)Math.cos(thetaOverTwo);
        deltaRotationVector[0] = sinThetaOverTwo * normValues[0];
        deltaRotationVector[1] = sinThetaOverTwo * normValues[1];
        deltaRotationVector[2] = sinThetaOverTwo * normValues[2];
        deltaRotationVector[3] = cosThetaOverTwo;
    }

    // This function performs the integration of the gyroscope data.
    // It writes the gyroscope based orientation into gyroOrientation.

    public void gyroFunction(SensorEvent event) {
        // don't start until first accelerometer/magnetometer orientation has been acquired
        if (accMagOrientation == null)
            return;

        // initialisation of the gyroscope based rotation matrix
        if(initState) {
            float[] initMatrix = new float[9];
            initMatrix = getRotationMatrixFromOrientation(accMagOrientation);
            float[] test = new float[3];
            SensorManager.getOrientation(initMatrix, test);
            gyroMatrix = matrixMultiplication(gyroMatrix, initMatrix);
            initState = false;
        }

        // copy the new gyro values into the gyro array
        // convert the raw gyro data into a rotation vector
        float[] deltaVector = new float[4];
        if(timestamp != 0) {
            dT = (event.timestamp - timestamp) * NS2S;
            System.arraycopy(event.values, 0, gyro, 0, 3);
            getRotationVectorFromGyro(gyro, deltaVector, dT / 2.0f);
        }

        // measurement done, save current time for next interval
        timestamp = event.timestamp;

        // convert rotation vector into rotation matrix
        float[] deltaMatrix = new float[9];
        SensorManager.getRotationMatrixFromVector(deltaMatrix, deltaVector);

        // apply the new rotation interval on the gyroscope based rotation matrix
        gyroMatrix = matrixMultiplication(gyroMatrix, deltaMatrix);

        // get the gyroscope based orientation from the rotation matrix
        SensorManager.getOrientation(gyroMatrix, gyroOrientation);
    }

    private float[] getRotationMatrixFromOrientation(float[] o) {
        float[] xM = new float[9];
        float[] yM = new float[9];
        float[] zM = new float[9];

        float sinX = (float)Math.sin(o[1]);
        float cosX = (float)Math.cos(o[1]);
        float sinY = (float)Math.sin(o[2]);
        float cosY = (float)Math.cos(o[2]);
        float sinZ = (float)Math.sin(o[0]);
        float cosZ = (float)Math.cos(o[0]);

        // rotation about x-axis (pitch)
        xM[0] = 1.0f; xM[1] = 0.0f; xM[2] = 0.0f;
        xM[3] = 0.0f; xM[4] = cosX; xM[5] = sinX;
        xM[6] = 0.0f; xM[7] = -sinX; xM[8] = cosX;

        // rotation about y-axis (roll)
        yM[0] = cosY; yM[1] = 0.0f; yM[2] = sinY;
        yM[3] = 0.0f; yM[4] = 1.0f; yM[5] = 0.0f;
        yM[6] = -sinY; yM[7] = 0.0f; yM[8] = cosY;

        // rotation about z-axis (azimuth)
        zM[0] = cosZ; zM[1] = sinZ; zM[2] = 0.0f;
        zM[3] = -sinZ; zM[4] = cosZ; zM[5] = 0.0f;
        zM[6] = 0.0f; zM[7] = 0.0f; zM[8] = 1.0f;

        // rotation order is y, x, z (roll, pitch, azimuth)
        float[] resultMatrix = matrixMultiplication(xM, yM);
        resultMatrix = matrixMultiplication(zM, resultMatrix);
        return resultMatrix;
    }

    private float[] matrixMultiplication(float[] A, float[] B) {
        float[] result = new float[9];

        result[0] = A[0] * B[0] + A[1] * B[3] + A[2] * B[6];
        result[1] = A[0] * B[1] + A[1] * B[4] + A[2] * B[7];
        result[2] = A[0] * B[2] + A[1] * B[5] + A[2] * B[8];

        result[3] = A[3] * B[0] + A[4] * B[3] + A[5] * B[6];
        result[4] = A[3] * B[1] + A[4] * B[4] + A[5] * B[7];
        result[5] = A[3] * B[2] + A[4] * B[5] + A[5] * B[8];

        result[6] = A[6] * B[0] + A[7] * B[3] + A[8] * B[6];
        result[7] = A[6] * B[1] + A[7] * B[4] + A[8] * B[7];
        result[8] = A[6] * B[2] + A[7] * B[5] + A[8] * B[8];

        return result;
    }

    private float[][] matMul2(float[][] A, float[][] B) {
        float[][] ab = new float[2][2];

        ab[0][0] = A[0][0] * B[0][0] + A[0][1] * B[1][0];
        ab[0][1] = A[0][0] * B[0][1] + A[0][1] * B[1][1];
        ab[1][0] = A[1][0] * B[0][0] + A[1][1] * B[1][0];
        ab[1][1] = A[1][0] * B[0][1] + A[1][1] * B[1][1];

        return ab;
    }


    class calculateFusedOrientationTask extends TimerTask {
        public void run() {
            float oneMinusCoeff = 1.0f - FILTER_COEFFICIENT;

            /*
             * Fix for 179� <--> -179� transition problem:
             * Check whether one of the two orientation angles (gyro or accMag) is negative while the other one is positive.
             * If so, add 360� (2 * math.PI) to the negative value, perform the sensor fusion, and remove the 360� from the result
             * if it is greater than 180�. This stabilizes the output in positive-to-negative-transition cases.
             */

            // azimuth
            if (gyroOrientation[0] < -0.5 * Math.PI && accMagOrientation[0] > 0.0) {
                fusedOrientation[0] = (float) (FILTER_COEFFICIENT * (gyroOrientation[0] + 2.0 * Math.PI) + oneMinusCoeff * accMagOrientation[0]);
                fusedOrientation[0] -= (fusedOrientation[0] > Math.PI) ? 2.0 * Math.PI : 0;
            }
            else if (accMagOrientation[0] < -0.5 * Math.PI && gyroOrientation[0] > 0.0) {
                fusedOrientation[0] = (float) (FILTER_COEFFICIENT * gyroOrientation[0] + oneMinusCoeff * (accMagOrientation[0] + 2.0 * Math.PI));
                fusedOrientation[0] -= (fusedOrientation[0] > Math.PI)? 2.0 * Math.PI : 0;
            }
            else {
                fusedOrientation[0] = FILTER_COEFFICIENT * gyroOrientation[0] + oneMinusCoeff * accMagOrientation[0];
            }

            // pitch
            if (gyroOrientation[1] < -0.5 * Math.PI && accMagOrientation[1] > 0.0) {
                fusedOrientation[1] = (float) (FILTER_COEFFICIENT * (gyroOrientation[1] + 2.0 * Math.PI) + oneMinusCoeff * accMagOrientation[1]);
                fusedOrientation[1] -= (fusedOrientation[1] > Math.PI) ? 2.0 * Math.PI : 0;
            }
            else if (accMagOrientation[1] < -0.5 * Math.PI && gyroOrientation[1] > 0.0) {
                fusedOrientation[1] = (float) (FILTER_COEFFICIENT * gyroOrientation[1] + oneMinusCoeff * (accMagOrientation[1] + 2.0 * Math.PI));
                fusedOrientation[1] -= (fusedOrientation[1] > Math.PI)? 2.0 * Math.PI : 0;
            }
            else {
                fusedOrientation[1] = FILTER_COEFFICIENT * gyroOrientation[1] + oneMinusCoeff * accMagOrientation[1];
            }

            // roll
            if (gyroOrientation[2] < -0.5 * Math.PI && accMagOrientation[2] > 0.0) {
                fusedOrientation[2] = (float) (FILTER_COEFFICIENT * (gyroOrientation[2] + 2.0 * Math.PI) + oneMinusCoeff * accMagOrientation[2]);
                fusedOrientation[2] -= (fusedOrientation[2] > Math.PI) ? 2.0 * Math.PI : 0;
            }
            else if (accMagOrientation[2] < -0.5 * Math.PI && gyroOrientation[2] > 0.0) {
                fusedOrientation[2] = (float) (FILTER_COEFFICIENT * gyroOrientation[2] + oneMinusCoeff * (accMagOrientation[2] + 2.0 * Math.PI));
                fusedOrientation[2] -= (fusedOrientation[2] > Math.PI)? 2.0 * Math.PI : 0;
            }
            else {
                fusedOrientation[2] = FILTER_COEFFICIENT * gyroOrientation[2] + oneMinusCoeff * accMagOrientation[2];
            }

            // overwrite gyro matrix and orientation with fused orientation
            // to comensate gyro drift
            gyroMatrix = getRotationMatrixFromOrientation(fusedOrientation);
            System.arraycopy(fusedOrientation, 0, gyroOrientation, 0, 3);


            // update sensor output in GUI
            mHandler.post(updateOreintationDisplayTask);
        }
    }

    class calculateKalmanOrientationTask extends TimerTask {
        public void run() {

            if (initState1) {
                state0_prev[0][0] = accMagOrientation[0];
                state0_prev[1][0] = gyro[0];
                initState1 = false;
            }

            if (initState2) {
                state1_prev[0][0] = accMagOrientation[1];
                state1_prev[1][0] = gyro[1];
                initState2 = false;
            }

            if (initState3) {
                state2_prev[0][0] = accMagOrientation[2];
                state2_prev[1][0] = gyro[2];
                initState3 = false;
            }

            float[][] A = new float[2][2];
            A[0][0] = 1.0f;
            A[0][1] = dT;
            A[1][0] = 0.0f;
            A[1][1] = 1.0f;

            float Q[][] = new float[2][2];
            Q[0][0] = 1;
            Q[1][0] = 0;
            Q[0][1] = 0;
            Q[1][1] = 1;

            float R = 0.1f;

            /////Azimuth
            ///////Predict Steps
            state0_intermediate[0][0] = A[0][0] * state0_prev[0][0] + A[0][1] * state0_prev[1][0];
            state0_intermediate[1][0] = A[1][0] * state0_prev[0][0] + A[1][1] * state0_prev[1][0];

            cov0_intermediate = matMul2(A, cov0_prev);
            cov0_intermediate = matMul2(cov0_intermediate, A);
            cov0_intermediate[0][0] += Q[0][0];
            cov0_intermediate[0][1] += Q[0][1];
            cov0_intermediate[1][0] += Q[1][0];
            cov0_intermediate[1][1] += Q[1][1];

            ////////////Kalman Gain
            float inverse = cov0_intermediate[0][0] + R;
            KalmanGain[0][0] = cov0_intermediate[0][0] / inverse;
            KalmanGain[1][0] = cov0_intermediate[1][0] / inverse;

            ////Correction Step
            state0_current[0][0] = state0_intermediate[0][0] + (accMagOrientation[0] - state0_intermediate[0][0]) * KalmanGain[0][0];
            state0_current[1][0] = state0_intermediate[1][0] + (accMagOrientation[0] - state0_intermediate[0][0]) * KalmanGain[1][0];

            float upc[][] = new float[2][2];
            upc[0][0] = 1 - KalmanGain[0][0];
            upc[1][0] = 0;
            upc[0][1] = KalmanGain[1][0];
            upc[1][1] = 1;
            cov0_current = matMul2(upc, cov0_intermediate);

            /////Pitch
            ///////Predict Steps
            state1_intermediate[0][0] = A[0][0] * state1_prev[0][0] + A[0][1] * state1_prev[1][0];
            state1_intermediate[1][0] = A[1][0] * state1_prev[0][0] + A[1][1] * state1_prev[1][0];

            cov1_intermediate = matMul2(A, cov1_prev);
            cov1_intermediate = matMul2(cov1_intermediate, A);
            cov1_intermediate[0][0] += Q[0][0];
            cov1_intermediate[0][1] += Q[0][1];
            cov1_intermediate[1][0] += Q[1][0];
            cov1_intermediate[1][1] += Q[1][1];

            ////////////Kalman Gain
            inverse = cov1_intermediate[0][0] + R;
            KalmanGain[0][0] = cov1_intermediate[0][0] / inverse;
            KalmanGain[1][0] = cov1_intermediate[1][0] / inverse;

            ////Correction Step
            state1_current[0][0] = state1_intermediate[0][0] + (accMagOrientation[1] - state1_intermediate[0][0]) * KalmanGain[0][0];
            state1_current[1][0] = state1_intermediate[1][0] + (accMagOrientation[1] - state1_intermediate[0][0]) * KalmanGain[1][0];


            upc[0][0] = 1 - KalmanGain[0][0];
            upc[1][0] = 0;
            upc[0][1] = KalmanGain[1][0];
            upc[1][1] = 1;
            cov1_current = matMul2(upc, cov1_intermediate);

            /////Roll
            ///////Predict Steps
            state2_intermediate[0][0] = A[0][0] * state2_prev[0][0] + A[0][1] * state2_prev[1][0];
            state2_intermediate[1][0] = A[1][0] * state2_prev[0][0] + A[1][1] * state2_prev[1][0];

            cov2_intermediate = matMul2(A, cov2_prev);
            cov2_intermediate = matMul2(cov2_intermediate, A);
            cov2_intermediate[0][0] += Q[0][0];
            cov2_intermediate[0][1] += Q[0][1];
            cov2_intermediate[1][0] += Q[1][0];
            cov2_intermediate[1][1] += Q[1][1];

            ////////////Kalman Gain
            inverse = cov2_intermediate[0][0] + R;
            KalmanGain[0][0] = cov2_intermediate[0][0] / inverse;
            KalmanGain[1][0] = cov2_intermediate[1][0] / inverse;

            ////Correction Step
            state2_current[0][0] = state2_intermediate[0][0] + (accMagOrientation[2] - state2_intermediate[0][0]) * KalmanGain[0][0];
            state2_current[1][0] = state2_intermediate[1][0] + (accMagOrientation[2] - state2_intermediate[0][0]) * KalmanGain[1][0];


            upc[0][0] = 1 - KalmanGain[0][0];
            upc[1][0] = 0;
            upc[0][1] = KalmanGain[1][0];
            upc[1][1] = 1;
            cov2_current = matMul2(upc, cov2_intermediate);


            ////Display new angles
            // azimuth
            if (gyroOrientation[0] < -0.5 * Math.PI && accMagOrientation[0] > 0.0) {
                kalmanOrientation[0] = state0_current[0][0];
                kalmanOrientation[0] -= (kalmanOrientation[0] > Math.PI) ? 2.0 * Math.PI : 0;
            } else if (accMagOrientation[0] < -0.5 * Math.PI && gyroOrientation[0] > 0.0) {
                kalmanOrientation[0] = state0_current[0][0];
                kalmanOrientation[0] -= (kalmanOrientation[0] > Math.PI) ? 2.0 * Math.PI : 0;
            } else {
                kalmanOrientation[0] = state0_current[0][0];
            }

            // pitch
            if (gyroOrientation[1] < -0.5 * Math.PI && accMagOrientation[1] > 0.0) {
                kalmanOrientation[1] = state1_current[0][0];
                kalmanOrientation[1] -= (kalmanOrientation[1] > Math.PI) ? 2.0 * Math.PI : 0;
            } else if (accMagOrientation[1] < -0.5 * Math.PI && gyroOrientation[1] > 0.0) {
                kalmanOrientation[1] = state1_current[0][0];
                kalmanOrientation[1] -= (kalmanOrientation[1] > Math.PI) ? 2.0 * Math.PI : 0;
            } else {
                kalmanOrientation[1] = state1_current[0][0];
            }

            // roll
            if (gyroOrientation[2] < -0.5 * Math.PI && accMagOrientation[2] > 0.0) {
                kalmanOrientation[2] = state2_current[0][0];
                kalmanOrientation[2] -= (kalmanOrientation[2] > Math.PI) ? 2.0 * Math.PI : 0;
            } else if (accMagOrientation[2] < -0.5 * Math.PI && gyroOrientation[2] > 0.0) {
                kalmanOrientation[2] = state2_current[0][0];
                kalmanOrientation[2] -= (kalmanOrientation[2] > Math.PI) ? 2.0 * Math.PI : 0;
            } else {
                kalmanOrientation[2] = state2_current[0][0];
            }

            // overwrite gyro matrix and orientation with fused orientation
            // to comensate gyro drift
            gyroMatrix = getRotationMatrixFromOrientation(kalmanOrientation);
            System.arraycopy(kalmanOrientation, 0, gyroOrientation, 0, 3);

            // update sensor output in GUI
            mHandler.post(updateOreintationDisplayTask);

            state0_prev = state0_current;
            cov0_prev = cov0_current;

            state1_prev = state1_current;
            cov1_prev = cov1_current;

            state2_prev = state2_current;
            cov2_prev = cov2_current;

        }
    }


    // **************************** GUI FUNCTIONS *********************************
    @Override
    public void onCheckedChanged(RadioGroup group, int checkedId) {
        switch (checkedId) {
            case R.id.radio0:
                radioSelection = 0;
                break;
            case R.id.radio1:
                radioSelection = 1;
                break;
            case R.id.radio2:
                radioSelection = 2;
                break;
            case R.id.radio3:
                radioSelection = 3;
                break;
        }
    }

    public void updateOreintationDisplay() {
        switch(radioSelection) {
            case 0:
                mAzimuthView.setText(d.format(accMagOrientation[0] * 180/Math.PI));
                mPitchView.setText(d.format(accMagOrientation[1] * 180/Math.PI));
                mRollView.setText(d.format(accMagOrientation[2] * 180/Math.PI));
                break;
            case 1:
                mAzimuthView.setText(d.format(gyroOrientation[0] * 180/Math.PI));
                mPitchView.setText(d.format(gyroOrientation[1] * 180/Math.PI));
                mRollView.setText(d.format(gyroOrientation[2] * 180/Math.PI));
                break;
            case 2:
                mAzimuthView.setText(d.format(fusedOrientation[0] * 180/Math.PI));
                mPitchView.setText(d.format(fusedOrientation[1] * 180/Math.PI));
                mRollView.setText(d.format(fusedOrientation[2] * 180/Math.PI));
                break;

            case 3:
                mAzimuthView.setText(d.format(kalmanOrientation[0] * 180/Math.PI));
                mPitchView.setText(d.format(kalmanOrientation[1] * 180/Math.PI));
                mRollView.setText(d.format(kalmanOrientation[2] * 180/Math.PI));
                break;
        }
    }

    private Runnable updateOreintationDisplayTask = new Runnable() {
        public void run() {
            updateOreintationDisplay();
        }
    };
}