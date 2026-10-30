package org.btcsma.app;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private LinearLayout content;
    private boolean loading;
    private int selected = 1440;
    private final int ink = Color.rgb(233, 239, 248), muted = Color.rgb(156, 170, 190);
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) selected = state.getInt("selected", 1440);
        Monitor.channel(this);
        MonitorService.schedule(this);
        render();
        refresh();
    }
    @Override protected void onResume() { super.onResume(); if (content != null) render(); }
    @Override protected void onSaveInstanceState(Bundle state) { super.onSaveInstanceState(state); state.putInt("selected", selected); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextSize(size); view.setTextColor(color);
        view.setPadding(0, dp(8), 0, dp(8)); content.addView(view); return view;
    }
    private void button(String title, Runnable action) {
        Button button = new Button(this); button.setText(title); button.setAllCaps(false);
        content.addView(button); button.setOnClickListener(v -> action.run());
    }
    private void render() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(12, 19, 31));
        content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(22), dp(28), dp(22), dp(28));
        scroll.addView(content);
        scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(scroll);
        var p = Monitor.prefs(this);
        text("₿  BTC / USD", 32, ink);
        text("SMA 200 • Fechamento do candle", 17, muted);
        text("Fonte: Kraken • valores em dólares", 13, muted);
        RadioGroup tabs = new RadioGroup(this); tabs.setOrientation(LinearLayout.HORIZONTAL); content.addView(tabs);
        for (int interval : Monitor.INTERVALS) {
            RadioButton tab = new RadioButton(this); tab.setId(interval); tab.setText(Monitor.label(interval)); tab.setTextColor(ink); tabs.addView(tab);
        }
        tabs.check(selected);
        tabs.setOnCheckedChangeListener((group, id) -> { selected = id; render(); });
        try {
            JSONArray points = new JSONArray(p.getString("points" + selected, "[]"));
            if (points.length() > 0) {
                JSONArray last = points.getJSONArray(points.length() - 1);
                double close = last.getDouble(1), sma = last.getDouble(2);
                text(Monitor.money(close), 34, ink);
                text("Último fechamento • " + Monitor.date(last.getLong(0) + selected * 60L), 13, muted);
                text("SMA 200: " + Monitor.money(sma), 22, Color.rgb(255, 190, 70));
                text(String.format(Locale.US, "%+.2f%% em relação à média", (close / sma - 1) * 100), 17,
                    close >= sma ? Color.rgb(75, 220, 170) : Color.rgb(255, 125, 135));
                Chart chart = new Chart(this, points); content.addView(chart, new LinearLayout.LayoutParams(-1, dp(230)));
                text("Azul: fechamento  •  Amarelo: SMA 200\nÚltimos " + points.length() + " candles fechados • eixo vertical em USD", 12, muted);
                text("Consulta: " + Monitor.date(p.getLong("checked" + selected, 0)), 12, muted);
            } else text("Toque em atualizar para carregar o histórico.", 16, muted);
        } catch (JSONException e) { text("Não foi possível ler o histórico local. Atualize os dados.", 15, muted); }
        if (p.contains("error" + selected)) text("Falha na última consulta. Os valores exibidos podem estar antigos.\n" + p.getString("error" + selected, ""), 14, Color.rgb(255, 160, 130));
        if (p.getBoolean("gap" + selected, false)) text("Houve uma lacuna no histórico. O monitoramento foi retomado a partir dos dados disponíveis.", 14, muted);
        button(loading ? "Consultando…" : "Atualizar dados", this::refresh);
        text("Alertas no fechamento", 22, ink);
        for (int interval : Monitor.INTERVALS) {
            Switch toggle = new Switch(this); toggle.setText(Monitor.label(interval) + " • SMA 200"); toggle.setTextColor(ink);
            toggle.setPadding(0, dp(12), 0, dp(12)); toggle.setChecked(p.getBoolean("enabled" + interval, false)); content.addView(toggle);
            toggle.setOnCheckedChangeListener((v, enabled) -> {
                p.edit().putBoolean("enabled" + interval, enabled).remove("time" + interval).remove("side" + interval).apply();
                MonitorService.schedule(this);
                if (enabled) { requestNotifications(); refresh(); }
            });
        }
        text("O primeiro carregamento estabelece a referência. Depois, avisamos quando um candle fechar do outro lado da média. Tocar na média sem atravessá-la não gera alerta.", 14, muted);
        text("Consultas em segundo plano a cada 15 minutos, sujeitas aos atrasos do Android. Sem internet, com restrição de bateria ou após forçar parada, os avisos podem atrasar. Reabra o app após forçar parada.", 13, muted);
        if (!Monitor.canNotify(this)) {
            text("Notificações desativadas: os cruzamentos ainda aparecem no histórico.", 14, Color.rgb(255, 190, 70));
            button("Permitir notificações", () -> {
                if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                    requestNotifications();
                else startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
            });
        }
        button("Testar notificação", () -> {
            if (Monitor.canNotify(this)) Monitor.notify(this, "Teste: seus alertas de BTC/USD estão habilitados.", 1);
            else requestNotifications();
        });
        text("Histórico de cruzamentos", 22, ink);
        try {
            JSONArray history = new JSONArray(p.getString("history", "[]"));
            if (history.length() == 0) text("Nenhum cruzamento registrado desde a ativação.", 14, muted);
            for (int i = history.length() - 1; i >= 0; i--) {
                JSONObject event = history.getJSONObject(i);
                text(event.getString("text") + "\nDetectado: " + Monitor.date(event.getLong("detected")), 14, ink);
            }
        } catch (JSONException e) { text("Histórico local indisponível.", 14, muted); }
    }
    private void requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, 200);
    }
    private void refresh() {
        if (loading) return;
        loading = true; render();
        executor.submit(() -> {
            Monitor.refresh(getApplicationContext(), false);
            runOnUiThread(() -> { loading = false; if (!isDestroyed()) render(); });
        });
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results); render();
    }
    @Override protected void onDestroy() { executor.shutdownNow(); super.onDestroy(); }

    private static final class Chart extends View {
        private final List<double[]> points = new ArrayList<>();
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Chart(Context c, JSONArray json) throws JSONException {
            super(c);
            setContentDescription("Gráfico do fechamento do BTC/USD em azul e SMA 200 em amarelo. Valores mais recentes acima do gráfico.");
            for (int i = 0; i < json.length(); i++) points.add(new double[] {json.getJSONArray(i).getDouble(1), json.getJSONArray(i).getDouble(2)});
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (points.size() < 2) return;
            double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
            for (double[] point : points) for (double value : point) { min = Math.min(min, value); max = Math.max(max, value); }
            double padding = Math.max((max - min) * .1, 1); min -= padding; max += padding;
            float density = getResources().getDisplayMetrics().density;
            float left = 75 * density, right = getWidth() - 8 * density, top = 20 * density, bottom = getHeight() - 15 * density;
            paint.setStyle(Paint.Style.FILL); paint.setTextSize(11 * density); paint.setStrokeWidth(density);
            for (int i = 0; i <= 4; i++) {
                float y = top + (bottom - top) * i / 4;
                paint.setColor(Color.rgb(40, 54, 74)); canvas.drawLine(left, y, right, y, paint);
                paint.setColor(Color.LTGRAY); canvas.drawText(String.format(Locale.US, "%,.0f", max - (max - min) * i / 4), 0, y + 4 * density, paint);
            }
            for (int series = 0; series < 2; series++) {
                Path line = new Path();
                for (int i = 0; i < points.size(); i++) {
                    float x = left + (right - left) * i / (points.size() - 1);
                    float y = bottom - (float) ((points.get(i)[series] - min) / (max - min)) * (bottom - top);
                    if (i == 0) line.moveTo(x, y); else line.lineTo(x, y);
                }
                paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2 * density);
                paint.setColor(series == 0 ? Color.rgb(90, 175, 255) : Color.rgb(255, 190, 70)); canvas.drawPath(line, paint);
            }
        }
    }
}
