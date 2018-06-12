package com.accountant;

import org.fusesource.jansi.Ansi;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.TreeSet;

public class AutoCleaner implements Runnable {
    private Connection conn;
    @Override
    public void run() {
        Timestamp last = Timestamp.valueOf(LocalDateTime.now().minus(2,ChronoUnit.HOURS));
        while (!Thread.interrupted()){
            try {
                if(last.before(Timestamp.valueOf(LocalDateTime.now().minus(1,ChronoUnit.HOURS)))){
                    MyListener.dbExecutor.pause();
                    String sql = "";
                        try {
                            sql = "SELECT DISTINCT guildid, userId\n " +
                                    "  FROM (\n" +
                                    "    SELECT guildid,userId,expireDate FROM MemberRoles\n" +
                                    "    UNION\n" +
                                    "    SELECT guildid,userId,expireDate FROM MemberNick\n" +
                                    "  ) AS A\n" +
                                    "WHERE expireDate<"+Timestamp.valueOf(LocalDateTime.now());
                            PreparedStatement stmt = conn.prepareStatement(
                                    "SELECT DISTINCT guildid,userId FROM (" +
                                    "    SELECT guildid,userId,expireDate FROM MemberRoles " +
                                    "    UNION " +
                                    "    SELECT guildid,userId,expireDate FROM MemberNick " +
                                    "  ) AS A " +
                                    " WHERE expireDate<?");
                            stmt.setString(1, Timestamp.valueOf(LocalDateTime.now()).toString());
                            ResultSet rs = stmt.executeQuery();
                            if(rs.next()) {
                                Set<Long> guilds = new TreeSet<>();
                                Set<Long> users = new TreeSet<>();
                                int ctn;
                                do{
                                    guilds.add(rs.getLong("guildid"));
                                    users.add(rs.getLong("userid"));
                                }while (rs.next());
                                rs.close();
                                stmt.close();
                                sql = "DELETE FROM MemberRoles WHERE expireDate<"+Timestamp.valueOf(LocalDateTime.now());
                                stmt = conn.prepareStatement("DELETE FROM MemberRoles WHERE expireDate<?");
                                stmt.setString(1, Timestamp.valueOf(LocalDateTime.now()).toString());
                                ctn = stmt.executeUpdate();
                                stmt.close();
                                sql = "DELETE FROM MemberNick WHERE expireDate<"+Timestamp.valueOf(LocalDateTime.now());
                                stmt = conn.prepareStatement("DELETE FROM MemberNick WHERE expireDate<?");
                                stmt.setString(1, Timestamp.valueOf(LocalDateTime.now()).toString());
                                ctn += stmt.executeUpdate();
                                stmt.close();
                                if (ctn > 0) {
                                    conn.commit();
                                    Logger.logger.logGeneral("Cleaned "+users.size()+" expired User"+(users.size()==1?"":"s")
                                            +" in "+guilds.size()+" Guild"+(guilds.size()==1?"":"s"));
                                }
                            }else{
                                rs.close();
                                stmt.close();
                            }
                        } catch (SQLException ex) {
                            try {
                                conn.rollback();
                            } catch (SQLException ignored) {
                            }
                            Logger.logger.logError("SQLError in: " + sql);
                            Logger.logger.logError("SQLException: " + ex.getMessage());
                            Logger.logger.logError("SQLState: " + ex.getSQLState());
                            Logger.logger.logError("VendorError: " + ex.getErrorCode());
                        }
                    MyListener.dbExecutor.resume();
                }
                Thread.sleep(3600000);
            }catch (InterruptedException ex){
                System.err.println(Ansi.ansi().fgRed().a("STOPPING CLEANER THREAD").reset().toString());
                return;
            }
        }
    }

    public AutoCleaner(Connection conn) {
        this.conn = conn;
    }
}
