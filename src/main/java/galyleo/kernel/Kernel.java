package galyleo.kernel;

import com.fasterxml.jackson.databind.JsonNode;
import galyleo.io.PrintStreamBuffer;
import galyleo.server.Channel;
import galyleo.server.Connection;
import galyleo.server.Dispatcher;
import galyleo.server.Message;
import galyleo.server.Server;
import galyleo.shell.Java;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Named;
import javax.inject.Singleton;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.zeromq.ZMQ;

/**
 * Galyleo Jupyter {@link Kernel}.
 *
 * @author {@link.uri mailto:ball@hcf.dev Allen D. Ball}
 * @version $Revision$
 */
@Named @Singleton
@SpringBootApplication
@NoArgsConstructor @ToString @Log4j2
public class Kernel extends Server implements ApplicationRunner {
    private final Channel.Heartbeat heartbeat = new Channel.Heartbeat(this);
    private final Channel.Control control = new Control();
    private final Channel.IOPub iopub = new Channel.IOPub(this);
    private final Channel.Stdin stdin = new Stdin();
    private final Channel.Shell shell = new Shell();
    private final AtomicInteger execution_count = new AtomicInteger(0);
    private InputStream in = null;
    private PrintStreamBuffer out = null;
    private PrintStreamBuffer err = null;
    private final Java java = new Java();

    @PostConstruct
    public void init() { restart(); }

    @PreDestroy
    public void destroy() { shutdown(); }

    private void restart() {
        in = new ByteArrayInputStream(new byte[] { });
        out = new PrintStreamBuffer();
        err = new PrintStreamBuffer();

        java.restart(in, out, err);

        setSession(String.join("-",
                               Kernel.class.getCanonicalName(),
                               String.valueOf(ProcessHandle.current().pid()),
                               String.valueOf(java.restarts())));
    }

    /**
     * Add a connection specified by a {@link Connection} {@link File}.
     *
     * @param   path            The path to the {@link Connection}
     *                          {@link File}.
     *
     * @throws  IOException     If the {@link File} cannot be opened or
     *                          parsed.
     */
    public void bind(String path) throws IOException {
        bind(getObjectMapper().readTree(new File(path)));
    }

    /**
     * Add a connection specified by a {@link JsonNode}.
     *
     * @param   node            The {@link JsonNode} descibing the
     *                          {@link Connection}.
     *
     * @throws  IOException     If the {@link JsonNode} cannot be parsed or
     *                          if the {@link Connection} cannot be
     *                          established.
     */
    public void bind(JsonNode node) throws IOException {
        var connection = new Connection(node);

        connection.connect(shell, control, iopub, stdin, heartbeat);

        log.info("Listening to {}", connection);
    }

    @Override
    public void run(ApplicationArguments arguments) throws Exception {
        var paths = arguments.getOptionValues("connection-file");

        if (! (paths == null || paths.isEmpty())) {
            for (var path : paths) {
                try {
                    bind(path);
                } catch (Exception exception) {
                    log.warn("{}", exception);
                }
            }
        } else {
            throw new IllegalArgumentException("No connection file specified");
        }
    }

    @ToString
    private class Control extends Channel.Control {
        public Control() { super(Kernel.this); }

        @Override
        protected void dispatch(Dispatcher dispatcher, ZMQ.Socket socket, Message message) {
            if (message.isRequest()) {
                super.dispatch(dispatcher, socket, message);
            } else {
                log.warn("Ignoring non-request {}", message.msg_type());
            }
        }

        private void shutdown(Dispatcher dispatcher, ZMQ.Socket socket,
                              Message request, Message reply) throws Exception {
            var restart = request.content().at("/restart").asBoolean();

            if (restart) {
                Kernel.this.restart();
            } else {
                submit(() -> getServer().shutdown());
            }
        }

        private void interrupt(Dispatcher dispatcher, ZMQ.Socket socket,
                               Message request, Message reply) throws Exception {
            java.stop();
        }

        private void debug(Dispatcher dispatcher, ZMQ.Socket socket,
                           Message request, Message reply) throws Exception {
            throw new UnsupportedOperationException();
        }
    }

    @ToString
    private class Stdin extends Channel.Stdin {
        public Stdin() { super(Kernel.this); }

        @Override
        protected void dispatch(Dispatcher dispatcher, ZMQ.Socket socket, Message message) {
            if (message.isReply()) {
                log.warn("Ignoring {}", message.msg_type());
            } else {
                log.warn("Ignoring non-reply {}", message.msg_type());
            }
        }
    }

