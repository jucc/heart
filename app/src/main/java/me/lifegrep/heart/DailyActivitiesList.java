/**
 * Lifegrep daily activities list
 * Juliana Cavalcanti - 2017/03
 */
package me.lifegrep.heart;

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;


/**
 * TODO instead of a fixed list, read from database and allow the user to create custom activities
 */

public class DailyActivitiesList {

    public static HashMap<String, List<String>> getData() {

        HashMap<String, List<String>> listDetail = new HashMap<String, List<String>>();

        List<String> laying = new ArrayList<String>();
        laying.add("sleeping");
        laying.add("laying down resting");

        List<String> sitting = new ArrayList<String>();
        sitting.add("reading");
        sitting.add("playing");
        sitting.add("sitting resting");
        sitting.add("eating");
        sitting.add("commute");

        List<String> standing = new ArrayList<String>();
        standing.add("household chores");
        standing.add("standing still");
        standing.add("line");

        List<String> moving = new ArrayList<String>();
        moving.add("exercise");
        moving.add("walking commute");

        listDetail.put("LAYING DOWN", laying);
        listDetail.put("SITTING", sitting);
        listDetail.put("STANDING", standing);
        listDetail.put("MOVING", moving);

        return listDetail;
    }
}