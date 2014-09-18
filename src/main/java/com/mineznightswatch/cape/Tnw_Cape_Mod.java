package com.mineznightswatch.cape;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent.ClientTickEvent;
import cpw.mods.fml.common.gameevent.TickEvent.Phase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.ThreadDownloadImageData;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.entity.player.PlayerEvent;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@Mod(modid = Tnw_Cape_Mod.MODID, version = Tnw_Cape_Mod.VERSION)
public class Tnw_Cape_Mod
{
    public static final String MODID = "TNW_cape_mod";
    public static final String VERSION = "1.7.10-1.0.0";

    int CapeTick = 0;
    int CapeSweep = 20;

    int MemberTick = 0;
    int MemberSweep = 20;

    public ArrayList<String> capeDIRs = null;
    public ArrayList<String> membersDIRs = null;

    private HashMap<String, ThreadDownloadImageData> CapeChecked = new HashMap<String, ThreadDownloadImageData>();
    private HashMap<String, ThreadDownloadImageData> MembersChecked = new HashMap<String, ThreadDownloadImageData>();
    private ArrayList<String> CapeIgnored = new ArrayList<String>();
    private ArrayList<String> MembersIgnored = new ArrayList<String>();
    private ArrayList<String> CapePlayers = new ArrayList<String>();
    private ArrayList<String> MemberPlayers = new ArrayList<String>();

    Minecraft mc = Minecraft.getMinecraft();

    private String capesDir = "http://minez-nightswatch.com/mod/cape?user=";
    private String membersDir = "http://minez-nightswatch.com/users/";

    boolean CapeChecking = false;
    boolean MembersChecking = false;
    boolean CapeShouldClear = false;
    boolean MemberShouldClear = false;

    String Membersfound = null;

    @Mod.Instance
    public static Tnw_Cape_Mod instance;

    @EventHandler
    public void init(FMLInitializationEvent event)
    {
        findCapesDirectories();
        findMembersDirectories();

        FMLCommonHandler.instance().bus().register(this);
    }

    @SubscribeEvent
    public void tick(ClientTickEvent event)
    {
        if (event.phase == Phase.END)
        {
            updateCloakURLs();
            updateMembersURLs();
        }
    }

    @SubscribeEvent
    public void NameFormat(PlayerEvent.NameFormat event)
    {
        if (event.username.equals(Membersfound))
        {
            event.displayname = "[TNW]" + event.username;
        }
    }

    private void clearCloaks(List<EntityPlayer> playerEntities, Minecraft mc)
    {
        System.out.println("[TWN] Clearing capes...");

        CapeChecked.clear();
        CapeIgnored.clear();
    }
    private void findMembersDirectories()
    {
        new Thread(){
            public void run() {
                ArrayList<String> _MembersDIRs = new ArrayList<String>();
                try{
                    URL dirList = new URL("http://minez-nightswatch.com/users/");
                    BufferedReader in = new BufferedReader(new InputStreamReader(dirList.openStream()));

                    String inputLine;
                    while ((inputLine = in.readLine()) !=null) _MembersDIRs.add(inputLine);
                    in.close();
                } catch (Exception e) {
                    System.out.println("[TNW] could not detect members directory. try again or restart...");
                }

                _MembersDIRs.add(0, membersDir);
                System.out.println("[TNW]" + _MembersDIRs.size() + " member directories loaded!");
                membersDIRs = _MembersDIRs;
            }
        }.start();
    }


    private void findCapesDirectories() {
        new Thread() {
            public void run() {
                System.out.println("[TNW] Searching for capes directories ...");

                ArrayList<String> _capeDIRs = new ArrayList<String>();
                try {
                    URL dirList = new URL("http://minez-nightswatch.com/capesDirectory.list");
                    BufferedReader in = new BufferedReader(new InputStreamReader(dirList.openStream()));

                    String inputLine;
                    while ((inputLine = in.readLine()) != null) _capeDIRs.add(inputLine);
                    in.close();

                } catch (Exception e) {
                    System.out.println("[TNW] External cape directories could not be found. Try again on restart...");
                }

                _capeDIRs.add(0, capesDir);

                System.out.println("[TNW] " + _capeDIRs.size() + " cape directories loaded!");
                capeDIRs = _capeDIRs;
            }

        }.start();

    }

