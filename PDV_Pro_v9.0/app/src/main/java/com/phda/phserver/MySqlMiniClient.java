package com.phda.phserver;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/**
 * Cliente MySQL/MariaDB minimal usando autenticacao
 * mysql_native_password. Suporta:
 * <ul>
 *   <li>{@link #executeStatement} - comandos sem resultset (CREATE/DROP/USE/...)</li>
 *   <li>{@link #executeQuery} - SELECT/SHOW retornando lista de linhas</li>
 * </ul>
 *
 * <p>Implementacao manual do protocolo wire (Initial Handshake V10 +
 * HandshakeResponse41 + COM_QUERY) - ref:
 * https://dev.mysql.com/doc/dev/mysql-server/latest/page_protocol_basic_packets.html
 */
final class MySqlMiniClient {

    private static final Charset UTF8 = Charset.forName("UTF-8");

    // Capability flags
    private static final int CLIENT_LONG_PASSWORD     = 0x00000001;
    private static final int CLIENT_FOUND_ROWS        = 0x00000002;
    private static final int CLIENT_LONG_FLAG         = 0x00000004;
    private static final int CLIENT_PROTOCOL_41       = 0x00000200;
    private static final int CLIENT_TRANSACTIONS      = 0x00002000;
    private static final int CLIENT_SECURE_CONNECTION = 0x00008000;
    private static final int CLIENT_PLUGIN_AUTH       = 0x00080000;

    /** Resultado de {@link #executeQuery}. */
    static final class QueryResult {
        final List<String> columns;
        final List<List<String>> rows;
        final String errorMessage;

        QueryResult(List<String> columns, List<List<String>> rows, String errorMessage) {
            this.columns = columns;
            this.rows = rows;
            this.errorMessage = errorMessage;
        }

        boolean isError() { return errorMessage != null; }
    }

    private MySqlMiniClient() {}

    // ====================================================================
    // API publica
    // ====================================================================

    /**
     * Abre conexao, autentica e executa um comando sem resultset (CREATE/DROP/...).
     * Retorna string descrevendo o resultado: "OK affectedRows=N" ou
     * "ERR <codigo> <mensagem>".
     */
    static String executeStatement(String host, int port, String user,
                                   String password, String sql, int timeoutMs)
            throws IOException {
        Socket s = openAndAuth(host, port, user, password, timeoutMs);
        try {
            DataInputStream in = new DataInputStream(s.getInputStream());
            OutputStream out = s.getOutputStream();

            sendQuery(out, sql);

            byte[] reply = readPacket(in);
            if (reply.length == 0) {
                throw new IOException("resposta vazia do servidor pos-query");
            }
            int rh = reply[0] & 0xFF;
            if (rh == 0xFF) {
                return parseErr(reply);
            }
            if (rh == 0x00 || rh == 0xFE) {
                long affected = parseLenEnc(reply, new int[]{1});
                return "OK affectedRows=" + affected;
            }
            // Tem resultset (nao deveria aqui, mas drenamos pra fechar limpo)
            int columnCount = (int) parseLenEnc(reply, new int[]{0});
            for (int i = 0; i < columnCount; i++) readPacket(in);
            // EOF/columns -> rows...
            while (true) {
                byte[] r = readPacket(in);
                if (r.length > 0 && (r[0] & 0xFF) == 0xFE && r.length < 9) break;
            }
            return "OK (resultset descartado)";
        } finally {
            try { s.close(); } catch (IOException ignored) {}
        }
    }

    /**
     * Abre conexao, autentica, executa um SELECT/SHOW e retorna o resultset.
     */
    static QueryResult executeQuery(String host, int port, String user,
                                    String password, String sql, int timeoutMs)
            throws IOException {
        Socket s = openAndAuth(host, port, user, password, timeoutMs);
        try {
            DataInputStream in = new DataInputStream(s.getInputStream());
            OutputStream out = s.getOutputStream();

            sendQuery(out, sql);

            byte[] header = readPacket(in);
            if (header.length == 0) {
                throw new IOException("resposta vazia");
            }
            int rh = header[0] & 0xFF;
            if (rh == 0xFF) {
                return new QueryResult(null, null, parseErr(header));
            }
            if (rh == 0x00) {
                // OK packet - sem resultset (mas o usuario pediu query). Devolve vazio.
                return new QueryResult(new ArrayList<String>(),
                        new ArrayList<List<String>>(), null);
            }

            // header = column count (lenenc int)
            int columnCount = (int) parseLenEnc(header, new int[]{0});

            // ler N colunas
            List<String> columns = new ArrayList<String>(columnCount);
            for (int i = 0; i < columnCount; i++) {
                byte[] colDef = readPacket(in);
                columns.add(parseColumnName(colDef));
            }

            // ler EOF de fim das colunas
            byte[] eof1 = readPacket(in);
            if (eof1.length == 0 || (eof1[0] & 0xFF) != 0xFE) {
                throw new IOException(
                        "esperado EOF apos columns, recebi byte 0x"
                                + (eof1.length == 0 ? "?" : Integer.toHexString(eof1[0] & 0xFF)));
            }

            // ler linhas ate EOF final
            List<List<String>> rows = new ArrayList<List<String>>();
            while (true) {
                byte[] row = readPacket(in);
                if (row.length == 0) {
                    break;
                }
                int b = row[0] & 0xFF;
                if (b == 0xFE && row.length < 9) {
                    break; // EOF final
                }
                if (b == 0xFF) {
                    return new QueryResult(columns, rows, parseErr(row));
                }
                List<String> values = new ArrayList<String>(columnCount);
                int[] cur = {0};
                for (int i = 0; i < columnCount; i++) {
                    int p = cur[0];
                    if (p >= row.length) {
                        values.add(null);
                        continue;
                    }
                    int marker = row[p] & 0xFF;
                    if (marker == 0xFB) {
                        cur[0] = p + 1;
                        values.add(null);
                    } else {
                        int len = (int) parseLenEnc(row, cur);
                        int start = cur[0];
                        values.add(new String(row, start, len, UTF8));
                        cur[0] = start + len;
                    }
                }
                rows.add(values);
            }
            return new QueryResult(columns, rows, null);
        } finally {
            try { s.close(); } catch (IOException ignored) {}
        }
    }

