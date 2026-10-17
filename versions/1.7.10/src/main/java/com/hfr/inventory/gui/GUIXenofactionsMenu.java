package com.hfr.inventory.gui;
import java.util.*; import com.hfr.packet.PacketDispatcher; import com.hfr.packet.client.XFMenuDataRequestPacket; import com.hfr.xfgui.*; import com.hfr.xfgui.XFCommandArgument.Type; import net.minecraft.client.*; import net.minecraft.client.gui.*; import net.minecraft.util.*; import org.lwjgl.input.Mouse; import cpw.mods.fml.relauncher.*;
@SideOnly(Side.CLIENT) public class GUIXenofactionsMenu extends GuiScreen implements GuiYesNoCallback { private static XFMenuData data=new XFMenuData(); private static boolean pending; private static int nextRequest; private static int pendingRequest; private static long pendingSession; private final long session=XFMenuNavigation.session(); private boolean ready; private GuiTextField search; private final Map<String,GuiTextField> fields=new HashMap<String,GuiTextField>(); private XFCommandDefinition.Category cat=XFCommandDefinition.Category.FACTION; private XFCommandDefinition selected; private int scroll; private int argumentPage; private String validation="";
 private XFMenuData filteredData; private XFCommandDefinition.Category filteredCategory;
 private String filteredQuery; private int filteredGeneration=-1;
 private List<XFCommandDefinition> filtered=Collections.emptyList();
 private final Map<XFCommandDefinition,String> searchText=new HashMap<XFCommandDefinition,String>();
 private String cachedCommand;

