package com.accountant;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.*;

public class BotGuild {
        private PreparedStatement[] rmModStmt = new PreparedStatement[2];
        private PreparedStatement[] adModStmt = new PreparedStatement[2];
        private  PreparedStatement clModStmt;
        private  PreparedStatement lsModStmt;
        private  PreparedStatement isModStmt;
        private  PreparedStatement[] delRolStmt = new PreparedStatement[2];
        private  PreparedStatement[] aModStmt = new PreparedStatement[2];

        private List<PreparedStatement> stmts = new ArrayList<>(29);
        
        private Connection conn;



    public String removeModRole(Role role, Guild guild, ResourceBundle output, long messageId) {
        String sql = "";
        String ret;
        PreparedStatement stmt;
        ResultSet rs;
        synchronized (conn) {
            try {
                stmt = rmModStmt[0];
                sql = "SELECT * FROM roles WHERE guildid=" + guild.getId() + " AND roleid=" + role.getIdLong();
                synchronized (rmModStmt) {
                    stmt.setLong(1, guild.getIdLong());
                    stmt.setLong(2, role.getIdLong());
                    rs = stmt.executeQuery();
                    if (rs.next()) {
                        rs.close();
                        stmt = rmModStmt[1];
                        sql = "DELETE FROM roles WHERE guildid=" + guild.getId() + " AND roleid=" + role.getIdLong();
                        stmt.setLong(1, guild.getIdLong());
                        stmt.setLong(2, role.getIdLong());
                        stmt.executeUpdate();
                        stmt.getConnection().commit();
                        ret = output.getString("modrole-remove");
                        Logger.logger.logReponse("removed role " + role.getName(), guild, messageId);
                    } else {
                        rs.close();
                        ret = output.getString("error-modrole-missing");
                        Logger.logger.logReponse("role not modrole", guild, messageId);
                    }
                }
            } catch (SQLException ex) {
                return sqlError(sql, ex);
            }
        }
        return ret;
    }



    public String addModRole(Role role, Guild guild, ResourceBundle output, long messageId) {
        String sql = "";
        String ret;
        PreparedStatement stmt;
        ResultSet rs;
            try {
                stmt = adModStmt[0];
                sql = "SELECT * FROM roles WHERE guildid=" + guild.getId() + " AND roleid=" + role.getIdLong();
                synchronized (adModStmt) {
                    stmt.setLong(1, guild.getIdLong());
                    stmt.setLong(2, role.getIdLong());
                    rs = stmt.executeQuery();
                    if (!rs.next()) {
                        rs.close();
                        stmt = adModStmt[1];
                        sql = "INSERT INTO roles (guildid,roleid,rolename) VALUES (" + guild.getId() + "," + role.getIdLong() + ",'" + role.getName().replaceAll("[\',\"]", "") + "')";
                        stmt.setLong(1, guild.getIdLong());
                        stmt.setLong(2, role.getIdLong());
                        stmt.setString(3, role.getName().replaceAll("[\',\"]", ""));
                        if (stmt.executeUpdate() > 0)
                            stmt.getConnection().commit();
                        ret = output.getString("modrole-add");
                        Logger.logger.logReponse("added role " + role.getName(), guild, messageId);
                    } else {
                        rs.close();
                        ret = output.getString("error-modrole-exists");
                        Logger.logger.logReponse("role is modrole", guild, messageId);
                    }
                }
            } catch (SQLException ex) {
                return sqlError(sql, ex);
            }
        return ret;
    }

    public String clearModrole(Guild guild, ResourceBundle output, long messageId) {
        String sql = "";
        String ret;
        PreparedStatement stmt;
            try {
                stmt = clModStmt;
                sql = "DELETE FROM roles WHERE guildid=" + guild.getId();
                synchronized (clModStmt) {
                    stmt.setLong(1, guild.getIdLong());
                    stmt.executeUpdate();
                    stmt.getConnection().commit();
                }
                ret = output.getString("modrole-clear");
                Logger.logger.logReponse("cleared modroles", guild, messageId);
            } catch (SQLException ex) {
                return sqlError(sql, ex);
            }
        return ret;
    }

