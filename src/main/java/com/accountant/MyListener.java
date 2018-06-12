package com.accountant;

import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.*;
import net.dv8tion.jda.core.events.guild.member.*;
import net.dv8tion.jda.core.events.Event;
import net.dv8tion.jda.core.events.ReadyEvent;
import net.dv8tion.jda.core.events.guild.GuildJoinEvent;
import net.dv8tion.jda.core.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.events.role.RoleDeleteEvent;
import net.dv8tion.jda.core.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.core.hooks.EventListener;
import net.dv8tion.jda.core.managers.GuildController;
import net.dv8tion.jda.core.utils.PermissionUtil;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.json.JSONObject;

import java.awt.*;
import java.io.IOException;
import java.sql.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;


import static org.fusesource.jansi.Ansi.ansi;


@SuppressWarnings("WeakerAccess")
public class MyListener implements EventListener {
    private Connection conn;
    private DbInterface dbInterface;
    private static ExecutorService eventThreads = Executors.newCachedThreadPool(new MyThreadFactory());

    private static class MyThreadFactory implements ThreadFactory{
        private final Queue<Integer> tQueue = new PriorityQueue<Integer>((a, b) -> b - a) {
            @Override
            public synchronized boolean add(Integer e) {
                return super.add(e);
            }

            @Override
            public synchronized Integer poll() {
                return super.poll();
            }
        };
        private int ctn=0;

        @Override
        public Thread newThread(Runnable r) {

            int index;
            synchronized (tQueue) {
                if (tQueue.size() == 0)
                    index = ctn++;
                else
                    index = tQueue.poll();
            }

            Thread b = new Thread(() -> {
                try {
                    r.run();
                }catch (Exception ex){
                    tQueue.add(Integer.parseInt(Thread.currentThread().getName().replace("Event Thread: ", "")));
                    throw ex;
                }
                tQueue.add(Integer.parseInt(Thread.currentThread().getName().replace("Event Thread: ", "")));
            }, "Event Thread: " + index);
            b.setPriority(Thread.NORM_PRIORITY + 1);
            return b;
        }
    }

    static PausableSingleThreadExecutor dbExecutor = new PausableSingleThreadExecutor(a->new Thread(a,"DB Thread"));

    @Override
    public void onEvent(Event event)
    {
        if (event instanceof ReadyEvent)
            onReady((ReadyEvent) event);
        else if (event instanceof MessageReceivedEvent) {
            MessageReceivedEvent ev = (MessageReceivedEvent) event;
            if (!ev.isFromType(ChannelType.TEXT)) return;
            //if is a bot exit immediately
            if (ev.getAuthor().isBot()) return;
            //if i cant write
            if (!PermissionUtil.checkPermission(ev.getTextChannel(), ev.getGuild().getSelfMember(), Permission.MESSAGE_WRITE))
                return;
            if (!PermissionUtil.checkPermission(ev.getTextChannel(), ev.getGuild().getSelfMember(), Permission.MESSAGE_EMBED_LINKS)) {
                ev.getTextChannel().sendMessage("Missing permission EMBED_LINKS please fix").queue();
                return;
            }
            MessageChannel channel = ev.getChannel();
            //get message
            Message message = ev.getMessage();
            if (Global.getGbl().getMapChannel().get(channel.getIdLong()) != null ||
                    message.getContent().startsWith(System.getenv("BOT_PREFIX")))
                eventThreads.execute(() -> onMessageReceived((MessageReceivedEvent) event));
        }
        else if (event instanceof RoleDeleteEvent)
            eventThreads.execute(() -> onRoleDelete((RoleDeleteEvent) event));

        else if (event instanceof GuildJoinEvent)
            eventThreads.execute(() -> onGuildJoin((GuildJoinEvent) event));
        else if (event instanceof GuildLeaveEvent)
            eventThreads.execute(() -> onGuildLeave((GuildLeaveEvent) event));


        else if (event instanceof GuildMemberRoleAddEvent)
            eventThreads.execute(() -> onMemberRoleAdded((GuildMemberRoleAddEvent) event));
        else if (event instanceof GuildMemberRoleRemoveEvent)
            eventThreads.execute(() -> onMemberRoleRemoved((GuildMemberRoleRemoveEvent) event));


        else if (event instanceof GuildMemberJoinEvent) {
            try {
                Thread.sleep(30);
                eventThreads.execute(() -> onMemberJoin((GuildMemberJoinEvent) event));
            }catch (InterruptedException ignored){}
        }
        else if (event instanceof GuildMemberLeaveEvent)
            eventThreads.execute(() -> onMemberLeave((GuildMemberLeaveEvent) event));
        else if (event instanceof GuildMemberNickChangeEvent)
            eventThreads.execute(() -> onMemberNick((GuildMemberNickChangeEvent) event));

    }


    private void onReady(ReadyEvent event) {
        String sql = "";
        List<Guild> guilds = event.getJDA().getSelfUser().getMutualGuilds();
        try {
            dbExecutor.submit(()->
                dbInterface.cleanDb(sql, guilds)
            ).get();
        } catch (InterruptedException ignored) {
        } catch (Exception e) {
            e.printStackTrace();
        }
        updateServerCount(event.getJDA());
        Logger.logger.logGeneral("------------SYSTEM READY---------------\r\n");
        Logger.started = true;
    }



