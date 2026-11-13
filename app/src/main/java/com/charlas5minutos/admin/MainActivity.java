package com.charlas5minutos.admin;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Bundle;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Base64;
import java.io.OutputStream;
public class MainActivity extends Activity {
 private static final int PICK=1427;
 private static final int SAVE_EXCEL=1428;
 private byte[] pendingExcel;
 private WebView web;
 private ValueCallback<Uri[]> selection;
 private static final String URL_KEY="charlas5_v2_apps_script_url";
 private volatile String currentScreen="login";
 private long lastBackPress=0L;
 @Override public void onCreate(Bundle saved){
  super.onCreate(saved);
  getWindow().setStatusBarColor(Color.WHITE);
  getWindow().getDecorView().setSystemUiVisibility(android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
  web=new WebView(this);
  web.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));
  setContentView(web);
  WebSettings s=web.getSettings();
  s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setAllowFileAccess(false);s.setAllowContentAccess(true);
  s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);s.setBuiltInZoomControls(true);s.setDisplayZoomControls(false);
  CookieManager.getInstance().setAcceptCookie(true);
  CookieManager.getInstance().setAcceptThirdPartyCookies(web,true);
  web.addJavascriptInterface(new NativeBridge(),"AndroidBridge");
  web.setWebChromeClient(new WebChromeClient(){
   @Override public boolean onConsoleMessage(ConsoleMessage m){if(m.messageLevel()==ConsoleMessage.MessageLevel.ERROR){android.util.Log.w("C5-V2","Error JS en línea "+m.lineNumber());}return false;}
   @Override public boolean onShowFileChooser(WebView v,ValueCallback<Uri[]> cb,FileChooserParams p){
    if(selection!=null)selection.onReceiveValue(null);
    selection=cb;
    Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
    i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("image/*");i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);
    try{startActivityForResult(i,PICK);}catch(Exception e){selection.onReceiveValue(null);selection=null;}
    return true;
   }
  });
  web.setWebViewClient(new WebViewClient(){
   @Override public void onReceivedError(WebView v,WebResourceRequest request,android.webkit.WebResourceError err){if(request.isForMainFrame())Toast.makeText(MainActivity.this,"No se pudo cargar la aplicación. Revisa la conexión y selecciona Actualizar.",Toast.LENGTH_LONG).show();}
   @Override public void onReceivedSslError(WebView w,SslErrorHandler h,SslError e){h.cancel();}
   @Override public boolean shouldOverrideUrlLoading(WebView w,WebResourceRequest req){
    Uri u=req.getUrl();String host=u.getHost();
    if("https".equals(u.getScheme())&&host!=null&&(host.equals("script.google.com")||host.equals("script.googleusercontent.com")||host.endsWith(".googleusercontent.com")))return false;
    try{startActivity(new Intent(Intent.ACTION_VIEW,u));}catch(Exception e){}
    return true;
   }
  });
  String u=getPreferences(MODE_PRIVATE).getString(URL_KEY,"");
  if(u.isEmpty())config();else web.loadUrl(u+"?view=admin");
 }
 private String clean(String raw){
  try{
   Uri u=Uri.parse(raw.trim());String p=u.getPath();
   if(!"https".equals(u.getScheme())||!"script.google.com".equals(u.getHost())||p==null||!p.matches("/macros/s/[A-Za-z0-9_-]+/exec"))return null;
   return new Uri.Builder().scheme("https").authority("script.google.com").path(p).build().toString();
  }catch(Exception e){return null;}
 }
 private void config(){
  LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(45,20,45,10);
  TextView tv=new TextView(this);tv.setText("Pega aquí la dirección /exec de tu aplicación web de Google Apps Script.");l.addView(tv);
  EditText e=new EditText(this);e.setHint("https://script.google.com/macros/s/.../exec");l.addView(e);
  AlertDialog d=new AlertDialog.Builder(this).setTitle("Conectar Charlas 5 Minutos V2.3").setView(l).setCancelable(false).setPositiveButton("Conectar",null).create();
  d.setOnShowListener(v->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b->{
   String u=clean(e.getText().toString());if(u==null){e.setError("Enlace no válido");return;}
   getPreferences(MODE_PRIVATE).edit().putString(URL_KEY,u).apply();d.dismiss();web.loadUrl(u+"?view=admin");
  }));d.show();
 }
 @Override protected void onActivityResult(int r,int result,Intent data){
  super.onActivityResult(r,result,data);
  if(r==SAVE_EXCEL){
   byte[] bytes=pendingExcel;pendingExcel=null;
   if(result==RESULT_OK&&data!=null&&data.getData()!=null&&bytes!=null){
    try(OutputStream stream=getContentResolver().openOutputStream(data.getData())){
     if(stream==null)throw new IllegalStateException("No se pudo abrir el destino");
     stream.write(bytes);stream.flush();
     Toast.makeText(this,"Excel guardado correctamente",Toast.LENGTH_LONG).show();
    }catch(Exception e){
     Toast.makeText(this,"No se pudo guardar Excel: "+e.getMessage(),Toast.LENGTH_LONG).show();
    }
   }
   return;
  }
  if(r!=PICK||selection==null)return;
  Uri[] files=null;
  if(result==RESULT_OK&&data!=null){
   if(data.getClipData()!=null){
    int n=data.getClipData().getItemCount();files=new Uri[n];
    for(int i=0;i<n;i++)files[i]=data.getClipData().getItemAt(i).getUri();
   }else if(data.getData()!=null){files=new Uri[]{data.getData()};}
  }
  selection.onReceiveValue(files);selection=null;
 }
 public class NativeBridge{
  @JavascriptInterface public void exportExcel(String filename,String base64){
   runOnUiThread(()->{
    if(pendingExcel!=null){Toast.makeText(MainActivity.this,"Termina de guardar el archivo anterior",Toast.LENGTH_SHORT).show();return;}
    try{
     if(base64==null||base64.length()>12*1024*1024)throw new IllegalArgumentException("Excel excede el tamaño permitido");
     byte[] data=Base64.decode(base64,Base64.DEFAULT);
     if(data.length<100||data[0]!='P'||data[1]!='K')throw new IllegalArgumentException("El archivo XLSX no es válido");
     String safeName=filename==null?"Reporte.xlsx":filename.replaceAll("[^a-zA-Z0-9_.-]","_");
     if(!safeName.endsWith(".xlsx"))safeName+=".xlsx";
     pendingExcel=data;
     Intent save=new Intent(Intent.ACTION_CREATE_DOCUMENT);
     save.addCategory(Intent.CATEGORY_OPENABLE);
     save.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
     save.putExtra(Intent.EXTRA_TITLE,safeName);
     startActivityForResult(save,SAVE_EXCEL);
    }catch(Exception e){
     pendingExcel=null;Toast.makeText(MainActivity.this,"No se pudo preparar Excel: "+e.getMessage(),Toast.LENGTH_LONG).show();
    }
   });
  }
  @JavascriptInterface public void setScreen(String screen){
    if(screen!=null && screen.matches("[A-Za-z]+"))currentScreen=screen;
  }
  @JavascriptInterface public void shareWhatsApp(String txt){runOnUiThread(()->{
   Intent i=new Intent(Intent.ACTION_SEND);i.setType("text/plain");i.putExtra(Intent.EXTRA_TEXT,txt);i.setPackage("com.whatsapp");
   try{startActivity(i);}catch(ActivityNotFoundException ex){
    i.setPackage("com.whatsapp.w4b");
    try{startActivity(i);}catch(ActivityNotFoundException ex2){
     i.setPackage(null);startActivity(Intent.createChooser(i,"Compartir capacitación"));
    }
   }
  });}
  @JavascriptInterface public void printPage(){runOnUiThread(()->{
   PrintManager p=(PrintManager)getSystemService(Context.PRINT_SERVICE);
   p.print("Charlas 5 Minutos",web.createPrintDocumentAdapter("Charlas 5 Minutos"),new PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build());
  });}
 }
 @Override public boolean onCreateOptionsMenu(Menu m){m.add(0,1,0,"Configurar URL");m.add(0,2,1,"Actualizar");m.add(0,3,2,"Limpiar caché");return true;}
 @Override public boolean onOptionsItemSelected(MenuItem i){if(i.getItemId()==1){config();return true;}if(i.getItemId()==2){web.reload();return true;}if(i.getItemId()==3){web.clearCache(true);web.reload();return true;}return super.onOptionsItemSelected(i);}
 private void navigateBackInApp(){
  // Apps Script HtmlService suele ejecutar la UI dentro de un iframe.
  // postMessage alcanza al documento principal y a sus marcos sin leer su contenido.
  String js="(function(){var msg={type:'C5_NATIVE_BACK'};"+
      "try{window.postMessage(msg,'*');}catch(e){}"+
      "var frames=document.querySelectorAll('iframe');"+
      "for(var j=0;j<frames.length;j++){try{frames[j].contentWindow.postMessage(msg,'*');}catch(e){}}"+
      "})()";
  web.evaluateJavascript(js,null);
 }
 @Override public void onBackPressed(){
  if(!"inicio".equals(currentScreen)&&!"login".equals(currentScreen)){
    navigateBackInApp();return;
  }
  long now=android.os.SystemClock.elapsedRealtime();
  if(now-lastBackPress<1900){finish();return;}
  lastBackPress=now;
  Toast.makeText(this,"Presiona Atrás otra vez para salir",Toast.LENGTH_SHORT).show();
 }
 @Override protected void onDestroy(){if(selection!=null)selection.onReceiveValue(null);pendingExcel=null;web.destroy();super.onDestroy();}
}
