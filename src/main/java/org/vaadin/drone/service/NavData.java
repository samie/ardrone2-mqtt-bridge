package org.vaadin.drone.service;

import java.net.DatagramPacket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.zip.CRC32;

/**
 * NAVDATA sent by the AR.Drone.
 */
public class NavData {

    public static NavData create(DatagramPacket packet) {
        ByteBuffer buffer = ByteBuffer.wrap(packet.getData(), 0,
                packet.getLength());
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        int state = buffer.getInt();
        long seqNum = getUInt32(buffer);
        int vision = buffer.getInt();

        NavData d = new NavData(seqNum, state, vision);
        while (buffer.position() < buffer.limit()) {
            int tag = buffer.getShort() & 0xFFFF;
            int payloadSize = (buffer.getShort() & 0xFFFF) - 4;
            ByteBuffer optionData = buffer.slice().order(ByteOrder.LITTLE_ENDIAN);
            payloadSize = Math.max(0,Math.min(payloadSize, optionData.remaining())); // added due to new AR.Drone firmware version as of 06.01.2014 (don't know which version > 2.3.3 and < 2.4.8 caused the change)
            optionData.limit(payloadSize);
            parseOption(tag, optionData, d);
            buffer.position(buffer.position() + payloadSize);
        }

        if (!d.checkCheckSum(buffer)) {
           // throw new IllegalArgumentException("Invalid data checksum");
        }

        return d;
    }

    // supported option tags
    private static final int CKS_TAG = -1;
    private static final int DEMO_TAG = 0;
    private static final int TIME_TAG = 1;
    private static final int RAW_MEASURES_TAG = 2;
    private static final int PHYS_MEASURES_TAG = 3;
    private static final int GYROS_OFFSETS_TAG = 4;
    private static final int EULER_ANGLES_TAG = 5;
    private static final int REFERENCES_TAG = 6;
    private static final int TRIMS_TAG = 7;
    private static final int RC_REFERENCES_TAG = 8;
    private static final int PWM_TAG = 9;
    private static final int ALTITUDE_TAG = 10;
    private static final int VISION_RAW_TAG = 11;
    private static final int VISION_OF_TAG = 12;
    private static final int VISION_TAG = 13;
    private static final int VISION_PERF_TAG = 14;
    private static final int TRACKERS_SEND_TAG = 15;
    private static final int VISION_DETECT_TAG = 16;
    private static final int WATCHDOG_TAG = 17;
    private static final int ADC_DATA_FRAME_TAG = 18;
    private static final int VIDEO_STREAM_TAG = 19;
    private static final int GAMES_TAG = 20;
    private static final int PRESSURE_RAW_TAG = 21;
    private static final int MAGNETO_TAG = 22;
    private static final int WIND_TAG = 23;
    private static final int KALMAN_PRESSURE_TAG = 24;
    private static final int HDVIDEO_STREAM_TAG = 25;
    private static final int WIFI_TAG = 26;
    private static final int ZIMMU_3000_TAG = 27;

    private final long sequenceNumber;
    private final int state;
    private final int vision;
    private float psi;
    private float theta;
    private float phi;
    private int battery;
    private int altitude;
    private int linkQuality;
    private int checksum;
    private ControlState controlState;

    public NavData(long seqNo, int state, int vision) {
        this.sequenceNumber = seqNo;
        this.state = state;
        this.vision = vision;
    }

    public long getSequenceNumber() {
        return sequenceNumber;
    }

    public int getStateBits() {
        return state;
    }

    public boolean isVisionDefined() {
        return vision == 1;
    }

    public boolean isFlying() {
        return (state & (1 << 0)) != 0;
    }

    public boolean isVideoEnabled() {
        return (state & (1 << 1)) != 0;
    }

    public boolean isVisionEnabled() {
        return (state & (1 << 2)) != 0;
    }

    public boolean isAltitudeControlActive() {
        return (state & (1 << 4)) != 0;
    }

    public boolean isUserFeedbackOn() { // TODO better name
        return (state & (1 << 5)) != 0;
    }

    public boolean isControlReceived() {
        return (state & (1 << 6)) != 0;
    }

    public boolean isTrimReceived() { // ARDRONE 1.0 only?
        return (state & (1 << 7)) != 0;
    }

    public boolean isCameraReady() { // ARDRONE 2.0 only?
        return (state & (1 << 7)) != 0; // See SDK2.0, config.h
    }

    public boolean isTrimRunning() { // ARDRONE 1.0 only?
        return (state & (1 << 8)) != 0;
    }

    public boolean isTravellingMask() { // ARDRONE 2.0 only?
        return (state & (1 << 8)) != 0; // See SDK2.0, config.h
    }

    public boolean isTrimSucceeded() { // ARDRONE 1.0 only?
        return (state & (1 << 9)) != 0;
    }

