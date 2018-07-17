package com.accountant;

import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.*;
import net.dv8tion.jda.core.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.core.managers.GuildController;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

@SuppressWarnings("WeakerAccess")
public class DbInterface {
    private PreparedStatement[] rmRoleStmt = new PreparedStatement[2];
    private PreparedStatement[] adRoleStmt = new PreparedStatement[3];
    private PreparedStatement clRoleStmt;
    private PreparedStatement lsRoleStmt;
    private PreparedStatement isAdminStmt;
    private PreparedStatement[] delRoleStmt = new PreparedStatement[2];
    private PreparedStatement[] delChannelStmt = new PreparedStatement[2];
    private PreparedStatement[] aRoleStmt = new PreparedStatement[3];
    private PreparedStatement[] saveUserStmt = new PreparedStatement[3];
    private PreparedStatement[] roleRemoveStmt = new PreparedStatement[2];
    private PreparedStatement roleMemStmt;
    private PreparedStatement[] nickStmt = new PreparedStatement[3];
    private PreparedStatement[] uNameStmt = new PreparedStatement[4];
    private PreparedStatement restoreStmt;
    private PreparedStatement gChannelStmt;
    private PreparedStatement chChannelStmt;
    private PreparedStatement gDelayStmt;
    private PreparedStatement chDelayStmt;
    private PreparedStatement gPersStmt;
    private PreparedStatement chPersStmt;

    private List<PreparedStatement> stmts = new ArrayList<>(29);

    private Connection conn;




    public void cleanDb(String sql, List<Guild> guilds) {
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
                    if (guild.getIdLong() == rs.getLong("guildid")) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    sql = "DELETE FROM roles WHERE guildid=" + rs.getLong(1);
                    stmt2.execute(sql);
                    sql = "DELETE FROM guilds WHERE guildid=" + rs.getLong(1);
                    stmt2.execute(sql);
                    sql = "DELETE FROM MemberRoles WHERE guildid=" + rs.getLong(1);
                    stmt2.execute(sql);
                    sql = "DELETE FROM MemberNick WHERE guildid=" + rs.getLong(1);
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
    }

    public String forgetUser(ResourceBundle output, Guild guild, long Id, String sql) {
        try {
            PreparedStatement stmt = conn.prepareStatement("DELETE FROM MemberRoles WHERE guildid=? AND userId=?");
            stmt.setLong(1, guild.getIdLong());
            stmt.setLong(2, Id);
            stmt.executeUpdate();
            conn.commit();
            stmt.close();
            return output.getString("forget-done");
        } catch (SQLException ex) {
            sqlError(sql, ex);
            return "";
        }
    }

    public String removeAdmin(Role role, Guild guild, ResourceBundle output, long messageId) {
        String sql = "";
        String ret;
        PreparedStatement stmt;
        ResultSet rs;
        synchronized (conn) {
            try {
                stmt = rmRoleStmt[0];
                sql = "SELECT weight FROM roles WHERE guildid=" + guild.getId() + " AND roleid=" + role.getIdLong();
                synchronized (rmRoleStmt) {
                    stmt.setLong(1, guild.getIdLong());
                    stmt.setLong(2, role.getIdLong());
                    rs = stmt.executeQuery();
                    if (rs.next()) {
                        if (rs.getInt("weight") == 1) {
                            rs.close();
                            stmt = rmRoleStmt[1];
                            sql = "DELETE FROM roles WHERE guildid=" + guild.getId() + " AND roleid=" + role.getIdLong();
                            stmt.setLong(1, guild.getIdLong());
                            stmt.setLong(2, role.getIdLong());
                            stmt.setLong(3, 1);
                            stmt.executeUpdate();
                            stmt.getConnection().commit();
                            ret = output.getString("admin-remove");
                            Logger.logger.logReponse("removed role " + role.getName(), guild, messageId);
                            return ret;
                        }
                    }
                    rs.close();
                    ret = output.getString("error-admin-missing");
                    Logger.logger.logReponse("role not admin", guild, messageId);
                }
            } catch (SQLException ex) {
                sqlError(sql, ex);
                return null;
            }
        }
        return ret;
    }

