/*
 * Copyright (c) 2025-2026
 * Karlsruhe Institute of Technology - Institute for Automation and Applied Informatics (IAI)
 */
package edu.kit.iai.webis.proofworker;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.lang.ProcessBuilder.Redirect;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.listener.adapter.MessageListenerAdapter;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import edu.kit.iai.webis.proofutils.Colors;
import edu.kit.iai.webis.proofutils.LoggingHelper;
import edu.kit.iai.webis.proofutils.MessageBuilder;
import edu.kit.iai.webis.proofutils.helper.NameHelper;
import edu.kit.iai.webis.proofutils.io.MQNotifyProducer;
import edu.kit.iai.webis.proofutils.message.MessageType;
import edu.kit.iai.webis.proofutils.message.NotifyMessage;
import edu.kit.iai.webis.proofutils.model.CommunicationType;
import edu.kit.iai.webis.proofutils.model.InterfaceType;
import edu.kit.iai.webis.proofutils.model.SimulationPhase;
import edu.kit.iai.webis.proofutils.model.SimulationStatus;
import edu.kit.iai.webis.proofutils.model.SyncStrategy;
import edu.kit.iai.webis.proofutils.service.ConsumerManager;
import edu.kit.iai.webis.proofutils.wrapper.Block;
import edu.kit.iai.webis.proofutils.wrapper.Execution;
import edu.kit.iai.webis.proofutils.wrapper.Input;
import edu.kit.iai.webis.proofutils.wrapper.Program;
import edu.kit.iai.webis.proofworker.config.WorkerConfig;
import edu.kit.iai.webis.proofworker.exception.BlockConfigException;
import edu.kit.iai.webis.proofworker.exception.IOInterfaceSetupException;
import edu.kit.iai.webis.proofworker.io.MQInitValueHandler;
import edu.kit.iai.webis.proofworker.io.MQSyncHandler;
import edu.kit.iai.webis.proofworker.io.MQValueHandler;
import edu.kit.iai.webis.proofworker.model.InputQueueNameMapping;
import edu.kit.iai.webis.proofworker.model.ModelInputInterface;
import edu.kit.iai.webis.proofworker.model.OutputQueueNameMapping;
import edu.kit.iai.webis.proofworker.services.InputFileWriter;
import edu.kit.iai.webis.proofworker.services.InputSocketWriter;
import edu.kit.iai.webis.proofworker.services.InputStreamWriter;
import edu.kit.iai.webis.proofworker.services.NotifyController;
import edu.kit.iai.webis.proofworker.services.OutputSocketReader;
import edu.kit.iai.webis.proofworker.services.OutputStreamReader;
import edu.kit.iai.webis.proofworker.services.SyncController;
import edu.kit.iai.webis.proofworker.services.ValueController;
import edu.kit.iai.webis.proofworker.util.MappingHelper;
import edu.kit.iai.webis.proofworker.util.ReaderHelper;
import edu.kit.iai.webis.proofworker.util.StringTemplates;
import edu.kit.iai.webis.proofworker.util.WriterHelper;

@Profile({"dev", "prod", "debug"})
@Component
public class StartupRunner implements CommandLineRunner {

    @Value("${app.name}")
    private String appName;
    private final AmqpAdmin amqpAdmin;

	private final Execution execution;
    private final Block block;
    private final Program program;
    private final MappingHelper mappingHelper;
    private final ConsumerManager consumerManager;
    private final SyncController syncController;
    private final ValueController valueController;
    private final NotifyController notifyController;
    private final ReaderHelper readerHelper;
    private final WriterHelper writerHelper;
    private final WorkerConfig workerConfig;
    private final MQNotifyProducer notifyProducer;
    private PipedOutputStream outputStream;
    private PipedInputStream inputStream;
    private PipedInputStream pipedInputStream;
    private PipedOutputStream pipedOutputStream;
    private boolean isLoggingLevelDebugOrTrace = true;

