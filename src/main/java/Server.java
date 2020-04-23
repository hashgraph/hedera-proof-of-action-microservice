import com.google.common.flogger.FluentLogger;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;

public class Server extends AbstractVerticle {
    private static final FluentLogger logger = FluentLogger.forEnclosingClass();

    private HttpServer httpServer;

    public static void main(String[] args) {
        Vertx.vertx().deployVerticle(new Server());
    }

    @Override
    public void start(Promise<Void> startPromise) {
        // saves the server so we can cleanly exit later
        httpServer = vertx.createHttpServer();

        httpServer.requestHandler(req -> {
            // terminate immediately with "Hello, World"
            req.response().end("Hello, World");
        });

        httpServer.listen(8080, "0.0.0.0", v -> {
            if (v.succeeded()) {
                logger.atInfo().log("listening on http://0.0.0.0:8080/");

                startPromise.complete();
            } else {
                startPromise.fail(v.cause());
            }
        });
    }

    @Override
    public void stop(Future<Void> stopFuture) {
        httpServer.close(stopFuture);
    }
}