    public String addAdmin(Role role, Guild guild, ResourceBundle output, long messageId) {
        String sql = "";
        String ret;
        PreparedStatement stmt;
        ResultSet rs;
        try {
            stmt = adRoleStmt[0];
            sql = "SELECT * FROM roles WHERE guildid=" + guild.getId() + " AND roleid=" + role.getIdLong();
            synchronized (adRoleStmt) {
                stmt.setLong(1, guild.getIdLong());
                stmt.setLong(2, role.getIdLong());
                rs = stmt.executeQuery();
                if (!rs.next()) {
                    rs.close();
                    stmt = adRoleStmt[1];
                    sql = "INSERT INTO roles (guildid,roleid,rolename) VALUES (" + guild.getId() + "," + role.getIdLong() + ",'" + role.getName().replaceAll("[\',\"]", "") + "')";
                    stmt.setLong(1, guild.getIdLong());
                    stmt.setLong(2, role.getIdLong());
                    stmt.setString(3, role.getName().replaceAll("[\',\"]", ""));
                    stmt.setLong(4, 1);
                    if (stmt.executeUpdate() > 0)
                        stmt.getConnection().commit();
                    ret = output.getString("admin-add");
                    Logger.logger.logReponse("added role " + role.getName(), guild, messageId);
                } else {
                    if (rs.getInt(1) == 2) {
                        rs.close();
                        stmt = adRoleStmt[2];
                        sql = "UPDATE roles SET weight=1 WHERE guildId=" + guild.getId() + " AND roleId=" + role.getIdLong();
                        stmt.setLong(1, guild.getIdLong());
                        stmt.setLong(2, role.getIdLong());
                        if (stmt.executeUpdate() > 0)
                            stmt.getConnection().commit();
                        ret = output.getString("admin-update");
                        Logger.logger.logReponse("role updated" + role.getName(), guild, messageId);

                    } else {
                        rs.close();
                        ret = output.getString("error-admin-exists");
                        Logger.logger.logReponse("role is admin", guild, messageId);
                    }
                }
            }
        } catch (SQLException ex) {
            sqlError(sql, ex);
            return null;
        }
        return ret;
    }

    public String clearAdmin(Guild guild, ResourceBundle output, long messageId) {
        String sql = "";
        String ret;
        PreparedStatement stmt;
        try {
            stmt = clRoleStmt;
            sql = "DELETE FROM roles WHERE guildid=" + guild.getId();
            synchronized (clRoleStmt) {
                stmt.setLong(1, guild.getIdLong());
                stmt.setLong(2, 1);
                stmt.executeUpdate();
                stmt.getConnection().commit();
            }
            ret = output.getString("admin-clear");
            Logger.logger.logReponse("cleared mods", guild, messageId);
        } catch (SQLException ex) {
            sqlError(sql, ex);
            return null;
        }
        return ret;
    }

    public String listAdmin(Guild guild, ResourceBundle output, long messageId) {
        String sql = "";
        StringBuilder ret = new StringBuilder(output.getString("admin-list"));
        PreparedStatement stmt;
        try {
            stmt = lsRoleStmt;
            sql = "SELECT roleid FROM roles WHERE guildid=" + guild.getIdLong();
            syncModList(guild, ret, stmt);
        } catch (SQLException ex) {
            sqlError(sql, ex);
            return null;
        }
        Logger.logger.logReponse("listed admins", guild, messageId);
        return ret.toString();
    }

    public String changeChannel(Guild guild, ResourceBundle output, TextChannel channel, long messageId) {
        PreparedStatement stmt = chChannelStmt;
        String sql = "";
        try {
            if (channel == null)
                stmt.setNull(1, Types.BIGINT);
            else
                stmt.setLong(1, channel.getIdLong());
            stmt.setLong(2, guild.getIdLong());

            if (stmt.executeUpdate() > 0)
                conn.commit();

            Logger.logger.logReponse((channel == null) ? "channel reset" : ("channel set to #" + channel.getName()), guild, messageId);
            return output.getString((channel == null) ? "channel-reset" : "channel-set");
        } catch (SQLException ex) {
            sqlError(sql, ex);
            return null;
        }
    }

    public String changeDelay(Guild guild, ResourceBundle output, float delay, long messageId) {
        PreparedStatement stmt = chDelayStmt;
        String sql = "";
        try {
            stmt.setFloat(1, delay);
            stmt.setLong(2, guild.getIdLong());

            if (stmt.executeUpdate() > 0)
                conn.commit();

            Logger.logger.logReponse("delay set to " + delay + "s", guild, messageId);
            return output.getString("delay-set").replace("[time]", Float.toString(delay));
        } catch (SQLException ex) {
            sqlError(sql, ex);
            return null;
        }
    }




    public boolean memberIsAdmin(Member member, long guild) {
        String sql = "";
        List<Role> roles = member.getRoles();
        PreparedStatement stmt;
        ResultSet rs;
        if (member.getUser().getIdLong() == Long.parseLong(System.getenv("OWNER_ID")))
            return true;

        try {
            stmt = isAdminStmt;
            sql = "SELECT roleid FROM roles WHERE guildid=" + guild;
            synchronized (isAdminStmt) {
                stmt.setLong(1, guild);
                rs = stmt.executeQuery();
                while (rs.next()) {
                    for (Role role : roles) {
                        if (role.getIdLong() == rs.getLong("roleid")) {
                            rs.close();
                            return true;
                        }
                    }
                }
                rs.close();
            }
        } catch (SQLException ex) {
            sqlError(sql, ex);
        }

        return false;
    }