    public StartupRunner(@NonNull final AmqpAdmin amqpAdmin, final Program program,
                         final Block block,
                         final Execution execution,
                         final MappingHelper mappingHelper,
                         final ValueController valueController,
                         final SyncController syncController,
                         final NotifyController notifyController,
                         final ConsumerManager consumerFactory,
                         final ReaderHelper readerHelper,
                         final WriterHelper writerHelper,
                         final WorkerConfig workerConfig,
                         final MQNotifyProducer notifyProducer) {
        this.amqpAdmin = amqpAdmin;
        this.program = program;
        this.mappingHelper = mappingHelper;
        this.valueController = valueController;
        this.syncController = syncController;
        this.notifyController = notifyController;
        this.consumerManager = consumerFactory;
        this.readerHelper = readerHelper;
        this.writerHelper = writerHelper;
        this.workerConfig = workerConfig;
        this.block = block;
        this.execution = execution;
        this.notifyProducer = notifyProducer;
    }

    public void declareLogQueue() {
        final var queue = QueueBuilder.durable("logs.block." + this.appName + ".execution." + this.execution.getId()).build();
        final var binding = BindingBuilder.bind(queue).to(new TopicExchange("logs")).with(queue.getName());
        this.amqpAdmin.declareQueue(queue);
        this.amqpAdmin.declareBinding(binding);
    }

