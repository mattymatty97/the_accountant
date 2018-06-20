package com.accountant;

import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.*;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.core.managers.GuildController;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

@SuppressWarnings("WeakerAccess")
public class DbInterface {
    private PreparedStatement[] rmRoleStmt = new PreparedStatement[2];
    private PreparedStatement[] adRoleStmt = new PreparedStatement[3];
    private PreparedStatement clRoleStmt;
    private PreparedStatement lsRoleStmt;
    private PreparedStatement isModStmt;
    private PreparedStatement isAdminStmt;
    private PreparedStatement[] delRoleStmt = new PreparedStatement[2];
    private PreparedStatement[] aRoleStmt = new PreparedStatement[3];
    private PreparedStatement[] saveUserStmt = new PreparedStatement[3];
    private PreparedStatement[] roleRemoveStmt = new PreparedStatement[2];
    private PreparedStatement roleMemStmt;
    private PreparedStatement[] nickStmt = new PreparedStatement[3];
    private PreparedStatement[] uNameStmt = new PreparedStatement[2];
    private PreparedStatement restoreStmt;

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

    public String forgiveUser(ResourceBundle output, Guild guild, long Id, String sql) {
        try {
            PreparedStatement stmt = conn.prepareStatement("DELETE FROM MemberRoles WHERE guildid=? AND userId=?");
            stmt.setLong(1, guild.getIdLong());
            stmt.setLong(2, Id);
            stmt.executeUpdate();
            conn.commit();
            stmt.close();
            return output.getString("forgive-done");
        } catch (SQLException ex) {
            sqlError(sql, ex);
            return "";
        }
    }

