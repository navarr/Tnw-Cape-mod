package com.mineznightswatch.cape;

import com.mineznightswatch.cape.Util.LogHelper;
import com.mineznightswatch.cape.Util.references;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
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
import sun.rmi.runtime.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@Mod(modid = references.MOD_ID, name = references.MOD_NAME, version = references.MOD_VERSION)
public class Tnw_Cape_Mod {
    int tick = 0;
    int sweep = 20;

    private ArrayList<String> capeDIRs = null;

    private HashMap<String, ThreadDownloadImageData> checked = new HashMap<String, ThreadDownloadImageData>();
    private ArrayList<String> ignored = new ArrayList<String>();
    private ArrayList<String> players = new ArrayList<String>();

    private String capesDir = references.CAPES_DIR;

    boolean checking = false;
    boolean shouldClear = false;

    @EventHandler
    public void init(FMLInitializationEvent event) {
        findCapesDirectories();

        FMLCommonHandler.instance().bus().register(this);
    }

    @SubscribeEvent
    public void tick(ClientTickEvent event) {
        if (event.phase == Phase.END) {
            updateCloakURLs();
        }
    }


    private void clearCloaks(List<EntityPlayer> playerEntities, Minecraft mc) {
        LogHelper.info("[TWN] Clearing capes...");

        checked.clear();
        ignored.clear();
    }

    private void findCapesDirectories() {
        new Thread() {
            public void run() {
                LogHelper.info("[TNW] Searching for capes directories ...");

                ArrayList<String> _capeDIRs = new ArrayList<String>();
                try {
                    URL dirList = new URL("http://minez-nightswatch.com/capesDirectory.list");
                    BufferedReader in = new BufferedReader(new InputStreamReader(dirList.openStream()));

                    String inputLine;
                    while ((inputLine = in.readLine()) != null) _capeDIRs.add(inputLine);
                    in.close();

                } catch (Exception e) {
                    LogHelper.info("[TNW] External cape directories could not be found. Try again on restart...");
                }

                _capeDIRs.add(0, capesDir);

                LogHelper.info("[TNW] " + _capeDIRs.size() + " directories loaded!");
                capeDIRs = _capeDIRs;
            }

        }.start();

    }

    private void updateCloakURLs() {
        Minecraft mc = Minecraft.getMinecraft();

        if (capeDIRs == null || capeDIRs.isEmpty()) return;
        if (checking) return;

        if (tick >= sweep) {
            tick = 0;

            if (mc == null || mc.theWorld == null || mc.theWorld.playerEntities == null || mc.renderEngine == null) {
                return;
            }

            final List<EntityPlayer> playerEntities = mc.theWorld.playerEntities; //get the players

            // clear cloaks if requested
            if (shouldClear) {
                shouldClear = false;
                clearCloaks(playerEntities, mc);
                tick = sweep;
                return;
            }

            // apply found cloaks / find players
            players.clear();
            for (EntityPlayer entityplayer : playerEntities) {
                String playerName = entityplayer.getCommandSenderName();
                players.add(playerName);

                ThreadDownloadImageData usersCape = checked.get(playerName);

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

                        LogHelper.info("[TNW] Applying (new) cape for: " + playerName);

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
            checking = true;
            Thread checkThread = new Thread() {
                public void run() {
                    checkCloakURLs(players);
                    checking = false;
                }
            };
            checkThread.setPriority(Thread.MIN_PRIORITY);
            checkThread.start();

        } else {
            tick++;
        }

    }

    private String removeColorFromString(String string) {
        string = string.replaceAll("\\xa4\\w", "");
        string = string.replaceAll("\\xa7\\w", "");

        return string;
    }

    protected void checkCloakURLs(List<String> playerNames) {
        for (String playerName : playerNames) {

            if (ignored.contains(playerName) || checked.containsKey(playerName)) continue;

            LogHelper.info("[TNW] Found new player: " + playerName);

            String found = null;
            for (String capeURLcheck : capeDIRs) {

                String url = capeURLcheck + removeColorFromString(playerName) + ".png";
                try {
                    HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
                    con.setRequestMethod("HEAD");
                    con.setRequestProperty("User-agent", "MineCapes " + references.MOD_VERSION);
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
                ignored.add(playerName);
                LogHelper.warn("[TNW] Could not find any cloak, ignoring ...");

            } else {
                AbstractClientPlayer aPlayer = (AbstractClientPlayer) Minecraft.getMinecraft().theWorld.getPlayerEntityByName(playerName);

                // get cloak
                ResourceLocation resourcePackCloak = AbstractClientPlayer.getLocationCape(aPlayer.getCommandSenderName());

                TextureManager texturemanager = Minecraft.getMinecraft().getTextureManager();
                ThreadDownloadImageData object = new ThreadDownloadImageData(found, null, null);
                texturemanager.loadTexture(resourcePackCloak, (ITextureObject) object);

                checked.put(playerName, object);
                LogHelper.info("[TNW] Found cloak: " + found);
            }
        }
    }
}
