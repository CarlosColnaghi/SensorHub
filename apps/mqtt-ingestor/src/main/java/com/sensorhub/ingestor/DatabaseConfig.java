package com.sensorhub.ingestor;

record DatabaseConfig(
        String host,
        int port,
        String name,
        String user,
        String password
) {
    String jdbcUrl() {
        return "jdbc:postgresql://" + host + ":" + port + "/" + name;
    }
}
