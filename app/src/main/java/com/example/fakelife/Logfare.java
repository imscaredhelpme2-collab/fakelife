package com.example.fakelife;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;

/** Minimal client for Logfare's OpenAI-compatible chat completions endpoint. */
final class Logfare {
    /** Short summary of the last call (time, size, finish reason), shown in logs and tests. */
    static volatile String lastInfo = "";

    /** Streams the reply first (no proxy timeouts on slow generations); falls back to a plain request. */
    static String chat(Context c, String system, String user, double temperature, int maxTokens) throws Exception {
        try {
            String r = chatOnce(c, system, user, temperature, maxTokens, true);
            if (!r.trim().isEmpty()) return r;
            Journal.add(c, "Streamed reply was empty, trying a normal request...");
        } catch (java.net.SocketTimeoutException | java.net.UnknownHostException e) {
            throw e;
        } catch (IOException e) {
            Journal.add(c, "Streaming failed (" + shorten(String.valueOf(e.getMessage()), 120) + "), trying a normal request...");
        }
        String r = chatOnce(c, system, user, temperature, maxTokens, false);
        if (r.trim().isEmpty()) throw new IOException("Logfare returned an empty reply twice");
        return r;
    }

    private static String chatOnce(Context c, String system, String user, double temperature, int maxTokens,
                                   boolean stream) throws Exception {
        String key = Store.apiKey(c).trim();
        if (key.isEmpty()) throw new IOException("No Logfare API key set (Settings).");
        String base = Store.endpoint(c).trim();
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);

        JSONObject body = new JSONObject();
        body.put("model", Store.model(c));
        body.put("temperature", temperature);
        body.put("max_tokens", maxTokens);
        if (stream) body.put("stream", true);
        JSONArray msgs = new JSONArray();
        msgs.put(new JSONObject().put("role", "system").put("content", system));
        msgs.put(new JSONObject().put("role", "user").put("content", user));
        body.put("messages", msgs);

        resolveOrThrow(new URL(base).getHost());
        HttpURLConnection h = (HttpURLConnection) new URL(base + "/chat/completions").openConnection();
        h.setRequestMethod("POST");
        h.setConnectTimeout(20_000);
        h.setReadTimeout(stream ? 150_000 : 280_000); // streaming: max silence between chunks
        h.setDoOutput(true);
        h.setRequestProperty("Content-Type", "application/json");
        h.setRequestProperty("Accept", stream ? "text/event-stream" : "application/json");
        h.setRequestProperty("Authorization", "Bearer " + key);
        h.getOutputStream().write(body.toString().getBytes("UTF-8"));

        long t0 = System.currentTimeMillis();
        int code = h.getResponseCode();
        if (code >= 400) {
            String err = readAll(h.getErrorStream());
            h.disconnect();
            throw new IOException("Logfare HTTP " + code + ": " + shorten(err, 300));
        }