    @SuppressWarnings("Duplicates")
    private void onMessageReceived(MessageReceivedEvent event) {
        //locales generation (dynamic strings from file selectionable by language)
        ResourceBundle output = ResourceBundle.getBundle("messages");
        if (checkConnection()) {
            Guild guild = event.getGuild();

            try {
                dbExecutor.submit(()->
                        dbInterface.updateDatabase(guild, output)
                ).get();

                //name of sender server
                String guildname = event.getGuild().getName();
                //get sender member
                Member member = event.getMember();
                //get channel to send
                MessageChannel channel = event.getChannel();
                //get message
                Message message = event.getMessage();
                //get id
                long messageId = message.getIdLong();

                boolean admin = dbExecutor.submit(()->
                        dbInterface.memberIsAdmin(member, guild.getIdLong())
                ).get();

                boolean mod = dbExecutor.submit(()->
                        dbInterface.memberIsMod(member, guild.getIdLong())
                ).get();


                if (Global.getGbl().getMapChannel().get(channel.getIdLong()) != null)
                    onConsoleMessageReceived(event);
                else {
                    if (message.getContent().startsWith(System.getenv("BOT_PREFIX"))) {
                        if (!PermissionUtil.checkPermission(event.getTextChannel(), event.getGuild().getSelfMember(), Permission.MESSAGE_EMBED_LINKS)) {
                            channel.sendMessage("Error could not send embeds, pls change my permissions!").queue();
                            return;
                        }
                        String args[] = message.getContent().split(" +");
                        String command = args[0].substring(System.getenv("BOT_PREFIX").length());
                        switch (command) {
    //------USER---------------------HELP--------------------------------------

                            case "help":
                                Logger.logger.logMessage("help", message);
                                PrintHelp(channel, member, guild,admin,mod);
                                Logger.logger.logReponse("help shown", guild, messageId);
                                break;

    //------USER--------------------PING---------------------------------------

                            case "ping":
                                Logger.logger.logMessage("Ping", message);
                                channel.sendMessage(output.getString("pong")).queue();
                                Logger.logger.logReponse("Ping shown", guild, messageId);
                                MessageChannel listen = Global.getGbl().getListener();
                                if (listen != null) {
                                    listen.sendMessage(new EmbedBuilder()
                                            .setAuthor(guildname, null, guild.getIconUrl())
                                            .addField("ID", guild.getId(), false).build()).queue();
                                    Global.getGbl().setListener(null);
                                }
                                break;
    //------ADMIN---------------MOD---------------------------------------
                            case "mod":
                                channel.sendTyping().queue();
                                //if member is allowed

                                if (member.isOwner() || admin) {
                                    //if there are other arguments
                                    if (args.length > 1) {
                                        //get mentioned roles
                                        List<Role> mentions = message.getMentionedRoles();
                                        //test on second arg
                                        Role role = null;
                                        if(mentions.size()>=1)
                                            role=mentions.get(0);

                                        switch (args[1]) {
                                            case "add":
                                                //if there is a mentioned role
                                                if(role==null)
                                                    if(args.length>2) {
                                                        try {
                                                            role = guild.getRoleById(args[2]);
                                                        } catch (NumberFormatException ex) {
                                                            channel.sendMessage(output.getString("error-roleId-invalid")).queue();
                                                        }
                                                    }else{
                                                        channel.sendMessage(output.getString("error-missing-param")).queue();
                                                    }

                                                Logger.logger.logMessage("mod add", message);
                                                if (role!=null) {
                                                    //call class method to add roles
                                                    final Role rl = role;
                                                    String msg = dbExecutor.submit(()->
                                                            dbInterface.addMod(rl, guild, output, messageId)
                                                    ).get();
                                                    channel.sendMessage(msg).queue();
                                                }
                                                break;
                                            case "remove":
                                                //if there is a mentioned user
                                                if(role==null)
                                                    if(args.length>2) {
                                                        try {
                                                            role = guild.getRoleById(args[2]);
                                                        } catch (NumberFormatException ex) {
                                                            channel.sendMessage(output.getString("error-roleId-invalid")).queue();
                                                        }
                                                    }else{
                                                        channel.sendMessage(output.getString("error-missing-param")).queue();
                                                    }


                                                Logger.logger.logMessage("mod remove", message);
                                                if (role!=null) {
                                                    //call class method to remove roles
                                                    final Role rl = role;
                                                    String msg = dbExecutor.submit(()->
                                                            dbInterface.removeMod(rl, guild, output, messageId)
                                                    ).get();
                                                    channel.sendMessage(msg).queue();
                                                }
                                                break;
                                            case "clear": {
                                                Logger.logger.logMessage("mod clear", message);
                                                String msg = dbExecutor.submit(() ->
                                                        dbInterface.clearMod(guild, output, messageId)).get();
                                                channel.sendMessage(msg).queue();
                                                Logger.logger.logReponse("mods cleared", guild, messageId);
                                                break;
                                            }
                                            case "list": {
                                                //list all mods
                                                Logger.logger.logMessage("mod list", message);
                                                String msg = dbExecutor.submit(() ->
                                                        dbInterface.listMod(guild, output, messageId)).get();
                                                SendMsg(channel, msg);
                                                break;
                                            }
                                        }

                                    }
                                    break;

                                } else {
                                    Logger.logger.logMessage("mod", message);
                                    channel.sendMessage(output.getString("error-user-permission")).queue();
                                    Logger.logger.logReponse("user not allowed", guild, messageId);
                                }
                                break;
    //------ADMIN---------------ADMIN---------------------------------------
                            case "admin":
                                channel.sendTyping().queue();
                                //if member is allowed
                                if (member.isOwner() || admin) {
                                    //if there are other arguments
                                    if (args.length > 1) {
                                        //get mentioned roles
                                        List<Role> mentions = message.getMentionedRoles();
                                        Role role = null;
                                        if(mentions.size()>=1)
                                            role=mentions.get(0);

                                        //test on second arg
                                        switch (args[1]) {
                                            case "add":
                                                //if there is a mentioned role
                                                if(role==null)
                                                    if(args.length>2) {
                                                        try {
                                                            role = guild.getRoleById(args[2]);
                                                        } catch (NumberFormatException ex) {
                                                            channel.sendMessage(output.getString("error-roleId-invalid")).queue();
                                                        }
                                                    }else{
                                                        channel.sendMessage(output.getString("error-missing-param")).queue();
                                                    }

                                                Logger.logger.logMessage("admin add", message);
                                                if (role!=null) {
                                                    //call class method to add roles
                                                    final Role rl = role;
                                                    String msg = dbExecutor.submit(()->
                                                            dbInterface.addAdmin(rl, guild, output, messageId)
                                                    ).get();
                                                    channel.sendMessage(msg).queue();
                                                }
                                                break;
                                            case "remove":
                                                //if there is a mentioned user
                                                if(role==null)
                                                    if(args.length>2) {
                                                        try {
                                                            role = guild.getRoleById(args[2]);
                                                        } catch (NumberFormatException ex) {
                                                            channel.sendMessage(output.getString("error-roleId-invalid")).queue();
                                                        }
                                                    }else{
                                                        channel.sendMessage(output.getString("error-missing-param")).queue();
                                                    }

                                                Logger.logger.logMessage("admin remove", message);
                                                if (role!=null) {
                                                    //call class method to remove roles
                                                    final Role rl = role;
                                                    String msg= dbExecutor.submit(()->
                                                            dbInterface.removeAdmin(rl, guild, output, messageId)
                                                    ).get();
                                                    channel.sendMessage(msg).queue();
                                                }
                                                break;
                                            case "clear": {
                                                Logger.logger.logMessage("admin clear", message);
                                                String msg = dbExecutor.submit(()->
                                                        dbInterface.clearAdmin(guild, output, messageId)
                                                ).get();
                                                channel.sendMessage(msg).queue();
                                                Logger.logger.logReponse("admins cleared", guild, messageId);
                                                break;
                                            }
                                            case "auto": {
                                                Logger.logger.logMessage("admin auto", message);
                                                dbExecutor.submit(()->dbInterface.autoRole(event.getGuild())).get();
                                                channel.sendMessage(output.getString("admin-auto")).queue();
                                                Logger.logger.logReponse("admins updated", guild, messageId);
                                            }
                                            case "list": {
                                                //list all mods
                                                Logger.logger.logMessage("admin list", message);
                                                String msg = dbExecutor.submit(()->
                                                        dbInterface.listAdmin(guild, output, messageId)
                                                ).get();
                                                SendMsg(channel,msg);
                                                break;
                                            }
                                        }

                                    }
                                    break;

                                } else {
                                    Logger.logger.logMessage("admin", message);
                                    channel.sendMessage(output.getString("error-user-permission")).queue();
                                    Logger.logger.logReponse("user not allowed", guild, messageId);
                                }
                                break;
                            case "roles":
                                channel.sendTyping().queue();
                                //if member is allowed
                                if (member.isOwner() || admin) {
                                    Logger.logger.logMessage("roles",message);
                                    StringBuilder ret = new StringBuilder();
                                    ret.append(output.getString("roles-head")).append("\n");
                                    for (Role r : guild.getRoles()){
                                        if(!r.isPublicRole())
                                            ret.append(r.getName()).append(" [").append(r.getId()).append("]\n");
                                    }
                                    SendMsg(channel,ret.toString());
                                    Logger.logger.logReponse("role list shown",guild,messageId);
                                } else {
                                    Logger.logger.logMessage("roles", message);
                                    channel.sendMessage(output.getString("error-user-permission")).queue();
                                    Logger.logger.logReponse("user not allowed", guild, messageId);
                                }
                                break;
    //------ADMIN---------------FORGIVE---------------------------------------
                            case "forgive":
                                channel.sendTyping().queue();
                                //if member is allowed
                                if (member.isOwner() || admin) {
                                    //if there are other arguments
                                    if (args.length > 1) {
                                        //get mentioned roles
                                        List<User> mentions = message.getMentionedUsers();
                                        //test on mentions
                                        if(mentions.size()>0) {
                                            String sql = "";
                                            if(guild.getMemberById(mentions.get(0).getIdLong())==null) {
                                                dbExecutor.submit(()->dbInterface.forgiveUser(output, guild, channel, mentions.get(0), sql)).get();
                                            }else
                                                channel.sendMessage(output.getString("error-forgive-mutual")).queue();
                                        }
                                    }
                                    break;
                                } else {
                                    Logger.logger.logMessage("mod", message);
                                    channel.sendMessage(output.getString("error-user-permission")).queue();
                                    Logger.logger.logReponse("user not allowed", guild, messageId);
                                }
                                break;

    //------ADMIN---------------**SUPPORT GUILD**---------------------------------------
                            default:
                                if (guildIsSupport(guild))
                                    switch (command) {
                                        case "console":
                                            Logger.logger.logMessage("console", message);
                                            if (member.isOwner() || mod) {
                                                if (guild.getTextChannelById(channel.getIdLong()).getTopic().contains(":console:")) {
                                                    LogLinker act = Global.getGbl().getMapChannel().get(channel.getIdLong());
                                                    if (act == null) {
                                                        if (args.length >= 2) {
                                                            if (args[1].matches("\\d{18}")) {
                                                                long guildId = Long.parseLong(args[1]);

                                                                if (guildIdIsValid(guildId, message)) {
                                                                    new LogLinker(guildId, channel);
                                                                    channel.sendMessage(output.getString("console-started")).queue();
                                                                    Logger.logger.logReponse("log daemon started in channel: " + channel.getName(), guild, messageId);
                                                                } else {
                                                                    channel.sendMessage(output.getString("error-console-non_mutual")).queue();
                                                                    Logger.logger.logReponse("guild non mutual", guild, messageId);
                                                                }
                                                            } else {
                                                                channel.sendMessage(output.getString("error-console-non_id")).queue();
                                                                Logger.logger.logReponse("not an id", guild, messageId);
                                                            }
                                                        } else {
                                                            channel.sendMessage(output.getString("error-console-no_id")).queue();
                                                            Logger.logger.logReponse("mssing id", guild, messageId);
                                                        }
                                                    } else {
                                                        channel.sendMessage(output.getString("error-console-active")).queue();
                                                        Logger.logger.logReponse("already a running daemon", guild, messageId);
                                                    }
                                                } else {
                                                    channel.sendMessage(output.getString("error-console-channel")).queue();
                                                    Logger.logger.logReponse("channel not console channel", guild, messageId);
                                                }
                                            } else {
                                                channel.sendMessage(output.getString("error-user-permission")).queue();
                                                Logger.logger.logReponse("user not allowed", guild, messageId);
                                            }
                                            break;
                                        case "listen":
                                            Logger.logger.logMessage("listen", message);
                                            if (member.isOwner() || mod) {
                                                if (guild.getTextChannelById(channel.getIdLong()).getTopic().contains(":console:")) {
                                                    if (Global.getGbl().getListener() == null) {
                                                        Global.getGbl().setListener(channel);
                                                        channel.sendMessage(output.getString("listen-enabled")).queue();
                                                        Logger.logger.logReponse("listener enabled", guild, messageId);
                                                    } else {
                                                        channel.sendMessage(output.getString("error-listen")).queue();
                                                        Logger.logger.logReponse("error listener active", guild, messageId);
                                                    }
                                                } else {
                                                    channel.sendMessage(output.getString("error-console-channel")).queue();
                                                    Logger.logger.logReponse("channel not console channel", guild, messageId);
                                                }
                                            } else {
                                                channel.sendMessage(output.getString("error-user-permission")).queue();
                                                Logger.logger.logReponse("user not allowed", guild, messageId);
                                            }
                                    }
                                else if(member.getUser().getIdLong() == Long.parseLong(System.getenv("OWNER_ID"))){
                                    if(command.equals("reload")){
                                        dbExecutor.submit(()->dbInterface.rePopolateDb(event)).get();
                                    }
                                }

                        }
                    }
                }
            } catch (InterruptedException ignored) {
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            event.getJDA().shutdown();
            Reconnector.reconnect();
        }

    }





    private void onMemberRoleAdded(GuildMemberRoleAddEvent event){
        ResourceBundle output = ResourceBundle.getBundle("messages");
        try {
            if (checkConnection()) {
                Guild guild = event.getGuild();

                dbExecutor.submit(() -> dbInterface.updateDatabase(guild, output)).get();
                //get sender member
                Member member = event.getMember();

                User user = member.getUser();

                List<Role> roles = event.getRoles();

                dbExecutor.submit(()-> dbInterface.memorizeRole(guild, user, roles)).get();
            } else {
                event.getJDA().shutdown();
                Reconnector.reconnect();
            }
        }catch (InterruptedException ignored){}
        catch (Exception e){
            e.printStackTrace();
        }

    }



    private void onMemberRoleRemoved(GuildMemberRoleRemoveEvent event){
        ResourceBundle output = ResourceBundle.getBundle("messages");
        try {
            if (checkConnection()) {
                Guild guild = event.getGuild();

                dbExecutor.submit(() -> dbInterface.updateDatabase(guild, output)).get();
                //get sender member
                Member member = event.getMember();

                User user = member.getUser();

                List<Role> roles = event.getRoles();

                dbInterface.removeRole(guild, user, roles);
            } else {
                event.getJDA().shutdown();
                Reconnector.reconnect();
            }
        }catch (InterruptedException ignored){}
        catch (Exception e){
            e.printStackTrace();
        }
    }



    private void onMemberNick(GuildMemberNickChangeEvent event){
        ResourceBundle output = ResourceBundle.getBundle("messages");
        try {
            if (checkConnection()) {
                Guild guild = event.getGuild();

                dbExecutor.submit(()->dbInterface.updateDatabase(guild, output)).get();
                //get sender member
                Member member = event.getMember();

                User user = member.getUser();

                String nick = event.getNewNick();

                dbExecutor.submit(()->dbInterface.updateNick(guild, user, nick)).get();
            } else {
                event.getJDA().shutdown();
                Reconnector.reconnect();
            }
        }catch (InterruptedException ignored){}
        catch (Exception e){
            e.printStackTrace();
        }
    }



    private void onMemberJoin(GuildMemberJoinEvent event){
        ResourceBundle output = ResourceBundle.getBundle("messages");
        try {
            if (checkConnection()) {

                Guild guild = event.getGuild();

                dbExecutor.submit(()->dbInterface.updateDatabase(guild, output)).get();
                //get sender member
                Member member = event.getMember();

                User user = member.getUser();

                GuildController gc = new GuildController(guild);


                //wait till other bots act
                try {
                    //act
                    List<Role> roles = new ArrayList<>();
                    int out = dbExecutor.submit(()->dbInterface.restoreUser(guild, member, user, gc, roles)).get();
                    if (out > 0) {
                        Logger.logger.logEvent("User " + member.getEffectiveName() + " JOINED - RESTORED", guild);
                        try {
                            event.getGuild().getDefaultChannel().sendMessage(output.getString("restore").replace("[mention]", member.getAsMention()) + "\n" +
                                    (out > 1 ? output.getString("restored-muted") : "")).queue();
                        } catch (Exception ignore) {
                        }
                    } else
                        Logger.logger.logEvent("User " + member.getEffectiveName() + " JOINED", guild);
                } catch (InterruptedException ignored) {

                }
            } else {
                event.getJDA().shutdown();
                Reconnector.reconnect();
            }
        }catch (InterruptedException ignored){}
        catch (Exception e){
            e.printStackTrace();
        }
    }



    private void onMemberLeave(GuildMemberLeaveEvent event){
        ResourceBundle output = ResourceBundle.getBundle("messages");
        try {
            if (checkConnection()) {
                Guild guild = event.getGuild();

                dbExecutor.submit(()->dbInterface.updateDatabase(guild, output)).get();
                //get sender member
                Member member = event.getMember();

                User user = member.getUser();

                dbExecutor.submit(()->dbInterface.saveUser(guild, user)).get();
                Logger.logger.logEvent("User " + member.getEffectiveName() + " LEAVED", guild);

            } else {
                event.getJDA().shutdown();
                Reconnector.reconnect();
            }
        }catch (InterruptedException ignored){}
        catch (Exception e){
            e.printStackTrace();
        }
    }




    private void onRoleDelete(RoleDeleteEvent event) {
        ResourceBundle output;
        try {
            if (checkConnection()) {
                output = ResourceBundle.getBundle("messages");

                if (dbExecutor.submit(() -> dbInterface.onRoleDeleted(event.getRole())).get()) {
                    Logger.logger.logEvent("role deleted in guild: ", event.getGuild());
                    try {
                        TextChannel channel = event.getGuild().getDefaultChannel();
                        channel.sendMessage(output.getString("event-role-deleted")).queue();
                        channel.sendMessage(output.getString("event-role-deleted-2")).queue();
                    } catch (InsufficientPermissionException ex) {
                        event.getGuild().getOwner().getUser().openPrivateChannel().queue((PrivateChannel channel) ->
                        {
                            channel.sendMessage(output.getString("event-role-deleted")).queue();
                            channel.sendMessage(output.getString("event-role-deleted-2")).queue();
                        });
                    }

                }
            } else {
                event.getJDA().shutdown();
                Reconnector.reconnect();
            }
        }catch (InterruptedException ignored){}
        catch (Exception e){
            e.printStackTrace();
        }
    }


    private void onGuildJoin(GuildJoinEvent event) {
        ResourceBundle output = ResourceBundle.getBundle("messages");
        Logger.logger.logEvent("GUILD HAS JOINED", event.getGuild());
        try {
            if(!dbExecutor.submit(()->dbInterface.guildIsInDb(event.getGuild())).get()) {
                try {
                    event.getGuild().getDefaultChannel().sendMessage(output.getString("event-join").replace("[version]", Global.version)).queue();
                } catch (InsufficientPermissionException ex) {
                    event.getGuild().getOwner().getUser().openPrivateChannel().queue((PrivateChannel channel) ->
                            channel.sendMessage(output.getString("event-join").replace("[version]", Global.version)).queue());
                }
                dbExecutor.submit(() -> dbInterface.newGuild(event.getGuild())).get();
                dbExecutor.submit(() -> dbInterface.autoRole(event.getGuild())).get();
            }
        }catch (InterruptedException ignored){}
        catch (Exception e){
            e.printStackTrace();
        }
        updateServerCount(event.getJDA());
    }

    


    private void onGuildLeave(GuildLeaveEvent event) {
        Logger.logger.logEvent("GUILD HAS LEAVED", event.getGuild());
        try {
            dbExecutor.submit(()->dbInterface.guildLeave(event.getGuild())).get();
        } catch (InterruptedException ignored) {
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
        updateServerCount(event.getJDA());
    }

    

    //prints the help message
    private void PrintHelp(MessageChannel channel, Member member, Guild guild,boolean admin,boolean mod) {
        ResourceBundle output = ResourceBundle.getBundle("messages");
        EmbedBuilder helpMsg = new EmbedBuilder();
        helpMsg.setColor(Color.GREEN);
        //help is dynamic (different for every user)
        helpMsg.setTitle(output.getString("help-title"));
        helpMsg.setDescription(output.getString("help-description"));
        helpMsg.addField("help", output.getString("help-def-help"), false);
        helpMsg.addField("ping", output.getString("help-def-ping"), false);

        //if is allowed to use mod commands
        if (member.isOwner() || admin) {
            helpMsg.addBlankField(false);
            helpMsg.addField("ADMIN commands:", "", false);

            helpMsg.addField("admin", output.getString("help-def-admin"), false);

            helpMsg.addField("mod", output.getString("help-def-mod"), false);

            helpMsg.addField("roles", output.getString("help-def-roles"), false);

            helpMsg.addField("forgive", output.getString("help-def-forgive"), false);
        }
        if(member.isOwner() || mod){
            if (guildIsSupport(guild)) {
                helpMsg.addBlankField(false);

                helpMsg.addField("SUPPORT commands:", "", false);

                helpMsg.addField("console", output.getString("help-def-console"), false);

                helpMsg.addField("listen", output.getString("help-def-listen"), false);
            }
        }

        helpMsg.addField("",output.getString("help-last"),false);

        if (member.getUser().getIdLong() == Long.parseLong(System.getenv("OWNER_ID")))
            helpMsg.setFooter(output.getString("help-footer-owner"), member.getUser().getAvatarUrl());
        else
            helpMsg.setFooter(output.getString("help-footer"), guild.getIconUrl());
        channel.sendMessage(helpMsg.build()).queue();
    }

    private boolean checkConnection() {
            try {
                Statement stmt = conn.createStatement();
                stmt.execute("SELECT 1");
                stmt.close();
                return true;
            } catch (SQLException ex) {
                Logger.logger.logError("SQLError in : SELECT 1");
                Logger.logger.logError("SQLException: " + ex.getMessage());
                Logger.logger.logError("SQLState: " + ex.getSQLState());
                Logger.logger.logError("VendorError: " + ex.getErrorCode());
            }
        return true;
    }

    private boolean guildIdIsValid(long guildId, Message message) {
        JDA jda = message.getJDA();
        return jda.getGuildById(guildId) != null;
    }

    private void updateServerCount(JDA api) {
        String url = "https://discordbots.org/api/bots/" + api.getSelfUser().getId() + "/stats";
        String discordbots_key = System.getenv("DISCORDBOTS_KEY");

        JSONObject data = new JSONObject();
        data.put("server_count", api.getGuilds().size());

        RequestBody body = RequestBody.create(MediaType.parse("application/json"), data.toString());

        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .addHeader("User-Agent", "DiscordBot " + api.getSelfUser().getName())
                .addHeader("Authorization", discordbots_key)
                .build();

        try {
            new OkHttpClient().newCall(request).execute().close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    

    private void SendMsg(MessageChannel channel, String text) {
        int messages = ((Double) Math.ceil(text.length() / 1000.0)).intValue();
        if (messages > 1) {
            int s = 0;
            for (int i = 0; i < messages; i++) {
                int p = s, a = s;
                while ((a - s) < 1000 & a != -1) {
                    p = a;
                    a = text.indexOf("\n", p + 1);
                }
                if (a == -1)
                    p = text.length();
                channel.sendMessage(text.substring(s, p)).queue();
                s = p;
            }
        } else {
            channel.sendMessage(text).queue();
        }
    }

    private boolean guildIsSupport(Guild guild) {
        return guild.getIdLong() == Long.parseLong(System.getenv("SUPPORT_GUILD_ID"));
    }

    @SuppressWarnings("Duplicates")
    public void onConsoleMessageReceived(MessageReceivedEvent event) {
        ResourceBundle output = ResourceBundle.getBundle("messages");

        Guild remote = event.getGuild();
        //get channel to send
        MessageChannel channel = event.getChannel();
        //get message
        Message message = event.getMessage();
        //get id
        long messageId = message.getIdLong();
        //get bind guild
        Guild guild = event.getJDA().getGuildById(Global.getGbl().getMapChannel().get(channel.getIdLong()).getGuildId());

        String args[] = message.getContent().split(" +");

        try {
            switch (args[0].equals("") ? args[1] : args[0]) {
                case "end":
                    Logger.logger.logMessage("end", message);
                    LogLinker act = Global.getGbl().getMapChannel().get(channel.getIdLong());
                    act.delete();
                    channel.sendMessage(output.getString("console-stopped")).queue();
                    Logger.logger.logReponse("console daemon stopped in channel:" + channel.getName(), guild, messageId);
                    break;
                case "mod":
                    //if member is allowed
                    if (args.length > 1) {
                        //get mentioned roles
                        List<Role> mentions = message.getMentionedRoles();
                        Role role = null;
                        if (mentions.size() > 1)
                            role = mentions.get(0);


                        //test on second arg
                        switch (args[1]) {
                            case "add":
                                //if there is a mentioned role
                                if (role == null)
                                    if (args.length > 2) {
                                        try {
                                            role = remote.getRoleById(args[2]);
                                        } catch (NumberFormatException ex) {
                                            channel.sendMessage(output.getString("error-roleId-invalid")).queue();
                                        }
                                    } else {
                                        channel.sendMessage(output.getString("error-missing-param")).queue();
                                    }

                                Logger.logger.logRemoteMsg("mod add", message, guild);
                                if (role != null) {
                                    //call class method to add roles
                                    final Role rl = role;
                                    String msg = dbExecutor.submit(() ->
                                            dbInterface.addRemoteMod(rl, guild, output, messageId, remote)
                                    ).get();
                                    channel.sendMessage(msg).queue();
                                }
                                break;
                            case "remove":
                                //if there is a mentioned user
                                if (role == null)
                                    if (args.length > 2) {
                                        try {
                                            role = remote.getRoleById(args[2]);
                                        } catch (NumberFormatException ex) {
                                            channel.sendMessage(output.getString("error-roleId-invalid")).queue();
                                        }
                                    } else {
                                        channel.sendMessage(output.getString("error-missing-param")).queue();
                                    }
                                Logger.logger.logRemoteMsg("mod remove", message, guild);
                                if (role != null) {
                                    //call class method to remove roles
                                    final Role rl = role;
                                    String msg = dbExecutor.submit(()->
                                            dbInterface.removeRemoteMod(rl, guild, output, messageId, remote)
                                    ).get();
                                    channel.sendMessage(msg).queue();
                                }
                                break;
                            case "clear": {
                                Logger.logger.logRemoteMsg("mod clear", message, guild);
                                String msg = dbExecutor.submit(()->
                                        dbInterface.clearRemoteMod(guild, output, messageId, remote)
                                ).get();
                                channel.sendMessage(msg).queue();
                                Logger.logger.logRemoteRep("mods cleared", guild, messageId, remote);
                                break;
                            }
                            case "list": {
                                //list all mods
                                Logger.logger.logRemoteMsg("mod list", message, guild);
                                String msg = dbExecutor.submit(()->
                                        dbInterface.listRemoteMod(guild, output, messageId, remote)
                                ).get();
                                SendMsg(channel,msg);
                                break;
                            }
                        }

                    }
                    break;
                case "admin":
                    //if member is allowed
                    if (args.length > 1) {
                        //get mentioned roles
                        List<Role> mentions = message.getMentionedRoles();
                        Role role = null;
                        if (mentions.size() > 1)
                            role = mentions.get(0);


                        //test on second arg
                        switch (args[1]) {
                            case "add":
                                //if there is a mentioned role
                                if (role == null)
                                    if (args.length > 2) {
                                        try {
                                            role = remote.getRoleById(args[2]);
                                        } catch (NumberFormatException ex) {
                                            channel.sendMessage(output.getString("error-roleId-invalid")).queue();
                                        }
                                    } else {
                                        channel.sendMessage(output.getString("error-missing-param")).queue();
                                    }

                                Logger.logger.logRemoteMsg("mod add", message, guild);
                                if (role != null) {
                                    //call class method to add roles
                                    final Role rl = role;
                                    String msg = dbExecutor.submit(() ->
                                            dbInterface.addRemoteAdmin(rl, guild, output, messageId, remote)
                                    ).get();
                                    channel.sendMessage(msg).queue();
                                }
                                break;
                            case "remove":
                                //if there is a mentioned user
                                if (role == null)
                                    if (args.length > 2) {
                                        try {
                                            role = remote.getRoleById(args[2]);
                                        } catch (NumberFormatException ex) {
                                            channel.sendMessage(output.getString("error-roleId-invalid")).queue();
                                        }
                                    } else {
                                        channel.sendMessage(output.getString("error-missing-param")).queue();
                                    }
                                Logger.logger.logRemoteMsg("mod remove", message, guild);
                                if (role != null) {
                                    //call class method to remove roles
                                    final Role rl = role;
                                    String msg = dbExecutor.submit(() ->
                                            dbInterface.removeRemoteAdmin(rl, guild, output, messageId, remote)
                                    ).get();
                                    channel.sendMessage(msg).queue();
                                }
                                break;
                            case "clear": {
                                Logger.logger.logRemoteMsg("mod clear", message, guild);
                                String msg = dbExecutor.submit(() ->
                                        dbInterface.clearRemoteAdmin(guild, output, messageId, remote)
                                ).get();
                                channel.sendMessage(msg).queue();
                                Logger.logger.logRemoteRep("mods cleared", guild, messageId, remote);
                                break;
                            }
                            case "auto": {
                                Logger.logger.logRemoteMsg("mod auto", message, guild);
                                dbExecutor.submit(()->dbInterface.autoRole(guild)).get();
                                channel.sendMessage(output.getString("mod-auto")).queue();
                                Logger.logger.logRemoteRep("mods updated", guild, messageId, remote);
                            }
                            case "list":{
                                //list all mods
                                Logger.logger.logRemoteMsg("mod list", message, guild);
                                String msg = dbExecutor.submit(() ->
                                        dbInterface.listRemoteAdmin(guild, output, messageId, remote)
                                ).get();
                                SendMsg(channel, msg);
                                break;
                            }

                        }

                    }
                    break;
                case "roles":
                    channel.sendTyping().queue();
                    //if member is allowed
                    Logger.logger.logMessage("roles", message);
                    StringBuilder ret = new StringBuilder();
                    ret.append(output.getString("roles-head")).append("\n");
                    for (Role r : remote.getRoles()) {
                        if (!r.isPublicRole())
                            ret.append(r.getName()).append(" [").append(r.getId()).append("]\n");
                    }
                    SendMsg(channel, ret.toString());
                    Logger.logger.logReponse("role list shown", guild, messageId);
                    break;
                case "whoami":
                    Logger.logger.logMessage("whoami", message);
                    EmbedBuilder eb = new EmbedBuilder();
                    eb.setAuthor(guild.getName(), null, guild.getIconUrl());
                    eb.setDescription(guild.getId());
                    channel.sendMessage(eb.build()).queue();
                    Logger.logger.logReponse("info shown", remote, messageId);
                    break;
                case "help":
                    Logger.logger.logMessage("console help", message);
                    channel.sendMessage(output.getString("help-console-def")).queue();
                    Logger.logger.logReponse("help shown", remote, messageId);
                    break;
            }
        } catch (InterruptedException ignored) {
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
    }

    public MyListener(Connection conn) {
        this.conn = conn;
        this.dbInterface = new DbInterface(conn);
    }

    public void close(){
        System.err.println(ansi().fgRed().a("Closing Threads").reset());
        eventThreads.shutdown();
        System.err.println(ansi().fgGreen().a("Threads closed").reset());
        System.err.println();
        System.err.println(ansi().fgRed().a("Closing dbThread").reset());
        dbExecutor.shutdown();
        System.err.println(ansi().fgGreen().a("dbThread closed").reset());
        System.err.println();
        System.err.println(ansi().fgRed().a("Closing Statements").reset());
        dbInterface.close();
        System.err.println(ansi().fgGreen().a("Statements closed").reset());
        System.err.println();
        try {
            System.err.println(ansi().fgRed().a("Closing connection").reset());
            conn.close();
            System.err.println(ansi().fgGreen().a("Connection closed").reset());
        } catch (SQLException ignored) {
        }
    }



    static public class PausableSingleThreadExecutor extends ThreadPoolExecutor{
        private boolean isPaused;

        private ReentrantLock lock = new ReentrantLock();
        private Condition condition = lock.newCondition();
        private int ctn;


        public PausableSingleThreadExecutor(ThreadFactory threadFactory) {
            super(1, 1, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), threadFactory);
        }

        @Override
        protected void beforeExecute(Thread t, Runnable r) {
            super.beforeExecute(t, r);
            lock.lock();
            try {
                while (isPaused) {
                    ctn++;
                    condition.await();
                    ctn--;
                }
            } catch (InterruptedException ie) {
                t.interrupt();
            } finally {
                lock.unlock();
            }
        }

        public boolean isRunning() {
            return !isPaused && ctn==0;
        }

        public boolean isPaused() {
            return isPaused && ctn==1;
        }

        /**
         * Pause the execution
         */
        public void pause() throws InterruptedException{
            lock.lock();
            try {
                isPaused = true;
            } finally {
                lock.unlock();
            }
            while (ctn==0){
                if(Thread.currentThread().isInterrupted())
                    throw new InterruptedException();
            }
        }

        /**
         * Resume pool execution
         */
        public void resume() {
            lock.lock();
            try {
                isPaused = false;
                condition.signalAll();
            } finally {
                lock.unlock();
            }
        }
    }






}
