package org.vaadin.drone.service;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * AR.Drone2 communication.
 *
 * @author Sami Ekblad
 */
public final class ARDrone {

    private final int MAX_PACKET_SIZE = 4096;
    private final int TIMEOUT_COMMAND = 3000;
    private final int TIMEOUT_NAVDATA = 3000;

    /* Command sequence */
    private long sequence = 0;

    private synchronized long nextSequence() {
        return ++sequence;
    }

    private final String ip;
    private final int comPort;
    private final int navPort;
    private final InetAddress inetAddr;
    private final DatagramSocket comSocket;
    private final DatagramSocket navSocket;
    private final Buffer commBuf;
    private boolean running = false;
    private final List<DroneStateCallback> stateCallbacks = new ArrayList<>();
    private long navdataInterval = 1000;

    public ARDrone(String ip, int comPort, int navPort) throws IOException {
        this.ip = ip;
        this.comPort = comPort;
        this.navPort = navPort;

        inetAddr = parseIPAddress(ip);
        comSocket = new DatagramSocket();
        comSocket.setSoTimeout(TIMEOUT_COMMAND);

        navSocket = new DatagramSocket();
        navSocket.setSoTimeout(TIMEOUT_NAVDATA);

        commBuf = new Buffer(4);

        // Create a buffer for conversion
        // Failsafe: Default max altitude to 2m
        sendInternalCommand(InternalCommand.MAX_ALTITUDE, 2000);
        sendInternalCommand(InternalCommand.RESET_EMERGENCY);

    }

