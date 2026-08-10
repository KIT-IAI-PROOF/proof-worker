/*
 * Copyright (c) 2026
 * Karlsruhe Institute of Technology - Institute for Automation and Applied Informatics
 */

package edu.kit.iai.webis.proofworker.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.time.Instant;

/**
 * DTO for publishing block status updates to the monitoring service.
 * This format matches the BlockStatusUpdate.java in proof-monitoring.
 */
public class BlockStatusUpdate implements Serializable {

    /** The execution this update belongs to. */
    @JsonProperty("executionId")
    private String executionId;

    /** The block whose status changed. */
    @JsonProperty("blockId")
    private String blockId;

    /** The new status value (as string to match ESimulationStatus enum name). */
    @JsonProperty("status")
    private String status;

    /** The current communication point. */
    @JsonProperty("cp")
    private Integer cp;

    /** Optional error message when status is ERROR_*. */
    @JsonProperty("message")
    private String message;

    /** Server-side timestamp of the event. */
    @JsonProperty("timestamp")
    @JsonFormat(shape = JsonFormat.Shape.NUMBER)
    private Instant timestamp;

    public BlockStatusUpdate() {
    }

    public BlockStatusUpdate(String executionId, String blockId, String status, Integer cp, String message, Instant timestamp) {
        this.executionId = executionId;
        this.blockId = blockId;
        this.status = status;
        this.cp = cp;
        this.message = message;
        this.timestamp = timestamp;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getExecutionId() {
        return executionId;
    }

    public void setExecutionId(String executionId) {
        this.executionId = executionId;
    }

    public String getBlockId() {
        return blockId;
    }

    public void setBlockId(String blockId) {
        this.blockId = blockId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getCP() {
        return cp;
    }

    public void setCP(Integer cp) {
        this.cp = cp;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public static class Builder {
        private String executionId;
        private String blockId;
        private String status;
        private Integer cp;
        private String message;
        private Instant timestamp;

        public Builder executionId(String executionId) {
            this.executionId = executionId;
            return this;
        }

        public Builder blockId(String blockId) {
            this.blockId = blockId;
            return this;
        }

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public Builder cp(Integer cp) {
            this.cp = cp;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public BlockStatusUpdate build() {
            return new BlockStatusUpdate(executionId, blockId, status, cp, message, timestamp);
        }
    }
}
