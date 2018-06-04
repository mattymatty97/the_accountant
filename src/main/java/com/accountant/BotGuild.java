package com.accountant;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.*;

public class BotGuild {
        private PreparedStatement[] rmRoleStmt = new PreparedStatement[2];
        private PreparedStatement[] adRoleStmt = new PreparedStatement[3];
        private  PreparedStatement clRoleStmt;
        private  PreparedStatement lsRoleStmt;
        private  PreparedStatement isModStmt;
        private  PreparedStatement isAdminStmt;
        private  PreparedStatement[] delRoleStmt = new PreparedStatement[2];
        private  PreparedStatement[] aRoleStmt = new PreparedStatement[3];

        private List<PreparedStatement> stmts = new ArrayList<>(29);
        
        private Connection conn;



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
                        if(rs.getInt(1)==2) {
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
                sql = "SELECT * FROM roles WHERE guildid=" + guild.getId() + " AND roleid=" + role.getIdLong();
                synchronized (rmRoleStmt) {
                    stmt.setLong(1, guild.getIdLong());
                    stmt.setLong(2, role.getIdLong());
                    rs = stmt.executeQuery();
                    if (rs.next()) {
                        if(rs.getInt(1)==1) {
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
                        if(rs.getInt(1)==1){
                            rs.close();
                            ret = output.getString("error-admin-exists");
                            Logger.logger.logReponse("role is admin", guild, messageId);
                        }else {
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
                        if(rs.getInt(1)==2){
                            rs.close();
                            stmt = adRoleStmt[2];
                            sql = "UPDATE roles SET weight=1 WHERE guildId=" + guild.getId() + " AND roleId=" + role.getIdLong();
                            stmt.setLong(1, guild.getIdLong());
                            stmt.setLong(2, role.getIdLong());
                            if (stmt.executeUpdate() > 0)
                                stmt.getConnection().commit();
                            ret = output.getString("admin-update");
                            Logger.logger.logReponse("role updated" + role.getName(), guild, messageId);

                        }else {
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
            if(type==1)
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
                syncModList(guild, ret, stmt,2);
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
            syncModList(guild, ret, stmt,1);
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
        if(member.getUser().getIdLong()==Long.parseLong(System.getenv("OWNER_ID")))
            return true;

        try {
            stmt = Stmt;
            sql = "SELECT roleid FROM roles WHERE guildid=" + guild;
            synchronized (Stmt) {
                stmt.setLong(1, guild);
                rs = stmt.executeQuery();
                while (rs.next()) {
                    for (Role role : roles) {
                        if (role.getIdLong() == rs.getLong(1)) {
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
                            }else{
                                if(rs.getInt(1)==2)
                                {
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
            } catch (SQLException ex) {
                sqlError(sql, ex);
            }
        return ret;
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
                        if(rs.getInt(1)==2) {
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
                sql = "(remote) SELECT * FROM roles WHERE guildid=" + guild.getId() + " AND roleid=" + role.getIdLong();
                synchronized (rmRoleStmt) {
                    stmt.setLong(1, guild.getIdLong());
                    stmt.setLong(2, role.getIdLong());
                    rs = stmt.executeQuery();
                    if (rs.next()) {
                        if(rs.getInt(1)==1) {
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
                    } else
                        if(rs.getInt(1)==1){
                            rs.close();
                            ret = output.getString("error-admin-exists");
                            Logger.logger.logRemoteRep("role is admin", guild, messageId,remote);
                        }else {
                            rs.close();
                            ret = output.getString("error-mod-exists");
                            Logger.logger.logRemoteRep("role is mod", guild, messageId,remote);
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
                        if(rs.getInt(1)==2){
                            rs.close();
                            stmt = adRoleStmt[2];
                            sql = "UPDATE roles SET weight=1 WHERE guildId=" + remote.getId() + " AND roleId=" + role.getIdLong();
                            stmt.setLong(1, remote.getIdLong());
                            stmt.setLong(2, role.getIdLong());
                            if (stmt.executeUpdate() > 0)
                                stmt.getConnection().commit();
                            ret = output.getString("admin-update");
                            Logger.logger.logRemoteRep("role updated" + role.getName(), guild, messageId,remote);
                        }else {
                            rs.close();
                            ret = output.getString("error-admin-exists");
                            Logger.logger.logRemoteRep("role is admin", guild, messageId,remote);
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
                    stmt.setLong(2,2);
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
                    stmt.setLong(2,1);
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
                syncModList(remote, ret, stmt,2);
            } catch (SQLException ex) {
                return sqlError(sql, ex);
            }
        Logger.logger.logRemoteRep("listed mods", guild, messageId,remote);
        return ret.toString();
    }

    public String listRemoteAdmin(Guild guild, ResourceBundle output, long messageId, Guild remote) {
        String sql = "";
        StringBuilder ret = new StringBuilder(output.getString("mod-list"));
        PreparedStatement stmt;
            try {
                stmt = lsRoleStmt;
                sql = "(remote) SELECT roleid FROM roles WHERE guildid=" + guild.getIdLong();
                syncModList(remote, ret, stmt,1);
            } catch (SQLException ex) {
                return sqlError(sql, ex);
            }
        Logger.logger.logRemoteRep("listed admins", guild, messageId,remote);
        return ret.toString();
    }


    private void syncModList(Guild guild, StringBuilder ret, PreparedStatement stmt,int type) throws SQLException {
        ResultSet rs;
            stmt.setLong(1,guild.getIdLong());
            stmt.setLong(2,type);
            rs = stmt.executeQuery();
            while (rs.next()) {
                Role role = guild.getRoleById(rs.getLong(1));
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
        }catch (SQLException ignored){}
        Logger.logger.logError("SQLError in : "+ sql);
        Logger.logger.logError(ex.getMessage());
        Logger.logger.logError("SQLState: " + ex.getSQLState());
        Logger.logger.logError("VendorError: " + ex.getErrorCode());
        return null;
    }


    BotGuild(Connection conn) {
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

    public void close (){
        for (PreparedStatement stmt : stmts){
            try {
                stmt.close();
            } catch (SQLException ignored) {
            }
        }
    }


}
