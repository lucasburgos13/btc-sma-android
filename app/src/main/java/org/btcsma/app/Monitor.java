package org.btcsma.app;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;
import org.json.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.*;
import java.util.*;

public final class Monitor {
    public static final String CHANNEL = "crossings";
    public static final int[] INTERVALS = {1440, 10080};
    public static android.content.SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("btc-sma", Context.MODE_PRIVATE);
    }
    public static String label(int interval) { return interval == 1440 ? "Diário" : "Semanal"; }
    public static String money(double value) {
        return NumberFormat.getCurrencyInstance(Locale.US).format(value);
    }
    public static String date(long seconds) {
        SimpleDateFormat f = new SimpleDateFormat("dd/MM/yyyy HH:mm 'UTC'", new Locale("pt", "BR"));
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date(seconds * 1000));
    }
    public static void channel(Context context) {
        context.getSystemService(NotificationManager.class).createNotificationChannel(
            new NotificationChannel(CHANNEL, "Cruzamentos da SMA 200", NotificationManager.IMPORTANCE_DEFAULT));
    }
    public static boolean canNotify(Context context) {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        NotificationChannel channel = nm.getNotificationChannel(CHANNEL);
        return (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED) && nm.areNotificationsEnabled()
            && channel != null && channel.getImportance() != NotificationManager.IMPORTANCE_NONE;
    }

    private static List<Signals.Candle> fetch(int interval) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(
            "https://api.kraken.com/0/public/OHLC?pair=XBTUSD&interval=" + interval).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);
        connection.setRequestProperty("User-Agent", "BTC-SMA-Android/0.1");
        try {
            if (connection.getResponseCode() != 200) throw new IOException("HTTP " + connection.getResponseCode());
            StringBuilder body = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Consulta interrompida");
                    body.append(line);
                    if (body.length() > 2_000_000) throw new IOException("Resposta muito grande");
                }
            }
            JSONObject json = new JSONObject(body.toString());
            if (json.getJSONArray("error").length() != 0) throw new IOException(json.getJSONArray("error").toString());
            JSONArray rows = json.getJSONObject("result").getJSONArray("XXBTZUSD");
            if (rows.length() < 201) throw new IOException("Histórico insuficiente");
            long activeStart = rows.getJSONArray(rows.length() - 1).getLong(0);
            long now = System.currentTimeMillis() / 1000;
            if (activeStart > now + 60 || now - activeStart > interval * 60L + 300)
                throw new IOException("Dados desatualizados; verifique também o relógio do celular");
            List<Signals.Candle> candles = new ArrayList<>();
            // Kraken ALWAYS includes the candle still forming as the final row.
            for (int i = 0; i < rows.length() - 1; i++) {
                JSONArray row = rows.getJSONArray(i);
                candles.add(new Signals.Candle(row.getLong(0), row.getDouble(4)));
            }
            return candles;
        } finally { connection.disconnect(); }
    }

    /** Serializes manual refresh and scheduled refresh, preventing duplicate alerts. */
    public static synchronized String refresh(Context c, boolean enabledOnly) {
        StringBuilder failures = new StringBuilder();
        for (int interval : INTERVALS) {
            if (Thread.currentThread().isInterrupted()) break;
            if (enabledOnly && !prefs(c).getBoolean("enabled" + interval, false)) continue;
            try { update(c, interval); }
            catch (Exception e) {
                if (Thread.currentThread().isInterrupted()) break;
                String error = label(interval) + ": " + e.getMessage();
                prefs(c).edit().putString("error" + interval, error).apply();
                failures.append(error).append("\n");
            }
        }
        return failures.toString();
    }

    private static void update(Context c, int interval) throws Exception {
        List<Signals.Point> points = Signals.points(fetch(interval));
        if (Thread.currentThread().isInterrupted()) return;
        var p = prefs(c);
        JSONArray cache = new JSONArray();
        for (Signals.Point point : points.subList(Math.max(0, points.size() - 120), points.size()))
            cache.put(new JSONArray().put(point.time()).put(point.close()).put(point.sma()));
        p.edit().putString("points" + interval, cache.toString())
            .putLong("checked" + interval, System.currentTimeMillis() / 1000).remove("error" + interval).apply();
        if (!p.getBoolean("enabled" + interval, false)) return;
        Signals.State previous = p.contains("time" + interval)
            ? new Signals.State(p.getLong("time" + interval, 0), p.getInt("side" + interval, 0)) : null;
        Signals.Evaluation result = Signals.evaluate(points, previous, interval * 60L);
        JSONArray history = new JSONArray(p.getString("history", "[]"));
        for (Signals.Crossing event : result.crossings()) {
            long closeTime = event.point().time() + interval * 60L;
            String text = label(interval) + " • fechou " + (event.direction() > 0 ? "ACIMA" : "ABAIXO")
                + " da SMA 200\nFechamento: " + money(event.point().close())
                + " • SMA: " + money(event.point().sma()) + "\n" + date(closeTime);
            JSONObject entry = new JSONObject().put("id", interval + ":" + closeTime)
                .put("text", text).put("detected", System.currentTimeMillis() / 1000);
            boolean duplicate = false;
            for (int i = 0; i < history.length(); i++)
                if (history.getJSONObject(i).getString("id").equals(entry.getString("id"))) duplicate = true;
            if (!duplicate) {
                history.put(entry);
                // Save history first: a restart won't repeat an already recorded alert.
                p.edit().putString("history", history.toString()).commit();
                if (canNotify(c)) notify(c, text, entry.getString("id").hashCode());
            }
        }
        JSONArray limited = new JSONArray();
        for (int i = Math.max(0, history.length() - 50); i < history.length(); i++) limited.put(history.get(i));
        p.edit().putLong("time" + interval, result.state().time()).putInt("side" + interval, result.state().side())
            .putBoolean("gap" + interval, result.gap()).putString("history", limited.toString()).commit();
    }

    public static void notify(Context c, String text, int id) {
        Intent intent = new Intent(c, MainActivity.class);
        PendingIntent tap = PendingIntent.getActivity(c, 0, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(c, CHANNEL).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("BTC/USD • SMA 200").setContentText(text.split("\n")[0])
            .setStyle(new Notification.BigTextStyle().bigText(text)).setContentIntent(tap).setAutoCancel(true).build();
        try { c.getSystemService(NotificationManager.class).notify(id, n); }
        catch (SecurityException ignored) { /* Permission may be revoked between check and delivery. */ }
    }
}
