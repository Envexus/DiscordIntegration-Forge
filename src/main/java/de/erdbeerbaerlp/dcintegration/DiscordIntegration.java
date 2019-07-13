package de.erdbeerbaerlp.dcintegration;


import com.feed_the_beast.ftbutilities.FTBUtilitiesConfig;
import de.erdbeerbaerlp.dcintegration.commands.*;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.requests.RequestFuture;
import net.dv8tion.jda.webhook.WebhookClient;
import net.dv8tion.jda.webhook.WebhookMessageBuilder;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.common.ForgeVersion;
import net.minecraftforge.common.ForgeVersion.CheckResult;
import net.minecraftforge.common.ForgeVersion.Status;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.AdvancementEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.*;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Date;
import java.util.concurrent.ExecutionException;

@Mod(modid = DiscordIntegration.MODID, version = DiscordIntegration.VERSION, name = DiscordIntegration.NAME, serverSideOnly = true, acceptableRemoteVersions = "*", updateJSON = "https://raw.githubusercontent.com/ErdbeerbaerLP/Discord-Chat-Integration/master/update_check.json")
public class DiscordIntegration {
	/**
	 * Mod name
	 */
	public static final String NAME = "Discord Integration";
	/**
	 * Mod version
	 */
	public static final String VERSION = "1.0.1";
	/**
	 * Modid
	 */
	public static final String MODID = "dcintegration";
	/**
	 * The only instance of {@link Discord}
	 */
    @Nullable
    public static Discord discord_instance;
	/**
	 * Message sent when the server is starting (in non-webhook mode!), stored for editing
	 */
	private RequestFuture<Message> startingMsg;
	/**
	 * If the server was stopped or has crashed
	 */
	private boolean stopped = false;
	/**
	 * Time when the server was started
	 */
	public static long started;
	public DiscordIntegration() {

	}
	@EventHandler
	public void preInit(FMLPreInitializationEvent ev) {
		System.out.println("Loading mod");
		try {
			discord_instance = new Discord();
			discord_instance.registerCommand(new CommandHelp());
			discord_instance.registerCommand(new CommandList());
			discord_instance.registerCommand(new CommandKill());
			discord_instance.registerCommand(new CommandStop());
			discord_instance.registerCommand(new CommandKick());
			discord_instance.registerCommand(new CommandUptime());
		} catch (Exception e) {
			System.err.println("Failed to login: "+e.getMessage());
			discord_instance = null;
		}
		MinecraftForge.EVENT_BUS.register(this);
	}
	@EventHandler
	public void init(FMLInitializationEvent ev) {
		if(discord_instance != null && !Configuration.WEBHOOK.BOT_WEBHOOK) this.startingMsg = discord_instance.sendMessageReturns("Server Starting...");
		if(discord_instance != null && Configuration.GENERAL.MODIFY_CHANNEL_DESCRIPTRION) discord_instance.getChannelManager().setTopic(Configuration.MESSAGES.CHANNEL_DESCRIPTION_STARTING).complete();
	}
	@EventHandler
	public void serverAboutToStart(FMLServerAboutToStartEvent ev) {

	}
	@EventHandler
	public void serverStarting(FMLServerStartingEvent ev) {
		if(Configuration.DISCORD_COMMAND.enabled) ev.registerServerCommand(new McCommandDiscord());
	}
	@EventHandler
	public void serverStarted(FMLServerStartedEvent ev) {
		started = new Date().getTime();
		if(discord_instance != null) if(startingMsg != null) try {
			this.startingMsg.get().editMessage(Configuration.MESSAGES.SERVER_STARTED_MSG).queue();
		} catch (InterruptedException | ExecutionException e) {
			e.printStackTrace();
		}
		else discord_instance.sendMessage(Configuration.MESSAGES.SERVER_STARTED_MSG);
		if(discord_instance != null) {
			if(Configuration.GENERAL.MODIFY_CHANNEL_DESCRIPTRION) discord_instance.updateChannelDesc.start();
			if(Loader.isModLoaded("ftbutilities")) {
				if(FTBUtilitiesConfig.auto_shutdown.enabled) discord_instance.ftbUtilitiesShutdownDetectThread.start();
				if(FTBUtilitiesConfig.afk.enabled) discord_instance.ftbUtilitiesAFKDetectThread.start();
			}
		}
		Runtime.getRuntime().addShutdownHook(new Thread(){
			@Override
			public void run() {
				if(discord_instance != null) {
					if(!stopped) {
						if(!discord_instance.isKilled) {
							discord_instance.stopThreads();
							if(Configuration.GENERAL.MODIFY_CHANNEL_DESCRIPTRION) discord_instance.getChannelManager().setTopic(Configuration.MESSAGES.SERVER_CRASHED_MSG).complete();
							discord_instance.sendMessage(Configuration.MESSAGES.SERVER_CRASHED_MSG);
						}
					}
					discord_instance.kill();
				}
			}
		});
		
		if(Configuration.GENERAL.UPDATE_CHECK) {
				CheckResult result = ForgeVersion.getResult(Loader.instance().getIndexedModList().get(DiscordIntegration.MODID));
				if (result.status == Status.OUTDATED){
					System.out.println("\n\u00A76[\u00A75DiscordIntegration\u00A76]\u00A7c Update available!\n\u00A7cCurrent version: \u00A74"+DiscordIntegration.VERSION+"\u00A7c, Newest: \u00A7a"+result.target+"\n\u00A7cChangelog:\n\u00A76"+result.changes.get(result.target)+"\nDownload the newest version on https://minecraft.curseforge.com/projects/dcintegration");
				}else if(result.status == Status.AHEAD){
					System.out.println("\n\u00A76[\u00A75DiscordIntegration\u00A76]\u00A77 It looks like you are using an Development version... \n\u00A77Your version: \u00A76"+DiscordIntegration.VERSION);
				}else if(result.status == Status.FAILED){
					System.err.println("\u00A76[\u00A75DiscordIntegration\u00A76]\u00A7c FAILED TO CHECK FOR UPDATES");
				}else if(result.status == Status.BETA){
					System.out.println("\n\u00A76[\u00A75DiscordIntegration\u00A76]\u00A7a You are using an Beta Version. This may contain bugs which are being fixed.");
				}else if(result.status == Status.BETA_OUTDATED){
					System.out.println(new TextComponentString("\u00A76[\u00A75DiscordIntegration\u00A76]\u00A7c You are using an Outdated Beta Version. This may contain bugs which are being fixed or are already fixed\n\u00A76Changelog of newer Beta:"+result.changes.get(result.target)));
				}else if(result.status == Status.UP_TO_DATE) {
				}else {
					System.out.println("\n\u00A76[\u00A75DiscordIntegration\u00A76]\u00A7cUpdateCheck: "+result.status.toString());
				}
			
		}
	}
	
