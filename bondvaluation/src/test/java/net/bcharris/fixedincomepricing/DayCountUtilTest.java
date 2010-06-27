package net.bcharris.fixedincomepricing;

import org.junit.Test;
import static org.junit.Assert.*;

public class DayCountUtilTest {

    @Test
    public void test_period_length_and_periods_per_year_consistency() {
        for (int i = 1; i <= 360; i++) {
            int pl = DayCountUtil.periodLength(i);
            int ppy = DayCountUtil.periodsPerYear(i);
            assertEquals("periodLength, i=" + i, pl, DayCountUtil.periodLength(DayCountUtil.periodsPerYear(pl)));
            assertEquals("periodsPerYear, i=" + i, ppy, DayCountUtil.periodsPerYear(DayCountUtil.periodLength(ppy)));
        }
    }
}
