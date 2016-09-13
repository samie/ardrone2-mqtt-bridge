/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.vaadin.drone.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.PostConstruct;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Bridge between MTQQ and AR.Drone2.
 *
 * Effectively this publishes the AR.Drone2 controls to under given MQTT topic.
 *
 * @author Sami Ekblad
 */
@Service
public class MQTTBridge {

    @Autowired
    private DroneMqttSettings settings;

    private MqttClient mqtt;
    private ARDrone drone;

    public MQTTBridge() {
    }

    @PostConstruct
    public void openConnection() {
        try {
            mqtt = new MqttClient(settings.getMqttUrl(), settings.getMqttId());
            mqtt.connect();

            drone = new ARDrone(settings.getIp(), settings.getCmdPort(), settings.getNavPort());
            drone.addCallback(e -> publish(settings.getMqttPub(), e));

            // Receive commands
            mqtt.setCallback(new MqttCallBack());
            mqtt.subscribe(settings.getMqttSub());

            // Visual feedback of connection
            drone.cmdBlink(6);

        } catch (MqttException | IOException ex) {
            Logger.getLogger(MQTTBridge.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void publish(String parentTopic, NavData navData) {

        Gson b = new GsonBuilder().setDateFormat("yyyy-MM-dd HH:mm:ss:SSS").create();
        JsonObject json = b
                .toJsonTree(navData).getAsJsonObject();
        if (isPublishJson()) {
            try {
                mqtt.publish(parentTopic, new MqttMessage(b.toJson(json).getBytes()));
                Logger.getLogger(MQTTBridge.class.getName()).log(Level.FINE, "MQTT: publish " + parentTopic + "=" + json.toString());
            } catch (MqttException ex) {
                Logger.getLogger(MQTTBridge.class.getName()).log(Level.SEVERE, null, ex);
            }

        } else {
            json.entrySet().stream().forEach(p -> {
                try {
                    String k = "/" + p.getKey().toUpperCase();
                    String v = p.getValue().toString();
                    mqtt.publish(parentTopic + p.getKey(), new MqttMessage(v.getBytes()));
                    Logger.getLogger(MQTTBridge.class.getName()).log(Level.FINE, "MQTT: publish " + parentTopic + k + "=" + v);
                } catch (MqttException ex) {
                    Logger.getLogger(MQTTBridge.class.getName()).log(Level.SEVERE, null, ex);
                }
            });

        }

    }

    private boolean isPublishJson() {
        return settings.isJsonMode();
    }

    /**
     * Callback for receiving the drone commands over MQTT.
     */
    private class MqttCallBack implements MqttCallback {

        public MqttCallBack() {
        }

        @Override
        public void connectionLost(Throwable thrwbl) {
            Logger.getLogger(MQTTBridge.class.getName()).log(Level.FINE, "MQTT: connectionLost");
        }

        @Override
        public void messageArrived(String topic, MqttMessage mm) throws Exception {
            String payload = new String(mm.getPayload());
            Logger.getLogger(MQTTBridge.class.getName()).log(Level.FINE, "MQTT: messageArrived " + topic + ":" + payload);

            String cmdName = topic.substring(topic.lastIndexOf("/") + 1);
            ARDrone.AT cmd = null;
            try {
                cmd = ARDrone.AT.valueOf(cmdName);
            } catch (Exception ignored) {
            }
            if (cmd != null) {
                drone.sendCommand(cmd, payload);
            } else if (TRIM_CMD.equals(cmdName)) {
                drone.cmdReset();
            } else if (RESET_CMD.equals(cmdName)) {
                drone.cmdReset();
            } else if (TAKEOFF_CMD.equals(cmdName)) {
                drone.cmdTakeoff();
            } else if (LAND_CMD.equals(cmdName)) {
                drone.cmdLand();
            } else if (TRIM_CMD.equals(cmdName)) {
                drone.cmdReset();
            } else if (NAVDATA_CMD.equals(cmdName)) {
                String type = payload.toLowerCase();
                if ("stop".equals(type)) {
                    drone.stopNavData();
                } else {
                    try {
                        int interval = Integer.parseInt(type);
                        drone.setNavdataInterval(interval);
                    } catch (NumberFormatException ignored) {
                    }
                    if ("demo".equals(type)) {
                        drone.cmdNavData(true);
                    } else if ("all".equals(type)) {
                        drone.cmdNavData(false);
                    }
                    drone.startNavData();
                }
            }
        }

        private static final String NAVDATA_CMD = "NAVDATA";
        private static final String TRIM_CMD = "TRIM";
        private static final String RESET_CMD = "RESET";
        private static final String TAKEOFF_CMD = "TAKEOFF";
        private static final String LAND_CMD = "LAND";

        @Override
        public void deliveryComplete(IMqttDeliveryToken imdt) {
            Logger.getLogger(MQTTBridge.class.getName()).log(Level.FINE, "MQTT: deliveryComplete" + imdt);
        }
    }

}
