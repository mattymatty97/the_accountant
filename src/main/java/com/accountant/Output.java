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
        int lastE;
        synchronized (Global.eventQueue) {
            lastE = Optional.ofNullable(Global.eventQueue.peek()).orElse(Global.maxEventCtn) - 1;
        }
        int lastT=Thread.activeCount();

        System.out.print(ansi().fgCyan().a("Event Threads: ").fgBrightGreen().a(lastE).a("\\").a(lastT));
        while (!Thread.interrupted() && Logger.started) {
            try {
                Thread.sleep(300);
            }catch (InterruptedException ignored){}
            threads =Thread.activeCount();
            synchronized (Global.eventQueue) {
                events = Optional.ofNullable(Global.eventQueue.peek()).orElse(Global.maxEventCtn) - 1;
            }
            if(threads != lastT || events!=lastE) {
                synchronized (System.out) {
                    for(int i = 0; i<(lastE +"\\"+ lastT).length(); i++)
                        System.out.print("\b");
                    System.out.print(ansi().fgBrightGreen().a(events).a("\\").a(threads));
                }
            }
            lastT= threads;
            lastE= events;
        }
    }
}