    public String removeMod(Role role, Guild guild, ResourceBundle output, long messageId) {
        String sql = "";
        String ret;
        PreparedStatement stmt;
        ResultSet rs;
        try {
            stmt = rmRoleStmt[0];
            sql = "SELECT weight FROM roles WHERE guildid=" + guild.getId() + " AND roleid=" + role.getIdLong();
            synchronized (rmRoleStmt) {
                stmt.setLong(1, guild.getIdLong());
                stmt.setLong(2, role.getIdLong());
                rs = stmt.executeQuery();
                if (rs.next()) {
                    if (rs.getInt("weight") == 2) {
                        rs.close();
                        stmt = rmRoleStmt[1];
                        sql = "DELETE FROM roles WHERE guildid=" + guild.getId() + " AND roleid=" + role.getIdLong();
                        stmt.setLong(1, guild.getIdLong());
                        stmt.setLong(2, role.getIdLong());
                        stmt.setLong(3, 2);
                        stmt.executeUpdate();
                        stmt.getConnection().commit();
                        ret = output.getString("mod-remove");
                        Logger.logger.logReponse("removed role " + role.getName(), guild, messageId);
                        return ret;
                    }
                }
                rs.close();
                ret = output.getString("error-mod-missing");
                Logger.logger.logReponse("role not mod", guild, messageId);
            }
        } catch (SQLException ex) {
            return sqlError(sql, ex);
        }
        return ret;
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
                return sqlError(sql, ex);
            }
        }
        return ret;
    }

    public String addMod(Role role, Guild guild, ResourceBundle output, long messageId) {
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
                    stmt.setLong(4, 2);
                    if (stmt.executeUpdate() > 0)
                        stmt.getConnection().commit();
                    ret = output.getString("mod-add");
                    Logger.logger.logReponse("added role " + role.getName(), guild, messageId);
                } else {
                    if (rs.getInt(1) == 1) {
                        rs.close();
                        ret = output.getString("error-admin-exists");
                        Logger.logger.logReponse("role is admin", guild, messageId);
                    } else {
                        rs.close();
                        ret = output.getString("error-mod-exists");
                        Logger.logger.logReponse("role is mod", guild, messageId);
                    }
                }
            }
        } catch (SQLException ex) {
            return sqlError(sql, ex);
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
            return sqlError(sql, ex);
        }
        return ret;
    }

    public String clearMod(Guild guild, ResourceBundle output, long messageId) {
        return clearRole(guild, output, messageId, 2);
    }

    public String clearAdmin(Guild guild, ResourceBundle output, long messageId) {
        return clearRole(guild, output, messageId, 1);
    }

    private String clearRole(Guild guild, ResourceBundle output, long messageId, int type) {
        String sql = "";
        String ret;
        PreparedStatement stmt;
        try {
            stmt = clRoleStmt;
            sql = "DELETE FROM roles WHERE guildid=" + guild.getId();
            synchronized (clRoleStmt) {
                stmt.setLong(1, guild.getIdLong());
                stmt.setLong(2, type);
                stmt.executeUpdate();
                stmt.getConnection().commit();
            }
            if (type == 1)
                ret = output.getString("admin-clear");
            else
                ret = output.getString("mod-clear");
            Logger.logger.logReponse("cleared mods", guild, messageId);
        } catch (SQLException ex) {
            return sqlError(sql, ex);
        }
        return ret;
    }

    public String listMod(Guild guild, ResourceBundle output, long messageId) {
        String sql = "";
        StringBuilder ret = new StringBuilder(output.getString("mod-list"));
        PreparedStatement stmt;
        try {
            stmt = lsRoleStmt;
            sql = "SELECT roleid FROM roles WHERE guildid=" + guild.getIdLong();
            syncModList(guild, ret, stmt, 2);
        } catch (SQLException ex) {
            return sqlError(sql, ex);
        }
        Logger.logger.logReponse("listed mods", guild, messageId);
        return ret.toString();
    }

    public String listAdmin(Guild guild, ResourceBundle output, long messageId) {
        String sql = "";
        StringBuilder ret = new StringBuilder(output.getString("admin-list"));
        PreparedStatement stmt;
        try {
            stmt = lsRoleStmt;
            sql = "SELECT roleid FROM roles WHERE guildid=" + guild.getIdLong();
            syncModList(guild, ret, stmt, 1);
        } catch (SQLException ex) {
            return sqlError(sql, ex);
        }
        Logger.logger.logReponse("listed admins", guild, messageId);
        return ret.toString();
    }

    public boolean memberIsMod(Member member, long guild) {
        return check_roles(member, guild, isModStmt);
    }

    public boolean memberIsAdmin(Member member, long guild) {
        return check_roles(member, guild, isAdminStmt);
    }

    private boolean check_roles(Member member, long guild, PreparedStatement Stmt) {
        String sql = "";
        List<Role> roles = member.getRoles();
        PreparedStatement stmt;
        ResultSet rs;
        if (member.getUser().getIdLong() == Long.parseLong(System.getenv("OWNER_ID")))
            return true;

        try {
            stmt = Stmt;
            sql = "SELECT roleid FROM roles WHERE guildid=" + guild;
            synchronized (Stmt) {
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

    public void newGuild(Guild guild) {
        String sql="";
        try {
            Statement stmt = conn.createStatement();
            sql = "INSERT INTO guilds(guildid, guildname) VALUES (" + guild.getIdLong() + ",'" + guild.getName().replaceAll("[\',\"]", "") + "')";
            if (stmt.executeUpdate(sql) > 0)
                stmt.getConnection().commit();
            stmt.close();
        } catch (SQLException ex) {
            sqlError(sql, ex);
        }
        guild.getMembers().forEach(a -> {
            String sql2 = "";
            try {
                int ctn = 0;
                PreparedStatement stmt1 = conn.prepareStatement("INSERT INTO MemberRoles(guildId, userId, roleId) VALUES (?,?,?)");
                stmt1.setLong(1, guild.getIdLong());
                stmt1.setLong(2, a.getUser().getIdLong());
                for (Role role : a.getRoles()) {
                    stmt1.setLong(3, role.getIdLong());
                    ctn += stmt1.executeUpdate();
                }
                stmt1.close();
                stmt1 = conn.prepareStatement("INSERT INTO MemberNick(guildId, userId, nickname) VALUES (?,?,?)");
                stmt1.setLong(1, guild.getIdLong());
                stmt1.setLong(2, a.getUser().getIdLong());
                stmt1.setString(3, a.getNickname());
                ctn += stmt1.executeUpdate();
                if (ctn > 0)
                    conn.commit();
                stmt1.close();
            } catch (SQLException ex) {
                sqlError(sql2, ex);
            }
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
            if (guildIsInDb(guild)) {
                ResultSet rs;
                sql = "SELECT guildName FROM guilds WHERE guildid=" + guild.getIdLong();
                rs = stmt1.executeQuery(sql);
                if(rs.next()) {
                    if(!rs.getString("guildName").equals(guild.getName().replaceAll("[',\"]", ""))) {
                        sql = "UPDATE guilds SET guildname='" + guild.getName().replaceAll("[',\"]", "") + "' WHERE guildid=" + guild.getIdLong();
                        if (stmt1.executeUpdate(sql) > 0)
                            conn.commit();
                    }
                }
                rs.close();
            } else {
                newGuild(guild);
                autoRole(guild);
                try {
                    guild.getDefaultChannel().sendMessage(output.getString("event-join").replace("[version]", Global.version)).queue();
                } catch (InsufficientPermissionException ex) {
                    guild.getOwner().getUser().openPrivateChannel().queue((PrivateChannel channel) ->
                            channel.sendMessage(output.getString("event-join").replace("[version]", Global.version)).queue());
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

    public void rePopolateDb(MessageReceivedEvent event) {
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

    public void memorizeRole(Guild guild, User user, List<Role> roles) {
        String partsql = "INSERT INTO MemberRoles(guildId, userId, roleId) VALUES (" + guild.getId() + "," + user.getId() + ",";
        String sql = "";

        int ctn = 0;
        try {
            PreparedStatement stmt = roleMemStmt;
            stmt.setLong(1, guild.getIdLong());
            stmt.setLong(2, user.getIdLong());
            for (Role role : roles) {
                stmt.setLong(3, role.getIdLong());
                sql = role.getId();
                ctn += stmt.executeUpdate();
            }
            if (ctn > 0)
                stmt.getConnection().commit();
        } catch (SQLException ex) {
            sqlError(partsql+sql+")", ex);
        }
    }

    public void removeRole(Guild guild, User user, List<Role> roles) {
        String partsql = "UPDATE MemberRoles SET expireDate=" + Timestamp.valueOf(LocalDateTime.now().plus(1, ChronoUnit.MINUTES)) + " WHERE guildId=" + guild.getId() + " AND userId=" + user.getId() + " AND roleId=";
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
                stmt.setString(1, Timestamp.valueOf(LocalDateTime.now().plus(1, ChronoUnit.MINUTES)).toString());
                stmt.setLong(2, guild.getIdLong());
                stmt.setLong(3, user.getIdLong());

                for (Role role : roles) {
                    sql = partsql + role.getId();
                    stmt.setLong(4, role.getIdLong());
                    ctn += stmt.executeUpdate();
                }
                if (ctn > 0)
                    stmt.getConnection().commit();
            }
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
            }
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
            stmt.setString(3, Timestamp.valueOf(LocalDateTime.now()).toString());
            stmt.setLong(1, guild.getIdLong());
            stmt.setLong(2, user.getIdLong());
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                Role role = guild.getRoleById(rs.getLong("roleId"));
                if (role != null && (role.getPosition() < guild.getSelfMember().getRoles().stream().mapToInt(Role::getPosition).max().orElse(0))) {
                    roles.add(role);
                    restored = true;
                    if (role.getName().matches(".*[Mm][Uu][Tt][Ee][Dd].*"))
                        mute = true;
                }
            }
            rs.close();
            sql = "SELECT nickname FROM MemberNicks WHERE guildId=" + guild.getId() + " AND userId=" + user.getId() + " AND expireDate>" + Date.valueOf(LocalDate.now());

            stmt = conn.prepareStatement("SELECT DISTINCT nickname FROM MemberNick WHERE guildId=? AND userId=? AND expireDate>?");
            stmt.setString(3, Timestamp.valueOf(LocalDateTime.now()).toString());
            stmt.setLong(1, guild.getIdLong());
            stmt.setLong(2, user.getIdLong());
            rs = stmt.executeQuery();
            while (rs.next()) {
                try {
                    gc.setNickname(member, rs.getString("nickname")).reason("restore Nickanme").queue();
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
            sql = "SELECT * FROM MemberRoles,MemberNick WHERE guildId=" + guild.getId() + " AND userId=" + user.getId() + " AND expireDate=null";
            PreparedStatement stmt = saveUserStmt[0];
            stmt.setLong(1, guild.getIdLong());
            stmt.setLong(2, user.getIdLong());
            stmt.setString(3, Timestamp.valueOf(LocalDateTime.now()).toString());
            ResultSet rs = stmt.executeQuery();
            if(rs.next()) {
                sql = "UPDATE MemberRoles SET expireDate=" + Timestamp.valueOf(LocalDateTime.now().plus(1, ChronoUnit.DAYS)) + " WHERE guildId=" + guild.getId() + " AND userId=" + user.getId() + " AND expireDate=null";
                stmt = saveUserStmt[1];
                stmt.setString(1, Timestamp.valueOf(LocalDateTime.now().plus(1, ChronoUnit.DAYS)).toString());
                stmt.setLong(2, guild.getIdLong());
                stmt.setLong(3, user.getIdLong());
                stmt.setString(4, Timestamp.valueOf(LocalDateTime.now()).toString());
                ctn += stmt.executeUpdate();
                sql = "UPDATE MemberNick SET expireDate=" + Timestamp.valueOf(LocalDateTime.now().plus(1, ChronoUnit.DAYS)) + " WHERE guildId=" + guild.getId() + " AND userId=" + user.getId() + " AND expireDate=null";
                stmt = saveUserStmt[2];
                stmt.setString(1, Timestamp.valueOf(LocalDateTime.now().plus(1, ChronoUnit.DAYS)).toString());
                stmt.setLong(2, guild.getIdLong());
                stmt.setLong(3, user.getIdLong());
                stmt.setString(4, Timestamp.valueOf(LocalDateTime.now()).toString());
                ctn += stmt.executeUpdate();
                if (ctn > 0)
                    stmt.getConnection().commit();
            }
            rs.close();
        } catch (SQLException ex) {
            sqlError(sql, ex);
        }
    }


    //remote methods

    public String removeRemoteMod(Role role, Guild guild, ResourceBundle output, long messageId, Guild remote) {
        String sql = "";
        String ret;
        PreparedStatement stmt;
        ResultSet rs;
        try {
            stmt = rmRoleStmt[0];
            sql = "(remote) SELECT weight FROM roles WHERE guildid=" + guild.getId() + " AND roleid=" + role.getIdLong();
            synchronized (rmRoleStmt) {
                stmt.setLong(1, guild.getIdLong());
                stmt.setLong(2, role.getIdLong());
                rs = stmt.executeQuery();
                if (rs.next()) {
                    if (rs.getInt("weight") == 2) {
                        rs.close();
                        stmt = rmRoleStmt[1];
                        sql = "(remote) DELETE FROM roles WHERE guildid=" + guild.getId() + " AND roleid=" + role.getIdLong();
                        stmt.setLong(1, guild.getIdLong());
                        stmt.setLong(2, role.getIdLong());
                        stmt.executeUpdate();
                        stmt.getConnection().commit();
                        ret = output.getString("mod-remove");
                        Logger.logger.logRemoteRep("removed role " + role.getName(), guild, messageId, remote);
                        return ret;
                    }
                }
                rs.close();
                ret = output.getString("error-mod-missing");
                Logger.logger.logRemoteRep("role not mod", guild, messageId, remote);

            }
        } catch (SQLException ex) {
            return sqlError(sql, ex);
        }
        return ret;
    }

    public String removeRemoteAdmin(Role role, Guild guild, ResourceBundle output, long messageId, Guild remote) {
        String sql = "";
        String ret;
        PreparedStatement stmt;
        ResultSet rs;
        try {
            stmt = rmRoleStmt[0];
            sql = "(remote) SELECT weight FROM roles WHERE guildid=" + guild.getId() + " AND roleid=" + role.getIdLong();
            synchronized (rmRoleStmt) {
                stmt.setLong(1, guild.getIdLong());
                stmt.setLong(2, role.getIdLong());
                rs = stmt.executeQuery();
                if (rs.next()) {
                    if (rs.getInt("weight") == 1) {
                        rs.close();
                        stmt = rmRoleStmt[1];
                        sql = "(remote) DELETE FROM roles WHERE guildid=" + guild.getId() + " AND roleid=" + role.getIdLong();
                        stmt.setLong(1, guild.getIdLong());
                        stmt.setLong(2, role.getIdLong());
                        stmt.executeUpdate();
                        stmt.getConnection().commit();
                        ret = output.getString("mod-remove");
                        Logger.logger.logRemoteRep("removed role " + role.getName(), guild, messageId, remote);
                        return ret;
                    }
                }
                rs.close();
                ret = output.getString("error-mod-missing");
                Logger.logger.logRemoteRep("role not mod", guild, messageId, remote);
            }
        } catch (SQLException ex) {
            return sqlError(sql, ex);
        }
        return ret;
    }

    public String addRemoteMod(Role role, Guild guild, ResourceBundle output, long messageId, Guild remote) {
        String sql = "";
        String ret;
        PreparedStatement stmt;
        ResultSet rs;
        try {
            stmt = adRoleStmt[0];
            sql = "(remote) SELECT * FROM roles WHERE guildid=" + guild.getId() + " AND roleid=" + role.getIdLong();
            synchronized (adRoleStmt) {
                stmt.setLong(1, guild.getIdLong());
                stmt.setLong(2, role.getIdLong());
                rs = stmt.executeQuery();
                if (!rs.next()) {
                    rs.close();
                    stmt = adRoleStmt[1];
                    sql = "(remote) INSERT INTO roles (guildid,roleid,rolename) VALUES (" + guild.getId() + "," + role.getIdLong() + ",'" + role.getName().replaceAll("[\',\"]", "") + "')";
                    stmt.setLong(1, guild.getIdLong());
                    stmt.setLong(2, role.getIdLong());
                    stmt.setString(3, role.getName().replaceAll("[\',\"]", ""));
                    if (stmt.executeUpdate() > 0)
                        stmt.getConnection().commit();
                    ret = output.getString("mod-add");
                    Logger.logger.logRemoteRep("added role " + role.getName(), guild, messageId, remote);
                } else if (rs.getInt(1) == 1) {
                    rs.close();
                    ret = output.getString("error-admin-exists");
                    Logger.logger.logRemoteRep("role is admin", guild, messageId, remote);
                } else {
                    rs.close();
                    ret = output.getString("error-mod-exists");
                    Logger.logger.logRemoteRep("role is mod", guild, messageId, remote);
                }
            }
        } catch (SQLException ex) {
            return sqlError(sql, ex);
        }
        return ret;
    }

    public String addRemoteAdmin(Role role, Guild guild, ResourceBundle output, long messageId, Guild remote) {
        String sql = "";
        String ret;
        PreparedStatement stmt;
        ResultSet rs;
        try {
            stmt = adRoleStmt[0];
            sql = "(remote) SELECT * FROM roles WHERE guildid=" + guild.getId() + " AND roleid=" + role.getIdLong();
            synchronized (adRoleStmt) {
                stmt.setLong(1, guild.getIdLong());
                stmt.setLong(2, role.getIdLong());
                rs = stmt.executeQuery();
                if (!rs.next()) {
                    rs.close();
                    stmt = adRoleStmt[1];
                    sql = "(remote) INSERT INTO roles (guildid,roleid,rolename) VALUES (" + guild.getId() + "," + role.getIdLong() + ",'" + role.getName().replaceAll("[\',\"]", "") + "')";
                    stmt.setLong(1, guild.getIdLong());
                    stmt.setLong(2, role.getIdLong());
                    stmt.setString(3, role.getName().replaceAll("[\',\"]", ""));
                    if (stmt.executeUpdate() > 0)
                        stmt.getConnection().commit();
                    ret = output.getString("mod-add");
                    Logger.logger.logRemoteRep("added role " + role.getName(), guild, messageId, remote);
                } else {
                    if (rs.getInt(1) == 2) {
                        rs.close();
                        stmt = adRoleStmt[2];
                        sql = "UPDATE roles SET weight=1 WHERE guildId=" + remote.getId() + " AND roleId=" + role.getIdLong();
                        stmt.setLong(1, remote.getIdLong());
                        stmt.setLong(2, role.getIdLong());
                        if (stmt.executeUpdate() > 0)
                            stmt.getConnection().commit();
                        ret = output.getString("admin-update");
                        Logger.logger.logRemoteRep("role updated" + role.getName(), guild, messageId, remote);
                    } else {
                        rs.close();
                        ret = output.getString("error-admin-exists");
                        Logger.logger.logRemoteRep("role is admin", guild, messageId, remote);
                    }
                }
            }
        } catch (SQLException ex) {
            return sqlError(sql, ex);
        }
        return ret;
    }

    public String clearRemoteMod(Guild guild, ResourceBundle output, long messageId, Guild remote) {
        String sql = "";
        String ret;
        PreparedStatement stmt;
        try {
            stmt = clRoleStmt;
            sql = "(remote) DELETE FROM roles WHERE guildid=" + guild.getId();
            synchronized (clRoleStmt) {
                stmt.setLong(1, guild.getIdLong());
                stmt.setLong(2, 2);
                stmt.executeUpdate();
                stmt.getConnection().commit();
            }
            ret = output.getString("mod-clear");
            Logger.logger.logRemoteRep("cleared mods", guild, messageId, remote);
        } catch (SQLException ex) {
            return sqlError(sql, ex);
        }
        return ret;
    }

    public String clearRemoteAdmin(Guild guild, ResourceBundle output, long messageId, Guild remote) {
        String sql = "";
        String ret;
        PreparedStatement stmt;
        try {
            stmt = clRoleStmt;
            sql = "(remote) DELETE FROM roles WHERE guildid=" + guild.getId();
            synchronized (clRoleStmt) {
                stmt.setLong(1, guild.getIdLong());
                stmt.setLong(2, 1);
                stmt.executeUpdate();
                stmt.getConnection().commit();
            }
            ret = output.getString("admin-clear");
            Logger.logger.logRemoteRep("cleared admins", guild, messageId, remote);
        } catch (SQLException ex) {
            return sqlError(sql, ex);
        }
        return ret;
    }

    public String listRemoteMod(Guild guild, ResourceBundle output, long messageId, Guild remote) {
        String sql = "";
        StringBuilder ret = new StringBuilder(output.getString("mod-list"));
        PreparedStatement stmt;
        try {
            stmt = lsRoleStmt;
            sql = "(remote) SELECT roleid FROM roles WHERE guildid=" + guild.getIdLong();
            syncModList(remote, ret, stmt, 2);
        } catch (SQLException ex) {
            return sqlError(sql, ex);
        }
        Logger.logger.logRemoteRep("listed mods", guild, messageId, remote);
        return ret.toString();
    }

    public String listRemoteAdmin(Guild guild, ResourceBundle output, long messageId, Guild remote) {
        String sql = "";
        StringBuilder ret = new StringBuilder(output.getString("mod-list"));
        PreparedStatement stmt;
        try {
            stmt = lsRoleStmt;
            sql = "(remote) SELECT roleid FROM roles WHERE guildid=" + guild.getIdLong();
            syncModList(remote, ret, stmt, 1);
        } catch (SQLException ex) {
            return sqlError(sql, ex);
        }
        Logger.logger.logRemoteRep("listed admins", guild, messageId, remote);
        return ret.toString();
    }


    private void syncModList(Guild guild, StringBuilder ret, PreparedStatement stmt, int type) throws SQLException {
        ResultSet rs;
        stmt.setLong(1, guild.getIdLong());
        stmt.setLong(2, type);
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

    private String sqlError(String sql, SQLException ex) {
        try {
            conn.rollback();
        } catch (SQLException ignored) {
        }
        Logger.logger.logError("SQLError in : " + sql);
        Logger.logger.logError(ex.getMessage());
        Logger.logger.logError("SQLState: " + ex.getSQLState());
        Logger.logger.logError("VendorError: " + ex.getErrorCode());
        return null;
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
                stmts.add(this.isModStmt = conn.prepareStatement("SELECT roleid FROM roles WHERE guildid=? AND weight<=2"));
                stmts.add(this.isAdminStmt = conn.prepareStatement("SELECT roleid FROM roles WHERE guildid=? AND weight=1"));
                stmts.add(this.saveUserStmt[0] = conn.prepareStatement("SELECT nickname,roleId FROM MemberRoles R,MemberNick N WHERE (R.guildId=? AND R.userId=? AND (R.expireDate IS NULL OR R.expireDate>?)) OR (N.guildId=? AND N.userId=? AND (N.expireDate IS NULL OR N.expireDate>?))"));
                stmts.add(this.saveUserStmt[1] = conn.prepareStatement("UPDATE MemberRoles SET expireDate=? WHERE guildId=? AND userId=? AND (expireDate IS NULL OR expireDate>?)"));
                stmts.add(this.saveUserStmt[2] = conn.prepareStatement("UPDATE MemberNick SET expireDate=? WHERE guildId=? AND userId=? AND (expireDate IS NULL OR expireDate>?)"));
                stmts.add(this.roleRemoveStmt[0] = conn.prepareStatement("SELECT * FROM MemberRoles WHERE guildId=? AND userId=? AND roleId=? AND expireDate IS NULL"));
                stmts.add(this.roleRemoveStmt[1] = conn.prepareStatement("UPDATE MemberRoles SET expireDate=? WHERE guildId=? AND userId=? AND roleId=? AND expireDate IS NULL"));
                stmts.add(this.roleMemStmt = conn.prepareStatement("INSERT INTO MemberRoles(guildId, userId, roleId) VALUES (?,?,?)"));
                stmts.add(this.nickStmt[0] = conn.prepareStatement("SELECT * FROM MemberNick WHERE guildId=? AND userId=? AND expireDate IS NULL"));
                stmts.add(this.nickStmt[1] = conn.prepareStatement("UPDATE MemberNick SET nickname=? WHERE guildId=? AND userId=? AND expireDate IS NULL"));
                stmts.add(this.nickStmt[2] = conn.prepareStatement("INSERT INTO MemberNick(guildId, userId, nickname) VALUES (?,?,?)"));
                stmts.add(this.restoreStmt = conn.prepareStatement("SELECT DISTINCT roleId FROM MemberRoles WHERE guildId=? AND userId=? AND expireDate>?"));
                stmts.add(this.uNameStmt[0] = conn.prepareStatement("SELECT * FROM MemberNick WHERE userId=? AND nickname=? AND expireDate IS NULL"));
                stmts.add(this.uNameStmt[1] = conn.prepareStatement("UPDATE MemberNick SET nickname=? WHERE userId=? AND nickname=? AND expireDate IS NULL"));
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