 public static void updateSession(){
  if(XFMenuNavigation.updateSession(Minecraft.getMinecraft())){data=new XFMenuData();pending=false;pendingRequest=0;}
 }
 public static void requestSessionReset(){XFMenuNavigation.requestReset();}
 public static void requestAndOpen(){
  Minecraft mc=Minecraft.getMinecraft(); updateSession();
  if(mc.theWorld==null||mc.thePlayer==null||mc.currentScreen instanceof GUIXenofactionsMenu)return;
  GUIXenofactionsMenu menu=new GUIXenofactionsMenu();
  mc.displayGuiScreen(menu); menu.requestData();
 }
 private void requestData(){
  rememberLocation(); ready=false; selected=null; fields.clear(); validation="";
  pending=true; pendingSession=session; pendingRequest=++nextRequest;
  data=new XFMenuData(); initGui();
  PacketDispatcher.wrapper.sendToServer(new XFMenuDataRequestPacket(pendingRequest));
 }
 public static void receiveMenuData(XFMenuData d,int request){
  updateSession();
  if(!pending||request!=pendingRequest||pendingSession!=XFMenuNavigation.session())return;
  data=d==null?new XFMenuData():d; pending=false;
  GuiScreen screen=Minecraft.getMinecraft().currentScreen;
  if(screen instanceof GUIXenofactionsMenu){
   GUIXenofactionsMenu menu=(GUIXenofactionsMenu)screen;
   if(menu.session==XFMenuNavigation.session()){menu.ready=true;menu.restoreLocation(XFMenuNavigation.category());}
  }
 }
 private boolean currentSession(){updateSession();return session==XFMenuNavigation.session();}
 private void rememberLocation(){
  if(ready&&currentSession())XFMenuNavigation.remember(cat,new XFMenuNavigation.Location(selected==null?null:selected.id,search==null?"":search.getText(),scroll,argumentPage));
 }
 private void restoreLocation(XFCommandDefinition.Category category){
  XFMenuNavigation.Location location=XFMenuNavigation.location(category);
  cat=category; search.setText(location.search); scroll=location.scroll; argumentPage=location.argumentPage;
  selected=null; validation="";
  for(XFCommandDefinition d:list())if(d.id.equals(location.commandId)&&canRestore(d)){selected=d;break;}
  if(selected==null)argumentPage=0;
  clampScroll(); initGui(); rememberLocation();
 }
 private boolean canRestore(XFCommandDefinition d){
  if(d.factionRank==null)return true;
  int rank;try{rank=Integer.parseInt(data.currentRank);}catch(NumberFormatException e){return false;}
  int required="Leader".equals(d.factionRank)||("ClaimRename".equals(d.factionRank)&&!data.claimRenameOfficersAllowed)?3:2;
  return rank>=required;
 }
 public void onGuiClosed(){rememberLocation();super.onGuiClosed();}
 public boolean doesGuiPauseGame(){return false;} public void initGui(){cachedCommand=null;clampScroll();buttonList.clear(); fields.clear(); if(search==null) search=new GuiTextField(fontRendererObj,8,26,140,18); else search.xPosition=8; search.yPosition=26; search.width=140; int x=8,y=50,id=100; for(XFCommandDefinition.Category c:XFCommandDefinition.Category.values()){buttonList.add(new GuiButton(id++,x,y,70,18,loc("xf.category."+c.name().toLowerCase()))); if(id%2==0)x=82; else{x=8;y+=20;}} int by=height-24; buttonList.add(new GuiButton(1,width-258,by,58,20,loc("xf.menu.run"))); buttonList.add(new GuiButton(2,width-198,by,78,20,loc("xf.menu.chat"))); buttonList.add(new GuiButton(3,width-118,by,58,20,loc("xf.menu.copy"))); buttonList.add(new GuiButton(4,8,height-24,62,20,loc("xf.menu.refresh"))); buttonList.add(new GuiButton(5,74,height-24,62,20,loc("xf.menu.tutorial"))); buttonList.add(new GuiButton(6,140,height-24,80,20,loc("xf.menu.reset_tutorial"))); layoutFields(); buttonList.add(new GuiButton(10,Math.max(160,width/2),height-50,20,18,"<")); buttonList.add(new GuiButton(11,Math.max(160,width/2)+24,height-50,20,18,">")); for(Object o:buttonList){GuiButton b=(GuiButton)o; if(b.id==10||b.id==11)b.visible=selected!=null&&selected.arguments.length>argumentRows(); if(!ready)b.enabled=false;} }
 private void layoutFields(){ if(selected==null)return; int x=Math.max(160,width/2), y=116; for(int i=0;i<selected.arguments.length;i++){GuiTextField f=new GuiTextField(fontRendererObj,x,y,Math.min(160,width-x-34),18); f.setMaxStringLength(selected.arguments[i].preserveSpaces?256:64); if(selected.arguments[i].options.length==1)f.setText(selected.arguments[i].options[0]); fields.put("a"+i,f); List<String> choices=choices(selected.arguments[i]); if(!choices.isEmpty())buttonList.add(new GuiButton(2000+i,x+f.width+2,y,22,18,">")); y+=34;} positionFields();}
 protected void actionPerformed(GuiButton b){cachedCommand=null; if(!ready||!currentSession())return; if(b.id>=2000){int i=b.id-2000; List<String> choices=choices(selected.arguments[i]); if(!choices.isEmpty()){GuiTextField field=fields.get("a"+i); int next=(choices.indexOf(field.getText())+1)%choices.size();field.setText(choices.get(next));}return;} if(b.id==10||b.id==11){argumentPage=Math.max(0,Math.min((selected.arguments.length-1)/argumentRows(),argumentPage+(b.id==10?-1:1)));positionFields();return;} if(b.id>=100){rememberLocation();restoreLocation(XFCommandDefinition.Category.values()[b.id-100]);return;} if(b.id==4){requestData();return;} if(b.id==5){mc.displayGuiScreen(new GUIXenofactionsTutorial());return;} if(b.id==6){mc.displayGuiScreen(new GuiYesNo(this,loc("xf.confirm.reset_tutorial"),GUIXenofactionsTutorial.getFlagFile().getAbsolutePath(),6));return;} if(selected==null)return; String cmd=command(); if(b.id==3){setClipboardString(usage(selected));return;} if(!validate())return; if(b.id==2){mc.displayGuiScreen(new GuiChat(cmd));return;} if(b.id==1){ if(selected.dangerous) mc.displayGuiScreen(new GuiYesNo(this,loc("xf.confirm.title"),cmd,9)); else send(cmd);} }
 public void confirmClicked(boolean ok,int id){ if(ok&&id==6)GUIXenofactionsTutorial.deleteFlag(); if(ok&&id==9&&ready&&currentSession())send(command()); else mc.displayGuiScreen(this);} private void send(String cmd){if(!ready||!currentSession())return;rememberLocation();if(mc.thePlayer!=null)mc.thePlayer.sendChatMessage(cmd); mc.displayGuiScreen(null);} private boolean validate(){validation=""; if(command().length()>100){validation=loc("xf.validation.length");return false;} for(int i=0;i<selected.arguments.length;i++){XFCommandArgument a=selected.arguments[i]; String v=val(i); if(a.required&&v.trim().length()==0){argumentPage=i/argumentRows();positionFields();validation=loc("xf.validation.required");return false;} for(int j=0;j<v.length();j++){char c=v.charAt(j); if(c=='\n'||c=='\r'||Character.isISOControl(c)){validation=loc("xf.validation.control");return false;}} if(!a.preserveSpaces&&v.trim().contains(" ")&&!(a.type==Type.FACTION_TOKEN||a.type==Type.FACTION||a.type==Type.ALLY||a.type==Type.ENEMY||a.type==Type.CITY)){validation=loc("xf.validation.token");return false;} if(a.options.length>0&&v.trim().length()>0&&!Arrays.asList(a.options).contains(v.trim())){validation=loc("xf.validation.choice");return false;} if(a.type==Type.INTEGER&&v.trim().length()>0)try{Integer.parseInt(v.trim());}catch(Exception e){validation=loc("xf.validation.integer");return false;} if((a.type==Type.DECIMAL)&&v.trim().length()>0)try{Double.parseDouble(v.trim());}catch(Exception e){validation=loc("xf.validation.decimal");return false;} } for(int i=0;i<selected.arguments.length;i++){if(val(i).trim().isEmpty())for(int j=i+1;j<selected.arguments.length;j++)if(!val(j).trim().isEmpty()){validation=loc("xf.validation.position");return false;}} return true;}
 private String command(){if(cachedCommand!=null)return cachedCommand;List<String> vals=new ArrayList<String>(); if(selected!=null)for(int i=0;i<selected.arguments.length;i++){XFCommandArgument a=selected.arguments[i];vals.add(val(i));} return cachedCommand=selected==null?"":selected.buildCommand(vals);} private String val(int i){GuiTextField f=fields.get("a"+i);return f==null?"":f.getText();}
 public void updateScreen(){if(!currentSession()){mc.displayGuiScreen(null);return;}search.updateCursorCounter(); for(GuiTextField f:fields.values())f.updateCursorCounter();}
 protected void mouseClicked(int mx,int my,int btn){if(!ready||!currentSession())return;try{super.mouseClicked(mx,my,btn);}catch(Exception e){} search.mouseClicked(mx,my,btn); for(int i=0;selected!=null&&i<selected.arguments.length;i++)if(argumentVisible(i))fields.get("a"+i).mouseClicked(mx,my,btn); int x=154,y=50; List<XFCommandDefinition> list=list(); for(int i=scroll;i<list.size()&&y<height-32;i++,y+=18) if(mx>=x&&mx<Math.min(width/2-4,x+150)&&my>=y&&my<y+17){selected=list.get(i);argumentPage=0;initGui();break;}}
 protected void keyTyped(char c,int k){cachedCommand=null; if(k==1){mc.displayGuiScreen(null);return;} if(!ready||!currentSession())return; if(search.isFocused()){search.textboxKeyTyped(c,k);scroll=0;if(selected!=null&&!list().contains(selected)){selected=null;argumentPage=0;validation="";initGui();}} for(GuiTextField f:fields.values())if(f.isFocused())f.textboxKeyTyped(c,k); } public void handleMouseInput(){super.handleMouseInput(); int d=Mouse.getEventDWheel(); if(d!=0&&ready){scroll+=d<0?1:-1;clampScroll();}}
 public void drawScreen(int mx,int my,float f){drawDefaultBackground(); drawCenteredString(fontRendererObj,loc("xf.menu.title"),width/2,8,0xffffff); drawString(fontRendererObj,pending?loc("xf.menu.loading"):loc("xf.menu.ready"),8,8,0xa0a0a0); search.drawTextBox(); drawString(fontRendererObj,loc("xf.menu.search"),8,16,0xa0a0a0); int x=154,y=50; List<XFCommandDefinition> list=list(); for(int i=scroll;i<list.size()&&y<height-32;i++,y+=18){XFCommandDefinition d=list.get(i); drawRect(x,y,Math.min(width/2-4,x+150),y+17,d==selected?0x805050a0:0x80000000); drawString(fontRendererObj,fontRendererObj.trimStringToWidth(loc(d.displayKey),Math.max(1,Math.min(width/2-4,x+150)-x-6)),x+3,y+5,d.dangerous?0xff8080:0xffffff);} if(selected!=null){int dx=Math.max(160,width/2); drawString(fontRendererObj,loc(selected.displayKey),dx,28,0xffff80); drawString(fontRendererObj,loc(selected.descriptionKey),dx,42,0xe0e0e0); drawString(fontRendererObj,loc("xf.menu.usage")+": "+usage(selected),dx,56,0xa0ffa0); drawString(fontRendererObj,loc("xf.menu.aliases")+": "+Arrays.toString(selected.aliases),dx,68,0xa0a0a0); drawString(fontRendererObj,loc("xf.menu.requirements")+": "+(selected.opLevel>0?StatCollector.translateToLocalFormatted("xf.permission.level",selected.opLevel):selected.factionRank==null?loc("xf.permission.none"):loc("xf.permission."+("ClaimRename".equals(selected.factionRank)?(data.claimRenameOfficersAllowed?"officer":"leader"):selected.factionRank.toLowerCase(Locale.ROOT)))),dx,80,0xa0a0a0); drawString(fontRendererObj,loc("xf.menu.preview")+": "+command(),dx,92,0xffffff); int yy=116; for(int i=argumentPage*argumentRows();i<selected.arguments.length&&i<(argumentPage+1)*argumentRows();i++){drawString(fontRendererObj,loc(selected.arguments[i].nameKey),dx,yy-10,0xa0a0a0); GuiTextField gf=fields.get("a"+i); if(gf!=null)gf.drawTextBox(); yy+=34;} if(selected.arguments.length>argumentRows())drawString(fontRendererObj,StatCollector.translateToLocalFormatted("xf.menu.arguments_page",argumentPage+1,(selected.arguments.length+argumentRows()-1)/argumentRows()),dx+50,height-45,0xa0a0a0); if(validation.length()>0)drawString(fontRendererObj,validation,dx,height-36,0xff8080);} super.drawScreen(mx,my,f);} private List<XFCommandDefinition> list(){if(!ready)return Collections.emptyList();String query=search==null?"":search.getText();
 int generation=com.hfr.client.render.GpuModelManager.generation();
 if(filteredData==data&&filteredCategory==cat&&generation==filteredGeneration&&query.equals(filteredQuery))return filtered;
 if(filteredGeneration!=generation)searchText.clear();
 filteredData=data;filteredCategory=cat;filteredGeneration=generation;filteredQuery=query;
 String q=query.toLowerCase(Locale.ROOT);List<XFCommandDefinition> r=new ArrayList<XFCommandDefinition>(); for(XFCommandDefinition d:XFCommandCatalog.getCommands()){ if(q.length()==0&&d.category!=cat)continue; if(d.opLevel>0&&!data.permittedCommands.contains(d.id))continue; if("tdm".equals(d.configRequirement)&&!data.tdmEnabled)continue; if("customflags".equals(d.configRequirement)&&!data.customFlagsEnabled)continue; if("xflags".equals(d.configRequirement)&&!data.xflagsEnabled)continue; String hay=searchText.get(d); if(hay==null){hay=(loc(d.displayKey)+" "+loc(d.descriptionKey)+" "+usage(d)+" "+Arrays.toString(d.aliases)+" /"+d.root+(d.sub==null?"":" "+d.sub)).toLowerCase(Locale.ROOT);searchText.put(d,hay);} if(q.length()==0||hay.contains(q))r.add(d);} filtered=Collections.unmodifiableList(r);return filtered;} private static String usage(XFCommandDefinition d){return StatCollector.canTranslate(d.usage)?loc(d.usage):d.usage;} private static String loc(String k){String s=StatCollector.translateToLocal(k);return s==null?k:s;}
 private void clampScroll(){int rows=Math.max(1,(height-83)/18+1);scroll=Math.max(0,Math.min(Math.max(0,list().size()-rows),scroll));}
 private int argumentRows(){return Math.max(1,(height-154)/34);}
 private boolean argumentVisible(int i){return i>=argumentPage*argumentRows()&&i<(argumentPage+1)*argumentRows();}
 private void positionFields(){
  if(selected==null)return;
  argumentPage=Math.min(argumentPage,Math.max(0,(selected.arguments.length-1)/argumentRows()));
  for(int i=0;i<selected.arguments.length;i++){
   GuiTextField field=fields.get("a"+i); field.yPosition=116+(i-argumentPage*argumentRows())*34;
   if(!argumentVisible(i))field.setFocused(false);
  }
  for(Object o:buttonList){GuiButton b=(GuiButton)o;if(b.id>=2000){int i=b.id-2000;b.visible=argumentVisible(i);b.yPosition=fields.get("a"+i).yPosition;}}
 }
 private List<String> choices(XFCommandArgument a){
  List<String> result=new ArrayList<String>(); if(!a.required)result.add("");
  if(a.options.length>0){for(String option:a.options)if(!result.contains(option))result.add(option);return result;}
  Collection<String> source=Collections.emptyList();
  switch(a.type){
   case ONLINE_PLAYER: source=data.onlinePlayers;break;
   case FACTION: source=data.factions;break;
   case FACTION_MEMBER: source=data.members;break;
   case APPLICANT: source=data.applicants;break;
   case ALLY: source=data.allies;break;
   case ENEMY: case FACTION_TOKEN: source=data.enemies;break;
   case WARP: source=data.warps;break;
   case CITY: source=data.cities;break;
   case FLAG: source=data.flags;break;
   default: return Collections.emptyList();
  }
  for(String value:source)if(!result.contains(value))result.add(value);return result;
 }
}
