import com.accountant.*;
import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;
import net.dv8tion.jda.core.entities.Game;
import org.fusesource.jansi.AnsiConsole;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;

import static org.fusesource.jansi.Ansi.ansi;
public class BOT
{
    public static void main(String[] arguments) throws Exception
    {
        System.out.print((char)27+"[?25l");
        Connection conn=null;
        AnsiConsole.systemInstall();

        testEnv();

        Thread mine = Thread.currentThread();



        Logger.tlogger.setPriority(Thread.NORM_PRIORITY - 2);
        Logger.tlogger.start();

        Logger.logger.logInit();
        Logger.logger.logGeneral("-----------STARTING SYSTEM------------");
        Logger.logger.logGeneral("SYSTEM VERSION: "+Global.version);
        Logger.logger.logGeneral("BUILD: "+Global.build+"\r\n");

        try {
            String path= System.getenv("DATABASE_PATH");
            Logger.logger.logGeneral("Connecting to: "+ path);
            conn = DriverManager.getConnection("jdbc:sqlite:"+path);
            conn.setAutoCommit(false);
            Logger.logger.logGeneral("SQL INITIALIZZATED");
        } catch (SQLException ex) {
            Logger.logger.logGeneral(ansi().fgRed()+"SQLException: " + ex.getMessage());
            Logger.logger.logGeneral(ansi().fgRed()+"SQLState: " + ex.getSQLState());
            Logger.logger.logGeneral(ansi().fgRed()+"VendorError: " + ex.getErrorCode());
            System.exit(-1);
        }

        Thread ac = new Thread(new AutoCleaner(conn),"Cleaner Thread");
        ac.setPriority(Thread.NORM_PRIORITY - 1);
        ac.start();

        JDA api = new JDABuilder(AccountType.BOT).setToken(System.getenv("BOT_TOKEN")).buildAsync();

        MyListener listener = new MyListener(conn);

        Runtime.getRuntime().addShutdownHook(new Thread(()-> {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException ignored) {
            }
            mine.interrupt();
            Logger.started = false;
            System.out.println((char)27+"[?25h");
            System.err.println(ansi().fgRed().a("Closing program").reset());
            ac.interrupt();
            listener.close();
            Logger.tlogger.interrupt();
            try {
                Logger.tlogger.join();
            }catch (Exception ignore){}
            Logger.logger.closeFiles();
        }));

        api.addEventListener(listener);
        api.getPresence().setGame(Game.playing(Global.version));

        while (!Logger.started && !Thread.interrupted()) ;

        Output.run();
    }

    private static void testEnv() throws Exception {
        Map<String, String> env = System.getenv();

        String var = env.get("BOT_TOKEN");
        if (var == null || var.isEmpty())
            throw new Exception("Missing environement variable: BOT_TOKEN");

        var = env.get("DATABASE_PATH");
        if (var == null || var.isEmpty())
            throw new Exception("Missing environement variable: DATABASE_URL");

        var = env.get("BOT_PREFIX");
        if (var == null || var.isEmpty())
            throw new Exception("Missing environement variable: DEFAULT_EMOJI_PREFIX");

        var = env.get("DISCORDBOTS_KEY");
        if (var == null)
            throw new Exception("Missing environement variable: DISCORDBOTS_KEY (can be empty)");

        var = env.get("SUPPORT_GUILD_ID");
        if (var == null || var.isEmpty())
            throw new Exception("Missing environement variable: SUPPORT_GUILD_ID");
        else
            try {
                Long.parseLong(var);
            } catch (NumberFormatException ex) {
                throw new Exception("Environement variable ( SUPPORT_GUILD_ID ) is not valid");
            }

        var = env.get("OWNER_ID");
        if (var == null || var.isEmpty() || Long.parseLong(var) == 0)
            throw new Exception("Missing environement variable: OWNER_ID");
        else
            try {
                Long.parseLong(var);
            } catch (NumberFormatException ex) {
                throw new Exception("Environement variable ( OWNER_ID ) is not valid");
            }

    }

}
