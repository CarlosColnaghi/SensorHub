package com.sensorhub.ingestor;

record MqttConfig(
        String host,
        int port,
        String topic,
        String clientId,
        int qos
) {
    String brokerUri() {
        return "tcp://" + host + ":" + port;
    }
}