    // ====================================================================
    // Conexao + autenticacao (compartilhado)
    // ====================================================================

    private static Socket openAndAuth(String host, int port, String user,
                                      String password, int timeoutMs)
            throws IOException {
        Socket s = new Socket();
        boolean closeOnFail = true;
        try {
            s.setTcpNoDelay(true);
            s.setSoTimeout(timeoutMs);
            s.connect(new InetSocketAddress(host, port), timeoutMs);

            DataInputStream in = new DataInputStream(s.getInputStream());
            OutputStream out = s.getOutputStream();

            byte[] handshake = readPacket(in);
            HandshakeInfo hs = parseHandshake(handshake);

            byte[] authResponse;
            if (password == null || password.length() == 0) {
                authResponse = new byte[0];
            } else {
                authResponse = nativePasswordHash(password.getBytes(UTF8), hs.authSeed);
            }

            int caps = CLIENT_LONG_PASSWORD | CLIENT_LONG_FLAG | CLIENT_PROTOCOL_41
                    | CLIENT_TRANSACTIONS | CLIENT_SECURE_CONNECTION
                    | CLIENT_PLUGIN_AUTH | CLIENT_FOUND_ROWS;

            byte[] response = buildHandshakeResponse(caps, user, authResponse);
            writePacket(out, 1, response);

            byte[] authResult = readPacket(in);
            if (authResult.length == 0) {
                throw new IOException("resposta vazia do servidor pos-auth");
            }
            int header = authResult[0] & 0xFF;
            if (header == 0xFF) {
                throw new IOException(parseErr(authResult));
            }
            if (header == 0xFE) {
                throw new IOException("servidor pediu AuthSwitchRequest "
                        + "(plugin nao suportado por este cliente minimal)");
            }
            // 0x00 = OK, autenticado

            closeOnFail = false;
            return s;
        } finally {
            if (closeOnFail) {
                try { s.close(); } catch (IOException ignored) {}
            }
        }
    }

    private static void sendQuery(OutputStream out, String sql) throws IOException {
        byte[] sqlBytes = sql.getBytes(UTF8);
        byte[] query = new byte[1 + sqlBytes.length];
        query[0] = 0x03; // COM_QUERY
        System.arraycopy(sqlBytes, 0, query, 1, sqlBytes.length);
        writePacket(out, 0, query);
    }

    // ==== leitura/escrita de packets ====

    private static byte[] readPacket(DataInputStream in) throws IOException {
        int b0 = in.readUnsignedByte();
        int b1 = in.readUnsignedByte();
        int b2 = in.readUnsignedByte();
        int len = b0 | (b1 << 8) | (b2 << 16);
        in.readUnsignedByte(); // sequence id (descartado)
        byte[] buf = new byte[len];
        in.readFully(buf);
        return buf;
    }

    private static void writePacket(OutputStream out, int seq, byte[] payload)
            throws IOException {
        int len = payload.length;
        byte[] hdr = new byte[]{
                (byte) (len & 0xFF),
                (byte) ((len >>> 8) & 0xFF),
                (byte) ((len >>> 16) & 0xFF),
                (byte) (seq & 0xFF)
        };
        out.write(hdr);
        out.write(payload);
        out.flush();
    }

    // ==== parsing do handshake inicial ====

    private static class HandshakeInfo {
        byte[] authSeed;
        String authPluginName;
    }

