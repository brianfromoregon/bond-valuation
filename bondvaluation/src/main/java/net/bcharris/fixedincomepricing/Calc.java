package net.bcharris.fixedincomepricing;

public class Calc {

    /**
     * Calculate price.
     * @param daysToMaturity Number of days until maturity, must be >= 1.
     * @param factors The factors, where factors[factors.length-1] must be 0.
     * @param periodYield The required rate of return divided by the number of periods per year.
     * @param periodLength The length of a period.
     * @param periodCoupon The annual coupon rate divided by the number of periods per year.
     * @param payDelay The payment delay for cash flows.
     * @return The price.
     */
    public static double price(int daysToMaturity, double[] factors, double periodYield, int periodLength, double periodCoupon, int payDelay) {
        int cashFlows = daysToMaturity / periodLength;
        double partialPeriod = daysToMaturity % periodLength;
        if (partialPeriod > 0) {
            cashFlows++;
        }

        double px = 0;
        int factorIdx = factors.length - 1;
        for (int i = 0; i < cashFlows; i++) {
            px *= 1 / (1 + periodYield);
            double factor = factors[factorIdx];
            double prevFactor = factors[factorIdx - 1];

            px += prevFactor * periodCoupon; // coupon amount
            px += prevFactor - factor; // paydown amount
            factorIdx--;
        }

        if (partialPeriod == 0) {
            px *= 1 / (1 + periodYield);
        } else {
            px *= 1 / Math.pow(1 + periodYield, partialPeriod / periodLength);
        }

        if (payDelay > 0) {
            px *= 1 / Math.pow(1 + periodYield, (double) payDelay / periodLength);
        }

        px /= factors[factorIdx];

        if (partialPeriod > 0) {
            px -= periodCoupon * ((periodLength - partialPeriod) / periodLength); // current accrued
        }
        return px;
    }
}