    @ToString
    private class Shell extends Channel.Shell {
        public Shell() {
            super(Kernel.this, Kernel.this.iopub, Kernel.this.stdin);
        }

        @Override
        protected void dispatch(Dispatcher dispatcher, ZMQ.Socket socket, Message message) {
            if (message.isRequest()) {
                super.dispatch(dispatcher, socket, message);
            } else {
                log.warn("Ignoring non-request {}", message.msg_type());
            }
        }

        private void kernel_info(Dispatcher dispatcher, ZMQ.Socket socket,
                                 Message request, Message reply) throws Exception {
            var content = reply.content();

            content.put("protocol_version", PROTOCOL_VERSION);
            content.put("implementation", "galyleo");
            content.put("implementation_version", "1.0.0");

            var language_info = content.with("language_info");

            language_info.put("name", "java");
            language_info.put("version", System.getProperty("java.version"));
            language_info.put("mimetype", "text/plain");
            language_info.put("file_extension", ".java");

            content.set("language_info", language_info);

            var help_links = content.with("help_links");
        }

        private void execute(Dispatcher dispatcher, ZMQ.Socket socket,
                             Message request, Message reply) throws Exception {
            var code = request.content().at("/code").asText();
            var silent = request.content().at("/silent").asBoolean();
            var store_history = request.content().at("/store_history").asBoolean();
            var user_expressions = request.content().at("/user_expressions");
            var allow_stdin = request.content().at("/allow_stdin").asBoolean();
            var stop_on_error = request.content().at("/stop_on_error").asBoolean();

            try {
                if (! code.isEmpty()) {
                    if (! silent) {
                        if (store_history) {
                            execution_count.incrementAndGet();

                            var execute_input = request.reply(getSession(), "execute_input");

                            execute_input.content().put("code", code);
                            execute_input.content().put("execution_count", execution_count.intValue());

                            iopub.pub(execute_input);
                        }
                    }

                    java.execute(code);
                }
            } catch (Throwable throwable) {
                reply.content().setAll(toStandardErrorMessage(throwable, code));
            } finally {
                reply.content().put("execution_count", execution_count.intValue());

                if (reply.content().get("status").asText().equals("ok")) {
                    reply.content().withArray("payload");

                    var in = request.content().at("/user_expressions");
                    var iterator = in.fields();
                    var out = reply.content().with("user_expressions");

                    while (iterator.hasNext()) {
                        var entry = iterator.next();
                        var name = entry.getKey();
                        var expression = entry.getValue().asText();

                        try {
                            out.put(name, String.valueOf(java.evaluate(expression)));
                        } catch (Throwable throwable) {
                            out.set(name, toStandardErrorMessage(throwable, expression));
                        }
                    }
                }

                var stdout = out.toString();
                var stderr = err.toString();

                out.reset();
                err.reset();

                if (! silent) {
                    var execute_result = request.reply(getSession(), "execute_result");

                    execute_result.content().put("execution_count", execution_count.intValue());

                    var data = execute_result.content().with("data");
                    var metadata = execute_result.content().with("metadata");

                    data.put("text/plain", stdout);
                    metadata.with("text/plain");

                    stdout = "";

                    iopub.pub(execute_result);

                    if (! stdout.isEmpty()) {
                        iopub.stdout(request, stdout);
                    }

                    if (! stderr.isEmpty()) {
                        iopub.stderr(request, stderr);
                    }
                }
            }
        }

        private void inspect(Dispatcher dispatcher, ZMQ.Socket socket,
                             Message request, Message reply) throws Exception {
            throw new UnsupportedOperationException();
        }

        private void complete(Dispatcher dispatcher, ZMQ.Socket socket,
                              Message request, Message reply) throws Exception {
            throw new UnsupportedOperationException();
        }

        private void history(Dispatcher dispatcher, ZMQ.Socket socket,
                             Message request, Message reply) throws Exception {
            throw new UnsupportedOperationException();
        }

        private void is_complete(Dispatcher dispatcher, ZMQ.Socket socket,
                                 Message request, Message reply) throws Exception {
            var code = request.content().at("/code");

            reply.content().put("status", "unknown");
        }

        private void connect(Dispatcher dispatcher, ZMQ.Socket socket,
                             Message request, Message reply) throws Exception {
            throw new UnsupportedOperationException();
        }

        private void comm_info(Dispatcher dispatcher, ZMQ.Socket socket,
                               Message request, Message reply) throws Exception {
            throw new UnsupportedOperationException();
        }
    }
}
