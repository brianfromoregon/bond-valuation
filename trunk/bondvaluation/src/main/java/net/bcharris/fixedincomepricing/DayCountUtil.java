package net.bcharris.fixedincomepricing;

public class DayCountUtil {

    public static int periodsPerYear(int periodLength) {
        return 360 / periodLength;
    }

    public static int periodLength(int periodsPerYear) {
        return 360 / periodsPerYear;
    }
}
