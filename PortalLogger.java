package who.pvpb0t.b0tware.impl.modules.exploit;

import com.mojang.realmsclient.gui.ChatFormatting;
import com.typesafe.config.ConfigException;
import net.minecraft.block.Block;
import net.minecraft.block.BlockAir;
import net.minecraft.block.BlockPortal;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.server.SPacketBlockChange;
import net.minecraft.network.play.server.SPacketChunkData;
import net.minecraft.network.play.server.SPacketEntityEffect;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.Sys;
import scala.Int;
import who.pvpb0t.b0tware.B0TWARE;
import who.pvpb0t.b0tware.api.Discord;
import who.pvpb0t.b0tware.api.event.events.PacketEvent;
import who.pvpb0t.b0tware.api.manager.FileManager;
import who.pvpb0t.b0tware.api.manager.ModuleManager;
import who.pvpb0t.b0tware.api.util.Util;
import who.pvpb0t.b0tware.impl.command.Command;
import who.pvpb0t.b0tware.impl.modules.Module;
import who.pvpb0t.b0tware.impl.modules.client.HUD;
import who.pvpb0t.b0tware.impl.modules.visual.Fullbright;
import who.pvpb0t.b0tware.impl.modules.visual.HoleESP;
import who.pvpb0t.b0tware.impl.setting.Setting;