	@EventHandler
	public void serverStopping(FMLServerStoppingEvent ev) {
		if(discord_instance != null) {
			discord_instance.stopThreads();
			if(Configuration.WEBHOOK.BOT_WEBHOOK) {
				final WebhookMessageBuilder b = new WebhookMessageBuilder();
				b.setContent(Configuration.MESSAGES.SERVER_STOPPED_MSG);
				b.setUsername(Configuration.WEBHOOK.SERVER_NAME);
				b.setAvatarUrl(Configuration.WEBHOOK.SERVER_AVATAR);
				final WebhookClient cli = discord_instance.getWebhook().newClient().build();
				cli.send(b.build());
				cli.close();
			}else discord_instance.getChannel().sendMessage(Configuration.MESSAGES.SERVER_STOPPED_MSG).complete();
			discord_instance.getChannelManager().setTopic(Configuration.MESSAGES.CHANNEL_DESCRIPTION_OFFLINE).complete();
		}
		stopped = true;
	}
	@EventHandler
	public void serverStopped(FMLServerStoppedEvent ev) {
		if(discord_instance != null) {
			if(!stopped) {
				if(!discord_instance.isKilled) {
					discord_instance.stopThreads();
					if(Configuration.GENERAL.MODIFY_CHANNEL_DESCRIPTRION) discord_instance.getChannelManager().setTopic(Configuration.MESSAGES.SERVER_CRASHED_MSG).complete();
					discord_instance.sendMessage(Configuration.MESSAGES.SERVER_CRASHED_MSG);
				}
			}
			discord_instance.kill();
		}
	}
	@SubscribeEvent
	public void playerJoin(PlayerLoggedInEvent ev) {
		if(discord_instance != null) discord_instance.sendMessage(
				Configuration.MESSAGES.PLAYER_JOINED_MSG
				.replace("%player%", ev.player.getName())
				);
	}
	public static EntityPlayerMP lastTimeout;
	@SubscribeEvent
	public void playerLeave(PlayerLoggedOutEvent ev) {

		if(discord_instance != null && !ev.player.equals(lastTimeout))
			discord_instance.sendMessage(
					Configuration.MESSAGES.PLAYER_LEFT_MSG
					.replace("%player%", ev.player.getName())
					);
		else if(discord_instance != null && ev.player.equals(lastTimeout)) {
			discord_instance.sendMessage(
					Configuration.MESSAGES.PLAYER_TIMEOUT_MSG
					.replace("%player%", ev.player.getName())
					);
			lastTimeout = null;
		}
	}
	@SubscribeEvent
	public void command(CommandEvent ev) {
		if(discord_instance != null)
			if(ev.getCommand().getName().equals("say")) {
				String msg = "";
				for(String s : ev.getParameters()) {
					msg = msg+s+" ";
				}
				if(ev.getSender() instanceof DedicatedServer) 
					discord_instance.sendMessage(msg);
				else if(ev.getSender().getCommandSenderEntity() instanceof EntityPlayer)
					discord_instance.sendMessage(ev.getSender().getName(), ev.getSender().getCommandSenderEntity().getUniqueID().toString(), msg);
			}
	}
	@SubscribeEvent
	public void chat(ServerChatEvent ev) {
		if(discord_instance != null) discord_instance.sendMessage(ev.getPlayer(), ev.getMessage());
	}
	@SubscribeEvent
	public void death(LivingDeathEvent ev) {
		if(ev.getEntity() instanceof EntityPlayerMP) {
			if(discord_instance!=null) discord_instance.sendMessage(
					Configuration.MESSAGES.PLAYER_DEATH_MSG
                            .replace("%player%", ev.getEntity().getName())
					.replace("%msg%", ev.getSource().getDeathMessage(ev.getEntityLiving()).getUnformattedText().replace(ev.getEntity().getName()+" ", ""))
					);
		}
	}
	@SubscribeEvent
	public void advancement(AdvancementEvent ev) {
		if(discord_instance != null && ev.getAdvancement().getDisplay() != null && ev.getAdvancement().getDisplay().shouldAnnounceToChat()) discord_instance.sendMessage(
				Configuration.MESSAGES.PLAYER_ADVANCEMENT_MSG
				.replace("%player%", ev.getEntityPlayer().getName())
				.replace("%name%", ev.getAdvancement().getDisplay().getTitle().getUnformattedText())
				.replace("%desc%", ev.getAdvancement().getDisplay().getDescription().getUnformattedText())
				.replace("\\n", "\n")
				);
	}
	
	public static String getUptime() {
        if (started == 0) {
            return "?????";
        }

        long diff = new Date().getTime() - started;

        int seconds = (int) Math.floorDiv(diff, 1000);
        if (seconds < 60) {
            return seconds + " second" + (seconds == 1 ? "" : "s");
        }
        int minutes = Math.floorDiv(seconds, 60);
        seconds -= minutes * 60;
        if (minutes < 60) {
            return minutes + " minute" + (minutes == 1 ? "" : "s") + ", " + seconds + " second" + (seconds == 1 ? "" : "s");
        }
        int hours = Math.floorDiv(minutes, 60);
        minutes -= hours * 60;
        if (hours < 24) {
            return hours + " hour" + (hours == 1 ? "" : "s") + ", " + minutes + " minute" + (minutes == 1 ? "" : "s") + ", " + seconds + " second" + (seconds == 1 ? "" : "s");
        }
        int days = Math.floorDiv(hours, 24);
        hours -= days * 24;
        return days + " day" + (days == 1 ? "" : "s") + ", " + hours + " hour" + (hours == 1 ? "" : "s") + ", " + minutes + " minute" + (minutes == 1 ? "" : "s") + ", " + seconds + " second" + (seconds == 1 ? "" : "s");
    }
}
