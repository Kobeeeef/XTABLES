package org.kobe.xbot.Test;


import java.util.ArrayList;
import java.util.List;

public class Main {
    private static final List<Integer> MESSAGES = new ArrayList<>() {
        @Override
        public boolean add(Integer i) {
            boolean added = super.add(i);
            while (added && size() > 10) {
                super.remove(0);
            }
            return added;
        }
    };
    public static void main(String[] args) {
       for (int i = 1; i<= 200; i++) {
           MESSAGES.add(i);
           System.out.println(MESSAGES);
       }
    }
}