    public void autoRole(Guild guild) {
        String sql = "";
        PreparedStatement stmt;
        ResultSet rs;
        long guildId = guild.getIdLong();
        for (Role role : guild.getRoles()) {
            if (role.isManaged())
                continue;
            if (role.hasPermission(Permission.ADMINISTRATOR) ||
                    role.hasPermission(Permission.MANAGE_SERVER) ||
                    role.hasPermission(Permission.MANAGE_ROLES))

                try {
                    stmt = aRoleStmt[0];
                    sql = "SELECT weight FROM roles WHERE guildid=" + guildId + " AND roleid=" + role.getIdLong();
                    synchronized (aRoleStmt) {
                        stmt.setLong(1, guildId);
                        stmt.setLong(2, role.getIdLong());
                        rs = stmt.executeQuery();
                        if (!rs.next()) {
                            rs.close();
                            stmt = aRoleStmt[1];
                            sql = "INSERT INTO roles (guildid,roleid,rolename,weight) VALUES (" + guildId + "," + role.getIdLong() + ",'" + role.getName().replaceAll("[\',\"]", "") + "',1)";
                            stmt.setLong(1, guildId);
                            stmt.setLong(2, role.getIdLong());
                            stmt.setString(3, role.getName().replaceAll("[\',\"]", ""));
                            if (stmt.executeUpdate() > 0)
                                stmt.getConnection().commit();
                        } else {
                            if (rs.getInt(1) == 2) {
                                rs.close();
                                stmt = aRoleStmt[2];
                                sql = "UPDATE roles SET weight=1 WHERE guildid=" + guildId + " AND roleid=" + role.getIdLong();
                                stmt.setLong(1, guildId);
                                stmt.setLong(2, role.getIdLong());
                                if (stmt.executeUpdate() > 0)
                                    stmt.getConnection().commit();
                            }
                        }
                        rs.close();
                    }
                } catch (SQLException ex) {
                    sqlError(sql, ex);
                }
        }
    }




    public boolean onRoleDeleted(Role role) {
        String sql = "";
        boolean ret = false;
        PreparedStatement stmt;
        ResultSet rs;
        try {
            stmt = delRoleStmt[0];
            sql = "SELECT * FROM roles WHERE guildid=" + role.getGuild().getIdLong() + " AND roleid=" + role.getIdLong();
            synchronized (delRoleStmt) {
                stmt.setLong(1, role.getGuild().getIdLong());
                stmt.setLong(2, role.getIdLong());
                rs = stmt.executeQuery();
                if (rs.next()) {
                    rs.close();
                    stmt = delRoleStmt[1];
                    sql = "DELETE FROM roles WHERE guildid=" + role.getGuild().getIdLong() + " AND roleid=" + role.getIdLong();
                    stmt.setLong(1, role.getGuild().getIdLong());
                    stmt.setLong(2, role.getIdLong());
                    stmt.executeUpdate();
                    stmt.getConnection().commit();
                    ret = true;
                }
            }
            sql = "DELETE FROM MemberRoles WHERE guildId=" + role.getGuild().getId() + " AND roleId=" + role.getId();
            stmt = conn.prepareStatement("DELETE FROM MemberRoles WHERE guildId=? AND roleId=?");
            stmt.setLong(1, role.getGuild().getIdLong());
            stmt.setLong(2, role.getIdLong());
            if (stmt.executeUpdate() > 0)
                stmt.getConnection().commit();
            stmt.close();
        } catch (SQLException ex) {
            sqlError(sql, ex);
        }
        return ret;
    }

    public boolean onChannelDeleted(TextChannel channel) {
        String sql = "";
        boolean ret = false;
        PreparedStatement stmt;
        ResultSet rs;
        try {
            stmt = delChannelStmt[0];
            sql = "SELECT channel FROM guilds WHERE guildid=" + channel.getGuild().getIdLong();
            synchronized (delChannelStmt) {
                stmt.setLong(1, channel.getGuild().getIdLong());
                rs = stmt.executeQuery();
                if (rs.next()) {
                    if(rs.getLong(1)==channel.getIdLong()) {
                        rs.close();
                        stmt = delRoleStmt[1];
                        sql = "UPDATE guilds SET channel=NULL WHERE guildId="+channel.getGuild().getId();
                        stmt.setLong(1, channel.getGuild().getIdLong());
                        stmt.executeUpdate();
                        stmt.getConnection().commit();
                        ret = true;
                    }
                }
                rs.close();
            }
        } catch (SQLException ex) {
            sqlError(sql, ex);
        }
        return ret;
    }




    public void newGuild(Guild guild) {
        String sql="";
        try {
            Statement stmt = conn.createStatement();
            sql = "INSERT INTO guilds(guildid) VALUES (" + guild.getIdLong() + ")";
            if (stmt.executeUpdate(sql) > 0)
                stmt.getConnection().commit();
            stmt.close();
        } catch (SQLException ex) {
            sqlError(sql, ex);
        }
        guild.getMembers().forEach(m -> {
            memorizeRole(guild,m.getUser(),m.getRoles());
            baseRole(guild,m.getUser());
            memUname(m);
        });
    }

