package com.accountant;

import static org.fusesource.jansi.Ansi.ansi;

public class Output {
    private static int act=0;
    public static void println(String st){
        if (Logger.started) {
            synchronized (System.out){
                System.out.print("\r                          \r"+ansi().reset());
                System.out.println(st);
                System.out.print(ansi().fgCyan().a("Active Threads: ").fgBrightGreen().a(act));
            }
        }
        else
            System.out.println(st);
    }

    public static void run(){
        int last=0;
        System.out.print(ansi().fgCyan().a("Active Threads: "));
        while (!Thread.interrupted() && Logger.started) {
            try {
                Thread.sleep(300);
            }catch (InterruptedException ignored){}
            act=Thread.activeCount();
            if(act!=last) {
                synchronized (System.out) {
                    for(int i=0;i<String.valueOf(last).length();i++)
                        System.out.print("\b");
                    System.out.print(ansi().fgBrightGreen().a(act));
                }
            }
            last=act;
        }
    }
}