    private void updateCloakURLs()
    {
        if (capeDIRs == null || capeDIRs.isEmpty()) return;
        if (CapeChecking) return;

        if (CapeTick >= CapeSweep) {
            CapeTick = 0;

            if (mc == null || mc.theWorld == null || mc.theWorld.playerEntities == null || mc.renderEngine == null)
            {
                return;
            }

            final List<EntityPlayer> playerEntities = mc.theWorld.playerEntities; //get the players

            // clear cloaks if requested
            if (CapeShouldClear) {
                CapeShouldClear = false;
                clearCloaks(playerEntities, mc);
                CapeTick = CapeSweep;
                return;
            }

            // apply found cloaks / find players
            CapePlayers.clear();
            for (EntityPlayer entityplayer : playerEntities) {
                String playerName = entityplayer.getCommandSenderName();
                CapePlayers.add(playerName);

                ThreadDownloadImageData usersCape = CapeChecked.get(playerName);

                if (usersCape != null) {

                    AbstractClientPlayer aPlayer = (AbstractClientPlayer) entityplayer;
                    ThreadDownloadImageData currentCape = null;
                    Field downloadImageCape = null;

                    // make cloak resource field accessible and get current cape
                    try {
                        downloadImageCape = AbstractClientPlayer.class.getDeclaredField("field_110315_c");

                        if (!downloadImageCape.isAccessible()) downloadImageCape.setAccessible(true);

                        currentCape = (ThreadDownloadImageData) downloadImageCape.get(aPlayer);

                    } catch (Exception e) {
                        e.printStackTrace();
                    }


                    // check if needs update
                    if (downloadImageCape != null && usersCape != currentCape) {

                        System.out.println("[TNW] Applying (new) cape for: " + playerName);

                        // set as users cloak resource
                        try {
                            downloadImageCape.set(aPlayer, usersCape);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                    }

                }
            }

            // run cloak find in another thread
            CapeChecking = true;
            Thread checkThread = new Thread() {
                public void run() {
                    checkCloakURLs(CapePlayers);
                    CapeChecking = false;
                    checkMembersURLs(MemberPlayers);
                }
            };
            checkThread.setPriority(Thread.MIN_PRIORITY);
            checkThread.start();

        } else {
            CapeTick++;
        }

    }

    private void updateMembersURLs()
    {
        if (membersDIRs == null || membersDIRs.isEmpty()) return;
        if (MembersChecking) return;

        if (MemberTick >= MemberSweep)
        {
            MemberTick = 0;

            if (mc == null || mc.theWorld == null || mc.theWorld.playerEntities == null || mc.renderEngine == null)
            {
                return;
            }

            final List<EntityPlayer> playerEntities = mc.theWorld.playerEntities; //get the players

            // clear cloaks if requested
            if (MemberShouldClear)
            {
                MemberShouldClear = false;
                clearTags(playerEntities, mc);
                MemberTick = MemberSweep;
                return;
            }

            // apply found cloaks / find players
            MemberPlayers.clear();
            for (EntityPlayer entityplayer : playerEntities)
            {
                String playerName = entityplayer.getCommandSenderName();
                MemberPlayers.add(playerName);

                // run cloak find in another thread
                CapeChecking = true;
                Thread checkThread = new Thread()
                {
                    public void run()
                    {
                        Tnw_Cape_Mod TNW = new Tnw_Cape_Mod();
                        TNW.checkMembersURLs(MemberPlayers);
                        MembersChecking = false;
                    }
                };
                checkThread.setPriority(Thread.MIN_PRIORITY);
                checkThread.start();

            }
        }else MemberTick++;
    }

    private void clearTags(List<EntityPlayer> playerEntities, Minecraft mc)
    {
        System.out.println("[TWN] Clearing Pre-fixes ...");

        MembersChecked.clear();
        MembersIgnored.clear();
    }

    public String removeColorFromString(String string) {
        string = string.replaceAll("\\xa4\\w", "");
        string = string.replaceAll("\\xa7\\w", "");

        return string;
    }


    protected void checkCloakURLs(List<String> playerNames) {
        for (String playerName : playerNames) {

            if (CapeIgnored.contains(playerName) || CapeChecked.containsKey(playerName)) continue;

            System.out.println("[TNW] Found new player: " + playerName);

            String found = null;
            for (String capeURLcheck : capeDIRs) {

                String url = capeURLcheck + removeColorFromString(playerName) + ".png";
                try {
                    HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
                    con.setRequestMethod("HEAD");
                    con.setRequestProperty("User-agent", "MineCapes " + VERSION);
                    con.setRequestProperty("Java-Version", System.getProperty("java.version"));
                    con.setConnectTimeout(2000);
                    con.setUseCaches(false);

                    if (con.getResponseCode() == HttpURLConnection.HTTP_OK) found = url;

                    con.disconnect();

                } catch (Exception e) {
                    // Expected Failure if no cape
                }


                if (found != null) break;
            }

            if (found == null) {
                CapeIgnored.add(playerName);
                System.out.println("[TNW] Could not find any cloak, ignoring ...");

            } else {
                AbstractClientPlayer aPlayer = (AbstractClientPlayer) Minecraft.getMinecraft().theWorld.getPlayerEntityByName(playerName);

                // get cloak
                ResourceLocation resourcePackCloak = aPlayer.getLocationCape();

                TextureManager texturemanager = Minecraft.getMinecraft().getTextureManager();
                ThreadDownloadImageData object = new ThreadDownloadImageData(null, found, null, null);
                texturemanager.loadTexture(resourcePackCloak, (ITextureObject) object);

                CapeChecked.put(playerName, object);
                System.out.println("[TNW] Found cloak: " + found);
            }
        }
    }
    protected void checkMembersURLs(List<String> PlayerNames) {
        for (String playerName : PlayerNames) {

            if (MembersIgnored.contains(playerName) || MembersChecked.containsKey(playerName)) continue;
            for (String membersURLcheck : membersDIRs) {

                String url = membersURLcheck + removeColorFromString(playerName);
                try {
                    HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
                    con.setRequestMethod("HEAD");
                    con.setRequestProperty("User-agent", "MineCapes " + VERSION);
                    con.setRequestProperty("Java-Version", System.getProperty("java.version"));
                    con.setConnectTimeout(2000);
                    con.setUseCaches(false);

                    if (con.getResponseCode() == HttpURLConnection.HTTP_OK) Membersfound = playerName;

                    con.disconnect();

                } catch (Exception e) {
                    // Expected Failure if not a member
                    System.out.println("playerName: " + playerName);
                }


                if (Membersfound != null) break;

            }
        }
    }
}
