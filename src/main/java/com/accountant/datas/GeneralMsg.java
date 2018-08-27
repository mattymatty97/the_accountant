package com.accountant.datas;

import java.util.Date;

public class GeneralMsg implements Datas{
    final private String text;
    final private Date current;


    public GeneralMsg(Date current,String text){
        this.text=text;
        this.current=current;
    }

    @Override
    public String getText(){
        return text;
    }

    @Override
    public Date getDate() {
        return current;
    }
}
