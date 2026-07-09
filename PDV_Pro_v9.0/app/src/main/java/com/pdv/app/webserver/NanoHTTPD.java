package com.pdv.app.webserver;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * NanoHTTPD - Servidor HTTP embutido ultra-leve para Android.
 * Versão customizada para o PDV Pro - Sistema de Chamados.
 * 
 * v6.9.1 - Corrigido bug de leitura do body para requisições com caracteres UTF-8 multibyte.
 *           O Content-Length é em bytes, mas a leitura anterior usava chars, causando
 *           travamento/timeout quando o body continha acentos (ã, é, ç, etc.).
 */
public abstract class NanoHTTPD {
    private final int port;
    private ServerSocket serverSocket;
    private Thread listenerThread;
    private final ExecutorService threadPool;
    private boolean isRunning = false;

    public NanoHTTPD(int port) {
        this.port = port;
        this.threadPool = Executors.newFixedThreadPool(8);
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        serverSocket.setReuseAddress(true);
        isRunning = true;
        listenerThread = new Thread(() -> {
            while (isRunning && !serverSocket.isClosed()) {
                try {
                    Socket socket = serverSocket.accept();
                    socket.setSoTimeout(30000);
                    threadPool.submit(() -> handleConnection(socket));
                } catch (IOException e) {
                    if (isRunning) {
                        e.printStackTrace();
                    }
                }
            }
        }, "NanoHTTPD-Listener");
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    public void stop() {
        isRunning = false;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) {}
        threadPool.shutdownNow();
    }

    public boolean isAlive() {
        return isRunning && serverSocket != null && !serverSocket.isClosed();
    }

    public int getPort() {
        return port;
    }

    private void handleConnection(Socket socket) {
        try {
            InputStream is = socket.getInputStream();

            // v6.9.1 - Ler headers usando BufferedInputStream para preservar bytes do body
            // Primeiro lemos os headers como texto linha a linha
            // Depois lemos o body como bytes brutos e convertemos para String UTF-8
            ByteArrayOutputStream headerBytes = new ByteArrayOutputStream();
            int prev = -1;
            int curr;
            boolean headersDone = false;
            // Ler byte a byte até encontrar \r\n\r\n (fim dos headers)
            int crlfCount = 0;
            while ((curr = is.read()) != -1) {
                headerBytes.write(curr);
                if (curr == '\n' && prev == '\r') {
                    crlfCount++;
                    if (crlfCount >= 2) {
                        headersDone = true;
                        break;
                    }
                } else if (curr != '\r') {
                    crlfCount = 0;
                }
                prev = curr;
            }

            if (!headersDone && headerBytes.size() == 0) {
                socket.close();
                return;
            }

            String headerSection = new String(headerBytes.toByteArray(), "UTF-8");
            String[] headerLines = headerSection.split("\r\n");

            if (headerLines.length == 0 || headerLines[0].isEmpty()) {
                socket.close();
                return;
            }

            // Parse request line
            String requestLine = headerLines[0];
            String[] parts = requestLine.split(" ");
            if (parts.length < 2) {
                socket.close();
                return;
            }

            String method = parts[0];
            String uri = parts[1];

            // Parse headers
            Map<String, String> headers = new HashMap<>();
            int contentLength = 0;
            for (int i = 1; i < headerLines.length; i++) {
                String line = headerLines[i];
                if (line.isEmpty()) continue;
                int idx = line.indexOf(':');
                if (idx > 0) {
                    String key = line.substring(0, idx).trim().toLowerCase();
                    String value = line.substring(idx + 1).trim();
                    headers.put(key, value);
                    if ("content-length".equals(key)) {
                        try { contentLength = Integer.parseInt(value); } catch (NumberFormatException ignored) {}
                    }
                }
            }

            // v6.9.1 - Parse body for POST: ler BYTES (não chars) baseado no Content-Length
            String body = "";
            if ("POST".equalsIgnoreCase(method) && contentLength > 0) {
                byte[] bodyBytes = new byte[contentLength];
                int totalRead = 0;
                while (totalRead < contentLength) {
                    int read = is.read(bodyBytes, totalRead, contentLength - totalRead);
                    if (read == -1) break;
                    totalRead += read;
                }
                // Converter bytes para String UTF-8 corretamente
                body = new String(bodyBytes, 0, totalRead, "UTF-8");
            }

            // Parse query params
            Map<String, String> params = new HashMap<>();
            String path = uri;
            int qIdx = uri.indexOf('?');
            if (qIdx >= 0) {
                path = uri.substring(0, qIdx);
                parseParams(uri.substring(qIdx + 1), params);
            }

            if ("POST".equalsIgnoreCase(method) && !body.isEmpty()) {
                String ct = headers.get("content-type");
                if (ct != null && ct.contains("application/x-www-form-urlencoded")) {
                    parseParams(body, params);
                } else if (ct != null && ct.contains("application/json")) {
                    params.put("__json_body__", body);
                }
            }

            Response response = serve(method, path, params, headers, body);
            writeResponse(socket.getOutputStream(), response);

        } catch (Exception e) {
            try {
                Response err = newResponse(Status.INTERNAL_ERROR, "text/plain", "500 Internal Server Error");
                writeResponse(socket.getOutputStream(), err);
            } catch (Exception ignored) {}
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    private void parseParams(String query, Map<String, String> params) {
        if (query == null || query.isEmpty()) return;
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                try {
                    String key = URLDecoder.decode(pair.substring(0, eq), "UTF-8");
                    String val = URLDecoder.decode(pair.substring(eq + 1), "UTF-8");
                    params.put(key, val);
                } catch (Exception ignored) {}
            }
        }
    }

    private void writeResponse(OutputStream os, Response response) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("HTTP/1.1 ").append(response.status.code).append(" ").append(response.status.desc).append("\r\n");
        sb.append("Content-Type: ").append(response.mimeType).append("\r\n");
        sb.append("Access-Control-Allow-Origin: *\r\n");
        sb.append("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n");
        sb.append("Access-Control-Allow-Headers: Content-Type\r\n");
        sb.append("Cache-Control: no-cache\r\n");
        sb.append("Connection: close\r\n");

        byte[] data = null;
        if (response.data != null) {
            data = response.data;
        } else if (response.text != null) {
            data = response.text.getBytes("UTF-8");
        }

        if (data != null) {
            sb.append("Content-Length: ").append(data.length).append("\r\n");
        }
        sb.append("\r\n");

        os.write(sb.toString().getBytes("UTF-8"));
        if (data != null) {
            os.write(data);
        }
        os.flush();
    }

    public abstract Response serve(String method, String uri, Map<String, String> params, Map<String, String> headers, String body);

    public static Response newResponse(Status status, String mimeType, String text) {
        Response r = new Response();
        r.status = status;
        r.mimeType = mimeType;
        r.text = text;
        return r;
    }

    public static Response newBinaryResponse(Status status, String mimeType, byte[] data) {
        Response r = new Response();
        r.status = status;
        r.mimeType = mimeType;
        r.data = data;
        return r;
    }

    public static class Response {
        public Status status;
        public String mimeType;
        public String text;
        public byte[] data;
    }

    public enum Status {
        OK(200, "OK"),
        CREATED(201, "Created"),
        NO_CONTENT(204, "No Content"),
        BAD_REQUEST(400, "Bad Request"),
        NOT_FOUND(404, "Not Found"),
        METHOD_NOT_ALLOWED(405, "Method Not Allowed"),
        INTERNAL_ERROR(500, "Internal Server Error");

        public final int code;
        public final String desc;
        Status(int code, String desc) {
            this.code = code;
            this.desc = desc;
        }
    }
}