    public boolean isUsbKeyReady() { // ARDRONE 2.0 only?
        return (state & (1 << 9)) != 0; // See SDK2.0, config.h
    }

    public boolean isNavDataDemoOnly() {
        return (state & (1 << 10)) != 0;
    }

    public boolean isNavDataBootstrap() {
        return (state & (1 << 11)) != 0;
    }

    public boolean isMotorsDown() {
        return (state & (1 << 12)) != 0;
    }

    public boolean isCommunicationLost() { // ARDRONE 2.0 only?
        return (state & (1 << 13)) != 0; // Communication Lost : (1) com
        // problem, (0) Com is ok
    }

    public boolean isGyrometersDown() { // ARDRONE 1.0 only?
        return (state & (1 << 14)) != 0;
    }

    public boolean isSoftwareFaultDetected() { // ARDRONE 2.0 only?
        return (state & (1 << 14)) != 0;
    }

    public boolean isBatteryTooLow() {
        return (state & (1 << 15)) != 0;
    }

    public boolean isBatteryTooHigh() { // ARDRONE 1.0 only?
        return (state & (1 << 16)) != 0;
    }

    public boolean isUserEmergencyLanding() { // ARDRONE 2.0 only?
        return (state & (1 << 16)) != 0;
    }

    public boolean isTimerElapsed() {
        return (state & (1 << 17)) != 0;
    }

    public boolean isNotEnoughPower() { // ARDRONE 1.0 only?
        return (state & (1 << 18)) != 0;
    }

    public boolean isMagnetoCalibrationNeeded() { // ARDRONE 2.0 only?
        return (state & (1 << 18)) != 0;
    }

    public boolean isAngelsOutOufRange() {
        return (state & (1 << 19)) != 0;
    }

    public boolean isTooMuchWind() {
        return (state & (1 << 20)) != 0;
    }

    public boolean isUltrasonicSensorDeaf() {
        return (state & (1 << 21)) != 0;
    }

    public boolean isCutoutSystemDetected() {
        return (state & (1 << 22)) != 0;
    }

    public boolean isPICVersionNumberOK() {
        return (state & (1 << 23)) != 0;
    }

    public boolean isATCodedThreadOn() {
        return (state & (1 << 24)) != 0;
    }

    public boolean isNavDataThreadOn() {
        return (state & (1 << 25)) != 0;
    }

    public boolean isVideoThreadOn() {
        return (state & (1 << 26)) != 0;
    }

    public boolean isAcquisitionThreadOn() {
        return (state & (1 << 27)) != 0;
    }

    public boolean isControlWatchdogDelayed() {
        return (state & (1 << 28)) != 0;
    }

    public boolean isADCWatchdogDelayed() {
        return (state & (1 << 29)) != 0;
    }

    public boolean isCommunicationProblemOccurred() {
        return (state & (1 << 30)) != 0;
    }

