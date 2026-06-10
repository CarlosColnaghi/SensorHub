package com.sensorhub.ingestor;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Logger;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

final class MqttTelemetryConsumer implements AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(MqttTelemetryConsumer.class.getName());

    private final MqttConfig config;
    private final TelemetryProcessor processor;
    private final CountDownLatch stopped = new CountDownLatch(1);
    private MqttClient client;

    MqttTelemetryConsumer(MqttConfig config, TelemetryProcessor processor) {
        this.config = config;
        this.processor = processor;
    }

    void start() throws MqttException {
        client = new MqttClient(config.brokerUri(), config.clientId(), new MemoryPersistence());
        client.setCallback(new MqttCallback() {
            @Override
            public void connectionLost(Throwable cause) {
                LOGGER.warning("MQTT connection lost: " + cause.getMessage());
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) {
                String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
                processor.process(payload);
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
            }
        });

        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(true);
        options.setAutomaticReconnect(true);

        client.connect(options);
        client.subscribe(config.topic(), config.qos());
        LOGGER.info("subscribed to " + config.topic() + " at " + config.brokerUri());
    }

    void await() throws InterruptedException {
        stopped.await();
    }

    @Override
    public void close() throws MqttException {
        stopped.countDown();
        if (client != null && client.isConnected()) {
            client.disconnect();
        }
    }
}
