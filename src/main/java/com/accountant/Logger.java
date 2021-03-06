package com.accountant;

import com.accountant.datas.Datas;
import com.accountant.datas.GeneralMsg;
import com.accountant.datas.GuildMsg;
import com.accountant.datas.RemoteMsg;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.User;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.Semaphore;

import static org.fusesource.jansi.Ansi.ansi;

@SuppressWarnings({"WeakerAccess", "Duplicates"})
public class Logger implements Runnable{
    private static final DateFormat sdf = new SimpleDateFormat("yyyy/MM/dd");
    private static final DateFormat stf = new SimpleDateFormat("HH:mm:ss");
    private static String lastDate="0/0/0";
    private static Queue<Datas> queue = new LinkedList<Datas>(){
        @Override
        public synchronized boolean add(Datas a) {
            return super.add(a);
        }

        @Override
        public synchronized Datas poll(){
            return super.poll();
        }
    };
    private static Semaphore sem = new Semaphore(0);
    public static boolean started = false;

    public static Logger logger = new Logger();
    public static Thread tlogger = new Thread(logger,"Logger Thread");

    public void logMessage(String log, Message message) {

        Date now = new Date();
        String time = stf.format(now);
        StringBuilder sb = new StringBuilder();
        StringBuilder sbt = new StringBuilder();
        Member sender = message.getMember();
        logGeneral("event in guild "+message.getGuild().getName()+" ["+message.getGuild().getId()+"]");

        sbt.append("[").append(time).append("]\t");

        sb.append("messageId [").append(message.getId()).append("]\t| ");
        sb.append("User \"").append(sender.getEffectiveName()).append("\"(").append(sender.getUser().getId()).append(")");
        sb.append(" triggered ").append(log);

        Output.println(sbt.toString() + ansi().fgBrightYellow().a(sb.toString()).reset().toString());

        queue.add(new GuildMsg(now,sbt.toString() + sb.toString(), message.getGuild(),false));
        sem.release();
    }

    public void logReponse(String log,Guild guild,long messageId){

        Date now = new Date();
        String time = stf.format(now);
        StringBuilder sb = new StringBuilder();
        StringBuilder sbt = new StringBuilder();

        sbt.append("[").append(time).append("]\t");

        sb.append("messageId [").append(messageId).append("]\t| ").append(log);

        Output.println(sbt.toString() + ansi().reset() + sb.toString());

        queue.add(new GuildMsg(now,sbt.toString() + sb.toString(), guild,true));
        sem.release();
    }

    public void logEvent(String log,Guild guild){

        Date now = new Date();
        String time = stf.format(now);
        StringBuilder sb = new StringBuilder();

        logGeneral(log+": "+guild.getName());

        sb.append("[").append(time).append("]\t");

        sb.append(log);

        queue.add(new GuildMsg(now,sb.toString(), guild,false));
        sem.release();
    }

    public void logUserEvent(String log,Guild guild,User user){
        String debug = System.getenv().get("DEBUG");
        if (debug == null || debug.isEmpty())
            debug = "";
        else
            debug = "[" + Thread.currentThread().getName() + "]\u2BB7\r\n";

        if (!started) {
            debug = "";
        }
        Date now = new Date();
        String time = stf.format(now);
        StringBuilder sb = new StringBuilder();
        StringBuilder sbt = new StringBuilder();

        sbt.append("[").append(time).append("]\t");

        sb.append("User ").append(user.getName());

        sb.append(" (").append(user.getId()).append(") ");

        sb.append(log);

        Output.println(debug + sbt + ansi().reset().fgBrightYellow().a(sb.toString()+": "+guild.getName()).reset().toString());

        queue.add(new GuildMsg(now,sbt + sb.toString(), guild,false));
        sem.release();
    }

    public void logError(String log){
        String debug = System.getenv().get("DEBUG");
        if (debug == null || debug.isEmpty())
            debug = "";
        else
            debug = "[" + Thread.currentThread().getName() + "]\u2BB7\r\n";

        if (!started) {
            debug = "";
        }

        Date now = new Date();
        String time = stf.format(now);
        StringBuilder sbt = new StringBuilder();
        sbt.append("[").append(time).append("]\t");


        Output.println(debug + sbt.toString() + ansi().fgRed().a(log).reset());


        queue.add(new GeneralMsg(now,sbt.toString()+log));
        sem.release();
    }


    public void logGeneral(String log) {
        String debug = System.getenv().get("DEBUG");
        if (debug == null || debug.isEmpty())
            debug = "";
        else
            debug = "[" + Thread.currentThread().getName() + "]\u2BB7\r\n";

        if (!started) {
            debug = "";
        }

        Date now = new Date();
        String time = stf.format(now);
        StringBuilder sbt = new StringBuilder();
        sbt.append("[").append(time).append("]\t");

        Output.println(debug + sbt.toString() + ansi().fgYellow().a(log).reset());


        queue.add(new GeneralMsg(now,sbt.toString() + log));
        sem.release();
    }

