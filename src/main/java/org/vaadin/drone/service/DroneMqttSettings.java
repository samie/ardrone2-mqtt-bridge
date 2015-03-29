package org.vaadin.drone.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Connection settings for the AR.Drone2.
 *
 * @author Sami Ekblad
 */
@Component
@ConfigurationProperties(prefix = "drone")
public class DroneMqttSettings {

    private String ip;
    private int cmdPort;
    private int navPort;

    private String mqttUrl;
    private String mqttId;

    private String mqttPub;
    private String mqttSub;
    private boolean jsonMode;

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public int getCmdPort() {
        return cmdPort;
    }

    public void setCmdPort(int cmdPort) {
        this.cmdPort = cmdPort;
    }

    public int getNavPort() {
        return navPort;
    }

    public void setNavPort(int navPort) {
        this.navPort = navPort;
    }

    public String getMqttUrl() {
        return mqttUrl;
    }

    public void setMqttUrl(String mqttUrl) {
        this.mqttUrl = mqttUrl;
    }

    public String getMqttId() {
        return mqttId;
    }

    public void setMqttId(String mqttId) {
        this.mqttId = mqttId;
    }

    public String getMqttPub() {
        return mqttPub;
    }

    public void setMqttPub(String mqttPub) {
        this.mqttPub = mqttPub;
    }

    public String getMqttSub() {
        return mqttSub;
    }

    public void setMqttSub(String mqttSub) {
        this.mqttSub = mqttSub;
    }

    public boolean isJsonMode() {
        return jsonMode;
    }

    public void setJsonMode(boolean jsonMode) {
        this.jsonMode = jsonMode;
    }

}