    public void guildLeave(Guild guild) {
        String sql="";
        try {
            Statement stmt = conn.createStatement();
            sql = "DELETE FROM roles WHERE guildid=" + guild.getIdLong();
            stmt.execute(sql);
            sql = "DELETE FROM guilds WHERE guildid=" + guild.getIdLong();
            stmt.execute(sql);
            sql = "DELETE FROM MemberRoles WHERE guildid=" + guild.getIdLong();
            stmt.execute(sql);
            stmt.getConnection().commit();
            stmt.close();
        } catch (SQLException ex) {
            sqlError(sql, ex);
        }
    }


    public TextChannel getChannel(Guild guild) {
        PreparedStatement stmt = gChannelStmt;
        TextChannel channel = Optional.ofNullable(guild.getSystemChannel()).orElse(guild.getDefaultChannel());
        String sql = "SELECT channel FROM guilds WHERE guildId=" + guild.getId();
        try {
            long id;
            stmt.setLong(1, guild.getIdLong());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                id = rs.getLong(1);
                if (!rs.wasNull()) {
                    TextChannel S_channel = guild.getTextChannelById(id);
                    if (S_channel != null)
                        channel = S_channel;
                }
            }
            rs.close();
        } catch (SQLException ex) {
            sqlError(sql, ex);
        }
        return channel;
    }

    public float getDelay(Guild guild) {
        float delay = 0.5f;
        PreparedStatement stmt = gDelayStmt;
        String sql = "SELECT delay FROM guilds WHERE guildId=" + guild.getId();
        try {
            stmt.setLong(1, guild.getIdLong());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                delay = rs.getFloat(1);
            }
            rs.close();
        } catch (SQLException ex) {
            sqlError(sql, ex);
        }
        return delay;
    }

    public int getPersistence(Guild guild) {
        int persistence = 1;
        PreparedStatement stmt = gPersStmt;
        String sql = "SELECT persistence FROM guilds WHERE guildId=" + guild.getId();
        try {
            stmt.setLong(1, guild.getIdLong());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                persistence = rs.getInt(1);
            }
            rs.close();
        } catch (SQLException ex) {
            sqlError(sql, ex);
        }
        return persistence;
    }


    public boolean guildIsInDb(Guild guild) {
        String sql = "";
        boolean ret = false;
        try {
            Statement stmt = conn.createStatement();
            ResultSet rs;
            sql = "SELECT * FROM guilds WHERE guildid=" + guild.getIdLong();
            rs = stmt.executeQuery(sql);
            ret = rs.next();
            rs.close();
            stmt.close();
        } catch (SQLException ex) {
            sqlError(sql, ex);
        }
        return ret;
    }



    public void updateDatabase(Guild guild, ResourceBundle output) {
        String sql = "";
        try {
            Statement stmt1 = conn.createStatement();
            if (!guildIsInDb(guild)) {
                newGuild(guild);
                autoRole(guild);
                try {
                    getChannel(guild).sendMessage(output.getString("event-join").replace("[version]", Global.version)).queue();
                } catch (InsufficientPermissionException ex) {
                    try {
                        guild.getOwner().getUser().openPrivateChannel().queue((PrivateChannel channel) ->
                                channel.sendMessage(output.getString("event-join").replace("[version]", Global.version)).queue());

                    }catch (Exception ignored){}
                }
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

    public void rePopolateDb(JDA jda) {
        String sql = "";
        String sql2 = "";
        try {
            sql = "DELETE FROM MemberRoles WHERE expireDate IS NULL";
            PreparedStatement stmt = conn.prepareStatement("DELETE FROM MemberRoles WHERE expireDate IS NULL");
            stmt.executeUpdate();
            conn.commit();
            stmt.close();
            stmt = conn.prepareStatement("DELETE FROM MemberNick WHERE expireDate IS NULL");
            stmt.executeUpdate();
            conn.commit();
            stmt.close();
            final String sqli = "INSERT INTO MemberRoles(guildId, userId, roleId) VALUES (";
            final String sqli2 = "SELECT * FROM guilds WHERE guildId=";
            final String sqli3 = "INSERT INTO guilds(guildId) VALUE (";
            final PreparedStatement stmt1 = conn.prepareStatement("DELETE FROM MemberRoles WHERE guildid=? AND userId=?");
            final PreparedStatement stmt2 = conn.prepareStatement("INSERT INTO MemberRoles(guildId, userId, roleId) VALUES (?,?,?)");
            final PreparedStatement stmt3 = conn.prepareStatement("DELETE FROM MemberRoles WHERE guildid=? AND userId=?");
            final PreparedStatement stmt4 = conn.prepareStatement("INSERT INTO MemberNick(guildId, userId, nickname) VALUES (?,?,?)");
            final PreparedStatement stmt5 = conn.prepareStatement("SELECT * FROM guilds WHERE guildId=?");
            final PreparedStatement stmt6 = conn.prepareStatement("INSERT INTO guilds(guildId) VALUES (?)");
            jda.getGuilds().stream().peek(Output::println)
                    .peek(g -> {
                        String sql1 = "";
                        try {
                            stmt6.setLong(1, g.getIdLong());
                            stmt5.setLong(1, g.getIdLong());
                            sql1 = sqli2 + g.getId();
                            ResultSet rs = stmt5.executeQuery();
                            sql1 = sqli3 + g.getId() + ")";
                            if (!rs.next()){
                                stmt6.executeUpdate();
                            	autoRole(g);
                            }
                            rs.close();
                        } catch (SQLException ex) {
                            sqlError(sql1, ex);
                        }
                    })
                    .flatMap(a -> a.getMembers().stream()).forEach(m -> {
                String sql1 = "";
                try {
                    stmt1.setLong(1, m.getGuild().getIdLong());
                    stmt1.setLong(2, m.getUser().getIdLong());
                    stmt1.executeUpdate();
                    stmt2.setLong(1, m.getGuild().getIdLong());
                    stmt2.setLong(2, m.getUser().getIdLong());
                    stmt2.setNull(3,Types.BIGINT);
                    stmt2.executeUpdate();
                    stmt3.setLong(1, m.getGuild().getIdLong());
                    stmt3.setLong(2, m.getUser().getIdLong());
                    stmt3.executeUpdate();
                    stmt4.setLong(1, m.getGuild().getIdLong());
                    stmt4.setLong(2, m.getUser().getIdLong());
                    for (Role role : m.getRoles()) {
                        if (!(role.isPublicRole() || role.isManaged())) {
                            sql1 = sqli + m.getGuild().getId() + "," + m.getUser().getId() + "," + role.getId() + ")";
                            stmt2.setLong(3, role.getIdLong());
                            stmt2.executeUpdate();
                        }
                    }
                    stmt4.setString(3, m.getEffectiveName());
                    stmt4.executeUpdate();
                    roleMemStmt.setLong(1, m.getGuild().getIdLong());
                    roleMemStmt.setLong(2, m.getUser().getIdLong());
                    roleMemStmt.setNull(3,Types.BIGINT);
                    roleMemStmt.executeUpdate();
                } catch (SQLException ex) {
                    sqlError(sql1, ex);
                }
            });
            stmt1.close();
            stmt2.close();
            stmt3.close();
            stmt4.close();
            stmt5.close();
            stmt6.close();
            conn.commit();
            Output.println("Reload done");
        } catch (SQLException ex) {
            sqlError(sql, ex);
        }
    }



    public void memorizeRole(Guild guild, User user, List<Role> roles) {
        String partsql = "INSERT INTO MemberRoles(guildId, userId, roleId) VALUES (" + guild.getId() + "," + user.getId() + ",";
        String sql = "";

        int ctn = 0;
        try {
            PreparedStatement stmt = roleMemStmt;
            stmt.setLong(1, guild.getIdLong());
            stmt.setLong(2, user.getIdLong());
            for (Role role : roles) {
                if(!(role.isPublicRole() || role.isManaged())) {
                    stmt.setLong(3, role.getIdLong());
                    sql = role.getId();
                    ctn += stmt.executeUpdate();
                }
            }
            if (ctn > 0)
                conn.commit();
        } catch (SQLException ex) {
            sqlError(partsql+sql+")", ex);
        }
    }

    public void removeRole(Guild guild, User user, List<Role> roles) {
        String partsql = "DELETE FROM MemberRoles WHERE guildId=" + guild.getId() + " AND userId=" + user.getId() + " AND roleId=";
        String sql = "";

        try {
            int ctn = 0;
            PreparedStatement stmt = roleRemoveStmt[0];
            stmt.setLong(1, guild.getIdLong());
            stmt.setLong(2, user.getIdLong());
            for (Role role : roles) {
                ResultSet rs;
                sql = partsql + role.getId();
                stmt.setLong(3, role.getIdLong());
                rs = stmt.executeQuery();
                ctn += (rs.next())?1:0;
                rs.close();
            }
            if(ctn>0) {
                ctn=0;
                stmt = roleRemoveStmt[1];
                stmt.setLong(1, guild.getIdLong());
                stmt.setLong(2, user.getIdLong());

                for (Role role : roles) {
                    sql = partsql + role.getId();
                    stmt.setLong(3, role.getIdLong());
                    ctn += stmt.executeUpdate();
                }
                if (ctn > 0)
                    stmt.getConnection().commit();
            }
        } catch (SQLException ex) {
            sqlError(sql, ex);
        }
    }

    public void baseRole(Guild guild, User user){
        String sql = "INSERT INTO MemberRoles(guildId, userId, roleId) VALUES (" + guild.getId() + "," + user.getId() + ", NULL)";
        int ctn = 0;
        try {
            PreparedStatement stmt = roleMemStmt;
            stmt.setLong(1, guild.getIdLong());
            stmt.setLong(2, user.getIdLong());
            stmt.setNull(3,Types.BIGINT);
            ctn = stmt.executeUpdate();
            if (ctn > 0)
                conn.commit();
        } catch (SQLException ex) {
            sqlError(sql, ex);
        }
    }




    public void updateNick(Guild guild, User user, String nick) {
        String sql = "SELECT * FROM MemberNick WHERE guildId="+guild.getId()+" AND userId="+user.getId()+" AND expireDate IS NULL";
        try {
            PreparedStatement stmt = nickStmt[0];
            stmt.setLong(1, guild.getIdLong());
            stmt.setLong(2, user.getIdLong());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                rs.close();
                sql= "UPDATE MemberNick SET nickname="+nick+" WHERE guildId="+guild.getId()+" AND userId="+user.getId()+" AND expireDate IS NULL";
                stmt = nickStmt[1];
                stmt.setString(1, nick);
                stmt.setLong(2, guild.getIdLong());
                stmt.setLong(3, user.getIdLong());
                if (stmt.executeUpdate() > 0)
                    stmt.getConnection().commit();
            } else {
                rs.close();
                stmt = nickStmt[2];
                sql= "INSERT INTO MemberNick(guildId, userId, nickname) VALUES ("+guild.getId()+","+user.getId()+","+nick+")";
                stmt.setString(3, nick);
                stmt.setLong(1, guild.getIdLong());
                stmt.setLong(2, user.getIdLong());
                if (stmt.executeUpdate() > 0)
                    stmt.getConnection().commit();
            }
        } catch (SQLException ex) {
            sqlError(sql, ex);
        }
    }

    public void updateUname(User user, String uname) {
        String sql = "SELECT * FROM MemberNick WHERE userId=" + user.getId() + " AND nickname=" + uname + " AND expireDate IS NULL";
        try {
            PreparedStatement stmt = uNameStmt[0];
            stmt.setLong(1, user.getIdLong());
            stmt.setString(2, uname);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                rs.close();
                sql = "UPDATE MemberNick SET nickname=" + user.getName() + " WHERE userId=" + user.getId() + " AND nickname=" + uname + " AND expireDate IS NULL";
                stmt = uNameStmt[1];
                stmt.setString(1, user.getName());
                stmt.setLong(2, user.getIdLong());
                stmt.setString(3, uname);
                if (stmt.executeUpdate() > 0)
                    stmt.getConnection().commit();
            }else
                rs.close();
        } catch (SQLException ex) {
            sqlError(sql, ex);
        }
    }

    public void memUname(Member member){
        String sql = "SELECT * FROM MemberNick WHERE userId=" + member.getUser().getId() + " AND guildId=" + member.getGuild().getId();
        try {
            PreparedStatement stmt = uNameStmt[2];
            stmt.setLong(1, member.getUser().getIdLong());
            stmt.setLong(2, member.getGuild().getIdLong());
            ResultSet rs = stmt.executeQuery();
            if (!rs.next()) {
                rs.close();
                sql = "INSERT INTO MemberNick(guildId, userId, nickname) VALUES ("+member.getGuild().getIdLong()+","+member.getUser().getIdLong()+","+member.getEffectiveName()+")";
                stmt = uNameStmt[3];
                stmt.setLong(1, member.getGuild().getIdLong());
                stmt.setLong(2, member.getUser().getIdLong());
                stmt.setString(3, member.getEffectiveName());
                if (stmt.executeUpdate() > 0)
                    stmt.getConnection().commit();
            }else
                rs.close();
        } catch (SQLException ex) {
            sqlError(sql, ex);
        }
    }

    public int restoreUser(Guild guild, Member member, User user, GuildController gc, List<Role> roles) {
        boolean restored = false;
        boolean mute = false;
        int out = 0;
        String sql = "SELECT roleId FROM MemberRoles WHERE guildId=" + guild.getId() + " AND userId=" + user.getId() + " AND expireDate>" + Date.valueOf(LocalDate.now());
        try {
            PreparedStatement stmt = restoreStmt;
            stmt.setString(3, LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            stmt.setLong(1, guild.getIdLong());
            stmt.setLong(2, user.getIdLong());
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                Role role = guild.getRoleById(rs.getLong("roleId"));
                if (role != null &&
                        (role.getPosition() < guild.getSelfMember().getRoles().stream().mapToInt(Role::getPosition).max().orElse(0)) &&
                        !rs.wasNull() ) {
                    roles.add(role);
                    restored = true;
                    if (role.getName().matches(".*[Mm][Uu][Tt][Ee][Dd].*"))
                        mute = true;
                }
            }
            rs.close();
            sql = "SELECT nickname FROM MemberNicks WHERE guildId=" + guild.getId() + " AND userId=" + user.getId() + " AND expireDate>" + Date.valueOf(LocalDate.now());

            stmt = conn.prepareStatement("SELECT DISTINCT nickname FROM MemberNick WHERE guildId=? AND userId=? AND expireDate>?");
            stmt.setString(3, LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            stmt.setLong(1, guild.getIdLong());
            stmt.setLong(2, user.getIdLong());
            rs = stmt.executeQuery();
            while (rs.next()) {
                try {
                    gc.setNickname(member, rs.getString("nickname")).reason("restore Nickname").complete();
                    restored = true;
                } catch (Exception ignored) {
                }
            }
            rs.close();
            stmt.close();

            int ctn = 0;
            stmt = conn.prepareStatement("DELETE FROM MemberRoles WHERE guildid=? AND userId=? AND expireDate NOT NULL ");
            stmt.setLong(1, guild.getIdLong());
            stmt.setLong(2, user.getIdLong());
            ctn += stmt.executeUpdate();
            stmt.close();
            stmt = conn.prepareStatement("DELETE FROM MemberNick WHERE guildid=? AND userId=? AND expireDate NOT NULL ");
            stmt.setLong(1, guild.getIdLong());
            stmt.setLong(2, user.getIdLong());
            ctn += stmt.executeUpdate();
            stmt.close();

            if (ctn == 0) {
                stmt = conn.prepareStatement("INSERT INTO MemberNick(guildId, userId, nickname) VALUES (?,?,?)");
                stmt.setLong(1, guild.getIdLong());
                stmt.setLong(2, user.getIdLong());
                stmt.setString(3, user.getName());
                stmt.executeUpdate();
                stmt.close();
            }
            conn.commit();
        } catch (SQLException ex) {
            sqlError(sql, ex);
        }
        if (restored)
            out = 1;
        if (mute)
            out = 2;
        return out;
    }

    public void saveUser(Guild guild, User user) {
        String sql= "";
        try {
            int ctn = 0;
            int persistence = getPersistence(guild);
            sql = "SELECT roleId FROM MemberRoles WHERE guildId=" + guild.getId() + " AND userId=" + user.getId() + " AND (expireDate IS NULL OR expireDate>"+LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)+")";
            PreparedStatement stmt = saveUserStmt[0];
            stmt.setLong(1, guild.getIdLong());
            stmt.setLong(2, user.getIdLong());
            stmt.setString(3, LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            ResultSet rs = stmt.executeQuery();
            if(rs.next()) {
                sql = "UPDATE MemberRoles SET expireDate=" + LocalDateTime.now().plus(persistence, ChronoUnit.DAYS).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + " WHERE guildId=" + guild.getId() + " AND userId=" + user.getId() + " AND expireDate IS NULL";
                stmt = saveUserStmt[1];
                stmt.setString(1, LocalDateTime.now().plus(persistence, ChronoUnit.DAYS).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                stmt.setLong(2, guild.getIdLong());
                stmt.setLong(3, user.getIdLong());
                stmt.setString(4, LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                ctn += stmt.executeUpdate();
                sql = "UPDATE MemberNick SET expireDate=" + LocalDateTime.now().plus(persistence, ChronoUnit.DAYS).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + " WHERE guildId=" + guild.getId() + " AND userId=" + user.getId() + " AND expireDate IS NULL";
                stmt = saveUserStmt[2];
                stmt.setString(1, LocalDateTime.now().plus(persistence, ChronoUnit.DAYS).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                stmt.setLong(2, guild.getIdLong());
                stmt.setLong(3, user.getIdLong());
                stmt.setString(4, LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                ctn += stmt.executeUpdate();
                if (ctn > 0)
                    stmt.getConnection().commit();
            }
            rs.close();
        } catch (SQLException ex) {
            sqlError(sql, ex);
        }
    }



    private void syncModList(Guild guild, StringBuilder ret, PreparedStatement stmt) throws SQLException {
        ResultSet rs;
        stmt.setLong(1, guild.getIdLong());
        stmt.setLong(2, 1);
        rs = stmt.executeQuery();
        while (rs.next()) {
            Role role = guild.getRoleById(rs.getLong("roleid"));
            if (role != null) {
                ret.append("\n");
                ret.append(role.getName());
            }
        }
        rs.close();
    }




    private void sqlError(String sql, SQLException ex) {
        try {
            conn.rollback();
        } catch (SQLException ignored) {
        }
        Logger.logger.logError("SQLError in : " + sql);
        Logger.logger.logError(ex.getMessage());
        Logger.logger.logError("SQLState: " + ex.getSQLState());
        Logger.logger.logError("VendorError: " + ex.getErrorCode());
    }

    DbInterface(Connection conn) {
        synchronized (conn) {
            try {
                stmts.add(this.rmRoleStmt[0] = conn.prepareStatement("SELECT weight FROM roles WHERE guildid=? AND roleid=?"));
                stmts.add(this.rmRoleStmt[1] = conn.prepareStatement("DELETE FROM roles WHERE guildid=? AND roleid=? AND weight=?"));
                stmts.add(this.adRoleStmt[0] = rmRoleStmt[0]);
                stmts.add(this.adRoleStmt[1] = conn.prepareStatement("INSERT INTO roles (guildid,roleid,rolename,weight) VALUES (?,?,?,?)"));
                stmts.add(this.adRoleStmt[2] = conn.prepareStatement("UPDATE roles SET weight=1 WHERE guildid=? AND roleid=?"));
                stmts.add(this.clRoleStmt = conn.prepareStatement("DELETE FROM roles WHERE guildid=? AND weight=?"));
                stmts.add(this.lsRoleStmt = conn.prepareStatement("SELECT roleid FROM roles WHERE guildid=? AND weight=?"));
                stmts.add(this.aRoleStmt[0] = conn.prepareStatement("SELECT weight FROM roles WHERE guildid=? AND roleid=?"));
                stmts.add(this.aRoleStmt[1] = conn.prepareStatement("INSERT INTO roles (guildid,roleid,rolename,weight) VALUES (?,?,?,1)"));
                stmts.add(this.aRoleStmt[2] = conn.prepareStatement("UPDATE roles SET weight=1 WHERE guildid=? AND roleid=?"));
                stmts.add(this.delRoleStmt[0] = aRoleStmt[0]);
                stmts.add(this.delRoleStmt[1] = conn.prepareStatement("DELETE FROM roles WHERE guildid=? AND roleid=? AND weight=?"));
                stmts.add(this.delChannelStmt[0] = conn.prepareStatement("SELECT channel FROM guilds WHERE guildId=?"));
                stmts.add(this.delChannelStmt[1] = conn.prepareStatement("UPDATE guilds SET channel=NULL WHERE guildId=?"));
                stmts.add(this.isAdminStmt = conn.prepareStatement("SELECT roleid FROM roles WHERE guildid=? AND weight=1"));
                stmts.add(this.saveUserStmt[0] = conn.prepareStatement("SELECT roleId FROM MemberRoles WHERE guildId=? AND userId=? AND (expireDate IS NULL OR expireDate>?)"));
                stmts.add(this.saveUserStmt[1] = conn.prepareStatement("UPDATE MemberRoles SET expireDate=? WHERE guildId=? AND userId=? AND (expireDate IS NULL OR expireDate>?)"));
                stmts.add(this.saveUserStmt[2] = conn.prepareStatement("UPDATE MemberNick SET expireDate=? WHERE guildId=? AND userId=? AND (expireDate IS NULL OR expireDate>?)"));
                stmts.add(this.roleRemoveStmt[0] = conn.prepareStatement("SELECT * FROM MemberRoles WHERE guildId=? AND userId=? AND roleId=? AND expireDate IS NULL"));
                stmts.add(this.roleRemoveStmt[1] = conn.prepareStatement("DELETE FROM MemberRoles WHERE guildId=? AND userId=? AND roleId=? AND expireDate IS NULL"));
                stmts.add(this.roleMemStmt = conn.prepareStatement("INSERT INTO MemberRoles(guildId, userId, roleId) VALUES (?,?,?)"));
                stmts.add(this.nickStmt[0] = conn.prepareStatement("SELECT * FROM MemberNick WHERE guildId=? AND userId=? AND expireDate IS NULL"));
                stmts.add(this.nickStmt[1] = conn.prepareStatement("UPDATE MemberNick SET nickname=? WHERE guildId=? AND userId=? AND expireDate IS NULL"));
                stmts.add(this.nickStmt[2] = conn.prepareStatement("INSERT INTO MemberNick(guildId, userId, nickname) VALUES (?,?,?)"));
                stmts.add(this.restoreStmt = conn.prepareStatement("SELECT DISTINCT roleId FROM MemberRoles WHERE guildId=? AND userId=? AND expireDate>?"));
                stmts.add(this.uNameStmt[0] = conn.prepareStatement("SELECT * FROM MemberNick WHERE userId=? AND nickname=? AND expireDate IS NULL"));
                stmts.add(this.uNameStmt[1] = conn.prepareStatement("UPDATE MemberNick SET nickname=? WHERE userId=? AND nickname=? AND expireDate IS NULL"));
                stmts.add(this.uNameStmt[2] = conn.prepareStatement("SELECT * FROM MemberNick WHERE userId=? AND guildId=?"));
                stmts.add(this.uNameStmt[3] = conn.prepareStatement("INSERT INTO MemberNick(guildId, userId, nickname) VALUES (?,?,?)"));
                stmts.add(this.gChannelStmt = conn.prepareStatement("SELECT channel FROM guilds WHERE guildId=? "));
                stmts.add(this.chChannelStmt = conn.prepareStatement("UPDATE guilds SET channel=? WHERE guildId=?"));
                stmts.add(this.gDelayStmt = conn.prepareStatement("SELECT delay FROM guilds WHERE guildId=? "));
                stmts.add(this.chDelayStmt = conn.prepareStatement("UPDATE guilds SET delay=? WHERE guildId=?"));
                stmts.add(this.gPersStmt = conn.prepareStatement("SELECT persistence FROM guilds WHERE guildId=? "));
                stmts.add(this.chPersStmt = conn.prepareStatement("UPDATE guilds SET persistence=? WHERE guildId=?"));
                this.conn = conn;
            } catch (SQLException ex) {
                Logger.logger.logError("SQLError in SQL preparation");
                Logger.logger.logError(ex.getMessage());
                Logger.logger.logError("SQLState: " + ex.getSQLState());
                Logger.logger.logError("VendorError: " + ex.getErrorCode());
                System.exit(-1);
            }
        }
    }

    public void close() {
        for (PreparedStatement stmt : stmts) {
            try {
                stmt.close();
            } catch (SQLException ignored) {
            }
        }
    }


}