        StringBuilder content = new StringBuilder();
        StringBuilder reasoning = new StringBuilder();
        StringBuilder raw = new StringBuilder();
        String finish = null;
        java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(h.getInputStream(), "UTF-8"));
        String line;
        int lastLogged = 0;
        while ((line = br.readLine()) != null) {
            if (raw.length() < 400_000) raw.append(line).append('\n');
            if (!line.startsWith("data:")) continue;
            String d = line.substring(5).trim();
            if (d.isEmpty()) continue;
            if (d.equals("[DONE]")) break;
            try {
                JSONObject o = new JSONObject(d);
                JSONArray ch = o.optJSONArray("choices");
                if (ch == null || ch.length() == 0) continue;
                JSONObject c0 = ch.getJSONObject(0);
                JSONObject delta = c0.optJSONObject("delta");
                if (delta != null) {
                    if (!delta.isNull("content")) content.append(delta.getString("content"));
                    if (!delta.isNull("reasoning_content")) reasoning.append(delta.getString("reasoning_content"));
                    else if (!delta.isNull("reasoning")) reasoning.append(delta.getString("reasoning"));
                }
                if (!c0.isNull("finish_reason")) finish = c0.getString("finish_reason");
            } catch (Exception ignored) {
            }
            int total = content.length() + reasoning.length();
            if (total - lastLogged >= 3000) {
                lastLogged = total;
                Journal.add(c, "...receiving (" + content.length() + " chars" + (reasoning.length() > 0 ? ", thinking " + reasoning.length() : "") + ")");
            }
        }
        br.close();
        h.disconnect();

        // Server ignored stream=true and sent one normal JSON body.
        if (content.length() == 0 && raw.toString().trim().startsWith("{")) {
            try {
                JSONObject r = new JSONObject(raw.toString().trim());
                JSONObject ch0 = r.getJSONArray("choices").getJSONObject(0);
                JSONObject msg = ch0.getJSONObject("message");
                if (!msg.isNull("content")) content.append(msg.getString("content"));
                if (!msg.isNull("reasoning_content")) reasoning.append(msg.getString("reasoning_content"));
                if (!ch0.isNull("finish_reason")) finish = ch0.getString("finish_reason");
            } catch (Exception ignored) {
            }
        }
        long secs = (System.currentTimeMillis() - t0) / 1000;
        String out = content.toString();
        if (out.trim().isEmpty() && reasoning.indexOf("{") >= 0) out = reasoning.toString();
        lastInfo = secs + "s, " + out.length() + " chars, finish=" + finish;
        Journal.add(c, "Logfare answered in " + secs + "s: " + out.length() + " chars, finish=" + finish
                + (reasoning.length() > 0 ? ", thinking=" + reasoning.length() : ""));
        if (out.trim().isEmpty()) {
            String hint = "length".equals(finish)
                    ? " The model ran out of tokens (probably spent them thinking): raise 'Max reply tokens' in Settings."
                    : "";
            throw new IOException("Logfare returned an empty reply (finish=" + finish + ", stream=" + stream + ")." + hint);
        }
        return out;
    }

    /** DNS lookup with a 10 second limit so a dead network fails fast with a clear message. */
    static InetAddress[] resolveOrThrow(final String host) throws IOException {
        final InetAddress[][] res = new InetAddress[1][];
        final Exception[] err = new Exception[1];
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try { res[0] = InetAddress.getAllByName(host); } catch (Exception e) { err[0] = e; }
            }
        });
        t.start();
        try { t.join(10_000); } catch (InterruptedException ignored) { }
        if (res[0] != null) return res[0];
        throw new IOException("Cannot resolve " + host + " (" + (err[0] != null ? err[0].getMessage() : "timed out after 10s")
                + "). This Android user has no working internet/DNS for the app. Run Test connection in Settings.");
    }

    /** Plain-text report about network state, DNS and the Logfare endpoint. */
    static String diagnose(Context c) {
        StringBuilder sb = new StringBuilder();
        try {
            android.net.ConnectivityManager cm = c.getSystemService(android.net.ConnectivityManager.class);
            android.net.Network n = cm.getActiveNetwork();
            if (n == null) {
                sb.append("Active network: NONE (this user is not connected)\n");
            } else {
                android.net.NetworkCapabilities caps = cm.getNetworkCapabilities(n);
                String kind = "other";
                if (caps != null) {
                    if (caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)) kind = "Wi-Fi";
                    else if (caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)) kind = "cellular";
                    else if (caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN)) kind = "VPN";
                }
                sb.append("Active network: ").append(kind)
                        .append(", internet=").append(caps != null && caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET))
                        .append(", validated=").append(caps != null && caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)).append('\n');
                sb.append("Private DNS in use: ").append(cm.getLinkProperties(n) != null && cm.getLinkProperties(n).isPrivateDnsActive()).append('\n');
            }
            int rb = cm.getRestrictBackgroundStatus();
            sb.append("Data Saver: ").append(rb == 3 ? "ON, app not allowed" : rb == 2 ? "on, app allowed" : "off").append('\n');
        } catch (Exception e) {
            sb.append("Network info failed: ").append(e).append('\n');
        }
        String host = "logfare.ai";
        try { host = new URL(Store.endpoint(c).trim()).getHost(); } catch (Exception ignored) { }
        for (String h : new String[]{"example.com", host}) {
            try {
                InetAddress[] a = resolveOrThrow(h);
                sb.append("DNS ").append(h).append(": OK ").append(a[0].getHostAddress()).append('\n');
            } catch (Exception e) {
                sb.append("DNS ").append(h).append(": FAILED\n");
            }
        }
        try {
            String base = Store.endpoint(c).trim();
            while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
            HttpURLConnection h = (HttpURLConnection) new URL(base + "/models").openConnection();
            h.setConnectTimeout(15_000);
            h.setReadTimeout(15_000);
            h.setRequestProperty("Authorization", "Bearer " + Store.apiKey(c).trim());
            int code = h.getResponseCode();
            sb.append("GET ").append(base).append("/models: HTTP ").append(code).append('\n');
            if (code == 200) {
                try {
                    JSONObject mj = new JSONObject(readAll(h.getInputStream()));
                    JSONArray da = mj.optJSONArray("data");
                    if (da != null) {
                        sb.append("Models (").append(da.length()).append("): ");
                        for (int i = 0; i < Math.min(30, da.length()); i++) sb.append(da.getJSONObject(i).optString("id")).append(i < da.length() - 1 ? ", " : "");
                        sb.append('\n');
                    }
                } catch (Exception ignored) { }
            }
            h.disconnect();
        } catch (Exception e) {
            sb.append("GET /models failed: ").append(e.getClass().getSimpleName()).append(": ").append(e.getMessage()).append('\n');
        }
        try {
            long t = System.currentTimeMillis();
            String r = chat(c, "Reply with only JSON.", "Return exactly {\"ok\":true}", 0.2, 600);
            sb.append("Chat test: OK in ").append((System.currentTimeMillis() - t) / 1000).append("s, reply: ")
                    .append(shorten(r.replace('\n', ' '), 80)).append(" [").append(lastInfo).append("]\n");
        } catch (Exception e) {
            sb.append("Chat test FAILED: ").append(e.getMessage()).append('\n');
        }
        sb.append("\nHow to read this: example.com FAILED too = this Android user/app has no internet at all. "
                + "Only the Logfare host FAILED = something blocks that name (Private DNS, VPN, ad blocker). "
                + "HTTP 401/403 = network is fine, check the API key.");
        return sb.toString();
    }

    /** Tiny real chat call so you can see what the model really returns and how long it takes. */
    static String testChat(Context c) {
        long t0 = System.currentTimeMillis();
        try {
            String r = chat(c, "Reply with ONE JSON object and nothing else.",
                    "Return {\"ok\":true,\"actions\":[{\"t\":\"note\",\"title\":\"hi\",\"body\":\"test\"}]}", 0.2, 400);
            return "OK in " + (System.currentTimeMillis() - t0) / 1000 + "s. Reply:\n" + shorten(r, 400);
        } catch (Exception e) {
            return "FAILED after " + (System.currentTimeMillis() - t0) / 1000 + "s: " + e.getClass().getSimpleName() + ": " + e.getMessage();
        }
    }

    private static String readAll(InputStream in) throws IOException {
        if (in == null) return "";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        in.close();
        return out.toString("UTF-8");
    }

    static String shorten(String s, int n) {
        return s.length() <= n ? s : s.substring(0, n) + "...";
    }

    /** Pulls the first top-level JSON object out of a model reply (tolerates ``` fences and chatter). */
    static JSONObject parseJson(String reply) throws Exception {
        String s = reply.trim();
        int a = s.indexOf('{');
        int b = s.lastIndexOf('}');
        if (a < 0 || b <= a) throw new IOException("Reply had no JSON: " + shorten(s, 120));
        s = s.substring(a, b + 1);
        try {
            return new JSONObject(s);
        } catch (Exception first) {
            // Truncated reply: try to close it by dropping the last unfinished action.
            int cut = s.lastIndexOf("},");
            if (cut > 0) {
                String fixed = s.substring(0, cut + 1) + "]}";
                try { return new JSONObject(fixed); } catch (Exception ignored) { }
            }
            throw first;
        }
    }
}
