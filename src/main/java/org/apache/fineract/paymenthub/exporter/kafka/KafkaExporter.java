/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package org.apache.fineract.paymenthub.exporter.kafka;

import org.apache.fineract.paymenthub.exporter.config.ElasticSearchConfiguration;
import org.slf4j.Logger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;

import io.zeebe.exporter.api.Exporter;
import io.zeebe.exporter.api.context.Context;
import io.zeebe.exporter.api.context.Controller;
import io.zeebe.protocol.record.Record;

@ConditionalOnExpression("${exporter.kafka.enabled}")
public class KafkaExporter implements Exporter {
    private Logger logger;
    private Controller controller;

    private ElasticSearchConfiguration configuration;

    private KafkaExporterClient client;

    private long lastPosition = -1;

    @Override
    public void configure(final Context context) {
        try {
            logger = context.getLogger();
            configuration = context.getConfiguration().instantiate(ElasticSearchConfiguration.class);
            logger.debug("DPC Kafka exporter configured with {}", configuration);

            // context.setFilter(new KafkaRecordFilter(configuration));
        } catch (Exception e) {
            logger.error("Failed to configure KafkaExporter", e);
        }
    }

    @Override
    public void open(final Controller controller) {
        logger.info("DPC Kafka exporter opening");
        this.controller = controller;
        client = createClient();

        // scheduleDelayedFlush();
        logger.info("DPC Kafka exporter opened");
    }

    @Override
    public void close() {
        try {
            flush();
        } catch (final Exception e) {
            logger.warn("Failed to flush records before closing exporter.", e);
        }

        try {
            client.close();
        } catch (final Exception e) {
            logger.warn("Failed to close elasticsearch client", e);
        }

        logger.info("DPC Kafka exporter closed");
    }

    @Override
    public void export(final Record<?> record) {
        client.index(record);
        lastPosition = record.getPosition();

        if (client.shouldFlush()) {
            flush();
        }
    }

    protected KafkaExporterClient createClient() {
        return new KafkaExporterClient(configuration, logger);
    }

    /*
    private void flushAndReschedule() {
        try {
            flush();
        } catch (final Exception e) {
            logger.error("Unexpected exception occurred on periodically flushing bulk, will retry later.", e);
        }
        scheduleDelayedFlush();
    }
    */

    /*
    private void scheduleDelayedFlush() {
        controller.scheduleTask(Duration.ofSeconds(configuration.bulk.delay), this::flushAndReschedule);
    }
    */

    private void flush() {
        if (client.flush()) {
            controller.updateLastExportedRecordPosition(lastPosition);
        } else {
            logger.warn("Failed to flush bulk completely");
        }
    }

    // public static class KafkaRecordFilter implements Context.RecordFilter {
    // private final KafkaExporterConfiguration configuration;
    //
    // KafkaRecordFilter(final KafkaExporterConfiguration configuration) {
    // this.configuration = configuration;
    // }
    //
    // @Override
    // public boolean acceptType(final RecordType recordType) {
    // return configuration.shouldIndexRecordType(recordType);
    // }
    //
    // @Override
    // public boolean acceptValue(final ValueType valueType) {
    // return configuration.shouldIndexValueType(valueType);
    // }
    // }
}
