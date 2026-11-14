package com.dvdtecno.tienda;

import android.app.Activity;
import android.content.Intent;
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
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
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
import java.util.LinkedHashSet;
import java.util.Set;

public class MainActivity extends Activity {

    // >>> CAMBIA ESTO: enlace de Dropbox de tu archivo catalogo.json <<<
    static final String CATALOG_URL = "https://www.dropbox.com/scl/fi/5vifpxczn2599a0kc4r3d/catalogo.json?rlkey=ankon8c0iq20l2o4mh6wi9xn5&st=kconzw26&dl=0";

    LinearLayout tabsRow, list;
    TextView status;
    JSONArray apps = new JSONArray();
    String tab = "Todas";
    boolean busy = false;
    JSONObject self = null;

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

    Button btn(String t, android.view.View.OnClickListener l) {
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
                list.addView(btn("⬆ Hay una versión nueva de la tienda. Toca para actualizar", v -> download(up)), lu);
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
            list.addView(ab, lp);
            icon(ab, a.optString("icono"));
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
        new Thread(() -> {
            try {
                File dir = new File(getExternalFilesDir(null), "apks");
                dir.mkdirs();
                File[] old = dir.listFiles();
                if (old != null) for (File f : old) f.delete();
                File out = new File(dir, name.replaceAll("[^A-Za-z0-9_-]", "_") + ".apk");
                HttpURLConnection c = open(fix(a.optString("url")));
                int total = c.getContentLength();
                long done = 0, last = 0;
                try (InputStream in = c.getInputStream(); OutputStream os = new FileOutputStream(out)) {
                    byte[] buf = new byte[65536];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        os.write(buf, 0, n);
                        done += n;
                        long now = SystemClock.elapsedRealtime();
                        if (now - last > 500) {
                            last = now;
                            say("Descargando " + name + "… " + (total > 0 ? (done * 100 / total) + "%" : (done / 1048576) + " MB"));
                        }
                    }
                }
                byte[] head = new byte[2];
                try (FileInputStream fi = new FileInputStream(out)) { fi.read(head); }
                if (out.length() < 1000 || head[0] != 'P' || head[1] != 'K')
                    throw new Exception("El enlace no entregó un archivo APK. Revisa el enlace.");
                say("Listo. Toca «Instalar» en la pantalla que se abre.");
                runOnUiThread(() -> install(out));
            } catch (Exception e) {
                say("Error: " + e.getMessage());
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
