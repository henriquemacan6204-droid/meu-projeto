package br.com.lumaglass.browser;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.mozilla.geckoview.ContentBlocking;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoRuntimeSettings;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSessionSettings;
import org.mozilla.geckoview.GeckoView;
import org.mozilla.geckoview.WebExtension;
import org.mozilla.geckoview.WebExtensionController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static GeckoRuntime runtime;
    private SharedPreferences prefs;
    private final ArrayList<Tab> tabs = new ArrayList<>();
    private Tab active;
    private GeckoView web;
    private FrameLayout pages;
    private LinearLayout root, bar, homepage;
    private EditText address;
    private ProgressBar loading;
    private TextView counter, subheading;
    private WebExtension blocker;
    private boolean dark;
    private boolean showHome=true;

    private static final String IOS_UA =
        "Mozilla/5.0 (iPhone; CPU iPhone OS 27_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/27.0 Mobile/15E148 Safari/604.1";
    private static final String[] ACTIONS={
        "AdBlock", "Antirrastreamento", "Extensões", "VPN do Android",
        "Favoritar página", "Favoritos", "Histórico", "Abas abertas",
        "Nova aba privativa", "Compartilhar", "Recarregar", "Configurações"
    };
    private static final class Tab {
        GeckoSession session;
        String url="";
        String title="Nova aba";
        boolean privateMode=false;
        boolean canBack,canForward;
    }
    private int dp(float value){return (int)(value*getResources().getDisplayMetrics().density+0.5f);}
    private int bg(){return dark?0xFF090C13:0xFFF0F4FA;}
    private int fg(){return dark?0xFFF5F7FF:0xFF121B2E;}
    private int soft(){return dark?0xFFB5C2D8:0xFF4B5A71;}
    private int glass(){
        int percent=prefs.getInt("glass",72);
        int alpha=(int)(40+percent*1.8);
        return (alpha<<24)|(dark?0x161D2C:0xFFFFFF);
    }
    private GradientDrawable rounded(int fill,float radius,boolean border){
        GradientDrawable d=new GradientDrawable();
        d.setColor(fill);d.setCornerRadius(dp(radius));
        if(border)d.setStroke(dp(1),dark?0x50FFFFFF:0x77FFFFFF);
        return d;
    }
    private TextView text(String s,int size,boolean bold){
        TextView t=new TextView(this);
        t.setText(s);t.setTextColor(fg());t.setTextSize(size);
        if(bold)t.setTypeface(android.graphics.Typeface.DEFAULT,1);
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }
    private LinearLayout.LayoutParams lp(int w,int h){return new LinearLayout.LayoutParams(w<0?w:dp(w),h<0?h:dp(h));}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(0xFF090C13);
        getWindow().setNavigationBarColor(0xFF090C13);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        prefs=getSharedPreferences("lumaglass",MODE_PRIVATE);
        dark=prefs.getBoolean("dark",true);
        if(runtime==null){
            ContentBlocking.Settings cb=new ContentBlocking.Settings.Builder()
                .antiTracking(ContentBlocking.AntiTracking.STRICT)
                .cookieBehavior(ContentBlocking.CookieBehavior.ACCEPT_NON_TRACKERS)
                .cookiePurging(true)
                .build();
            runtime=GeckoRuntime.create(getApplicationContext(),new GeckoRuntimeSettings.Builder()
                .contentBlocking(cb).build());
        }
        buildLayout();
        runtime.getWebExtensionController().setPromptDelegate(new WebExtensionController.PromptDelegate(){
            @Override public GeckoResult<WebExtension.PermissionPromptResponse> onInstallPromptRequest(
                WebExtension extension,String[] permissions,String[] origins,String[] dataCollectionPermissions){
                GeckoResult<WebExtension.PermissionPromptResponse> result=new GeckoResult<>();
                runOnUiThread(()->{
                    String id=extension.id;
                    String info="Extensão: "+id+"\n\nPermissões solicitadas:\n"+
                        String.join(", ",permissions)+"\n\nSites:\n"+String.join(", ",origins)+
                        "\n\nInstale apenas extensões em que confia.";
                    new AlertDialog.Builder(MainActivity.this).setTitle("Permitir extensão?")
                        .setMessage(info).setNegativeButton("Recusar",(d,w)->result.complete(
                            new WebExtension.PermissionPromptResponse(false,false,false)))
                        .setPositiveButton("Instalar",(d,w)->result.complete(
                            new WebExtension.PermissionPromptResponse(true,false,false)))
                        .setOnCancelListener(d->result.complete(
                            new WebExtension.PermissionPromptResponse(false,false,false))).show();
                });
                return result;
            }
        });
        runtime.getWebExtensionController()
            .ensureBuiltIn("resource://android/assets/glassblock/","glassblock@lumaglass.local")
            .accept(ext->{blocker=ext;if(!prefs.getBoolean("ads",true))
                runtime.getWebExtensionController().disable(ext,WebExtensionController.EnableSource.USER)
                    .accept(x->{},e->toast("Erro ao pausar AdBlock: "+e.getMessage()));},
                err->toast("AdBlock indisponível: "+err.getMessage()));
        createTab(false,true);
        Intent start=getIntent();
        if(start!=null && start.getData()!=null)open(start.getData().toString());
    }
    private void toast(String s){runOnUiThread(()->Toast.makeText(this,s,Toast.LENGTH_LONG).show());}
    private void buildLayout(){
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(bg());
        setContentView(root);
        LinearLayout header=new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(22),dp(8),dp(20),dp(4));
        TextView logo=text("LUMA",19,true);logo.setLetterSpacing(.22f);
        header.addView(logo,new LinearLayout.LayoutParams(0,dp(38),1));
        subheading=text("GLASS · NAVEGADOR PRIVADO",9,true);
        subheading.setTextColor(soft());header.addView(subheading,lp(-2,38));
        root.addView(header,lp(-1,45));
        loading=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        loading.setMax(100);loading.setProgress(0);loading.setVisibility(View.INVISIBLE);
        root.addView(loading,lp(-1,2));
        pages=new FrameLayout(this);
        root.addView(pages,new LinearLayout.LayoutParams(-1,0,1));
        web=new GeckoView(this);pages.addView(web,new FrameLayout.LayoutParams(-1,-1));
        createHomepage();
        bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);
        int pad=prefs.getInt("glass",72)>80?8:7;
        bar.setPadding(dp(7),dp(pad),dp(7),dp(pad));bar.setBackground(rounded(glass(),37,true));
        LinearLayout shell=new LinearLayout(this);shell.setPadding(dp(10),dp(8),dp(10),dp(12));
        shell.addView(bar,lp(-1,62));root.addView(shell,lp(-1,-2));
        makeButton("back",()->{if(active!=null&&active.canBack)active.session.goBack();});
        makeButton("next",()->{if(active!=null&&active.canForward)active.session.goForward();});
        address=new EditText(this);address.setSingleLine(true);
        address.setTextSize(13);address.setTextColor(fg());address.setHintTextColor(soft());
        address.setHint("Buscar ou inserir URL");
        address.setPadding(dp(13),0,dp(8),0);
        address.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        address.setImeOptions(EditorInfo.IME_ACTION_GO);
        address.setBackground(rounded(dark?0x802E3A51:0xC8FFFFFF,35,true));
        address.setOnEditorActionListener((v,act,event)->{
            if(act==EditorInfo.IME_ACTION_GO||(event!=null && event.getKeyCode()==KeyEvent.KEYCODE_ENTER)){
                String q=address.getText().toString();hideKeyboard();open(q);return true;}
            return false;
        });
        bar.addView(address,new LinearLayout.LayoutParams(0,dp(48),1));
        makeButton("tabs",()->tabPicker());
        makeButton("menu",()->menu());
        if(active!=null)refreshBar();
    }
    private void hideKeyboard(){
        ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(address.getWindowToken(),0);
        address.clearFocus();
    }
    private void makeButton(String which,Runnable action){
        IconButton b=new IconButton(this,which);b.setColor(fg());
        b.setBackground(rounded(0x00FFFFFF,30,false));
        b.setOnClickListener(v->action.run());
        bar.addView(b,lp(42,46));
    }
    private void createHomepage(){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        LinearLayout home=new LinearLayout(this);home.setOrientation(LinearLayout.VERTICAL);
        home.setPadding(dp(24),dp(38),dp(24),dp(24));
        TextView title=text("Tudo começa\ncom uma busca.",35,true);
        title.setLineSpacing(dp(3),1f);home.addView(title,lp(-1,-2));
        TextView caption=text("Rápido. Discreto. Do seu jeito.",14,false);
        caption.setTextColor(soft());home.addView(caption,lp(-1,54));
        TextView section=text("ACESSO RÁPIDO",11,true);section.setLetterSpacing(.14f);
        section.setTextColor(soft());home.addView(section,lp(-1,64));
        addShortcut(home,"Google","google.com","Buscar na web",0xFF4285F4);
        addShortcut(home,"YouTube","youtube.com","Vídeos e canais",0xFFFF3F47);
        addShortcut(home,"Instagram","instagram.com","Social",0xFFB743B8);
        addShortcut(home,"GitHub","github.com","Projetos e código",0xFF6585A2);
        TextView footer=text("Proteção antirrastreamento · GeckoView\nSites podem identificar o navegador real apesar do modo iPhone.",11,false);
        footer.setTextColor(soft());footer.setPadding(0,dp(20),0,0);home.addView(footer,lp(-1,75));
        scroll.addView(home,new ScrollView.LayoutParams(-1,-2));
        homepage=new LinearLayout(this);homepage.addView(scroll,lp(-1,-1));
        homepage.setBackgroundColor(bg());pages.addView(homepage,new FrameLayout.LayoutParams(-1,-1));
    }
    private void addShortcut(LinearLayout parent,String title,String url,String caption,int accent){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(15),dp(8),dp(14),dp(8));
        row.setBackground(rounded(glass(),22,true));
        TextView letter=text(title.substring(0,1),22,true);letter.setGravity(Gravity.CENTER);
        letter.setTextColor(Color.WHITE);letter.setBackground(rounded(accent,16,false));row.addView(letter,lp(45,45));
        LinearLayout descriptions=new LinearLayout(this);descriptions.setOrientation(LinearLayout.VERTICAL);
        descriptions.setPadding(dp(14),0,0,0);
        TextView name=text(title,15,true);descriptions.addView(name,lp(-1,24));
        TextView detail=text(caption,12,false);detail.setTextColor(soft());descriptions.addView(detail,lp(-1,20));
        row.addView(descriptions,new LinearLayout.LayoutParams(0,-2,1));
        row.setOnClickListener(v->open(url));
        LinearLayout.LayoutParams item=lp(-1,65);item.bottomMargin=dp(10);parent.addView(row,item);
    }
    private void createTab(boolean privateMode,boolean select){
        Tab tab=new Tab();tab.privateMode=privateMode;
        GeckoSessionSettings.Builder settings=new GeckoSessionSettings.Builder()
            .usePrivateMode(privateMode)
            .useTrackingProtection(prefs.getBoolean("track",true));
        tab.session=new GeckoSession(settings.build());
        applyUA(tab);
        tab.session.setNavigationDelegate(new GeckoSession.NavigationDelegate(){
            @Override public void onLocationChange(GeckoSession s,String url,
                 List<GeckoSession.PermissionDelegate.ContentPermission> perms,Boolean userGesture){
                if(url!=null){tab.url=url;if(!tab.privateMode)remember(url);}
                if(active==tab){showHome=false;homepage.setVisibility(View.GONE);refreshBar();}
            }
            @Override public void onCanGoBack(GeckoSession s,boolean value){tab.canBack=value;}
            @Override public void onCanGoForward(GeckoSession s,boolean value){tab.canForward=value;}
            @Override public GeckoResult<GeckoSession> onNewSession(GeckoSession s,String uri){
                Tab child=new Tab();child.privateMode=tab.privateMode;
                GeckoSessionSettings.Builder childSettings=new GeckoSessionSettings.Builder()
                    .usePrivateMode(child.privateMode)
                    .useTrackingProtection(prefs.getBoolean("track",true));
                child.session=new GeckoSession(childSettings.build());applyUA(child);
                tabs.add(child);attachDelegates(child);
                child.session.open(runtime);selectTab(child);
                return GeckoResult.fromValue(child.session);
            }
        });
        attachDelegates(tab);
        tab.session.open(runtime);
        tabs.add(tab);
        if(select)selectTab(tab);
    }
    private void attachDelegates(Tab tab){
        tab.session.setProgressDelegate(new GeckoSession.ProgressDelegate(){
            @Override public void onPageStart(GeckoSession session,String url){
                if(active==tab){loading.setProgress(12);loading.setVisibility(View.VISIBLE);}
            }
            @Override public void onProgressChange(GeckoSession session,int progress){
                if(active==tab)loading.setProgress(progress);
            }
            @Override public void onPageStop(GeckoSession session,boolean success){
                if(active==tab)loading.setVisibility(View.INVISIBLE);
            }
        });
        tab.session.setContentDelegate(new GeckoSession.ContentDelegate(){
            @Override public void onTitleChange(GeckoSession session,String title){
                if(title!=null&&!title.isEmpty())tab.title=title;
            }
        });
    }
    private void selectTab(Tab tab){
        if(active!=null)web.releaseSession();
        active=tab;web.setSession(tab.session);
        showHome=tab.url.isEmpty();
        homepage.setVisibility(showHome?View.VISIBLE:View.GONE);
        refreshBar();
    }
    private void refreshBar(){
        if(address==null || active==null)return;
        if(!address.hasFocus())address.setText(showHome?"":active.url);
        subheading.setText(active.privateMode?"MODO PRIVATIVO · NÃO SALVA HISTÓRICO":"GLASS · NAVEGADOR PRIVADO");
    }
    private void applyUA(Tab tab){
        String choice=prefs.getString("ua","android");
        if("iphone".equals(choice)){
            tab.session.getSettings().setUserAgentOverride(IOS_UA);
            tab.session.getSettings().setViewportMode(GeckoSessionSettings.VIEWPORT_MODE_MOBILE);
        }else{
            tab.session.getSettings().setUserAgentOverride(null);
            tab.session.getSettings().setUserAgentMode("desktop".equals(choice)?
                GeckoSessionSettings.USER_AGENT_MODE_DESKTOP:GeckoSessionSettings.USER_AGENT_MODE_MOBILE);
            tab.session.getSettings().setViewportMode("desktop".equals(choice)?
                GeckoSessionSettings.VIEWPORT_MODE_DESKTOP:GeckoSessionSettings.VIEWPORT_MODE_MOBILE);
        }
    }
    private void open(String raw){
        String input=raw==null?"":raw.trim();
        if(input.isEmpty()){showHome=true;homepage.setVisibility(View.VISIBLE);return;}
        String url;
        if(input.matches("(?i)^https?://.+"))url=input;
        else if(!input.contains(" ")&&(input.contains(".")||input.equals("localhost")))url="https://"+input;
        else{
            String q=URLEncoder.encode(input,StandardCharsets.UTF_8);
            String search=prefs.getString("search","Google");
            url="DuckDuckGo".equals(search)?"https://duckduckgo.com/?q="+q:
                "Bing".equals(search)?"https://www.bing.com/search?q="+q:"https://www.google.com/search?q="+q;
        }
        showHome=false;homepage.setVisibility(View.GONE);
        active.session.loadUri(url);address.setText(url);
    }
    private void tabPicker(){
        ArrayList<String> titles=new ArrayList<>();
        for(int i=0;i<tabs.size();i++){
            Tab tab=tabs.get(i);
            titles.add((tab==active?"● ":"○ ")+(i+1)+" · "+(tab.privateMode?"[Privado] ":"")+tab.title);
        }
        titles.add("＋ Nova aba");titles.add("＋ Nova privativa");titles.add("× Fechar aba atual");
        new AlertDialog.Builder(this).setTitle("Abas · "+tabs.size()).setItems(titles.toArray(new String[0]),(d,which)->{
            if(which<tabs.size())selectTab(tabs.get(which));
            else if(which==tabs.size())createTab(false,true);
            else if(which==tabs.size()+1)createTab(true,true);
            else closeTab();
        }).setNegativeButton("Cancelar",null).show();
    }
    private void closeTab(){
        if(active==null)return;
        Tab closed=active;web.releaseSession();tabs.remove(closed);closed.session.close();active=null;
        if(tabs.isEmpty())createTab(false,true);
        else selectTab(tabs.get(tabs.size()-1));
    }
    private void menu(){
        String[] choices=ACTIONS.clone();
        choices[0]=(prefs.getBoolean("ads",true)?"Desligar":"Ligar")+" AdBlock";
        choices[1]=(prefs.getBoolean("track",true)?"Desligar":"Ligar")+" antirrastreamento";
        new AlertDialog.Builder(this).setTitle("Ferramentas · Luma Glass").setItems(choices,(d,index)->{
            switch(index){
                case 0: toggleAds();break;
                case 1: toggleTracking();break;
                case 2: extensionManager();break;
                case 3: vpn();break;
                case 4: if(active!=null&&!active.url.isEmpty())saveBookmark(active.url);break;
                case 5: showSaved("Favoritos","favorites");break;
                case 6: showSaved("Histórico","history");break;
                case 7: tabPicker();break;
                case 8: createTab(true,true);break;
                case 9: share();break;
                case 10: if(active!=null)active.session.reload();break;
                case 11: settingsMenu();break;
            }
        }).show();
    }
    private void toggleAds(){
        boolean enabled=!prefs.getBoolean("ads",true);
        if(blocker==null){toast("Motor AdBlock ainda iniciando");return;}
        WebExtensionController ctl=runtime.getWebExtensionController();
        if(enabled)ctl.enable(blocker,WebExtensionController.EnableSource.USER).accept(
            e->{blocker=e;prefs.edit().putBoolean("ads",true).apply();toast("AdBlock ligado");},
            ex->toast("Erro ao ligar: "+ex.getMessage()));
        else ctl.disable(blocker,WebExtensionController.EnableSource.USER).accept(
            e->{blocker=e;prefs.edit().putBoolean("ads",false).apply();toast("AdBlock desligado");},
            ex->toast("Erro ao desligar: "+ex.getMessage()));
    }
    private void toggleTracking(){
        boolean enabled=!prefs.getBoolean("track",true);
        prefs.edit().putBoolean("track",enabled).apply();
        for(Tab tab:tabs)tab.session.getSettings().setUseTrackingProtection(enabled);
        toast("Antirrastreamento "+(enabled?"ligado":"desligado"));
    }
    private void vpn(){
        new AlertDialog.Builder(this).setTitle("VPN")
            .setMessage("Uma VPN real requer um provedor ou servidor VPN. O navegador não afirma proteger seu IP sem uma conexão estabelecida.\n\nAbra o painel VPN do Android para configurar ou conectar um provedor.")
            .setPositiveButton("Abrir configurações VPN",(d,w)->{
                try{startActivity(new Intent(Settings.ACTION_VPN_SETTINGS));}
                catch(Exception e){toast("Abra Configurações > Rede e Internet > VPN");}
            }).setNegativeButton("Cancelar",null).show();
    }
    private void extensionManager(){
        String[] choices={"Instalar extensão assinada (.xpi)","Gerenciar extensões instaladas",
            "Abrir catálogo Mozilla"};
        new AlertDialog.Builder(this).setTitle("Extensões WebExtensions")
            .setItems(choices,(d,w)->{
                if(w==0)installExtension();
                if(w==1)listExtensions();
                if(w==2)open("https://addons.mozilla.org/android/");
            }).show();
    }
    private void installExtension(){
        EditText input=new EditText(this);input.setHint("https://.../extensao.xpi");
        input.setSingleLine(true);input.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        FrameLayout wrapper=new FrameLayout(this);wrapper.setPadding(dp(18),dp(12),dp(18),dp(8));
        wrapper.addView(input,new FrameLayout.LayoutParams(-1,dp(54)));
        new AlertDialog.Builder(this).setTitle("Instalar extensão")
            .setMessage("Cole o endereço HTTPS direto de um arquivo .xpi assinado pela Mozilla. Nem todas as extensões são compatíveis com GeckoView.")
            .setView(wrapper).setNegativeButton("Cancelar",null)
            .setPositiveButton("Instalar",(d,w)->{
                String url=input.getText().toString().trim();
                if(!url.startsWith("https://")){toast("Use uma URL HTTPS");return;}
                runtime.getWebExtensionController().install(url).accept(
                    extension->toast("Extensão instalada: "+extension.id),
                    err->toast("Instalação não concluída: "+err.getMessage()));
            }).show();
    }
    private void listExtensions(){
        runtime.getWebExtensionController().list().accept(exts->runOnUiThread(()->{
            if(exts==null||exts.isEmpty()){toast("Nenhuma extensão instalada");return;}
            String[] names=new String[exts.size()];
            for(int i=0;i<exts.size();i++)names[i]=exts.get(i).id;
            new AlertDialog.Builder(this).setTitle("Extensões instaladas").setItems(names,(d,i)->{
                WebExtension ext=exts.get(i);
                new AlertDialog.Builder(this).setTitle(ext.id)
                    .setItems(new String[]{"Ativar","Desativar","Desinstalar"},(dialog,choice)->{
                        WebExtensionController ctl=runtime.getWebExtensionController();
                        if(choice==0)ctl.enable(ext,WebExtensionController.EnableSource.USER)
                            .accept(x->toast("Extensão ativada"),e->toast(e.getMessage()));
                        if(choice==1)ctl.disable(ext,WebExtensionController.EnableSource.USER)
                            .accept(x->toast("Extensão desativada"),e->toast(e.getMessage()));
                        if(choice==2 && !ext.id.equals("glassblock@lumaglass.local"))
                            ctl.uninstall(ext).accept(x->toast("Extensão removida"),e->toast(e.getMessage()));
                        if(choice==2 && ext.id.equals("glassblock@lumaglass.local"))
                            toast("Use a opção AdBlock para controlar a extensão integrada.");
                    }).show();
            }).show();
        }),e->toast("Erro ao listar: "+e.getMessage()));
    }
    private void share(){
        if(active==null||active.url.isEmpty())return;
        Intent intent=new Intent(Intent.ACTION_SEND);intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT,active.url);
        startActivity(Intent.createChooser(intent,"Compartilhar página"));
    }
    private void remember(String url){
        if(!url.startsWith("http"))return;
        ArrayList<String> urls=loadLines("history");urls.remove(url);urls.add(0,url);
        while(urls.size()>100)urls.remove(urls.size()-1);
        prefs.edit().putString("history",String.join("\n",urls)).apply();
    }
    private ArrayList<String> loadLines(String key){
        ArrayList<String> result=new ArrayList<>();
        for(String line:prefs.getString(key,"").split("\n"))if(!line.isEmpty())result.add(line);
        return result;
    }
    private void saveBookmark(String url){
        ArrayList<String> urls=loadLines("favorites");
        if(!urls.contains(url))urls.add(0,url);
        prefs.edit().putString("favorites",String.join("\n",urls)).apply();
        toast("Adicionado aos favoritos");
    }
    private void showSaved(String label,String key){
        ArrayList<String> urls=loadLines(key);
        if(urls.isEmpty()){toast("Nenhum item no "+label.toLowerCase(Locale.ROOT));return;}
        new AlertDialog.Builder(this).setTitle(label).setItems(urls.toArray(new String[0]),
            (d,which)->open(urls.get(which))).setNeutralButton("Limpar",(d,w)->{
                new AlertDialog.Builder(this).setTitle("Limpar "+label+"?")
                    .setPositiveButton("Limpar",(dlg,c)->prefs.edit().remove(key).apply())
                    .setNegativeButton("Cancelar",null).show();
            }).setNegativeButton("Fechar",null).show();
    }
    private void settingsMenu(){
        String[] options={"Identificação do navegador: "+prefs.getString("ua","android"),
            "Mecanismo de busca: "+prefs.getString("search","Google"),
            "Aparência: "+(dark?"Escura":"Clara"),
            "Transparência Liquid Glass: "+prefs.getInt("glass",72)+"%",
            "Privacidade e limitações", "Limpar histórico", "Página inicial"};
        new AlertDialog.Builder(this).setTitle("Configurações").setItems(options,(d,i)->{
            switch(i){
                case 0:chooseUA();break;case 1:chooseSearch();break;case 2:
                    dark=!dark;prefs.edit().putBoolean("dark",dark).apply();buildLayout();
                    if(active!=null){web.setSession(active.session);refreshBar();homepage.setVisibility(showHome?View.VISIBLE:View.GONE);}
                    break;
                case 3:adjustGlass();break;
                case 4:new AlertDialog.Builder(this).setTitle("Privacidade")
                    .setMessage("Modo iPhone altera o User-Agent e o viewport, não transforma o Android em iOS. VPN exige provedor externo. Extensões devem ser compatíveis e assinadas pela Mozilla. O modo privativo não salva o histórico nesta aplicação, mas não torna você invisível na internet.")
                    .setPositiveButton("OK",null).show();break;
                case 5:prefs.edit().remove("history").apply();toast("Histórico local limpo");break;
                case 6:showHome=true;homepage.setVisibility(View.VISIBLE);address.setText("");break;
            }
        }).show();
    }
    private void chooseUA(){
        String[] modes={"Android (padrão)","iPhone 18 Pro · iOS 27 (identificação)","Computador"};
        String[] values={"android","iphone","desktop"};
        new AlertDialog.Builder(this).setTitle("Como os sites identificam o navegador")
            .setSingleChoiceItems(modes,-1,(dialog,which)->{
                prefs.edit().putString("ua",values[which]).apply();
                for(Tab tab:tabs)applyUA(tab);
                if(active!=null&&!active.url.isEmpty())active.session.reload();
                dialog.dismiss();
                toast("Identificação: "+modes[which]);
            }).setNegativeButton("Cancelar",null).show();
    }
    private void chooseSearch(){
        String[] options={"Google","DuckDuckGo","Bing"};
        new AlertDialog.Builder(this).setTitle("Buscar com").setItems(options,(d,i)->{
            prefs.edit().putString("search",options[i]).apply();toast("Busca: "+options[i]);
        }).show();
    }
    private void adjustGlass(){
        SeekBar seek=new SeekBar(this);seek.setMax(100);seek.setProgress(prefs.getInt("glass",72));
        FrameLayout frame=new FrameLayout(this);frame.setPadding(dp(25),dp(18),dp(25),dp(18));
        frame.addView(seek,new FrameLayout.LayoutParams(-1,dp(60)));
        new AlertDialog.Builder(this).setTitle("Transparência do vidro")
            .setView(frame).setPositiveButton("Aplicar",(d,w)->{
                prefs.edit().putInt("glass",seek.getProgress()).apply();
                bar.setBackground(rounded(glass(),37,true));
            }).setNegativeButton("Cancelar",null).show();
    }
    @Override public void onBackPressed(){
        if(active!=null&&active.canBack)active.session.goBack();
        else if(!showHome){showHome=true;homepage.setVisibility(View.VISIBLE);refreshBar();}
        else super.onBackPressed();
    }
    private static final class IconButton extends View{
        private final String type;
        private final Paint p=new Paint(3);
        private int color=Color.WHITE;
        IconButton(Context ctx,String type){super(ctx);this.type=type;setContentDescription(type);setClickable(true);}
        void setColor(int color){this.color=color;invalidate();}
        @Override protected void onDraw(Canvas c){
            super.onDraw(c);
            c.save();c.translate(getWidth()/2f,getHeight()/2f);
            float s=getResources().getDisplayMetrics().density;
            c.scale(s,s);
            p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2.3f);
            p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
            if(type.equals("back")||type.equals("next")){
                int dir=type.equals("back")?-1:1;
                c.drawLine(-dir*6,-7,dir*1,0,p);
                c.drawLine(dir*1,0,-dir*6,7,p);
            }else if(type.equals("tabs")){
                c.drawRoundRect(-10,-8,6,8,2,2,p);
                c.drawRoundRect(-5,-11,10,5,2,2,p);
            }else if(type.equals("menu")){
                p.setStyle(Paint.Style.FILL);
                for(int i=-1;i<=1;i++)c.drawCircle(i*6,0,1.7f,p);
            }
            c.restore();
        }
    }
}