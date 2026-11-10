import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LocalApiServer {
    private static final int MAX_ITEM_ID = 100;
    private static final int WORKER_THREADS = 8;
    private static final Pattern ITEM_PATH = Pattern.compile("/api/items/([1-9][0-9]{0,2})");

    private LocalApiServer() {
    }

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(System.getProperty("port", "8080"));
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Port must be between 1 and 65535");
        }

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 128);
        ExecutorService workers = Executors.newFixedThreadPool(WORKER_THREADS);
        server.setExecutor(workers);
        server.createContext("/", LocalApiServer::handleRequest);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop(1);
            workers.shutdown();
        }));

        server.start();
        System.out.printf("Local API started at http://127.0.0.1:%d%n", port);
        System.out.printf("Worker threads: %d%n", WORKER_THREADS);
        System.out.println("GET /health");
        System.out.println("GET /api/items/{id}, id = 1..100");
        System.out.println("Press Ctrl+C to stop");
    }

    private static void handleRequest(HttpExchange exchange) throws IOException {
        try {
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Allow", "GET");
                sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }

            String path = exchange.getRequestURI().getPath();
            if ("/health".equals(path)) {
                sendJson(exchange, 200, "{\"status\":\"UP\"}");
                return;
            }

            Matcher itemPath = ITEM_PATH.matcher(path);
            if (itemPath.matches()) {
                int id = Integer.parseInt(itemPath.group(1));
                if (id <= MAX_ITEM_ID) {
                    String body = "{\"id\":" + id
                            + ",\"name\":\"Item " + id
                            + "\",\"price\":" + (100 + id) + "}";
                    sendJson(exchange, 200, body);
                    return;
                }
            }

            sendJson(exchange, 404, "{\"error\":\"Not found\"}");
        } finally {
            exchange.close();
        }
    }

    private static void sendJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        if ("HEAD".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(status, -1);
        } else {
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
        }
    }
}
