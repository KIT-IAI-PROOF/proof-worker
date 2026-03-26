/*
 * Copyright (c) 2025-2026
 * Karlsruhe Institute of Technology - Institute for Automation and Applied Informatics (IAI)
 */
package edu.kit.iai.webis.proofworker.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import edu.kit.iai.webis.proofutils.LoggingHelper;
import edu.kit.iai.webis.proofutils.service.ConfigManagerService;
import edu.kit.iai.webis.proofutils.wrapper.Block;
import edu.kit.iai.webis.proofutils.wrapper.Execution;
import edu.kit.iai.webis.proofutils.wrapper.Program;
import edu.kit.iai.webis.proofutils.wrapper.Workflow;

/**
 * Configuration class for worker environment variables injected either from runtime arguments or from application.properties file
 */
@Configuration
public class WorkerConfig {

    private final ConfigManagerService configManagerService;

    /**
     * the local id (number) of the block in the workflow
     */
    @Value("${proof.worker.block.id}")
    private Integer localBlockId;

    /**
     * the id (uuid) of the block
     */
    @Value("${proof.worker.block.uuid}")
    private String globalBlockId;

    /**
     * the id (uuid) of the workflow
     */
    @Value("${proof.worker.workflow.uuid}")
    private String workflowUuid;

    /**
     * the execution id (uuid) of the simulation process
     */
    @Value("${proof.worker.workflow.executionId}")
    private String workflowExecutionId;

    /**
     * The PROOF workspace, the area where all data will be located to run a workflow. Default is '/tmp/proof'
     */
    @Value("${proof.workspace.directory:/tmp/proof}")
    private String workspaceDir;

    /**
     * The attachments directory, the area where all model files are located. Default is '/tmp/proof/attachments'
     */
    @Value("${proof.attachments.directory:/tmp/proof/attachments}")
    private String attachmentsDir;

    /**
     * The Userdata workspace, the area where all user data will be located which is used in a workflow. Default is '/tmp/proof/userdata'
     */
    @Value("${proof.userdata.directory:/tmp/proof/userdata}")
    private String userdataDir;

    /**
     * the logging level, Default is INFO
     */
    @Value("${proof.worker.logging.logLevel:INFO}")
    private String logLevel;

    /**
     * The logging directory for file-based logging, default is '/tmp'
     */
    @Value("${proof.worker.logging.directory:/tmp/proof/logs}")
    private String loggingDir;

    /**
     * The start number for automatically created reader socket ports, default is 50001
     */
    @Value("${proof.worker.socket.reader.startport:50001}")
    private Integer socketReaderStartPort;

    /**
     * The interval between automatically created reader or writer socket ports, default is 10
     */
    @Value("${proof.worker.socket.portinterval:10}")
    private Integer socketPortInterval;

    /**
     * The start number for automatically created writer socket ports, default is 50002
     */
    @Value("${proof.worker.socket.writer.startport:50002}")
    private Integer socketWriterStartPort;

    /**
     * for template (block) development affairs, if true (default), the model (Python) files in the working directory will be overridden.
     * If false, the model files are left as they are (e.g. edited by the developer) to test the models
     */
    @Value("${proof.worker.run.modelfiles.override:true}")
    private Boolean overrideModelFiles;

    /**
     * the time the process should wait for required inputs in seconds
     */
    @Value("${proof.worker.process.timeout:10}")
    private Integer processTimeout;

    /**
     * create WorkerConfig instance with given autowired arguments
     * @param configManagerService
     */
    public WorkerConfig(final ConfigManagerService configManagerService) {
    	this.configManagerService = configManagerService;
    }

    /**
	 * @return the processTimeout
	 */
	public Integer getProcessTimeout() {
		return this.processTimeout;
	}

	/**
	 * @return the workflow uuid
	 */
    public String getWorkflowUuid() {
        return this.workflowUuid;
    }

    /**
     * @return the execution id of the running workflow
     */
    public String getWorkflowExecutionId() {
        return this.workflowExecutionId;
    }

    /**
     * @return the global block id
     */
    public String getGlobalBlockId() {
    	return this.globalBlockId;
    }

    /**
     * @return the local block id
     */
    public Integer getLocalBlockId() {
        return this.localBlockId;
    }

    /**
     * get the PROOF working directory where all PROOF related data is located
     * @return the working directory
     */
    public String getWorkspaceDir() {
        return this.workspaceDir;
    }

    /**
     * get the attachments directory where the models run
     * @return the attachments directory
     */
    public String getAttachmentsDir() {
        return this.attachmentsDir;
    }

    /**
     * get the user data directory where the user data is located
     * @return the userdata directory
     */
    public String getUserdataDir() {
        return this.userdataDir;
    }

	/**
	 * get the logging level for the {@link LoggingHelper}, may be either
	 * Level.TRACE, Level.DEBUG, Level.INFO, Level.WARN, or Level.ERROR (see
	 * {@link org.slf4j.event.Level})
	 *
	 * @return the logging level for the {@link LoggingHelper}
	 */
    public String getLogLevel() {
        return this.logLevel;
    }

    /**
     * @return the logging directory for file-based logging, default is '/tmp'
     */
    public String getLoggingDir() {
        return this.loggingDir;
    }

	/**
     * @return the socketPort
     */
    public Integer getSocketReaderPort() {
        return this.socketReaderStartPort + (this.socketPortInterval * this.localBlockId );
    }

    /**
     * @return the socketWriterPort
     */
    public Integer getSocketWriterPort() {
    	return this.socketWriterStartPort + (this.socketPortInterval * this.localBlockId );
    }

    /**
     * get the unique {@link Workflow} (Singleton) that is used by the worker. The Workflow is identified with its id (workflowUuid)
     * @return the unique {@link Workflow} (Singleton) for the worker
     * <b>Note: </b> no {@link Block}s are retrieved explicitly from the worker.
     * The block instances are available with {@link Workflow#getBlocks()} or with  {@link Workflow#getBlock(String)}
     */
    @Bean
    public Workflow workflow() {
        final Workflow workflow = this.configManagerService.getWorkflow(this.workflowUuid);
    	return workflow;
    }

    /**
     * get the unique {@link Block} (Singleton) that is used by the worker. The block is identified with its identifiers (localBlockId and gloablBlockId)
     * @return the unique {@link Block} (Singleton) for the worker
     */
    @Bean
    public Block block() {
    	LoggingHelper.debug().log("retrieving Block with id: " + this.globalBlockId);
    	final Block block = this.configManagerService.getBlock(this.globalBlockId);
    	return block;
    }

    /**
     * get the unique {@link Execution} (Singleton) that is used by the worker.
     * @return the unique {@link Execution} (Singleton) for the worker
     */
    @Bean
    public Execution execution() {
    	LoggingHelper.debug().log("retrieving Execution with id: " + this.workflowExecutionId);
    	final Execution execution = this.configManagerService.getExecution(this.workflowExecutionId);
    	return execution;
    }

    /**
     * get the unique {@link Program} (Singleton) that is used by the worker.
     * @return the unique {@link Program} (Singleton) for the worker
     */
    @Bean
    public Program program() {
    	Block block = this.block();
    	try {
    		final Program program = this.configManagerService.getProgram(block.getProgramId());
    		return program;
		} catch (Exception e) {
			LoggingHelper.error().printStackTrace(e).log("Error loading program '%s' from database, Reason: %s", block.getProgramId(), e.getMessage());
		}
		return null;

    }


}