    /**
     * Main initiator method
     */
    @Override
    public void run(final String... args) {
        LoggingHelper.printColored(false);
        LoggingHelper.logSourcePosition(true);
        LoggingHelper.info().messageColor(Colors.ANSI_WHITE).log("Change colour to: white");
        // Set the log level from configuration
        LoggingHelper.setLogLevel(this.workerConfig.getLogLevel());
        LoggingHelper.info().log("Current LOG Level is " + LoggingHelper.getLogLevel());

        try {
            this.declareLogQueue();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        this.isLoggingLevelDebugOrTrace = LoggingHelper.isLevelDebugOrTrace();
        if (this.isLoggingLevelDebugOrTrace) {
            if ((this.execution.getInterfaceType() == InterfaceType.SOCKET)) {
                LoggingHelper.debug().log("===========> StartupRunner:: Ports:  READER: " + this.workerConfig.getSocketReaderPort() + ",  WRITER: " + this.workerConfig.getSocketWriterPort());
            }
            else {
                LoggingHelper.debug().log("===========> StartupRunner:: Sockets are not used ..." );
            }
        }


        // always STEPBASED:    if (this.block.getCommunicationParadigm().equals(CommunicationParadigm.STEPBASED)) {
        if (true) {
            // Use stepbased execution
        	LoggingHelper.info().log(StringTemplates.STARTING_BLOCK.formatted( this.block.getIndex(), this.block.getId()));
            this.setupSyncQueue(NameHelper.getSyncQueueName(this.workerConfig.getWorkflowExecutionId(), this.block));

            this.setupStepBasedInputs();
            this.setupStepBasedOutputs();
            this.setupIOStreams();
            this.setupWrapperCommunication();
            this.consumerManager.startConsumers();  // start communication
            this.startExecutor();

            if (this.execution.getInterfaceType() == InterfaceType.SOCKET) {
                this.finishSocketCommunication();
            }

            LoggingHelper.info().log(StringTemplates.BLOCK_STARTED.formatted( this.block.getIndex(), this.block.getId()));
        }
        // FEAT: Implement event based workflow
    }

    /**
     * Setup sync queue for this block for TACT messages
     */
    private void setupSyncQueue(String syncQueueName) {
        LoggingHelper.debug().log("instantiating SYNC Queue:  " + syncQueueName);
        this.consumerManager.instantiateReceiver(syncQueueName, MessageType.SYNC,
                new MessageListenerAdapter(new MQSyncHandler(this.syncController, this.notifyController),
                        new Jackson2JsonMessageConverter()));
    }

    /**
     * Setup input and output pipe stream for the connection to the executed program
     */
    private void setupIOStreams() {
        try {
            // SOCKETS:  POPs nur bei normalen streams, ansonsten
            this.outputStream = new PipedOutputStream();
            this.pipedInputStream = new PipedInputStream(this.outputStream);
            this.inputStream = new PipedInputStream();
            this.pipedOutputStream = new PipedOutputStream(this.inputStream);
        } catch (final Exception e) {
            final var message = StringTemplates.COULD_NOT_SETUP_MAIN_IO_PIPE_STREAMS;
            LoggingHelper.error().log(message);
            throw new IOInterfaceSetupException(message, e);
        }
    }

    /**
     * Setup inputs of the block and their IO definition, including StreamWriter, Filewriter and Socket
     */
    private void setupWrapperCommunication() {
    	for( SimulationPhase sPhase : SimulationPhase.values() ) {
    		ModelInputInterface mii = ModelInputInterface.createModelInputInterface(sPhase, this.block);
    		if( mii != null ) {
    			if (this.block.getInterfaceType() == InterfaceType.STDIO) {
    				final var inputStreamWriter = new InputStreamWriter(this.pipedOutputStream, this.writerHelper);
    				mii.setWriter(inputStreamWriter);
    				LoggingHelper.info().messageColor(Colors.ANSI_PURPLE).log("StartupRunner::  inputStreamWriter created" +
    						" with PipedOutputStream: "/* + this.pipedOutputStream*/);
    			}
    			else if(this.block.getInterfaceType() == InterfaceType.FILE) {
    				// Using FileWriter for input only
    				final var path = NameHelper.getIOPath(this.workerConfig.getWorkspaceDir(), this.workerConfig.getWorkflowExecutionId(), this.block);
    				if (path != null) {
    					final var fileWriter = new InputFileWriter(path.toString(), this.writerHelper);
    					mii.setWriter(fileWriter);
    				} else {
    					final var message = StringTemplates.PARAMETER_PATH_NOT_PRESENT;
    					LoggingHelper.error().log(message);
    					throw new BlockConfigException(message);
    				}
    			}
    		}
    	}

		for( SimulationPhase  phase : SimulationPhase.values() ) {
			Map<String, String> mappings =  this.block.getInputNameMappings(phase);
			if( mappings != null ) {
				LoggingHelper.debug().messageColor(Colors.ANSI_PURPLE).log( "Phase %s of block '%s' has the input(s): %s     and" +
                        " the outputs %s",
						phase, this.block.getName(), mappings, this.block.getOutputNameMappings(SimulationPhase.INIT));
			}
		}

    }

    private void finishSocketCommunication() {
        final InputSocketWriter inputSocketWriter = new InputSocketWriter(this.workerConfig.getSocketWriterPort(),
                this.writerHelper);
        LoggingHelper.info().messageColor(Colors.ANSI_PURPLE).log("StartupRunner::  inputSocketWriter created: " + inputSocketWriter);
        ModelInputInterface.setAllWriters(inputSocketWriter);
    }

    /**
     * Preparing stepbased input queues with consumers for VALUE messages
     */
    private void setupStepBasedInputs() {

    	Map<String, String> startValues = this.execution.getExecStartValues();
    	Map<String, String> defaultValues = this.execution.getExecDefaultValues();

    	this.block.getDynamicInputs().forEach((final var input) -> {

    			final var queueName = NameHelper.getInputQueueName(
    					this.workerConfig.getWorkflowExecutionId(),
    					this.workerConfig.getWorkflowUuid(),
    					this.block.getIndex(),
    					input.getName());

                    this.consumerManager.instantiateReceiver(queueName, MessageType.VALUE,
                                    new Jackson2JsonMessageConverter()));

    			final var inputSource = new InputQueueNameMapping(input, queueName);
    			this.valueController.addInputQueueNameMapping(inputSource);
    			LoggingHelper.debug().log("instantiating ValueReceiver for DYNAMIC Input '%s', Phase = %s, Queue name = %s",
    					input.getName(), input.getSimulationPhase(), queueName);

    			LoggingHelper.debug().log("setting start value and default value for input '%s': startValue=%s, defaultValue=%s",
    					input.getName(), startValues.get(input.getId()), defaultValues.get(input.getId()));
    			input.setStartValue(startValues.get(input.getId()));
    			input.setDefaultValue(defaultValues.get(input.getId()));
    	});

        final List<Input> staticInputs = this.block.getStaticInputs();
    	if(staticInputs.size() > 0) {
    		final var staticInputsQueueName = NameHelper.getStaticInputsQueueName(this.workerConfig.getWorkflowExecutionId(),this.block);
    		this.consumerManager.instantiateReceiver(staticInputsQueueName, MessageType.VALUE,
    				new MessageListenerAdapter(new MQInitValueHandler(this.valueController, this.notifyController, staticInputs),
    						new Jackson2JsonMessageConverter()));

    		staticInputs.forEach(input -> {
    			final var inputQNM = new InputQueueNameMapping(input, staticInputsQueueName);
    			this.valueController.addInputQueueNameMapping(inputQNM);
    			LoggingHelper.debug().log("InputQueueNameMapping for STATIC input '%s', Queue name = %s ", input.getName(), staticInputsQueueName);
    		});
    	}
    }

