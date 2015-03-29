# AR.Drone 2 navdata and control over MQTT.

Standalone Spring Boot service that publishes and subscribes MQTT topics from/to an AR.Drone 2.0.
This can be used wigether with [MQTT dashboard to display](https://github.com/samie/bluemix-mqtt-dashboard) in-flight data from the drone as well as 
control the drone over MQTT.

## Used technology

* Spring Boot, http://projects.spring.io/spring-boot/  
* Eclipse Paho MQTT, https://eclipse.org/paho/clients/java/

Project created with http://start.spring.io/

## Build and install

    git clone

## Configuration

The MQTT bridge service is configured in [src/main/resources/application.properties](src/main/resources/application.properties)

Drone settings are typically the same, but MQTT behavior should be adjusted to fit your needs.
Here is a sample configuration:

    # These are typically not changed
    drone.ip=192.168.1.1
    drone.cmdPort=5556
    drone.navPort=5554
    
    # MQTT pub/sub configuration
    drone.mqttUrl=tcp://localhost:1883
    drone.mqttId=vaadindrone
    drone.mqttPub=vaadindrone/NAVDATA
    drone.mqttSub=vaadindrone/CMD/#
    drone.jsonMode=true

Note: 'drone.jsonMode' configures how navdata is published over MQTT. If this is 'true' all data is published 
as single JSON object message with topic specified by 'drone.mqttPub'. If set to 'false', data is published in their own 
topics under the topic 'drone.mqttPub'. For example battery level would be a value  'vaadindrone/NAVDATA/BATTERY'.
  
## License

Apache 2.0 License, https://www.apache.org/licenses/LICENSE-2.0.html