    private static HandshakeInfo parseHandshake(byte[] p) throws IOException {
        int proto = p[0] & 0xFF;
        if (proto != 10) {
            throw new IOException("protocol version inesperada: " + proto);
        }
        int idx = 1;
        while (idx < p.length && p[idx] != 0) idx++;
        idx++;
        idx += 4; // connection id

        byte[] seed1 = new byte[8];
        System.arraycopy(p, idx, seed1, 0, 8);
        idx += 8;
        idx++; // filler
        idx += 2; // caps lower

        if (idx >= p.length) {
            HandshakeInfo info = new HandshakeInfo();
            info.authSeed = seed1;
            info.authPluginName = "mysql_native_password";
            return info;
        }
        idx += 1; // charset
        idx += 2; // status
        idx += 2; // caps upper
        int authLen = p[idx] & 0xFF;
        idx += 1;
        idx += 10; // reserved

        int part2Len = Math.max(13, authLen - 8);
        byte[] seed2 = new byte[part2Len];
        System.arraycopy(p, idx, seed2, 0, part2Len);
        idx += part2Len;
        int realPart2 = seed2.length;
        if (realPart2 > 0 && seed2[realPart2 - 1] == 0) {
            realPart2--;
        }
        int start = idx;
        while (idx < p.length && p[idx] != 0) idx++;
        String plugin = new String(p, start, idx - start, UTF8);

        byte[] seed = new byte[8 + realPart2];
        System.arraycopy(seed1, 0, seed, 0, 8);
        System.arraycopy(seed2, 0, seed, 8, realPart2);

        HandshakeInfo info = new HandshakeInfo();
        info.authSeed = seed;
        info.authPluginName = plugin.length() == 0 ? "mysql_native_password" : plugin;
        return info;
    }

    private static byte[] buildHandshakeResponse(int caps, String user,
                                                  byte[] authResponse) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeInt4(out, caps);
        writeInt4(out, 0x01000000); // max packet
        out.write(33); // utf8 charset
        for (int i = 0; i < 23; i++) out.write(0);
        out.write(user.getBytes(UTF8));
        out.write(0);
        out.write(authResponse.length);
        out.write(authResponse);
        out.write("mysql_native_password".getBytes(UTF8));
        out.write(0);
        return out.toByteArray();
    }

    private static void writeInt4(ByteArrayOutputStream out, int v) {
        out.write(v & 0xFF);
        out.write((v >>> 8) & 0xFF);
        out.write((v >>> 16) & 0xFF);
        out.write((v >>> 24) & 0xFF);
    }

    // ==== ColumnDefinition parsing (so o nome) ====

    private static String parseColumnName(byte[] p) {
        // formato: catalog (lenenc str), schema (lenenc str), table (lenenc str),
        //          org_table (lenenc str), name (lenenc str), org_name (lenenc str), ...
        int[] cur = {0};
        skipLenEncString(p, cur); // catalog
        skipLenEncString(p, cur); // schema
        skipLenEncString(p, cur); // table
        skipLenEncString(p, cur); // org_table
        // name (queremos)
        int len = (int) parseLenEnc(p, cur);
        int start = cur[0];
        cur[0] = start + len;
        return new String(p, start, len, UTF8);
    }

    private static void skipLenEncString(byte[] p, int[] cur) {
        int len = (int) parseLenEnc(p, cur);
        cur[0] += len;
    }

    // ==== ERR packet ====

    private static String parseErr(byte[] p) {
        int code = (p[1] & 0xFF) | ((p[2] & 0xFF) << 8);
        int idx = 3;
        if (p.length > 3 && p[3] == '#') {
            idx = 9;
        }
        String msg = new String(p, idx, p.length - idx, UTF8);
        return "ERR " + code + " " + msg;
    }

    // ==== lenenc int ====

    private static long parseLenEnc(byte[] p, int[] cursor) {
        int i = cursor[0];
        int b = p[i] & 0xFF;
        if (b < 0xFB) {
            cursor[0] = i + 1;
            return b;
        }
        if (b == 0xFB) {
            // NULL marker em row data; aqui em outros lugares e' invalido
            cursor[0] = i + 1;
            return 0;
        }
        if (b == 0xFC) {
            cursor[0] = i + 3;
            return (p[i + 1] & 0xFFL) | ((p[i + 2] & 0xFFL) << 8);
        }
        if (b == 0xFD) {
            cursor[0] = i + 4;
            return (p[i + 1] & 0xFFL) | ((p[i + 2] & 0xFFL) << 8)
                    | ((p[i + 3] & 0xFFL) << 16);
        }
        if (b == 0xFE) {
            cursor[0] = i + 9;
            long v = 0;
            for (int k = 0; k < 8; k++) {
                v |= (p[i + 1 + k] & 0xFFL) << (8 * k);
            }
            return v;
        }
        cursor[0] = i + 1;
        return 0;
    }

    // ==== mysql_native_password = SHA1(pwd) XOR SHA1(seed + SHA1(SHA1(pwd))) ====

    private static byte[] nativePasswordHash(byte[] password, byte[] seed) {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            byte[] stage1 = sha1.digest(password);
            sha1.reset();
            byte[] stage2 = sha1.digest(stage1);
            sha1.reset();
            sha1.update(seed);
            byte[] stage3 = sha1.digest(stage2);
            byte[] out = new byte[stage1.length];
            for (int i = 0; i < stage1.length; i++) {
                out[i] = (byte) (stage1[i] ^ stage3[i]);
            }
            return out;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
