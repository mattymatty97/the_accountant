package com.accountant;

import org.fusesource.jansi.Ansi;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public class AutoCleaner implements Runnable {
    private Connection conn;
    @Override
    public void run() {
        Timestamp last = Timestamp.valueOf(LocalDateTime.now().minus(2,ChronoUnit.HOURS));
        while (!Thread.interrupted()){
            try {
                if(last.before(Timestamp.valueOf(LocalDateTime.now().minus(1,ChronoUnit.HOURS)))){
                    MyListener.dbExecutor.pause();
                    String sql = "DELETE FROM MemberRoles WHERE expireDate<"+Timestamp.valueOf(LocalDateTime.now());
                        try {
                            PreparedStatement stmt = conn.prepareStatement("SELECT FROM MemberRoles WHERE expireDate<?");
                            stmt.setString(1, Timestamp.valueOf(LocalDateTime.now()).toString());
                            ResultSet rs = stmt.executeQuery();
                            if(rs.next()) {
                                rs.close();
                                stmt.close();
                                stmt = conn.prepareStatement("DELETE FROM MemberRoles WHERE expireDate<?");
                                stmt.setString(1, Timestamp.valueOf(LocalDateTime.now()).toString());
                                if (stmt.executeUpdate() > 0) {
                                    conn.commit();
                                    Logger.logger.logGeneral("Cleaning expired users");
                                }
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
