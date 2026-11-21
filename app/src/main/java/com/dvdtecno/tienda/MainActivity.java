package com.dvdtecno.tienda;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.core.content.FileProvider;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MainActivity extends Activity {

    // >>> CAMBIA ESTO: enlace de Dropbox de tu archivo catalogo.json <<<
    static final String CATALOG_URL = "https://www.dropbox.com/scl/fi/5vifpxczn2599a0kc4r3d/catalogo.json?rlkey=ankon8c0iq20l2o4mh6wi9xn5&st=kconzw26&dl=0";

    static final int COLOR_TXT = 0xFFB8C0E8;
    static final int COLOR_OK = 0xFF4CD98A;
    static final int COLOR_ERR = 0xFFFF6B6B;
    static final int COLOR_BAR = 0xFF3B5BFD;

    LinearLayout tabsRow, list;
    TextView status;
    JSONArray apps = new JSONArray();
    String tab = "Todas";
    boolean busy = false;
    JSONObject self = null;

    // ----- Estado del panel de progreso (dentro de cada tarjeta) -----
    static class Holder {
        LinearLayout panel;
        TextView txt;
        ProgressBar bar;
    }
    final Map<String, Holder> holders = new HashMap<>();
    String activeName = null;   // app que se está descargando (o la última)
    String stText = "";
    int stPct = 0;              // 0-100, -1 = indeterminado, -2 = sin barra
    int stColor = COLOR_TXT;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0F1220);
        root.setPadding(dp(32), dp(20), dp(32), dp(20));
        root.addView(text("DVD Tecno · Tienda de aplicaciones", 26, Color.WHITE));
        HorizontalScrollView hs = new HorizontalScrollView(this);
        tabsRow = new LinearLayout(this);
        hs.addView(tabsRow);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2);
        hp.setMargins(0, dp(12), 0, dp(6));
        root.addView(hs, hp);
        status = text("", 16, 0xFF9AA2C4);
        root.addView(status);
        ScrollView sv = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        sv.addView(list);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
        load();
    }

    int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        return t;
    }

    GradientDrawable rect(int c) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(c);
        g.setCornerRadius(dp(12));
        return g;
    }

    Button btn(String t, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(t);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(18);
        b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        b.setPadding(dp(18), dp(12), dp(18), dp(12));
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused}, rect(0xFF3B5BFD));
        s.addState(new int[]{android.R.attr.state_pressed}, rect(0xFF3B5BFD));
        s.addState(new int[]{}, rect(0xFF222844));
        b.setBackground(s);
        b.setOnClickListener(l);
        return b;
    }

    // Tarjeta = botón de la app + panel de progreso debajo (oculto hasta que se descarga)
    LinearLayout card(Button b, String name) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.addView(b, new LinearLayout.LayoutParams(-1, -2));

        Holder h = new Holder();
        h.panel = new LinearLayout(this);
        h.panel.setOrientation(LinearLayout.VERTICAL);
        h.panel.setBackground(rect(0xFF1A1F38));
        h.panel.setPadding(dp(18), dp(10), dp(18), dp(12));
        h.panel.setVisibility(View.GONE);

        h.txt = text("", 15, COLOR_TXT);
        h.panel.addView(h.txt);

        h.bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        h.bar.setMax(100);
        if (Build.VERSION.SDK_INT >= 21) {
            h.bar.setProgressTintList(ColorStateList.valueOf(COLOR_BAR));
            h.bar.setIndeterminateTintList(ColorStateList.valueOf(COLOR_BAR));
            h.bar.setProgressBackgroundTintList(ColorStateList.valueOf(0xFF33395C));
        }
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, dp(8));
        bp.setMargins(0, dp(8), 0, 0);
        h.panel.addView(h.bar, bp);

        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, -2);
        pp.setMargins(0, dp(2), 0, 0);
        c.addView(h.panel, pp);

        holders.put(name, h);
        return c;
    }

    void applyState(Holder h) {
        h.panel.setVisibility(View.VISIBLE);
        h.txt.setText(stText);
        h.txt.setTextColor(stColor);
        if (stPct == -2) {
            h.bar.setVisibility(View.GONE);
        } else {
            h.bar.setVisibility(View.VISIBLE);
            if (stPct == -1) {
                h.bar.setIndeterminate(true);
            } else {
                h.bar.setIndeterminate(false);
                h.bar.setProgress(stPct);
            }
        }
    }

    // Se puede llamar desde cualquier hilo
    void progress(final String name, final String t, final int pct, final int color) {
        runOnUiThread(() -> {
            activeName = name;
            stText = t;
            stPct = pct;
            stColor = color;
            Holder h = holders.get(name);
            if (h != null) applyState(h);
        });
    }

    static String mb(long bytes) { return String.format(Locale.US, "%.1f", bytes / 1048576f); }

    static String fix(String u) {
        u = u.trim();
        if (u.contains("dropbox.com")) {
            u = u.replaceAll("([?&])dl=0", "$1dl=1");
            if (!u.contains("dl=1")) u += (u.contains("?") ? "&" : "?") + "dl=1";
        }
        return u;
    }

    HttpURLConnection open(String u) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "Mozilla/5.0");
        if (c.getResponseCode() >= 400) throw new Exception("Error HTTP " + c.getResponseCode());
        return c;
    }

    void say(final String s) { runOnUiThread(() -> status.setText(s)); }

    void load() {
        status.setText("Cargando catálogo…");
        new Thread(() -> {
            String json;
            try {
                HttpURLConnection c = open(fix(CATALOG_URL));
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                try (InputStream in = c.getInputStream()) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                }
                json = bo.toString("UTF-8");
                new JSONObject(json); // validar
                getSharedPreferences("t", 0).edit().putString("c", json).apply();
            } catch (Exception e) {
                json = getSharedPreferences("t", 0).getString("c", null);
            }
            final String j = json;
            runOnUiThread(() -> show(j));
        }).start();
    }

    void show(String j) {
        try {
            JSONObject root = new JSONObject(j);
            apps = root.getJSONArray("apps");
            self = root.optJSONObject("tienda");
            status.setText("");
        } catch (Exception e) {
            apps = new JSONArray();
            status.setText("No se pudo cargar el catálogo. Revisa tu internet.");
        }
        render();
    }

    void render() {
        tabsRow.removeAllViews();
        list.removeAllViews();
        holders.clear();
        Set<String> tabs = new LinkedHashSet<>();
        tabs.add("Todas");
        for (int i = 0; i < apps.length(); i++)
            tabs.add(apps.optJSONObject(i).optString("dispositivo", "Otros"));
        if (!tabs.contains(tab)) tab = "Todas";
        for (final String t : tabs) {
            Button b = btn((t.equals(tab) ? "● " : "") + t, v -> { tab = t; render(); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.setMargins(0, 0, dp(8), 0);
            tabsRow.addView(b, lp);
            if (t.equals(tab)) b.requestFocus();
        }
        tabsRow.addView(btn("⟳ Actualizar lista", v -> load()));
        int n = 0;
        try {
            int mine = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
            if (self != null && self.optInt("versionCode", 0) > mine && self.optString("url").length() > 0) {
                final JSONObject up = new JSONObject();
                up.put("nombre", "Tienda DVD Tecno");
                up.put("url", self.optString("url"));
                LinearLayout.LayoutParams lu = new LinearLayout.LayoutParams(-1, -2);
                lu.setMargins(0, dp(6), 0, dp(6));
                Button ub = btn("⬆ Hay una versión nueva de la tienda. Toca para actualizar", v -> download(up));
                list.addView(card(ub, "Tienda DVD Tecno"), lu);
            }
        } catch (Exception e) { }
        for (int i = 0; i < apps.length(); i++) {
            final JSONObject a = apps.optJSONObject(i);
            if (!tab.equals("Todas") && !a.optString("dispositivo", "Otros").equals(tab)) continue;
            n++;
            String txt = a.optString("nombre") + "   v" + a.optString("version") + "\n" + a.optString("descripcion");
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.setMargins(0, dp(6), 0, dp(6));
            Button ab = btn(txt, v -> download(a));
            list.addView(card(ab, a.optString("nombre")), lp);
            icon(ab, a.optString("icono"));
        }
        // Si se redibuja la lista durante una descarga, se restaura el progreso
        if (activeName != null) {
            Holder h = holders.get(activeName);
            if (h != null) applyState(h);
        }
        if (n == 0 && status.getText().length() == 0) status.setText("No hay aplicaciones en esta sección.");
    }

    void icon(final Button b, final String url) {
        if (url.length() == 0) return;
        new Thread(() -> {
            File f = new File(getCacheDir(), "i" + Math.abs(url.hashCode()) + ".img");
            try {
                if (!f.exists()) {
                    HttpURLConnection c = open(fix(url));
                    try (InputStream in = c.getInputStream(); OutputStream os = new FileOutputStream(f)) {
                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
                    }
                }
                Bitmap bm = BitmapFactory.decodeFile(f.getPath());
                if (bm == null) { f.delete(); return; }
                final Bitmap sm = Bitmap.createScaledBitmap(bm, dp(56), dp(56), true);
                runOnUiThread(() -> {
                    b.setCompoundDrawablePadding(dp(14));
                    b.setCompoundDrawablesWithIntrinsicBounds(new BitmapDrawable(getResources(), sm), null, null, null);
                });
            } catch (Exception e) { f.delete(); }
        }).start();
    }

    void download(final JSONObject a) {
        if (busy) return;
        if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            status.setText("Activa «Permitir de esta fuente» para esta app y vuelve a tocar la aplicación.");
            startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName())));
            return;
        }
        busy = true;
        final String name = a.optString("nombre");
        status.setText("");
        // Oculta paneles de descargas anteriores y muestra el de esta app
        for (Holder h : holders.values()) h.panel.setVisibility(View.GONE);
        progress(name, "Conectando…", -1, COLOR_TXT);
        new Thread(() -> {
            try {
                File dir = new File(getExternalFilesDir(null), "apks");
                dir.mkdirs();
                File[] old = dir.listFiles();
                if (old != null) for (File f : old) f.delete();
                File out = new File(dir, name.replaceAll("[^A-Za-z0-9_-]", "_") + ".apk");
                HttpURLConnection c = open(fix(a.optString("url")));
                long total = c.getContentLength();
                long done = 0, last = 0;
                try (InputStream in = c.getInputStream(); OutputStream os = new FileOutputStream(out)) {
                    byte[] buf = new byte[65536];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        os.write(buf, 0, n);
                        done += n;
                        long now = SystemClock.elapsedRealtime();
                        if (now - last > 200) {
                            last = now;
                            if (total > 0) {
                                int pct = (int) (done * 100 / total);
                                progress(name, "Descargando… " + pct + "%  ·  " + mb(done) + " / " + mb(total) + " MB", pct, COLOR_TXT);
                            } else {
                                progress(name, "Descargando… " + mb(done) + " MB", -1, COLOR_TXT);
                            }
                        }
                    }
                }
                byte[] head = new byte[2];
                try (FileInputStream fi = new FileInputStream(out)) { fi.read(head); }
                if (out.length() < 1000 || head[0] != 'P' || head[1] != 'K')
                    throw new Exception("El enlace no entregó un archivo APK. Revisa el enlace.");
                progress(name, "✓ Descarga completa · Toca «Instalar» en la pantalla que se abre", 100, COLOR_OK);
                runOnUiThread(() -> install(out));
            } catch (Exception e) {
                progress(name, "✕ Error: " + e.getMessage() + " · Toca la app para reintentar", -2, COLOR_ERR);
            } finally {
                busy = false;
            }
        }).start();
    }

    void install(File f) {
        Uri u = FileProvider.getUriForFile(this, getPackageName() + ".fp", f);
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(u, "application/vnd.android.package-archive");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
    }
}
