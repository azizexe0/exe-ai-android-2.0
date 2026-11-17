package com.exeai.assistant;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private final int RED = Color.rgb(255, 35, 55);
    private final int BG = Color.rgb(12, 12, 15);
    private final int PANEL = Color.rgb(27, 27, 33);
    private static final int VOICE_REQUEST = 41;
    private LinearLayout messages;
    private EditText input;
    private ScrollView scroll;
    private TextView status;
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private String previousResponseId = null;
    private SharedPreferences prefs;
    private boolean waiting = false;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("exe_ai", MODE_PRIVATE);
        previousResponseId = prefs.getString("previous_response_id", null);
        buildUi();
        addMessage("EXE AI", "EXE AI 2.0 hazır. İnternet varsa OpenAI destekli sohbet ve web araştırması; internet yoksa yerel komutlar kullanılabilir.\n\nSunucu adresini ayarlamak için üstteki EXE AI başlığına uzun bas.", false);
    }

    private GradientDrawable shape(int color, int radius, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color); d.setCornerRadius(radius);
        if (stroke > 0) d.setStroke(2, RED);
        return d;
    }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value); t.setTextSize(size); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }
    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label); b.setTextColor(Color.WHITE); b.setAllCaps(false); b.setTextSize(16);
        b.setBackground(shape(RED, 16, 0)); return b;
    }

    private void buildUi() {
        LinearLayout base = new LinearLayout(this);
        base.setOrientation(LinearLayout.VERTICAL); base.setPadding(18, 22, 18, 14); base.setBackgroundColor(BG);
        TextView title = text("EXE AI", 30, RED, true); title.setGravity(Gravity.CENTER);
        title.setOnLongClickListener(v -> { configureServer(); return true; });
        TextView subtitle = text("OPENAI ONLINE  //  OFFLINE COMMANDS", 10, Color.LTGRAY, true);
        subtitle.setGravity(Gravity.CENTER);
        status = text("● Sunucu ayarlanmamış", 11, Color.GRAY, false); status.setGravity(Gravity.CENTER);
        base.addView(title); base.addView(subtitle); base.addView(status);
        View line = new View(this); line.setBackgroundColor(RED);
        LinearLayout.LayoutParams lpLine = new LinearLayout.LayoutParams(-1, 2); lpLine.setMargins(0, 12, 0, 12);
        base.addView(line, lpLine);
        scroll = new ScrollView(this); messages = new LinearLayout(this); messages.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(messages); base.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView hint = text("Örnek: kamerayı aç • Wi-Fi ayarları • YouTube aç • Bana Python kodu yaz", 11, Color.GRAY, false);
        hint.setPadding(2, 8, 2, 8); base.addView(hint);
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
        input = new EditText(this); input.setSingleLine(true); input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.GRAY); input.setHint("Komutunu yaz...");
        input.setPadding(12, 8, 12, 8); input.setBackground(shape(PANEL, 18, 1));
        input.setImeOptions(EditorInfo.IME_ACTION_SEND);
        row.addView(input, new LinearLayout.LayoutParams(0, 52, 1));
        Button send = button("➤"); LinearLayout.LayoutParams b1 = new LinearLayout.LayoutParams(54, 52);
        b1.setMargins(8, 0, 0, 0); row.addView(send, b1); send.setOnClickListener(v -> submit());
        input.setOnEditorActionListener((v, action, event) -> { submit(); return true; });
        Button voice = button("🎙"); LinearLayout.LayoutParams b2 = new LinearLayout.LayoutParams(54, 52);
        b2.setMargins(8, 0, 0, 0); row.addView(voice, b2); voice.setOnClickListener(v -> startVoice());
        base.addView(row); setContentView(base); refreshStatus();
    }

    private void configureServer() {
        EditText field = new EditText(this);
        field.setSingleLine(true); field.setText(prefs.getString("server_url", ""));
        field.setHint("https://sunucu-adresin.example");
        new AlertDialog.Builder(this).setTitle("EXE AI sunucu adresi")
            .setMessage("OpenAI anahtarını buraya yazma. Buraya yalnızca kendi güvenli backend adresini gir. Örnek: https://exe-ai-api.example.com")
            .setView(field)
            .setNegativeButton("İptal", null)
            .setPositiveButton("Kaydet", (d, w) -> {
                String value = field.getText().toString().trim().replaceAll("/+$", "");
                prefs.edit().putString("server_url", value).remove("previous_response_id").apply();
                previousResponseId = null; refreshStatus();
                addMessage("EXE AI", "Sunucu adresi kaydedildi. İnternet sohbeti için bağlantıyı deneyebilirsin.", false);
            }).show();
    }

    private void refreshStatus() {
        String s = prefs.getString("server_url", "");
        status.setText(s.isEmpty() ? "● Sunucu ayarlanmamış — çevrim dışı komutlar kullanılabilir" : "● OpenAI sunucusu yapılandırıldı");
        status.setTextColor(s.isEmpty() ? Color.GRAY : Color.rgb(100, 220, 130));
    }

    private void addMessage(String who, String msg, boolean user) {
        TextView t = text((user ? "YOU" : who) + "\n" + msg, 14, Color.WHITE, false);
        t.setPadding(14, 11, 14, 11);
        t.setBackground(shape(user ? Color.rgb(55, 25, 31) : PANEL, 15, user ? 1 : 0));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, 5, 0, 5); messages.addView(t, p);
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    private void submit() {
        String command = input.getText().toString().trim();
        if (command.isEmpty() || waiting) return;
        input.setText(""); addMessage("YOU", command, true);
        if (!handleLocal(command)) sendToOpenAI(command);
    }

    private String clean(String s) {
        s = s.toLowerCase(Locale.ROOT).replace('ı','i').replace('ğ','g').replace('ü','u').replace('ş','s').replace('ö','o').replace('ç','c');
        s = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return s.replaceAll("[^a-z0-9.:/ ]", " ").replaceAll("\\s+", " ").trim();
    }
    private void reply(String s) { addMessage("EXE AI", s, false); }

    // These are explicit, limited on-device commands. Other requests go to OpenAI.
    private boolean handleLocal(String raw) {
        String c = clean(raw);
        if (c.matches(".*\\b(merhaba|selam|hello|hi|hey)\\b.*")) { reply("Merhaba! EXE AI çevrim içi veya çevrim dışı yardımcı olabilir."); return true; }
        if (c.contains("yardim") || c.equals("help") || c.contains("komutlar")) {
            reply("Yerel komutlar:\n• kamera aç / open camera\n• ayarları aç / open settings\n• wifi ayarları / wifi settings\n• bluetooth ayarları\n• youtube aç / open youtube\n• google.com aç\n• saat kaç / time\n• tarih / date\nDiğer sorular OpenAI sunucusuna gönderilir (sunucu ayarlı ve internet varsa)."); return true;
        }
        if (c.contains("saat") || c.equals("time") || c.contains("what time")) {
            reply(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date())); return true;
        }
        if (c.contains("tarih") || c.equals("date") || c.contains("what date")) {
            reply(new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(new Date())); return true;
        }
        if (c.contains("kamera") || c.contains("camera")) {
            try { startActivity(new Intent("android.media.action.IMAGE_CAPTURE")); reply("Kamera uygulaması açılıyor."); }
            catch (Exception e) { reply("Kamera uygulaması bulunamadı."); } return true;
        }
        if (c.contains("wifi") || c.contains("wi fi")) { openSettings(Settings.ACTION_WIFI_SETTINGS, "Wi-Fi ayarları açılıyor."); return true; }
        if (c.contains("bluetooth")) { openSettings(Settings.ACTION_BLUETOOTH_SETTINGS, "Bluetooth ayarları açılıyor."); return true; }
        if (c.contains("ayar") || c.contains("settings")) { openSettings(Settings.ACTION_SETTINGS, "Ayarlar açılıyor."); return true; }
        if (c.contains("youtube")) { openUrl("https://www.youtube.com"); return true; }
        if (c.contains("google")) { openUrl("https://www.google.com"); return true; }
        String url = extractUrl(raw);
        if (url != null) { openUrl(url); return true; }
        String app = c.replaceAll("\\b(uygulamasini|uygulamayi|uygulama|app|ac|open|launch|baslat|calistir|please|the)\\b", "").trim();
        if (!app.isEmpty() && launchApp(app)) return true;
        return false;
    }

    private void openSettings(String action, String message) {
        try { startActivity(new Intent(action)); reply(message); }
        catch (Exception e) { reply("Bu ayar ekranı cihazda desteklenmiyor."); }
    }
    private String extractUrl(String raw) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("((https?://)?([a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,}([/\\\\w?&=.%#-]*)?)").matcher(raw.trim());
        if (m.find()) { String u=m.group(1); return u.startsWith("http")?u:"https://"+u; }
        return null;
    }
    private void openUrl(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); reply("Site tarayıcıda açılıyor: " + url); }
        catch (Exception e) { reply("Site açılamadı."); }
    }
    private boolean launchApp(String query) {
        try {
            PackageManager pm=getPackageManager();
            List<ApplicationInfo> apps=pm.getInstalledApplications(PackageManager.GET_META_DATA);
            String q=clean(query); ApplicationInfo best=null; int bestScore=999;
            for (ApplicationInfo info:apps) {
                String label=clean(pm.getApplicationLabel(info).toString());
                if (label.isEmpty()) continue;
                if (label.equals(q)||label.contains(q)||q.contains(label)) { best=info; bestScore=0; break; }
                int score=distance(q,label); if(score<bestScore){bestScore=score;best=info;}
            }
            if(best!=null && bestScore<=Math.max(2,q.length()/3)) {
                Intent launch=pm.getLaunchIntentForPackage(best.packageName);
                if(launch!=null){startActivity(launch);reply(pm.getApplicationLabel(best)+" açılıyor.");return true;}
            }
        } catch(Exception ignored) {}
        return false;
    }
    private int distance(String a,String b) {
        int[] prev=new int[b.length()+1],cur=new int[b.length()+1];
        for(int j=0;j<=b.length();j++)prev[j]=j;
        for(int i=1;i<=a.length();i++){cur[0]=i;for(int j=1;j<=b.length();j++)cur[j]=Math.min(Math.min(cur[j-1]+1,prev[j]+1),prev[j-1]+(a.charAt(i-1)==b.charAt(j-1)?0:1));int[] t=prev;prev=cur;cur=t;}
        return prev[b.length()];
    }

    private void sendToOpenAI(String message) {
        String base=prefs.getString("server_url","");
        if(base.isEmpty()){reply("Bu istek için OpenAI bağlantısı gerekiyor. Önce EXE AI başlığına uzun basıp güvenli sunucu adresini ayarla. İnternetsizken yalnızca yerel komutlar çalışır.");return;}
        waiting=true; status.setText("● OpenAI yanıtı bekleniyor…");
        network.execute(() -> {
            HttpURLConnection conn=null;
            try {
                URL url=new URL(base+"/chat");
                conn=(HttpURLConnection)url.openConnection();
                conn.setRequestMethod("POST"); conn.setConnectTimeout(20000); conn.setReadTimeout(90000);
                conn.setDoOutput(true); conn.setRequestProperty("Content-Type","application/json; charset=utf-8");
                JSONObject body=new JSONObject(); body.put("message",message);
                if(previousResponseId!=null) body.put("previous_response_id",previousResponseId);
                byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);
                conn.getOutputStream().write(bytes);
                int code=conn.getResponseCode();
                InputStream stream=(code>=200&&code<300)?conn.getInputStream():conn.getErrorStream();
                String response=readAll(stream);
                JSONObject json=new JSONObject(response);
                if(code<200||code>=300) throw new Exception(json.optString("error","Sunucu hatası ("+code+")"));
                String answer=json.optString("text","Yanıt boş geldi.");
                String rid=json.optString("response_id","");
                if(!rid.isEmpty()){previousResponseId=rid;prefs.edit().putString("previous_response_id",rid).apply();}
                runOnUiThread(() -> {waiting=false;reply(answer);refreshStatus();});
            } catch(Exception e) {
                String msg=e.getMessage()==null?"Bağlantı kurulamadı.":e.getMessage();
                runOnUiThread(() -> {waiting=false;reply("OpenAI bağlantısı başarısız: "+msg+"\nYerel komutları kullanmaya devam edebilirsin.");refreshStatus();});
            } finally {if(conn!=null)conn.disconnect();}
        });
    }
    private String readAll(InputStream in)throws Exception {
        if(in==null)return "{}";
        ByteArrayOutputStream out=new ByteArrayOutputStream(); byte[] buf=new byte[4096]; int n;
        while((n=in.read(buf))!=-1)out.write(buf,0,n);
        return out.toString("UTF-8");
    }
    private void startVoice() {
        try {
            Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"tr-TR"); i.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true);
            i.putExtra(RecognizerIntent.EXTRA_PROMPT,"EXE AI komutunu söyle"); startActivityForResult(i,VOICE_REQUEST);
        } catch(Exception e){reply("Bu cihazda ses tanıma hizmeti bulunamadı.");}
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==VOICE_REQUEST&&resultCode==RESULT_OK&&data!=null){
            ArrayList<String> r=data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if(r!=null&&!r.isEmpty()){input.setText(r.get(0));submit();}
        }
    }
    @Override protected void onDestroy(){super.onDestroy();network.shutdown();}
  }
      
