package net.bcharris.fixedincomepricing;

import static com.google.common.base.Preconditions.*;

public class Calc {

    /**
     * Calculate price.
     * @param daysToMaturity Number of days until maturity, must be >= 1.
     * @param factors The factors for each period.  The array's last value need not be 0 because this function assumes
     * the factor goes to 0 at maturity.
     * @param rates The effective coupon rates for each period, each having been divided by the number of periods per year.
     * @param periodYield The required rate of return divided by the number of periods per year.
     * @param periodLength The length of a period.
     * @param payDelay The payment delay for cash flows.
     * @return The price.
     */
    public static double price(int daysToMaturity, double[] factors, double rates[], double periodYield, int periodLength, int payDelay) {
        int numCashFlows = daysToMaturity / periodLength;
        double partialPeriod = daysToMaturity % periodLength;
        if (partialPeriod > 0) {
            numCashFlows++;
        }
        checkArgument(factors.length >= numCashFlows, "factors.length >= # of future cash flows");
        checkArgument(rates.length == factors.length, "rates.length == factors.length");

        double px = 0;
        int periodIdx = numCashFlows;
        boolean finalPeriod = true;
        for (int i = 0; i < numCashFlows; i++) {
            px *= 1 / (1 + periodYield);
            double factor;
            if (finalPeriod) {
                factor = 0;
                finalPeriod = false;
            } else {
                factor = factors[periodIdx];
            }
            double prevFactor = factors[periodIdx - 1];

            px += prevFactor * rates[periodIdx - 1]; // coupon amount
            px += prevFactor - factor; // paydown amount
            periodIdx--;
        }

        if (partialPeriod == 0) {
            px *= 1 / (1 + periodYield);
        } else {
            px *= 1 / Math.pow(1 + periodYield, partialPeriod / periodLength);
        }

        if (payDelay > 0) {
            px *= 1 / Math.pow(1 + periodYield, (double) payDelay / periodLength);
        }

        px /= factors[periodIdx];

        if (partialPeriod > 0) {
            px -= rates[periodIdx] * ((periodLength - partialPeriod) / periodLength); // calc date accrued
        }
        return px;
    }
}