    public String listModrole(Guild guild, ResourceBundle output, long messageId) {
        String sql = "";
        StringBuilder ret = new StringBuilder(output.getString("modrole-list"));
        PreparedStatement stmt;
            try {
                stmt = lsModStmt;
                sql = "SELECT roleid FROM roles WHERE guildid=" + guild.getIdLong();
                syncModroleList(guild, ret, stmt);
            } catch (SQLException ex) {
                return sqlError(sql, ex);
            }
        Logger.logger.logReponse("listed modroles", guild, messageId);
        return ret.toString();
    }

    public boolean memberIsMod(Member member, long guild) {
        String sql = "";
        List<Role> roles = member.getRoles();
        PreparedStatement stmt;
        ResultSet rs;
        if(member.getUser().getIdLong()==Long.parseLong(System.getenv("OWNER_ID")))
            return true;

            try {
                stmt = isModStmt;
                sql = "SELECT roleid FROM roles WHERE guildid=" + guild;
                synchronized (isModStmt) {
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

    public void autoModRole(Guild guild) {
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
                        stmt = aModStmt[0];
                        sql = "SELECT * FROM roles WHERE guildid=" + guildId + " AND roleid=" + role.getIdLong();
                        synchronized (aModStmt) {
                            stmt.setLong(1, guildId);
                            stmt.setLong(2, role.getIdLong());
                            rs = stmt.executeQuery();
                            if (!rs.next()) {
                                rs.close();
                                stmt = aModStmt[1];
                                sql = "INSERT INTO roles (guildid,roleid,rolename) VALUES (" + guildId + "," + role.getIdLong() + ",'" + role.getName().replaceAll("[\',\"]", "") + "')";
                                stmt.setLong(1, guildId);
                                stmt.setLong(2, role.getIdLong());
                                stmt.setString(3, role.getName().replaceAll("[\',\"]", ""));
                                if (stmt.executeUpdate() > 0)
                                    stmt.getConnection().commit();
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
                stmt = delRolStmt[0];
                sql = "SELECT * FROM roles WHERE guildid=" + role.getGuild().getIdLong() + " AND roleid=" + role.getIdLong();
                synchronized (delRolStmt) {
                    stmt.setLong(1, role.getGuild().getIdLong());
                    stmt.setLong(2, role.getIdLong());
                    rs = stmt.executeQuery();
                    if (rs.next()) {
                        rs.close();
                        stmt = delRolStmt[1];
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

    public String removeRemoteModRole(Role role, Guild guild, ResourceBundle output, long messageId,Guild remote) {
        String sql = "";
        String ret;
        PreparedStatement stmt;
        ResultSet rs;
            try {
                stmt = rmModStmt[0];
                sql = "(remote) SELECT * FROM roles WHERE guildid=" + guild.getId() + " AND roleid=" + role.getIdLong();
                synchronized (rmModStmt) {
                    stmt.setLong(1, guild.getIdLong());
                    stmt.setLong(2, role.getIdLong());
                    rs = stmt.executeQuery();
                    if (rs.next()) {
                        rs.close();
                        stmt = rmModStmt[1];
                        sql = "(remote) DELETE FROM roles WHERE guildid=" + guild.getId() + " AND roleid=" + role.getIdLong();
                        stmt.setLong(1, guild.getIdLong());
                        stmt.setLong(2, role.getIdLong());
                        stmt.executeUpdate();
                        stmt.getConnection().commit();
                        ret = output.getString("modrole-remove");
                        Logger.logger.logRemoteRep("removed role " + role.getName(), guild, messageId, remote);
                    } else {
                        rs.close();
                        ret = output.getString("error-modrole-missing");
                        Logger.logger.logRemoteRep("role not modrole", guild, messageId, remote);
                    }
                }
            } catch (SQLException ex) {
                return sqlError(sql, ex);
            }
        return ret;
    }

    public String addRemoteModRole(Role role, Guild guild, ResourceBundle output, long messageId,Guild remote) {
        String sql = "";
        String ret;
        PreparedStatement stmt;
        ResultSet rs;
            try {
                stmt = adModStmt[0];
                sql = "(remote) SELECT * FROM roles WHERE guildid=" + guild.getId() + " AND roleid=" + role.getIdLong();
                synchronized (adModStmt) {
                    stmt.setLong(1, guild.getIdLong());
                    stmt.setLong(2, role.getIdLong());
                    rs = stmt.executeQuery();
                    if (!rs.next()) {
                        rs.close();
                        stmt = adModStmt[1];
                        sql = "(remote) INSERT INTO roles (guildid,roleid,rolename) VALUES (" + guild.getId() + "," + role.getIdLong() + ",'" + role.getName().replaceAll("[\',\"]", "") + "')";
                        stmt.setLong(1, guild.getIdLong());
                        stmt.setLong(2, role.getIdLong());
                        stmt.setString(3, role.getName().replaceAll("[\',\"]", ""));
                        if (stmt.executeUpdate() > 0)
                            stmt.getConnection().commit();
                        ret = output.getString("modrole-add");
                        Logger.logger.logRemoteRep("added role " + role.getName(), guild, messageId, remote);
                    } else {
                        rs.close();
                        ret = output.getString("error-modrole-exists");
                        Logger.logger.logRemoteRep("role is modrole", guild, messageId, remote);
                    }
                }
            } catch (SQLException ex) {
                return sqlError(sql, ex);
            }
        return ret;
    }

    public String clearRemoteModrole(Guild guild, ResourceBundle output, long messageId,Guild remote) {
        String sql = "";
        String ret;
        PreparedStatement stmt;
            try {
                stmt = clModStmt;
                sql = "(remote) DELETE FROM roles WHERE guildid=" + guild.getId();
                synchronized (clModStmt) {
                    stmt.setLong(1, guild.getIdLong());
                    stmt.executeUpdate();
                    stmt.getConnection().commit();
                }
                ret = output.getString("modrole-clear");
                Logger.logger.logRemoteRep("cleared modroles", guild, messageId, remote);
            } catch (SQLException ex) {
                return sqlError(sql, ex);
            }
        return ret;
    }

    public String listRemoteModrole(Guild guild, ResourceBundle output, long messageId,Guild remote) {
        String sql = "";
        StringBuilder ret = new StringBuilder(output.getString("modrole-list"));
        PreparedStatement stmt;
            try {
                stmt = lsModStmt;
                sql = "(remote) SELECT roleid FROM roles WHERE guildid=" + guild.getIdLong();
                syncModroleList(guild, ret, stmt);
            } catch (SQLException ex) {
                return sqlError(sql, ex);
            }
        Logger.logger.logRemoteRep("listed modroles", guild, messageId,remote);
        return ret.toString();
    }


    private void syncModroleList(Guild guild, StringBuilder ret, PreparedStatement stmt) throws SQLException {
        ResultSet rs;
            stmt.setLong(1,guild.getIdLong());
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
                stmts.add(this.rmModStmt[0] = conn.prepareStatement("SELECT * FROM roles WHERE guildid=? AND roleid=?"));
                stmts.add(this.rmModStmt[1] = conn.prepareStatement("DELETE FROM roles WHERE guildid=? AND roleid=?"));
                stmts.add(this.adModStmt[0] = rmModStmt[0]);
                stmts.add(this.adModStmt[1] = conn.prepareStatement("INSERT INTO roles (guildid,roleid,rolename) VALUES (?,?,?)"));
                stmts.add(this.clModStmt = conn.prepareStatement("DELETE FROM roles WHERE guildid=?"));
                stmts.add(this.lsModStmt = conn.prepareStatement("SELECT roleid FROM roles WHERE guildid=?"));
                stmts.add(this.aModStmt[0] = conn.prepareStatement("SELECT * FROM roles WHERE guildid=? AND roleid=?"));
                stmts.add(this.aModStmt[1] = conn.prepareStatement("INSERT INTO roles (guildid,roleid,rolename) VALUES (?,?,?)"));
                stmts.add(this.delRolStmt[0] = aModStmt[0]);
                stmts.add(this.delRolStmt[1] = conn.prepareStatement("DELETE FROM roles WHERE guildid=? AND roleid=?"));
                stmts.add(this.isModStmt = conn.prepareStatement("SELECT roleid FROM roles WHERE guildid=?"));
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
