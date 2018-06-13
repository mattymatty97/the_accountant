package com.accountant;

import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.Message;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import com.accountant.datas.*;
import net.dv8tion.jda.core.entities.User;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.Semaphore;

import static org.fusesource.jansi.Ansi.ansi;

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


        String time = stf.format(new Date());
        StringBuilder sb = new StringBuilder();
        Member sender = message.getMember();
        logGeneral("event in guild "+message.getGuild().getName()+" ["+message.getGuild().getId()+"]");

        sb.append("[").append(time).append("]\t");

        sb.append("messageId [").append(message.getId()).append("]\t| ");
        sb.append("User \"").append(sender.getEffectiveName()).append("\"(").append(sender.getUser().getId()).append(")");
        sb.append(" triggered ").append(log);

        Output.println(ansi().fgBrightYellow().a(sb.toString()).reset().toString());

        queue.add(new GuildMsg(sb.toString(), message.getGuild(),false));
        sem.release();

        LogLinker act = Global.getGbl().getMapGuild().get(message.getGuild().getIdLong());
        if(act!=null){
            EmbedBuilder builder = act.getMessage();
            builder.setAuthor(message.getAuthor().getName(),null,message.getAuthor().getAvatarUrl());
            builder.setDescription(message.getRawContent());
        }
    }

    public void logReponse(String log,Guild guild,long messageId){

        String time = stf.format(new Date());
        StringBuilder sb = new StringBuilder();

        sb.append("[").append(time).append("]\t");

        sb.append("messageId [").append(messageId).append("]\t| ").append(log);

        Output.println(ansi().reset() + sb.toString());

        queue.add(new GuildMsg(sb.toString(), guild,true));
        sem.release();

        LogLinker act = Global.getGbl().getMapGuild().get(guild.getIdLong());
        if(act!=null)
        {
            EmbedBuilder build = act.getMessage();
            build.addField("Reponse",log,false);
            act.getChannel().sendMessage(build.build()).queue();
            build.clearFields();
        }
    }

    public void logEvent(String log,Guild guild){

        String time = stf.format(new Date());
        StringBuilder sb = new StringBuilder();

        logGeneral(log+": "+guild.getName());

        sb.append("[").append(time).append("]\t");

        sb.append(log);

        queue.add(new GuildMsg(sb.toString(), guild,false));
        sem.release();

        LogLinker act = Global.getGbl().getMapGuild().get(guild.getIdLong());
        if(act!=null)
        {
            EmbedBuilder build = act.getMessage();
            build.setAuthor(guild.getName(),null,guild.getIconUrl());
            build.setDescription("");
            build.addField("EVENT",log+": "+guild.getName(),false);
            act.getChannel().sendMessage(build.build()).queue();
            build.clearFields();
        }
    }

    public void logUserEvent(String log,Guild guild,User user){

        String time = stf.format(new Date());
        StringBuilder sb1 = new StringBuilder();
        StringBuilder sb2 = new StringBuilder();

        sb1.append("[").append(time).append("]\t");

        sb2.append("User ").append(user.getName());

        sb2.append(" (").append(user.getId()).append(") ");

        sb2.append(log);

        Output.println(ansi().reset().fgBrightYellow().a(sb2.toString()+": "+guild.getName()).reset().toString());

        queue.add(new GuildMsg(sb1.toString()+sb2.toString(), guild,false));
        sem.release();

        LogLinker act = Global.getGbl().getMapGuild().get(guild.getIdLong());
        if(act!=null)
        {
            EmbedBuilder build = act.getMessage();
            build.setAuthor(guild.getName(),null,guild.getIconUrl());
            build.setDescription("");
            build.addField("EVENT",log+": "+guild.getName(),false);
            act.getChannel().sendMessage(build.build()).queue();
            build.clearFields();
        }
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

        String time = stf.format(new Date());
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(time).append("]\t");
        sb.append(log);


        Output.println(debug + ansi().fgRed().a(sb.toString()).reset());


        queue.add(new GeneralMsg(sb.toString()));
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

        String time = stf.format(new Date());
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(time).append("]\t");
        sb.append(log);


        Output.println(debug + ansi().fgYellow().a(sb.toString()).reset());


        queue.add(new GeneralMsg(sb.toString()));
        sem.release();
    }

    public void logRemoteMsg(String log, Message message, Guild guild){

        String time = stf.format(new Date());
        StringBuilder sb = new StringBuilder();
        Member sender = message.getMember();
        logGeneral("event in guild "+message.getGuild().getName()+" ["+message.getGuild().getId()+"]");
                sb.append("[").append(time).append("]\t");

                sb.append("messageId [").append(message.getId()).append("]\t| ");
                sb.append("User \"").append(sender.getEffectiveName()).append("\"(").append(sender.getUser().getId()).append(")");
                sb.append(" triggered ").append(log);
                sb.append("[").append(guild.getId()).append("]");

        synchronized (System.out) {
            System.out.println(ansi().fgBrightYellow().a(sb.toString()).reset());
        }

        queue.add(new RemoteMsg(sb.toString(), message.getGuild(), guild, false));

        sem.release();
    }

    public void logRemoteRep(String log,Guild guild,long messageId,Guild remote){

        String time = stf.format(new Date());
        StringBuilder sb = new StringBuilder();

        sb.append("[").append(time).append("]\t");
        sb.append("messageId [").append(messageId).append("]\t| ").append(log);


        Output.println(sb.toString());


        queue.add(new RemoteMsg(sb.toString(), guild, remote, true));
        sem.release();
    }


    public void logInit()
    {
        String date = sdf.format(new Date());
        File file = new File("./logs/"+date+"/BOT.log");
        if(file.getParentFile().exists() || file.getParentFile().mkdirs())
        {
            try {
                FileWriter fw;
                fw = new FileWriter(file, true);
                fw.append("\r\n\r\n");
                System.out.println("\r\n\r\n");
                fw.flush();
                Global.getGbl().setFwGlobal(fw);
                lastDate=date;
            }catch (IOException ex)
            {
                ex.printStackTrace();
            }
        }
    }

    private FileWriter openFile(Guild guild)
    {
        String date = sdf.format(new Date());
        File file = new File("./logs/"+date+"/"+guild.getIdLong()+".log");
        if (!date.equals(lastDate)) {
            closeFiles();
            lastDate=date;
        }

        FileWriter fw = Global.getGbl().getFwServers().get(guild.getIdLong());

        if(fw!=null){
            return fw;
        }

        if(file.getParentFile().exists() || file.getParentFile().mkdirs())
        {
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
            }catch (IOException ex)
            {
                ex.printStackTrace();
            }
        }
        return null;
    }

    private FileWriter openFile()
    {
        String date = sdf.format(new Date());
        File file = new File("./logs/"+date+"/BOT.log");
        if (!date.equals(lastDate)) {
            closeFiles();
            lastDate=date;
        }

        FileWriter fw = Global.getGbl().getFwGlobal();
        if(fw!=null)
            return fw;
        if(file.getParentFile().exists() || file.getParentFile().mkdirs())
        {
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
                if ((fw = openFile()) != null) {
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
                    if ((fw1 = openFile(rem.getGuild())) != null && (fw2 = openFile(rem.getRemote())) != null) {
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
                    if ((fw = openFile(rem.getGuild())) != null) {
                        try {
                            fw.append(rem.getText());
                            fw.append(" on guild ").append(rem.getRemote().getName());
                            fw.append("\r\n");
                            fw.flush();
                        } catch (IOException ex) {
                            ex.printStackTrace();
                        }
                    }
                    if ((fw = openFile(rem.getRemote())) != null) {
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
            } else {
                GuildMsg msg = (GuildMsg)data;
                if ((fw = openFile(msg.getGuild())) != null) {
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