    /**
     * Preparing stepbased output queues with producers for VALUE messages
     */
    private void setupStepBasedOutputs() {
        // Iterate over outputs
        if (this.block.getOutputs() != null) {
            this.block.getOutputs().values().forEach((final var output) -> {
                if (output.getCommunicationType().equals(CommunicationType.STEPBASED)
                        || output.getCommunicationType().equals(CommunicationType.STEPBASED_STATIC)) {
                	final var queueName = NameHelper.getOutputQueueName(
                            this.workerConfig.getWorkflowExecutionId(),
                            this.workerConfig.getWorkflowUuid(),
                            this.block.getIndex(),
                            output.getName());
                    final var outputQNM = new OutputQueueNameMapping(output, queueName);
                    this.valueController.addOutputQueueNameMapping(outputQNM);
                    LoggingHelper.debug().log("OutputQueueNameMapping: " + output.getName() + " - " + queueName);
                }
            });
        }
    }

    /**
     * Setup program start command and start executor
     */
    private void startExecutor() {
        LoggingHelper.debug().log("starting executor ...  #inputs=%d, #outputs=%d".formatted(this.block.getInputs().size(),this.block.getOutputs().size()));

        if ( this.block.getInputs().size() > 0 || this.block.getOutputs().size() > 0) {

            final List<String> args = new ArrayList<String>();
            if (Objects.equals(this.program.getRuntime().getCommand(), "java -jar")){
                // For java -jar, split the command into "java" and "-jar" to avoid issues with ProcessBuilder
                args.add("java");
                args.add("-jar");
            }
            else {
                args.add(this.program.getRuntime().getCommand());
            }
            String fn = this.program.getEntryPointFileName();
            String wrapperFileName = fn.substring(fn.lastIndexOf(':')+1);
            String attachmentsDir = this.workerConfig.getAttachmentsDir();
            // Use the absolute model path.
            String modelPath = wrapperFileName;
            if (!modelPath.contains(attachmentsDir)) {
				// Add the attachments directory if not yet stored in the path.
                modelPath = Paths.get(attachmentsDir, wrapperFileName).toString();
			}
            args.add(modelPath);

            args.add("--local_block_id");
            args.add("" + this.workerConfig.getLocalBlockId());

            args.add("--waitForSync");
            args.add(this.block.getSyncStrategy() == SyncStrategy.WAIT_FOR_SYNC ? "true" : "false");

            args.add("--logLevel");
            args.add(this.workerConfig.getLogLevel());

            args.add("--userdata_directory");
            args.add(this.workerConfig.getUserdataDir());

            args.add("--inputs");
            args.add(this.mappingHelper.getInputMappingsAsJsonString());
            args.add("--outputs");
            args.add(this.mappingHelper.getOutputMappingsAsJsonString());

            if (this.execution.getInterfaceType() == InterfaceType.SOCKET) {
                args.add("--ports");
                args.add("[" + this.workerConfig.getSocketReaderPort() + "," + this.workerConfig.getSocketWriterPort() + "]");
            }
            args.add("--loggingDir");
            args.add(this.workerConfig.getLoggingDir());

            LoggingHelper.info().log("executing Wrapper with command:");
            args.forEach(a -> System.out.print(a + " "));
            System.out.println();

            ProcessBuilder pb = new ProcessBuilder(args)
                    .redirectOutput(Redirect.PIPE)
                    .redirectInput(Redirect.PIPE);

            Process p;
            try {
                ExecutorService streamHandlers = Executors.newFixedThreadPool(1);

                p = pb.start();

                /**
                 * get the error stream to print the background process and to empty its buffer
                 */
                InputStream stdErr = p.getErrorStream();
                streamHandlers.execute(() -> this.handleStream(stdErr));

                if (this.execution.getInterfaceType() == InterfaceType.SOCKET) {
                    final var outputSocketReader = new OutputSocketReader(this.workerConfig.getSocketReaderPort(),
                            this.readerHelper);
                    outputSocketReader.read();
                } else {

                    final var outputStreamReader = new OutputStreamReader(new BufferedInputStream(p.getInputStream())
                            , this.readerHelper);
                    outputStreamReader.read();
                    final InputStreamWriter inputStreamWriter =
                            new InputStreamWriter(new BufferedOutputStream(p.getOutputStream()), this.writerHelper);

                    ModelInputInterface.setAllWriters(inputStreamWriter);

                }
            } catch (IOException e) {
            	String errMsg = "Error starting wrapper for block %d (%s)! Reason: %s"
            			.formatted(this.workerConfig.getLocalBlockId(), this.workerConfig.getGlobalBlockId(), e.getMessage() );
            	NotifyMessage nMsg = (NotifyMessage) MessageBuilder.init(MessageType.NOTIFY)
            			.errorText(errMsg)
            			.blockStatus(SimulationStatus.ERROR_INIT).build();
            	this.notifyController.sendNotifyMessage(nMsg);
//                e.printStackTrace();
            }
        }
    }

