package com.accountant;

import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.*;
import net.dv8tion.jda.core.events.Event;
import net.dv8tion.jda.core.events.ReadyEvent;
import net.dv8tion.jda.core.events.channel.text.TextChannelDeleteEvent;
import net.dv8tion.jda.core.events.guild.GuildJoinEvent;
import net.dv8tion.jda.core.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.core.events.guild.member.*;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.events.role.RoleDeleteEvent;
import net.dv8tion.jda.core.events.user.update.UserUpdateNameEvent;
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
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static org.fusesource.jansi.Ansi.ansi;


@SuppressWarnings("WeakerAccess")
public class MyListener implements EventListener {
    private Connection conn;
    private DbInterface dbInterface;
    private boolean ready;
    private static ExecutorService eventThreads = new ThreadPoolExecutor(0, Integer.MAX_VALUE,
            60L, TimeUnit.SECONDS,
            new SynchronousQueue<>()) {

        Map<Thread, Integer> threadIntegerMap = new HashMap<>();

        @Override
        protected void beforeExecute(Thread t, Runnable r) {
            super.beforeExecute(t, r);
            int index;
            synchronized (Global.eventQueue) {
                index = Optional.ofNullable(Global.eventQueue.poll()).orElse(-1);
            }
            if (index == -1)
                index = Global.maxEventCtn++;

            t.setName("Event Thread: " + index);
            threadIntegerMap.put(t, index);
        }

        @Override
        protected void afterExecute(Runnable r, Throwable t) {
            super.afterExecute(r, t);
            int index;
            synchronized (Global.eventQueue) {
                Thread thread = Thread.currentThread();
                index = threadIntegerMap.get(thread);
                Global.eventQueue.add(index);
            }
        }
    };

    private Set<User> restoring = new HashSet<>();

    private Set<Guild> initializing = new HashSet<>();

    static PausableSingleThreadExecutor dbExecutor = new PausableSingleThreadExecutor(a -> new Thread(a, "DB Thread"));