import java.io.BufferedWriter;
import java.io.Console;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class PortalLogger extends Module {
  
  /*
  by pvpb0t
  3/6/2022
  */

    private Setting<Boolean> writeToFile = register(new Setting<Boolean>("WriteToFile", true));
    private Setting<Boolean> console = register(new Setting<Boolean>("WriteToConsole", false));
    private final Setting<Integer> distance = this.register(new Setting<Integer>("Distance", 100, 10, 300));
    private final Setting<Integer> height = this.register(new Setting<Integer>("Height", 128, 0, 255));
    private final Setting<Integer> depth = this.register(new Setting<Integer>("Test Depth", 2, 1, 5));
    private final Setting<Integer> refreshAm = this.register(new Setting<Integer>("TicksRefresh", 10, 1, 100));
    private final AtomicBoolean canInterup = new AtomicBoolean(false);
    List<BlockPos> tmp = new ArrayList<>();
    public ArrayList<BlockPos> portalz = new ArrayList<>();
    public List<BlockPos> result;

    public static PortalLogger INSTANCE = new PortalLogger();
    public boolean isRunning = false;
    private Thread thread;

    public int rangeInt = -1;

    public PortalLogger(){
        super("PortalLogger", "", Category.EXPLOIT, true, false ,false);
        this.setInstance();

    }

    public static PortalLogger getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new PortalLogger();
        }
        return INSTANCE;
    }

    private void setInstance() {
        INSTANCE = this;
    }

    FileManager fileManager = new FileManager();

    private String WriterName = null;
    private String WriterNamePath = null;

    @Override
    public void onEnable(){
        Util.mc.renderGlobal.loadRenderers();

        if (writeToFile.getValue())
        {
            //Part of the formatting is taken from Salhack
            String server = mc.getCurrentServerData() != null ? mc.getCurrentServerData().serverIP : "singleplayer";

            server = server.replaceAll("\\.", "");

            if (server.contains(":"))
                server = server.substring(0, server.indexOf(":"));

            String name = mc.player.getName();

            String date = new SimpleDateFormat("yyyyMMddHHmmss'.txt'").format(new Date());

            String file = server + "_" + name + "_" + date;

            try
            {
                WriterNamePath = String.valueOf(fileManager.base) + "/PortalLogger";
                WriterName = "ARTEMIS/PortalLogger/" + file;

            } catch (NullPointerException  e)
            {
                e.printStackTrace();
            }

            LoggerNotifiy("File was created: " + file + " at " + WriterNamePath);
        }
        start();
    }

    @Override
    public void onDisable(){
        tmp.clear();
        portalz.clear();
        Util.mc.renderGlobal.loadRenderers();
        if (this.thread != null) {
            this.canInterup.set(true);
            this.thread.interrupt();
        }
    }

    //@Override
    public void start() {
        thread = new Thread(() -> {
            while (this.isEnabled() && Util.mc.world != null) {
                if (this.canInterup.get()) {
                    this.canInterup.set(false);
                }
                try {
                    thread.sleep(refreshAm.getValue());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                long time = System.currentTimeMillis();
                int r = rangeInt != -1 ? rangeInt : distance.getValue().intValue();
                tmp = new ArrayList<>();
                for (int x = -r; x <= r; x++)
                    for (int y = 1; y <= height.getValue().intValue(); y++)
                        for (int z = -r; z <= r; z++) {
                            BlockPos pos = new BlockPos(mc.player.posX + x, y, mc.player.posZ + z);
                            if (this.isTarget(pos) && this.FacingBlocks(pos, depth.getValue().intValue())) {
                                tmp.add(pos);
                                if (Util.mc.world.getBlockState(pos).getBlock() == Blocks.PORTAL && !portalz.contains(pos)) {
                                    portalz.add(pos);
                                    this.LoggerNotifiy(String.format("Found PORTAL! X:%d Y:%d Z:%d Dimension:%s", pos.getX(), pos.getY(), pos.getZ(), getDimType()));

                                }
                            }
                        }
                result = tmp;
                if (console.getValue().booleanValue()) {
                    System.out.println("Using time:" + (System.currentTimeMillis() - time));
                }
            }
            isRunning = false;
        }, "PORTALLOGGER-Callback-Handler");
        thread.start();
    }

    public boolean isTarget(BlockPos pos) {
        Block block = Util.mc.world.getBlockState(pos).getBlock();
        if (Blocks.PORTAL.equals(block)) {
            return true;
        }
        return false;
    }



    public Boolean FacingBlocks(BlockPos origPos, double depth) {
        Collection<BlockPos> posesNew = new ArrayList<>();
        Collection<BlockPos> posesLast = new ArrayList<>(Collections.singletonList(origPos));
        Collection<BlockPos> finalList = new ArrayList<>();
        for (int i = 0; i < depth; i++) {
            for (BlockPos blockPos : posesLast) {
                posesNew.add(blockPos.up());
                posesNew.add(blockPos.down());
                posesNew.add(blockPos.north());
                posesNew.add(blockPos.south());
                posesNew.add(blockPos.west());
                posesNew.add(blockPos.east());
            }
            for (BlockPos pos : posesNew) {
                if (posesLast.contains(pos)) {
                    posesNew.remove(pos);
                }
            }
            posesLast = posesNew;
            finalList.addAll(posesNew);
            posesNew = new ArrayList<>();
        }

        List<Block> legitBlocks = Arrays.asList(Blocks.WATER, Blocks.LAVA, Blocks.FLOWING_LAVA, Blocks.AIR, Blocks.FLOWING_WATER);

        return finalList.stream().anyMatch(blockPos -> legitBlocks.contains(Util.mc.world.getBlockState(blockPos).getBlock()));
    }



    public void LoggerNotifiy(String content)  {
        if(writeToFile.getValue().booleanValue()) {

            Path path = Paths.get("ARTEMIS/PortalLogger/");
            try {
                Files.createDirectories(path);
            } catch (IOException e) {
                e.printStackTrace();
            }

            try {
                FileWriter writer = new FileWriter(WriterName, true);
                writer.write(content + "\n");
                writer.close();
            } catch (IOException e) {
                e.printStackTrace();
            }


        }


        Command.sendMessage(content);
    }





    public String getDimType(){
        switch(mc.player.dimension){
            case -1:
                return "Nether";
            case 0:
                return "Overworld";
            case 1:
                return "End";
            default:
                return "Menu";
        }
    }



}
