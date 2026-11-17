package com.example.fakelife;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;

/** Minimal client for Logfare's OpenAI-compatible chat completions endpoint. */
final class Logfare {
    static String chat(Context c, String system, String user, double temperature, int maxTokens) throws Exception {
        String key = Store.apiKey(c).trim();
        if (key.isEmpty()) throw new IOException("No Logfare API key set (Settings).");
        String base = Store.endpoint(c).trim();
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);

        JSONObject body = new JSONObject();
        body.put("model", Store.model(c));
        body.put("temperature", temperature);
        body.put("max_tokens", maxTokens);
        JSONArray msgs = new JSONArray();
        msgs.put(new JSONObject().put("role", "system").put("content", system));
        msgs.put(new JSONObject().put("role", "user").put("content", user));
        body.put("messages", msgs);

        resolveOrThrow(new URL(base).getHost());
        HttpURLConnection h = (HttpURLConnection) new URL(base + "/chat/completions").openConnection();
        h.setRequestMethod("POST");
        h.setConnectTimeout(20_000);
        h.setReadTimeout(300_000);
        h.setDoOutput(true);
        h.setRequestProperty("Content-Type", "application/json");
        h.setRequestProperty("Authorization", "Bearer " + key);
        h.getOutputStream().write(body.toString().getBytes("UTF-8"));

        int code = h.getResponseCode();
        InputStream in = code < 400 ? h.getInputStream() : h.getErrorStream();
        String text = readAll(in);
        h.disconnect();
        if (code >= 400) throw new IOException("Logfare HTTP " + code + ": " + shorten(text, 300));

        JSONObject r = new JSONObject(text);
        JSONObject msg = r.getJSONArray("choices").getJSONObject(0).getJSONObject("message");
        String content = msg.optString("content", "");
        if (content.isEmpty()) throw new IOException("Logfare returned an empty reply.");
        return content;
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
            sb.append("GET ").append(base).append("/models: HTTP ").append(h.getResponseCode()).append('\n');
            h.disconnect();
        } catch (Exception e) {
            sb.append("GET /models failed: ").append(e.getClass().getSimpleName()).append(": ").append(e.getMessage()).append('\n');
        }
        sb.append("\nHow to read this: example.com FAILED too = this Android user/app has no internet at all. "
                + "Only the Logfare host FAILED = something blocks that name (Private DNS, VPN, ad blocker). "
                + "HTTP 401/403 = network is fine, check the API key.");
        return sb.toString();
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
