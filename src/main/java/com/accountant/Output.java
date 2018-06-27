package com.accountant;

import net.dv8tion.jda.core.entities.Guild;

import java.util.Optional;

import static org.fusesource.jansi.Ansi.ansi;

public class Output {
    private static int threads=0;
    private static int events =0;

    public static void println(Object obj) {
        println(obj.toString());
    }

    public static void println(Guild guild) {
        println(guild.toString() + " Members:" + guild.getMembers().size());
    }

    public static void println(String st){
        if (Logger.started) {
            synchronized (System.out){
                System.out.print("\r                              \r"+ansi().reset());
                System.out.println(st);
                System.out.print(ansi().fgCyan().a("Event Threads: ").fgBrightGreen().a(events).a("\\").a(threads));
            }
        }
        else
            System.out.println(st);
    }

    public static void run(){
        int laste=Optional.ofNullable(Global.eventQueue.peek()).orElse(0);
        int lastt=Thread.activeCount();;

        System.out.print(ansi().fgCyan().a("Event Threads: ").fgBrightGreen().a(laste).a("\\").a(lastt));
        while (!Thread.interrupted() && Logger.started) {
            try {
                Thread.sleep(300);
            }catch (InterruptedException ignored){}
            threads =Thread.activeCount();
            events = Optional.ofNullable(Global.eventQueue.peek()).orElse(Global.maxEventCtn);
            if(threads != lastt || events!=laste) {
                synchronized (System.out) {
                    for(int i = 0; i<(laste +"\\"+ lastt).length(); i++)
                        System.out.print("\b");
                    System.out.print(ansi().fgBrightGreen().a(events).a("\\").a(threads));
                }
            }
            lastt= threads;
            laste= events;
        }
    }
}
