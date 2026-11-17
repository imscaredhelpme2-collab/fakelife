package com.example.fakelife;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
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