    @Override
    public void onEvent(Event event) {
        if (event instanceof ReadyEvent)
            onReady((ReadyEvent) event);
        else if (ready) {
            if (event instanceof MessageReceivedEvent) {
                MessageReceivedEvent ev = (MessageReceivedEvent) event;
                if (!ev.isFromType(ChannelType.TEXT)) return;
                //if is a bot exit immediately
                if (ev.getAuthor().isBot()) return;
                //if i cant write
                if (!PermissionUtil.checkPermission(ev.getTextChannel(), ev.getGuild().getSelfMember(), Permission.MESSAGE_WRITE))
                    return;
                //get message
                Message message = ev.getMessage();
                if (message.getContentDisplay().startsWith(System.getenv("BOT_PREFIX")))
                    eventThreads.execute(() -> onMessageReceived((MessageReceivedEvent) event));
            } else if (event instanceof RoleDeleteEvent)
                eventThreads.execute(() -> onRoleDelete((RoleDeleteEvent) event));

            else if (event instanceof TextChannelDeleteEvent)
                eventThreads.execute(() -> onChannelDelete((TextChannelDeleteEvent) event));

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
                    restoring.add(((GuildMemberJoinEvent) event).getUser());
                    Thread.sleep(30);
                    eventThreads.execute(() -> onMemberJoin((GuildMemberJoinEvent) event));
                } catch (InterruptedException ignored) {
                }
            } else if (event instanceof GuildMemberLeaveEvent)
                eventThreads.execute(() -> onMemberLeave((GuildMemberLeaveEvent) event));
            else if (event instanceof GuildMemberNickChangeEvent)
                eventThreads.execute(() -> onMemberNick((GuildMemberNickChangeEvent) event));
            else if (event instanceof UserUpdateNameEvent)
                eventThreads.execute(() -> onMemberUsername((UserUpdateNameEvent) event));
        }

    }


    private void onReady(ReadyEvent event) {
        String sql = "";
        List<Guild> guilds = event.getJDA().getSelfUser().getMutualGuilds();
        try {
            dbExecutor.submit(() ->
                    dbInterface.cleanDb(sql, guilds)
            ).get();
            dbExecutor.submit(() ->
                    dbInterface.rePopolateDb(event.getJDA())
            ).get();
        } catch (InterruptedException ignored) {
        } catch (Exception e) {
            e.printStackTrace();
        }
        updateServerCount(event.getJDA());
        Logger.logger.logGeneral("------------SYSTEM READY---------------\r\n");
        Logger.started = true;
        ready = true;
    }


    @SuppressWarnings("Duplicates")
    private void onMessageReceived(MessageReceivedEvent event) {
        //locales generation (dynamic strings from file selectionable by language)
        ResourceBundle output = ResourceBundle.getBundle("messages");
        if (!initializing.contains(event.getGuild()))
            if (checkConnection()) {
                Guild guild = event.getGuild();

                try {
                    dbExecutor.submit(() ->
                            dbInterface.updateDatabase(guild, output)
                    ).get();

                    //name of sender server
                    String guildname = event.getGuild().getName();
                    //get sender member
                    Member member = event.getMember();
                    //get channel to send
                    TextChannel channel = (TextChannel) event.getChannel();
                    //get message
                    Message message = event.getMessage();
                    //get id
                    long messageId = message.getIdLong();

                    boolean isAdmin = dbExecutor.submit(() ->
                            dbInterface.memberIsAdmin(member, guild.getIdLong())
                    ).get();

                    if (!PermissionUtil.checkPermission(channel, guild.getSelfMember(), Permission.MESSAGE_EMBED_LINKS)) {
                        channel.sendMessage("Missing permission EMBED_LINKS please fix").queue();
                        return;
                    }

                    String args[] = message.getContentDisplay().split(" +");
                    String command = args[0].substring(System.getenv("BOT_PREFIX").length());
                    switch (command) {
//------USER---------------------HELP--------------------------------------

                        case "help":
                            Logger.logger.logMessage("help", message);
                            PrintHelp(channel, member, guild, isAdmin);
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
//------USER-------------------SUPPORT---------------------------------------
                        case "support":
                            Logger.logger.logMessage("support", message);
                            channel.sendMessage(output.getString("support-msg")).queue();
                            Logger.logger.logReponse("support link sent", guild, messageId);
                            break;
//------USER-------------------OTHER---------------------------------------
                        case "other":
                            Logger.logger.logMessage("other", message);
                            channel.sendMessage(output.getString("other-msg")).queue();
                            Logger.logger.logReponse("other bots sent", guild, messageId);
                            break;
                        default:
//------OWNER--------------RELOAD--------------------------------------------
                            //if it's bot owner

                            if (member.getUser().getIdLong() == Long.parseLong(System.getenv("OWNER_ID"))) {
                                if (command.equals("reload")) {
                                    channel.sendMessage(output.getString("reload-started")).queue();
                                    dbExecutor.submit(() -> dbInterface.rePopolateDb(event.getJDA())).get();
                                    channel.sendMessage(output.getString("reload-ended")).queue();
                                    break;
                                }
                                if (command.equals("persistence")) {
                                    if(args.length==2){
                                        long id = Long.parseLong(args[1]);
                                        int pers = dbExecutor.submit(() -> dbInterface.getPersistence(id)).get();
                                        channel.sendMessage(""+ pers).queue();
                                    }else if(args.length==3){
                                        long id = Long.parseLong(args[1]);
                                        int pers = Integer.parseInt(args[2]);
                                        dbExecutor.submit(() -> dbInterface.updatePersistence(id,pers));
                                        channel.sendMessage(""+ pers).queue();
                                    }
                                    break;
                                }
                            }

                            //if the member is allowed
                            if (member.isOwner() || isAdmin) {
                                switch (command) {
//------ADMIN---------------ADMIN---------------------------------------
                                    case "admin": {
                                        //if there are other arguments
                                        if (args.length > 1) {
                                            //get mentioned roles
                                            List<Role> mentions = message.getMentionedRoles();
                                            Role role = null;
                                            if (mentions.size() >= 1)
                                                role = mentions.get(0);

                                            //test on second arg
                                            switch (args[1]) {
                                                case "add":
                                                    //if there is a mentioned role
                                                    if (role == null)
                                                        if (args.length > 2) {
                                                            try {
                                                                role = guild.getRoleById(args[2]);
                                                            } catch (NumberFormatException ex) {
                                                                channel.sendMessage(output.getString("error-roleId-invalid")).queue();
                                                            }
                                                        } else {
                                                            channel.sendMessage(output.getString("error-missing-param")).queue();
                                                        }

                                                    Logger.logger.logMessage("admin add", message);
                                                    if (role != null) {
                                                        //call class method to add roles
                                                        final Role rl = role;
                                                        String msg = dbExecutor.submit(() ->
                                                                dbInterface.addAdmin(rl, guild, output, messageId)
                                                        ).get();
                                                        channel.sendMessage(msg).queue();
                                                    }
                                                    break;
                                                case "remove":
                                                    //if there is a mentioned user
                                                    if (role == null)
                                                        if (args.length > 2) {
                                                            try {
                                                                role = guild.getRoleById(args[2]);
                                                            } catch (NumberFormatException ex) {
                                                                channel.sendMessage(output.getString("error-roleId-invalid")).queue();
                                                            }
                                                        } else {
                                                            channel.sendMessage(output.getString("error-missing-param")).queue();
                                                        }

                                                    Logger.logger.logMessage("admin remove", message);
                                                    if (role != null) {
                                                        //call class method to remove roles
                                                        final Role rl = role;
                                                        String msg = dbExecutor.submit(() ->
                                                                dbInterface.removeAdmin(rl, guild, output, messageId)
                                                        ).get();
                                                        channel.sendMessage(msg).queue();
                                                    }
                                                    break;
                                                case "clear": {
                                                    Logger.logger.logMessage("admin clear", message);
                                                    String msg = dbExecutor.submit(() ->
                                                            dbInterface.clearAdmin(guild, output, messageId)
                                                    ).get();
                                                    channel.sendMessage(msg).queue();
                                                    Logger.logger.logReponse("admins cleared", guild, messageId);
                                                    break;
                                                }
                                                case "auto": {
                                                    Logger.logger.logMessage("admin auto", message);
                                                    dbExecutor.submit(() -> dbInterface.autoRole(event.getGuild())).get();
                                                    channel.sendMessage(output.getString("admin-auto")).queue();
                                                    Logger.logger.logReponse("admins updated", guild, messageId);
                                                }
                                                case "list": {
                                                    //list all mods
                                                    Logger.logger.logMessage("admin list", message);
                                                    String msg = dbExecutor.submit(() ->
                                                            dbInterface.listAdmin(guild, output, messageId)
                                                    ).get();
                                                    SendMsg(channel, msg);
                                                    break;
                                                }
                                            }

                                        }

                                        break;
                                    }
//------ADMIN---------------ROLES----------------------------------------
                                    case "roles": {
                                        Logger.logger.logMessage("roles", message);
                                        StringBuilder ret = new StringBuilder();
                                        ret.append(output.getString("roles-head")).append("\n");
                                        for (Role r : guild.getRoles()) {
                                            if (!r.isPublicRole())
                                                ret.append(r.getName()).append(" [").append(r.getId()).append("]\n");
                                        }
                                        SendMsg(channel, ret.toString());
                                        Logger.logger.logReponse("role list shown", guild, messageId);
                                        break;
                                    }
//------ADMIN---------------FORGIVE---------------------------------------
                                    case "forget": {
                                        //if there are other arguments
                                        Logger.logger.logMessage("forgive", message);
                                        if (args.length > 1) {
                                            //get mentioned roles
                                            try {
                                                long id = Long.parseLong(args[1]);
                                                String sql = "";
                                                if (guild.getMemberById(id) == null) {
                                                    String ret = dbExecutor.submit(() -> dbInterface.forgetUser(output, guild, id, sql)).get();
                                                    channel.sendMessage(ret).queue();
                                                } else
                                                    channel.sendMessage(output.getString("error-forget-mutual")).queue();
                                            } catch (NumberFormatException ex) {
                                                channel.sendMessage(output.getString("error-non-id")).queue();
                                            }
                                        }
                                        break;
                                    }
//------ADMIN--------------CHANNEL-----------------------------------------
                                    case "wbchannel": {
                                        //if there are other arguments
                                        Logger.logger.logMessage("wbchannel", message);
                                        //get mentioned roles
                                        String ret;
                                        List<TextChannel> mentions = message.getMentionedChannels();
                                        if (mentions.size() == 0)
                                            ret = dbExecutor.submit(() -> dbInterface.changeChannel(guild, output, null, messageId)).get();
                                        else
                                            ret = dbExecutor.submit(() -> dbInterface.changeChannel(guild, output, mentions.get(0), messageId)).get();

                                        channel.sendMessage(ret).queue();
                                        break;
                                    }
                                    case "wbtest": {
                                        //if there are other arguments
                                        Logger.logger.logMessage("wbtest", message);
                                        //get mentioned roles
                                        TextChannel wbChannel = dbExecutor.submit(() -> dbInterface.getChannel(guild)).get();
                                        try {
                                            wbChannel.sendMessage(output.getString("test-message")).queue();
                                            channel.sendMessage(output.getString("test-message-sent").replace("[channel]", wbChannel.getAsMention())).queue();
                                        } catch (InsufficientPermissionException ex) {
                                            channel.sendMessage(output.getString("error-missing-permission").replace("[channel]", wbChannel.getAsMention()) + "\n" + ex.getMessage()).queue();
                                        }
                                        Logger.logger.logReponse("test message sent", guild, messageId);
                                        break;
                                    }
//------ADMIN--------------DELAY---------------------------------------------
                                    case "delay": {
                                        //if there are other arguments
                                        Logger.logger.logMessage("delay", message);
                                        if (args.length > 1) {
                                            try {
                                                float delay = Float.parseFloat(args[1]);
                                                if (delay < 0)
                                                    throw new NumberFormatException("Negative values not allowed");
                                                String ret = dbExecutor.submit(() -> dbInterface.changeDelay(guild, output, delay, messageId)).get();
                                                channel.sendMessage(ret).queue();
                                            } catch (NumberFormatException ex) {
                                                channel.sendMessage(output.getString("error-number")).queue();
                                            }
                                        }
                                        break;
                                    }

//------ADMIN---------------LIMITROLE-----------------------------------------
                                    case "limitrole": {
                                        Role myrole = guild.getSelfMember().getRoles().stream().filter(Role::isManaged).max(Comparator.comparingLong(Role::getPosition)).orElse(guild.getPublicRole());
                                        channel.sendMessage(output.getString("limitrole-text")+" "+ myrole.getAsMention()).queue();
                                        break;
                                    }
//------ADMIN---------------DELETE-----------------------------------------
                                    case "delete":{
                                        MessageHistory history = channel.getHistory();
                                        history.retrievePast(10).complete();
                                        history.getRetrievedHistory().stream().filter(m -> m.getMember().equals(guild.getSelfMember()))
                                        .max(Comparator.comparing(Message::getCreationTime)).ifPresent(m -> m.delete().queue());
                                        break;
                                    }
                                }


                            } else {
                                Logger.logger.logMessage("forgive", message);
                                channel.sendMessage(output.getString("error-user-permission")).queue();
                                Logger.logger.logReponse("user not allowed", guild, messageId);
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


    private void onMemberRoleAdded(GuildMemberRoleAddEvent event) {
        ResourceBundle output = ResourceBundle.getBundle("messages");
        if (!initializing.contains(event.getGuild()))
            try {
                if (checkConnection()) {
                    Guild guild = event.getGuild();

                    dbExecutor.submit(() -> dbInterface.updateDatabase(guild, output)).get();
                    //get sender member
                    Member member = event.getMember();

                    User user = member.getUser();

                    List<Role> roles = event.getRoles();

                    if (!restoring.contains(event.getUser()))
                        dbExecutor.execute(() -> dbInterface.memorizeRole(guild, user, roles));
                } else {
                    event.getJDA().shutdown();
                    Reconnector.reconnect();
                }
            } catch (InterruptedException ignored) {
            } catch (Exception e) {
                e.printStackTrace();
            }

    }


    private void onMemberRoleRemoved(GuildMemberRoleRemoveEvent event) {
        ResourceBundle output = ResourceBundle.getBundle("messages");
        if (!initializing.contains(event.getGuild()))
            try {
                if (checkConnection()) {
                    Guild guild = event.getGuild();

                    dbExecutor.submit(() -> dbInterface.updateDatabase(guild, output)).get();
                    //get sender member
                    Member member = event.getMember();

                    User user = member.getUser();

                    List<Role> roles = event.getRoles();

                    if (!restoring.contains(event.getUser()))
                        dbExecutor.execute(() -> dbInterface.removeRole(guild, user, roles));
                } else {
                    event.getJDA().shutdown();
                    Reconnector.reconnect();
                }
            } catch (InterruptedException ignored) {
            } catch (Exception e) {
                e.printStackTrace();
            }
    }


    private void onMemberNick(GuildMemberNickChangeEvent event) {
        ResourceBundle output = ResourceBundle.getBundle("messages");
        if (!initializing.contains(event.getGuild()))
            try {
                if (checkConnection()) {
                    Guild guild = event.getGuild();

                    dbExecutor.submit(() -> dbInterface.updateDatabase(guild, output)).get();
                    //get sender member
                    Member member = event.getMember();

                    User user = member.getUser();

                    String nick = member.getEffectiveName();
                    if (!restoring.contains(user)) {
                        dbExecutor.execute(() -> dbInterface.updateNick(guild, user, nick));
                    }
                } else {
                    event.getJDA().shutdown();
                    Reconnector.reconnect();
                }
            } catch (InterruptedException ignored) {
            } catch (Exception e) {
                e.printStackTrace();
            }
    }

    private void onMemberUsername(UserUpdateNameEvent event) {
        try {
            if (checkConnection()) {
                User user = event.getUser();
                Thread.sleep(1000);
                String oldUname = event.getOldName();
                if (!restoring.contains(user)) {
                    dbExecutor.execute(() -> dbInterface.updateUname(user, oldUname));
                }
            } else {
                event.getJDA().shutdown();
                Reconnector.reconnect();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private void onMemberJoin(GuildMemberJoinEvent event) {
        ResourceBundle output = ResourceBundle.getBundle("messages");
        if (!initializing.contains(event.getGuild()))
            try {
                if (checkConnection()) {

                    Guild guild = event.getGuild();

                    dbExecutor.submit(() -> dbInterface.updateDatabase(guild, output)).get();
                    //get sender member
                    Member member = event.getMember();

                    User user = member.getUser();

                    GuildController gc = new GuildController(guild);
                    //wait till other bots act
                    try {
                        //act
                        List<Role> roles = new ArrayList<>();
                        int out = dbExecutor.submit(() -> dbInterface.restoreUser(guild, member, user, gc, roles)).get();
                        if (out > 0) {
                            Logger.logger.logUserEvent("JOINED - RESTORED", guild, user);
                            try {
                                String msg = output.getString("restore").replace("[mention]", member.getAsMention()).replace("[uname]", member.getEffectiveName());
                                TextChannel channel = dbExecutor.submit(() -> dbInterface.getChannel(guild)).get();
                                channel.sendMessage(msg + "\n" +
                                        (out > 1 ? output.getString("restored-muted") : "")).queue();
                            } catch (Exception ignore) {
                            }
                            float delay = dbExecutor.submit(() -> dbInterface.getDelay(guild)).get();
                            Thread.sleep(Math.round(delay * 1000));
                            try {
                                final long maxHeight = guild.getSelfMember().getRoles().stream().filter(Role::isManaged).mapToLong(Role::getPosition).max().orElse(0);
                                List<Role> newList = roles.stream().filter(r -> {
                                    if (r.isManaged())
                                        return false;
                                    if (r.isPublicRole())
                                        return false;
                                    if (r.getPosition() >= maxHeight)
                                        return false;
                                    return true;
                                }).collect(Collectors.toList());

                                newList.addAll(member.getRoles().stream().filter(
                                        r ->
                                                r.getPosition() >= maxHeight || r.isManaged()
                                ).collect(Collectors.toList()));

                                gc.modifyMemberRoles(member, newList).reason("Role restore").queue();


                            } catch (Exception ignored) {
                            }
                        } else
                            Logger.logger.logUserEvent("JOINED", guild, user);

                        dbExecutor.submit(() -> dbInterface.memUname(member)).get();
                        dbExecutor.submit(() -> dbInterface.baseRole(guild, user)).get();
                        dbExecutor.submit(() -> dbInterface.updateNick(guild, user, user.getName())).get();
                        restoring.remove(user);
                        dbExecutor.execute(() -> dbInterface.memorizeRole(guild, user, member.getRoles()));
                    } catch (InterruptedException ignored) {

                    }
                } else {
                    event.getJDA().shutdown();
                    Reconnector.reconnect();
                }
            } catch (InterruptedException ignored) {
            } catch (Exception e) {
                e.printStackTrace();
            }
    }


    private void onMemberLeave(GuildMemberLeaveEvent event) {
        ResourceBundle output = ResourceBundle.getBundle("messages");
        if (!initializing.contains(event.getGuild()))
            try {
                if (checkConnection()) {
                    Guild guild = event.getGuild();

                    dbExecutor.submit(() -> dbInterface.updateDatabase(guild, output)).get();
                    //get sender member
                    Member member = event.getMember();

                    User user = member.getUser();

                    dbExecutor.submit(() -> dbInterface.saveUser(guild, user)).get();
                    Logger.logger.logUserEvent("LEAVED", guild, user);

                } else {
                    event.getJDA().shutdown();
                    Reconnector.reconnect();
                }
            } catch (InterruptedException ignored) {
            } catch (Exception e) {
                e.printStackTrace();
            }
    }


    private void onRoleDelete(RoleDeleteEvent event) {
        ResourceBundle output;
        if (!initializing.contains(event.getGuild()))
            try {
                if (checkConnection()) {
                    output = ResourceBundle.getBundle("messages");

                    if (dbExecutor.submit(() -> dbInterface.onRoleDeleted(event.getRole())).get()) {
                        Logger.logger.logEvent("role deleted in guild: ", event.getGuild());
                        try {
                            TextChannel channel = dbExecutor.submit(() -> dbInterface.getChannel(event.getGuild())).get();
                            channel.sendMessage(output.getString("event-role-deleted")).queue();
                            channel.sendMessage(output.getString("event-auto-message")).queue();
                        } catch (InsufficientPermissionException ex) {
                            try {
                                event.getGuild().getOwner().getUser().openPrivateChannel().queue((PrivateChannel channel) ->
                                {
                                    channel.sendMessage(output.getString("event-role-deleted")).queue();
                                    channel.sendMessage(output.getString("event-auto-message")).queue();
                                });
                            } catch (Exception ignored) {
                            }
                        }

                    }
                } else {
                    event.getJDA().shutdown();
                    Reconnector.reconnect();
                }
            } catch (InterruptedException ignored) {
            } catch (Exception e) {
                e.printStackTrace();
            }
    }

    private void onChannelDelete(TextChannelDeleteEvent event) {
        ResourceBundle output;
        try {
            if (checkConnection()) {
                output = ResourceBundle.getBundle("messages");
                if (dbExecutor.submit(() -> dbInterface.onChannelDeleted(event.getChannel())).get()) {
                    Logger.logger.logEvent("channel deleted in guild: ", event.getGuild());
                    try {
                        TextChannel channel = dbExecutor.submit(() -> dbInterface.getChannel(event.getGuild())).get();
                        channel.sendMessage(output.getString("event-channel-deleted")).queue();
                        channel.sendMessage(output.getString("event-auto-message")).queue();
                    } catch (InsufficientPermissionException ex) {
                        try {
                            event.getGuild().getOwner().getUser().openPrivateChannel().queue((PrivateChannel channel) ->
                            {
                                channel.sendMessage(output.getString("event-channel-deleted")).queue();
                                channel.sendMessage(output.getString("event-auto-message")).queue();
                            });
                        } catch (Exception ignored) {
                        }
                    }

                }
            } else {
                event.getJDA().shutdown();
                Reconnector.reconnect();
            }
        } catch (InterruptedException ignored) {
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private void onGuildJoin(GuildJoinEvent event) {
        ResourceBundle output = ResourceBundle.getBundle("messages");
        Logger.logger.logEvent("GUILD HAS JOINED", event.getGuild());
        initializing.add(event.getGuild());
        try {
            if (!dbExecutor.submit(() -> dbInterface.guildIsInDb(event.getGuild())).get()) {
                try {
                    Optional.ofNullable(event.getGuild().getSystemChannel()).orElse(event.getGuild().getDefaultChannel()).sendMessage(output.getString("event-join").replace("[version]", Global.version)).queue();
                } catch (InsufficientPermissionException ex) {
                    try {
                        event.getGuild().getOwner().getUser().openPrivateChannel().queue((PrivateChannel channel) ->
                                channel.sendMessage(output.getString("event-join").replace("[version]", Global.version)).queue());
                    } catch (Exception ignored) {
                    }
                }
                dbExecutor.submit(() -> dbInterface.newGuild(event.getGuild())).get();
                dbExecutor.submit(() -> dbInterface.autoRole(event.getGuild())).get();
            }
        } catch (InterruptedException ignored) {
        } catch (Exception e) {
            e.printStackTrace();
        }
        updateServerCount(event.getJDA());
        initializing.remove(event.getGuild());
    }

    private void onGuildLeave(GuildLeaveEvent event) {
        Logger.logger.logEvent("GUILD HAS LEAVED", event.getGuild());
        try {
            dbExecutor.submit(() -> dbInterface.guildLeave(event.getGuild())).get();
        } catch (InterruptedException ignored) {
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
        updateServerCount(event.getJDA());
    }


    //prints the help message
    private void PrintHelp(MessageChannel channel, Member member, Guild guild, boolean admin) {
        ResourceBundle output = ResourceBundle.getBundle("messages");
        EmbedBuilder helpMsg = new EmbedBuilder();
        helpMsg.setColor(Color.GREEN);
        //help is dynamic (different for every user)
        helpMsg.setTitle(output.getString("help-title"));
        helpMsg.setDescription(output.getString("help-description"));
        helpMsg.addField("help", output.getString("help-def-help"), false);
        helpMsg.addField("ping", output.getString("help-def-ping"), false);
        helpMsg.addField("support", output.getString("help-def-support"), false);
        helpMsg.addField("other", output.getString("help-def-other"), false);

        //if is allowed to use mod commands
        if (member.isOwner() || admin) {
            helpMsg.addBlankField(false);
            helpMsg.addField("ADMIN commands:", "", false);

            helpMsg.addField("admin", output.getString("help-def-admin"), false);

            helpMsg.addField("roles", output.getString("help-def-roles"), false);

            helpMsg.addField("forget", output.getString("help-def-forget"), false);

            helpMsg.addField("wbchannel", output.getString("help-def-channel"), false);

            helpMsg.addField("wbtest", output.getString("help-def-channel-test"), false);

            helpMsg.addField("delay", output.getString("help-def-delay"), false);

            helpMsg.addField("limitrole", output.getString("help-def-limit"), false);

            helpMsg.addField("delete", output.getString("help-def-delete"), false);
        }

        helpMsg.addField("", output.getString("help-last"), false);

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
        SendMsg(channel, text, null);
    }

    @SuppressWarnings("all")
    private void SendMsg(MessageChannel channel, String text, String codeStyle) {
        boolean codeBlock = codeStyle != null;
        long messages = Math.round((Math.ceil(text.length() / 1000.0)));
        if (messages > 1) {
            int s = 0;
            int p = s, a;
            while (p != text.length()) {
                a = s;
                while ((a - s) < 1000 & a != -1) {
                    p = a;
                    a = text.indexOf("\n", p + 1);
                }
                if (a == -1)
                    p = text.length();
                if (p > s)
                    channel.sendMessage(
                            ((codeBlock) ? "```" + codeStyle + "\n" : "") +
                                    text.substring(s, p) +
                                    ((codeBlock) ? "\n```" : "")
                    ).queue();
                s = p;
            }
        } else {
            channel.sendMessage(text).queue();
        }
    }

    public MyListener(Connection conn) {
        this.conn = conn;
        this.dbInterface = new DbInterface(conn);
    }

    public void close() {
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


    static public class PausableSingleThreadExecutor extends ThreadPoolExecutor {
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
            return !isPaused && ctn == 0;
        }

        public boolean isPaused() {
            return isPaused && ctn == 1;
        }

        /**
         * Pause the execution
         */
        public void pause() throws InterruptedException {
            lock.lock();
            try {
                isPaused = true;
            } finally {
                lock.unlock();
            }
            while (ctn == 0) {
                if (Thread.currentThread().isInterrupted())
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