    public void logInit() {
        Date now = new Date();
        String date = sdf.format(now);
        File file = new File("./logs/"+date+"/BOT.log");
        if(file.getParentFile().exists() || file.getParentFile().mkdirs()) {
            try {
                FileWriter fw;
                fw = new FileWriter(file, true);
                fw.append("\r\n\r\n");
                System.out.println("\r\n\r\n");
                fw.flush();
                Global.getGbl().setFwGlobal(fw);
                lastDate=date;
            }catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    private FileWriter openFile(Date current,Guild guild) {
        String date = sdf.format(current);
        File file = new File("./logs/"+date+"/"+guild.getIdLong()+".log");
        if (!date.equals(lastDate)) {
            closeFiles();
            lastDate=date;
        }

        FileWriter fw = Global.getGbl().getFwServers().get(guild.getIdLong());

        if(fw!=null){
            return fw;
        }

        if(file.getParentFile().exists() || file.getParentFile().mkdirs()) {
            try {
                boolean existing=false;
                if(file.exists()){
                    existing=true;
                }
                fw = new FileWriter(file, true);
                if(!existing){
                    fw.append("FILE CREATED FOR GUILD:\r\n").append(guild.getName()).append("\r\n");
                }
                Global.getGbl().getFwServers().put(guild.getIdLong(),fw);
                return fw;
            }catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        return null;
    }

    private FileWriter openFile(Date current) {
        String date = sdf.format(current);
        File file = new File("./logs/"+date+"/BOT.log");
        if (!date.equals(lastDate)) {
            closeFiles();
            lastDate=date;
        }

        FileWriter fw = Global.getGbl().getFwGlobal();
        if(fw!=null)
            return fw;
        if(file.getParentFile().exists() || file.getParentFile().mkdirs()) {
            try {
                fw = new FileWriter(file, true);
                Global.getGbl().setFwGlobal(fw);
                return fw;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    public void closeFiles(){
        Collection<FileWriter> fileWriters = Global.getGbl().getFwServers().values();
        for (FileWriter fw: fileWriters) {
            try {
                fw.flush();
                fw.close();
            }catch (IOException ignored){
            }
        }
        Global.getGbl().getFwServers().clear();
        FileWriter g = Global.getGbl().getFwGlobal();
        try {
            g.flush();
            g.close();
        } catch (IOException ignored) {
        }
        Global.getGbl().setFwGlobal(null);
    }

    @Override
    public void run() {
        synchronized (this) {
            try {
                while (!Thread.interrupted()) {
                    sem.acquire(3);
                    print(true);
                }
            } catch (InterruptedException ignored) {
            } finally {
                System.err.println(ansi().fgYellow().a("Flushing last queued messages").reset());
                print(false);
                System.err.println(ansi().fgRed().a("Exiting logger daemon").reset());
            }
        }
    }

    private void print(boolean yield){
        while (queue.size() > 0) {
            FileWriter fw, fw1, fw2;
            Datas data = queue.poll();
            if (data instanceof GeneralMsg) {
                if ((fw = openFile(data.getDate())) != null) {
                    try {
                        fw.append(data.getText()).append("\r\n");
                        fw.flush();
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                }
            } else if (data instanceof RemoteMsg) {
                RemoteMsg rem = (RemoteMsg)data;
                if (rem.isReponse()) {
                    if ((fw1 = openFile(data.getDate(),rem.getGuild())) != null && (fw2 = openFile(data.getDate(),rem.getRemote())) != null) {
                        try {
                            fw1.append(rem.getText()).append("\r\n");
                            fw2.append(rem.getText()).append("\r\n");
                            fw1.flush();
                            fw2.flush();
                        } catch (IOException ex) {
                            ex.printStackTrace();
                        }
                    }
                } else {
                    if ((fw = openFile(data.getDate(),rem.getGuild())) != null) {
                        try {
                            fw.append(rem.getText());
                            fw.append(" on guild ").append(rem.getRemote().getName());
                            fw.append("\r\n");
                            fw.flush();
                        } catch (IOException ex) {
                            ex.printStackTrace();
                        }
                    }
                    if ((fw = openFile(data.getDate(),rem.getRemote())) != null) {
                        try {
                            fw.append(rem.getText());
                            fw.append(" remotely");
                            fw.append("\r\n");
                            fw.flush();
                        } catch (IOException ex) {
                            ex.printStackTrace();
                        }
                    }
                }
            } else if(data instanceof GuildMsg){
                GuildMsg msg = (GuildMsg)data;
                if ((fw = openFile(data.getDate(),msg.getGuild())) != null) {
                    try {
                        fw.append(msg.getText()).append("\r\n");
                        fw.flush();
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                }
            }
            if(yield)
                Thread.yield();
        }
        int n = sem.availablePermits();
        if(n>0 && queue.size()==0)
            sem.acquireUninterruptibly(n);
    }
}
