package edu.buffalo.cse.cse486586.groupmessenger2;

import java.util.Comparator;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Created by sajidkhan on 3/4/18.
 */

public class CustomPriorityQueue extends PriorityQueue {

    @Override
    public Comparator comparator() {

        return  new Comparator<Map.Entry<Integer, String>>() {
            @Override
            public int compare(Map.Entry<Integer, String> e1, Map.Entry<Integer, String> e2) {
                return e2.getKey().compareTo(e1.getKey());
            }
        };
    }
}
