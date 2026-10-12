package com.mytvplus.mobile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Local range-normalizing media proxy (same design as the desktop client's).
 * The service's CDNs break WebView playback in two ways: 301-redirected media
 * files and open-ended range requests. HttpURLConnection follows redirects
 * and we relay in bounded chunks (1 MB first, then 4 MB), which all CDNs
 * tested handle correctly.
 */
public final class MediaProxy {

    private static final String UA = "And$MyTV";
    private static final Map<String, Long> TOTALS = new ConcurrentHashMap<>();
    private static volatile int port = -1;

    public static int getPort() { return port; }

    public static void start() {
        new Thread(() -> {
            try {
                ServerSocket ss = new ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"));
                port = ss.getLocalPort();
                while (true) {
                    final Socket s = ss.accept();
                    new Thread(() -> handle(s)).start();
                }
            } catch (Exception e) {
                port = -1;
            }
        }).start();
    }

    private static void handle(Socket s) {
        try {
            s.setSoTimeout(25000);
            InputStream in = s.getInputStream();
            OutputStream out = s.getOutputStream();

            String line = readLine(in);
            if (line == null || !line.startsWith("GET ")) { close(s); return; }
            String path = line.substring(4, line.indexOf(' ', 4));
            Map<String, String> headers = new HashMap<>();
            String h;
            while ((h = readLine(in)) != null && !h.isEmpty()) {
                int c = h.indexOf(':');
                if (c > 0) headers.put(h.substring(0, c).trim().toLowerCase(), h.substring(c + 1).trim());
            }

            String target = null;
            int q = path.indexOf("?u=");
            if (path.startsWith("/m") && q > 0) {
                target = URLDecoder.decode(path.substring(q + 3), "UTF-8");
            }
            if (target == null || !(target.startsWith("http://") || target.startsWith("https://"))) {
                writeSimple(out, 400, "bad request");
                close(s); return;
            }

            String range = headers.getOrDefault("range", "bytes=0-");
            long start = 0, end = -1;
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("bytes=(\\d*)-(\\d*)").matcher(range);
            if (m.find()) {
                if (!m.group(1).isEmpty()) start = Long.parseLong(m.group(1));
                if (!m.group(2).isEmpty()) end = Long.parseLong(m.group(2));
                if (m.group(1).isEmpty() && !m.group(2).isEmpty()) start = -Long.parseLong(m.group(2)); // suffix
            }

            Long total = probeTotal(target);
            if (start < 0) {
                if (total == null) { writeSimple(out, 416, "no total"); close(s); return; }
                start = Math.max(0, total + start);
            }
            int chunk = start == 0 ? 1024 * 1024 : 4 * 1024 * 1024;
            if (end == -1) {
                end = total != null ? Math.min(start + chunk - 1, total - 1) : start + chunk - 1;
            }

            HttpURLConnection conn = (HttpURLConnection) new URL(target).openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(25000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", UA);
            conn.setRequestProperty("Range", "bytes=" + start + "-" + end);
            int code = conn.getResponseCode();
            if (code != 206 && code != 200) { writeSimple(out, 502, "upstream " + code); close(s); return; }

            InputStream body = conn.getInputStream();
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] b = new byte[65536];
            int n;
            while ((n = body.read(b)) > 0) { buf.write(b, 0, n); if (buf.size() > 8 * 1024 * 1024) break; }
            byte[] data = buf.toByteArray();
            conn.disconnect();

            long knownTotal = total != null ? total : start + data.length;

            StringBuilder resp = new StringBuilder();
            if (code == 206) {
                resp.append("HTTP/1.1 206 Partial Content\r\n");
                resp.append("Content-Range: bytes ").append(start).append('-').append(start + data.length - 1)
                    .append('/').append(knownTotal).append("\r\n");
            } else {
                resp.append("HTTP/1.1 200 OK\r\n");
            }
            resp.append("Content-Type: ").append(conn.getContentType() == null ? "video/mp4" : conn.getContentType()).append("\r\n");
            resp.append("Content-Length: ").append(data.length).append("\r\n");
            resp.append("Accept-Ranges: bytes\r\nConnection: close\r\n\r\n");
            out.write(resp.toString().getBytes("ISO-8859-1"));
            out.write(data);
            out.flush();
            close(s);
        } catch (Exception e) {
            close(s);
        }
    }

    private static Long probeTotal(String url) {
        if (TOTALS.containsKey(url)) return TOTALS.get(url);
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(10000);
            c.setReadTimeout(15000);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent", UA);
            c.setRequestProperty("Range", "bytes=0-0");
            int code = c.getResponseCode();
            String cr = c.getHeaderField("Content-Range");
            c.disconnect();
            if (code == 206 && cr != null) {
                String[] parts = cr.split("/");
                if (parts.length == 2) {
                    long t = Long.parseLong(parts[1].trim());
                    TOTALS.put(url, t);
                    return t;
                }
            }
        } catch (Exception ignored) { }
        return null;
    }

    private static String readLine(InputStream in) throws java.io.IOException {
        ByteArrayOutputStream o = new ByteArrayOutputStream(128);
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') break;
            o.write(c);
        }
        String s = o.toString("ISO-8859-1");
        if (s.endsWith("\r")) s = s.substring(0, s.length() - 1);
        return o.size() == 0 && c == -1 ? null : s;
    }

    private static void writeSimple(OutputStream out, int code, String msg) {
        try {
            out.write(("HTTP/1.1 " + code + " " + msg + "\r\nContent-Length: 0\r\nConnection: close\r\n\r\n").getBytes("ISO-8859-1"));
            out.flush();
        } catch (Exception ignored) { }
    }

    private static void close(Socket s) {
        try { s.close(); } catch (Exception ignored) { }
    }
}
