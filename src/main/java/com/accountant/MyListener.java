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
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.stream.Collector;
import java.util.stream.Collectors;


import static org.fusesource.jansi.Ansi.ansi;

public class MyListener implements EventListener {
    private Connection conn;
    private BotGuild botGuild;
    private ExecutorService threads= Executors.newSingleThreadExecutor(new MyThreadFactory());

    private class MyThreadFactory implements ThreadFactory{
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
                threads.execute(() -> onMessageReceived((MessageReceivedEvent) event));
        }
        else if (event instanceof RoleDeleteEvent)
            threads.execute(() -> onRoleDelete((RoleDeleteEvent) event));

        else if (event instanceof GuildJoinEvent)
            threads.execute(() -> onGuildJoin((GuildJoinEvent) event));
        else if (event instanceof GuildLeaveEvent)
            threads.execute(() -> onGuildLeave((GuildLeaveEvent) event));


        else if (event instanceof GuildMemberRoleAddEvent)
            threads.execute(() -> onMemberRoleAdded((GuildMemberRoleAddEvent) event));
        else if (event instanceof GuildMemberRoleRemoveEvent)
            threads.execute(() -> onMemberRoleRemoved((GuildMemberRoleRemoveEvent) event));


        else if (event instanceof GuildMemberJoinEvent)
            threads.execute(() -> onMemberJoin((GuildMemberJoinEvent) event));
        else if (event instanceof GuildMemberLeaveEvent)
            threads.execute(() -> onMemberLeave((GuildMemberLeaveEvent) event));
        else if (event instanceof GuildMemberNickChangeEvent)
            threads.execute(() -> onMemberNick((GuildMemberNickChangeEvent) event));
    }

    private void onReady(ReadyEvent event) {
        String sql = "";
        List<Guild> guilds = event.getJDA().getSelfUser().getMutualGuilds();
        Statement stmt1, stmt2;
        ResultSet rs;
            try {
                stmt1 = conn.createStatement();
                stmt2 = conn.createStatement();
                sql = "SELECT guildid FROM guilds";
                rs = stmt1.executeQuery(sql);
                while (rs.next()) {
                    boolean found = false;
                    for (Guild guild : guilds) {
                        if (guild.getIdLong() == rs.getLong(1)) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        sql = "DELETE FROM roles WHERE guildid=" + rs.getLong(1);
                        stmt2.execute(sql);
                        sql = "DELETE FROM guilds WHERE guildid=" + rs.getLong(1);
                        stmt2.execute(sql);
                        stmt2.getConnection().commit();
                    }
                }
                stmt2.close();
                stmt1.close();
            } catch (SQLException ex) {
                try {
                    conn.rollback();
                } catch (SQLException ignored) {
                }
                Logger.logger.logError("SQLError in : " + sql);
                Logger.logger.logError("SQLException: " + ex.getMessage());
                Logger.logger.logError("SQLState: " + ex.getSQLState());
                Logger.logger.logError("VendorError: " + ex.getErrorCode());

            }
        updateServerCount(event.getJDA());
        Logger.logger.logGeneral("------------SYSTEM READY---------------\r\n");
        Logger.started = true;
    }


    private void onMessageReceived(MessageReceivedEvent event) {
        //locales generation (dynamic strings from file selectionable by language)
        ResourceBundle output = ResourceBundle.getBundle("messages");
        if (checkConnection()) {
            Guild guild = event.getGuild();

            updateDatabase(guild, output);
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
                            PrintHelp(channel, member, guild);
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
//------MOD------------------MODROLE---------------------------------------
                        case "modrole":
                            channel.sendTyping().queue();
                            //if member is allowed
                            if (member.isOwner() || botGuild.memberIsMod(member, guild.getIdLong())) {
                                //if there are other arguments
                                if (args.length > 1) {
                                    //get mentioned roles
                                    List<Role> mentions = message.getMentionedRoles();
                                    //test on second arg
                                    switch (args[1]) {
                                        case "add":
                                            //if there is a mentioned role
                                            Logger.logger.logMessage("modrole add", message);
                                            if (mentions.size() >= 1) {
                                                //call class method to add roles
                                                channel.sendMessage(botGuild.addModRole(mentions.get(0), guild, output, messageId)).queue();
                                            }
                                            break;
                                        case "remove":
                                            //if there is a mentioned user
                                            Logger.logger.logMessage("modrole remove", message);
                                            if (mentions.size() >= 1) {
                                                //call class method to remove roles
                                                channel.sendMessage(botGuild.removeModRole(mentions.get(0), guild, output, messageId)).queue();
                                            }
                                            break;
                                        case "clear":
                                            Logger.logger.logMessage("modrole clear", message);
                                            channel.sendMessage(botGuild.clearModrole(guild, output, messageId)).queue();
                                            Logger.logger.logReponse("modroles cleared", guild, messageId);
                                            break;
                                        case "auto":
                                            Logger.logger.logMessage("modrole auto", message);
                                            botGuild.autoModRole(event.getGuild());
                                            channel.sendMessage(output.getString("modrole-auto")).queue();
                                            Logger.logger.logReponse("modroles updated", guild, messageId);
                                        case "list":
                                            //list all modroles
                                            Logger.logger.logMessage("modrole list", message);
                                            SendMsg(channel, botGuild.listModrole(guild, output, messageId));
                                            break;
                                    }

                                }
                                break;
                            } else {
                                Logger.logger.logMessage("modrole", message);
                                channel.sendMessage(output.getString("error-user-permission")).queue();
                                Logger.logger.logReponse("user not allowed", guild, messageId);
                            }
                            break;
                        case "forgive":
                            channel.sendTyping().queue();
                            //if member is allowed
                            if (member.isOwner() || botGuild.memberIsMod(member, guild.getIdLong())) {
                                //if there are other arguments
                                if (args.length > 1) {
                                    //get mentioned roles
                                    List<User> mentions = message.getMentionedUsers();
                                    //test on mentions
                                    if(mentions.size()>0) {
                                        String sql = "";
                                        if(guild.getMemberById(mentions.get(0).getIdLong())==null) {
                                                try {
                                                    PreparedStatement stmt = conn.prepareStatement("DELETE FROM MemberRoles WHERE guildid=? AND userId=?");
                                                    stmt.setLong(1, guild.getIdLong());
                                                    stmt.setLong(2, mentions.get(0).getIdLong());
                                                    stmt.executeUpdate();
                                                    conn.commit();
                                                    stmt.close();
                                                    channel.sendMessage(output.getString("forgive-done")).queue();
                                                } catch (SQLException ex) {
                                                    sqlError(sql, ex);
                                                }
                                        }else
                                            channel.sendMessage(output.getString("error-forgive-mutual")).queue();
                                    }
                                }
                                break;
                            } else {
                                Logger.logger.logMessage("modrole", message);
                                channel.sendMessage(output.getString("error-user-permission")).queue();
                                Logger.logger.logReponse("user not allowed", guild, messageId);
                            }
                            break;
                        default:
                            if (guildIsSupport(guild))
                                switch (command) {
                                    case "console":
                                        Logger.logger.logMessage("console", message);
                                        if (member.isOwner() || botGuild.memberIsMod(member, guild.getIdLong())) {
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
                                        if (member.isOwner() || botGuild.memberIsMod(member, guild.getIdLong())) {
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
                                    rePopolateDb(event);
                                }
                            }

                    }
                }
            }
        } else {
            event.getJDA().shutdown();
            Reconnector.reconnect();
        }

    }

    private void rePopolateDb(MessageReceivedEvent event) {
        String sql = "";
            try {
                sql = "DELETE FROM MemberRoles WHERE expireDate IS NULL";
                PreparedStatement stmt1 = conn.prepareStatement("DELETE FROM MemberRoles WHERE expireDate IS NULL");
                stmt1.executeUpdate();
                conn.commit();
                stmt1.close();
                final String sqli = sql = "INSERT INTO MemberRoles(guildId, userId, roleId) VALUES (";
                final PreparedStatement stmt = conn.prepareStatement("INSERT INTO MemberRoles(guildId, userId, roleId) VALUES (?,?,?)");
                event.getJDA().getGuilds().stream().flatMap(a -> a.getMembers().stream()).forEach(m -> {
                    String sql1 = "";
                    int ctn = 0;
                    try {
                        stmt.setLong(1, m.getGuild().getIdLong());
                        stmt.setLong(2, m.getUser().getIdLong());
                        for (Role role : m.getRoles()) {
                            sql1 = sqli + m.getGuild().getId() + "," + m.getUser().getId() + "," + role.getId() + ")";
                            stmt.setLong(3, role.getIdLong());
                            ctn += stmt.executeUpdate();
                        }
                        if (ctn > 0)
                            stmt.getConnection().commit();
                    } catch (SQLException ex) {
                        sqlError(sql1, ex);
                    }
                });
                stmt.close();
            } catch (SQLException ex) {
                sqlError(sql, ex);
            }
    }


    private void onMemberRoleAdded(GuildMemberRoleAddEvent event){
        ResourceBundle output = ResourceBundle.getBundle("messages");
        if (checkConnection()) {
            Guild guild = event.getGuild();

            updateDatabase(guild, output);
            //get sender member
            Member member = event.getMember();

            User user = member.getUser();

            List<Role> roles = event.getRoles();

            String partsql="INSERT INTO MemberRoles(guildId, userId, roleId) VALUES ("+guild.getId()+","+user.getId()+",";
            String sql="";

            int ctn=0;
                try {
                    PreparedStatement stmt = conn.prepareStatement("INSERT INTO MemberRoles(guildId, userId, roleId) VALUES (?,?,?)");
                    stmt.setLong(1, guild.getIdLong());
                    stmt.setLong(2, user.getIdLong());
                    for (Role role : roles) {
                        sql = partsql + role.getId() + ")";
                        stmt.setLong(3, role.getIdLong());
                        ctn += stmt.executeUpdate();
                    }
                    if (ctn > 0)
                        stmt.getConnection().commit();
                    stmt.close();
                } catch (SQLException ex) {
                    sqlError(sql, ex);
                }
        } else {
            event.getJDA().shutdown();
            Reconnector.reconnect();
        }

    }

    private void onMemberRoleRemoved(GuildMemberRoleRemoveEvent event){
        ResourceBundle output = ResourceBundle.getBundle("messages");
        if (checkConnection()) {
            Guild guild = event.getGuild();

            updateDatabase(guild, output);
            //get sender member
            Member member = event.getMember();

            User user = member.getUser();

            List<Role> roles = event.getRoles();

            String partsql="UPDATE MemberRoles SET expireDate="+Timestamp.valueOf(LocalDateTime.now().plus(1,ChronoUnit.MINUTES))+" WHERE guildId="+guild.getId()+" AND userId="+user.getId()+" AND roleId=";
            String sql="";

                try {
                    PreparedStatement stmt = conn.prepareStatement("UPDATE MemberRoles SET expireDate=? WHERE guildId=? AND userId=? AND roleId=? AND expireDate IS NULL");
                    stmt.setString(1, Timestamp.valueOf(LocalDateTime.now().plus(1, ChronoUnit.MINUTES)).toString());
                    stmt.setLong(2, guild.getIdLong());
                    stmt.setLong(3, user.getIdLong());
                    int ctn = 0;
                    for (Role role : roles) {
                        sql = partsql + role.getId();
                        stmt.setLong(4, role.getIdLong());
                        ctn += stmt.executeUpdate();
                    }
                    if (ctn > 0)
                        stmt.getConnection().commit();
                    stmt.close();
                } catch (SQLException ex) {
                    sqlError(sql, ex);
                }
        } else {
            event.getJDA().shutdown();
            Reconnector.reconnect();
        }
    }

    private void onMemberNick(GuildMemberNickChangeEvent event){
        ResourceBundle output = ResourceBundle.getBundle("messages");
        if (checkConnection()) {
            Guild guild = event.getGuild();

            updateDatabase(guild, output);
            //get sender member
            Member member = event.getMember();

            User user = member.getUser();

            String nick = event.getNewNick();

            String partsql="UPDATE MemberRoles SET expireDate="+Timestamp.valueOf(LocalDateTime.now().plus(1,ChronoUnit.MINUTES))+" WHERE guildId="+guild.getId()+" AND userId="+user.getId()+" AND roleId=";
            String sql="";

            try {
                PreparedStatement stmt = conn.prepareStatement("SELECT * FROM MemberNick WHERE guildId=? AND userId=? AND expireDate IS NULL");
                stmt.setLong(1, guild.getIdLong());
                stmt.setLong(2, user.getIdLong());
                ResultSet rs = stmt.executeQuery();
                if(rs.next()) {
                    rs.close();
                    stmt.close();
                    stmt = conn.prepareStatement("UPDATE MemberNick SET nickname=? WHERE guildId=? AND userId=? AND expireDate IS NULL");
                    stmt.setString(1, nick);
                    stmt.setLong(2, guild.getIdLong());
                    stmt.setLong(3, user.getIdLong());
                    if (stmt.executeUpdate() > 0)
                        stmt.getConnection().commit();
                    stmt.close();
                }else{
                    rs.close();
                    stmt.close();
                    stmt = conn.prepareStatement("INSERT INTO MemberNick(guildId, userId, nickname) VALUES (?,?,?)");
                    stmt.setString(3, nick);
                    stmt.setLong(1, guild.getIdLong());
                    stmt.setLong(2, user.getIdLong());
                    if (stmt.executeUpdate() > 0)
                        stmt.getConnection().commit();
                    stmt.close();
                }
            } catch (SQLException ex) {
                sqlError(sql, ex);
            }
        } else {
            event.getJDA().shutdown();
            Reconnector.reconnect();
        }
    }

    private void onMemberJoin(GuildMemberJoinEvent event){
        ResourceBundle output = ResourceBundle.getBundle("messages");
        if (checkConnection()) {
            boolean restored=false;
            boolean mute=false;
            Guild guild = event.getGuild();

            updateDatabase(guild, output);
            //get sender member
            Member member = event.getMember();

            User user = member.getUser();

            GuildController gc = new GuildController(guild);

            String sql="SELECT roleId FROM MemberRoles WHERE guildId="+guild.getId()+" AND userId="+user.getId()+" AND expireDate>"+Date.valueOf(LocalDate.now());

            List<Role> roles = new ArrayList<>();
                try {
                    PreparedStatement stmt = conn.prepareStatement("SELECT DISTINCT roleId FROM MemberRoles WHERE guildId=? AND userId=? AND expireDate>?");
                    stmt.setString(3, Timestamp.valueOf(LocalDateTime.now()).toString());
                    stmt.setLong(1, guild.getIdLong());
                    stmt.setLong(2, user.getIdLong());
                    ResultSet rs = stmt.executeQuery();
                    while (rs.next()) {
                        Role role = guild.getRoleById(rs.getLong(1));
                        if (role != null && (role.getPosition() < guild.getSelfMember().getRoles().stream().mapToInt(Role::getPosition).max().orElse(0))) {
                            if (!member.getRoles().contains(role))
                                roles.add(role);
                            restored = true;
                            if(role.getName().matches(".*[Mm][Uu][Tt][Ee][Dd].*"))
                                mute=true;
                        }
                    }
                    rs.close();
                    stmt.close();

                try {
                    gc.addRolesToMember(member, roles).queue();
                } catch (Exception ignored) {}

                    sql="SELECT nickname FROM MemberNicks WHERE guildId="+guild.getId()+" AND userId="+user.getId()+" AND expireDate>"+Date.valueOf(LocalDate.now());

                    stmt = conn.prepareStatement("SELECT DISTINCT nickname FROM MemberNick WHERE guildId=? AND userId=? AND expireDate>?");
                    stmt.setString(3, Timestamp.valueOf(LocalDateTime.now()).toString());
                    stmt.setLong(1, guild.getIdLong());
                    stmt.setLong(2, user.getIdLong());
                    rs = stmt.executeQuery();
                    while (rs.next()) {
                        try {
                            gc.setNickname(member, rs.getString(1)).queue();
                            restored=true;
                        }catch (Exception ignored){}
                    }
                    rs.close();
                    stmt.close();

                    int ctn=0;
                    stmt = conn.prepareStatement("DELETE FROM MemberRoles WHERE guildid=? AND userId=? AND expireDate NOT NULL ");
                    stmt.setLong(1, guild.getIdLong());
                    stmt.setLong(2, user.getIdLong());
                    ctn+=stmt.executeUpdate();
                    stmt.close();
                    stmt = conn.prepareStatement("DELETE FROM MemberNick WHERE guildid=? AND userId=? AND expireDate NOT NULL ");
                    stmt.setLong(1, guild.getIdLong());
                    stmt.setLong(2, user.getIdLong());
                    ctn+=stmt.executeUpdate();
                    if(ctn>0)
                        conn.commit();
                    stmt.close();
                } catch (SQLException ex) {
                    sqlError(sql, ex);
                }
            if(restored) {
                Logger.logger.logEvent("User " + member.getEffectiveName() + " JOINED - RESTORED", guild);
                try {
                    EmbedBuilder eb = new EmbedBuilder();
                    eb.setAuthor(member.getEffectiveName(),null,member.getUser().getAvatarUrl());
                    eb.setDescription(roles.stream().map(Role::getAsMention).reduce("",(a,b)->a+" "+b));
                    event.getGuild().getDefaultChannel().sendMessage( output.getString("restore").replace("[mention]",member.getAsMention()) + "\n" +
                            (mute?output.getString("restored-muted"):"")).queue();
                    //event.getGuild().getDefaultChannel().sendMessage(eb.build()).queue();
                } catch (Exception ignore){}
            }
            else
                Logger.logger.logEvent("User "+member.getEffectiveName()+" JOINED",guild);

        } else {
            event.getJDA().shutdown();
            Reconnector.reconnect();
        }
    }

    private void onMemberLeave(GuildMemberLeaveEvent event){
        ResourceBundle output = ResourceBundle.getBundle("messages");
        if (checkConnection()) {
            Guild guild = event.getGuild();

            updateDatabase(guild, output);
            //get sender member
            Member member = event.getMember();

            User user = member.getUser();

            String sql="UPDATE MemberRoles SET expireDate="+Timestamp.valueOf(LocalDateTime.now().plus(1,ChronoUnit.DAYS))+" WHERE guildId="+guild.getId()+" AND userId="+user.getId()+" AND expireDate=null";
                try {
                    int ctn=0;
                    PreparedStatement stmt = conn.prepareStatement("UPDATE MemberRoles SET expireDate=? WHERE guildId=? AND userId=? AND (expireDate IS NULL OR expireDate>?)");
                    stmt.setString(1, Timestamp.valueOf(LocalDateTime.now().plus(1, ChronoUnit.DAYS)).toString());
                    stmt.setLong(2, guild.getIdLong());
                    stmt.setLong(3, user.getIdLong());
                    stmt.setString(4, Timestamp.valueOf(LocalDateTime.now()).toString());
                    ctn+=stmt.executeUpdate();
                    stmt.close();
                    stmt = conn.prepareStatement("UPDATE MemberNick SET expireDate=? WHERE guildId=? AND userId=? AND (expireDate IS NULL OR expireDate>?)");
                    stmt.setString(1, Timestamp.valueOf(LocalDateTime.now().plus(1, ChronoUnit.DAYS)).toString());
                    stmt.setLong(2, guild.getIdLong());
                    stmt.setLong(3, user.getIdLong());
                    stmt.setString(4, Timestamp.valueOf(LocalDateTime.now()).toString());
                    ctn+=stmt.executeUpdate();
                    if (ctn > 0)
                        stmt.getConnection().commit();
                    stmt.close();
                } catch (SQLException ex) {
                    sqlError(sql, ex);
                }
            Logger.logger.logEvent("User "+member.getEffectiveName()+" LEAVED",guild);

        } else {
            event.getJDA().shutdown();
            Reconnector.reconnect();
        }
    }


    private void sqlError(String sql, SQLException ex) {
        try {
            conn.rollback();
        }catch (SQLException ignored){}
        Logger.logger.logError("SQLError in: " + sql);
        Logger.logger.logError("SQLException: " + ex.getMessage());
        Logger.logger.logError("SQLState: " + ex.getSQLState());
        Logger.logger.logError("VendorError: " + ex.getErrorCode());
    }


    private void onRoleDelete(RoleDeleteEvent event) {
        ResourceBundle output;
        if (checkConnection()) {
            output = ResourceBundle.getBundle("messages");

            if (botGuild.onRoleDeleted(event.getRole())) {
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

            String sql="DELETE FROM MemberRoles WHERE guildId="+event.getGuild().getId()+" AND roleId="+event.getRole().getId();
                try {
                    PreparedStatement stmt = conn.prepareStatement("DELETE FROM MemberRoles WHERE guildId=? AND roleId=?");
                    stmt.setLong(1, event.getGuild().getIdLong());
                    stmt.setLong(2, event.getRole().getIdLong());
                    stmt.executeUpdate();
                    stmt.getConnection().commit();
                    stmt.close();
                } catch (SQLException ex) {
                    sqlError(sql, ex);
                }
        } else {
            event.getJDA().shutdown();
            Reconnector.reconnect();
        }
    }


    private void onGuildJoin(GuildJoinEvent event) {
        ResourceBundle output = ResourceBundle.getBundle("messages");
        String sql = "";
        //search for existent informations class for server
        Logger.logger.logEvent("GUILD HAS JOINED", event.getGuild());
        try {
            event.getGuild().getDefaultChannel().sendMessage(output.getString("event-join").replace("[version]",Global.version)).queue();
        } catch (InsufficientPermissionException ex) {
            event.getGuild().getOwner().getUser().openPrivateChannel().queue((PrivateChannel channel) ->
                    channel.sendMessage(output.getString("event-join").replace("[version]",Global.version)).queue());
        }
            try {
                Statement stmt = conn.createStatement();
                sql = "INSERT INTO guilds(guildid, guildname) VALUES (" + event.getGuild().getIdLong() + ",'" + event.getGuild().getName().replaceAll("[\',\"]", "") + "')";
                if (stmt.executeUpdate(sql) > 0)
                    stmt.getConnection().commit();
                stmt.close();
            } catch (SQLException ex) {
                sqlError(sql, ex);
            }
            event.getGuild().getMembers().forEach(a -> {
                String sql2 = "";
                try {
                    int ctn = 0;
                    PreparedStatement stmt1 = conn.prepareStatement("INSERT INTO MemberRoles(guildId, userId, roleId) VALUES (?,?,?)");
                    stmt1.setLong(1, event.getGuild().getIdLong());
                    stmt1.setLong(2, a.getUser().getIdLong());
                    for (Role role : a.getRoles()) {
                        stmt1.setLong(3, role.getIdLong());
                        ctn += stmt1.executeUpdate();
                    }
                    stmt1.close();
                    stmt1 = conn.prepareStatement("INSERT INTO MemberNick(guildId, userId, nickname) VALUES (?,?,?)");
                    stmt1.setLong(1, event.getGuild().getIdLong());
                    stmt1.setLong(2, a.getUser().getIdLong());
                    stmt1.setString(3,a.getNickname());
                    ctn+=stmt1.executeUpdate();
                    if (ctn > 0)
                        conn.commit();
                    stmt1.close();
                } catch (SQLException ex) {
                    sqlError(sql2, ex);
                }
            });
        botGuild.autoModRole(event.getGuild());
        updateServerCount(event.getJDA());
    }


    private void onGuildLeave(GuildLeaveEvent event) {
        String sql = "";
        Logger.logger.logEvent("GUILD HAS LEAVED", event.getGuild());
            try {
                Statement stmt = conn.createStatement();
                sql = "DELETE FROM roles WHERE guildid=" + event.getGuild().getIdLong();
                stmt.execute(sql);
                sql = "DELETE FROM guilds WHERE guildid=" + event.getGuild().getIdLong();
                stmt.execute(sql);
                sql = "DELETE FROM MemberRoles WHERE guildid=" + event.getGuild().getIdLong();
                stmt.execute(sql);
                stmt.getConnection().commit();
                stmt.close();
            } catch (SQLException ex) {
                sqlError(sql, ex);
            }
        updateServerCount(event.getJDA());
    }

    //prints the help message
    private void PrintHelp(MessageChannel channel, Member member, Guild guild) {
        ResourceBundle output = ResourceBundle.getBundle("messages");
        EmbedBuilder helpMsg = new EmbedBuilder();
        helpMsg.setColor(Color.GREEN);
        //help is dynamic (different for every user)
        helpMsg.setTitle(output.getString("help-title"));
        helpMsg.setDescription(output.getString("help-description"));
        helpMsg.addField("help", output.getString("help-def-help"), false);
        helpMsg.addField("ping", output.getString("help-def-ping"), false);

        //if is allowed to use mod commands
        if (member.isOwner() || botGuild.memberIsMod(member, guild.getIdLong())) {
            helpMsg.addBlankField(false);
            helpMsg.addField("MOD commands:", "", false);

            helpMsg.addField("modrole", output.getString("help-def-modrole"), false);

            helpMsg.addField("forgive", output.getString("help-def-forgive"), false);

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
        return false;
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

    private void updateDatabase(Guild guild, ResourceBundle output) {
        String sql = "";
            try {
                Statement stmt1 = conn.createStatement();
                ResultSet rs;
                sql = "SELECT * FROM guilds WHERE guildid=" + guild.getIdLong();
                rs = stmt1.executeQuery(sql);
                if (rs.next()) {
                    rs.close();
                    sql = "UPDATE guilds SET guildname='" + guild.getName().replaceAll("[',\"]", "") + "' WHERE guildid=" + guild.getIdLong();
                    if (stmt1.executeUpdate(sql) > 0)
                        conn.commit();
                } else {
                    rs.close();
                    sql = "INSERT INTO guilds(guildid, guildname) VALUES (" + guild.getIdLong() + ",'" + guild.getName().replaceAll("[',\"]", "") + "')";
                    if (stmt1.executeUpdate(sql) > 0)
                        conn.commit();
                    botGuild.autoModRole(guild);
                    updateServerCount(guild.getJDA());
                    try {
                        guild.getDefaultChannel().sendMessage(output.getString("event-join").replace("[version]", Global.version)).queue();
                    } catch (InsufficientPermissionException ex) {
                        guild.getOwner().getUser().openPrivateChannel().queue((PrivateChannel channel) ->
                                channel.sendMessage(output.getString("event-join").replace("[version]", Global.version)).queue());
                    }
                    guild.getMembers().forEach(a -> {
                        String sql2 = "";
                        int ctn = 0;
                        try {
                            PreparedStatement stmt2 = conn.prepareStatement("INSERT INTO MemberRoles(guildId, userId, roleId) VALUES (?,?,?)");
                            stmt2.setLong(1, guild.getIdLong());
                            stmt2.setLong(2, a.getUser().getIdLong());
                            for (Role role : a.getRoles()) {
                                stmt2.setLong(3, role.getIdLong());
                                ctn += stmt2.executeUpdate();
                            }
                            stmt2.close();
                            stmt2 = conn.prepareStatement("INSERT INTO MemberNick(guildId, userId, nickname) VALUES (?,?,?)");
                            stmt2.setLong(1, guild.getIdLong());
                            stmt2.setLong(2, a.getUser().getIdLong());
                            stmt2.setString(3,a.getNickname());
                            ctn+=stmt2.executeUpdate();
                            if (ctn > 0)
                                conn.commit();
                            stmt2.close();
                        } catch (SQLException ex) {
                            sqlError(sql2, ex);
                        }
                    });
                }
                stmt1.close();
            } catch (SQLException ex) {
                try {
                    conn.rollback();
                } catch (SQLException ignored) {
                }
                Logger.logger.logError("SQLError in : " + sql);
                Logger.logger.logError("SQLException: " + ex.getMessage());
                Logger.logger.logError("SQLState: " + ex.getSQLState());
                Logger.logger.logError("VendorError: " + ex.getErrorCode());
                Logger.logger.logGeneral(ex.getStackTrace()[1].toString());
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

        switch (args[0].equals("") ? args[1] : args[0]) {
            case "end":
                Logger.logger.logMessage("end", message);
                LogLinker act = Global.getGbl().getMapChannel().get(channel.getIdLong());
                act.delete();
                channel.sendMessage(output.getString("console-stopped")).queue();
                Logger.logger.logReponse("console daemon stopped in channel:" + channel.getName(), guild, messageId);
                break;
            case "modrole":
                //if member is allowed
                if (args.length > 1) {
                    //get mentioned roles
                    List<Role> mentions = message.getMentionedRoles();
                    //test on second arg
                    switch (args[1]) {
                        case "add":
                            //if there is a mentioned role
                            Logger.logger.logRemoteMsg("modrole add", message,guild);
                            if (mentions.size() >= 1) {
                                //call class method to add roles
                                channel.sendMessage(botGuild.addRemoteModRole(mentions.get(0), guild, output, messageId,remote)).queue();
                            }
                            break;
                        case "remove":
                            //if there is a mentioned user
                            Logger.logger.logRemoteMsg("modrole remove", message,guild);
                            if (mentions.size() >= 1) {
                                //call class method to remove roles
                                channel.sendMessage(botGuild.removeRemoteModRole(mentions.get(0), guild, output, messageId,remote)).queue();
                            }
                            break;
                        case "clear":
                            Logger.logger.logRemoteMsg("modrole clear", message,guild);
                            channel.sendMessage(botGuild.clearRemoteModrole(guild, output, messageId,remote)).queue();
                            Logger.logger.logRemoteRep("modroles cleared", guild, messageId,remote);
                            break;
                        case "auto":
                            Logger.logger.logRemoteMsg("modrole auto", message,guild);
                            botGuild.autoModRole(guild);
                            channel.sendMessage(output.getString("modrole-auto")).queue();
                            Logger.logger.logRemoteRep("modroles updated", guild, messageId,remote);
                        case "list":
                            //list all modroles
                            Logger.logger.logRemoteMsg("modrole list", message,guild);
                            SendMsg(channel, botGuild.listRemoteModrole(guild, output, messageId,remote));
                            break;

                    }

                }
                break;
            case "whoami":
                Logger.logger.logMessage("whoami", message);
                EmbedBuilder eb = new EmbedBuilder();
                eb.setAuthor(guild.getName(),null,guild.getIconUrl());
                eb.setDescription(guild.getId());
                channel.sendMessage(eb.build()).queue();
                Logger.logger.logReponse("info shown",remote,messageId);
                break;
            case "help":
                Logger.logger.logMessage("console help", message);
                channel.sendMessage(output.getString("help-console-def")).queue();
                Logger.logger.logReponse("help shown",remote,messageId);
                break;
        }

    }

    public MyListener(Connection conn) {
        this.conn = conn;
        this.botGuild = new BotGuild(conn);
    }

    public void close(){
        System.err.println(ansi().fgRed().a("Closing Statements").reset());
        botGuild.close();
        System.err.println(ansi().fgGreen().a("Statements closed").reset());
        System.err.println();
        System.err.println(ansi().fgRed().a("Closing threads").reset());
        threads.shutdown();
        System.err.println(ansi().fgGreen().a("Threads closed").reset());
        System.err.println();
        try {
            System.err.println(ansi().fgRed().a("Closing connection").reset());
            conn.close();
            System.err.println(ansi().fgGreen().a("Connection closed").reset());
        } catch (SQLException ignored) {
        }
    }
}