    private static InetAddress parseIPAddress(String ip) {
        StringTokenizer st = new StringTokenizer(ip, ".");

        byte[] ipBytes = new byte[4];

        for (int i = 0; i < 4; i++) {
            ipBytes[i] = (byte) Integer.parseInt(st.nextToken());
        }
        try {
            return InetAddress.getByAddress(ipBytes);
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    public void startNavData() {
        startNavDataThread();
    }

    public void startNavData(DroneStateCallback cb, long navdataInterval) {
        setNavdataInterval(navdataInterval);
        addCallback(cb);
        startNavDataThread();
    }

    public long getNavdataInterval() {
        return navdataInterval;
    }

    public void setNavdataInterval(long navdataInterval) {
        this.navdataInterval = navdataInterval;
    }

    private void startNavDataThread() {
        // Start reading NAV data in separate thread
        if (isRunning()) {
            return;
        }

        running = true;
        new Thread(() -> {
            try {
                navDataLoop();
            } catch (Exception e) {
                Logger.getLogger(ARDrone.class.getName()).log(Level.SEVERE, null, e);
            }
        }).start();
    }

    public void stopNavData() {
        this.running = false;
    }

    private void navDataLoop() throws Exception {
        Logger.getLogger(ARDrone.class.getName()).log(Level.INFO, "Starting navdata receiver");

        //  initiate the communication
        sendInitPacket(navSocket, inetAddr, navPort);

        long lastReportTime = System.currentTimeMillis();
        while (isRunning()) {
            long timeNow = System.currentTimeMillis();
            try {
                NavData currentState = readNavdata(navSocket,
                        MAX_PACKET_SIZE);
                if (timeNow - lastReportTime > this.getNavdataInterval()) {                    
                    Logger.getLogger(ARDrone.class.getName()).log(Level.FINEST, currentState.toString());
                    stateCallbacks.forEach(cb -> cb.onDroneStateChanged(currentState));
                    lastReportTime = timeNow;
                    Logger.getLogger(ARDrone.class.getName()).log(Level.FINEST, "Navdata update complete ");
                }
            } catch (java.lang.IllegalArgumentException e) {
                Logger.getLogger(ARDrone.class.getName()).log(Level.FINEST, "Failed to parse: " + e.getMessage(), e);
            } catch (java.net.SocketTimeoutException e) {
                Logger.getLogger(ARDrone.class.getName()).log(Level.FINEST, "Navdata connection reset");
                sendInitPacket(navSocket, inetAddr, navPort);
            } catch (Throwable t) {
                Logger.getLogger(ARDrone.class.getName()).log(Level.SEVERE, "Message read failed", t);
            }
        }
        Logger.getLogger(ARDrone.class.getName()).log(Level.INFO, "Stopped navdata receiver");
    }

    private NavData readNavdata(DatagramSocket datagramSocket,
            int maxPacketSize) throws IOException {

        DatagramPacket packet = new DatagramPacket(new byte[maxPacketSize],
                maxPacketSize);
        datagramSocket.receive(packet);
        NavData droneState = NavData.create(packet);
        return droneState;
    }

    public void cmdTakeoff() throws IOException {
        sendInternalCommand(InternalCommand.TAKEOFF);
    }

    public void cmdLand() throws IOException {
        sendInternalCommand(InternalCommand.LAND);
    }

    public void cmdBlink(int seconds) throws IOException {
        sendInternalCommand(InternalCommand.INIT_BLINK, seconds);
    }

    public void cmdReset() throws IOException {
        sendInternalCommand(InternalCommand.RESET_EMERGENCY);
    }

    public void cmdNavData(boolean demoMode) throws IOException {
        sendInternalCommand(InternalCommand.START_NAVDATA, String.valueOf(demoMode).toUpperCase());
    }

    /**
     * Format command.
     *
     * @param cmd
     * @param payload
     * @param args
     * @return
     */
    public String formatCommand(AT cmd, String payload, Object... args) {
        if (payload == null) {
            // Simple (no payload) command
            return AT_COMMAND_PREFIX.concat(cmd.name())
                    .concat(AT_COMMAND_DELIM)
                    .concat(String.valueOf(nextSequence()));
        } else {
            if (args != null) {
                // Substitute the variables
                return AT_COMMAND_PREFIX.concat(cmd.name())
                        .concat(AT_COMMAND_DELIM)
                        .concat(String.valueOf(nextSequence()))
                        .concat(AT_PAYLOAD_DELIM)
                        .concat(String.format(payload, args));
            } else {
                // No variable substitution
                return AT_COMMAND_PREFIX
                        .concat(cmd.name())
                        .concat(AT_COMMAND_DELIM)
                        .concat(String.valueOf(nextSequence()))
                        .concat(AT_PAYLOAD_DELIM)
                        .concat(payload);
            }
        }
    }
    private static final String AT_COMMAND_PREFIX = "AT*";
    private static final String AT_COMMAND_DELIM = "=";
    private static final String AT_PAYLOAD_DELIM = ",";

    /**
     * Format a predefined internal command.
     *
     * @param cmd
     * @param args
     * @return
     */
    public String formatCommand(InternalCommand cmd, Object... args) {
        return formatCommand(cmd.cmd, cmd.payload, args);
    }

    public void sendCommand(AT atCommand, String payload) throws IOException {
        sendCmd(formatCommand(atCommand, payload));
    }

    private void sendInternalCommand(InternalCommand cmd, Object... values) throws IOException {
        sendCmd(formatCommand(cmd, values));
    }

    private void sendCmd(String cmd) throws IOException {
        System.out.println("Send to drone '" + inetAddr.getHostAddress() + "':" + comPort + " -> " + cmd);
        byte[] buffer = (cmd + "\r").getBytes();
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length, inetAddr, comPort);
        comSocket.send(packet);
    }

    private boolean isRunning() {
        return this.running;
    }

    private static void sendInitPacket(DatagramSocket navSocket, InetAddress addr, int port) throws IOException {
        navSocket.send(new DatagramPacket(new byte[]{0x01, 0x00, 0x00,
            0x00}, 4, addr, port));
    }

    /**
     * Buffer for data conversion.
     *
     */
    private class Buffer {

        private FloatBuffer fb;
        private IntBuffer ib;

        private Buffer(int size) {
            ByteBuffer bb = ByteBuffer.allocate(size);
            fb = bb.asFloatBuffer();
            ib = bb.asIntBuffer();

        }

        private synchronized int floatToInt(float f) {
            fb.put(0, f);
            return ib.get(0);
        }

    }

    /**
     * Predefined drone commands.
     */
    private enum InternalCommand {

        INIT_BLINK(AT.LED, "20,1056964608,%d"),
        RESET_EMERGENCY(AT.REF, "290717952"),
        MAX_ALTITUDE(AT.CONFIG, "\"control:altitude_max\",\"%d\""),
        START_NAVDATA(AT.CONFIG, "\"general:navdata_demo\",\"%s\""),
        WATCHDOG(AT.COMWDG),
        TRIM(AT.FTRIM),
        TAKEOFF(AT.REF, "290718208"),
        LAND(AT.REF, "290717696"),
        HOVERING(AT.PCMD, "1,0,0,0,0");

        private AT cmd;
        private String payload;

        private InternalCommand(AT cmd) {
            this.cmd = cmd;
        }

        private InternalCommand(AT cmd, String payload) {
            this.cmd = cmd;
            this.payload = payload;
        }

        public AT getCmd() {
            return cmd;
        }

        public String getPayload() {
            return payload;
        }

        @Override
        public String toString() {
            return "InternalCommand{" + "cmd=" + cmd + ", payload=" + payload + '}';
        }

    }

    public interface DroneStateCallback {

        void onDroneStateChanged(NavData latestState);
    }

    public void addCallback(DroneStateCallback cb) {
        synchronized (stateCallbacks) {
            stateCallbacks.add(cb);
        }
    }

    public void removeCallback(DroneStateCallback cb) {
        synchronized (stateCallbacks) {
            stateCallbacks.remove(cb);
        }
    }

    /**
     * Supported Drone AT commands.
     */
    public enum AT {
        REF,
        PCMD,
        CONFIG,
        CTRL,
        FTRIM,
        LED,
        ANIM,
        COMWDG;
    }

}
