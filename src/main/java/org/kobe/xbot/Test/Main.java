package org.kobe.xbot.Test;

import java.util.Arrays;

public class Main {
    public static void main(String[] args) {
        String[] requestInfo = {"id:put", "2","3","4","5"};
        System.out.println(String.join(" ", Arrays.copyOfRange(requestInfo, 1, requestInfo.length)));
    }
}
