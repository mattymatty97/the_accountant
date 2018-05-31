package com.accountant.datas;

public class GeneralMsg implements Datas{
    final private String text;
    public GeneralMsg(String text){
        this.text=text;
    }

    @Override
    public String getText(){
        return text;
    }
}