    private void handleStream(InputStream inputStream) {
        try (BufferedReader stdOutReader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line = "";
            while ((line = stdOutReader.readLine()) != null) {
                // Forward Python log line as-is; setSimplifiedFormat routes it to the bare-message
                // CONSOLE-PY/AMQP-PY appenders without adding a Java header on top.
                // MDC.clear() inside log() automatically removes the simplifiedFormat key.
                LoggingHelper.setSimplifiedFormat(true);
                modelLevelLogger(line).messageColor(Colors.ANSI_CYAN).log("MODEL-CONSOLE: " + line);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Map the Python log level embedded in a log line to the corresponding {@link LoggingHelper} builder.
     * Python lines contain patterns like {@code [INFO]}, {@code [DEBUG]}, {@code [WARN]}, {@code [ERROR]}, {@code [CRITICAL]}.
     * Falls back to INFO for unrecognised or non-Python lines.
     */
    private static LoggingHelper.LogBuilder modelLevelLogger(String line) {
        if (line.contains("[ERROR]") || line.contains("[CRITICAL]")) {
			return LoggingHelper.error();
		}
        if (line.contains("[WARN ]") || line.contains("[WARN]")) {
			return LoggingHelper.warn();
		}
        // DEBUG and TRACE introduce stacktrace, which we want to avoid for Python logs,
        // so we treat them as INFO and rely on the original Python level in the message text.
        //if (line.contains("[DEBUG]"))                                 return LoggingHelper.debug();
        //if (line.contains("[TRACE]"))                                 return LoggingHelper.trace();
        return LoggingHelper.info();
    }
}