    public boolean isEmergency() {
        return (state & (1 << 31)) != 0;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || o.getClass() != getClass()) {
            return false;
        }
        return state == ((NavData) o).state
                && vision == ((NavData) o).vision;
    }

    @Override
    public int hashCode() {
        return 31 * state + 15 * vision;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("isVisionDefined: ").append(isVisionDefined()).append("\n");
        sb.append("isFlying: ").append(isFlying()).append("\n");
        sb.append("isVideoEnabled: ").append(isVideoEnabled()).append("\n");
        sb.append("isVisionEnabled: ").append(isVisionEnabled()).append("\n");
        sb.append("AltitudeControlActive: ").append(isAltitudeControlActive()).append("\n");
        sb.append("isUserFeedbackOn: ").append(isUserFeedbackOn()).append("\n");
        sb.append("ControlReceived: ").append(isControlReceived()).append("\n");
        sb.append("isCameraReady: ").append(isCameraReady()).append("\n");
        sb.append("isTravellingMask: ").append(isTravellingMask()).append("\n");
        sb.append("isUsbKeyReady: ").append(isUsbKeyReady()).append("\n");
        sb.append("isSoftwareFaultDetected: ").append(isSoftwareFaultDetected()).append("\n");
        sb.append("isUserEmergencyLanding: ").append(isUserEmergencyLanding()).append("\n");
        sb.append("isMagnetoCalibrationNeeded: ").append(isMagnetoCalibrationNeeded()).append("\n");
        sb.append("isNavDataDemoOnly: ").append(isNavDataDemoOnly()).append("\n");
        sb.append("isNavDataBootstrap: ").append(isNavDataBootstrap()).append("\n");
        sb.append("isMotorsDown: ").append(isMotorsDown()).append("\n");
        sb.append("isBatteryLow: ").append(isBatteryTooLow()).append("\n");
        sb.append("isTimerElapsed: ").append(isTimerElapsed()).append("\n");
        sb.append("isAngelsOutOufRange: ").append(isAngelsOutOufRange()).append("\n");
        sb.append("isTooMuchWind: ").append(isTooMuchWind()).append("\n");
        sb.append("isUltrasonicSensorDeaf: ").append(isUltrasonicSensorDeaf()).append("\n");
        sb.append("isCutoutSystemDetected: ").append(isCutoutSystemDetected()).append("\n");
        sb.append("isPICVersionNumberOK: ").append(isPICVersionNumberOK()).append("\n");
        sb.append("isATCodedThreadOn: ").append(isATCodedThreadOn()).append("\n");
        sb.append("isNavDataThreadOn: ").append(isNavDataThreadOn()).append("\n");
        sb.append("isVideoThreadOn: ").append(isVideoThreadOn()).append("\n");
        sb.append("isAcquisitionThreadOn: ").append(isAcquisitionThreadOn()).append("\n");
        sb.append("isControlWatchdogDelayed: ").append(isControlWatchdogDelayed()).append("\n");
        sb.append("isADCWatchdogDelayed: ").append(isADCWatchdogDelayed()).append("\n");
        sb.append("isCommunicationProblemOccurred: ").append(isCommunicationProblemOccurred()).append("\n");
        sb.append("IsEmergency: ").append(isEmergency()).append("\n");
        sb.append("altitude: ").append(getAltitude()).append("\n");
        sb.append("battery: ").append(getBattery()).append("\n");
        sb.append("theta: ").append(getTheta()).append("\n");
        sb.append("phi: ").append(getPhi()).append("\n");
        sb.append("psi: ").append(getPsi()).append("\n");
        return sb.toString();
    }

    private void setLinkQuality(int linkQuality) {
        this.linkQuality = linkQuality;
    }

    public int getLinkQuality() {
        return linkQuality;
    }

    private void setAltitude(int altitude) {
        this.altitude = altitude;
    }

    public int getAltitude() {
        return altitude;
    }

    private void setBattery(int battery) {
        this.battery = battery;
    }

    public int getBattery() {
        return battery;
    }

    private void setTheta(float theta) {
        this.theta = theta;
    }

    public float getTheta() {
        return theta;
    }

    private void setPhi(float phi) {
        this.phi = phi;
    }

    public float getPhi() {
        return phi;
    }

    private void setPsi(float psi) {
        this.psi = psi;
    }

    public float getPsi() {
        return psi;
    }

    private boolean checkCheckSum(ByteBuffer b) {
        return getCRC(b, 0, b.limit() - 4) == getChecksum();
    }

    public int getState() {
        return state;
    }

    public int getVision() {
        return vision;
    }

    private void setCheckSum(int aInt) {
        this.checksum = aInt;
    }

    public int getChecksum() {
        return checksum;
    }

    private void setControlState(int state) {
        this.controlState = ControlState.fromInt(state);
    }

    public ControlState getControlState() {
        return controlState;
    }

    /* Unsigned int as long.
     */
    private static long getUInt32(ByteBuffer b) {
        return (b.getInt() & 0xFFFFFFFFL);
    }

    private static float[] getFloat(ByteBuffer b, int n) {
        float f[] = new float[n];
        for (int k = 0; k < f.length; k++) {
            f[k] = b.getFloat();
        }
        return f;
    }

    private int getCRC(byte[] b, int offset, int length) {
        CRC32 cks = new CRC32();
        cks.update(b, offset, length);
        return (int) (cks.getValue() & 0xFFFFFFFFL);
    }

    private int getCRC(ByteBuffer b, int offset, int length) {
        return getCRC(b.array(), b.arrayOffset() + offset, length);
    }

    private static void parseOption(int tag, ByteBuffer optionData,
            NavData droneState) {

        switch (tag) {
            case DEMO_TAG:
                int controlState = optionData.getInt();
                int batteryPercentage = optionData.getInt();
                float theta = optionData.getFloat();
                float phi = optionData.getFloat();
                float psi = optionData.getFloat();
                int altitude = optionData.getInt();
                float v[] = getFloat(optionData, 3);

                droneState.setControlState(controlState);
                droneState.setBattery(batteryPercentage);
                droneState.setTheta(theta);
                droneState.setPhi(phi);
                droneState.setPsi(psi);
                droneState.setAltitude(altitude);
                break;
            case WIFI_TAG:
                long linkQuality = getUInt32(optionData);
                droneState.setLinkQuality((int) linkQuality);
                break;
            case ALTITUDE_TAG:
                altitude = optionData.getInt();
                droneState.setAltitude(altitude);
                break;
            case CKS_TAG:
                droneState.setCheckSum(optionData.getInt());
                break;
        }
    }

    public enum ControlState {

        DEFAULT, INIT, LANDED, FLYING, HOVERING, TEST, TRANS_TAKEOFF, TRANS_GOTOFIX, TRANS_LANDING;

        public static ControlState fromInt(int v) {
            ControlState[] values = values();
            if (v < 0 || v > values.length) {
                return null;
            }
            return values[v];
        }
    }

}
